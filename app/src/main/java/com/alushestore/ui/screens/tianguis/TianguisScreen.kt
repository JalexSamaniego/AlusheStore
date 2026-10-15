package com.alushestore.ui.screens.tianguis

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alushestore.data.local.entities.TianguisSesionEntity
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.TianguisViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TianguisScreen(
    viewModel: TianguisViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val baseActivaId = uiState.baseActivaId
    val snackbarHostState = remember { SnackbarHostState() }

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
            if (uiState.sesionActiva != null) {
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
                            "+ Agregar venta",
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

        if (uiState.sesionActiva == null) {
            // ── Sin sesión activa ──────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                TianguisHeaderSinSesion(
                    onBack = onBack,
                    onHistorial = { viewModel.abrirHistorial() }
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(AlusheCreamDark)
                                .border(1.dp, AlusheBorder, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏪", fontSize = 40.sp)
                        }
                        Text(
                            "Sin sesión activa",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AlusheInk
                        )
                        Text(
                            "Inicia una sesión para registrar ventas\nde forma rápida y temporal",
                            fontFamily = DmSans,
                            fontSize = 14.sp,
                            color = AlusheInkLight,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        AlusheButton(
                            text = "Iniciar tianguis",
                            onClick = { viewModel.abrirIniciarSheet() },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                }
            }
        } else {
            // ── Sesión activa ──────────────────────────────────────────────────
            val sesion = uiState.sesionActiva!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    TianguisHeaderConSesion(
                        sesion = sesion,
                        onBack = onBack,
                        onHistorial = { viewModel.abrirHistorial() },
                        onEditarNombre = { nuevoNombre -> viewModel.editarNombreSesion(nuevoNombre) }
                    )
                }

                item {
                    // Total grande
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlusheCream)
                            .padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Total tianguis",
                            fontFamily = DmSans,
                            fontSize = 13.sp,
                            color = AlusheInkLight,
                            letterSpacing = 0.5.sp
                        )
                        AnimatedMoneyText(
                            monto = uiState.totalSesion,
                            fontSize = 46.sp,
                            color = AlusheGreen,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "Inicio: ${sesion.fechaInicio.toHora()}",
                            fontFamily = DmSans,
                            fontSize = 12.sp,
                            color = AlusheInkLight
                        )
                    }
                }

                item {
                    // Sección ventas
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
                                "Ventas de esta sesión",
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

                if (uiState.ventasSesion.isEmpty()) {
                    item {
                        Box(modifier = Modifier.background(AlusheCream)) {
                            EmptyState(
                                titulo = "Sin ventas aún",
                                subtitulo = "Toca el botón verde para agregar\ntu primera venta del tianguis"
                            )
                        }
                    }
                } else {
                    items(uiState.ventasSesion, key = { it.id }) { venta ->
                        Box(
                            modifier = Modifier
                                .animateItemPlacement()
                                .background(AlusheCream)
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                        ) {
                            VentaTianguisCard(
                                venta = venta,
                                onDelete = { viewModel.eliminarVenta(venta.id) },
                                onEdit = { viewModel.abrirEditSheet(venta) }
                            )
                        }
                    }
                }

                item {
                    // Botón Cerrar sesión
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlusheCream)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        AlusheButton(
                            text = "Cerrar sesión",
                            onClick = { viewModel.abrirCerrarSheet() },
                            modifier = Modifier.fillMaxWidth(),
                            variante = ButtonVariante.NEGRO
                        )
                    }
                }
            }
        }
    }

    // ── Sheets ──────────────────────────────────────────────────────────────────

    TianguisModals(viewModel = viewModel)
}

