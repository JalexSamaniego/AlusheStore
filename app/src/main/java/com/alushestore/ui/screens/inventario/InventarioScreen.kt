package com.alushestore.ui.screens.inventario

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.alushestore.data.local.entities.LoteEntity
import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.repository.LoteRentabilidad
import com.alushestore.domain.OrigenProducto
import com.alushestore.domain.ProductoUnificado
import com.alushestore.ui.components.AlusheBarcodeScanner
import com.alushestore.ui.components.ShapeCard
import com.alushestore.ui.components.ShapeCardLg
import com.alushestore.ui.components.ShapeChip
import com.alushestore.ui.components.ShapePill
import com.alushestore.ui.components.bounce
import com.alushestore.ui.components.bounceClickable
import com.alushestore.ui.components.toFechaCorta
import com.alushestore.ui.components.toMXN
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.InventarioUiState
import com.alushestore.viewmodel.InventarioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioScreen(
    viewModel: InventarioViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showAddProductoSheet by viewModel.showAddProductoSheet.collectAsStateWithLifecycle()
    val productoEditando by viewModel.productoEditando.collectAsStateWithLifecycle()
    val showAddLoteSheet by viewModel.showAddLoteSheet.collectAsStateWithLifecycle()
    val loteEditando by viewModel.loteEditando.collectAsStateWithLifecycle()
    val pidiendoEmparejar by viewModel.pidiendoEmparejar.collectAsStateWithLifecycle()
    val emparejando by viewModel.emparejando.collectAsStateWithLifecycle()
    val productoParaVender by viewModel.productoParaVender.collectAsStateWithLifecycle()
    val vendiendo by viewModel.vendiendo.collectAsStateWithLifecycle()
    val emparejado by viewModel.emparejado.collectAsStateWithLifecycle()

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
        topBar = {
            InventarioTopBar(
                modoPrivacidad = uiState.modoPrivacidad,
                onTogglePrivacidad = { viewModel.toggleModoPrivacidad() },
                onBack = onBack,
                onDriveBackup = { viewModel.respaldarEnDrive() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.pestañaSeleccionada == 0) {
                        viewModel.abrirAddProductoSheet()
                    } else {
                        viewModel.abrirAddLoteSheet()
                    }
                },
                containerColor = AlusheGreen,
                contentColor = AlusheWhite,
                shape = ShapePill,
                modifier = Modifier
                    .navigationBarsPadding()
                    .bounce()
                    .shadow(elevation = 8.dp, shape = ShapePill, spotColor = AlusheGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (uiState.pestañaSeleccionada == 0) "Nuevo producto" else "Nuevo lote",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Pestañas de navegación Catálogo vs Lotes
            TabRow(
                selectedTabIndex = uiState.pestañaSeleccionada,
                containerColor = AlusheInk,
                contentColor = AlusheCream,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.pestañaSeleccionada]),
                        color = AlusheGreen,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = uiState.pestañaSeleccionada == 0,
                    onClick = { viewModel.setPestaña(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Catálogo & Stock", fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    selectedContentColor = AlusheGreen,
                    unselectedContentColor = AlusheCream.copy(alpha = 0.65f)
                )
                Tab(
                    selected = uiState.pestañaSeleccionada == 1,
                    onClick = { viewModel.setPestaña(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Lotes e Inversión", fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    selectedContentColor = AlusheGreen,
                    unselectedContentColor = AlusheCream.copy(alpha = 0.65f)
                )
            }

            AnimatedContent(
                targetState = uiState.pestañaSeleccionada,
                label = "tabContent"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> CatalogoTabContent(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                    1 -> LotesTabContent(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Emparejamiento del teléfono con el almacén compartido
    if (pidiendoEmparejar) {
        EmparejarDialog(
            emparejando = emparejando,
            onEmparejar = { correo, pass -> viewModel.emparejar(correo, pass) },
            onDismiss = { viewModel.cerrarEmparejamiento() }
        )
    }

    // Administrar un producto del catálogo compartido
    productoParaVender?.let { prod ->
        AdminProductoWebDialog(
            producto = prod,
            guardando = vendiendo,
            onGuardar = { stock, precio, costo ->
                viewModel.guardarProductoWeb(prod, stock, precio, costo)
            },
            onDismiss = { viewModel.cerrarVentaWeb() }
        )
    }

    // Modal de Agregar Producto
    if (showAddProductoSheet) {
        ProductoFormSheet(
            lotes = uiState.lotesRaw,
            categoriasExistentes = uiState.categorias,
            onDismiss = { viewModel.cerrarAddProductoSheet() },
            onAutoSku = { onResult -> viewModel.autogenerarSku(onResult) },
            puedePublicar = emparejado,
            onSave = { sku, nom, cat, loteId, costo, precio, stock, ubicacion, publicar ->
                viewModel.agregarProducto(sku, nom, cat, loteId, costo, precio, stock, ubicacion, publicar)
            }
        )
    }

    // Modal de Editar Producto
    productoEditando?.let { prod ->
        ProductoFormSheet(
            producto = prod,
            lotes = uiState.lotesRaw,
            categoriasExistentes = uiState.categorias,
            onDismiss = { viewModel.cerrarEditProductoSheet() },
            onAutoSku = { onResult -> viewModel.autogenerarSku(onResult) },
            onSave = { sku, nom, cat, loteId, costo, precio, stock, ubicacion, _ ->
                viewModel.editarProducto(
                    prod.copy(
                        sku = sku,
                        nombre = nom,
                        categoria = cat,
                        loteId = loteId,
                        costoUnitario = costo,
                        precioVenta = precio,
                        stockDisponible = stock,
                        ubicacion = ubicacion
                    )
                )
            },
            onDelete = { viewModel.eliminarProducto(prod); viewModel.cerrarEditProductoSheet() }
        )
    }

    // Modal de Agregar Lote
    if (showAddLoteSheet) {
        LoteFormSheet(
            onDismiss = { viewModel.cerrarAddLoteSheet() },
            onCalcularProrrateo = { envio, pzas -> viewModel.calcularProrrateo(envio, pzas) },
            onSave = { nom, merc, envio, fecha, notas ->
                viewModel.agregarLote(nom, merc, envio, fecha, notas)
            }
        )
    }

    // Modal de Editar Lote
    loteEditando?.let { lote ->
        LoteFormSheet(
            lote = lote,
            onDismiss = { viewModel.cerrarEditLoteSheet() },
            onCalcularProrrateo = { envio, pzas -> viewModel.calcularProrrateo(envio, pzas) },
            onSave = { nom, merc, envio, fecha, notas ->
                viewModel.editarLote(
                    lote.copy(
                        nombre = nom,
                        inversionMercancia = merc,
                        gastosExtraEnvio = envio,
                        fechaCompra = fecha,
                        notas = notas
                    )
                )
            },
            onDelete = { viewModel.eliminarLote(lote); viewModel.cerrarEditLoteSheet() }
        )
    }
}

// ── TopBar con Modo Privacidad ────────────────────────────────────────────────

@Composable
private fun InventarioTopBar(
    modoPrivacidad: Boolean,
    onTogglePrivacidad: () -> Unit,
    onBack: () -> Unit,
    onDriveBackup: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlusheInk)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.bounce()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = AlusheCream
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inventario & POS",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = AlusheCream
                )
                Text(
                    text = if (modoPrivacidad) "Modo Privacidad Activo (Costos ocultos)" else "Control de Stock y Lotes",
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    color = if (modoPrivacidad) AlusheGreen else AlusheCream.copy(alpha = 0.65f)
                )
            }

            // Botón de Modo Privacidad ("Ojo")
            IconButton(
                onClick = onTogglePrivacidad,
                modifier = Modifier
                    .bounce()
                    .clip(CircleShape)
                    .background(if (modoPrivacidad) AlusheGreenSurface.copy(alpha = 0.2f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = if (modoPrivacidad) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Modo Privacidad",
                    tint = if (modoPrivacidad) AlusheGreen else AlusheCream.copy(alpha = 0.8f)
                )
            }

            // Respaldo en Drive
            IconButton(onClick = onDriveBackup, modifier = Modifier.bounce()) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Respaldar Inventario en Drive",
                    tint = AlusheCream.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Pestaña 1: Catálogo y Stock ───────────────────────────────────────────────

@Composable
private fun CatalogoTabContent(
    uiState: InventarioUiState,
    viewModel: InventarioViewModel
) {
    // Catálogo único: lo del teléfono y lo de alushe.store en la misma lista
    val productos by viewModel.catalogoUnificado.collectAsStateWithLifecycle()
    val categorias by viewModel.categoriasUnificadas.collectAsStateWithLifecycle()
    val cargandoWeb by viewModel.cargandoCatalogoWeb.collectAsStateWithLifecycle()
    val errorWeb by viewModel.errorCatalogoWeb.collectAsStateWithLifecycle()
    val emparejado by viewModel.emparejado.collectAsStateWithLifecycle()
    val correoSesion by viewModel.correoSesion.collectAsStateWithLifecycle()

    // Los totales de arriba cuentan lo que se está viendo, de los dos orígenes.
    // Antes sólo sumaban el inventario local y decían 0 aunque la lista trajera 144.
    val piezasDisponibles = remember(productos) { productos.sumOf { it.stockDisponible } }
    val piezasApartadas = remember(productos) { productos.sumOf { it.stockApartado } }
    val valorInventario = remember(productos) { productos.sumOf { it.costo * it.stockDisponible } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlusheCream)
    ) {
        // Resumen superior en StatCards elegantes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                shape = ShapeCard,
                colors = CardDefaults.cardColors(containerColor = AlusheWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "Stock disponible",
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheInkMid
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$piezasDisponibles pzas",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AlusheInk
                    )
                    if (piezasApartadas > 0) {
                        Text(
                            text = "+$piezasApartadas apartadas",
                            fontFamily = DmSans,
                            fontSize = 10.sp,
                            color = AlusheGreenDark
                        )
                    }
                }
            }

            Card(
                shape = ShapeCard,
                colors = CardDefaults.cardColors(containerColor = AlusheWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "Valor de inventario",
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheInkMid
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (uiState.modoPrivacidad) "••••••••" else valorInventario.toMXN(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (uiState.modoPrivacidad) AlusheInkMid else AlusheGreenDark
                    )
                    Text(
                        text = "Mercancía activa",
                        fontFamily = DmSans,
                        fontSize = 10.sp,
                        color = AlusheInkLight
                    )
                }
            }
        }

        // Buscador instantáneo + botón de Escáner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Buscar por nombre, SKU o categoría...", fontFamily = DmSans, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AlusheInkLight)
                },
                trailingIcon = {
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = AlusheInkLight)
                        }
                    }
                },
                singleLine = true,
                shape = ShapePill,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AlusheWhite,
                    unfocusedContainerColor = AlusheWhite,
                    focusedBorderColor = AlusheGreen,
                    unfocusedBorderColor = AlusheBorder
                )
            )

            // Botón de toggle escáner
            IconButton(
                onClick = { viewModel.toggleScanner() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (uiState.scannerActivo) AlusheGreen else AlusheInk)
                    .bounce()
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Escanear",
                    tint = if (uiState.scannerActivo) AlusheWhite else AlusheCream
                )
            }
        }

        // Visor de Cámara de Escaneo Colapsable si está activo
        AnimatedVisibility(
            visible = uiState.scannerActivo,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                AlusheBarcodeScanner(
                    onBarcodeScanned = { code -> viewModel.onBarcodeScanned(code) },
                    onClose = { viewModel.setScannerActivo(false) }
                )
            }
        }

        // Estado del catálogo compartido
        EstadoCatalogoWeb(
            cargando = cargandoWeb,
            error = errorWeb,
            emparejado = emparejado,
            correo = correoSesion,
            onReintentar = { viewModel.cargarCatalogoWeb() },
            onEmparejar = { viewModel.abrirEmparejamiento() }
        )

        // Categorías en chips horizontales
        if (categorias.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.categoriaFiltro == null,
                    onClick = { viewModel.setCategoriaFiltro(null) },
                    label = { Text("Todos (${productos.size})", fontFamily = Montserrat, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlusheInk,
                        selectedLabelColor = AlusheCream
                    )
                )
                categorias.forEach { cat ->
                    FilterChip(
                        selected = uiState.categoriaFiltro == cat,
                        onClick = { viewModel.setCategoriaFiltro(cat) },
                        label = { Text(cat, fontFamily = Montserrat, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlusheGreen,
                            selectedLabelColor = AlusheWhite
                        )
                    )
                }
            }
        }

        // Lista de productos
        if (productos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = AlusheInkLight.copy(alpha = 0.5f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (uiState.query.isNotBlank()) "Sin resultados para \"${uiState.query}\"" else "Catálogo vacío",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AlusheInkMid
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Toca '+ Nuevo producto' para registrar mercancía o escanea un código.",
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = AlusheInkLight,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(productos, key = { it.clave }) { producto ->
                    ProductoItemCard(
                        producto = producto,
                        modoPrivacidad = uiState.modoPrivacidad,
                        onClick = { viewModel.abrirEditDesdeUnificado(producto) }
                    )
                }

                if (productos.size <= 3 && uiState.query.isBlank()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = ShapeCard,
                            colors = CardDefaults.cardColors(containerColor = AlusheGreenSurface.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlusheGreen.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = AlusheGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Consejo AlusheStore",
                                        fontFamily = Montserrat,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AlusheGreenDark
                                    )
                                }
                                Text(
                                    text = "Organiza tus compras por Lotes de Inversión para calcular el retorno (ROI) real, prorratear gastos de envío y medir utilidades netas con precisión.",
                                    fontFamily = DmSans,
                                    fontSize = 12.sp,
                                    color = AlusheInkMid,
                                    lineHeight = 16.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { viewModel.setPestaña(1) }
                                    ) {
                                        Text(
                                            text = "Ver Lotes e Inversión →",
                                            fontFamily = Montserrat,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = AlusheGreenDark
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
}

@Composable
private fun ProductoItemCard(
    producto: ProductoUnificado,
    modoPrivacidad: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClickable { onClick() },
        shape = ShapeCard,
        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header con SKU, origen y Ubicación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (!producto.sku.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(ShapeChip)
                                .background(AlusheGreenSurface)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = producto.sku,
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = AlusheGreenDark
                            )
                        }
                    }
                    OrigenChip(producto.origen)
                }

                if (!producto.ubicacion.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = AlusheInkLight
                        )
                        Text(
                            text = producto.ubicacion,
                            fontFamily = DmSans,
                            fontSize = 11.sp,
                            color = AlusheInkLight
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Nombre y Categoría
            Text(
                text = producto.nombre,
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = AlusheInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!producto.categoria.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = producto.categoria,
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = AlusheInkLight
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = AlusheBorder, thickness = 0.8.dp)
            Spacer(Modifier.height(10.dp))

            // Footer con Stock, Precio y Margen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock disponible y apartado
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val stockColor = if (producto.stockDisponible > 0) AlusheGreen else AlusheRed
                    Box(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(stockColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${producto.stockDisponible} disp",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = stockColor
                        )
                    }

                    if (producto.stockApartado > 0) {
                        Box(
                            modifier = Modifier
                                .clip(ShapePill)
                                .background(AlusheInkMid.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${producto.stockApartado} apart",
                                fontFamily = Montserrat,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = AlusheInkMid
                            )
                        }
                    }
                }

                // Precios y Margen Financiero
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = producto.precio.toMXN(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = AlusheInk
                    )

                    if (modoPrivacidad) {
                        Text(
                            text = "Costo: •••• | Margen: ••••",
                            fontFamily = DmSans,
                            fontSize = 10.sp,
                            color = AlusheInkLight
                        )
                    } else {
                        val pct = producto.margenSobreCosto ?: 100
                        Text(
                            text = "Costo ${producto.costo.toMXN()} · +$pct%",
                            fontFamily = DmSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = AlusheGreenDark
                        )
                    }
                }
            }
        }
    }
}

