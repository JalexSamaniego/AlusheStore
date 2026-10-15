package com.alushestore.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ClienteResumen
import com.alushestore.data.local.entities.EstadoApartado
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.data.repository.AlusheRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ClientesUiState(
    val clientes: List<ClienteEntity> = emptyList(),
    /** clienteId → apartados pendientes y saldo por cobrar */
    val resumenes: Map<Long, ClienteResumen> = emptyMap(),
    val totalPorCobrar: Double = 0.0,
    val totalPendientes: Int = 0,
    val isLoading: Boolean = true,
    val isRespaldando: Boolean = false,
    val mensaje: String? = null,
    val needsGoogleAccount: Boolean = false,
    val authConsentIntent: Intent? = null
)

data class ClienteDetalleUiState(
    val cliente: ClienteEntity? = null,
    val apartados: List<ApartadoEntity> = emptyList(),
    val saldoPendiente: Double = 0.0,
    val countPendientes: Int = 0,
    val isLoading: Boolean = true,
    val mensaje: String? = null
)

class ClientesViewModel(private val repository: AlusheRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientesUiState())
    val uiState: StateFlow<ClientesUiState> = _uiState.asStateFlow()

    // ── Sheets ──
    private val _showAddClienteSheet = MutableStateFlow(false)
    val showAddClienteSheet: StateFlow<Boolean> = _showAddClienteSheet.asStateFlow()

    private val _clienteEditando = MutableStateFlow<ClienteEntity?>(null)
    val clienteEditando: StateFlow<ClienteEntity?> = _clienteEditando.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllClientes(),
                repository.getResumenPorCliente()
            ) { lista, resumenes ->
                Triple(lista, resumenes.associateBy { it.clienteId }, resumenes)
            }.collect { (lista, porCliente, resumenes) ->
                _uiState.update {
                    it.copy(
                        clientes = lista,
                        resumenes = porCliente,
                        totalPorCobrar = resumenes.sumOf { r -> r.saldo },
                        totalPendientes = resumenes.sumOf { r -> r.pendientes },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun agregarCliente(nombre: String, telefono: String, notas: String) {
        if (nombre.isBlank()) return
        viewModelScope.launch {
            repository.agregarCliente(nombre, telefono, notas)
            mostrarMensaje("Cliente agregado ✓")
        }
    }

    fun editarCliente(cliente: ClienteEntity) {
        viewModelScope.launch {
            repository.editarCliente(cliente)
            mostrarMensaje("Cliente actualizado ✓")
        }
    }

    fun eliminarCliente(cliente: ClienteEntity) {
        viewModelScope.launch {
            repository.eliminarCliente(cliente)
            mostrarMensaje("Cliente eliminado")
        }
    }

    // ── Respaldo manual en Drive ──────────────────────────────────────────────

    fun respaldarEnDrive() {
        viewModelScope.launch {
            if (!repository.tieneDriveService()) {
                _uiState.update { it.copy(needsGoogleAccount = true) }
                return@launch
            }
            _uiState.update { it.copy(isRespaldando = true) }
            val result = repository.respaldarClientes()
            val ex = result.exceptionOrNull()
            when {
                result.isSuccess -> mostrarMensaje("Clientes respaldados en Google Drive ✓")
                ex is UserRecoverableAuthIOException ->
                    _uiState.update { it.copy(authConsentIntent = ex.intent) }
                else -> mostrarMensaje("Error al respaldar: ${ex?.message}")
            }
            _uiState.update { it.copy(isRespaldando = false) }
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

    fun abrirAddClienteSheet() { _showAddClienteSheet.value = true }
    fun cerrarAddClienteSheet() { _showAddClienteSheet.value = false }

    fun abrirEditClienteSheet(cliente: ClienteEntity) { _clienteEditando.value = cliente }
    fun cerrarEditClienteSheet() { _clienteEditando.value = null }

    fun mensajeMostrado() = _uiState.update { it.copy(mensaje = null) }
    private fun mostrarMensaje(msg: String) = _uiState.update { it.copy(mensaje = msg) }
}

class ClienteDetalleViewModel(
    private val repository: AlusheRepository,
    private val clienteId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClienteDetalleUiState())
    val uiState: StateFlow<ClienteDetalleUiState> = _uiState.asStateFlow()

    private val _showAddApartadoSheet = MutableStateFlow(false)
    val showAddApartadoSheet: StateFlow<Boolean> = _showAddApartadoSheet.asStateFlow()

    private val _apartadoEditando = MutableStateFlow<ApartadoEntity?>(null)
    val apartadoEditando: StateFlow<ApartadoEntity?> = _apartadoEditando.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getClienteById(clienteId),
                repository.getApartadosByCliente(clienteId),
                repository.getSaldoPendienteByCliente(clienteId),
                repository.getCountPendientesByCliente(clienteId)
            ) { cliente, apartados, saldo, count ->
                ClienteDetalleUiState(
                    cliente = cliente,
                    apartados = apartados,
                    saldoPendiente = saldo ?: 0.0,
                    countPendientes = count,
                    isLoading = false
                )
            }.collect { nuevo ->
                // Conserva el mensaje en curso al refrescar los datos
                _uiState.update { nuevo.copy(mensaje = it.mensaje) }
            }
        }
    }

    fun agregarApartado(
        descripcion: String,
        monto: Double,
        anticipo: Double,
        notas: String,
        tipo: TipoApartado
    ) {
        if (descripcion.isBlank()) return
        viewModelScope.launch {
            repository.agregarApartado(clienteId, descripcion, monto, anticipo, notas, tipo)
            mostrarMensaje(if (tipo == TipoApartado.PEDIDO) "Pedido agregado ✓" else "Apartado agregado ✓")
        }
    }

    fun editarApartado(apartado: ApartadoEntity) {
        viewModelScope.launch {
            repository.editarApartado(apartado)
            mostrarMensaje("Actualizado ✓")
        }
    }

    fun eliminarApartado(apartadoId: Long) {
        viewModelScope.launch {
            repository.eliminarApartado(apartadoId)
            mostrarMensaje("Eliminado")
        }
    }

    fun marcarEntregado(apartadoId: Long) {
        viewModelScope.launch {
            repository.marcarApartadoEntregado(apartadoId)
            mostrarMensaje("Marcado como entregado ✓")
        }
    }

    fun marcarCancelado(apartado: ApartadoEntity) {
        viewModelScope.launch {
            repository.editarApartado(apartado.copy(estado = EstadoApartado.CANCELADO))
            mostrarMensaje("Cancelado")
        }
    }

    fun reactivar(apartado: ApartadoEntity) {
        viewModelScope.launch {
            repository.editarApartado(apartado.copy(estado = EstadoApartado.PENDIENTE))
            mostrarMensaje("Reactivado ✓")
        }
    }

    fun abrirAddApartadoSheet() { _showAddApartadoSheet.value = true }
    fun cerrarAddApartadoSheet() { _showAddApartadoSheet.value = false }

    fun abrirEditApartadoSheet(apartado: ApartadoEntity) { _apartadoEditando.value = apartado }
    fun cerrarEditApartadoSheet() { _apartadoEditando.value = null }

    fun mensajeMostrado() = _uiState.update { it.copy(mensaje = null) }
    private fun mostrarMensaje(msg: String) = _uiState.update { it.copy(mensaje = msg) }
}

class ClientesViewModelFactory(private val repository: AlusheRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ClientesViewModel(repository) as T
    }
}

class ClienteDetalleViewModelFactory(
    private val repository: AlusheRepository,
    private val clienteId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ClienteDetalleViewModel(repository, clienteId) as T
    }
}