@Composable
fun TianguisModals(viewModel: TianguisViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showAddSheet by viewModel.showAddSheet.collectAsStateWithLifecycle()
    val showIniciarSheet by viewModel.showIniciarSheet.collectAsStateWithLifecycle()
    val ventaEditando by viewModel.ventaEditando.collectAsStateWithLifecycle()
    val baseActivaId = uiState.baseActivaId

    if (showIniciarSheet) {
        IniciarSesionSheet(
            onDismiss = { viewModel.cerrarIniciarSheet() },
            onIniciar = { nombre -> viewModel.iniciarSesion(nombre) }
        )
    }

    if (showAddSheet) {
        AddVentaTianguisSheet(
            onDismiss = { viewModel.cerrarAddSheet() },
            onSave = { descripcion, monto ->
                viewModel.agregarVenta(descripcion, monto)
                viewModel.cerrarAddSheet()
            }
        )
    }

    ventaEditando?.let { venta ->
        EditVentaTianguisSheet(
            venta = venta,
            onDismiss = { viewModel.cerrarEditSheet() },
            onSave = { desc, monto ->
                viewModel.editarVenta(venta.copy(descripcion = desc, monto = monto))
                viewModel.cerrarEditSheet()
            }
        )
    }

    if (uiState.mostrarCerrarSheet) {
        uiState.sesionActiva?.let { sesion ->
            CerrarSesionSheet(
                sesion = sesion,
                totalSesion = uiState.totalSesion,
                cantidadVentas = uiState.cantidadVentas,
                ventaMasAlta = uiState.ventaMasAlta,
                tieneBaseActiva = baseActivaId != null,
                onDismiss = { viewModel.cerrarCerrarSheet() },
                onGuardar = { baseActivaId?.let { id -> viewModel.cerrarYGuardar(id) } },
                onDescartar = { viewModel.cerrarYDescartar() }
            )
        }
    }

    if (uiState.mostrarHistorial) {
        HistorialSesionesSheet(
            sesiones = uiState.historialSesiones.filter { !it.activa },
            onDismiss = { viewModel.cerrarHistorial() },
            onVerDetalle = { sesion -> viewModel.abrirDetalleSesion(sesion) }
        )
    }

    uiState.sesionDetalle?.let { sesion ->
        DetalleSesionSheet(
            sesion = sesion,
            ventas = uiState.ventasDetalle,
            onDismiss = { viewModel.cerrarDetalleSesion() }
        )
    }
}

// ── Headers ───────────────────────────────────────────────────────────────────

@Composable
fun TianguisHeaderSinSesion(onBack: () -> Unit, onHistorial: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlusheInk)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.bounce()) {
                Icon(Icons.Default.ArrowBack, "Regresar", tint = AlusheCream.copy(alpha = 0.8f))
            }
            Text(
                "Tianguis",
                fontFamily = Montserrat,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = AlusheCream,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onHistorial, modifier = Modifier.bounce()) {
                Icon(Icons.Default.History, "Historial", tint = AlusheCream.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun TianguisHeaderConSesion(
    sesion: TianguisSesionEntity,
    onBack: () -> Unit,
    onHistorial: () -> Unit,
    onEditarNombre: (String) -> Unit
) {
    var mostrarEditDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlusheInk)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.bounce()) {
                Icon(Icons.Default.ArrowBack, "Regresar", tint = AlusheCream.copy(alpha = 0.8f))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .bounceClickable { mostrarEditDialog = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    sesion.nombre,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = AlusheCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.Edit,
                    "Editar nombre",
                    tint = AlusheCream.copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(onClick = onHistorial, modifier = Modifier.bounce()) {
                Icon(Icons.Default.History, "Historial", tint = AlusheCream.copy(alpha = 0.7f))
            }
        }
    }

    if (mostrarEditDialog) {
        EditarNombreDialog(
            nombreActual = sesion.nombre,
            onDismiss = { mostrarEditDialog = false },
            onGuardar = { nuevoNombre ->
                onEditarNombre(nuevoNombre)
                mostrarEditDialog = false
            }
        )
    }
}

// ── VentaTianguisCard ─────────────────────────────────────────────────────────

@Composable
fun VentaTianguisCard(
    venta: TianguisVentaEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bounce()
            .shadow(3.dp, ShapeCard, ambientColor = Color(0x10000000), spotColor = Color(0x10000000))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit() },
            colors = CardDefaults.cardColors(containerColor = AlusheWhite),
            shape = ShapeCard,
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ShapeChip)
                        .background(AlusheCreamDark)
                        .border(1.dp, AlusheBorder, ShapeChip),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = venta.descripcion.firstOrNull()?.uppercase() ?: "V",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = AlusheGreen
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = venta.descripcion,
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = AlusheInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = venta.fecha.toFechaCorta(),
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkLight
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = venta.monto.toMXN(),
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = AlusheGreen
                )
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = AlusheInkLight,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = AlusheRed.copy(alpha = 0.55f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = ShapeCardLg,
            title = {
                Text("¿Eliminar venta?", fontFamily = Montserrat, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Se eliminará \"${venta.descripcion}\" por ${venta.monto.toMXN()}.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AlusheRed)
                ) {
                    Text("Eliminar", fontFamily = Montserrat, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar", fontFamily = Montserrat)
                }
            }
        )
    }
}

// ── IniciarSesionSheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IniciarSesionSheet(onDismiss: () -> Unit, onIniciar: (String) -> Unit) {
    val fechaDefault = remember {
        SimpleDateFormat("dd/MM", Locale("es", "MX")).format(Date())
    }
    var nombre by remember { mutableStateOf("Tianguis $fechaDefault") }
    var errorNombre by remember { mutableStateOf(false) }
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
                        .width(4.dp).height(28.dp)
                        .clip(ShapePill).background(AlusheGreen)
                )
                Text(
                    "Nueva sesión",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it; errorNombre = false },
                label = { Text("Nombre de la sesión", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorNombre,
                supportingText = if (errorNombre) {
                    { Text("Ingresa un nombre", fontFamily = DmSans) }
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
                    text = "Iniciar",
                    onClick = {
                        errorNombre = nombre.isBlank()
                        if (!errorNombre) onIniciar(nombre.trim())
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── AddVentaTianguisSheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVentaTianguisSheet(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var descripcion by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var errorDesc by remember { mutableStateOf(false) }
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
                    .clip(ShapePill).background(AlusheBorder)
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
                        .width(4.dp).height(28.dp)
                        .clip(ShapePill).background(AlusheGreen)
                )
                Text(
                    "Agregar venta",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
            }

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it; errorDesc = false },
                label = { Text("Descripción", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                placeholder = { Text("Ej: Blusa bordada", fontFamily = DmSans) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorDesc,
                supportingText = if (errorDesc) {
                    { Text("Ingresa una descripción", fontFamily = DmSans) }
                } else null,
                singleLine = true,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite, focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen, focusedLabelColor = AlusheGreen,
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
                    unfocusedContainerColor = AlusheWhite, focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen, focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

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
                        val m = monto.toDoubleOrNull()
                        errorDesc = descripcion.isBlank()
                        errorMonto = m == null || m <= 0
                        if (!errorDesc && !errorMonto) onSave(descripcion.trim(), m!!)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── EditVentaTianguisSheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVentaTianguisSheet(
    venta: TianguisVentaEntity,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var descripcion by remember { mutableStateOf(venta.descripcion) }
    var monto by remember { mutableStateOf(venta.monto.toString()) }
    var errorDesc by remember { mutableStateOf(false) }
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
                    .clip(ShapePill).background(AlusheBorder)
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
                        .width(4.dp).height(28.dp)
                        .clip(ShapePill).background(AlusheGreen)
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
                value = descripcion,
                onValueChange = { descripcion = it; errorDesc = false },
                label = { Text("Descripción", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorDesc,
                supportingText = if (errorDesc) {
                    { Text("Ingresa una descripción", fontFamily = DmSans) }
                } else null,
                singleLine = true,
                shape = ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = AlusheWhite, focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen, focusedLabelColor = AlusheGreen,
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
                    unfocusedContainerColor = AlusheWhite, focusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen, focusedLabelColor = AlusheGreen,
                    cursorColor = AlusheGreen
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlusheButton(
                    text = "Cancelar", onClick = onDismiss,
                    modifier = Modifier.weight(1f), variante = ButtonVariante.OUTLINE
                )
                AlusheButton(
                    text = "Guardar",
                    onClick = {
                        val m = monto.toDoubleOrNull()
                        errorDesc = descripcion.isBlank()
                        errorMonto = m == null || m <= 0
                        if (!errorDesc && !errorMonto) onSave(descripcion.trim(), m!!)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── CerrarSesionSheet — resumen ticket ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CerrarSesionSheet(
    sesion: TianguisSesionEntity,
    totalSesion: Double,
    cantidadVentas: Int,
    ventaMasAlta: Double,
    tieneBaseActiva: Boolean,
    onDismiss: () -> Unit,
    onGuardar: () -> Unit,
    onDescartar: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val horaFin = remember { System.currentTimeMillis() }

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
                    .clip(ShapePill).background(AlusheBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp).height(28.dp)
                        .clip(ShapePill).background(AlusheGreen)
                )
                Text(
                    "Resumen de sesión",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
            }

            // Ticket card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AlusheWhite),
                shape = ShapeCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        sesion.nombre,
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = AlusheInk
                    )

                    Text(
                        totalSesion.toMXN(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 36.sp,
                        color = AlusheGreen
                    )

                    HorizontalDivider(color = AlusheBorder)

                    TicketRow("Ventas", "$cantidadVentas")
                    TicketRow("Inicio", sesion.fechaInicio.toHora())
                    TicketRow("Fin", horaFin.toHora())
                    if (ventaMasAlta > 0) TicketRow("Venta más alta", ventaMasAlta.toMXN())
                }
            }

            if (!tieneBaseActiva) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeChip)
                        .background(AlusheRedSurface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null,
                        tint = AlusheRed, modifier = Modifier.size(16.dp))
                    Text(
                        "Sin base activa — no se puede guardar",
                        fontFamily = DmSans, fontSize = 12.sp, color = AlusheRed
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDescartar,
                    modifier = Modifier.weight(1f),
                    shape = ShapePill,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AlusheRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlusheRed)
                ) {
                    Text("Descartar", fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                AlusheButton(
                    text = "Guardar en base",
                    onClick = onGuardar,
                    modifier = Modifier.weight(1f),
                    enabled = tieneBaseActiva
                )
            }
        }
    }
}

@Composable
private fun TicketRow(label: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontFamily = DmSans, fontSize = 13.sp, color = AlusheInkLight)
        Text(valor, fontFamily = Montserrat, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, color = AlusheInk)
    }
}

// ── HistorialSesionesSheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialSesionesSheet(
    sesiones: List<TianguisSesionEntity>,
    onDismiss: () -> Unit,
    onVerDetalle: (TianguisSesionEntity) -> Unit
) {
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
                    .clip(ShapePill).background(AlusheBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp).height(28.dp)
                        .clip(ShapePill).background(AlusheGreen)
                )
                Text(
                    "Historial",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
            }

            if (sesiones.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay sesiones anteriores",
                        fontFamily = DmSans,
                        fontSize = 14.sp,
                        color = AlusheInkLight
                    )
                }
            } else {
                sesiones.forEach { sesion ->
                    HistorialSesionCard(
                        sesion = sesion,
                        onClick = { onVerDetalle(sesion) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistorialSesionCard(sesion: TianguisSesionEntity, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, ShapeCard, ambientColor = Color(0x0A000000), spotColor = Color(0x0A000000))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = AlusheWhite),
            shape = ShapeCard,
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sesion.nombre,
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = AlusheInk
                    )
                    Text(
                        sesion.fechaInicio.toFechaCorta(),
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkLight
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(if (sesion.guardadaEnBase) AlusheGreenSurface else AlusheCreamDark)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (sesion.guardadaEnBase) "Guardada" else "Descartada",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        color = if (sesion.guardadaEnBase) AlusheGreenDark else AlusheInkLight,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = AlusheInkLight,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── DetalleSesionSheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleSesionSheet(
    sesion: TianguisSesionEntity,
    ventas: List<TianguisVentaEntity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val total = ventas.sumOf { it.monto }

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
                    .clip(ShapePill).background(AlusheBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp).height(28.dp)
                        .clip(ShapePill).background(AlusheGreen)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sesion.nombre,
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = AlusheInk
                    )
                    Text(
                        sesion.fechaInicio.toFechaLarga(),
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkLight
                    )
                }
                Text(
                    total.toMXN(),
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = AlusheGreen
                )
            }

            HorizontalDivider(color = AlusheBorder)

            if (ventas.isEmpty()) {
                Text(
                    "Sin ventas registradas",
                    fontFamily = DmSans,
                    fontSize = 14.sp,
                    color = AlusheInkLight,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                ventas.forEach { venta ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                venta.descripcion,
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = AlusheInk
                            )
                            Text(
                                venta.fecha.toFechaCorta(),
                                fontFamily = DmSans,
                                fontSize = 11.sp,
                                color = AlusheInkLight
                            )
                        }
                        Text(
                            venta.monto.toMXN(),
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = AlusheGreen
                        )
                    }
                    HorizontalDivider(color = AlusheBorder.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ── EditarNombreDialog ────────────────────────────────────────────────────────

@Composable
fun EditarNombreDialog(
    nombreActual: String,
    onDismiss: () -> Unit,
    onGuardar: (String) -> Unit
) {
    var nombre by remember { mutableStateOf(nombreActual) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AlusheWhite,
        shape = ShapeCardLg,
        title = {
            Text("Editar nombre", fontFamily = Montserrat, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                singleLine = true,
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
        },
        confirmButton = {
            TextButton(
                onClick = { if (nombre.isNotBlank()) onGuardar(nombre.trim()) },
                colors = ButtonDefaults.textButtonColors(contentColor = AlusheGreen)
            ) {
                Text("Guardar", fontFamily = Montserrat, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", fontFamily = Montserrat)
            }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Long.toHora(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale("es", "MX"))
    return sdf.format(Date(this))
}
