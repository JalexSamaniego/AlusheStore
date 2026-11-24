package com.alushestore.domain

import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.VentaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Protege cómo se reparte el costo de una pieza apartada entre sus abonos.
 *
 * La regla: los abonos de una pieza que entran a una base cargan, en total,
 * exactamente el costo de la pieza. Antes cada abono contaba como utilidad del
 * 100% y la utilidad de los apartados salía inflada.
 */
class CostoDeAbonoTest {

    private fun costo(abono: Double, saldoAntes: Double, costoPieza: Double? = 100.0, monto: Double = 300.0) =
        CalculadoraMetricas.costoDeAbono(costoPieza, monto, abono, saldoAntes)

    @Test
    fun `tres abonos iguales cargan el costo completo de la pieza`() {
        val total = costo(100.0, 300.0)!! + costo(100.0, 200.0)!! + costo(100.0, 100.0)!!
        assertEquals(100.0, total, 0.001)
    }

    @Test
    fun `abonos desiguales tambien suman el costo exacto`() {
        val total = costo(50.0, 300.0)!! + costo(220.0, 250.0)!! + costo(30.0, 30.0)!!
        assertEquals(100.0, total, 0.001)
    }

    @Test
    fun `lo pagado de mas sobre el saldo no carga costo`() {
        // Debía 100 y pagó 150: sólo 100 cubren la pieza
        assertEquals(100.0 * 100.0 / 300.0, costo(150.0, 100.0)!!, 0.001)
    }

    @Test
    fun `abono a un apartado ya liquidado no carga costo`() {
        assertNull(costo(50.0, 0.0))
    }

    @Test
    fun `pieza sin costo conocido se comporta como antes`() {
        assertNull(costo(100.0, 300.0, costoPieza = null))
        assertNull(costo(100.0, 300.0, costoPieza = 0.0))
    }

    @Test
    fun `apartado de cero pesos no divide entre cero`() {
        assertNull(costo(10.0, 0.0, monto = 0.0))
    }

    @Test
    fun `un anticipo que no entro a la base no se cobra en costo despues`() {
        // Pieza de $300 que costó $100. Anticipo de $100 fuera de la base,
        // luego dos abonos de $100 a la base: cargan 2/3 del costo, no todo.
        val c1 = costo(100.0, 200.0)!!
        val c2 = costo(100.0, 100.0)!!
        assertEquals(200.0 / 3.0, c1 + c2, 0.001)
    }

    @Test
    fun `la utilidad del periodo con abonos queda como en el POS`() {
        val ventas = listOf(
            VentaEntity(id = 1, baseId = 1, articulo = "Abono: Bolsa", monto = 100.0),
            VentaEntity(id = 2, baseId = 1, articulo = "Abono: Bolsa", monto = 100.0),
            VentaEntity(id = 3, baseId = 1, articulo = "Abono: Bolsa", monto = 100.0)
        )
        val saldos = listOf(300.0, 200.0, 100.0)
        val detalles = ventas.mapIndexed { i, v ->
            DetalleVentaEntity(
                ventaId = v.id,
                nombreProductoSnapshot = "Bolsa",
                cantidad = 1,
                costoUnitarioHistorico = costo(100.0, saldos[i])!!,
                precioUnitarioFinal = 100.0
            )
        }

        val m = CalculadoraMetricas.calcular(ventas, detalles)

        // Igual que vender la bolsa de $300 con costo $100 en el POS
        assertEquals(300.0, m.totalVendido, 0.001)
        assertEquals(100.0, m.costoVendido, 0.001)
        assertEquals(200.0, m.utilidadNeta, 0.001)
        assertEquals(3, m.ventasConCosto)
    }
}
