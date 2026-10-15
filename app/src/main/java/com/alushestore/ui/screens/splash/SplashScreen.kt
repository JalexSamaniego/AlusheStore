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
        animationSpec = tween(durationMillis = 300),
        label = "splash_bg",
        finishedListener = { onSplashComplete() }
    )

    // ── Secuencia de animación ────────────────────────────────────────────────
    LaunchedEffect(Unit) {

        // Fase 1 — logo entra con scale (overshoot) + fade, 600ms en paralelo
        coroutineScope {
            launch {
                logoAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500)
                )
            }
            launch {
                logoScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }

        delay(150)

        // Fase 2 — punto verde aparece y pulsa dos veces (latido), ~400ms c/u
        coroutineScope {
            launch { dotAlpha.animateTo(1f, tween(120)) }
            launch { dotScale.animateTo(1f, tween(120)) }
        }

        // Latido 1
        dotScale.animateTo(1.5f, tween(160))
        dotScale.animateTo(0.85f, tween(160))
        // Latido 2
        dotScale.animateTo(1.5f, tween(160))
        dotScale.animateTo(0.85f, tween(160))

        // Punto desaparece
        coroutineScope {
            launch { dotAlpha.animateTo(0f, tween(200)) }
            launch { dotScale.animateTo(0.3f, tween(200)) }
        }

        delay(80)

        // Fase 3 — fondo hace fade a crema; onSplashComplete() se llama
        // al terminar via finishedListener del animateColorAsState
        bgTransition = true
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
