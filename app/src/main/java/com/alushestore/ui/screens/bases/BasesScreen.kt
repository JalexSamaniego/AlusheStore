package com.alushestore.ui.screens.bases

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.repository.DriveBackupInfo
import com.alushestore.data.repository.BaseBackup
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.BasesViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BasesScreen(
    viewModel: BasesViewModel,
    onBack: () -> Unit,
    onNavigateToEstadisticas: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNuevaBaseDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (accountName != null) {
                context.getSharedPreferences("alushe_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putString("google_account", accountName).apply()
                viewModel.onCuentaSeleccionada(accountName, context)
            } else {
                viewModel.onPickAccountCancelled()
            }
        } else {
            viewModel.onPickAccountCancelled()
        }
    }

    val authConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onAuthGranted()
        }
    }

    LaunchedEffect(uiState.needsGoogleAccount) {
        if (uiState.needsGoogleAccount) {
            @Suppress("DEPRECATION")
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            accountPickerLauncher.launch(intent)
        }
    }

    LaunchedEffect(uiState.authConsentIntent) {
        uiState.authConsentIntent?.let {
            viewModel.onAuthConsentMostrado()
            authConsentLauncher.launch(it)
        }
    }

    LaunchedEffect(uiState.mensaje) {
        uiState.mensaje?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AlusheCream,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AlusheInk)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.bounce()) {
                        Icon(Icons.Default.ArrowBack, "Regresar", tint = AlusheCream)
                    }
                    Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = com.alushestore.R.drawable.logo_alushe
                        ),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Bases de datos",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = AlusheCream
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNuevaBaseDialog = true },
                containerColor = AlusheGreen,
                contentColor = AlusheWhite,
                shape = ShapePill,
                modifier = Modifier
                    .bounce()
                    .shadow(8.dp, ShapePill, spotColor = AlusheGreen.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Add, "Nueva base")
            }
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Base activa ──────────────────────────────────────────────────
            item {
                Text(
                    "Base activa",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AlusheInk
                )
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = AlusheBorder, thickness = 1.dp)
            }

            if (uiState.baseActiva != null) {
                item {
                    BaseCard(
                        base = uiState.baseActiva!!,
                        esActiva = true,
                        onCerrar = {
                            viewModel.cerrarBase(uiState.baseActiva!!.id, uiState.baseActiva!!.nombre)
                        },
                        onRespaldar = {
                            viewModel.respaldarBase(uiState.baseActiva!!.id)
                        },
                        onEliminar = {
                            viewModel.eliminarBase(uiState.baseActiva!!)
                        },
                        onVerEstadisticas = {
                            onNavigateToEstadisticas(uiState.baseActiva!!.id)
                        }
                    )
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = ShapeCardLg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Sin base activa",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.SemiBold,
                                color = AlusheInkMid
                            )
                            Spacer(Modifier.height(10.dp))
                            AlusheButton(
                                text = "Crear nueva base",
                                onClick = { showNuevaBaseDialog = true },
                                variante = ButtonVariante.VERDE
                            )
                        }
                    }
                }
            }

            // ── Bases cerradas ───────────────────────────────────────────────
            if (uiState.basesCerradas.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Historial de bases",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AlusheInk
                    )
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = AlusheBorder, thickness = 1.dp)
                }

                items(uiState.basesCerradas, key = { it.id }) { base ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        BaseCard(
                            base = base,
                            esActiva = false,
                            onCerrar = {},
                            onRespaldar = { viewModel.respaldarBase(base.id) },
                            onEliminar = { viewModel.eliminarBase(base) },
                            onVerEstadisticas = { onNavigateToEstadisticas(base.id) }
                        )
                    }
                }
            }
            
            // ── Respaldos en Drive ───────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Respaldos en la nube",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AlusheInk
                    )
                    TextButton(onClick = { viewModel.listarRespaldosDrive() }) {
                        Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Actualizar", fontFamily = Montserrat, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = AlusheBorder, thickness = 1.dp)
            }
            
            if (uiState.isLoading && uiState.respaldosDrive.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AlusheGreen)
                    }
                }
            } else if (uiState.respaldosDrive.isEmpty()) {
                item {
                    Text(
                        "No se encontraron respaldos o no se han cargado.",
                        fontFamily = DmSans,
                        fontSize = 13.sp,
                        color = AlusheInkMid,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(uiState.respaldosDrive, key = { it.fileId }) { backup ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        DriveBackupCard(
                            backupInfo = backup,
                            onClick = { viewModel.descargarRespaldoDrive(backup.fileId) }
                        )
                    }
                }
            }
        }
    }

    uiState.baseDescargada?.let { backup ->
        DriveBackupDetailDialog(
            backup = backup,
            onDismiss = { viewModel.cerrarDetalleDrive() },
            onImportar = { viewModel.importarBaseLocal() }
        )
    }

    if (showNuevaBaseDialog) {
        NuevaBaseDialog(
            onDismiss = { showNuevaBaseDialog = false },
            onConfirm = { nombre, notas ->
                viewModel.crearBase(nombre, notas)
                showNuevaBaseDialog = false
            }
        )
    }
}

