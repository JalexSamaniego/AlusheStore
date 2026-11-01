package com.alushestore.domain

import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.VentaEntity

/**
 * Resultado del cálculo de dinero de un conjunto de ventas.
 */
data class MetricasPeriodo(
    val totalVendido: Double,
    val numVentas: Int,
    val ticketPromedio: Double,
    val costoVendido: Double,
    val utilidadNeta: Double,
    val margenSobreCosto: Int,
    /** Ventas que sí traen desglose de inventario con costo conocido */
    val ventasConCosto: Int,
    /** Ventas capturadas a mano: cuentan como utilidad del 100% */
    val ventasSinCosto: Int
)

/**
 * Única definición de las métricas de dinero de AlusheStore.
 *
 * Tanto la pantalla de Estadísticas como el corte mensual calculan desde aquí,
 * para que sea imposible que muestren números distintos sobre los mismos datos.
 *
 * Regla de negocio: una venta sin desglose de inventario no aporta costo, de modo
 * que su monto completo cuenta como utilidad. Son los artículos viejos cuyo costo
 * ya no se recuerda, y que por eso se venden sin pasar por el catálogo.
 *
 * El margen es **sobre costo** (markup), no sobre venta, y vale 100% cuando no
 * hubo costo que descontar.
 */
object CalculadoraMetricas {

    /** Costo congelado de las ventas indicadas, sumando solo sus líneas de inventario. */
    fun costoDeVentas(ventaIds: Set<Long>, detalles: List<DetalleVentaEntity>): Double =
        detalles.filter { it.ventaId in ventaIds }
            .sumOf { it.costoUnitarioHistorico * it.cantidad }

    /** Margen sobre costo, en porcentaje entero. */
    fun margenSobreCosto(totalBruto: Double, costo: Double, utilidad: Double): Int =
        if (costo > 0.0) ((utilidad / costo) * 100).toInt() else if (totalBruto > 0) 100 else 0

    /** Métricas completas de un periodo a partir de sus ventas y los detalles de inventario. */
    fun calcular(ventas: List<VentaEntity>, detalles: List<DetalleVentaEntity>): MetricasPeriodo {
        val totalBruto = ventas.sumOf { it.monto }
        val ventaIds = ventas.map { it.id }.toSet()
        val costo = costoDeVentas(ventaIds, detalles)
        val utilidad = totalBruto - costo

        val idsConCosto = detalles.filter { it.ventaId in ventaIds }.map { it.ventaId }.toSet()

        return MetricasPeriodo(
            totalVendido = totalBruto,
            numVentas = ventas.size,
            ticketPromedio = if (ventas.isNotEmpty()) totalBruto / ventas.size else 0.0,
            costoVendido = costo,
            utilidadNeta = utilidad,
            margenSobreCosto = margenSobreCosto(totalBruto, costo, utilidad),
            ventasConCosto = idsConCosto.size,
            ventasSinCosto = ventas.count { it.id !in idsConCosto }
        )
    }
}
