package com.alushestore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.data.repository.AlusheRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CalendarioDia(
    val diaNumero: Int,            // 1..31 (0 si es espacio vacío de relleno)
    val fechaMillis: Long,         // timestamp medianoche del día
    val tieneVentas: Boolean,
    val totalMonto: Double,
    val cantidadVentas: Int,
    val esHoy: Boolean,
    val esMesActual: Boolean
)

data class SemanaDia(
    val nombreDia: String,         // "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"
    val diaNumero: Int,            // día del mes (1..31)
    val fechaMillis: Long,         // timestamp medianoche
    val tieneVentas: Boolean,
    val totalMonto: Double,
    val cantidadVentas: Int,
    val esHoy: Boolean
)

data class EstadisticasUiState(
    val tabSeleccionada: Int = 0,               // 0=Hoy, 1=Semana, 2=Mes
    val basesDisponibles: List<BaseEntity> = emptyList(),
    val baseSeleccionada: BaseEntity? = null,

    // Tab Hoy
    val ventasHoy: List<VentaEntity> = emptyList(),
    val totalHoy: Double = 0.0,

    // Tab Semana
    val semanaRangoTexto: String = "",          // "13 - 19 Octubre 2026"
    val diasSemana: List<SemanaDia> = emptyList(),
    val diaSeleccionadoSemana: Long? = null,    // Timestamp del día seleccionado (null = toda la semana)
    val ventasSemanaFiltradas: List<VentaEntity> = emptyList(),
    val totalSemanaFiltrado: Double = 0.0,
    val totalSemanaCompleta: Double = 0.0,
    val cantidadSemanaCompleta: Int = 0,

    // Tab Mes
    val nombreMesAnio: String = "",             // "Octubre 2026"
    val diasMes: List<CalendarioDia> = emptyList(), // Cuadrícula con offsets (7 columnas)
    val diaSeleccionadoMes: Long? = null,       // Timestamp del día seleccionado (null = todo el mes)
    val ventasMesFiltradas: List<VentaEntity> = emptyList(),
    val totalMesFiltrado: Double = 0.0,
    val totalMesCompleto: Double = 0.0,
    val cantidadMesCompleto: Int = 0,
    val promedioDiarioMes: Double = 0.0,
    val mejorDiaMes: String = "",

    val isLoading: Boolean = true
)