// ── BaseCard ──────────────────────────────────────────────────────────────────

@Composable
fun BaseCard(
    base: BaseEntity,
    esActiva: Boolean,
    onCerrar: () -> Unit,
    onRespaldar: () -> Unit,
    onEliminar: () -> Unit,
    onVerEstadisticas: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCerrarDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounce()
            .shadow(4.dp, ShapeCardLg,
                ambientColor = Color(0x0A000000),
                spotColor = Color(0x0A000000))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (esActiva) AlusheGreenSurface else AlusheWhite
            ),
            shape = ShapeCardLg,
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = if (esActiva) 1.5.dp else 1.dp,
                color = if (esActiva) AlusheGreen.copy(alpha = 0.4f) else AlusheBorder
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ícono con fondo
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(ShapeChip)
                            .background(
                                if (esActiva) AlusheGreen.copy(alpha = 0.12f)
                                else AlusheCreamDark
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (esActiva) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (esActiva) AlusheGreenDark else AlusheInkMid,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            base.nombre,
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = AlusheInk
                        )
                        Text(
                            "Creada: ${base.fechaCreacion.toFechaLarga()}",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = AlusheInkLight
                        )
                        if (!esActiva && base.fechaCierre != null) {
                            Text(
                                "Cerrada: ${base.fechaCierre.toFechaLarga()}",
                                fontFamily = DmSans,
                                fontSize = 12.sp,
                                color = AlusheInkLight
                            )
                        }
                    }
                    // Badge Drive
                    if (base.driveFileId != null) {
                        Box(
                            modifier = Modifier
                                .clip(ShapePill)
                                .background(AlusheGreenSurface)
                                .border(1.dp, AlusheGreen.copy(alpha = 0.25f), ShapePill)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "DRIVE",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp,
                                color = AlusheGreenDark,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                if (base.notas.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        base.notas,
                        fontFamily = DmSans,
                        fontSize = 13.sp,
                        color = AlusheInkMid
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = AlusheBorder.copy(alpha = 0.6f))
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onVerEstadisticas,
                        colors = ButtonDefaults.textButtonColors(contentColor = AlusheInk)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth, null,
                            Modifier.size(15.dp), tint = AlusheGreenDark
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Ver calendario",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    if (esActiva) {
                        TextButton(
                            onClick = { showCerrarDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = AlusheInkMid)
                        ) {
                            Icon(Icons.Default.Lock, null,
                                Modifier.size(15.dp), tint = AlusheInkLight)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Cerrar",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    TextButton(
                        onClick = onRespaldar,
                        colors = ButtonDefaults.textButtonColors(contentColor = AlusheGreenDark)
                    ) {
                        Icon(Icons.Default.CloudUpload, null,
                            Modifier.size(15.dp), tint = AlusheGreen)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Respaldar",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete, null,
                            tint = AlusheRed.copy(alpha = 0.55f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar base?") },
            text = { Text("Se eliminarán \"${base.nombre}\" y todas sus ventas. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onEliminar() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showCerrarDialog) {
        AlertDialog(
            onDismissRequest = { showCerrarDialog = false },
            title = { Text("¿Cerrar periodo?") },
            text = { Text("Cerrar \"${base.nombre}\" la moverá al historial. Las ventas se conservan. Puedes respaldarla en Drive antes.") },
            confirmButton = {
                TextButton(onClick = { showCerrarDialog = false; onCerrar() }) { Text("Cerrar periodo") }
            },
            dismissButton = {
                TextButton(onClick = { showCerrarDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ── Dialog nueva base ─────────────────────────────────────────────────────────

@Composable
fun NuevaBaseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ShapeCardLg,
        title = {
            Text(
                "Nueva base de datos",
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = AlusheInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it; error = false },
                    label = {
                        Text("Nombre", fontFamily = Montserrat,
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    },
                    placeholder = { Text("Ej: Tianguis Mayo", fontFamily = DmSans) },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCard,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlusheGreen,
                        focusedLabelColor = AlusheGreen,
                        cursorColor = AlusheGreen
                    )
                )
                OutlinedTextField(
                    value = notas,
                    onValueChange = { notas = it },
                    label = {
                        Text("Notas (opcional)", fontFamily = Montserrat,
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCard,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlusheGreen,
                        focusedLabelColor = AlusheGreen,
                        cursorColor = AlusheGreen
                    )
                )
            }
        },
        confirmButton = {
            AlusheButton(
                text = "Crear",
                onClick = {
                    if (nombre.isBlank()) { error = true; return@AlusheButton }
                    onConfirm(nombre, notas)
                },
                variante = ButtonVariante.VERDE
            )
        },
        dismissButton = {
            AlusheButton(
                text = "Cancelar",
                onClick = onDismiss,
                variante = ButtonVariante.OUTLINE
            )
        }
    )
}

// ── DriveBackupCard ──────────────────────────────────────────────────────────

@Composable
fun DriveBackupCard(
    backupInfo: DriveBackupInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
        shape = ShapeCardLg,
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeChip)
                    .background(AlusheGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CloudDownload, null, tint = AlusheGreenDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    backupInfo.nombre.replace(".json", "").replace("_", " "),
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AlusheInk
                )
                Text(
                    "Modificado: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(backupInfo.modificadoEn))}",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = AlusheInkLight
                )
            }
        }
    }
}

// ── DriveBackupDetailDialog ──────────────────────────────────────────────────

@Composable
fun DriveBackupDetailDialog(
    backup: BaseBackup,
    onDismiss: () -> Unit,
    onImportar: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ShapeCardLg,
        title = {
            Text(
                "Respaldo en la nube",
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = AlusheInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Base: ${backup.base.nombre}", fontWeight = FontWeight.Bold, fontFamily = Montserrat)
                Text("Ventas registradas: ${backup.cantidadVentas}", fontFamily = DmSans)
                Text("Total acumulado: \$${backup.totalMonto}", fontFamily = DmSans, color = AlusheGreen, fontWeight = FontWeight.Bold)
                if (backup.base.notas.isNotBlank()) {
                    Text("Notas: ${backup.base.notas}", fontFamily = DmSans, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }

                Spacer(Modifier.height(8.dp))
                if (confirm) {
                    Text("¿Seguro que deseas importar esta base? Las ventas importadas se mostrarán en tu historial y no reemplazarán tu base activa actual.",
                        color = AlusheRed, fontFamily = DmSans, fontSize = 13.sp)
                } else {
                    Text("Puedes importar este respaldo a tu celular para revisar sus registros en detalle.", fontFamily = DmSans, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (!confirm) {
                AlusheButton(text = "Importar al celular", onClick = { confirm = true }, variante = ButtonVariante.VERDE)
            } else {
                TextButton(onClick = onImportar) {
                    Text("Sí, importar", color = AlusheGreenDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = AlusheInkMid)
            }
        }
    )
}
