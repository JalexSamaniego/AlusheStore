package com.alushestore.domain

import com.alushestore.data.local.MetasConfig
import com.alushestore.data.local.entities.MetodoPago
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.data.local.entities.VentaEntity

/** Dónde está el dinero que entró hoy */
data class CajaDelDia(
    val efectivo: Double = 0.0,
    val transferencia: Double = 0.0,
    val tarjeta: Double = 0.0,
    /** Ventas viejas o del widget, que no tienen método */
    val sinEspecificar: Double = 0.0,
    val numVentas: Int = 0
) {
    val total: Double get() = efectivo + transferencia + tarjeta + sinEspecificar

    companion object {
        fun de(ventas: List<VentaEntity>): CajaDelDia {
            fun suma(m: MetodoPago?) = ventas.filter { MetodoPago.de(it.metodoPago) == m }.sumOf { it.monto }
            return CajaDelDia(
                efectivo = suma(MetodoPago.EFECTIVO),
                transferencia = suma(MetodoPago.TRANSFERENCIA),
                tarjeta = suma(MetodoPago.TARJETA),
                sinEspecificar = suma(null),
                numVentas = ventas.size
            )
        }

        fun deTianguis(ventas: List<TianguisVentaEntity>): CajaDelDia {
            fun suma(m: MetodoPago?) = ventas.filter { MetodoPago.de(it.metodoPago) == m }.sumOf { it.monto }
            return CajaDelDia(
                efectivo = suma(MetodoPago.EFECTIVO),
                transferencia = suma(MetodoPago.TRANSFERENCIA),
                tarjeta = suma(MetodoPago.TARJETA),
                sinEspecificar = suma(null),
                numVentas = ventas.size
            )
        }
    }
}

/** Avance de una meta: cuánto se lleva contra cuánto se quería */
data class AvanceMeta(val etiqueta: String, val meta: Double, val vendido: Double) {
    /** 0..1+, puede pasar de 1 si se superó la meta */
    val fraccion: Double get() = if (meta > 0) vendido / meta else 0.0
    val porcentaje: Int get() = (fraccion * 100).toInt()
    val falta: Double get() = (meta - vendido).coerceAtLeast(0.0)
}

/**
 * Las metas de una base con lo vendido en ella. null si esa base no tiene
 * ninguna meta puesta. Sólo cuentan las ventas de esa base, no las de otras.
 */
data class ProgresoMetas(
    val general: AvanceMeta?,
    val porVendedor: List<AvanceMeta>
) {
    companion object {
        fun de(config: MetasConfig, ventasDeBase: List<VentaEntity>, nombreBase: String): ProgresoMetas? {
            if (!config.activa) return null
            val general = config.metaMensual?.let {
                AvanceMeta("Meta · $nombreBase", it, ventasDeBase.sumOf { v -> v.monto })
            }
            val porVendedor = config.porVendedor.map { (nombre, meta) ->
                AvanceMeta(
                    nombre,
                    meta,
                    ventasDeBase.filter { it.vendedor.equals(nombre, ignoreCase = true) }.sumOf { it.monto }
                )
            }.sortedBy { it.etiqueta }
            return ProgresoMetas(general, porVendedor)
        }
    }
}
