package com.alushestore.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── Shapes oficiales de la marca ──────────────────────────────────────────────
val ShapeCard    = RoundedCornerShape(12.dp)
val ShapeCardLg  = RoundedCornerShape(16.dp)
val ShapePill    = RoundedCornerShape(50.dp)
val ShapeChip    = RoundedCornerShape(8.dp)

// ── Formateadores ─────────────────────────────────────────────────────────────

fun Double.toMXN(): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    return fmt.format(this)
}

fun Long.toFechaCorta(): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale("es", "MX"))
    return sdf.format(Date(this))
}

fun Long.toFechaLarga(): String {
    val sdf = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "MX"))
    return sdf.format(Date(this))
}

// ── Micro-interacciones: Bounce Effect ────────────────────────────────────────

@Composable
fun Modifier.bounce(scaleDown: Float = 0.96f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceSpring"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Modifier.bounceClickable(
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceClickSpring"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = rememberRipple(color = AlusheGreen.copy(alpha = 0.15f)),
            onClick = onClick
        )
}

// ── Animated Money Text (Efecto Ticker / Odómetro) ─────────────────────────────

@Composable
fun AnimatedMoneyText(
    monto: Double,
    modifier: Modifier = Modifier,
    fontFamily: androidx.compose.ui.text.font.FontFamily = Montserrat,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontSize: androidx.compose.ui.unit.TextUnit = 36.sp,
    color: Color = AlusheGreen,
    letterSpacing: androidx.compose.ui.unit.TextUnit = (-0.5).sp
) {
    val formatted = monto.toMXN()
    AnimatedContent(
        targetState = formatted,
        transitionSpec = {
            (slideInVertically { height -> height / 2 } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically { height -> -height / 2 } + fadeOut(tween(180)))
        },
        label = "animatedMoneyOdometer"
    ) { text ->
        Text(
            text = text,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            color = color,
            letterSpacing = letterSpacing,
            modifier = modifier
        )
    }
}

// ── SlidingSegmentedTabs (Control deslizante estilo iOS / Flo) ─────────────────

@Composable
fun SlidingSegmentedTabs(
    opciones: List<String>,
    seleccionadoIndex: Int,
    onSeleccionar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapePill)
            .background(AlusheCreamDark)
            .border(1.dp, AlusheBorder, ShapePill)
            .padding(4.dp)
    ) {
        val totalWidth = maxWidth
        val tabWidth = totalWidth / opciones.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * seleccionadoIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "tabIndicatorOffset"
        )

        // Píldora deslizante activa
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(3.dp, ShapePill, ambientColor = Color(0x18000000), spotColor = Color(0x18000000))
                .clip(ShapePill)
                .background(AlusheInk)
        )

        // Botones de las pestañas
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            opciones.forEachIndexed { index, texto ->
                val activo = index == seleccionadoIndex
                val textColor by animateColorAsState(
                    targetValue = if (activo) AlusheCream else AlusheInkMid,
                    animationSpec = tween(180),
                    label = "tabTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ShapePill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSeleccionar(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = texto,
                        fontFamily = Montserrat,
                        fontWeight = if (activo) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = textColor
                    )
                }
            }
        }
    }
}

// ── TotalCard de Lujo (Fondo degradado sutil con borde esmeralda) ──────────────

