package com.alushestore.ui.screens.clientes

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ClienteResumen
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.ClientesViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ClientesScreen(
    viewModel: ClientesViewModel,
    onClienteClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == Activity.RESULT_OK && accountName != null) {
            context.getSharedPreferences("alushe_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("google_account", accountName).apply()
            viewModel.onCuentaSeleccionada(accountName, context)
        } else {
            viewModel.onPickAccountCancelled()
        }
    }

    val authConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onAuthGranted()
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
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.bounce()) {
                        Icon(Icons.Default.ArrowBack, "Regresar", tint = AlusheCream)
                    }
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = AlusheGreen,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Clientes",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = AlusheCream
                    )
                    Spacer(Modifier.weight(1f))
                    // Cantidad badge
                    if (uiState.clientes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(ShapePill)
                                .background(AlusheGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${uiState.clientes.size}",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = AlusheGreen
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (uiState.isRespaldando) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = AlusheGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.respaldarEnDrive() }, modifier = Modifier.bounce()) {
                            Icon(
                                Icons.Default.CloudUpload, "Respaldar en Drive",
                                tint = AlusheCream.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.abrirAddClienteSheet() },
                containerColor = AlusheGreen,
                contentColor = AlusheWhite,
                shape = ShapePill,
                modifier = Modifier
                    .bounce()
                    .shadow(8.dp, ShapePill, spotColor = AlusheGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(20.dp))
                    Text(
                        "Nuevo cliente",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { paddingValues ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AlusheGreen)
            }
            return@Scaffold
        }

        if (uiState.clientes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                EmptyState(
                    titulo = "Sin clientes todavía",
                    subtitulo = "Agrega un cliente para registrar sus apartados y pedidos",
                    emoji = "👥"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = "Por cobrar",
                            valor = uiState.totalPorCobrar.toMXN(),
                            modifier = Modifier.weight(1f),
                            esDestacada = true
                        )
                        StatCard(
                            label = "Pendientes",
                            valor = uiState.totalPendientes.toString(),
                            modifier = Modifier.weight(1f),
                            accentColor = AlusheInk
                        )
                    }
                }
                items(uiState.clientes, key = { it.id }) { cliente ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        ClienteListCard(
                            cliente = cliente,
                            resumen = uiState.resumenes[cliente.id],
                            onClick = { onClienteClick(cliente.id) },
                            onEdit = { viewModel.abrirEditClienteSheet(cliente) },
                            onDelete = { viewModel.eliminarCliente(cliente) }
                        )
                    }
                }
            }
        }
    }

    ClientesModals(viewModel = viewModel)
}

// ── Modals ────────────────────────────────────────────────────────────────────

@Composable
fun ClientesModals(viewModel: ClientesViewModel) {
    val showAddSheet by viewModel.showAddClienteSheet.collectAsStateWithLifecycle()
    val clienteEditando by viewModel.clienteEditando.collectAsStateWithLifecycle()

    if (showAddSheet) {
        AddClienteSheet(
            onDismiss = { viewModel.cerrarAddClienteSheet() },
            onSave = { nombre, telefono, notas ->
                viewModel.agregarCliente(nombre, telefono, notas)
                viewModel.cerrarAddClienteSheet()
            }
        )
    }

    clienteEditando?.let { cliente ->
        AddClienteSheet(
            clienteInicial = cliente,
            onDismiss = { viewModel.cerrarEditClienteSheet() },
            onSave = { nombre, telefono, notas ->
                viewModel.editarCliente(cliente.copy(nombre = nombre, telefono = telefono, notas = notas))
                viewModel.cerrarEditClienteSheet()
            }
        )
    }
}

// ── ClienteListCard ────────────────────────────────────────────────────────────

@Composable
fun ClienteListCard(
    cliente: ClienteEntity,
    resumen: ClienteResumen?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounce()
            .shadow(3.dp, ShapeCardLg, ambientColor = Color(0x08000000), spotColor = Color(0x08000000))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = AlusheWhite),
            shape = ShapeCardLg,
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar inicial
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ShapePill)
                        .background(AlusheGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        cliente.nombre.take(1).uppercase(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = AlusheGreenDark
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        cliente.nombre,
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AlusheInk
                    )
                    if (cliente.telefono.isNotBlank()) {
                        Text(
                            cliente.telefono,
                            fontFamily = DmSans,
                            fontSize = 12.sp,
                            color = AlusheInkLight
                        )
                    }
                    if (cliente.notas.isNotBlank()) {
                        Text(
                            cliente.notas,
                            fontFamily = DmSans,
                            fontSize = 12.sp,
                            color = AlusheInkMid,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (resumen != null && resumen.pendientes > 0) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(ShapePill)
                                .background(AlusheGreenSurface)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${resumen.pendientes} pendiente${if (resumen.pendientes == 1) "" else "s"} · debe ${resumen.saldo.toMXN()}",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = AlusheGreenDark
                            )
                        }
                    }
                }

                Icon(Icons.Default.ChevronRight, null, tint = AlusheInkLight, modifier = Modifier.size(20.dp))

                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, null, tint = AlusheInkMid, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, tint = AlusheRed.copy(alpha = 0.55f), modifier = Modifier.size(17.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar cliente?") },
            text = { Text("Se eliminarán \"${cliente.nombre}\" y todos sus apartados. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AlusheRed)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ── AddClienteSheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClienteSheet(
    clienteInicial: ClienteEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val esEdicion = clienteInicial != null
    var nombre by remember { mutableStateOf(clienteInicial?.nombre ?: "") }
    var telefono by remember { mutableStateOf(clienteInicial?.telefono ?: "") }
    var notas by remember { mutableStateOf(clienteInicial?.notas ?: "") }
    var errorNombre by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AlusheWhite,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (esEdicion) "Editar cliente" else "Nuevo cliente",
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = AlusheInk
            )

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it; errorNombre = false },
                label = { Text("Nombre *", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                placeholder = { Text("Ej: María González", fontFamily = DmSans) },
                isError = errorNombre,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCard,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it },
                label = { Text("Teléfono (opcional)", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                placeholder = { Text("Ej: 614 123 4567", fontFamily = DmSans) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCard,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas (opcional)", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlusheGreen,
                    focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AlusheButton(
                    text = "Cancelar",
                    onClick = onDismiss,
                    variante = ButtonVariante.OUTLINE,
                    modifier = Modifier.weight(1f)
                )
                AlusheButton(
                    text = if (esEdicion) "Guardar" else "Agregar",
                    onClick = {
                        if (nombre.isBlank()) { errorNombre = true; return@AlusheButton }
                        onSave(nombre.trim(), telefono.trim(), notas.trim())
                    },
                    variante = ButtonVariante.VERDE,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
