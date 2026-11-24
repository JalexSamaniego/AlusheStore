package com.alushestore.domain

import com.alushestore.data.remote.ProductoRemoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La foto se elige igual que en la web: `url_github || url_imagen`.
 * Los enlaces de Facebook de `url_imagen` caducan; la copia en Supabase no.
 */
class FotoDurableTest {

    private val supabase = "https://x.supabase.co/storage/v1/object/public/catalogo-images/bolsa.jpg"
    private val facebook = "https://scontent.fna.fbcdn.net/v/bolsa.jpg?oe=6B014099"

    @Test
    fun `gana la copia de Supabase aunque haya enlace de Facebook`() {
        val p = ProductoRemoto(id = 1, nombre = "Bolsa", urlImagen = facebook, urlGithub = supabase)
        assertEquals(supabase, p.fotoDurable)
        assertEquals(supabase, ProductoUnificado.de(p).urlImagen)
    }

    @Test
    fun `sin copia en Supabase se usa la de Facebook`() {
        val p = ProductoRemoto(id = 1, nombre = "Bolsa", urlImagen = facebook, urlGithub = "")
        assertEquals(facebook, p.fotoDurable)
    }

    @Test
    fun `sin ninguna foto queda vacio`() {
        assertNull(ProductoRemoto(id = 1, nombre = "Bolsa").fotoDurable)
    }
}
