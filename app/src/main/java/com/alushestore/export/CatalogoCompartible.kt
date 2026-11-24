package com.alushestore.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.alushestore.domain.ProductoUnificado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

/**
 * Catálogo para mandar por WhatsApp o subir a estados.
 *
 * La imagen se dibuja directo en un bitmap de software, con fondo crema
 * sólido y se exporta en JPEG, siguiendo la regla de GEMINI.md: así WhatsApp
 * nunca la muestra en negro.
 */
object CatalogoCompartible {

    /** Más de esto ya no se lee en el teléfono de quien lo recibe */
    const val MAXIMO_EN_IMAGEN = 12

    private val dinero = NumberFormat.getCurrencyInstance(Locale("es", "MX")).apply {
        maximumFractionDigits = 0
    }

    private fun precio(p: ProductoUnificado): String =
        if (p.precio % 1.0 == 0.0) dinero.format(p.precio)
        else NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(p.precio)

    /** Lista en texto, para pegar en un chat o en una publicación */
    fun texto(productos: List<ProductoUnificado>): String = buildString {
        append("✨ *Disponible en AlusheStore* ✨\n\n")
        productos.forEach { append("• ${it.nombre} — *${precio(it)}*\n") }
        append("\nPregunta por el tuyo y te lo apartamos 🤍")
    }

    fun compartirTexto(context: Context, productos: List<ProductoUnificado>) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto(productos))
        }
        context.startActivity(Intent.createChooser(intent, "Compartir catálogo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Arma la imagen y la deja lista para compartir.
     * Las fotos se descargan del catálogo web; si alguna no baja, esa tarjeta
     * lleva la inicial del producto en su lugar.
     */
    suspend fun imagen(context: Context, productos: List<ProductoUnificado>): Uri = withContext(Dispatchers.IO) {
        val lista = productos.take(MAXIMO_EN_IMAGEN)
        val fotos = coroutineScope {
            lista.map { p -> async { p.urlImagen?.let { descargar(it) } } }.awaitAll()
        }

        val ancho = 1080
        val margen = 48
        val separacion = 28
        val columnas = 2
        val anchoTarjeta = (ancho - margen * 2 - separacion) / columnas
        val altoFoto = anchoTarjeta
        // Dos renglones de nombre (≈82px) más el precio, sin encimarse
        val altoTexto = 180
        val altoTarjeta = altoFoto + altoTexto
        val encabezado = 210
        val pie = 120
        val filas = (lista.size + columnas - 1) / columnas
        val alto = encabezado + filas * altoTarjeta + (filas - 1).coerceAtLeast(0) * separacion + pie

        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#F7F5F0"))

        val tinta = Color.parseColor("#1A1A1A")
        val verde = Color.parseColor("#2D7A4F")
        val gris = Color.parseColor("#7A7A6E")

        // Encabezado
        val pEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = verde; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.18f
        }
        val pTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tinta; textSize = 76f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        c.drawText("ALUSHESTORE", margen.toFloat(), 92f, pEtiqueta)
        c.drawText("Disponible hoy", margen.toFloat(), 172f, pTitulo)

        val pTarjeta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E4D6"); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        val pNombre = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tinta; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
        }
        val pPrecio = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = verde; textSize = 40f; typeface = Typeface.DEFAULT_BOLD
        }
        val pInicial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = verde; textSize = 160f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val pFondoFoto = Paint().apply { color = Color.parseColor("#EAF5EE") }

        lista.forEachIndexed { i, p ->
            val col = i % columnas
            val fila = i / columnas
            val x = margen + col * (anchoTarjeta + separacion)
            val y = encabezado + fila * (altoTarjeta + separacion)
            val tarjeta = RectF(x.toFloat(), y.toFloat(), (x + anchoTarjeta).toFloat(), (y + altoTarjeta).toFloat())

            c.drawRoundRect(tarjeta, 28f, 28f, pTarjeta)

            // Foto (recortada al centro) o inicial
            val areaFoto = Rect(x, y, x + anchoTarjeta, y + altoFoto)
            c.save()
            // Recorta a la forma de la tarjeta para respetar las esquinas redondeadas
            c.clipPath(android.graphics.Path().apply { addRoundRect(tarjeta, 28f, 28f, android.graphics.Path.Direction.CW) })
            c.clipRect(areaFoto)
            c.drawRect(areaFoto, pFondoFoto)
            val foto = fotos[i]
            if (foto != null) {
                c.drawBitmap(foto, recorteCentrado(foto, anchoTarjeta, altoFoto), areaFoto, null)
            } else {
                val inicial = p.nombre.trim().firstOrNull()?.uppercase() ?: "A"
                c.drawText(inicial, areaFoto.exactCenterX(), areaFoto.exactCenterY() + 55f, pInicial)
            }
            c.restore()
            c.drawRoundRect(tarjeta, 28f, 28f, pBorde)

            // Nombre en máximo dos renglones, y precio
            val layout = StaticLayout.Builder
                .obtain(p.nombre, 0, p.nombre.length, pNombre, anchoTarjeta - 40)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            c.save()
            c.translate((x + 20).toFloat(), (y + altoFoto + 16).toFloat())
            layout.draw(c)
            c.restore()
            c.drawText(precio(p), (x + 20).toFloat(), (y + altoTarjeta - 22).toFloat(), pPrecio)
        }

        val pPie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gris; textSize = 30f; textAlign = Paint.Align.CENTER
        }
        c.drawText("Pregunta por el tuyo · alushe.store", ancho / 2f, (alto - 52).toFloat(), pPie)

        val dir = File(context.cacheDir, "catalogo").apply { mkdirs() }
        val archivo = File(dir, "catalogo_alushe.jpg")
        FileOutputStream(archivo).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        fotos.forEach { it?.recycle() }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    }

    fun compartirImagen(context: Context, uri: Uri, productos: List<ProductoUnificado>) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, texto(productos))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir catálogo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Recorta la foto al centro para llenar el cuadro sin deformarla */
    private fun recorteCentrado(foto: Bitmap, ancho: Int, alto: Int): Rect {
        val escala = maxOf(ancho.toFloat() / foto.width, alto.toFloat() / foto.height)
        val w = (ancho / escala).toInt()
        val h = (alto / escala).toInt()
        val x = (foto.width - w) / 2
        val y = (foto.height - h) / 2
        return Rect(x, y, x + w, y + h)
    }

    /** Baja una foto reducida. null si no hay red o la URL no sirve. */
    private fun descargar(url: String): Bitmap? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { entrada ->
                val bytes = entrada.readBytes()
                val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
                // Basta con ~600px por lado para una tarjeta de 500px
                var muestra = 1
                while (opciones.outWidth / (muestra * 2) >= 600 && opciones.outHeight / (muestra * 2) >= 600) muestra *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = muestra
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}
