package com.alushestore.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.CorteConVendedores
import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.MetodoPago
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.domain.OrigenProducto
import com.alushestore.domain.ProductoUnificado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Archivos para guardar, imprimir o pasarle al contador.
 *
 * Todo se escribe en el caché de la app y se comparte con el selector de
 * Android (WhatsApp, Drive, correo, imprimir…). No se piden permisos de
 * almacenamiento.
 */
object Exportador {

    private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val MIME_PDF = "application/pdf"

    private val dinero = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    private fun fecha(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX")).format(Date(ms))
    private fun dia(ms: Long) = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX")).format(Date(ms))

    // ── Excel ────────────────────────────────────────────────────────────────

    /** Resumen y ventas de una base en un .xlsx de dos hojas */
    suspend fun ventasDeBase(
        context: Context,
        base: BaseEntity,
        ventas: List<VentaEntity>,
        detalles: List<DetalleVentaEntity>
    ): Uri = withContext(Dispatchers.IO) {
        val costoPorVenta = detalles.groupBy { it.ventaId }
            .mapValues { (_, d) -> d.sumOf { it.costoTotal } }

        val filasVentas = mutableListOf<List<Celda>>(
            listOf("Fecha", "Artículo", "Vendedor", "Método", "Monto", "Costo", "Utilidad").map { Celda.Texto(it) }
        )
        ventas.sortedBy { it.fecha }.forEach { v ->
            val costo = costoPorVenta[v.id]
            filasVentas += listOf(
                Celda.Texto(fecha(v.fecha)),
                Celda.Texto(v.articulo),
                Celda.Texto(v.vendedor),
                Celda.Texto(MetodoPago.de(v.metodoPago)?.etiqueta ?: ""),
                Celda.Dinero(v.monto),
                costo?.let { Celda.Dinero(it) } ?: Celda.Vacia,
                Celda.Dinero(v.monto - (costo ?: 0.0))
            )
        }

        val total = ventas.sumOf { it.monto }
        val costo = costoPorVenta.values.sum()
        val resumen = mutableListOf<List<Celda>>(
            listOf(Celda.Texto("Concepto"), Celda.Texto("Monto")),
            listOf(Celda.Texto("Base"), Celda.Texto(base.nombre)),
            listOf(Celda.Texto("Vendido"), Celda.Dinero(total)),
            listOf(Celda.Texto("Costo de lo vendido"), Celda.Dinero(costo)),
            listOf(Celda.Texto("Utilidad"), Celda.Dinero(total - costo)),
            listOf(Celda.Texto("Número de ventas"), Celda.Numero(ventas.size.toDouble()))
        )
        // Cuánto entró por cada forma de pago
        ventas.groupBy { MetodoPago.de(it.metodoPago)?.etiqueta }
            .forEach { (metodo, suyas) ->
                val etiqueta = metodo?.let { "Cobrado en $it" } ?: "Sin método registrado"
                resumen += listOf(Celda.Texto(etiqueta), Celda.Dinero(suyas.sumOf { it.monto }))
            }

        val bytes = XlsxWriter.escribir(listOf(Hoja("Resumen", resumen), Hoja("Ventas", filasVentas)))
        guardar(context, "ventas_${limpiar(base.nombre)}.xlsx", bytes)
    }

    /** Catálogo con stock. El costo sólo va si [incluirCostos], por el modo privacidad. */
    suspend fun inventario(
        context: Context,
        productos: List<ProductoUnificado>,
        incluirCostos: Boolean
    ): Uri = withContext(Dispatchers.IO) {
        val encabezado = buildList {
            addAll(listOf("Producto", "SKU", "Categoría", "Origen", "Disponible", "Apartado", "Precio"))
            if (incluirCostos) addAll(listOf("Costo", "Valor a costo"))
        }
        val filas = mutableListOf<List<Celda>>(encabezado.map { Celda.Texto(it) })
        productos.sortedBy { it.nombre.lowercase() }.forEach { p ->
            filas += buildList<Celda> {
                add(Celda.Texto(p.nombre))
                add(Celda.Texto(p.sku.orEmpty()))
                add(Celda.Texto(p.categoria.orEmpty()))
                add(Celda.Texto(when (p.origen) {
                    OrigenProducto.LOCAL -> "Teléfono"
                    OrigenProducto.WEB -> "Web"
                    OrigenProducto.AMBOS -> "Sincronizado"
                }))
                add(Celda.Numero(p.stockDisponible.toDouble()))
                add(Celda.Numero(p.stockApartado.toDouble()))
                add(Celda.Dinero(p.precio))
                if (incluirCostos) {
                    add(Celda.Dinero(p.costo))
                    add(Celda.Dinero(p.costo * p.stockDisponible))
                }
            }
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        guardar(context, "inventario_$stamp.xlsx", XlsxWriter.escribir(listOf(Hoja("Inventario", filas))))
    }

    // ── PDF del corte ────────────────────────────────────────────────────────

    /** Estado de cuenta de un periodo cerrado, listo para imprimir (carta) */
    suspend fun cortePdf(
        context: Context,
        corte: CorteConVendedores
    ): Uri = withContext(Dispatchers.IO) {
        val c = corte.corte
        val doc = PdfDocument()
        val ancho = 612
        val alto = 792
        val margen = 48f

        val verde = Color.parseColor("#2D7A4F")
        val tinta = Color.parseColor("#1A1A1A")
        val gris = Color.parseColor("#6E6E66")

        val titulo = Paint().apply { color = tinta; textSize = 22f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); isAntiAlias = true }
        val etiqueta = Paint().apply { color = gris; textSize = 9f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f; isAntiAlias = true }
        val texto = Paint().apply { color = tinta; textSize = 11f; isAntiAlias = true }
        val textoGris = Paint(texto).apply { color = gris }
        val valor = Paint(texto).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val linea = Paint().apply { color = Color.parseColor("#E5E4D6"); strokeWidth = 1f }

        var numPagina = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, numPagina).create())
        var canvas = pagina.canvas
        var y = margen

