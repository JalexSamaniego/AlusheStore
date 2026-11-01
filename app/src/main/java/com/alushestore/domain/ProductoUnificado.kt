package com.alushestore.domain

import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.remote.ProductoRemoto
import java.text.Normalizer

/** De dónde viene un producto del catálogo que ve la app */
enum class OrigenProducto {
    /** Sólo existe en el Room de este teléfono */
    LOCAL,

    /** Sólo existe en el catálogo compartido de alushe.store */
    WEB,

    /** El mismo producto en los dos lados, ya hermanado */
    AMBOS
}

/**
 * Un producto del catálogo, venga del teléfono o de alushe.store.
 *
 * Existe para que la pestaña de POS muestre **una sola lista** en vez de dos
 * inventarios que no se hablan.
 */
data class ProductoUnificado(
    val idLocal: Long? = null,
    val idRemoto: Long? = null,
    val nombre: String,
    val sku: String? = null,
    val categoria: String? = null,
    val costo: Double = 0.0,
    val precio: Double = 0.0,
    val stockDisponible: Int = 0,
    val stockApartado: Int = 0,
    /** Sólo la tiene lo que vive en Room; el catálogo web no maneja ubicación */
    val ubicacion: String? = null,
    val origen: OrigenProducto
) {
    /** Clave de identidad estable para listas de Compose */
    val clave: String
        get() = when {
            idLocal != null -> "local-$idLocal"
            idRemoto != null -> "web-$idRemoto"
            else -> "nombre-${normalizar(nombre)}"
        }

    /** Sólo se puede editar y vender desde la app lo que vive en Room */
    val editableEnApp: Boolean get() = idLocal != null

    /** Su stock vive en alushe.store, así que lo comparten los dos teléfonos */
    val esCompartido: Boolean get() = idRemoto != null

    val margenSobreCosto: Int?
        get() = if (costo > 0.0) (((precio - costo) / costo) * 100).toInt() else null

    companion object {

        fun de(p: ProductoEntity) = ProductoUnificado(
            idLocal = p.id,
            nombre = p.nombre,
            sku = p.sku,
            categoria = p.categoria,
            costo = p.costoUnitario,
            precio = p.precioVenta,
            stockDisponible = p.stockDisponible,
            stockApartado = p.stockApartado,
            ubicacion = p.ubicacion,
            origen = OrigenProducto.LOCAL
        )

        fun de(p: ProductoRemoto) = ProductoUnificado(
            idRemoto = p.id,
            nombre = p.nombre,
            sku = p.sku,
            categoria = p.categoria,
            costo = p.costo ?: 0.0,
            precio = p.precioNumerico,
            stockDisponible = p.stock ?: 0,
            origen = OrigenProducto.WEB
        )

        /** El SKU como llave de comparación, o null si no sirve como tal */
        private fun claveSku(sku: String?): String? =
            sku?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

        /**
         * Funde las dos listas en una sola.
         *
         * Hermana primero por **SKU** y sólo después por nombre normalizado.
         *
         * El orden importa: el nombre es un dato que se edita (desde el admin
         * web, desde el scraper de Facebook), y cada vez que cambia el producto
         * se vuelve "otro" para la app, que lo mostraría duplicado y le
         * descontaría stock al que no era. El SKU no cambia nunca.
         *
         * El nombre se queda como respaldo porque los 144 productos que ya
         * existían nacieron con SKU `WEB-####`, que ningún producto de Room
         * tiene. Quedan hermanados por nombre hasta que la app les grabe su
         * propio `ALU-####` al editarlos, y de ahí en adelante por SKU.
         *
         * Cuando un producto está en los dos lados gana el local, porque es el
         * que se puede editar y vender desde aquí, pero conserva el `idRemoto`
         * para poder descontarle stock compartido.
         */
        fun fusionar(
            locales: List<ProductoEntity>,
            remotos: List<ProductoRemoto>
        ): List<ProductoUnificado> {
            val deLocales = locales.map { de(it) }

            // Un SKU repetido entre dos productos locales haría ambiguo el
            // hermanado, así que esos se dejan fuera y caen al nombre.
            val porSku = deLocales
                .mapNotNull { l -> claveSku(l.sku)?.let { it to l } }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size == 1 }
                .mapValues { (_, v) -> v.first().idLocal }

            val porNombre = deLocales
                .groupBy { normalizar(it.nombre) }
                .filterValues { it.size == 1 }
                .mapValues { (_, v) -> v.first().idLocal }

            val hermanados = mutableMapOf<Long, Long>()  // idLocal -> idRemoto
            val yaHermanado = mutableSetOf<Long>()       // idRemoto

            // Dos pasadas, no una: si un producto web coincide por SKU con el
            // mismo local que otro coincide por nombre, tiene que ganar el del
            // SKU. En una sola pasada ganaría el que apareciera primero.
            for (r in remotos) {
                val idLocal = claveSku(r.sku)?.let { porSku[it] } ?: continue
                if (hermanados.putIfAbsent(idLocal, r.id) == null) yaHermanado += r.id
            }
            for (r in remotos) {
                if (r.id in yaHermanado) continue
                val idLocal = porNombre[normalizar(r.nombre)] ?: continue
                if (hermanados.putIfAbsent(idLocal, r.id) == null) yaHermanado += r.id
            }

            val soloRemotos = remotos.filterNot { it.id in yaHermanado }.map { de(it) }

            val resultado = deLocales.map { local ->
                val idRemoto = local.idLocal?.let { hermanados[it] }
                if (idRemoto != null) local.copy(idRemoto = idRemoto, origen = OrigenProducto.AMBOS)
                else local
            }

            return (resultado + soloRemotos).sortedBy { normalizar(it.nombre) }
        }

        /** minúsculas, sin acentos, sin espacios de más y sin emojis de adorno */
        fun normalizar(texto: String): String =
            Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