class EstadisticasViewModel(
    private val repository: AlusheRepository,
    private val initialBaseId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EstadisticasUiState())
    val uiState: StateFlow<EstadisticasUiState> = _uiState.asStateFlow()

    private var mesCalendar: Calendar = Calendar.getInstance()
    private var semanaCalendar: Calendar = Calendar.getInstance()

    private var ventasJob: Job? = null
    private var todasLasVentasActuales: List<VentaEntity> = emptyList()

    init {
        cargarBases()
    }

    private fun cargarBases() {
        viewModelScope.launch {
            repository.getAllBases().collect { bases ->
                _uiState.update { it.copy(basesDisponibles = bases) }

                // Si aún no hay base seleccionada o cambió la lista
                val actual = _uiState.value.baseSeleccionada
                if (actual == null && bases.isNotEmpty()) {
                    val baseElegida = if (initialBaseId != null && initialBaseId > 0) {
                        bases.find { it.id == initialBaseId } ?: bases.first()
                    } else {
                        bases.find { !it.cerrada } ?: bases.first()
                    }
                    seleccionarBaseInterna(baseElegida)
                } else if (actual != null) {
                    val actualizada = bases.find { it.id == actual.id }
                    if (actualizada != null && actualizada != actual) {
                        _uiState.update { it.copy(baseSeleccionada = actualizada) }
                    }
                } else if (bases.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun seleccionarBase(base: BaseEntity) {
        seleccionarBaseInterna(base)
    }

    private fun seleccionarBaseInterna(base: BaseEntity) {
        _uiState.update {
            it.copy(
                baseSeleccionada = base,
                diaSeleccionadoSemana = null,
                diaSeleccionadoMes = null
            )
        }

        // Si es una base cerrada, posicionamos los calendarios en la fecha de cierre o creación
        if (base.cerrada) {
            val fechaRef = base.fechaCierre ?: base.fechaCreacion
            mesCalendar = Calendar.getInstance().apply { timeInMillis = fechaRef }
            semanaCalendar = Calendar.getInstance().apply { timeInMillis = fechaRef }
        } else {
            mesCalendar = Calendar.getInstance()
            semanaCalendar = Calendar.getInstance()
        }

        ventasJob?.cancel()
        ventasJob = viewModelScope.launch {
            repository.getVentasByBase(base.id).collect { ventas ->
                todasLasVentasActuales = ventas
                recalcularEstadisticas()
            }
        }
    }

    fun seleccionarTab(index: Int) {
        _uiState.update { it.copy(tabSeleccionada = index) }
    }

    // ── Navegación Mes (Calendario Flo) ────────────────────────────────────────

    fun anteriorMes() {
        mesCalendar.add(Calendar.MONTH, -1)
        _uiState.update { it.copy(diaSeleccionadoMes = null) }
        recalcularEstadisticas()
    }

    fun siguienteMes() {
        mesCalendar.add(Calendar.MONTH, 1)
        _uiState.update { it.copy(diaSeleccionadoMes = null) }
        recalcularEstadisticas()
    }

    fun irAMesActual() {
        mesCalendar = Calendar.getInstance()
        _uiState.update { it.copy(diaSeleccionadoMes = null) }
        recalcularEstadisticas()
    }

    fun seleccionarDiaMes(fechaMillis: Long?) {
        val nuevo = if (_uiState.value.diaSeleccionadoMes == fechaMillis) null else fechaMillis
        _uiState.update { it.copy(diaSeleccionadoMes = nuevo) }
        actualizarFiltroMes()
    }

    // ── Navegación Semana (Tira Flo) ───────────────────────────────────────────

    fun anteriorSemana() {
        semanaCalendar.add(Calendar.WEEK_OF_YEAR, -1)
        _uiState.update { it.copy(diaSeleccionadoSemana = null) }
        recalcularEstadisticas()
    }

    fun siguienteSemana() {
        semanaCalendar.add(Calendar.WEEK_OF_YEAR, 1)
        _uiState.update { it.copy(diaSeleccionadoSemana = null) }
        recalcularEstadisticas()
    }

    fun irASemanaActual() {
        semanaCalendar = Calendar.getInstance()
        _uiState.update { it.copy(diaSeleccionadoSemana = null) }
        recalcularEstadisticas()
    }

    fun seleccionarDiaSemana(fechaMillis: Long?) {
        val nuevo = if (_uiState.value.diaSeleccionadoSemana == fechaMillis) null else fechaMillis
        _uiState.update { it.copy(diaSeleccionadoSemana = nuevo) }
        actualizarFiltroSemana()
    }

    // ── Cálculos ──────────────────────────────────────────────────────────────

    private fun recalcularEstadisticas() {
        val todasLasVentas = todasLasVentasActuales
        val hoy = Calendar.getInstance()

        // ── 1. Hoy
        val inicioHoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val finHoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val ventasHoy = todasLasVentas.filter { it.fecha in inicioHoy..finHoy }
        val totalHoy = ventasHoy.sumOf { it.monto }

        // ── 2. Semana
        val lunesCal = obtenerLunesDeSemana(semanaCalendar)
        val domingoCal = lunesCal.clone() as Calendar
        domingoCal.add(Calendar.DAY_OF_MONTH, 6)
        domingoCal.set(Calendar.HOUR_OF_DAY, 23); domingoCal.set(Calendar.MINUTE, 59)
        domingoCal.set(Calendar.SECOND, 59); domingoCal.set(Calendar.MILLISECOND, 999)

        val inicioSemana = lunesCal.timeInMillis
        val finSemana = domingoCal.timeInMillis

        val formatoRango = SimpleDateFormat("d", Locale("es", "MX"))
        val formatoMesAnio = SimpleDateFormat("MMMM yyyy", Locale("es", "MX"))
        val semanaRangoTexto = "${formatoRango.format(lunesCal.time)} - ${formatoRango.format(domingoCal.time)} ${formatoMesAnio.format(domingoCal.time).replaceFirstChar { it.uppercase() }}"

        val diasNombres = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        val diasSemana = (0..6).map { offset ->
            val diaCal = lunesCal.clone() as Calendar
            diaCal.add(Calendar.DAY_OF_MONTH, offset)
            val iniDia = diaCal.timeInMillis
            diaCal.set(Calendar.HOUR_OF_DAY, 23); diaCal.set(Calendar.MINUTE, 59)
            diaCal.set(Calendar.SECOND, 59); diaCal.set(Calendar.MILLISECOND, 999)
            val finDia = diaCal.timeInMillis

            val ventasDia = todasLasVentas.filter { it.fecha in iniDia..finDia }
            val esHoy = diaCal.get(Calendar.YEAR) == hoy.get(Calendar.YEAR) &&
                    diaCal.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR)

            SemanaDia(
                nombreDia = diasNombres[offset],
                diaNumero = diaCal.get(Calendar.DAY_OF_MONTH),
                fechaMillis = iniDia,
                tieneVentas = ventasDia.isNotEmpty(),
                totalMonto = ventasDia.sumOf { it.monto },
                cantidadVentas = ventasDia.size,
                esHoy = esHoy
            )
        }

        val ventasSemanaCompleta = todasLasVentas.filter { it.fecha in inicioSemana..finSemana }
        val totalSemanaCompleta = ventasSemanaCompleta.sumOf { it.monto }

        // ── 3. Mes
        val primerDiaMes = mesCalendar.clone() as Calendar
        primerDiaMes.set(Calendar.DAY_OF_MONTH, 1)
        primerDiaMes.set(Calendar.HOUR_OF_DAY, 0); primerDiaMes.set(Calendar.MINUTE, 0)
        primerDiaMes.set(Calendar.SECOND, 0); primerDiaMes.set(Calendar.MILLISECOND, 0)

        val maxDiasMes = primerDiaMes.getActualMaximum(Calendar.DAY_OF_MONTH)
        val ultimoDiaMes = primerDiaMes.clone() as Calendar
        ultimoDiaMes.set(Calendar.DAY_OF_MONTH, maxDiasMes)
        ultimoDiaMes.set(Calendar.HOUR_OF_DAY, 23); ultimoDiaMes.set(Calendar.MINUTE, 59)
        ultimoDiaMes.set(Calendar.SECOND, 59); ultimoDiaMes.set(Calendar.MILLISECOND, 999)

        val inicioMes = primerDiaMes.timeInMillis
        val finMes = ultimoDiaMes.timeInMillis

        val sdfMes = SimpleDateFormat("MMMM yyyy", Locale("es", "MX"))
        val nombreMesAnio = sdfMes.format(primerDiaMes.time).replaceFirstChar { it.uppercase() }

        // Offset lunes
        val dayOfWeek = primerDiaMes.get(Calendar.DAY_OF_WEEK)
        val offsetLunes = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

        val celdasMes = mutableListOf<CalendarioDia>()
        // Celdas de relleno
        for (i in 0 until offsetLunes) {
            celdasMes.add(
                CalendarioDia(
                    diaNumero = 0,
                    fechaMillis = 0L,
                    tieneVentas = false,
                    totalMonto = 0.0,
                    cantidadVentas = 0,
                    esHoy = false,
                    esMesActual = false
                )
            )
        }

        // Celdas reales
        for (dia in 1..maxDiasMes) {
            val diaCal = primerDiaMes.clone() as Calendar
            diaCal.set(Calendar.DAY_OF_MONTH, dia)
            val iniDia = diaCal.timeInMillis
            diaCal.set(Calendar.HOUR_OF_DAY, 23); diaCal.set(Calendar.MINUTE, 59)
            diaCal.set(Calendar.SECOND, 59); diaCal.set(Calendar.MILLISECOND, 999)
            val finDia = diaCal.timeInMillis

            val ventasDia = todasLasVentas.filter { it.fecha in iniDia..finDia }
            val esHoy = diaCal.get(Calendar.YEAR) == hoy.get(Calendar.YEAR) &&
                    diaCal.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR)

            celdasMes.add(
                CalendarioDia(
                    diaNumero = dia,
                    fechaMillis = iniDia,
                    tieneVentas = ventasDia.isNotEmpty(),
                    totalMonto = ventasDia.sumOf { it.monto },
                    cantidadVentas = ventasDia.size,
                    esHoy = esHoy,
                    esMesActual = true
                )
            )
        }

        val ventasMesCompleto = todasLasVentas.filter { it.fecha in inicioMes..finMes }
        val totalMesCompleto = ventasMesCompleto.sumOf { it.monto }

        // Agrupación para mejor día y promedio
        val ventasPorDia = ventasMesCompleto.groupBy { v ->
            Calendar.getInstance().apply { timeInMillis = v.fecha }.get(Calendar.DAY_OF_MONTH)
        }
        val mejorDia = ventasPorDia.maxByOrNull { it.value.sumOf { v -> v.monto } }
        val mejorDiaStr = mejorDia?.let { "Día ${it.key} (${it.value.size} ventas)" } ?: "—"
        val diasConVentas = ventasPorDia.keys.size.coerceAtLeast(1)
        val promedioDiario = if (ventasMesCompleto.isEmpty()) 0.0 else totalMesCompleto / diasConVentas

        _uiState.update {
            it.copy(
                ventasHoy = ventasHoy,
                totalHoy = totalHoy,
                semanaRangoTexto = semanaRangoTexto,
                diasSemana = diasSemana,
                totalSemanaCompleta = totalSemanaCompleta,
                cantidadSemanaCompleta = ventasSemanaCompleta.size,
                nombreMesAnio = nombreMesAnio,
                diasMes = celdasMes,
                totalMesCompleto = totalMesCompleto,
                cantidadMesCompleto = ventasMesCompleto.size,
                promedioDiarioMes = promedioDiario,
                mejorDiaMes = mejorDiaStr,
                isLoading = false
            )
        }

        actualizarFiltroSemana()
        actualizarFiltroMes()
    }

    private fun actualizarFiltroSemana() {
        val diaSel = _uiState.value.diaSeleccionadoSemana
        val todasLasVentas = todasLasVentasActuales

        if (diaSel == null) {
            val lunesCal = obtenerLunesDeSemana(semanaCalendar)
            val domingoCal = lunesCal.clone() as Calendar
            domingoCal.add(Calendar.DAY_OF_MONTH, 6)
            domingoCal.set(Calendar.HOUR_OF_DAY, 23); domingoCal.set(Calendar.MINUTE, 59)
            domingoCal.set(Calendar.SECOND, 59); domingoCal.set(Calendar.MILLISECOND, 999)

            val ventas = todasLasVentas.filter { it.fecha in lunesCal.timeInMillis..domingoCal.timeInMillis }
            _uiState.update {
                it.copy(
                    ventasSemanaFiltradas = ventas,
                    totalSemanaFiltrado = ventas.sumOf { v -> v.monto }
                )
            }
        } else {
            val diaCal = Calendar.getInstance().apply { timeInMillis = diaSel }
            val ini = diaCal.timeInMillis
            diaCal.set(Calendar.HOUR_OF_DAY, 23); diaCal.set(Calendar.MINUTE, 59)
            diaCal.set(Calendar.SECOND, 59); diaCal.set(Calendar.MILLISECOND, 999)
            val fin = diaCal.timeInMillis

            val ventas = todasLasVentas.filter { it.fecha in ini..fin }
            _uiState.update {
                it.copy(
                    ventasSemanaFiltradas = ventas,
                    totalSemanaFiltrado = ventas.sumOf { v -> v.monto }
                )
            }
        }
    }

    private fun actualizarFiltroMes() {
        val diaSel = _uiState.value.diaSeleccionadoMes
        val todasLasVentas = todasLasVentasActuales

        if (diaSel == null) {
            val primerDiaMes = mesCalendar.clone() as Calendar
            primerDiaMes.set(Calendar.DAY_OF_MONTH, 1)
            primerDiaMes.set(Calendar.HOUR_OF_DAY, 0); primerDiaMes.set(Calendar.MINUTE, 0)
            primerDiaMes.set(Calendar.SECOND, 0); primerDiaMes.set(Calendar.MILLISECOND, 0)

            val maxDiasMes = primerDiaMes.getActualMaximum(Calendar.DAY_OF_MONTH)
            val ultimoDiaMes = primerDiaMes.clone() as Calendar
            ultimoDiaMes.set(Calendar.DAY_OF_MONTH, maxDiasMes)
            ultimoDiaMes.set(Calendar.HOUR_OF_DAY, 23); ultimoDiaMes.set(Calendar.MINUTE, 59)
            ultimoDiaMes.set(Calendar.SECOND, 59); ultimoDiaMes.set(Calendar.MILLISECOND, 999)

            val ventas = todasLasVentas.filter { it.fecha in primerDiaMes.timeInMillis..ultimoDiaMes.timeInMillis }
            _uiState.update {
                it.copy(
                    ventasMesFiltradas = ventas,
                    totalMesFiltrado = ventas.sumOf { v -> v.monto }
                )
            }
        } else {
            val diaCal = Calendar.getInstance().apply { timeInMillis = diaSel }
            val ini = diaCal.timeInMillis
            diaCal.set(Calendar.HOUR_OF_DAY, 23); diaCal.set(Calendar.MINUTE, 59)
            diaCal.set(Calendar.SECOND, 59); diaCal.set(Calendar.MILLISECOND, 999)
            val fin = diaCal.timeInMillis

            val ventas = todasLasVentas.filter { it.fecha in ini..fin }
            _uiState.update {
                it.copy(
                    ventasMesFiltradas = ventas,
                    totalMesFiltrado = ventas.sumOf { v -> v.monto }
                )
            }
        }
    }

    private fun obtenerLunesDeSemana(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val diasDesdeLunes = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -diasDesdeLunes)
        return cal
    }
}

class EstadisticasViewModelFactory(
    private val repository: AlusheRepository,
    private val initialBaseId: Long? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EstadisticasViewModel(repository, initialBaseId) as T
    }
}
