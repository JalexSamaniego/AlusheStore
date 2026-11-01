package com.alushestore.ui.screens.home

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
import androidx.compose.animation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import com.alushestore.R
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.data.local.ActiveUserManager
import com.alushestore.data.repository.CartItem
import com.alushestore.domain.OrigenProducto
import com.alushestore.domain.ProductoUnificado
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.HomeViewModel
import com.alushestore.BuildConfig
import com.alushestore.updater.AppUpdateManager
import com.alushestore.updater.UpdateDialog
import com.alushestore.updater.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToBases: () -> Unit,
    onNavigateToEstadisticas: () -> Unit,
    onNavigateToTianguis: () -> Unit = {},
    onNavigateToClientes: () -> Unit = {},
    onNavigateToInventario: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vendedorActivo by viewModel.vendedorActivo.collectAsStateWithLifecycle()
    val usuario1 by viewModel.usuario1.collectAsStateWithLifecycle()
    val usuario2 by viewModel.usuario2.collectAsStateWithLifecycle()
    val movimientosPendientes by viewModel.movimientosPendientes.collectAsStateWithLifecycle()
    val movimientosEnConflicto by viewModel.movimientosEnConflicto.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    // Verificación de actualización en segundo plano tras render inicial ágil
    LaunchedEffect(Unit) {
        delay(1200)
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
        topBar = {
            AlusheHeader(
                nombreBase = uiState.baseActiva?.nombre ?: "Sin base activa",
                vendedorActivo = vendedorActivo,
                usuario1 = usuario1,
                usuario2 = usuario2,
                onToggleVendedor = { viewModel.alternarVendedor() },
                onConfigurarVendedores = { u1, u2 -> viewModel.configurarVendedores(u1, u2) },
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
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp)
            ) {
                // Botones secundarios: Clientes, Stock e Inventario, Tianguis
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.People, "Clientes", modifier = Modifier.size(17.dp))
                            Text(
                                "Clientes",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = onNavigateToInventario,
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Inventory2, "Stock", modifier = Modifier.size(17.dp))
                            Text(
                                "Stock",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Storefront, "Tianguis", modifier = Modifier.size(17.dp))
                            Text(
                                "Tianguis",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
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
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
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
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // El almacén rechazó estos descuentos: la venta ya se cobró, pero
            // el stock compartido quedó desfasado y hay que arreglarlo a mano.
            if (movimientosEnConflicto > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlusheCream)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(ShapeChip)
                            .background(AlusheRedSurface)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$movimientosEnConflicto descuento(s) no entraron al almacén. Revisa el stock en Inventario.",
                            fontFamily = DmSans,
                            fontSize = 11.sp,
                            color = AlusheInkMid,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Entendido",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = AlusheRed,
                            modifier = Modifier.bounceClickable { viewModel.descartarConflictos() }
                        )
                    }
                }
            }

            // Movimientos de stock esperando internet
            if (movimientosPendientes > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlusheCream)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(ShapeChip)
                            .background(AlusheCreamDark)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$movimientosPendientes descuento(s) de stock esperando internet",
                            fontFamily = DmSans,
                            fontSize = 11.sp,
                            color = AlusheInkMid,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Cancelar",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = AlusheInkLight,
                                modifier = Modifier.bounceClickable { viewModel.descartarPendientes() }
                            )
                            Text(
                                text = "Reintentar",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = AlusheGreenDark,
                                modifier = Modifier.bounceClickable { viewModel.sincronizarCola() }
                            )
                        }
                    }
                }
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
    val productosCatalogo by viewModel.productosCatalogo.collectAsStateWithLifecycle()
    val clientes by viewModel.clientes.collectAsStateWithLifecycle()

    if (showAddSheet) {
        AddVentaSheet(
            productos = productosCatalogo,
            clientes = clientes,
            onDismiss = { viewModel.cerrarAddSheet() },
            onSaveRapida = { articulo, monto, notas ->
                viewModel.agregarVenta(articulo, monto, notas)
                viewModel.cerrarAddSheet()
            },
            onCobrarPos = { items, notas ->
                viewModel.cobrarVentaPos(items, notas)
            },
            onCrearApartadoPos = { clienteId, producto, anticipo, notas ->
                viewModel.crearApartadoPos(clienteId, producto, anticipo, notas)
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
    vendedorActivo: String = "PRINCIPAL",
    usuario1: String = ActiveUserManager.DEFAULT_USER_1,
    usuario2: String = ActiveUserManager.DEFAULT_USER_2,
    onToggleVendedor: () -> Unit = {},
    onConfigurarVendedores: (String, String) -> Unit = { _, _ -> },
    onBasesClick: () -> Unit,
    onDriveClick: () -> Unit,
    onEstadisticasClick: () -> Unit,
    onCheckUpdateClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfigUserDialog by remember { mutableStateOf(false) }

    if (showConfigUserDialog) {
        ConfigurarVendedoresDialog(
            usuario1 = usuario1,
            usuario2 = usuario2,
            onDismiss = { showConfigUserDialog = false },
            onGuardar = { u1, u2 ->
                onConfigurarVendedores(u1, u2)
                showConfigUserDialog = false
            }
        )
    }

    Surface(
        color = AlusheInk,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Fila 1: Logo + Nombre de la App + Acciones principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_alushe),
                    contentDescription = "AlusheStore",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "AlusheStore",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = AlusheCream
                )
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onEstadisticasClick,
                    modifier = Modifier.size(38.dp).bounce()
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = "Estadísticas",
                        tint = AlusheCream.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDriveClick,
                    modifier = Modifier.size(38.dp).bounce()
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Respaldar",
                        tint = AlusheCream.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onBasesClick,
                    modifier = Modifier.size(38.dp).bounce()
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = "Bases",
                        tint = AlusheCream.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(38.dp).bounce()
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = AlusheCream.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
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
                                        "Vendedor: $vendedorActivo",
                                        fontFamily = Montserrat,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AlusheInk
                                    )
                                    Text(
                                        "Toca para alternar",
                                        fontFamily = DmSans,
                                        fontSize = 11.sp,
                                        color = AlusheGreenDark
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SwitchAccount, null, tint = AlusheGreenDark)
                            },
                            onClick = {
                                showMenu = false
                                onToggleVendedor()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Configurar vendedores",
                                    fontFamily = Montserrat,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = AlusheInk
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ManageAccounts, null, tint = AlusheInkMid)
                            },
                            onClick = {
                                showMenu = false
                                showConfigUserDialog = true
                            }
                        )
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

            Spacer(Modifier.height(4.dp))

            // Fila 2: Base activa (izquierda) y Vendedor activo (derecha)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Base pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(AlusheInkMid.copy(alpha = 0.35f))
                        .clickable { onBasesClick() }
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(ShapePill)
                            .background(AlusheGreen)
                    )
                    Text(
                        text = "Base: $nombreBase",
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheCream.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Vendedor pill
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(AlusheGreen.copy(alpha = 0.2f))
                        .clickable { onToggleVendedor() }
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = AlusheGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = vendedorActivo,
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = AlusheGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigurarVendedoresDialog(
    usuario1: String,
    usuario2: String,
    onDismiss: () -> Unit,
    onGuardar: (String, String) -> Unit
) {
    var u1 by remember { mutableStateOf(usuario1) }
    var u2 by remember { mutableStateOf(usuario2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Configurar Vendedores",
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = AlusheInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Define los nombres de los 2 vendedores para etiquetar ventas y abonos automáticamente.",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = AlusheInkLight
                )
                OutlinedTextField(
                    value = u1,
                    onValueChange = { u1 = it },
                    label = { Text("Vendedor 1") },
                    singleLine = true,
                    shape = ShapeCard
                )
                OutlinedTextField(
                    value = u2,
                    onValueChange = { u2 = it },
                    label = { Text("Vendedor 2") },
                    singleLine = true,
                    shape = ShapeCard
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(u1, u2) },
                colors = ButtonDefaults.buttonColors(containerColor = AlusheGreen),
                shape = ShapePill
            ) {
                Text("Guardar", fontFamily = Montserrat, fontWeight = FontWeight.Bold, color = AlusheWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", fontFamily = Montserrat, color = AlusheInkMid)
            }
        },
        containerColor = AlusheWhite
    )
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
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
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
    productos: List<ProductoUnificado> = emptyList(),
    clientes: List<ClienteEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveRapida: (String, Double, String) -> Unit,
    onCobrarPos: (List<CartItem>, String) -> Unit = { _, _ -> },
    onCrearApartadoPos: (clienteId: Long, producto: ProductoUnificado, anticipo: Double, notas: String) -> Unit = { _, _, _, _ -> }
) {
    var modoPos by remember { mutableStateOf(false) }

    // Estado Modo Rápido
    var articulo by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var notasRapida by remember { mutableStateOf("") }
    var errorArticulo by remember { mutableStateOf(false) }
    var errorMonto by remember { mutableStateOf(false) }

    // Estado Modo POS
    var posQuery by remember { mutableStateOf("") }
    var posScannerActivo by remember { mutableStateOf(false) }
    var carrito by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var carritoExpandido by remember { mutableStateOf(false) }
    var notasPos by remember { mutableStateOf("") }

    // Modales secundarios de POS
    var showArticuloLibreDialog by remember { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header con Selector de Modo (Rápido vs POS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(26.dp)
                            .clip(ShapePill)
                            .background(AlusheGreen)
                    )
                    Text(
                        if (modoPos) "Punto de Venta (POS)" else "Venta Rápida",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = AlusheInk
                    )
                }

                // Switcher entre Modo Rápido y Modo POS
                Row(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(AlusheCreamDark)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(if (!modoPos) AlusheGreen else Color.Transparent)
                            .clickable { modoPos = false }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "Rápida",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (!modoPos) AlusheWhite else AlusheInkMid
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(if (modoPos) AlusheInk else Color.Transparent)
                            .clickable { modoPos = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "POS",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (modoPos) AlusheCream else AlusheInkMid
                        )
                    }
                }
            }

            AnimatedContent(targetState = modoPos, label = "saleModeSwitch") { esPos ->
                if (!esPos) {
                    // ══════════════════════════════════════════════════════════
                    // MODO RÁPIDO (100% Retrocompatible)
                    // ══════════════════════════════════════════════════════════
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = articulo,
                            onValueChange = { articulo = it; errorArticulo = false },
                            label = { Text("Artículo", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                            placeholder = { Text("Ej: Venta rápida tianguis", fontFamily = DmSans) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorArticulo,
                            supportingText = if (errorArticulo) { { Text("Ingresa el nombre del artículo", fontFamily = DmSans) } } else null,
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
                            label = { Text("Monto (MXN)", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                            placeholder = { Text("0.00", fontFamily = DmSans) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorMonto,
                            supportingText = if (errorMonto) { { Text("Ingresa un monto válido", fontFamily = DmSans) } } else null,
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
                            value = notasRapida,
                            onValueChange = { notasRapida = it },
                            label = { Text("Notas (opcional)", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
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
                                text = "Cobrar",
                                onClick = {
                                    val montoDouble = monto.toDoubleOrNull()
                                    errorArticulo = articulo.isBlank()
                                    errorMonto = montoDouble == null || montoDouble <= 0
                                    if (!errorArticulo && !errorMonto) {
                                        onSaveRapida(articulo.trim(), montoDouble!!, notasRapida.trim())
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                variante = ButtonVariante.VERDE
                            )
                        }
                    }
                } else {
                    // ══════════════════════════════════════════════════════════
                    // MODO POS / CATÁLOGO
                    // ══════════════════════════════════════════════════════════
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Buscador y botones de Escáner y Artículo Libre
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = posQuery,
                                onValueChange = { posQuery = it },
                                placeholder = { Text("Buscar producto...", fontFamily = DmSans, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = AlusheInkLight, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = ShapePill,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = AlusheWhite,
                                    focusedContainerColor = AlusheWhite,
                                    focusedBorderColor = AlusheGreen,
                                    unfocusedBorderColor = AlusheBorder
                                )
                            )

                            // Botón de escáner
                            IconButton(
                                onClick = { posScannerActivo = !posScannerActivo },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (posScannerActivo) AlusheGreen else AlusheInk)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Escáner",
                                    tint = if (posScannerActivo) AlusheWhite else AlusheCream,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Botón de artículo libre
                            IconButton(
                                onClick = { showArticuloLibreDialog = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AlusheGreenSurface)
                            ) {
                                Icon(
                                    Icons.Default.PostAdd,
                                    contentDescription = "Artículo Libre",
                                    tint = AlusheGreenDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Visor de Cámara si escáner activo
                        AnimatedVisibility(
                            visible = posScannerActivo,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                AlusheBarcodeScanner(
                                    onBarcodeScanned = { code ->
                                        val match = productos.find { it.sku?.equals(code, ignoreCase = true) == true }
                                        if (match != null) {
                                            val idx = carrito.indexOfFirst { it.producto.clave == match.clave }
                                            carrito = if (idx >= 0) {
                                                carrito.toMutableList().also {
                                                    it[idx] = it[idx].copy(cantidad = it[idx].cantidad + 1)
                                                }
                                            } else {
                                                carrito + CartItem(match, 1)
                                            }
                                        } else {
                                            posQuery = code
                                        }
                                    },
                                    onClose = { posScannerActivo = false }
                                )
                            }
                        }

                        // Catálogo de productos filtrados
                        val qLimpia = posQuery.trim().lowercase()
                        val productosFiltrados = productos.filter {
                            qLimpia.isBlank() ||
                                    it.nombre.lowercase().contains(qLimpia) ||
                                    it.sku?.lowercase()?.contains(qLimpia) == true
                        }

                        if (productosFiltrados.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay productos que coincidan.",
                                    fontFamily = DmSans,
                                    fontSize = 12.sp,
                                    color = AlusheInkLight
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(productosFiltrados, key = { it.clave }) { prod ->
                                    val cantEnCarrito = carrito.find { it.producto.clave == prod.clave }?.cantidad ?: 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(ShapeCard)
                                            .background(AlusheWhite)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Inicial, como en Ventas recientes, para que la lista
                                        // tenga el mismo ritmo visual que el resto de la app.
                                        // El tono verde marca lo que viene del almacén
                                        // compartido, sin meter texto de más.
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(ShapeCard)
                                                .background(
                                                    if (prod.esCompartido) AlusheGreenSurface else AlusheCreamDark
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                // Muchos nombres empiezan con cantidad ("3 Paquetes
                                                // de..."), y una inicial "3" no dice nada. Se busca
                                                // la primera letra de verdad.
                                                text = (prod.nombre.firstOrNull { it.isLetter() }
                                                    ?: prod.nombre.trim().firstOrNull() ?: '·')
                                                    .uppercase(),
                                                fontFamily = Montserrat,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = if (prod.esCompartido) AlusheGreenDark else AlusheInkMid
                                            )
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = prod.nombre,
                                                fontFamily = Montserrat,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = AlusheInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = prod.precio.toMXN(),
                                                    fontFamily = Montserrat,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = AlusheGreenDark
                                                )
                                                Text(
                                                    text = "  ·  ${prod.stockDisponible} disp",
                                                    fontFamily = DmSans,
                                                    fontSize = 11.sp,
                                                    color = if (prod.stockDisponible > 0) AlusheInkLight else AlusheRed,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (cantEnCarrito > 0) {
                                                IconButton(
                                                    onClick = {
                                                        val idx = carrito.indexOfFirst { it.producto.clave == prod.clave }
                                                        if (idx >= 0) {
                                                            val nuevaCant = carrito[idx].cantidad - 1
                                                            carrito = if (nuevaCant <= 0) {
                                                                carrito.filterNot { it.producto.clave == prod.clave }
                                                            } else {
                                                                carrito.toMutableList().also { it[idx] = it[idx].copy(cantidad = nuevaCant) }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.RemoveCircleOutline, null, tint = AlusheInkLight, modifier = Modifier.size(18.dp))
                                                }
                                                Text(
                                                    text = cantEnCarrito.toString(),
                                                    fontFamily = Montserrat,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = AlusheInk
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    val idx = carrito.indexOfFirst { it.producto.clave == prod.clave }
                                                    carrito = if (idx >= 0) {
                                                        carrito.toMutableList().also {
                                                            it[idx] = it[idx].copy(cantidad = it[idx].cantidad + 1)
                                                        }
                                                    } else {
                                                        carrito + CartItem(prod, 1)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = AlusheGreen),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = ShapePill,
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("+", fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Barra Flotante Elástica del Carrito POS ────────────
                        if (carrito.isNotEmpty()) {
                            val totalPiezas = carrito.sumOf { it.cantidad }
                            val totalDinero = carrito.sumOf { it.subtotal }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(elevation = 8.dp, shape = ShapeCardLg, spotColor = AlusheInk.copy(alpha = 0.4f)),
                                shape = ShapeCardLg,
                                colors = CardDefaults.cardColors(containerColor = AlusheInk)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Resumen superior del carrito (tappable para expandir)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { carritoExpandido = !carritoExpandido },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(AlusheGreen),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.ShoppingCart, null, tint = AlusheWhite, modifier = Modifier.size(16.dp))
                                            }
                                            Column {
                                                Text(
                                                    text = "$totalPiezas artículos en carrito",
                                                    fontFamily = Montserrat,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = AlusheCream
                                                )
                                                Text(
                                                    text = if (carritoExpandido) "Toca para contraer" else "Toca para ver desglose",
                                                    fontFamily = DmSans,
                                                    fontSize = 10.sp,
                                                    color = AlusheCream.copy(alpha = 0.65f)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = totalDinero.toMXN(),
                                                fontFamily = Montserrat,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = AlusheGreen
                                            )
                                            Icon(
                                                if (carritoExpandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                null,
                                                tint = AlusheCream.copy(alpha = 0.65f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Desglose expandido de artículos
                                    AnimatedVisibility(visible = carritoExpandido) {
                                        Column(
                                            modifier = Modifier.padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            HorizontalDivider(color = AlusheBorderDark, thickness = 0.8.dp)
                                            carrito.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.producto.nombre,
                                                            fontFamily = Montserrat,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 12.sp,
                                                            color = AlusheCream
                                                        )
                                                        Text(
                                                            text = "${item.cantidad} x ${item.precioFinal.toMXN()} = ${item.subtotal.toMXN()}",
                                                            fontFamily = DmSans,
                                                            fontSize = 11.sp,
                                                            color = AlusheGreen
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { carrito = carrito.filterNot { it.producto.clave == item.producto.clave } },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, null, tint = AlusheCream.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // El POS sólo cobra. Los apartados se hacen desde
                                    // Clientes, que es donde vive el compromiso con la
                                    // persona y donde se le registran los abonos.
                                    Button(
                                        onClick = { onCobrarPos(carrito, notasPos) },
                                        modifier = Modifier.fillMaxWidth().height(44.dp).bounce(),
                                        colors = ButtonDefaults.buttonColors(containerColor = AlusheGreen),
                                        shape = ShapePill
                                    ) {
                                        Text("Cobrar ${totalDinero.toMXN()}", fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AlusheWhite)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo para Agregar Artículo Libre no inventariado al carrito POS
    if (showArticuloLibreDialog) {
        var nombreLibre by remember { mutableStateOf("") }
        var precioLibreStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showArticuloLibreDialog = false },
            title = {
                Text("Artículo Libre al Carrito", fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AlusheInk)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Agrega un concepto y monto manual sin código de barras al carrito.",
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkLight
                    )
                    OutlinedTextField(
                        value = nombreLibre,
                        onValueChange = { nombreLibre = it },
                        label = { Text("Concepto") },
                        placeholder = { Text("Ej: Cajita de regalo") },
                        singleLine = true,
                        shape = ShapeCard,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = precioLibreStr,
                        onValueChange = { precioLibreStr = it },
                        label = { Text("Precio ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = ShapeCard,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val precio = precioLibreStr.toDoubleOrNull() ?: 0.0
                        if (nombreLibre.isNotBlank() && precio > 0) {
                            // Artículo libre: no existe en ningún catálogo, se cobra
                            // y se registra sin tocar stock de nadie.
                            val dummyProducto = ProductoUnificado(
                                sku = "LIBRE",
                                nombre = nombreLibre.trim(),
                                costo = 0.0,
                                precio = precio,
                                stockDisponible = 999,
                                origen = OrigenProducto.LOCAL
                            )
                            carrito = carrito + CartItem(dummyProducto, 1)
                            showArticuloLibreDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlusheGreen),
                    shape = ShapePill
                ) {
                    Text("Agregar", fontFamily = Montserrat, fontWeight = FontWeight.Bold, color = AlusheWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArticuloLibreDialog = false }) {
                    Text("Cancelar", fontFamily = Montserrat, color = AlusheInkMid)
                }
            },
            containerColor = AlusheWhite
        )
    }
}