@Composable
fun TotalCard(
    titulo: String,
    total: Double,
    cantidad: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(12.dp, ShapeCardLg, ambientColor = Color(0x1A000000), spotColor = AlusheGreen.copy(alpha = 0.28f))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = ShapeCardLg,
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheGreen.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AlusheInk,
                                Color(0xFF142017),
                                Color(0xFF0F1711)
                            )
                        )
                    )
                    .padding(22.dp)
            ) {
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
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AlusheGreen)
                        )
                        Text(
                            text = titulo.uppercase(),
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = AlusheCream.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    AnimatedMoneyText(
                        monto = total,
                        fontSize = 38.sp,
                        color = AlusheGreen,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(Modifier.height(6.dp))

                    AnimatedContent(
                        targetState = cantidad,
                        label = "cantidadCounter"
                    ) { count ->
                        Text(
                            text = "$count ${if (count == 1) "venta" else "ventas"}",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = AlusheCream.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

// ── StatCard — con sombra suave y bounce al tacto ─────────────────────────────

@Composable
fun StatCard(
    label: String,
    valor: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    esDestacada: Boolean = false
) {
    val bgColor = if (esDestacada) AlusheGreen else AlusheWhite
    val textColor = if (esDestacada) AlusheWhite else accentColor
    val labelColor = if (esDestacada) AlusheWhite.copy(alpha = 0.8f) else AlusheInkLight

    Box(
        modifier = modifier
            .bounce()
            .shadow(
                elevation = 3.dp, shape = ShapeCard,
                ambientColor = Color(0x14000000), spotColor = Color(0x14000000)
            )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = ShapeCard,
            elevation = CardDefaults.cardElevation(0.dp),
            border = if (!esDestacada)
                androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
            else null
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                Text(
                    text = label.uppercase(),
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    color = labelColor,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = valor,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── VentaCard — card con spring feedback y borde brand ────────────────────────

@Composable
fun VentaCard(
    venta: VentaEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bounce()
            .shadow(3.dp, ShapeCard, ambientColor = Color(0x10000000), spotColor = Color(0x10000000))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit() },
            colors = CardDefaults.cardColors(containerColor = AlusheWhite),
            shape = ShapeCard,
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inicial del artículo
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ShapeChip)
                        .background(AlusheCreamDark)
                        .border(1.dp, AlusheBorder, ShapeChip),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = venta.articulo.firstOrNull()?.uppercase() ?: "V",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = venta.articulo,
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = venta.fecha.toFechaCorta(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = venta.monto.toMXN(),
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.width(2.dp))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = AlusheInkMid,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = AlusheRed.copy(alpha = 0.55f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = ShapeCardLg,
            title = {
                Text(
                    "¿Eliminar venta?",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Se eliminará \"${venta.articulo}\" por ${venta.monto.toMXN()}.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AlusheRed)
                ) {
                    Text("Eliminar", fontFamily = Montserrat, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar", fontFamily = Montserrat)
                }
            }
        )
    }
}

// ── GreenBadge — chip/badge verde estilo brand ────────────────────────────────

@Composable
fun GreenBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(AlusheGreenSurface)
            .border(1.dp, AlusheGreen.copy(alpha = 0.3f), ShapePill)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = Montserrat,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            color = AlusheGreenDark,
            letterSpacing = 0.8.sp
        )
    }
}

// ── EmptyState con Animación de Levitación Suave ──────────────────────────────

@Composable
fun EmptyState(
    titulo: String,
    subtitulo: String,
    emoji: String = "🛍️",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStatePulse")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyFloat"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 44.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .offset(y = floatOffset.dp)
                .size(74.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x0A000000), spotColor = Color(0x0A000000))
                .clip(RoundedCornerShape(20.dp))
                .background(AlusheWhite)
                .border(1.dp, AlusheBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 34.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = titulo,
            fontFamily = Montserrat,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ── AlusheButton — con Spring Bounce táctil ───────────────────────────────────

@Composable
fun AlusheButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variante: ButtonVariante = ButtonVariante.VERDE
) {
    val baseModifier = modifier.bounce()

    when (variante) {
        ButtonVariante.VERDE -> Button(
            onClick = onClick,
            modifier = baseModifier,
            enabled = enabled,
            shape = ShapePill,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlusheGreen,
                contentColor = AlusheWhite,
                disabledContainerColor = AlusheBorder
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
        ) {
            Text(text, fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        ButtonVariante.NEGRO -> Button(
            onClick = onClick,
            modifier = baseModifier,
            enabled = enabled,
            shape = ShapePill,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlusheInk,
                contentColor = AlusheWhite
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
        ) {
            Text(text, fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        ButtonVariante.OUTLINE -> OutlinedButton(
            onClick = onClick,
            modifier = baseModifier,
            enabled = enabled,
            shape = ShapePill,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AlusheInk),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlusheInk)
        ) {
            Text(text, fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

enum class ButtonVariante { VERDE, NEGRO, OUTLINE }
