package com.alushestore.domain

import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.remote.ProductoRemoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductoUnificadoTest {

    private fun local(id: Long, nombre: String, precio: Double = 100.0) =
        ProductoEntity(
            id = id, sku = "ALU-$id", nombre = nombre,
            costoUnitario = 10.0, precioVenta = precio, stockDisponible = 5
        )

    private fun web(id: Long, nombre: String, precio: String = "$100 MXN") =
        ProductoRemoto(id = id, nombre = nombre, precio = precio, costo = 10.0, stock = 3)

    @Test
    fun `normaliza acentos mayusculas y adornos`() {
        assertEquals("remate de mallon termico", ProductoUnificado.normalizar("🔴Remate de Mallón Térmico🔴"))
        assertEquals("audifonos", ProductoUnificado.normalizar("  AUDÍFONOS  "))
    }

    @Test
    fun `el mismo producto en los dos lados no se duplica`() {
        val fusion = ProductoUnificado.fusionar(
            listOf(local(1, "Audífonos inalámbricos")),
            listOf(web(99, "🔴AUDIFONOS INALAMBRICOS🔴"))
        )

        assertEquals(1, fusion.size)
        val p = fusion.first()
        assertEquals(OrigenProducto.AMBOS, p.origen)
        assertEquals(1L, p.idLocal)
        assertEquals(99L, p.idRemoto)
        assertTrue("debe poder editarse porque existe en local", p.editableEnApp)
    }

    @Test
    fun `productos que solo estan en la web se agregan como WEB`() {
        val fusion = ProductoUnificado.fusionar(
            listOf(local(1, "Tortas")),
            listOf(web(99, "Vidrios templados"))
        )

        assertEquals(2, fusion.size)
        val soloWeb = fusion.first { it.nombre == "Vidrios templados" }
        assertEquals(OrigenProducto.WEB, soloWeb.origen)
        assertEquals(null, soloWeb.idLocal)
        assertTrue("no se puede editar desde la app todavía", !soloWeb.editableEnApp)
    }

    @Test
    fun `el precio de texto de supabase se interpreta como numero`() {
        val fusion = ProductoUnificado.fusionar(emptyList(), listOf(web(1, "Algo", "$1,250.50 MXN")))
        assertEquals(1250.50, fusion.first().precio, 0.001)
    }

    @Test
    fun `precio vacio o basura no truena y queda en cero`() {
        val fusion = ProductoUnificado.fusionar(
            emptyList(),
            listOf(ProductoRemoto(id = 1, nombre = "Sin precio", precio = null))
        )
        assertEquals(0.0, fusion.first().precio, 0.001)
    }

    @Test
    fun `las claves de lista son unicas`() {
        val fusion = ProductoUnificado.fusionar(
            listOf(local(1, "Uno"), local(2, "Dos")),
            listOf(web(10, "Tres"), web(11, "Cuatro"))
        )
        val claves = fusion.map { it.clave }
        assertEquals(claves.size, claves.toSet().size)
        fusion.forEach { assertNotNull(it.clave) }
    }

    // ── Hermanado por SKU ────────────────────────────────────────────────────

    @Test
    fun `el SKU manda aunque el nombre ya no se parezca`() {
        // El caso que motivó todo esto: alguien renombra el producto en el
        // admin web. Antes se duplicaba; ahora el SKU lo sostiene.
        val fusion = ProductoUnificado.fusionar(
            listOf(local(1, "Audífonos inalámbricos")),
            listOf(web(99, "Audífonos Bluetooth 5.3 NEGROS").copy(sku = "ALU-1"))
        )

        assertEquals(1, fusion.size)
        assertEquals(OrigenProducto.AMBOS, fusion.first().origen)
        assertEquals(99L, fusion.first().idRemoto)
    }

    @Test
    fun `el SKU gana sobre una coincidencia de nombre`() {
        // Dos productos web compiten por el mismo local: uno coincide por
        // nombre y el otro trae el SKU. Tiene que ganar el del SKU, venga en el
        // orden que venga.
        val fusion = ProductoUnificado.fusionar(
            listOf(local(1, "Mallón térmico")),
            listOf(
                web(70, "Mallón térmico"),                        // coincide por nombre
                web(71, "Mallón térmico gris").copy(sku = "ALU-1") // coincide por SKU
            )
        )

        val hermanado = fusion.single { it.origen == OrigenProducto.AMBOS }
        assertEquals(71L, hermanado.idRemoto)
        // El otro no desaparece: sigue estando en la tienda y hay que verlo
        assertTrue(fusion.any { it.idRemoto == 70L && it.origen == OrigenProducto.WEB })
    }

    @Test
    fun `un SKU repetido en local no hermana a ciegas`() {
        // No debería pasar (el índice de Room es único), pero si pasara es
        // preferible caer al nombre que elegir un local al azar.
        val fusion = ProductoUnificado.fusionar(
            listOf(
                local(1, "Playera").copy(sku = "DUP"),
                local(2, "Gorra").copy(sku = "DUP")
            ),
            listOf(web(50, "Gorra").copy(sku = "DUP"))
        )

        val hermanado = fusion.single { it.origen == OrigenProducto.AMBOS }
        assertEquals("debe hermanar por nombre, no por el SKU ambiguo", 2L, hermanado.idLocal)
    }

    @Test
    fun `los WEB- del relleno no hermanan con nada`() {
        // Los 144 productos que ya existían nacieron con SKU WEB-####. Ninguno
        // debe hermanarse por SKU: siguen dependiendo del nombre.
        val fusion = ProductoUnificado.fusionar(
            listOf(local(1, "Collar de perlas")),
            listOf(
                web(10, "Collar de perlas").copy(sku = "WEB-0001"),
                web(11, "Arete dorado").copy(sku = "WEB-0002")
            )
        )

        assertEquals(2, fusion.size)
        assertEquals(10L, fusion.single { it.origen == OrigenProducto.AMBOS }.idRemoto)
    }

    // ── Precio ───────────────────────────────────────────────────────────────

    @Test
    fun `usa la columna numerica de Supabase cuando viene`() {
        // precio_mxn lo calcula Postgres: manda sobre el texto.
        val p = web(1, "Algo", precio = "MX$1,250").copy(precioMxn = 1250.0)
        assertEquals(1250.0, p.precioNumerico, 0.01)
    }

    @Test
    fun `un multipack se cobra por pieza`() {
        // "3xMX$100" son tres piezas por $100. El stock se cuenta en piezas y
        // el POS descuenta de a una, así que cobrar $100 sería cobrar de más.
        val p = web(1, "Calcetas", precio = "3xMX$100")
            .copy(precioMxn = 100.0, piezasPorPrecio = 3)
        assertEquals(33.33, p.precioNumerico, 0.01)
    }

    @Test
    fun `sin columna generada sigue leyendo el texto`() {
        // Respaldo por si la base todavía no tiene precio_mxn.
        assertEquals(100.0, web(1, "A", precio = "$100 MXN").precioNumerico, 0.01)
        assertEquals(1250.0, web(1, "B", precio = "MX$1,250").precioNumerico, 0.01)
        assertEquals(0.0, web(1, "C", precio = "").precioNumerico, 0.01)
    }
}
