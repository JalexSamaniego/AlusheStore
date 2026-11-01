package com.alushestore.data.repository

import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.MovimientoPendienteEntity
import com.alushestore.data.remote.CatalogoRemotoService
import com.alushestore.data.remote.ErrorDeNegocio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Cómo quedó un intento de vaciar la cola */
data class ResultadoCola(
    val enviados: Int = 0,
    val nuevosConflictos: Int = 0,
    val quedanPendientes: Int = 0,
    val sinRed: Boolean = false
)

/**
 * La cola de movimientos de stock que todavía no llegan a Supabase.
 *
 * Es lo que permite vender en el tianguis sin señal. La regla es sencilla:
 * **la venta nunca se detiene**. Si no hay internet, el descuento del almacén
 * espera aquí y se manda solo cuando vuelve la red.
 *
 * Lo que no se puede prometer es integridad perfecta del stock compartido
 * estando offline: si los dos teléfonos venden la última pieza sin señal, el
 * servidor rechazará una y quedará marcada como conflicto para que una persona
 * decida. Es preferible a perder la venta o a fingir que no pasó nada.
 */
class ColaMovimientosRepository(
    private val db: AlusheDatabase,
    private val remoto: CatalogoRemotoService
) {
    private val dao = db.movimientoPendienteDao()
    private val candado = Mutex()

    fun pendientes(): Flow<List<MovimientoPendienteEntity>> = dao.getTodos()

    fun conflictos(): Flow<List<MovimientoPendienteEntity>> = dao.getConflictos()

    /** Movimientos que siguen en camino: se mandan solos cuando haya red */
    fun contarPendientes(): Flow<Int> = dao.contarReintentables()

    /** Movimientos que el servidor rechazó y ya no se reintentan */
    fun contarConflictos(): Flow<Int> = dao.contarConflictos()

    /**
     * Olvida los movimientos en conflicto.
     *
     * La venta ya se cobró y se registró en la base: descartar sólo deja de
     * insistir. El stock compartido queda desfasado y hay que corregirlo a mano
     * desde Inventario, por eso la app lo dice antes de descartar.
     */
    suspend fun descartarConflictos() = withContext(Dispatchers.IO) { dao.descartarConflictos() }

    /**
     * Cancela los descuentos que todavía no se mandan.
     *
     * Sirve para una venta que se deshizo o para limpiar una prueba. El stock
     * del almacén se queda como está, así que hay que revisarlo en Inventario.
     */
    suspend fun descartarPendientes() = withContext(Dispatchers.IO) { dao.descartarPendientes() }

    /**
     * Piezas por producto que ya se vendieron pero aún no se descuentan allá.
     * Se restan del stock que se muestra para no vender dos veces lo mismo
     * desde este teléfono.
     */
    fun comprometidoPorProducto(): Flow<Map<Long, Int>> =
        dao.getComprometidoPorProducto().map { filas ->
            filas.associate { it.productoRemotoId to it.piezas }
        }

    suspend fun encolarSalida(
        productoRemotoId: Long,
        nombreProducto: String,
        cantidad: Int,
        vendedor: String,
        motivo: String = "Venta POS"
    ) = encolar(productoRemotoId, nombreProducto, "salida", cantidad, vendedor, motivo)

    /**
     * Deja un movimiento esperando internet.
     *
     * También acepta `"entrada"`: una devolución al almacén (un apartado que se
     * canceló) tiene que esperar igual que una salida. Si se perdiera, el
     * inventario compartido se quedaría con piezas de menos para siempre.
     */
    suspend fun encolar(
        productoRemotoId: Long,
        nombreProducto: String,
        tipo: String,
        cantidad: Int,
        vendedor: String,
        motivo: String
    ) = withContext(Dispatchers.IO) {
        dao.insertar(
            MovimientoPendienteEntity(
                productoRemotoId = productoRemotoId,
                nombreProducto = nombreProducto,
                tipo = tipo,
                cantidad = cantidad,
                motivo = motivo,
                vendedor = vendedor
            )
        )
    }

    suspend fun descartar(id: Long) = withContext(Dispatchers.IO) { dao.eliminar(id) }

    /**
     * Intenta mandar todo lo que está esperando.
     *
     * - Si el servidor acepta, el movimiento se borra de la cola.
     * - Si el servidor rechaza (sin stock, sin permiso), se marca como conflicto
     *   y se sigue con los demás: ese ya no se arregla reintentando.
     * - Si falla la red, se detiene de inmediato. No tiene sentido insistir con
     *   los demás y así se conserva el orden en que se vendieron.
     */
    suspend fun procesar(token: String): ResultadoCola = candado.withLock {
        withContext(Dispatchers.IO) {
            val porMandar = dao.getReintentables()
            if (porMandar.isEmpty()) return@withContext ResultadoCola()

            var enviados = 0
            var conflictos = 0
            var sinRed = false

            for (m in porMandar) {
                val r = remoto.registrarMovimiento(
                    accessToken = token,
                    productoId = m.productoRemotoId,
                    tipo = m.tipo,
                    cantidad = m.cantidad,
                    motivo = m.motivo + " (diferido)",
                    vendedor = m.vendedor
                )

                when {
                    r.isSuccess -> {
                        dao.eliminar(m.id)
                        enviados++
                    }
                    r.exceptionOrNull() is ErrorDeNegocio -> {
                        dao.marcarConflicto(m.id, r.exceptionOrNull()?.message)
                        conflictos++
                    }
                    else -> {
                        dao.marcarIntentoFallido(m.id, r.exceptionOrNull()?.message)
                        sinRed = true
                        break
                    }
                }
            }

            ResultadoCola(
                enviados = enviados,
                nuevosConflictos = conflictos,
                quedanPendientes = dao.getReintentables().size,
                sinRed = sinRed
            )
        }
    }
}
