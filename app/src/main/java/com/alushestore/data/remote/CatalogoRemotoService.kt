package com.alushestore.data.remote

import com.alushestore.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Producto tal como vive en el catálogo compartido de alushe.store.
 *
 * Los nombres con guion bajo son los de la tabla `productos` en Supabase, que
 * también alimenta el sitio público y el admin web.
 */
/**
 * El servidor respondió y rechazó la operación: no hay stock, la cuenta no tiene
 * permiso, el producto ya no existe. Reintentar no sirve, necesita una decisión.
 *
 * Cualquier otra excepción (sin red, timeout) sí se puede reintentar luego.
 */
class ErrorDeNegocio(mensaje: String) : Exception(mensaje)

data class ProductoRemoto(
    val id: Long,
    val nombre: String,
    /**
     * Llave estable del catálogo compartido. `WEB-####` si nació en la web,
     * `ALU-####` si lo dio de alta o lo hermanó esta app. Es lo que permite
     * renombrar un producto allá sin que la app crea que es otro distinto.
     */
    val sku: String? = null,
    /**
     * Ojo: en Supabase `precio` es TEXTO con formato, porque es lo que el sitio
     * muestra tal cual. Conviven "$100 MXN", "MX$1,200" y el multipack
     * "3xMX$100". Para cuentas se usa [precioNumerico], nunca este texto.
     */
    val precio: String? = null,
    /** Columna generada en Supabase: el número que hay dentro de [precio]. */
    @SerializedName("precio_mxn") val precioMxn: Double? = null,
    /** Cuántas piezas cubre [precioMxn]. 1 salvo multipack "3xMX$100". */
    @SerializedName("piezas_por_precio") val piezasPorPrecio: Int? = null,
    val costo: Double? = null,
    val stock: Int? = null,
    @SerializedName("stock_minimo") val stockMinimo: Int? = null,
    val categoria: String? = null,
    val estado: String? = null,
    @SerializedName("url_imagen") val urlImagen: String? = null,
    val fuente: String? = null,
    val actualizado: String? = null
) {
    /**
     * Precio de **una pieza**.
     *
     * Se prefiere `precio_mxn`, que lo calcula Postgres a partir del texto y no
     * se puede desincronizar de él. El parseo aquí queda sólo como respaldo por
     * si la base todavía no tiene la columna generada.
     *
     * En un multipack ("3xMX$100" = tres piezas por $100) se divide, porque el
     * stock se cuenta en piezas y el POS descuenta de a una: cobrar los $100
     * por una sola pieza sería cobrar de más.
     */
    val precioNumerico: Double
        get() {
            val total = precioMxn
                ?: precio?.substringAfterLast('$')
                    ?.replace(Regex("[^0-9.]"), "")
                    ?.toDoubleOrNull()
                ?: 0.0
            return total / (piezasPorPrecio ?: 1).coerceAtLeast(1)
        }

    val disponible: Boolean get() = (stock ?: 0) > 0 && !estado.equals("Agotado", ignoreCase = true)
    val stockBajo: Boolean get() = stockMinimo != null && (stock ?: 0) <= stockMinimo

    /** Margen sobre costo, con los mismos criterios que usa la app para sus ventas. */
    val margenSobreCosto: Int?
        get() {
            val c = costo ?: return null
            if (c <= 0.0) return null
            return (((precioNumerico - c) / c) * 100).toInt()
        }
}

/**
 * Puente de solo lectura con el catálogo de alushe.store.
 *
 * Usa la clave publishable, la misma que el sitio web expone en su JavaScript,
 * así que no necesita iniciar sesión: la política `lectura_publica` de Supabase
 * permite SELECT a cualquiera. Escribir stock o dar de alta productos sí exige
 * sesión autenticada y todavía no se hace desde aquí.
 */