        fun nuevaPaginaSiHaceFalta(espacio: Float) {
            if (y + espacio < alto - margen) return
            doc.finishPage(pagina)
            numPagina++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, numPagina).create())
            canvas = pagina.canvas
            y = margen
        }

        fun seccion(nombre: String) {
            nuevaPaginaSiHaceFalta(40f)
            y += 18f
            canvas.drawText(nombre.uppercase(), margen, y, etiqueta)
            y += 6f
            canvas.drawLine(margen, y, ancho - margen, y, linea)
            y += 16f
        }

        fun renglon(izq: String, der: String) {
            nuevaPaginaSiHaceFalta(18f)
            canvas.drawText(izq, margen, y, texto)
            canvas.drawText(der, ancho - margen, y, valor)
            y += 17f
        }

        canvas.drawText("ALUSHESTORE · ESTADO DE CUENTA", margen, y, etiqueta)
        y += 26f
        canvas.drawText(c.nombrePeriodo, margen, y, titulo)
        y += 18f
        canvas.drawText("${dia(c.fechaInicio)} — ${dia(c.fechaCierre)}", margen, y, textoGris)
        y += 22f

        // Bloque de utilidad, el número que importa
        val bloque = Paint().apply { color = verde }
        canvas.drawRoundRect(margen, y, ancho - margen, y + 70f, 10f, 10f, bloque)
        val blanco = Paint(etiqueta).apply { color = Color.WHITE }
        val grande = Paint().apply { color = Color.WHITE; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        canvas.drawText("UTILIDAD NETA · ${c.margenSobreCosto}% SOBRE COSTO", margen + 16f, y + 22f, blanco)
        canvas.drawText(dinero.format(c.utilidadNeta), margen + 16f, y + 52f, grande)
        y += 86f

        seccion("Venta")
        renglon("Total vendido", dinero.format(c.totalVendido))
        renglon("Ventas registradas", "${c.numVentas}")
        renglon("Ticket promedio", dinero.format(c.ticketPromedio))

        seccion("Costo")
        renglon("Costo de lo vendido", dinero.format(c.costoVendido))
        renglon("Ventas con costo conocido", "${c.ventasConCosto}")
        renglon("Ventas sin costo (100% utilidad)", "${c.ventasSinCosto}")

        if (corte.vendedores.isNotEmpty()) {
            seccion("Por persona")
            corte.vendedores.forEach { renglon("${it.vendedor} · ${it.numVentas} ventas", dinero.format(it.totalVendido)) }
        }

        if (!corte.esRetroactivo) {
            seccion("Inventario al cierre")
            renglon("Valor del stock", dinero.format(c.valorInventarioCierre))
            renglon("Piezas disponibles", "${c.piezasInventarioCierre}")
        }

        nuevaPaginaSiHaceFalta(30f)
        y += 20f
        canvas.drawText("Generado el ${fecha(System.currentTimeMillis())} desde la app AlusheStore", margen, y, textoGris)

        doc.finishPage(pagina)
        val bytes = java.io.ByteArrayOutputStream().use { out -> doc.writeTo(out); out.toByteArray() }
        doc.close()
        guardar(context, "corte_${limpiar(c.nombrePeriodo)}.pdf", bytes)
    }

    // ── Compartir ────────────────────────────────────────────────────────────

    fun compartir(context: Context, uri: Uri, titulo: String) {
        val mime = when {
            uri.toString().endsWith(".pdf") -> MIME_PDF
            uri.toString().endsWith(".xlsx") -> MIME_XLSX
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun guardar(context: Context, nombre: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, "exportados").apply { mkdirs() }
        val archivo = File(dir, nombre)
        archivo.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    }

    private fun limpiar(nombre: String) =
        nombre.trim().replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ_-]+"), "_").trim('_').ifBlank { "alushe" }.take(40)
}
