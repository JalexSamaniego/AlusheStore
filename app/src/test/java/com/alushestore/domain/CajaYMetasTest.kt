package com.alushestore.domain

import com.alushestore.data.local.MetasConfig
import com.alushestore.data.local.entities.MetodoPago
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.data.local.entities.VentaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CajaYMetasTest {

    private fun venta(monto: Double, metodo: MetodoPago?, vendedor: String = "Dulce") =
        VentaEntity(baseId = 1, articulo = "x", monto = monto, metodoPago = metodo?.name, vendedor = vendedor)

    @Test
    fun `la caja separa cada forma de pago`() {
        val caja = CajaDelDia.de(
            listOf(
                venta(100.0, MetodoPago.EFECTIVO),
                venta(50.0, MetodoPago.EFECTIVO),
                venta(200.0, MetodoPago.TRANSFERENCIA),
                venta(80.0, MetodoPago.TARJETA)
            )
        )
        assertEquals(150.0, caja.efectivo, 0.001)
        assertEquals(200.0, caja.transferencia, 0.001)
        assertEquals(80.0, caja.tarjeta, 0.001)
        assertEquals(430.0, caja.total, 0.001)
        assertEquals(4, caja.numVentas)
    }

    @Test
    fun `las ventas viejas sin metodo no se cuentan como efectivo`() {
        val caja = CajaDelDia.de(listOf(venta(100.0, null), venta(40.0, MetodoPago.EFECTIVO)))
        assertEquals(40.0, caja.efectivo, 0.001)
        assertEquals(100.0, caja.sinEspecificar, 0.001)
        assertEquals(140.0, caja.total, 0.001)
    }

    @Test
    fun `la caja del tianguis usa la misma regla`() {
        val caja = CajaDelDia.deTianguis(
            listOf(
                TianguisVentaEntity(sesionId = 1, descripcion = "a", monto = 60.0, metodoPago = "EFECTIVO"),
                TianguisVentaEntity(sesionId = 1, descripcion = "b", monto = 90.0, metodoPago = "TRANSFERENCIA"),
                TianguisVentaEntity(sesionId = 1, descripcion = "c", monto = 10.0)
            )
        )
        assertEquals(60.0, caja.efectivo, 0.001)
        assertEquals(90.0, caja.transferencia, 0.001)
        assertEquals(10.0, caja.sinEspecificar, 0.001)
    }

    @Test
    fun `sin metas no hay progreso que mostrar`() {
        assertNull(ProgresoMetas.de(MetasConfig(), listOf(venta(100.0, null)), "noviembre"))
    }

    @Test
    fun `la meta general suma todas las ventas del mes`() {
        val p = ProgresoMetas.de(
            MetasConfig(metaMensual = 1000.0),
            listOf(venta(300.0, null), venta(200.0, MetodoPago.EFECTIVO, vendedor = "Alejandro")),
            "noviembre"
        )!!
        assertEquals(500.0, p.general!!.vendido, 0.001)
        assertEquals(50, p.general!!.porcentaje)
        assertEquals(500.0, p.general!!.falta, 0.001)
    }

    @Test
    fun `la meta por persona sólo cuenta sus ventas`() {
        val p = ProgresoMetas.de(
            MetasConfig(porVendedor = mapOf("Dulce" to 400.0, "Alejandro" to 100.0)),
            listOf(venta(200.0, null, "Dulce"), venta(150.0, null, "Alejandro")),
            "noviembre"
        )!!
        val dulce = p.porVendedor.first { it.etiqueta == "Dulce" }
        val alejandro = p.porVendedor.first { it.etiqueta == "Alejandro" }
        assertEquals(50, dulce.porcentaje)
        assertEquals(150, alejandro.porcentaje)
        assertEquals(0.0, alejandro.falta, 0.001)
    }
}
