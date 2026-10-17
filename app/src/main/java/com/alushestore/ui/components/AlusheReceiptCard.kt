package com.alushestore.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Picture
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.alushestore.data.local.entities.ApartadoConAbonos
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.ui.theme.*
import java.io.File
import java.io.FileOutputStream

// ── Modificador para capturar un Composable a un Picture de Android ───────────

fun Modifier.recordToPicture(picture: Picture): Modifier = this.drawWithCache {
    val width = size.width.toInt().coerceAtLeast(1)
    val height = size.height.toInt().coerceAtLeast(1)
    onDrawWithContent {
        val pictureCanvas = Canvas(picture.beginRecording(width, height))
        // Pintar fondo sólido crema Alushe (#F7F5F0) explícito para evitar transparencia
        pictureCanvas.drawRect(
            androidx.compose.ui.geometry.Rect(0f, 0f, width.toFloat(), height.toFloat()),
            androidx.compose.ui.graphics.Paint().apply {
                color = androidx.compose.ui.graphics.Color(0xFFF7F5F0)
            }
        )
        CanvasDrawScope().draw(this, layoutDirection, pictureCanvas, size) {
            this@onDrawWithContent.drawContent()
        }
        picture.endRecording()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPicture(picture)
        }
    }
}

// ── Funciones de Utilidad para Bitmap y Compartir ──────────────────────────────

fun pictureToBitmap(picture: Picture): Bitmap {
    val width = picture.width.takeIf { it > 0 } ?: 1080
    val height = picture.height.takeIf { it > 0 } ?: 1920
    // Siempre usar un Bitmap ARGB_8888 de software.
    // NUNCA usar Bitmap.createBitmap(picture) en API 28+ porque genera un HARDWARE bitmap
    // cuyo búfer de píxeles no puede leerse al comprimir o compartir, produciendo imágenes negras.
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    // Fondo crema sólido editorial Alushe
    canvas.drawColor(android.graphics.Color.parseColor("#F7F5F0"))
    canvas.drawPicture(picture)
    return bitmap
}

fun saveReceiptBitmapToCache(
    context: Context,
    bitmap: Bitmap,
    fileName: String = "recibo_alushe.jpg"
): Uri {
    val dir = File(context.cacheDir, "recibos")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, fileName)
    FileOutputStream(file).use { out ->
        // Comprimir en JPEG de alta calidad (95%): evita cualquier canal alfa transparente que WhatsApp torne en negro
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

fun generateReceiptMessageText(cliente: ClienteEntity, item: ApartadoConAbonos): String {
    val apartado = item.apartado
    val saldoTexto = if (item.estaLiquidado) {
        "¡Tu cuenta está *TOTALMENTE LIQUIDADA*! 🎉"
    } else {
        "Tu saldo pendiente por liquidar es de *${item.saldoRestante.toMXN()}*"
    }

    val tipoTexto = if (apartado.tipo == TipoApartado.PEDIDO) "Pedido" else "Apartado"

    return """
        ✨ *COMPROBANTE DE COMPRA · ALUSHE STORE* ✨
        
        Hola *${cliente.nombre}*, te compartimos el resumen de tu $tipoTexto:
        
        🛍️ *Artículo:* ${apartado.descripcion}
        💵 *Monto total:* ${apartado.monto.toMXN()}
        ✅ *Total abonado:* ${item.totalAbonado.toMXN()}
        📌 *$saldoTexto*
        
        ¡Muchas gracias por tu preferencia y confianza! 🤍
    """.trimIndent()
}

fun shareReceiptViaWhatsApp(
    context: Context,
    imageUri: Uri,
    cliente: ClienteEntity,
    item: ApartadoConAbonos
) {
    val messageText = generateReceiptMessageText(cliente, item)
    val rawDigits = cliente.telefono.filter { it.isDigit() }
    val fullPhone = if (rawDigits.length == 10) "52$rawDigits" else rawDigits

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, messageText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage("com.whatsapp")
        if (fullPhone.isNotBlank()) {
            putExtra("jid", "$fullPhone@s.whatsapp.net")
        }
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback al selector universal
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, messageText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Compartir comprobante Alushe"
        )
        context.startActivity(chooser)
    }
}

fun shareReceiptGeneric(
    context: Context,
    imageUri: Uri,
    cliente: ClienteEntity,
    item: ApartadoConAbonos
) {
    val messageText = generateReceiptMessageText(cliente, item)
    val chooser = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, messageText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        "Compartir comprobante Alushe"
    )
    context.startActivity(chooser)
}

// ── Composable de Comprobante Visual Editorial Alushe ─────────────────────────

