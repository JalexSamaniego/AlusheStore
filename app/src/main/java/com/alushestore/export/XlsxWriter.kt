package com.alushestore.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Una celda de la hoja */
sealed interface Celda {
    data class Texto(val valor: String) : Celda
    data class Numero(val valor: Double) : Celda
    /** Número con formato de dinero ($1,234.50) */
    data class Dinero(val valor: Double) : Celda
    data object Vacia : Celda
}

/** Una hoja: la primera fila se pone en negritas como encabezado */
data class Hoja(val nombre: String, val filas: List<List<Celda>>)

/**
 * Escribe un .xlsx de verdad (Office Open XML) sin librerías.
 *
 * Un .xlsx es un zip con unos cuantos XML. Se escribe lo mínimo para que lo
 * abran Excel, Google Sheets y WPS: textos en línea, números, negritas en el
 * encabezado y formato de dinero. Evita meter Apache POI, que pesa varios MB.
 */
object XlsxWriter {

    fun escribir(hojas: List<Hoja>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            fun archivo(ruta: String, contenido: String) {
                zip.putNextEntry(ZipEntry(ruta))
                zip.write(contenido.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            archivo("[Content_Types].xml", contentTypes(hojas.size))
            archivo("_rels/.rels", RELS)
            archivo("xl/workbook.xml", workbook(hojas))
            archivo("xl/_rels/workbook.xml.rels", workbookRels(hojas.size))
            archivo("xl/styles.xml", STYLES)
            hojas.forEachIndexed { i, h -> archivo("xl/worksheets/sheet${i + 1}.xml", hoja(h)) }
        }
        return salida.toByteArray()
    }

    private fun hoja(h: Hoja): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        val columnas = h.filas.maxOfOrNull { it.size } ?: 0
        if (columnas > 0) {
            append("<cols>")
            for (c in 1..columnas) {
                val ancho = if (c == 1) 34 else 16
                append("""<col min="$c" max="$c" width="$ancho" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        h.filas.forEachIndexed { r, fila ->
            append("""<row r="${r + 1}">""")
            fila.forEachIndexed { c, celda ->
                val ref = "${columna(c)}${r + 1}"
                val estiloTexto = if (r == 0) """ s="1"""" else ""
                when (celda) {
                    is Celda.Texto ->
                        append("""<c r="$ref" t="inlineStr"$estiloTexto><is><t xml:space="preserve">${escapar(celda.valor)}</t></is></c>""")
                    is Celda.Numero -> append("""<c r="$ref"><v>${celda.valor}</v></c>""")
                    is Celda.Dinero -> append("""<c r="$ref" s="2"><v>${celda.valor}</v></c>""")
                    Celda.Vacia -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** 0 → A, 25 → Z, 26 → AA */
    fun columna(indice: Int): String {
        var n = indice + 1
        val sb = StringBuilder()
        while (n > 0) {
            val resto = (n - 1) % 26
            sb.insert(0, ('A' + resto))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun escapar(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")
        // Caracteres de control que XML no acepta
        .filter { it == '\n' || it == '\t' || it >= ' ' }

    /** Excel no acepta ciertos caracteres en el nombre de la hoja y lo corta a 31 */
    private fun nombreHoja(s: String) = s.replace(Regex("""[\\/?*\[\]:]"""), " ").take(31).ifBlank { "Hoja" }

    private fun contentTypes(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(hojas: List<Hoja>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        hojas.forEachIndexed { i, h ->
            append("""<sheet name="${escapar(nombreHoja(h.nombre))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    // Estilos: 0 normal, 1 negritas (encabezado), 2 dinero
    private val STYLES ="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;${'$'}&quot;#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>"""
}
