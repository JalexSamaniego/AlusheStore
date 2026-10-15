package com.alushestore.ui.screens.clientes

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.EstadoApartado
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.ClienteDetalleViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ClienteDetalleScreen(
    viewModel: ClienteDetalleViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val cliente = uiState.cliente

    LaunchedEffect(uiState.mensaje) {
        uiState.mensaje?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }

    // Si el cliente ya no existe (p. ej. se eliminó), regresar
    LaunchedEffect(uiState.isLoading, cliente) {
        if (!uiState.isLoading && cliente == null) onBack()
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(ShapePill)
                            .background(AlusheGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            cliente?.nombre?.take(1)?.uppercase() ?: "",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = AlusheGreen
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            cliente?.nombre ?: "",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = AlusheCream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!cliente?.telefono.isNullOrBlank()) {
                            Text(
                                cliente!!.telefono,
                                fontFamily = DmSans,
                                fontSize = 12.sp,
                                color = AlusheCream.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.abrirAddApartadoSheet() },
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
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Text(
                        "Apartado / pedido",
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
                        valor = uiState.saldoPendiente.toMXN(),
                        modifier = Modifier.weight(1f),
                        esDestacada = true
                    )
                    StatCard(
                        label = "Pendientes",
                        valor = uiState.countPendientes.toString(),
                        modifier = Modifier.weight(1f),
                        accentColor = AlusheInk
                    )
                }
            }

            if (!cliente?.notas.isNullOrBlank()) {
                item {
                    Text(
                        cliente!!.notas,
                        fontFamily = DmSans,
                        fontSize = 13.sp,
                        color = AlusheInkMid,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (uiState.apartados.isEmpty()) {
                item {
                    EmptyState(
                        titulo = "Sin apartados ni pedidos",
                        subtitulo = "Toca el botón verde para anotar lo que este cliente aparta o pide",
                        emoji = "📦",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
            } else {
                items(uiState.apartados, key = { it.id }) { apartado ->
                    Box(modifier = Modifier.animateItemPlacement()) {
                        ApartadoCard(
                            apartado = apartado,
                            onEdit = { viewModel.abrirEditApartadoSheet(apartado) },
                            onDelete = { viewModel.eliminarApartado(apartado.id) },
                            onEntregar = { viewModel.marcarEntregado(apartado.id) },
                            onCancelar = { viewModel.marcarCancelado(apartado) },
                            onReactivar = { viewModel.reactivar(apartado) }
                        )
                    }
                }
            }
        }
    }

    ClienteDetalleModals(viewModel = viewModel)
}

// ── Modals ────────────────────────────────────────────────────────────────────

@Composable
private fun ClienteDetalleModals(viewModel: ClienteDetalleViewModel) {
    val showAddSheet by viewModel.showAddApartadoSheet.collectAsStateWithLifecycle()
    val apartadoEditando by viewModel.apartadoEditando.collectAsStateWithLifecycle()

    if (showAddSheet) {
        ApartadoSheet(
            onDismiss = { viewModel.cerrarAddApartadoSheet() },
            onSave = { descripcion, monto, anticipo, notas, tipo ->
                viewModel.agregarApartado(descripcion, monto, anticipo, notas, tipo)
                viewModel.cerrarAddApartadoSheet()
            }
        )
    }

    apartadoEditando?.let { apartado ->
        ApartadoSheet(
            apartadoInicial = apartado,
            onDismiss = { viewModel.cerrarEditApartadoSheet() },
            onSave = { descripcion, monto, anticipo, notas, tipo ->
                viewModel.editarApartado(
                    apartado.copy(
                        descripcion = descripcion,
                        monto = monto,
                        anticipo = anticipo,
                        notas = notas,
                        tipo = tipo
                    )
                )
                viewModel.cerrarEditApartadoSheet()
            }
        )
    }
}

// ── ApartadoCard ──────────────────────────────────────────────────────────────

@Composable
private fun ApartadoCard(
    apartado: ApartadoEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEntregar: () -> Unit,
    onCancelar: () -> Unit,
    onReactivar: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val pendiente = apartado.estado == EstadoApartado.PENDIENTE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounce()
            .shadow(3.dp, ShapeCardLg, ambientColor = Color(0x08000000), spotColor = Color(0x08000000))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (pendiente) AlusheWhite else AlusheWhite.copy(alpha = 0.7f)
            ),
            shape = ShapeCardLg,
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Badges + fecha
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Pill(
                        text = if (apartado.tipo == TipoApartado.PEDIDO) "Pedido" else "Apartado",
                        background = AlusheCreamDark,
                        color = AlusheInkMid
                    )
                    when (apartado.estado) {
                        EstadoApartado.PENDIENTE -> Pill("Pendiente", AlusheGreenSurface, AlusheGreenDark)
                        EstadoApartado.ENTREGADO -> Pill("Entregado", AlusheInk, AlusheCream)
                        EstadoApartado.CANCELADO -> Pill("Cancelado", AlusheRedSurface, AlusheRed)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        apartado.fecha.toFechaCorta(),
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheInkLight
                    )
                }

                Text(
                    apartado.descripcion,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AlusheInk
                )

                if (apartado.notas.isNotBlank()) {
                    Text(
                        apartado.notas,
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkMid
                    )
                }

                // Montos
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Monto("Total", apartado.monto.toMXN(), AlusheInk, Modifier.weight(1f))
                    Monto("Anticipo", apartado.anticipo.toMXN(), AlusheInkMid, Modifier.weight(1f))
                    Monto(
                        "Resta",
                        if (pendiente) apartado.saldo.toMXN() else "—",
                        if (pendiente) AlusheGreenDark else AlusheInkLight,
                        Modifier.weight(1f)
                    )
                }

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pendiente) {
                        TextButton(
                            onClick = onEntregar,
                            colors = ButtonDefaults.textButtonColors(contentColor = AlusheGreenDark)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Entregado", fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        TextButton(
                            onClick = onCancelar,
                            colors = ButtonDefaults.textButtonColors(contentColor = AlusheInkLight)
                        ) {
                            Text("Cancelar", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    } else {
                        TextButton(
                            onClick = onReactivar,
                            colors = ButtonDefaults.textButtonColors(contentColor = AlusheInkMid)
                        ) {
                            Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reactivar", fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "Editar", tint = AlusheInkMid, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = AlusheRed.copy(alpha = 0.55f), modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar registro?") },
            text = { Text("Se eliminará \"${apartado.descripcion}\" por ${apartado.monto.toMXN()}. Esta acción no se puede deshacer.") },
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

@Composable
private fun Pill(text: String, background: Color, color: Color) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text.uppercase(),
            fontFamily = Montserrat,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            color = color
        )
    }
}