/**
 * Franja delgada con el estado del catálogo compartido.
 * Sólo aparece cuando está trayendo datos o cuando algo falló; si todo va bien
 * no estorba, porque los productos ya se ven en la lista.
 */
@Composable
private fun EstadoCatalogoWeb(
    cargando: Boolean,
    error: String?,
    emparejado: Boolean,
    correo: String?,
    onReintentar: () -> Unit,
    onEmparejar: () -> Unit
) {
    if (!emparejado) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(ShapeChip)
                .background(AlusheGreenSurface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Empareja el teléfono para editar el almacén compartido",
                fontFamily = DmSans,
                fontSize = 11.sp,
                color = AlusheInkMid,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Emparejar",
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = AlusheGreenDark,
                modifier = Modifier.bounceClickable { onEmparejar() }
            )
        }
    } else if (!correo.isNullOrBlank() && !cargando && error == null) {
        Text(
            text = "Almacén compartido · $correo",
            fontFamily = DmSans,
            fontSize = 10.sp,
            color = AlusheInkLight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
    }

    when {
        error != null -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(ShapeChip)
                    .background(AlusheRedSurface)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sin conexión con alushe.store · mostrando sólo lo del teléfono",
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    color = AlusheInkMid,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Reintentar",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = AlusheRed,
                    modifier = Modifier.bounceClickable { onReintentar() }
                )
            }
        }

        cargando -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = AlusheGreen
                )
                Text(
                    text = "Sincronizando con alushe.store…",
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    color = AlusheInkLight
                )
            }
        }
    }
}

