package com.alushestore.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alushestore.ui.components.AlusheButton
import com.alushestore.ui.components.ButtonVariante
import com.alushestore.ui.components.ShapeCardLg
import com.alushestore.ui.components.ShapePill
import com.alushestore.ui.components.bounce
import com.alushestore.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

private enum class UpdateState {
    IDLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var state by remember { mutableStateOf(UpdateState.IDLE) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = {
            if (state != UpdateState.DOWNLOADING) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = state != UpdateState.DOWNLOADING,
            dismissOnClickOutside = state != UpdateState.DOWNLOADING
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .shadow(16.dp, ShapeCardLg, ambientColor = Color(0x20000000), spotColor = AlusheGreen.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = AlusheWhite),
            shape = ShapeCardLg,
            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Cabecera con ícono y botón de cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AlusheGreenSurface)
                                .border(1.dp, AlusheGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = AlusheGreenDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                "Actualización lista",
                                fontFamily = PlayfairDisplay,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AlusheInk
                            )
                            Box(
                                modifier = Modifier
                                    .clip(ShapePill)
                                    .background(AlusheGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = updateInfo.versionName.ifBlank { "v${updateInfo.versionCode}" },
                                    fontFamily = Montserrat,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    color = AlusheGreenDark
                                )
                            }
                        }
                    }

                    if (state != UpdateState.DOWNLOADING) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp).bounce()
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = AlusheInkMid,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Notas de la versión
                if (updateInfo.notas.isNotBlank()) {
                    Text(
                        "Novedades de esta versión:",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AlusheInkMid
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AlusheCream)
                            .border(1.dp, AlusheBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = updateInfo.notas,
                            fontFamily = DmSans,
                            fontSize = 13.sp,
                            color = AlusheInk,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }

                // Progreso de descarga
                AnimatedVisibility(visible = state == UpdateState.DOWNLOADING) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Descargando actualización...",
                                fontFamily = DmSans,
                                fontSize = 12.sp,
                                color = AlusheInkMid
                            )
                            Text(
                                "${(downloadProgress * 100).toInt()}%",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = AlusheGreenDark
                            )
                        }
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(ShapePill),
                            color = AlusheGreen,
                            trackColor = AlusheCreamDark
                        )
                    }
                }

                // Mensaje de error
                AnimatedVisibility(visible = state == UpdateState.ERROR) {
                    Text(
                        text = errorMessage.ifBlank { "Ocurrió un error al descargar. Revisa tu conexión a internet." },
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheRed,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Botones de acción según el estado
                when (state) {
                    UpdateState.IDLE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).bounce()
                            ) {
                                Text("Más tarde", fontFamily = Montserrat, color = AlusheInkMid, fontSize = 13.sp)
                            }

                            AlusheButton(
                                text = "Actualizar",
                                onClick = {
                                    state = UpdateState.DOWNLOADING
                                    coroutineScope.launch {
                                        val file = AppUpdateManager.downloadApk(
                                            context = context,
                                            apkUrl = updateInfo.apkUrl,
                                            onProgress = { downloadProgress = it }
                                        )
                                        if (file != null && file.exists()) {
                                            downloadedFile = file
                                            state = UpdateState.READY_TO_INSTALL
                                            // Lanzar instalación de inmediato
                                            AppUpdateManager.promptInstall(context, file)
                                        } else {
                                            state = UpdateState.ERROR
                                            errorMessage = "No se pudo descargar el instalador."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.3f)
                            )
                        }
                    }

                    UpdateState.DOWNLOADING -> {
                        // Solo muestra el progreso animado
                    }

                    UpdateState.READY_TO_INSTALL -> {
                        AlusheButton(
                            text = "Instalar ahora",
                            onClick = {
                                downloadedFile?.let { AppUpdateManager.promptInstall(context, it) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    UpdateState.ERROR -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).bounce()
                            ) {
                                Text("Cancelar", fontFamily = Montserrat, color = AlusheInkMid)
                            }
                            AlusheButton(
                                text = "Reintentar",
                                onClick = {
                                    state = UpdateState.DOWNLOADING
                                    coroutineScope.launch {
                                        val file = AppUpdateManager.downloadApk(
                                            context = context,
                                            apkUrl = updateInfo.apkUrl,
                                            onProgress = { downloadProgress = it }
                                        )
                                        if (file != null && file.exists()) {
                                            downloadedFile = file
                                            state = UpdateState.READY_TO_INSTALL
                                            AppUpdateManager.promptInstall(context, file)
                                        } else {
                                            state = UpdateState.ERROR
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
