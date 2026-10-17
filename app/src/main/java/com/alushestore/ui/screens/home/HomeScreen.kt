package com.alushestore.ui.screens.home

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alushestore.R
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.HomeViewModel
import com.alushestore.BuildConfig
import com.alushestore.updater.AppUpdateManager
import com.alushestore.updater.UpdateDialog
import com.alushestore.updater.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToBases: () -> Unit,
    onNavigateToEstadisticas: () -> Unit,
    onNavigateToTianguis: () -> Unit = {},
    onNavigateToClientes: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    // Verificación de actualización en segundo plano al iniciar y manejo de notificación
    LaunchedEffect(Unit) {
        val info = AppUpdateManager.checkForUpdateAndNotify(context, showNotificationIfFound = true)
        if (info != null) {
            updateInfo = info
        }
        if (AppUpdateManager.pendingUpdatePrompt) {
            AppUpdateManager.pendingUpdatePrompt = false
            if (info != null) {
                updateInfo = info
            }
        }
    }

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
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botones secundarios: Clientes y Tianguis
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FloatingActionButton(
                        onClick = onNavigateToClientes,
                        containerColor = AlusheInk,
                        contentColor = AlusheCream,
                        shape = ShapePill,
                        modifier = Modifier
                            .bounce()
                            .shadow(
                                elevation = 4.dp,
                                shape = ShapePill,
                                spotColor = AlusheInk.copy(alpha = 0.3f)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(Icons.Default.People, "Clientes",
                                modifier = Modifier.size(18.dp))
                            Text(
                                "Clientes",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = onNavigateToTianguis,
                        containerColor = AlusheInk,
                        contentColor = AlusheCream,
                        shape = ShapePill,
                        modifier = Modifier
                            .bounce()
                            .shadow(
                                elevation = 4.dp,
                                shape = ShapePill,
                                spotColor = AlusheInk.copy(alpha = 0.3f)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(Icons.Default.Storefront, "Tianguis",
                                modifier = Modifier.size(18.dp))
                            Text(
                                "Tianguis",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                // FAB principal verde
                FloatingActionButton(
                    onClick = { viewModel.abrirAddSheet() },
                    containerColor = AlusheGreen,
                    contentColor = AlusheWhite,
                    shape = ShapePill,
                    modifier = Modifier
                        .bounce()
                        .shadow(
                            elevation = 8.dp,
                            shape = ShapePill,
                            spotColor = AlusheGreen.copy(alpha = 0.4f)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, "Agregar", modifier = Modifier.size(20.dp))
                        Text(
                            "Agregar venta",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AlusheGreen) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            item {
                AlusheHeader(
                    nombreBase = uiState.baseActiva?.nombre ?: "Sin base activa",
                    onBasesClick = onNavigateToBases,
                    onDriveClick = { viewModel.respaldarEnDrive() },
                    onEstadisticasClick = onNavigateToEstadisticas,
                    onCheckUpdateClick = {
                        scope.launch {
                            isCheckingUpdates = true
                            snackbarHostState.showSnackbar("Buscando actualización...")
                            val info = AppUpdateManager.checkForUpdate()
                            isCheckingUpdates = false
                            if (info != null) {
                                updateInfo = info
                                AppUpdateManager.showUpdateNotification(context, info, force = true)
                            } else {
                                snackbarHostState.showSnackbar("AlusheStore está al día (v${BuildConfig.VERSION_NAME})")
                            }
                        }
                    }
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .background(AlusheCream)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = "Total base",
                            valor = uiState.totalBase.toMXN(),
                            modifier = Modifier.weight(1f),
                            esDestacada = true
                        )
                        StatCard(
                            label = "Hoy",
                            valor = uiState.totalHoy.toMXN(),
                            modifier = Modifier.weight(1f),
                            accentColor = AlusheInk
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = "Ventas",
                            valor = uiState.cantidadVentas.toString(),
                            modifier = Modifier.weight(1f),
                            accentColor = AlusheInkMid
                        )
                        StatCard(
                            label = "Promedio",
                            valor = uiState.promedio.toMXN(),
                            modifier = Modifier.weight(1f),
                            accentColor = AlusheInkMid
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlusheCreamDark)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Ventas recientes",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = AlusheInk,
                            letterSpacing = 0.3.sp
                        )
                        if (uiState.cantidadVentas > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(ShapePill)
                                    .background(AlusheGreen)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "${uiState.cantidadVentas}",
                                    fontFamily = Montserrat,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    color = AlusheWhite
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.ventas.isEmpty()) {
                item {
                    Box(modifier = Modifier.background(AlusheCream)) {
                        EmptyState(
                            titulo = "Sin ventas todavía",
                            subtitulo = "Toca el botón verde para agregar\ntu primera venta del día"
                        )
                    }
                }
            } else {
                items(uiState.ventas, key = { it.id }) { venta ->
                    Box(
                        modifier = Modifier
                            .animateItemPlacement()
                            .background(AlusheCream)
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                    ) {
                        VentaCard(
                            venta = venta,
                            onDelete = { viewModel.eliminarVenta(venta.id) },
                            onEdit = { viewModel.abrirEditSheet(venta) }
                        )
                    }
                }
            }
        }
    }

    HomeModals(viewModel = viewModel)

    updateInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            onDismiss = { updateInfo = null }
        )
    }
}

@Composable
fun HomeModals(viewModel: HomeViewModel) {
    val showAddSheet by viewModel.showAddSheet.collectAsStateWithLifecycle()
    val ventaEditando by viewModel.ventaEditando.collectAsStateWithLifecycle()

    if (showAddSheet) {
        AddVentaSheet(
            onDismiss = { viewModel.cerrarAddSheet() },
            onSave = { articulo, monto, notas ->
                viewModel.agregarVenta(articulo, monto, notas)
                viewModel.cerrarAddSheet()
            }
        )
    }

    ventaEditando?.let { venta ->
        EditVentaSheet(
            venta = venta,
            onDismiss = { viewModel.cerrarEditSheet() },
            onSave = { articuloEdit, montoEdit, notasEdit ->
                viewModel.editarVenta(venta.copy(articulo = articuloEdit, monto = montoEdit, notas = notasEdit))
                viewModel.cerrarEditSheet()
            }
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
fun AlusheHeader(
    nombreBase: String,
    onBasesClick: () -> Unit,
    onDriveClick: () -> Unit,
    onEstadisticasClick: () -> Unit,
    onCheckUpdateClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlusheInk)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_alushe),
                contentDescription = "AlusheStore",
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AlusheStore",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = AlusheCream
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(ShapePill)
                            .background(AlusheGreen)
                    )
                    Text(
                        nombreBase,
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheCream.copy(alpha = 0.65f)
                    )
                }
            }
            IconButton(onClick = onEstadisticasClick, modifier = Modifier.bounce()) {
                Icon(Icons.Default.BarChart, "Estadísticas",
                    tint = AlusheCream.copy(alpha = 0.7f))
            }
            IconButton(onClick = onDriveClick, modifier = Modifier.bounce()) {
                Icon(Icons.Default.CloudUpload, "Respaldar",
                    tint = AlusheCream.copy(alpha = 0.7f))
            }
            IconButton(onClick = onBasesClick, modifier = Modifier.bounce()) {
                Icon(Icons.Default.Folder, "Bases",
                    tint = AlusheCream.copy(alpha = 0.7f))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.bounce()) {
                    Icon(Icons.Default.MoreVert, "Opciones", tint = AlusheCream.copy(alpha = 0.7f))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(AlusheWhite)
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    "Buscar actualización",
                                    fontFamily = Montserrat,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AlusheInk
                                )
                                Text(
                                    "Versión ${BuildConfig.VERSION_NAME}",
                                    fontFamily = DmSans,
                                    fontSize = 11.sp,
                                    color = AlusheInkLight
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.SystemUpdate, null, tint = AlusheGreenDark)
                        },
                        onClick = {
                            showMenu = false
                            onCheckUpdateClick()
                        }
                    )
                }
            }
        }
    }
}

