package com.alushestore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alushestore.data.local.entities.TianguisSesionEntity
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.data.repository.AlusheRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

data class TianguisUiState(
    val sesionActiva: TianguisSesionEntity? = null,
    val ventasSesion: List<TianguisVentaEntity> = emptyList(),
    val totalSesion: Double = 0.0,
    val cantidadVentas: Int = 0,
    val isLoading: Boolean = true,
    val mensaje: String? = null,
    val mostrarCerrarSheet: Boolean = false,
    val ventaMasAlta: Double = 0.0,
    val historialSesiones: List<TianguisSesionEntity> = emptyList(),
    val mostrarHistorial: Boolean = false,
    val sesionDetalle: TianguisSesionEntity? = null,
    val ventasDetalle: List<TianguisVentaEntity> = emptyList(),
    val baseActivaId: Long? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class TianguisViewModel(private val repository: AlusheRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TianguisUiState())
    val uiState: StateFlow<TianguisUiState> = _uiState.asStateFlow()

    private val _showAddSheet = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet.asStateFlow()

    private val _showIniciarSheet = MutableStateFlow(false)
    val showIniciarSheet: StateFlow<Boolean> = _showIniciarSheet.asStateFlow()

    private val _ventaEditando = MutableStateFlow<TianguisVentaEntity?>(null)
    val ventaEditando: StateFlow<TianguisVentaEntity?> = _ventaEditando.asStateFlow()

    init {
        observarSesionActiva()
        observarHistorial()
        observarBaseActiva()
    }

    private fun observarSesionActiva() {
        viewModelScope.launch {
            repository.getSesionTianguisActiva().collect { sesion ->
                if (sesion != null) {
                    observarDatosDeSesion(sesion)
                } else {
                    _uiState.update { it.copy(
                        sesionActiva = null,
                        ventasSesion = emptyList(),
                        totalSesion = 0.0,
                        cantidadVentas = 0,
                        isLoading = false
                    )}
                }
            }
        }
    }

    private fun observarDatosDeSesion(sesion: TianguisSesionEntity) {
        viewModelScope.launch {
            combine(
                repository.getVentasTianguisBySesion(sesion.id),
                repository.getTotalTianguisBySesion(sesion.id),
                repository.getCountTianguisBySesion(sesion.id)
            ) { ventas, total, count ->
                TianguisUiState(
                    sesionActiva = sesion,
                    ventasSesion = ventas,
                    totalSesion = total ?: 0.0,
                    cantidadVentas = count,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        mensaje = current.mensaje,
                        mostrarCerrarSheet = current.mostrarCerrarSheet,
                        historialSesiones = current.historialSesiones,
                        mostrarHistorial = current.mostrarHistorial
                    )
                }
            }
        }
    }

    private fun observarHistorial() {
        viewModelScope.launch {
            repository.getAllSesionesTianguis().collect { sesiones ->
                _uiState.update { it.copy(historialSesiones = sesiones) }
            }
        }
    }

    private fun observarBaseActiva() {
        viewModelScope.launch {
            repository.getBaseActiva().collect { base ->
                _uiState.update { it.copy(baseActivaId = base?.id) }
            }
        }
    }

    fun iniciarSesion(nombre: String) {
        viewModelScope.launch {
            repository.iniciarSesionTianguis(nombre)
            _showIniciarSheet.value = false
            mostrarMensaje("Sesión iniciada")
        }
    }

    fun editarNombreSesion(nuevoNombre: String) {
        viewModelScope.launch {
            val sesion = _uiState.value.sesionActiva ?: return@launch
            repository.editarNombreSesion(sesion, nuevoNombre)
        }
    }

    fun agregarVenta(descripcion: String, monto: Double) {
        viewModelScope.launch {
            val sesionId = _uiState.value.sesionActiva?.id ?: return@launch
            repository.agregarVentaTianguis(sesionId, descripcion, monto)
            mostrarMensaje("Venta agregada ✓")
        }
    }

    fun editarVenta(venta: TianguisVentaEntity) {
        viewModelScope.launch {
            repository.editarVentaTianguis(venta)
            mostrarMensaje("Venta actualizada ✓")
        }
    }

    fun eliminarVenta(ventaId: Long) {
        viewModelScope.launch {
            repository.eliminarVentaTianguis(ventaId)
            mostrarMensaje("Venta eliminada")
        }
    }

    fun abrirEditSheet(venta: TianguisVentaEntity) {
        _ventaEditando.value = venta
    }

    fun cerrarEditSheet() {
        _ventaEditando.value = null
    }

    fun abrirCerrarSheet() {
        viewModelScope.launch {
            val sesionId = _uiState.value.sesionActiva?.id ?: return@launch
            val masAlta = repository.getVentaMasAltaTianguis(sesionId) ?: 0.0
            _uiState.update { it.copy(mostrarCerrarSheet = true, ventaMasAlta = masAlta) }
        }
    }

    fun cerrarCerrarSheet() {
        _uiState.update { it.copy(mostrarCerrarSheet = false) }
    }

    fun cerrarYGuardar(baseId: Long) {
        viewModelScope.launch {
            val sesionId = _uiState.value.sesionActiva?.id ?: return@launch
            repository.cerrarSesionTianguis(sesionId)
            repository.guardarSesionEnBase(sesionId, baseId)
            _uiState.update { it.copy(mostrarCerrarSheet = false) }
            mostrarMensaje("Sesión guardada en base ✓")
        }
    }

    fun cerrarYDescartar() {
        viewModelScope.launch {
            val sesionId = _uiState.value.sesionActiva?.id ?: return@launch
            repository.cerrarSesionTianguis(sesionId)
            _uiState.update { it.copy(mostrarCerrarSheet = false) }
            mostrarMensaje("Sesión cerrada")
        }
    }

    fun abrirHistorial() { _uiState.update { it.copy(mostrarHistorial = true) } }
    fun cerrarHistorial() { _uiState.update { it.copy(mostrarHistorial = false) } }

    fun abrirDetalleSesion(sesion: TianguisSesionEntity) {
        viewModelScope.launch {
            val ventas = repository.getVentasTianguisBySesionSync(sesion.id)
            _uiState.update { it.copy(sesionDetalle = sesion, ventasDetalle = ventas) }
        }
    }

    fun cerrarDetalleSesion() {
        _uiState.update { it.copy(sesionDetalle = null, ventasDetalle = emptyList()) }
    }

    fun abrirAddSheet() { _showAddSheet.value = true }
    fun cerrarAddSheet() { _showAddSheet.value = false }

    fun abrirIniciarSheet() { _showIniciarSheet.value = true }
    fun cerrarIniciarSheet() { _showIniciarSheet.value = false }

    fun mensajeMostrado() { _uiState.update { it.copy(mensaje = null) } }

    private fun mostrarMensaje(msg: String) {
        _uiState.update { it.copy(mensaje = msg) }
    }
}

// ── Factory ───────────────────────────────────────────────────────────────────

class TianguisViewModelFactory(private val repository: AlusheRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TianguisViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TianguisViewModel(repository) as T
        }
        throw IllegalArgumentException("ViewModel desconocido")
    }
}
