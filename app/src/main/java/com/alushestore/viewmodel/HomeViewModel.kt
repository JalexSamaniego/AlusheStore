package com.alushestore.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.data.repository.AlusheRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

data class HomeUiState(
    val baseActiva: BaseEntity? = null,
    val ventas: List<VentaEntity> = emptyList(),
    val totalBase: Double = 0.0,
    val totalHoy: Double = 0.0,
    val cantidadVentas: Int = 0,
    val promedio: Double = 0.0,
    val isLoading: Boolean = true,
    val mensaje: String? = null,
    val needsGoogleAccount: Boolean = false,
    val authConsentIntent: Intent? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class HomeViewModel(private val repository: AlusheRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _showAddSheet = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet.asStateFlow()

    private val _ventaEditando = MutableStateFlow<VentaEntity?>(null)
    val ventaEditando: StateFlow<VentaEntity?> = _ventaEditando.asStateFlow()

    init {
        observarBaseActiva()
    }

    private fun observarBaseActiva() {
        viewModelScope.launch {
            repository.getBaseActiva().collect { base ->
                if (base != null) {
                    observarDatosDeBase(base)
                } else {
                    _uiState.update { it.copy(baseActiva = null, isLoading = false) }
                }
            }
        }
    }

    private fun observarDatosDeBase(base: BaseEntity) {
        viewModelScope.launch {
            combine(
                repository.getVentasByBase(base.id),
                repository.getTotalByBase(base.id),
                repository.getTotalHoy(base.id),
                repository.getCountByBase(base.id)
            ) { ventas, total, totalHoy, count ->
                val totalReal = total ?: 0.0
                HomeUiState(
                    baseActiva = base,
                    ventas = ventas,
                    totalBase = totalReal,
                    totalHoy = totalHoy ?: 0.0,
                    cantidadVentas = count,
                    promedio = if (count > 0) totalReal / count else 0.0,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun agregarVenta(articulo: String, monto: Double, notas: String = "") {
        viewModelScope.launch {
            val baseId = _uiState.value.baseActiva?.id ?: run {
                // Si no hay base activa, crear una automáticamente
                repository.crearBase("Mi primera base")
            }
            repository.agregarVenta(baseId, articulo, monto, notas)
            mostrarMensaje("Venta agregada ✓")
        }
    }

    fun eliminarVenta(ventaId: Long) {
        viewModelScope.launch {
            repository.eliminarVenta(ventaId)
            mostrarMensaje("Venta eliminada")
        }
    }

    fun abrirEditSheet(venta: VentaEntity) {
        _ventaEditando.value = venta
    }

    fun cerrarEditSheet() {
        _ventaEditando.value = null
    }

    fun editarVenta(venta: VentaEntity) {
        viewModelScope.launch {
            repository.editarVenta(venta)
            mostrarMensaje("Venta actualizada ✓")
        }
    }

    fun respaldarEnDrive() {
        viewModelScope.launch {
            val baseId = _uiState.value.baseActiva?.id ?: return@launch
            if (!repository.tieneDriveService()) {
                _uiState.update { it.copy(needsGoogleAccount = true) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.respaldarBase(baseId)
            val ex = result.exceptionOrNull()
            when {
                result.isSuccess -> mostrarMensaje("Respaldo guardado en Google Drive ✓")
                ex is UserRecoverableAuthIOException ->
                    _uiState.update { it.copy(authConsentIntent = ex.intent) }
                else -> mostrarMensaje("Error al respaldar: ${ex?.message}")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onCuentaSeleccionada(accountName: String, context: Context) {
        repository.inicializarDriveService(context, accountName)
        _uiState.update { it.copy(needsGoogleAccount = false) }
        respaldarEnDrive()
    }

    fun onPickAccountCancelled() {
        _uiState.update { it.copy(needsGoogleAccount = false) }
    }

    fun onAuthGranted() {
        _uiState.update { it.copy(authConsentIntent = null) }
        respaldarEnDrive()
    }

    fun onAuthConsentMostrado() {
        _uiState.update { it.copy(authConsentIntent = null) }
    }

    fun abrirAddSheet() { _showAddSheet.value = true }
    fun cerrarAddSheet() { _showAddSheet.value = false }

    fun mensajeMostrado() { _uiState.update { it.copy(mensaje = null) } }

    private fun mostrarMensaje(msg: String) {
        _uiState.update { it.copy(mensaje = msg) }
    }
}

// ── Factory ───────────────────────────────────────────────────────────────────

class HomeViewModelFactory(private val repository: AlusheRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("ViewModel desconocido")
    }
}