class CatalogoRemotoService(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val apiKey: String = BuildConfig.SUPABASE_KEY
) {

    /** false cuando el proyecto se compiló sin credenciales en local.properties */
    fun estaConfigurado(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /**
     * Trae el catálogo completo, paginando de [PAGINA] en [PAGINA] filas porque
     * PostgREST corta las respuestas largas.
     */
    suspend fun listarProductos(): Result<List<ProductoRemoto>> = withContext(Dispatchers.IO) {
        if (!estaConfigurado()) {
            return@withContext Result.failure(
                IllegalStateException("Falta configurar supabase.url y supabase.key en local.properties")
            )
        }

        try {
            val acumulado = mutableListOf<ProductoRemoto>()
            var desde = 0

            while (true) {
                val pagina = pedirPagina(desde, desde + PAGINA - 1)
                acumulado += pagina
                if (pagina.size < PAGINA) break
                desde += PAGINA
            }

            Result.success(acumulado)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun pedirPagina(desde: Int, hasta: Int): List<ProductoRemoto> {
        val url = URL("$baseUrl/rest/v1/productos?select=*&order=nombre.asc")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Range", "$desde-$hasta")
        }

        try {
            // PostgREST responde 206 cuando entrega un rango parcial
            if (conn.responseCode !in 200..299) {
                val detalle = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw RuntimeException("Supabase respondió ${conn.responseCode}: ${detalle.take(200)}")
            }

            val cuerpo = conn.inputStream.bufferedReader().use { it.readText() }
            val tipo = object : TypeToken<List<ProductoRemoto>>() {}.type
            return Gson().fromJson(cuerpo, tipo) ?: emptyList()
        } finally {
            conn.disconnect()
        }
    }

    // ── Escritura: mover stock del almacén compartido ────────────────────────

    /**
     * Mueve el stock de un producto del catálogo compartido llamando al RPC
     * `registrar_movimiento`, que además deja constancia en la bitácora.
     *
     * No se actualiza `productos.stock` directamente a propósito: el RPC es la
     * única vía que escribe en `movimientos_inventario`, bloquea la fila para
     * evitar carreras entre dos teléfonos, y rechaza dejar el stock en negativo.
     *
     * @param tipo "salida" para una venta, "entrada" para reposición, "ajuste"
     *             para fijar el stock a un número exacto.
     * @return el stock que quedó.
     */
    suspend fun registrarMovimiento(
        accessToken: String,
        productoId: Long,
        tipo: String,
        cantidad: Int,
        motivo: String? = null,
        vendedor: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!estaConfigurado()) {
            return@withContext Result.failure(
                IllegalStateException("Falta configurar supabase.url y supabase.key en local.properties")
            )
        }

        val intento = llamarRpc(accessToken, productoId, tipo, cantidad, motivo, vendedor)

        // Si la base todavía tiene la función vieja de 4 parámetros, PostgREST
        // no encuentra la firma con p_vendedor. Se reintenta sin él para que la
        // app siga vendiendo aunque el SQL de separar vendedores no se haya corrido.
        if (vendedor != null && intento.exceptionOrNull() is FirmaDesconocida) {
            return@withContext llamarRpc(accessToken, productoId, tipo, cantidad, motivo, null)
        }

        intento
    }

    /** La función aún no acepta p_vendedor en esta base */
    private class FirmaDesconocida : Exception("La base todavía no tiene el parámetro de vendedor")

    private fun llamarRpc(
        accessToken: String,
        productoId: Long,
        tipo: String,
        cantidad: Int,
        motivo: String?,
        vendedor: String?
    ): Result<Int> {
        val conn = (URL("$baseUrl/rest/v1/rpc/registrar_movimiento").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val campos = mutableMapOf<String, Any?>(
                "p_producto_id" to productoId,
                "p_tipo" to tipo,
                "p_cantidad" to cantidad,
                "p_motivo" to motivo
            )
            vendedor?.takeIf { it.isNotBlank() }?.let { campos["p_vendedor"] = it }

            conn.outputStream.use { it.write(Gson().toJson(campos).toByteArray()) }

            if (conn.responseCode !in 200..299) {
                val detalle = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (conn.responseCode == 404 && detalle.contains("Could not find the function")) {
                    return Result.failure(FirmaDesconocida())
                }
                // El servidor contestó y dijo que no: reintentar no lo va a arreglar
                return Result.failure(ErrorDeNegocio(traducirError(conn.responseCode, detalle)))
            }

            val texto = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            val nuevoStock = texto.toIntOrNull()
                ?: return Result.failure(Exception("Respuesta inesperada del servidor: ${texto.take(80)}"))

            Result.success(nuevoStock)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Cambia precio y/o costo de un producto del catálogo compartido.
     *
     * El stock **no** se toca aquí: para eso está [registrarMovimiento], que
     * además deja la pieza registrada en la bitácora.
     *
     * Ojo con `precio`: en Supabase es una columna de TEXTO con formato
     * (`"$100 MXN"`), porque así la muestra el sitio. Se escribe con esa misma
     * forma para no romper el catálogo público.
     */
    suspend fun actualizarPrecioYCosto(
        accessToken: String,
        productoId: Long,
        precio: Double?,
        costo: Double?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (precio == null && costo == null) return@withContext Result.success(Unit)

        val campos = mutableMapOf<String, Any>()
        precio?.let { campos["precio"] = formatearPrecioWeb(it) }
        costo?.let { campos["costo"] = it }

        parchear(accessToken, productoId, campos)
    }

    /** PATCH a una fila de `productos`, comprobando que de verdad se escribió. */
    private suspend fun parchear(
        accessToken: String,
        productoId: Long,
        campos: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (campos.isEmpty()) return@withContext Result.success(Unit)

        val url = URL("$baseUrl/rest/v1/productos?id=eq.$productoId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            // Pide de vuelta la fila para saber si de verdad se escribió:
            // sin esto PostgREST responde 204 aunque RLS haya filtrado todo.
            setRequestProperty("Prefer", "return=representation")
        }

        try {
            conn.outputStream.use { it.write(Gson().toJson(campos).toByteArray()) }

            if (conn.responseCode !in 200..299) {
                val detalle = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext Result.failure(Exception(traducirError(conn.responseCode, detalle)))
            }

            val cuerpo = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            if (cuerpo == "[]") {
                return@withContext Result.failure(
                    Exception("No se guardó: esta cuenta no tiene permiso de editar el catálogo")
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Da de alta un producto en el catálogo compartido.
     *
     * Queda visible en alushe.store en cuanto se guarda, así que la pantalla
     * que llama aquí debe dejarlo claro.
     *
     * El `sku` viaja a propósito: es lo que hermana este producto con el de
     * Room para siempre, aunque después alguien le cambie el nombre desde el
     * admin web.
     *
     * @return el id que le asignó Supabase, para poder descontarle stock.
     */
    suspend fun crearProducto(
        accessToken: String,
        nombre: String,
        precio: Double,
        stock: Int,
        sku: String? = null,
        categoria: String? = null,
        costo: Double? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (!estaConfigurado()) {
            return@withContext Result.failure(
                IllegalStateException("Falta configurar supabase.url y supabase.key en local.properties")
            )
        }

        val campos = mutableMapOf<String, Any>(
            "nombre" to nombre.trim(),
            "precio" to formatearPrecioWeb(precio),
            "stock" to stock.coerceAtLeast(0),
            "estado" to if (stock > 0) "Disponible" else "Agotado",
            // Tiene que ser "manual": la tabla tiene un CHECK que sólo acepta
            // 'facebook' o 'manual'. Además es lo que protege al producto del
            // barrido de alushe_sync.py, que sólo borra los de 'facebook'.
            "fuente" to "manual"
        )
        sku?.takeIf { it.isNotBlank() }?.let { campos["sku"] = it.trim().uppercase() }
        categoria?.takeIf { it.isNotBlank() }?.let { campos["categoria"] = it.trim() }
        costo?.takeIf { it > 0.0 }?.let { campos["costo"] = it }

        val conn = (URL("$baseUrl/rest/v1/productos").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=representation")
        }

        try {
            conn.outputStream.use { it.write(Gson().toJson(campos).toByteArray()) }

            if (conn.responseCode !in 200..299) {
                val detalle = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext Result.failure(ErrorDeNegocio(traducirError(conn.responseCode, detalle)))
            }

            val cuerpo = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            val tipo = object : TypeToken<List<ProductoRemoto>>() {}.type
            val creados: List<ProductoRemoto> = Gson().fromJson(cuerpo, tipo) ?: emptyList()
            val nuevo = creados.firstOrNull()
                ?: return@withContext Result.failure(
                    ErrorDeNegocio("No se dio de alta: esta cuenta no tiene permiso de editar el catálogo")
                )

            Result.success(nuevo.id)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Graba el SKU de la app sobre un producto del catálogo compartido.
     *
     * Se usa cuando los dos lados quedaron hermanados por nombre: a partir de
     * aquí comparten llave y el hermanado deja de depender de cómo se escriba
     * el nombre.
     */
    suspend fun actualizarSku(
        accessToken: String,
        productoId: Long,
        sku: String
    ): Result<Unit> = parchear(accessToken, productoId, mapOf("sku" to sku.trim().uppercase()))

    /**
     * Mismo formato que escribe hoy el admin web (`formatearPrecio` en
     * admin/index.html): "MX$100", "MX$1,250".
     *
     * En la base conviven todavía 141 productos con el formato viejo
     * ("$100 MXN"); no se tocan desde aquí porque eso cambia lo que se ve en la
     * tienda. Da igual para las cuentas: `precio_mxn` lee los dos.
     */
    private fun formatearPrecioWeb(valor: Double): String {
        val entero = valor.toLong()
        return if (valor % 1.0 == 0.0) "MX$${"%,d".format(java.util.Locale.US, entero)}"
        else "MX$${String.format(java.util.Locale.US, "%,.2f", valor)}"
    }

    /** Convierte los errores de Postgres en algo que se pueda leer en pantalla */
    private fun traducirError(codigo: Int, detalle: String): String = when {
        detalle.contains("No autorizado") ->
            "Esta cuenta no tiene permiso de mover inventario"
        detalle.contains("Stock insuficiente") ->
            "No hay stock suficiente en el almacén compartido"
        detalle.contains("no existe") ->
            "Ese producto ya no está en el catálogo web"
        codigo == 401 || codigo == 403 ->
            "La sesión caducó, vuelve a emparejar el teléfono"
        else -> "Error $codigo al mover el stock: ${detalle.take(140)}"
    }

    private companion object {
        const val PAGINA = 500
        const val TIMEOUT_MS = 15_000
    }
}
