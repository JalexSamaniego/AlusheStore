package com.alushestore.ui.screens.estadisticas

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.ui.components.*
import com.alushestore.ui.theme.*
import com.alushestore.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EstadisticasScreen(
    viewModel: EstadisticasViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showBasesSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AlusheCream,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AlusheInk)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Regresar", tint = AlusheCream)
                    }
                    Text(
                        "Estadísticas y Calendario",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = AlusheCream,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Selector de Base (Activa o Pasadas) ──
                BaseSelectorBar(
                    baseActual = uiState.baseSeleccionada,
                    onClick = { showBasesSheet = true }
                )
            }
        }
    ) { paddingValues ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                CircularProgressIndicator(color = AlusheGreen)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {

            // ── Tabs: Hoy | Semana | Mes ──────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    SlidingSegmentedTabs(
                        opciones = listOf("Hoy", "Semana", "Mes"),
                        seleccionadoIndex = uiState.tabSeleccionada,
                        onSeleccionar = { viewModel.seleccionarTab(it) }
                    )
                }
            }

            when (uiState.tabSeleccionada) {

                // ══════════════════════════════════════════════════════════════
                // TAB 0: HOY
                // ══════════════════════════════════════════════════════════════
                0 -> {
                    item {
                        TotalCard(
                            titulo = "Ventas de hoy",
                            total = uiState.totalHoy,
                            cantidad = uiState.ventasHoy.size
                        )
                    }
                    if (uiState.ventasHoy.isEmpty()) {
                        item { SinVentasMsg("No hay ventas registradas hoy en esta base") }
                    } else {
                        item {
                            SeccionHeader(titulo = "Detalle del día", total = uiState.totalHoy, cantidad = uiState.ventasHoy.size)
                        }
                        items(uiState.ventasHoy, key = { it.id }) { venta ->
                            Box(modifier = Modifier.animateItemPlacement()) {
                                VentaRow(
                                    articulo = venta.articulo,
                                    monto = venta.monto,
                                    fecha = venta.fecha.toFechaCorta()
                                )
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // TAB 1: SEMANA (Tira Flo de 7 días)
                // ══════════════════════════════════════════════════════════════
                1 -> {
                    item {
                        // Navegador de semanas
                        NavegadorPeriodo(
                            titulo = uiState.semanaRangoTexto,
                            onAnterior = { viewModel.anteriorSemana() },
                            onSiguiente = { viewModel.siguienteSemana() },
                            onReset = { viewModel.irASemanaActual() },
                            resetLabel = "Esta semana"
                        )
                    }

                    item {
                        TotalCard(
                            titulo = if (uiState.diaSeleccionadoSemana != null) "Ventas del día seleccionado" else "Total esta semana",
                            total = if (uiState.diaSeleccionadoSemana != null) uiState.totalSemanaFiltrado else uiState.totalSemanaCompleta,
                            cantidad = if (uiState.diaSeleccionadoSemana != null) uiState.ventasSemanaFiltradas.size else uiState.cantidadSemanaCompleta
                        )
                    }

                    item {
                        // Tira de 7 días estilo Flo
                        SemanaStripFlo(
                            dias = uiState.diasSemana,
                            diaSeleccionadoMillis = uiState.diaSeleccionadoSemana,
                            onDiaClick = { fechaMillis -> viewModel.seleccionarDiaSemana(fechaMillis) }
                        )
                    }

                    // Título de la lista con acción para limpiar filtro
                    item {
                        val textoHeader = if (uiState.diaSeleccionadoSemana != null) {
                            val sdf = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX"))
                            "Día: ${sdf.format(Date(uiState.diaSeleccionadoSemana!!)).replaceFirstChar { it.uppercase() }}"
                        } else {
                            "Todas las ventas de la semana"
                        }
                        SeccionHeader(
                            titulo = textoHeader,
                            total = uiState.totalSemanaFiltrado,
                            cantidad = uiState.ventasSemanaFiltradas.size,
                            mostrarBotonReset = uiState.diaSeleccionadoSemana != null,
                            onReset = { viewModel.seleccionarDiaSemana(null) }
                        )
                    }

                    if (uiState.ventasSemanaFiltradas.isEmpty()) {
                        item {
                            SinVentasMsg(
                                if (uiState.diaSeleccionadoSemana != null) "Sin ventas en este día"
                                else "No hay ventas en esta semana"
                            )
                        }
                    } else {
                        items(uiState.ventasSemanaFiltradas, key = { it.id }) { venta ->
                            Box(modifier = Modifier.animateItemPlacement()) {
                                VentaRow(
                                    articulo = venta.articulo,
                                    monto = venta.monto,
                                    fecha = venta.fecha.toFechaCorta()
                                )
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // TAB 2: MES (Calendario Flo mensual)
                // ══════════════════════════════════════════════════════════════
                2 -> {
                    item {
                        // Navegador mensual
                        NavegadorPeriodo(
                            titulo = uiState.nombreMesAnio,
                            onAnterior = { viewModel.anteriorMes() },
                            onSiguiente = { viewModel.siguienteMes() },
                            onReset = { viewModel.irAMesActual() },
                            resetLabel = "Mes actual"
                        )
                    }

                    item {
                        TotalCard(
                            titulo = if (uiState.diaSeleccionadoMes != null) "Ventas del día seleccionado" else "Total acumulado del mes",
                            total = if (uiState.diaSeleccionadoMes != null) uiState.totalMesFiltrado else uiState.totalMesCompleto,
                            cantidad = if (uiState.diaSeleccionadoMes != null) uiState.ventasMesFiltradas.size else uiState.cantidadMesCompleto
                        )
                    }

                    item {
                        // Mini stats del mes (Promedio y Mejor día)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniStatCard(
                                label = "Promedio diario",
                                valor = uiState.promedioDiarioMes.toMXN(),
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatCard(
                                label = "Mejor día",
                                valor = uiState.mejorDiaMes,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        // Cuadrícula tipo Flo
                        CalendarioMesFlo(
                            celdas = uiState.diasMes,
                            diaSeleccionadoMillis = uiState.diaSeleccionadoMes,
                            onDiaClick = { fechaMillis -> viewModel.seleccionarDiaMes(fechaMillis) }
                        )
                    }

                    // Título de la lista con acción para limpiar filtro
                    item {
                        val textoHeader = if (uiState.diaSeleccionadoMes != null) {
                            val sdf = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX"))
                            "Día: ${sdf.format(Date(uiState.diaSeleccionadoMes!!)).replaceFirstChar { it.uppercase() }}"
                        } else {
                            "Todas las ventas del mes"
                        }
                        SeccionHeader(
                            titulo = textoHeader,
                            total = uiState.totalMesFiltrado,
                            cantidad = uiState.ventasMesFiltradas.size,
                            mostrarBotonReset = uiState.diaSeleccionadoMes != null,
                            onReset = { viewModel.seleccionarDiaMes(null) }
                        )
                    }

                    if (uiState.ventasMesFiltradas.isEmpty()) {
                        item {
                            SinVentasMsg(
                                if (uiState.diaSeleccionadoMes != null) "Sin ventas registradas en este día"
                                else "No hay ventas en este mes"
                            )
                        }
                    } else {
                        items(uiState.ventasMesFiltradas, key = { it.id }) { venta ->
                            Box(modifier = Modifier.animateItemPlacement()) {
                                VentaRow(
                                    articulo = venta.articulo,
                                    monto = venta.monto,
                                    fecha = venta.fecha.toFechaCorta()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de selección de base (Activa o Pasadas)
    if (showBasesSheet) {
        BasesSelectorSheet(
            bases = uiState.basesDisponibles,
            baseActual = uiState.baseSeleccionada,
            onDismiss = { showBasesSheet = false },
            onSelect = { base ->
                viewModel.seleccionarBase(base)
                showBasesSheet = false
            }
        )
    }
}

// ── BaseSelectorBar ───────────────────────────────────────────────────────────

@Composable
private fun BaseSelectorBar(
    baseActual: BaseEntity?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF1E2822),
        modifier = Modifier
            .fillMaxWidth()
            .bounce()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (baseActual?.cerrada == true) Icons.Default.Folder else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (baseActual?.cerrada == true) AlusheCream.copy(alpha = 0.7f) else AlusheGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = baseActual?.nombre ?: "Sin base seleccionada",
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = AlusheCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Badge
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(if (baseActual?.cerrada == true) AlusheCreamDark else AlusheGreenSurface)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (baseActual?.cerrada == true) "CERRADA" else "ACTIVA",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp,
                        letterSpacing = 0.5.sp,
                        color = if (baseActual?.cerrada == true) AlusheInk else AlusheGreenDark
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cambiar base",
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    color = AlusheCream.copy(alpha = 0.6f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AlusheCream.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Navegador de Periodo (< Mes/Semana >) ──────────────────────────────────────

@Composable
private fun NavegadorPeriodo(
    titulo: String,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onReset: () -> Unit,
    resetLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onAnterior,
            modifier = Modifier.bounce()
        ) {
            Icon(Icons.Default.ChevronLeft, "Anterior", tint = AlusheInk)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = titulo,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) + slideInVertically { height -> height / 3 })
                        .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutVertically { height -> -height / 3 })
                },
                label = "periodoTituloSlide"
            ) { targetTitulo ->
                Text(
                    targetTitulo,
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AlusheInk,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                resetLabel,
                fontFamily = DmSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = AlusheGreenDark,
                modifier = Modifier
                    .clip(ShapePill)
                    .bounceClickable(onClick = onReset)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        IconButton(
            onClick = onSiguiente,
            modifier = Modifier.bounce()
        ) {
            Icon(Icons.Default.ChevronRight, "Siguiente", tint = AlusheInk)
        }
    }
}

// ── CalendarioMesFlo (Cuadrícula Mensual tipo Flo) ─────────────────────────────

@Composable
private fun CalendarioMesFlo(
    celdas: List<CalendarioDia>,
    diaSeleccionadoMillis: Long?,
    onDiaClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(3.dp, ShapeCardLg, ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
        shape = ShapeCardLg,
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila de encabezados de los días (L, M, M, J, V, S, D)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { d ->
                    Text(
                        text = d,
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = AlusheInkMid,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = AlusheBorder, thickness = 0.5.dp)

            // Cuadrícula de 7 columnas agrupada en filas de 7
            val filas = celdas.chunked(7)
            filas.forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 7) {
                        val celda = fila.getOrNull(i)
                        if (celda != null && celda.diaNumero > 0) {
                            val isSelected = diaSeleccionadoMillis != null && celda.fechaMillis == diaSeleccionadoMillis

                            DiaCeldaFlo(
                                dia = celda,
                                isSelected = isSelected,
                                onClick = { onDiaClick(celda.fechaMillis) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            // Espacio vacío para alinear
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Leyenda explicativa estilo Flo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AlusheGreen)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Días con ventas registradas (toca un día para ver su detalle)",
                    fontFamily = DmSans,
                    fontSize = 11.sp,
                    color = AlusheInkLight
                )
            }
        }
    }
}

// ── DiaCeldaFlo (Celda individual de un día) ───────────────────────────────────

@Composable
private fun DiaCeldaFlo(
    dia: CalendarioDia,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> AlusheInk
        dia.tieneVentas -> AlusheGreenSurface
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> AlusheCream
        dia.tieneVentas -> AlusheGreenDark
        dia.esHoy -> AlusheInk
        else -> AlusheInk.copy(alpha = 0.8f)
    }

    val borderModifier = when {
        isSelected -> Modifier.border(1.5.dp, AlusheInk, CircleShape)
        dia.esHoy -> Modifier.border(1.5.dp, AlusheGreen, CircleShape)
        dia.tieneVentas -> Modifier.border(1.dp, AlusheGreen.copy(alpha = 0.35f), CircleShape)
        else -> Modifier
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "diaCeldaScale"
    )

    Column(
        modifier = modifier
            .padding(vertical = 3.dp)
            .bounceClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(borderModifier),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dia.diaNumero.toString(),
                    fontFamily = Montserrat,
                    fontWeight = if (dia.tieneVentas || dia.esHoy || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 12.sp,
                    color = textColor
                )

                // Pequeño indicador dot estilo Flo
                if (dia.tieneVentas && !isSelected) {
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(AlusheGreen)
                    )
                }
            }
        }
    }
}

// ── SemanaStripFlo (Tira de los 7 días de la semana) ───────────────────────────

@Composable
private fun SemanaStripFlo(
    dias: List<SemanaDia>,
    diaSeleccionadoMillis: Long?,
    onDiaClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(3.dp, ShapeCardLg, ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
        shape = ShapeCardLg,
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dias.forEach { dia ->
                    val isSelected = diaSeleccionadoMillis != null && dia.fechaMillis == diaSeleccionadoMillis

                    val bgCircle = when {
                        isSelected -> AlusheInk
                        dia.tieneVentas -> AlusheGreenSurface
                        else -> Color.Transparent
                    }

                    val textNumColor = when {
                        isSelected -> AlusheCream
                        dia.tieneVentas -> AlusheGreenDark
                        else -> AlusheInk
                    }

                    val border = when {
                        isSelected -> Modifier.border(1.5.dp, AlusheInk, CircleShape)
                        dia.esHoy -> Modifier.border(1.5.dp, AlusheGreen, CircleShape)
                        dia.tieneVentas -> Modifier.border(1.dp, AlusheGreen.copy(alpha = 0.35f), CircleShape)
                        else -> Modifier
                    }

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.14f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "semanaScale"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .bounceClickable { onDiaClick(dia.fechaMillis) }
                            .padding(vertical = 4.dp)
                    ) {
                        // Nombre del día ("LUN", "MAR"...)
                        Text(
                            text = dia.nombreDia.uppercase(),
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            color = if (dia.esHoy) AlusheGreenDark else AlusheInkMid
                        )

                        Spacer(Modifier.height(4.dp))

                        // Círculo con número
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(bgCircle)
                                .then(border),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dia.diaNumero.toString(),
                                fontFamily = Montserrat,
                                fontWeight = if (dia.tieneVentas || dia.esHoy || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = textNumColor
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Monto pequeño abajo
                        Text(
                            text = if (dia.tieneVentas) "$${dia.totalMonto.toInt()}" else "—",
                            fontFamily = Montserrat,
                            fontWeight = if (dia.tieneVentas) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 9.sp,
                            color = if (dia.tieneVentas) AlusheGreenDark else AlusheInkLight,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ── SeccionHeader ─────────────────────────────────────────────────────────────

@Composable
private fun SeccionHeader(
    titulo: String,
    total: Double,
    cantidad: Int,
    mostrarBotonReset: Boolean = false,
    onReset: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlusheCreamDark)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    fontFamily = Montserrat,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = AlusheInk
                )
                Text(
                    text = "${total.toMXN()} · $cantidad ${if (cantidad == 1) "venta" else "ventas"}",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    color = AlusheGreenDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (mostrarBotonReset) {
                Surface(
                    onClick = onReset,
                    color = AlusheInk,
                    shape = ShapePill,
                    modifier = Modifier.bounce()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = AlusheCream, modifier = Modifier.size(13.dp))
                        Text(
                            "Ver todo",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = AlusheCream
                        )
                    }
                }
            }
        }
    }
}

// ── BasesSelectorSheet (Modal para cambiar de base) ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasesSelectorSheet(
    bases: List<BaseEntity>,
    baseActual: BaseEntity?,
    onDismiss: () -> Unit,
    onSelect: (BaseEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AlusheCream,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Seleccionar Base de Ventas",
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = AlusheInk
            )
            Text(
                "Elige una base para ver sus métricas y calendario detallado.",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = AlusheInkMid
            )

            HorizontalDivider(color = AlusheBorder, thickness = 1.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bases, key = { it.id }) { base ->
                    val esSeleccionada = baseActual?.id == base.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounce()
                            .clickable { onSelect(base) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (esSeleccionada) AlusheGreenSurface else AlusheWhite
                        ),
                        shape = ShapeCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (esSeleccionada) AlusheGreen else AlusheBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (base.cerrada) Icons.Default.Folder else Icons.Default.FolderOpen,
                                    null,
                                    tint = if (esSeleccionada) AlusheGreenDark else AlusheInkMid
                                )
                                Column {
                                    Text(
                                        base.nombre,
                                        fontFamily = Montserrat,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = AlusheInk
                                    )
                                    Text(
                                        if (base.cerrada) "Cerrada: ${base.fechaCierre?.toFechaCorta() ?: "—"}"
                                        else "Base activa actual",
                                        fontFamily = DmSans,
                                        fontSize = 11.sp,
                                        color = AlusheInkLight
                                    )
                                }
                            }

                            if (esSeleccionada) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AlusheGreenDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ── MiniStatCard ──────────────────────────────────────────────────────────────

@Composable
fun MiniStatCard(label: String, valor: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.bounce(),
        colors = CardDefaults.cardColors(containerColor = AlusheWhite),
        shape = ShapeCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, AlusheBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label.uppercase(),
                fontFamily = Montserrat,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                color = AlusheInkLight,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                valor,
                fontFamily = Montserrat,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = AlusheInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── VentaRow ──────────────────────────────────────────────────────────────────

@Composable
fun VentaRow(articulo: String, monto: Double, fecha: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounce()
            .background(AlusheCream)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(ShapePill)
                .background(AlusheGreen)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                articulo,
                fontFamily = Montserrat,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = AlusheInk
            )
            Text(
                fecha,
                fontFamily = DmSans,
                fontSize = 11.sp,
                color = AlusheInkLight
            )
        }
        Text(
            monto.toMXN(),
            fontFamily = Montserrat,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = AlusheGreen
        )
    }
    HorizontalDivider(color = AlusheBorder, thickness = 0.5.dp)
}

// ── Sin ventas (Floating Empty State) ─────────────────────────────────────────

@Composable
fun SinVentasMsg(msg: String) {
    EmptyState(
        titulo = "Sin ventas",
        subtitulo = msg,
        emoji = "📅",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
}