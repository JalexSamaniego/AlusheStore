package com.alushestore.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alushestore.data.local.MetasConfig
import com.alushestore.domain.AvanceMeta
import com.alushestore.domain.CajaDelDia
import com.alushestore.domain.ProgresoMetas
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*

// ── Meta de la base ──────────────────────────────────────────────────────────────

/**
 * Una sola tarjeta delgada con la barra de avance. Las metas por persona,
 * si hay, van en un renglón chico debajo para no ocupar más espacio.
 */
@Composable
fun MetaProgresoCard(progreso: ProgresoMetas, onClick: () -> Unit) {
    val principal = progreso.general ?: progreso.porVendedor.firstOrNull() ?: return
    val secundarias = if (progreso.general != null) progreso.porVendedor else progreso.porVendedor.drop(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .clickable { onClick() },
        shape = ShapeCard,
        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    principal.etiqueta.uppercase(),
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = AlusheInkLight,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${principal.porcentaje}%",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (principal.fraccion >= 1.0) AlusheGreenDark else AlusheInk
                )
            }

            BarraAvance(principal.fraccion)

            Text(
                if (principal.falta > 0)
                    "${principal.vendido.toMXN()} de ${principal.meta.toMXN()} · faltan ${principal.falta.toMXN()}"
                else
                    "¡Meta cumplida! ${principal.vendido.toMXN()} de ${principal.meta.toMXN()} 🎉",
                fontFamily = DmSans,
                fontSize = 12.sp,
                color = AlusheInkMid
            )

            if (secundarias.isNotEmpty()) {
                Text(
                    secundarias.joinToString("  ·  ") { "${it.etiqueta} ${it.porcentaje}%" },
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = AlusheGreenDark
                )
            }
        }
    }
}

@Composable
private fun BarraAvance(fraccion: Double) {
    val animada by animateFloatAsState(
        targetValue = fraccion.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(600),
        label = "avanceMeta"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(ShapePill)
            .background(AlusheCreamDark)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animada)
                .fillMaxHeight()
                .clip(ShapePill)
                .background(AlusheGreen)
        )
    }
}

/** Fija o quita las metas. Dejar un campo vacío quita esa meta. */
@Composable
fun MetasDialog(
    config: MetasConfig,
    nombreBase: String,
    vendedores: List<String>,
    onDismiss: () -> Unit,
    onGuardar: (Double?, Map<String, Double?>) -> Unit
) {
    var mensual by remember { mutableStateOf(config.metaMensual?.let { formatoCampo(it) } ?: "") }
    val porPersona = remember {
        mutableStateMapOf<String, String>().apply {
            vendedores.forEach { put(it, config.porVendedor[it]?.let { m -> formatoCampo(m) } ?: "") }
        }
    }
    var verPorPersona by remember { mutableStateOf(config.porVendedor.isNotEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AlusheWhite,
        shape = ShapeCardLg,
        title = {
            Text("Meta de la base", fontFamily = PlayfairDisplay, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = AlusheInk)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cuenta sólo las ventas de $nombreBase. Cada base lleva su propia meta. Déjalo vacío para quitarla.",
                    fontFamily = DmSans, fontSize = 12.sp, color = AlusheInkMid
                )
                CampoMonto("Meta del negocio", mensual) { mensual = it }

                if (verPorPersona) {
                    vendedores.forEach { nombre ->
                        CampoMonto("Meta de $nombre", porPersona[nombre].orEmpty()) { porPersona[nombre] = it }
                    }
                } else {
                    Text(
                        "+ Meta por persona",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AlusheGreenDark,
                        modifier = Modifier.clip(ShapePill).clickable { verPorPersona = true }.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            AlusheButton(
                text = "Guardar",
                onClick = {
                    onGuardar(
                        mensual.aMonto(),
                        porPersona.mapValues { (_, v) -> if (verPorPersona) v.aMonto() else null }
                    )
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", fontFamily = Montserrat, color = AlusheInkMid)
            }
        }
    )
}

@Composable
private fun CampoMonto(etiqueta: String, valor: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta, fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
        prefix = { Text("$ ", fontFamily = DmSans) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = ShapeCard,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AlusheGreen,
            focusedLabelColor = AlusheGreen,
            cursorColor = AlusheGreen
        )
    )
}

private fun formatoCampo(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

private fun String.aMonto(): Double? =
    trim().replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

// ── Caja del día ──────────────────────────────────────────────────────────────

/**
 * Corte de caja de hoy: dónde está el dinero que entró. Sirve para cuadrar
 * al final del día: el efectivo es lo que debe haber en la caja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CajaDelDiaSheet(caja: CajaDelDia, onDismiss: () -> Unit) {
    var fondoInicial by remember { mutableStateOf("") }
    val fondo = fondoInicial.aMonto() ?: 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AlusheCream,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(ShapePill)
                    .background(AlusheBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text("Caja de hoy", fontFamily = PlayfairDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AlusheInk)
                Text(
                    "${caja.numVentas} ${if (caja.numVentas == 1) "venta" else "ventas"} · ${caja.total.toMXN()}",
                    fontFamily = DmSans, fontSize = 13.sp, color = AlusheInkMid
                )
            }

            Card(
                shape = ShapeCardLg,
                colors = CardDefaults.cardColors(containerColor = AlusheWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RenglonCaja("Efectivo", caja.efectivo, destacado = true)
                    RenglonCaja("Transferencia", caja.transferencia)
                    RenglonCaja("Tarjeta", caja.tarjeta)
                    if (caja.sinEspecificar > 0) RenglonCaja("Sin especificar", caja.sinEspecificar, tenue = true)
                }
            }

            // Para cuadrar la caja física: lo que había al abrir + el efectivo de hoy
            OutlinedTextField(
                value = fondoInicial,
                onValueChange = { fondoInicial = it },
                label = { Text("Fondo con el que abriste (opcional)", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                prefix = { Text("$ ", fontFamily = DmSans) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = ShapeCard,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite,
                    focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCard)
                    .background(AlusheGreen)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Debe haber en caja",
                    fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = AlusheWhite, modifier = Modifier.weight(1f)
                )
                Text(
                    (fondo + caja.efectivo).toMXN(),
                    fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = AlusheWhite
                )
            }

            if (caja.sinEspecificar > 0) {
                Text(
                    "Las ventas sin método (las de antes de esta versión o del widget) no se cuentan como efectivo.",
                    fontFamily = DmSans, fontSize = 11.sp, color = AlusheInkLight
                )
            }
        }
    }
}

@Composable
private fun RenglonCaja(etiqueta: String, monto: Double, destacado: Boolean = false, tenue: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            etiqueta,
            fontFamily = DmSans,
            fontSize = 14.sp,
            color = if (tenue) AlusheInkLight else AlusheInkMid,
            modifier = Modifier.weight(1f)
        )
        Text(
            monto.toMXN(),
            fontFamily = Montserrat,
            fontWeight = if (destacado) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = if (destacado) 17.sp else 14.sp,
            color = when {
                tenue -> AlusheInkLight
                destacado -> AlusheGreenDark
                else -> AlusheInk
            }
        )
    }
}
