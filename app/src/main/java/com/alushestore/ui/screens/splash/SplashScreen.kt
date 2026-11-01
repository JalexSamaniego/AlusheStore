package com.alushestore.ui.screens.splash

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alushestore.R
import com.alushestore.ui.theme.AlusheCream
import com.alushestore.ui.theme.AlusheGreen
import com.alushestore.ui.theme.AlusheInk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Tope de espera para la coreografía de entrada antes de ceder el paso a Home */
private const val ANIM_TIMEOUT_MS = 1200L

/** Duración de la transición de fondo a crema antes de navegar */
private const val BG_FADE_MS = 300L

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {

    // ── Animatables ───────────────────────────────────────────────────────────
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }
    val dotScale  = remember { Animatable(0f) }
    val dotAlpha  = remember { Animatable(0f) }

    // El fondo se anima con un state booleano para simplificar el Color Animatable
    var bgTransition by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (bgTransition) AlusheCream else AlusheInk,
        animationSpec = tween(durationMillis = BG_FADE_MS.toInt()),
        label = "splash_bg"
    )

    // La salida del splash nunca debe depender de que una animación termine:
    // con las animaciones del sistema desactivadas jamás se completan y la app
    // se quedaría atorada aquí para siempre.
    val salidaDisparada = remember { mutableStateOf(false) }

    // ── Secuencia de animación ────────────────────────────────────────────────
    LaunchedEffect(Unit) {

        // La coreografía es decorativa: si el dispositivo va lento o tiene las
        // animaciones apagadas, se abandona al vencer el tope y se sigue de largo.
        withTimeoutOrNull(ANIM_TIMEOUT_MS) {

            // Fase 1 — logo entra con scale + fade rápido (260ms)
            coroutineScope {
                launch {
                    logoAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 260)
                    )
                }
                launch {
                    logoScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }

            // Fase 2 — punto verde pulsa elegantemente una sola vez (220ms)
            coroutineScope {
                launch { dotAlpha.animateTo(1f, tween(80)) }
                launch { dotScale.animateTo(1.3f, tween(110)) }
            }
            dotScale.animateTo(0.9f, tween(110))
        }

        // Si la coreografía se abandonó a medias, el logo se deja visible de golpe
        // para que nunca se vea una pantalla vacía.
        logoAlpha.snapTo(1f)
        logoScale.snapTo(1f)

        // Fase 3 — transición ágil a crema y salida garantizada
        bgTransition = true
        delay(BG_FADE_MS)

        if (!salidaDisparada.value) {
            salidaDisparada.value = true
            onSplashComplete()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo_alushe),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha  = logoAlpha.value
                    }
            )

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = dotScale.value
                        scaleY = dotScale.value
                        alpha  = dotAlpha.value
                    }
                    .clip(CircleShape)
                    .background(AlusheGreen)
            )
        }
    }
}