@Composable
fun AlusheReceiptCard(
    cliente: ClienteEntity,
    item: ApartadoConAbonos,
    modifier: Modifier = Modifier
) {
    val apartado = item.apartado
    val abonos = item.abonos

    Card(
        modifier = modifier.width(360.dp),
        colors = CardDefaults.cardColors(containerColor = AlusheCream),
        shape = ShapeCardLg,
        border = BorderStroke(1.dp, AlusheBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabecera de Marca Editorial
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(ShapePill)
                            .background(AlusheGreen)
                    )
                    Text(
                        "ALUSHE STORE",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp,
                        color = AlusheInk
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    if (apartado.tipo == TipoApartado.PEDIDO) "Comprobante de Pedido" else "Comprobante de Apartado",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = AlusheInk
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Folio #AP-${apartado.id.toString().padStart(4, '0')}",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = AlusheInkLight
                    )
                    Text("•", color = AlusheInkLight, fontSize = 10.sp)
                    Text(
                        System.currentTimeMillis().toFechaCorta(),
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheInkLight
                    )
                }
            }

            HorizontalDivider(color = AlusheBorder, thickness = 1.dp)

            // Datos del Cliente
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCard)
                    .background(AlusheWhite)
                    .border(1.dp, AlusheBorder, ShapeCard)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "CLIENTE",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = AlusheInkLight
                )
                Text(
                    cliente.nombre,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AlusheInk
                )
                if (cliente.telefono.isNotBlank()) {
                    Text(
                        "Tel: ${cliente.telefono}",
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkMid
                    )
                }
            }

            // Detalle del Artículo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCard)
                    .background(AlusheWhite)
                    .border(1.dp, AlusheBorder, ShapeCard)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (apartado.tipo == TipoApartado.PEDIDO) "PEDIDO" else "APARTADO",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        color = AlusheGreenDark
                    )
                    Text(
                        apartado.fecha.toFechaCorta(),
                        fontFamily = DmSans,
                        fontSize = 10.sp,
                        color = AlusheInkLight
                    )
                }

                Text(
                    apartado.descripcion,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AlusheInk
                )

                if (apartado.notas.isNotBlank()) {
                    Text(
                        apartado.notas,
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkMid
                    )
                }
            }

            // Historial de Abonos
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "DESGLOSE DE PAGOS",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        color = AlusheInkLight
                    )
                    Text(
                        "Total: ${apartado.monto.toMXN()}",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AlusheInk
                    )
                }

                if (abonos.isEmpty()) {
                    Text(
                        "Sin abonos registrados.",
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkLight
                    )
                } else {
                    abonos.forEach { abono ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeCard)
                                .background(AlusheWhite.copy(alpha = 0.8f))
                                .border(0.5.dp, AlusheBorder, ShapeCard)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    abono.fecha.toFechaCorta(),
                                    fontFamily = DmSans,
                                    fontSize = 11.sp,
                                    color = AlusheInkLight
                                )
                                if (abono.nota.isNotBlank()) {
                                    Text(
                                        abono.nota,
                                        fontFamily = DmSans,
                                        fontSize = 11.sp,
                                        color = AlusheInkMid
                                    )
                                }
                            }
                            Text(
                                "+ ${abono.monto.toMXN()}",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AlusheGreenDark
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Total abonado acumulado:",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = AlusheInkMid
                    )
                    Text(
                        item.totalAbonado.toMXN(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AlusheInk
                    )
                }
            }

            // Tarjeta Destacada de Saldo Pendiente
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AlusheGreenSurface),
                shape = ShapeCard,
                border = BorderStroke(1.dp, AlusheGreen.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.estaLiquidado) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = AlusheGreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "¡CUENTA TOTALMENTE LIQUIDADA!",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 0.8.sp,
                                color = AlusheGreenDark
                            )
                        }
                        Text(
                            "Saldo pendiente: $0.00 MXN",
                            fontFamily = DmSans,
                            fontSize = 12.sp,
                            color = AlusheGreenDark
                        )
                    } else {
                        Text(
                            "SALDO PENDIENTE POR LIQUIDAR",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            color = AlusheGreenDark
                        )
                        Text(
                            item.saldoRestante.toMXN(),
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = AlusheGreenDark
                        )
                    }
                }
            }

            // Pie de página de cortesía
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "¡Gracias por tu preferencia y confianza! ✨",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = AlusheInkMid,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Alushe Store · Moda & Accesorios",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    color = AlusheInkLight
                )
            }
        }
    }
}

// ── Modal de Vista Previa y Envío de Comprobante ──────────────────────────────

@Composable
fun ComprobanteDialog(
    cliente: ClienteEntity,
    item: ApartadoConAbonos,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val picture = remember { Picture() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AlusheWhite),
            border = BorderStroke(1.dp, AlusheBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Barra superior del modal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Vista previa del comprobante",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AlusheInk
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Cerrar", tint = AlusheInkMid)
                    }
                }

                // Comprobante visual capturado en memoria
                Box(modifier = Modifier.recordToPicture(picture)) {
                    AlusheReceiptCard(cliente = cliente, item = item)
                }

                // Acciones de envío
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val telefonoValido = cliente.telefono.filter { it.isDigit() }.isNotBlank()

                    AlusheButton(
                        text = if (telefonoValido) "Enviar por WhatsApp 📲" else "Compartir por WhatsApp 📲",
                        onClick = {
                            val bitmap = pictureToBitmap(picture)
                            val uri = saveReceiptBitmapToCache(
                                context,
                                bitmap,
                                "recibo_apartado_${item.apartado.id}.jpg"
                            )
                            shareReceiptViaWhatsApp(context, uri, cliente, item)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variante = ButtonVariante.VERDE
                    )

                    AlusheButton(
                        text = "Compartir imagen... 📤",
                        onClick = {
                            val bitmap = pictureToBitmap(picture)
                            val uri = saveReceiptBitmapToCache(
                                context,
                                bitmap,
                                "recibo_apartado_${item.apartado.id}.jpg"
                            )
                            shareReceiptGeneric(context, uri, cliente, item)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variante = ButtonVariante.OUTLINE
                    )
                }
            }
        }
    }
}
