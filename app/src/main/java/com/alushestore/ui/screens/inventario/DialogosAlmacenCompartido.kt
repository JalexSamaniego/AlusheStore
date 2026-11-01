package com.alushestore.ui.screens.inventario

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alushestore.domain.ProductoUnificado
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*

/**
 * Emparejamiento único del teléfono con el almacén compartido.
 *
 * Se escribe una sola vez por teléfono. Después sólo queda guardado el token de
 * renovación, cifrado, y la sesión se mantiene sola: no vuelve a pedir nada.
 */
@Composable
fun EmparejarDialog(
    emparejando: Boolean,
    onEmparejar: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!emparejando) onDismiss() }) {
        Surface(shape = ShapeCardLg, color = AlusheCream) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    "ALMACÉN COMPARTIDO",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = AlusheInkLight
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Emparejar este teléfono",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Se hace una sola vez. Después la sesión se renueva sola y no " +
                        "vuelve a pedirte nada. Tu contraseña no se guarda.",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = AlusheInkMid
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo", fontFamily = DmSans) },
                    singleLine = true,
                    enabled = !emparejando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña", fontFamily = DmSans) },
                    singleLine = true,
                    enabled = !emparejando,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlusheButton(
                        text = "Cancelar",
                        onClick = onDismiss,
                        enabled = !emparejando,
                        variante = ButtonVariante.OUTLINE,
                        modifier = Modifier.weight(1f)
                    )
                    AlusheButton(
                        text = if (emparejando) "Emparejando…" else "Emparejar",
                        onClick = { onEmparejar(correo, password) },
                        enabled = !emparejando && correo.isNotBlank() && password.isNotBlank(),
                        variante = ButtonVariante.VERDE,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Administración de un producto del catálogo compartido: stock, precio y costo.
 *
 * Aquí **no se vende**. Vender es cosa del POS en la pantalla principal; esta
 * pantalla es para mantener el catálogo.
 *
 * El stock se cambia como *ajuste*, o sea que se fija al número que escribas,
 * y queda en la bitácora con tu nombre.
 */
@Composable
fun AdminProductoWebDialog(
    producto: ProductoUnificado,
    guardando: Boolean,
    onGuardar: (stock: Int?, precio: Double?, costo: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var stock by remember { mutableStateOf(producto.stockDisponible.toString()) }
    var precio by remember { mutableStateOf(formatoLimpio(producto.precio)) }
    var costo by remember { mutableStateOf(formatoLimpio(producto.costo)) }

    val stockNuevo = stock.trim().toIntOrNull()
    val precioNuevo = precio.trim().replace(",", "").toDoubleOrNull()
    val costoNuevo = costo.trim().replace(",", "").toDoubleOrNull()

    val hayCambios = (stockNuevo != null && stockNuevo != producto.stockDisponible) ||
        (precioNuevo != null && precioNuevo != producto.precio) ||
        (costoNuevo != null && costoNuevo != producto.costo)

    val datosValidos = stockNuevo != null && stockNuevo >= 0 &&
        precioNuevo != null && precioNuevo >= 0 &&
        costoNuevo != null && costoNuevo >= 0

    Dialog(onDismissRequest = { if (!guardando) onDismiss() }) {
        Surface(shape = ShapeCardLg, color = AlusheCream) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.CloudQueue, null, Modifier.size(13.dp), tint = AlusheInkLight)
                    Text(
                        "EDITAR EN EL ALMACÉN COMPARTIDO",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.1.sp,
                        color = AlusheInkLight
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    producto.nombre,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AlusheInk
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it.filter { c -> c.isDigit() } },
                    label = { Text("Stock", fontFamily = DmSans) },
                    supportingText = {
                        Text(
                            "Hoy hay ${producto.stockDisponible}. Se guarda como ajuste.",
                            fontFamily = DmSans,
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    enabled = !guardando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = precio,
                        onValueChange = { precio = it },
                        label = { Text("Precio", fontFamily = DmSans) },
                        singleLine = true,
                        enabled = !guardando,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costo,
                        onValueChange = { costo = it },
                        label = { Text("Costo", fontFamily = DmSans) },
                        singleLine = true,
                        enabled = !guardando,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (precioNuevo != null && costoNuevo != null && costoNuevo > 0) {
                    Spacer(Modifier.height(8.dp))
                    val margen = (((precioNuevo - costoNuevo) / costoNuevo) * 100).toInt()
                    Text(
                        "Margen sobre costo: $margen%",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (margen >= 0) AlusheGreenDark else AlusheRed
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "El cambio se aplica al catálogo que ve el sitio y los dos teléfonos.",
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    color = AlusheInkLight
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlusheButton(
                        text = "Cancelar",
                        onClick = onDismiss,
                        enabled = !guardando,
                        variante = ButtonVariante.OUTLINE,
                        modifier = Modifier.weight(1f)
                    )
                    AlusheButton(
                        text = if (guardando) "Guardando…" else "Guardar",
                        onClick = { onGuardar(stockNuevo, precioNuevo, costoNuevo) },
                        enabled = !guardando && hayCambios && datosValidos,
                        variante = ButtonVariante.VERDE,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Quita decimales vacíos para que el campo no muestre "35.0" */
private fun formatoLimpio(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
