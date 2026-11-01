package com.alushestore.domain

import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.VentaEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Protege la regla de negocio del dinero de AlusheStore.
 *
 * Lo importante: una venta capturada a mano no tiene costo conocido y cuenta
 * como utilidad del 100%. Son los artículos viejos que se venden sin pasar por
 * el catálogo. Si alguien "arregla" esto tratando el costo faltante de otro modo,
 * estos tests se caen.
 */
class CalculadoraMetricasTest {

    private fun venta(id: Long, monto: Double, vendedor: String = "PRINCIPAL") =
        VentaEntity(id = id, baseId = 1, articulo = "art$id", monto = monto, vendedor = vendedor)

    private fun detalle(ventaId: Long, costo: Double, precio: Double, cantidad: Int = 1) =
        DetalleVentaEntity(
            ventaId = ventaId,
            nombreProductoSnapshot = "prod",
            cantidad = cantidad,
            costoUnitarioHistorico = costo,
            precioUnitarioFinal = precio
        )

    @Test
    fun `venta manual sin costo es utilidad del cien por ciento`() {
        val m = CalculadoraMetricas.calcular(listOf(venta(1, 90.0)), emptyList())

        assertEquals(90.0, m.totalVendido, 0.001)
        assertEquals(0.0, m.costoVendido, 0.001)
        assertEquals(90.0, m.utilidadNeta, 0.001)
        assertEquals(100, m.margenSobreCosto)
        assertEquals(0, m.ventasConCosto)
        assertEquals(1, m.ventasSinCosto)
    }

    @Test
    fun `venta con inventario descuenta su costo historico`() {
        val m = CalculadoraMetricas.calcular(
            listOf(venta(1, 300.0)),
            listOf(detalle(ventaId = 1, costo = 100.0, precio = 300.0))
        )

        assertEquals(100.0, m.costoVendido, 0.001)
        assertEquals(200.0, m.utilidadNeta, 0.001)
        assertEquals(200, m.margenSobreCosto) // markup sobre costo, no sobre venta
        assertEquals(1, m.ventasConCosto)
        assertEquals(0, m.ventasSinCosto)
    }

    @Test
    fun `mezcla de ventas manuales y de inventario`() {
        // Caso real de la base OCTUBRE-NOVIEMBRE: casi todo capturado a mano
        val ventas = listOf(venta(1, 90.0), venta(2, 160.0), venta(3, 500.0))
        val detalles = listOf(detalle(ventaId = 3, costo = 200.0, precio = 500.0))

        val m = CalculadoraMetricas.calcular(ventas, detalles)

        assertEquals(750.0, m.totalVendido, 0.001)
        assertEquals(200.0, m.costoVendido, 0.001)
        assertEquals(550.0, m.utilidadNeta, 0.001)
        assertEquals(1, m.ventasConCosto)
        assertEquals(2, m.ventasSinCosto)
        assertEquals(250.0, m.ticketPromedio, 0.001)
    }

    @Test
    fun `cantidad mayor a uno multiplica el costo`() {
        val m = CalculadoraMetricas.calcular(
            listOf(venta(1, 600.0)),
            listOf(detalle(ventaId = 1, costo = 100.0, precio = 200.0, cantidad = 3))
        )

        assertEquals(300.0, m.costoVendido, 0.001)
        assertEquals(300.0, m.utilidadNeta, 0.001)
    }

    @Test
    fun `los detalles de otras ventas no contaminan el periodo`() {
        val m = CalculadoraMetricas.calcular(
            listOf(venta(1, 100.0)),
            listOf(
                detalle(ventaId = 1, costo = 40.0, precio = 100.0),
                detalle(ventaId = 99, costo = 999.0, precio = 999.0) // de otra base
            )
        )

        assertEquals(40.0, m.costoVendido, 0.001)
        assertEquals(60.0, m.utilidadNeta, 0.001)
    }

    @Test
    fun `periodo sin ventas no divide entre cero`() {
        val m = CalculadoraMetricas.calcular(emptyList(), emptyList())

        assertEquals(0.0, m.totalVendido, 0.001)
        assertEquals(0.0, m.ticketPromedio, 0.001)
        assertEquals(0, m.margenSobreCosto)
        assertEquals(0, m.numVentas)
    }

    @Test
    fun `el margen coincide con el que muestra la pantalla de estadisticas`() {
        // Misma expresión que usa EstadisticasViewModel.recalcularUtilidades()
        assertEquals(100, CalculadoraMetricas.margenSobreCosto(625.0, 0.0, 625.0))
        assertEquals(0, CalculadoraMetricas.margenSobreCosto(0.0, 0.0, 0.0))
        assertEquals(50, CalculadoraMetricas.margenSobreCosto(300.0, 200.0, 100.0))
    }
}