@Composable
private fun Monto(label: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            fontFamily = Montserrat,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            color = AlusheInkLight
        )
        Text(
            valor,
            fontFamily = Montserrat,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── ApartadoSheet (agregar / editar) ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApartadoSheet(
    apartadoInicial: ApartadoEntity? = null,
    onDismiss: () -> Unit,
    onSave: (descripcion: String, monto: Double, anticipo: Double, notas: String, tipo: TipoApartado) -> Unit
) {
    val esEdicion = apartadoInicial != null
    var tipo by remember { mutableStateOf(apartadoInicial?.tipo ?: TipoApartado.APARTADO) }
    var descripcion by remember { mutableStateOf(apartadoInicial?.descripcion ?: "") }
    var monto by remember { mutableStateOf(apartadoInicial?.monto?.toInputText() ?: "") }
    var anticipo by remember {
        mutableStateOf(apartadoInicial?.anticipo?.takeIf { it > 0 }?.toInputText() ?: "")
    }
    var notas by remember { mutableStateOf(apartadoInicial?.notas ?: "") }
    var errorDescripcion by remember { mutableStateOf(false) }
    var errorMonto by remember { mutableStateOf(false) }
    var errorAnticipo by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = AlusheWhite,
        focusedContainerColor = AlusheWhite,
        focusedBorderColor = AlusheGreen,
        focusedLabelColor = AlusheGreen,
        cursorColor = AlusheGreen
    )

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
                    if (esEdicion) "Editar registro" else "Nuevo registro",
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = AlusheInk
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoApartado.values().forEach { opcion ->
                    FilterChip(
                        selected = tipo == opcion,
                        onClick = { tipo = opcion },
                        label = {
                            Text(
                                if (opcion == TipoApartado.PEDIDO) "Pedido" else "Apartado",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        },
                        shape = ShapePill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlusheGreen,
                            selectedLabelColor = AlusheWhite
                        )
                    )
                }
            }

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it; errorDescripcion = false },
                label = { Text("Qué aparta / pidió *", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                placeholder = { Text("Ej: Bolsa negra, 2 blusas talla M", fontFamily = DmSans) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorDescripcion,
                supportingText = if (errorDescripcion) {
                    { Text("Escribe qué es", fontFamily = DmSans) }
                } else null,
                shape = ShapeCard,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it; errorMonto = false; errorAnticipo = false },
                    label = { Text("Total (MXN) *", fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    placeholder = { Text("0.00", fontFamily = DmSans) },
                    modifier = Modifier.weight(1f),
                    isError = errorMonto,
                    supportingText = if (errorMonto) {
                        { Text("Monto inválido", fontFamily = DmSans) }
                    } else null,
                    singleLine = true,
                    shape = ShapeCard,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = anticipo,
                    onValueChange = { anticipo = it; errorAnticipo = false },
                    label = { Text("Anticipo", fontFamily = Montserrat,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    placeholder = { Text("0.00", fontFamily = DmSans) },
                    modifier = Modifier.weight(1f),
                    isError = errorAnticipo,
                    supportingText = if (errorAnticipo) {
                        { Text("No puede pasar del total", fontFamily = DmSans) }
                    } else null,
                    singleLine = true,
                    shape = ShapeCard,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors
                )
            }

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas (opcional)", fontFamily = Montserrat,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = ShapeCard,
                colors = fieldColors
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
                    text = if (esEdicion) "Guardar" else "Agregar",
                    onClick = {
                        val montoD = monto.toMontoOrNull()
                        val anticipoD = if (anticipo.isBlank()) 0.0 else anticipo.toMontoOrNull()
                        errorDescripcion = descripcion.isBlank()
                        errorMonto = montoD == null || montoD <= 0
                        errorAnticipo = anticipoD == null || anticipoD < 0 ||
                            (montoD != null && anticipoD > montoD)
                        if (!errorDescripcion && !errorMonto && !errorAnticipo) {
                            onSave(descripcion.trim(), montoD!!, anticipoD!!, notas.trim(), tipo)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variante = ButtonVariante.VERDE
                )
            }
        }
    }
}

/** 150.0 → "150", 150.5 → "150.5" (para mostrar en el campo de texto) */
private fun Double.toInputText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

/** Acepta coma o punto decimal */
private fun String.toMontoOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