// ── Bottom Sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVentaSheet(
    venta: VentaEntity,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit
) {
    var articulo by remember { mutableStateOf(venta.articulo) }
    var monto by remember { mutableStateOf(venta.monto.toString()) }
    var notas by remember { mutableStateOf(venta.notas) }
    var errorArticulo by remember { mutableStateOf(false) }
    var errorMonto by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(28.dp)
                        .clip(ShapePill)
                        .background(AlusheGreen)
                )
                Text(
                    "Editar venta",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
            }

            OutlinedTextField(
                value = articulo,
                onValueChange = { articulo = it; errorArticulo = false },
                label = { Text("Artículo", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorArticulo,
                supportingText = if (errorArticulo) {
                    { Text("Ingresa el nombre del artículo", fontFamily = DmSans) }
                } else null,
                singleLine = true,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite,
                    focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            OutlinedTextField(
                value = monto,
                onValueChange = { monto = it; errorMonto = false },
                label = { Text("Monto (MXN)", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMonto,
                supportingText = if (errorMonto) {
                    { Text("Ingresa un monto válido", fontFamily = DmSans) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite,
                    focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas (opcional)", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite,
                    focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlusheButton(
                    text = "Cancelar",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variante = ButtonVariante.OUTLINE
                )
                AlusheButton(
                    text = "Guardar",
                    onClick = {
                        val montoDouble = monto.toDoubleOrNull()
                        errorArticulo = articulo.isBlank()
                        errorMonto = montoDouble == null || montoDouble <= 0
                        if (!errorArticulo && !errorMonto) {
                            onSave(articulo.trim(), montoDouble!!, notas.trim())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variante = ButtonVariante.VERDE
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVentaSheet(
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit
) {
    var articulo by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var errorArticulo by remember { mutableStateOf(false) }
    var errorMonto by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(28.dp)
                        .clip(ShapePill)
                        .background(AlusheGreen)
                )
                Text(
                    "Nueva venta",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
            }

            OutlinedTextField(
                value = articulo,
                onValueChange = { articulo = it; errorArticulo = false },
                label = { Text("Artículo", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                placeholder = { Text("Ej: Venta tianguis", fontFamily = DmSans) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorArticulo,
                supportingText = if (errorArticulo) {
                    { Text("Ingresa el nombre del artículo", fontFamily = DmSans) }
                } else null,
                singleLine = true,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite,
                    focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            OutlinedTextField(
                value = monto,
                onValueChange = { monto = it; errorMonto = false },
                label = { Text("Monto (MXN)", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                placeholder = { Text("0.00", fontFamily = DmSans) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMonto,
                supportingText = if (errorMonto) {
                    { Text("Ingresa un monto válido", fontFamily = DmSans) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite,
                    focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas (opcional)", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite,
                    focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlusheButton(
                    text = "Cancelar",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    variante = ButtonVariante.OUTLINE
                )
                AlusheButton(
                    text = "Guardar",
                    onClick = {
                        val montoDouble = monto.toDoubleOrNull()
                        errorArticulo = articulo.isBlank()
                        errorMonto = montoDouble == null || montoDouble <= 0
                        if (!errorArticulo && !errorMonto) {
                            onSave(articulo.trim(), montoDouble!!, notas.trim())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variante = ButtonVariante.VERDE
                )
            }
        }
    }
}