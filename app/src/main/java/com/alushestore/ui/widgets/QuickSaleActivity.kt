package com.alushestore.ui.widgets

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.alushestore.data.local.ActiveUserManager
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.ui.components.ShapeCard
import com.alushestore.ui.components.ShapeCardLg
import com.alushestore.ui.components.ShapePill
import com.alushestore.ui.components.bounce
import com.alushestore.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Actividad flotante ultra ágil para registrar ventas directamente desde
 * el Widget de pantalla de inicio o App Shortcut sin pasar por la navegación completa.
 */
class QuickSaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userManager = ActiveUserManager.getInstance(this)

        setContent {
            AlusheStoreTheme {
                var baseActiva by remember { mutableStateOf<BaseEntity?>(null) }
                var concepto by remember { mutableStateOf("") }
                var montoStr by remember { mutableStateOf("") }
                var vendedor by remember { mutableStateOf(userManager.vendedorActivo.value) }
                var errorConcepto by remember { mutableStateOf(false) }
                var errorMonto by remember { mutableStateOf(false) }
                var isSaving by remember { mutableStateOf(false) }

                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    val db = AlusheDatabase.getDatabase(this@QuickSaleActivity)
                    baseActiva = withContext(Dispatchers.IO) {
                        db.baseDao().getBaseActivaSync()
                    }
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { if (!isSaving) finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clickable(enabled = false) {},
                        shape = ShapeCardLg,
                        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Cabecera del diálogo flotante
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(24.dp)
                                            .clip(ShapePill)
                                            .background(AlusheGreen)
                                    )
                                    Column {
                                        Text(
                                            text = "Venta Rápida ⚡",
                                            fontFamily = PlayfairDisplay,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = AlusheInk
                                        )
                                        Text(
                                            text = "Base: ${baseActiva?.nombre ?: "Sin base activa"}",
                                            fontFamily = DmSans,
                                            fontSize = 11.sp,
                                            color = if (baseActiva != null) AlusheGreenDark else AlusheRed
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { finish() },
                                    modifier = Modifier.size(28.dp).bounce()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = AlusheInkLight
                                    )
                                }
                            }

                            // Botones de monto sugerido rápido
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(20, 50, 100, 200).forEach { rapido ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(ShapePill)
                                            .background(AlusheGreenSurface)
                                            .clickable {
                                                val actual = montoStr.toDoubleOrNull() ?: 0.0
                                                val nuevo = if (actual == 0.0) rapido.toDouble() else actual + rapido
                                                montoStr = if (nuevo % 1.0 == 0.0) nuevo.toInt().toString() else nuevo.toString()
                                                errorMonto = false
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$$rapido",
                                            fontFamily = Montserrat,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = AlusheGreenDark
                                        )
                                    }
                                }
                            }

                            // Input Concepto
                            OutlinedTextField(
                                value = concepto,
                                onValueChange = {
                                    concepto = it
                                    errorConcepto = false
                                },
                                label = { Text("Concepto o Artículo") },
                                placeholder = { Text("Ej: Aretes perla, Blusa, etc.") },
                                singleLine = true,
                                isError = errorConcepto,
                                supportingText = if (errorConcepto) {
                                    { Text("Escribe un concepto", color = AlusheRed, fontSize = 11.sp) }
                                } else null,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                shape = ShapeCard,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AlusheGreen,
                                    unfocusedBorderColor = AlusheBorder,
                                    errorBorderColor = AlusheRed
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )

                            // Input Monto ($)
                            OutlinedTextField(
                                value = montoStr,
                                onValueChange = {
                                    montoStr = it
                                    errorMonto = false
                                },
                                label = { Text("Monto ($)") },
                                placeholder = { Text("0.00") },
                                singleLine = true,
                                isError = errorMonto,
                                supportingText = if (errorMonto) {
                                    { Text("Ingresa un monto válido mayor a 0", color = AlusheRed, fontSize = 11.sp) }
                                } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = ShapeCard,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AlusheGreen,
                                    unfocusedBorderColor = AlusheBorder,
                                    errorBorderColor = AlusheRed
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Selector de Vendedor Activo (1 toque)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Vendedor registrado:",
                                    fontFamily = DmSans,
                                    fontSize = 12.sp,
                                    color = AlusheInkMid
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(ShapePill)
                                        .background(AlusheGreen.copy(alpha = 0.15f))
                                        .clickable {
                                            userManager.alternarVendedor()
                                            vendedor = userManager.vendedorActivo.value
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = AlusheGreen,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = vendedor,
                                            fontFamily = Montserrat,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = AlusheGreen
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // Botones Cancelar y Guardar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { finish() },
                                    shape = ShapePill,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Cancelar",
                                        fontFamily = Montserrat,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = AlusheInkMid
                                    )
                                }

                                Button(
                                    onClick = {
                                        val montoVal = montoStr.toDoubleOrNull()
                                        if (concepto.isBlank()) {
                                            errorConcepto = true
                                            return@Button
                                        }
                                        if (montoVal == null || montoVal <= 0.0) {
                                            errorMonto = true
                                            return@Button
                                        }
                                        val targetBase = baseActiva
                                        if (targetBase == null) {
                                            Toast.makeText(this@QuickSaleActivity, "No hay base activa para registrar la venta", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }

                                        isSaving = true
                                        guardarVenta(
                                            baseId = targetBase.id,
                                            articulo = concepto.trim(),
                                            monto = montoVal,
                                            vendedor = vendedor
                                        )
                                    },
                                    enabled = !isSaving,
                                    shape = ShapePill,
                                    colors = ButtonDefaults.buttonColors(containerColor = AlusheGreen),
                                    modifier = Modifier.weight(1.3f).bounce()
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(color = AlusheWhite, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text(
                                            text = "Cobrar ✓",
                                            fontFamily = Montserrat,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = AlusheWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun guardarVenta(baseId: Long, articulo: String, monto: Double, vendedor: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AlusheDatabase.getDatabase(this@QuickSaleActivity)
                val nuevaVenta = VentaEntity(
                    baseId = baseId,
                    articulo = articulo,
                    monto = monto,
                    fecha = System.currentTimeMillis(),
                    notas = "Registrado desde Venta Rápida",
                    vendedor = vendedor
                )
                db.ventaDao().insertVenta(nuevaVenta)

                // Actualizar widgets de pantalla de inicio al instante
                AlusheWidgetUpdater.actualizarWidgets(this@QuickSaleActivity)

                // Vibración háptica
                dispararVibracion()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QuickSaleActivity, "Venta registrada: $$monto ✓", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QuickSaleActivity, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun dispararVibracion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        } catch (_: Exception) {}
    }
}