/** Marca de dónde viene el producto: del teléfono, de alushe.store, o de los dos */
@Composable
private fun OrigenChip(origen: OrigenProducto) {
    val (icono, texto, color) = when (origen) {
        OrigenProducto.LOCAL -> Triple(Icons.Default.PhoneAndroid, "Solo app", AlusheInkLight)
        OrigenProducto.AMBOS -> Triple(Icons.Default.CloudDone, "Sincronizado", AlusheGreenDark)
        OrigenProducto.WEB -> Triple(Icons.Default.CloudQueue, "Solo web", AlusheInkMid)
    }

    Row(
        modifier = Modifier
            .clip(ShapeChip)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(11.dp), tint = color)
        Text(
            text = texto,
            fontFamily = Montserrat,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = color
        )
    }
}

// ── Pestaña 2: Lotes e Inversión ──────────────────────────────────────────────

@Composable
private fun LotesTabContent(
    uiState: InventarioUiState,
    viewModel: InventarioViewModel
) {
    if (uiState.lotes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AlusheCream)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = AlusheInkLight.copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No hay lotes registrados",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AlusheInkMid
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Registra un lote de compra para controlar tu inversión, flete/envío y retorno neto.",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = AlusheInkLight,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AlusheCream),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Resumen de Inversión Global en Lotes
            item {
                val totalInvertido = uiState.lotes.sumOf { it.inversionTotal }
                val totalRecuperado = uiState.lotes.sumOf { it.totalRecuperado }
                val balanceGlobal = totalRecuperado - totalInvertido

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardLg,
                    colors = CardDefaults.cardColors(containerColor = AlusheInk),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "BALANCE GLOBAL DE LOTES",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = AlusheCream.copy(alpha = 0.65f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Invertido",
                                    fontFamily = DmSans,
                                    fontSize = 11.sp,
                                    color = AlusheCream.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = if (uiState.modoPrivacidad) "••••••••" else totalInvertido.toMXN(),
                                    fontFamily = Montserrat,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = AlusheCream
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Vendido",
                                    fontFamily = DmSans,
                                    fontSize = 11.sp,
                                    color = AlusheCream.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = if (uiState.modoPrivacidad) "••••••••" else totalRecuperado.toMXN(),
                                    fontFamily = Montserrat,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = AlusheGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (balanceGlobal >= 0) "Ganancia Neta" else "Por Recuperar",
                                    fontFamily = DmSans,
                                    fontSize = 11.sp,
                                    color = AlusheCream.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = if (uiState.modoPrivacidad) "••••••••" else balanceGlobal.toMXN(),
                                    fontFamily = Montserrat,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = if (balanceGlobal >= 0) AlusheGreen else AlusheRed
                                )
                            }
                        }
                    }
                }
            }

            items(uiState.lotes, key = { it.lote.id }) { rentabilidad ->
                LoteItemCard(
                    rentabilidad = rentabilidad,
                    modoPrivacidad = uiState.modoPrivacidad,
                    onClick = { viewModel.abrirEditLoteSheet(rentabilidad.lote) }
                )
            }
        }
    }
}

