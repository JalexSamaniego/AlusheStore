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

    // Paleta de Alushe (ui/theme/Color.kt), igual en la cuadrícula y en el estado
    private val CREMA = Color.parseColor("#F6F5CA")
    private val CREMA_OSCURA = Color.parseColor("#E8E7A8")
    private val VERDE = Color.parseColor("#25D366")
    private val TINTA = Color.parseColor("#1A1A18")
    private val TINTA_SUAVE = Color.parseColor("#7A7A6E")
    private val BORDE = Color.parseColor("#E5E4D6")

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
        if (productos.size == 1) return@withContext estado(context, productos.first())
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
        c.drawColor(CREMA)

        val tinta = TINTA
        val verde = VERDE
        val gris = TINTA_SUAVE

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
            color = BORDE; style = Paint.Style.STROKE; strokeWidth = 2f
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
        val pFondoFoto = Paint().apply { color = CREMA_OSCURA }

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

        fotos.forEach { it?.recycle() }
        guardar(context, bmp)
    }

    /**
     * Un solo artículo, a pantalla completa para un estado de WhatsApp (9:16).
     *
     * La foto llena la parte de arriba y abajo va una franja crema con el
     * nombre y el precio. El texto se queda lejos de las orillas, porque
     * WhatsApp encima su barra de avance arriba y la de "Responder" abajo.
     */
    private fun estado(context: Context, p: ProductoUnificado): Uri {
        val ancho = 1080
        val alto = 1920
        val altoFoto = 1300
        // Lo que tape WhatsApp abajo; el texto termina antes
        val zonaSeguraAbajo = 200

        val crema = CREMA
        val cremaOscura = CREMA_OSCURA
        val verde = VERDE
        val tinta = TINTA
        val tintaSuave = TINTA_SUAVE

        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(crema)

        // Foto a lo ancho, recortada al centro
        val areaFoto = Rect(0, 0, ancho, altoFoto)
        c.drawRect(areaFoto, Paint().apply { color = cremaOscura })
        val foto = p.urlImagen?.let { descargar(it, lado = 1200) }
        if (foto != null) {
            c.drawBitmap(foto, recorteCentrado(foto, ancho, altoFoto), areaFoto, Paint(Paint.FILTER_BITMAP_FLAG))
            foto.recycle()
        } else {
            val pInicial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = verde; textSize = 420f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val inicial = p.nombre.trim().firstOrNull()?.uppercase() ?: "A"
            c.drawText(inicial, ancho / 2f, altoFoto / 2f + 150f, pInicial)
        }
        // Filo verde entre la foto y la franja
        c.drawRect(0f, altoFoto.toFloat(), ancho.toFloat(), altoFoto + 10f, Paint().apply { color = verde })

        val pEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = verde; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f; textAlign = Paint.Align.CENTER
        }
        val pNombre = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tinta; textSize = 64f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val pPrecio = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = verde; textSize = 112f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val pPie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintaSuave; textSize = 32f; textAlign = Paint.Align.CENTER
        }
        val nombre = StaticLayout.Builder
            .obtain(p.nombre, 0, p.nombre.length, pNombre, ancho - 160)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        // El bloque de texto se centra en el espacio libre de la franja
        val altoEtiqueta = 34
        val bloque = altoEtiqueta + 28 + nombre.height + 24 + 112 + 40 + 32
        val inicioLibre = altoFoto + 10
        val finLibre = alto - zonaSeguraAbajo
        var y = inicioLibre + ((finLibre - inicioLibre - bloque) / 2).coerceAtLeast(24)

        y += altoEtiqueta
        c.drawText("ALUSHESTORE", ancho / 2f, y.toFloat(), pEtiqueta)
        y += 28
        c.save()
        c.translate(80f, y.toFloat())
        nombre.draw(c)
        c.restore()
        y += nombre.height + 24 + 100
        c.drawText(precio(p), ancho / 2f, y.toFloat(), pPrecio)
        y += 40 + 32
        c.drawText("Pregunta por el tuyo · alushe.store", ancho / 2f, y.toFloat(), pPie)

        return guardar(context, bmp)
    }

    /** JPEG al 95 en caché, listo para compartir. Libera el bitmap. */
    private fun guardar(context: Context, bmp: Bitmap): Uri {
        val dir = File(context.cacheDir, "catalogo").apply { mkdirs() }
        val archivo = File(dir, "catalogo_alushe.jpg")
        FileOutputStream(archivo).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
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
    private fun descargar(url: String, lado: Int = 600): Bitmap? = try {
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
                // Se reduce sin bajar de [lado] px: 600 para una tarjeta, 1200 para un estado
                var muestra = 1
                while (opciones.outWidth / (muestra * 2) >= lado && opciones.outHeight / (muestra * 2) >= lado) muestra *= 2
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
