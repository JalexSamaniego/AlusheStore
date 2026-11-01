package com.alushestore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alushestore.data.local.entities.CorteConVendedores
import com.alushestore.ui.theme.*

/**
 * Estado de cuenta de un periodo cerrado.
 * Muestra los números tal como quedaron congelados en el corte.
 */
@Composable
fun CorteDialog(
    corte: CorteConVendedores,
    onDismiss: () -> Unit
) {
    val c = corte.corte

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ShapeCardLg,
            color = AlusheCream,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Text(
                    "ESTADO DE CUENTA",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = AlusheInkLight
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    c.nombrePeriodo,
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = AlusheInk
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${c.fechaInicio.toFechaCorta()} — ${c.fechaCierre.toFechaCorta()}",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = AlusheInkMid
                )

                Spacer(Modifier.height(16.dp))

                // ── Utilidad, el número que importa ──────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCardLg)
                        .background(AlusheGreen)
                        .padding(16.dp)
                ) {
                    Text(
                        "UTILIDAD NETA",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        color = AlusheWhite.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        c.utilidadNeta.toMXN(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        color = AlusheWhite
                    )
                    Text(
                        "${c.margenSobreCosto}% de margen sobre costo",
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheWhite.copy(alpha = 0.9f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                CorteSeccion("Venta")
                CorteRenglon("Total vendido", c.totalVendido.toMXN(), destacado = true)
                CorteRenglon("Ventas registradas", "${c.numVentas}")
                CorteRenglon("Ticket promedio", c.ticketPromedio.toMXN())

                Spacer(Modifier.height(12.dp))

                CorteSeccion("Costo")
                CorteRenglon("Costo de lo vendido", c.costoVendido.toMXN())
                CorteRenglon("Ventas con costo conocido", "${c.ventasConCosto}")
                CorteRenglon("Ventas sin costo (100% utilidad)", "${c.ventasSinCosto}")

                if (corte.vendedores.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    CorteSeccion("Por persona")
                    corte.vendedores.forEach { v ->
                        CorteRenglon(
                            "${v.vendedor}  ·  ${v.numVentas} ventas",
                            v.totalVendido.toMXN()
                        )
                    }
                }

                if (!corte.esRetroactivo) {
                    Spacer(Modifier.height(12.dp))
                    CorteSeccion("Inventario al cierre")
                    CorteRenglon("Valor del stock", c.valorInventarioCierre.toMXN())
                    CorteRenglon("Piezas disponibles", "${c.piezasInventarioCierre}")
                }

                Spacer(Modifier.height(16.dp))

                if (corte.esRetroactivo) {
                    Text(
                        "Corte reconstruido después del cierre. No se incluye el " +
                            "inventario porque el stock de hoy no es el de entonces.",
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheInkLight
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Text(
                    if (c.subidoEn == null)
                        "Guardado en este teléfono. Todavía no se sube a la web."
                    else
                        "Publicado en la web.",
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    color = AlusheInkLight
                )

                Spacer(Modifier.height(14.dp))

                AlusheButton(
                    text = "Cerrar",
                    onClick = onDismiss,
                    variante = ButtonVariante.NEGRO,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CorteSeccion(titulo: String) {
    Text(
        titulo.uppercase(),
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        color = AlusheInkLight
    )
    HorizontalDivider(color = AlusheBorder, thickness = 1.dp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CorteRenglon(
    etiqueta: String,
    valor: String,
    destacado: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            etiqueta,
            fontFamily = DmSans,
            fontSize = 13.sp,
            color = AlusheInkMid,
            modifier = Modifier.weight(1f)
        )
        Text(
            valor,
            fontFamily = Montserrat,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (destacado) 15.sp else 13.sp,
            color = AlusheInk
        )
    }
}
