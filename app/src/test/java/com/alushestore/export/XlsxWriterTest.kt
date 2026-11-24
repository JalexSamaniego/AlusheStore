package com.alushestore.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private fun contenido(bytes: ByteArray): Map<String, String> {
        val archivos = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entrada = zip.nextEntry
            while (entrada != null) {
                archivos[entrada.name] = zip.readBytes().toString(Charsets.UTF_8)
                entrada = zip.nextEntry
            }
        }
        return archivos
    }

    @Test
    fun `el xlsx trae las partes que exige Excel`() {
        val bytes = XlsxWriter.escribir(
            listOf(Hoja("Ventas", listOf(listOf(Celda.Texto("Monto")), listOf(Celda.Dinero(150.5)))))
        )
        val archivos = contenido(bytes)
        listOf(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"
        ).forEach { assertTrue("Falta $it", it in archivos) }
        assertTrue(archivos.getValue("xl/worksheets/sheet1.xml").contains("<v>150.5</v>"))
    }

    @Test
    fun `los textos se escapan para no romper el XML`() {
        val bytes = XlsxWriter.escribir(
            listOf(Hoja("Hoja", listOf(listOf(Celda.Texto("Bolsa <negra> & \"grande\"")))))
        )
        val hoja = contenido(bytes).getValue("xl/worksheets/sheet1.xml")
        assertTrue(hoja.contains("Bolsa &lt;negra&gt; &amp; &quot;grande&quot;"))
    }

    @Test
    fun `nombres de columna como en Excel`() {
        assertEquals("A", XlsxWriter.columna(0))
        assertEquals("Z", XlsxWriter.columna(25))
        assertEquals("AA", XlsxWriter.columna(26))
        assertEquals("AZ", XlsxWriter.columna(51))
    }

    @Test
    fun `una hoja por cada lista`() {
        val bytes = XlsxWriter.escribir(listOf(Hoja("Uno", emptyList()), Hoja("Dos", emptyList())))
        val archivos = contenido(bytes)
        assertTrue("xl/worksheets/sheet2.xml" in archivos)
        assertTrue(archivos.getValue("xl/workbook.xml").contains("name=\"Dos\""))
    }
}