@Composable
private fun LoteItemCard(
    rentabilidad: LoteRentabilidad,
    modoPrivacidad: Boolean,
    onClick: () -> Unit
) {
    val lote = rentabilidad.lote

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClickable { onClick() },
        shape = ShapeCard,
        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lote.nombre,
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AlusheInk
                    )
                    Text(
                        text = "Comprado el ${lote.fechaCompra.toFechaCorta()}",
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheInkLight
                    )
                }

                // Badge de estado de recuperación
                val badgeBg = if (rentabilidad.estaRecuperado) AlusheGreenSurface else AlusheRedSurface
                val badgeColor = if (rentabilidad.estaRecuperado) AlusheGreenDark else AlusheRed
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(badgeBg)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (rentabilidad.estaRecuperado) "Recuperado ✓" else "En proceso",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = badgeColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Desglose de inversión y piezas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Inversión total", fontFamily = DmSans, fontSize = 11.sp, color = AlusheInkLight)
                    Text(
                        text = if (modoPrivacidad) "••••••" else lote.inversionTotal.toMXN(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AlusheInk
                    )
                    if (lote.gastosExtraEnvio > 0 && !modoPrivacidad) {
                        Text(
                            text = "Envío: ${lote.gastosExtraEnvio.toMXN()}",
                            fontFamily = DmSans,
                            fontSize = 10.sp,
                            color = AlusheInkLight
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Piezas lote", fontFamily = DmSans, fontSize = 11.sp, color = AlusheInkLight)
                    Text(
                        text = "${rentabilidad.totalPiezas} piezas",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AlusheInk
                    )
                    Text(
                        text = "${rentabilidad.piezasDisponibles} disp · ${rentabilidad.piezasApartadas} apart",
                        fontFamily = DmSans,
                        fontSize = 10.sp,
                        color = AlusheInkLight
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Recuperado", fontFamily = DmSans, fontSize = 11.sp, color = AlusheInkLight)
                    Text(
                        text = if (modoPrivacidad) "••••••" else rentabilidad.totalRecuperado.toMXN(),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AlusheGreenDark
                    )
                    Text(
                        text = "${rentabilidad.porcentajeRecuperado.toInt()}% ROI",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = AlusheGreenDark
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Barra de progreso ROI
            val progresoAnimado by androidx.compose.animation.core.animateFloatAsState(
                targetValue = (rentabilidad.porcentajeRecuperado / 100f).coerceIn(0f, 1f),
                animationSpec = androidx.compose.animation.core.tween(700),
                label = "roiProgress"
            )

            LinearProgressIndicator(
                progress = { progresoAnimado },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(ShapePill),
                color = if (rentabilidad.estaRecuperado) AlusheGreen else Color(0xFFE5A93C),
                trackColor = AlusheBorder
            )

            Spacer(Modifier.height(6.dp))

            // Mensaje de rentabilidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rentabilidad.balanceNeto >= 0) {
                    Text(
                        text = if (modoPrivacidad) "Ganancia neta generada" else "Ganancia neta: +${rentabilidad.balanceNeto.toMXN()}",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AlusheGreenDark
                    )
                } else {
                    Text(
                        text = if (modoPrivacidad) "Falta por recuperar inversión" else "Faltan ${(-rentabilidad.balanceNeto).toMXN()} para recuperar",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = AlusheInkLight
                    )
                }

                if (!lote.notas.isNullOrBlank()) {
                    Text(
                        text = lote.notas,
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = AlusheInkLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Modales / Sheets ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductoFormSheet(
    producto: ProductoEntity? = null,
    lotes: List<LoteEntity>,
    categoriasExistentes: List<String>,
    onDismiss: () -> Unit,
    onAutoSku: ((String) -> Unit) -> Unit,
    onSave: (sku: String, nombre: String, categoria: String?, loteId: Long?, costo: Double, precio: Double, stock: Int, ubicacion: String, publicarEnWeb: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Hay sesión con Supabase, así que se puede publicar en alushe.store */
    puedePublicar: Boolean = false
) {
    var publicarEnWeb by remember { mutableStateOf(false) }
    var sku by remember { mutableStateOf(producto?.sku ?: "") }
    var nombre by remember { mutableStateOf(producto?.nombre ?: "") }
    var categoria by remember { mutableStateOf(producto?.categoria ?: "") }
    var selectedLoteId by remember { mutableStateOf(producto?.loteId) }
    var costoUnitarioStr by remember { mutableStateOf(producto?.costoUnitario?.toString() ?: "") }
    var precioVentaStr by remember { mutableStateOf(producto?.precioVenta?.toString() ?: "") }
    var stockStr by remember { mutableStateOf(producto?.stockDisponible?.toString() ?: "1") }
    var ubicacion by remember { mutableStateOf(producto?.ubicacion ?: "General") }

    var scannerModalOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (producto == null && sku.isBlank()) {
            onAutoSku { sku = it }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AlusheWhite,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (producto == null) "Nuevo Producto" else "Editar Producto",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AlusheInk
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AlusheRed)
                    }
                }
            }

            // SKU + Botón de generar y Botón de escanear código físico
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it.uppercase() },
                    label = { Text("SKU / Código de Barras") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = ShapeCard
                )

                IconButton(
                    onClick = { onAutoSku { sku = it } },
                    modifier = Modifier
                        .clip(ShapeCard)
                        .background(AlusheGreenSurface)
                ) {
                    Icon(Icons.Default.Autorenew, contentDescription = "Generar SKU", tint = AlusheGreenDark)
                }

                IconButton(
                    onClick = { scannerModalOpen = !scannerModalOpen },
                    modifier = Modifier
                        .clip(ShapeCard)
                        .background(AlusheInk)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear", tint = AlusheCream)
                }
            }

            if (scannerModalOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AlusheBarcodeScanner(
                        onBarcodeScanned = { code ->
                            sku = code
                            scannerModalOpen = false
                        },
                        onClose = { scannerModalOpen = false }
                    )
                }
            }

            // Nombre
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del artículo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = ShapeCard
            )

            // Categoría y Ubicación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    label = { Text("Categoría (opcional)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = ShapeCard
                )
                OutlinedTextField(
                    value = ubicacion,
                    onValueChange = { ubicacion = it },
                    label = { Text("Ubicación") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = ShapeCard
                )
            }

            if (categoriasExistentes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoriasExistentes.take(6).forEach { cat ->
                        FilterChip(
                            selected = categoria.equals(cat, ignoreCase = true),
                            onClick = { categoria = cat },
                            label = { Text(cat, fontSize = 11.sp, fontFamily = Montserrat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AlusheGreenSurface,
                                selectedLabelColor = AlusheGreenDark
                            )
                        )
                    }
                }
            }

            // Precios: Costo y Venta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = costoUnitarioStr,
                    onValueChange = { costoUnitarioStr = it },
                    label = { Text("Costo compra ($)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = ShapeCard
                )
                OutlinedTextField(
                    value = precioVentaStr,
                    onValueChange = { precioVentaStr = it },
                    label = { Text("Precio público ($)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = ShapeCard
                )
            }

            // Stock Inicial y Selector de Lote
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stockStr,
                    onValueChange = { stockStr = it },
                    label = { Text("Stock disponible") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = ShapeCard
                )

                // Selector de Lote simple
                if (lotes.isNotEmpty()) {
                    var expandedLote by remember { mutableStateOf(false) }
                    val loteActual = lotes.find { it.id == selectedLoteId }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expandedLote = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ShapeCard
                        ) {
                            Text(
                                text = loteActual?.nombre ?: "Sin lote",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = DmSans,
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = expandedLote,
                            onDismissRequest = { expandedLote = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sin lote asignado") },
                                onClick = { selectedLoteId = null; expandedLote = false }
                            )
                            lotes.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text(l.nombre) },
                                    onClick = { selectedLoteId = l.id; expandedLote = false }
                                )
                            }
                        }
                    }
                }
            }

            // Vista previa de utilidad
            val costoVal = costoUnitarioStr.toDoubleOrNull() ?: 0.0
            val precioVal = precioVentaStr.toDoubleOrNull() ?: 0.0
            if (precioVal > 0) {
                val ganancia = precioVal - costoVal
                val margenPct = if (costoVal > 0) ((ganancia / costoVal) * 100).toInt() else 100
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(AlusheGreenSurface)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Utilidad estimada: ${ganancia.toMXN()} (+${margenPct}%) por pieza",
                        fontFamily = DmSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = AlusheGreenDark
                    )
                }
            }

            // Publicar en la tienda es un acto hacia afuera: queda apagado y se
            // dice en voz alta qué pasa al prenderlo. Sólo al dar de alta, no
            // al editar, porque editar un producto que ya está allá es otra
            // operación distinta.
            if (producto == null && puedePublicar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(AlusheCream)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Publicar en alushe.store",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AlusheInk
                        )
                        Text(
                            text = if (publicarEnWeb)
                                "Se verá en la tienda y el stock se comparte con el otro teléfono"
                            else
                                "Por ahora sólo queda en este teléfono",
                            fontFamily = DmSans,
                            fontSize = 11.sp,
                            color = AlusheInkMid
                        )
                    }
                    Switch(
                        checked = publicarEnWeb,
                        onCheckedChange = { publicarEnWeb = it }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val costo = costoUnitarioStr.toDoubleOrNull() ?: 0.0
                    val precio = precioVentaStr.toDoubleOrNull() ?: 0.0
                    val stock = stockStr.toIntOrNull() ?: 0
                    onSave(
                        sku.trim(),
                        nombre.trim(),
                        categoria.trim().ifBlank { null },
                        selectedLoteId,
                        costo,
                        precio,
                        stock,
                        ubicacion.trim().ifBlank { "General" },
                        publicarEnWeb
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bounce(),
                colors = ButtonDefaults.buttonColors(containerColor = AlusheGreen),
                shape = ShapePill
            ) {
                Text(
                    text = when {
                        producto != null -> "Actualizar producto"
                        publicarEnWeb -> "Guardar y publicar en la tienda"
                        else -> "Guardar en catálogo"
                    },
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AlusheWhite
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoteFormSheet(
    lote: LoteEntity? = null,
    onDismiss: () -> Unit,
    onCalcularProrrateo: (envio: Double, piezas: Int) -> Double,
    onSave: (nombre: String, inversionMercancia: Double, gastosExtraEnvio: Double, fecha: Long, notas: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var nombre by remember { mutableStateOf(lote?.nombre ?: "") }
    var inversionMercanciaStr by remember { mutableStateOf(lote?.inversionMercancia?.toString() ?: "") }
    var gastosEnvioStr by remember { mutableStateOf(lote?.gastosExtraEnvio?.toString() ?: "0.0") }
    var notas by remember { mutableStateOf(lote?.notas ?: "") }

    var pzasEstimadasStr by remember { mutableStateOf("20") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AlusheWhite,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lote == null) "Nuevo Lote de Inversión" else "Editar Lote",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AlusheInk
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AlusheRed)
                    }
                }
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del lote (ej. Pedido Octubre #1)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = ShapeCard
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inversionMercanciaStr,
                    onValueChange = { inversionMercanciaStr = it },
                    label = { Text("Costo mercancía ($)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = ShapeCard
                )
                OutlinedTextField(
                    value = gastosEnvioStr,
                    onValueChange = { gastosEnvioStr = it },
                    label = { Text("Envío / flete ($)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = ShapeCard
                )
            }

            // Calculadora rápida de prorrateo
            val envioVal = gastosEnvioStr.toDoubleOrNull() ?: 0.0
            if (envioVal > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCard)
                        .background(AlusheCreamDark)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Prorrateo de flete por pieza:",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AlusheInk
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pzasEstimadasStr,
                                onValueChange = { pzasEstimadasStr = it },
                                label = { Text("Piezas") },
                                modifier = Modifier.width(90.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = ShapeChip
                            )
                            val pzas = pzasEstimadasStr.toIntOrNull() ?: 1
                            val extraPorPieza = onCalcularProrrateo(envioVal, pzas)
                            Text(
                                text = "→ Suma ${extraPorPieza.toMXN()} al costo de cada pieza.",
                                fontFamily = DmSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = AlusheInkMid
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas o proveedor (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = ShapeCard
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val mercancia = inversionMercanciaStr.toDoubleOrNull() ?: 0.0
                    val envio = gastosEnvioStr.toDoubleOrNull() ?: 0.0
                    onSave(
                        nombre.trim(),
                        mercancia,
                        envio,
                        lote?.fechaCompra ?: System.currentTimeMillis(),
                        notas.trim().ifBlank { null }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bounce(),
                colors = ButtonDefaults.buttonColors(containerColor = AlusheGreen),
                shape = ShapePill
            ) {
                Text(
                    text = if (lote == null) "Crear lote de inversión" else "Guardar cambios",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AlusheWhite
                )
            }
        }
    }
}
