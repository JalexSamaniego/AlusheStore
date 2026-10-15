package com.alushestore.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.repository.AlusheRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.alushestore.data.repository.DriveBackupInfo
import com.alushestore.data.repository.BaseBackup

data class BasesUiState(
    val baseActiva: BaseEntity? = null,
    val basesCerradas: List<BaseEntity> = emptyList(),
    val respaldosDrive: List<DriveBackupInfo> = emptyList(),
    val baseDescargada: BaseBackup? = null,
    val isLoading: Boolean = false,
    val mensaje: String? = null,
    val needsGoogleAccount: Boolean = false,
    val pendingBackupBaseId: Long? = null,
    val pendingAction: String? = null, // "listar" o "descargar"
    val authConsentIntent: Intent? = null
)

class BasesViewModel(private val repository: AlusheRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BasesUiState())
    val uiState: StateFlow<BasesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getBaseActiva(),
                repository.getBasesCerradas()
            ) { activa, cerradas ->
                BasesUiState(baseActiva = activa, basesCerradas = cerradas)
            }.collect { _uiState.value = it }
        }
    }

    fun crearBase(nombre: String, notas: String = "") {
        if (nombre.isBlank()) return
        viewModelScope.launch {
            repository.crearBase(nombre.trim(), notas.trim())
            mostrarMensaje("Base \"$nombre\" creada ✓")
        }
    }

    fun cerrarBase(baseId: Long, nombreBase: String) {
        viewModelScope.launch {
            repository.cerrarBase(baseId)
            mostrarMensaje("\"$nombreBase\" cerrada")
        }
    }

    fun eliminarBase(base: BaseEntity) {
        viewModelScope.launch {
            repository.eliminarBase(base)
            mostrarMensaje("Base eliminada")
        }
    }

    fun respaldarBase(baseId: Long) {
        viewModelScope.launch {
            if (!repository.tieneDriveService()) {
                _uiState.update { it.copy(needsGoogleAccount = true, pendingBackupBaseId = baseId) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.respaldarBase(baseId)
            val ex = result.exceptionOrNull()
            when {
                result.isSuccess -> {
                    mostrarMensaje("Respaldo guardado en Drive ✓")
                    listarRespaldosDrive() // Actualizar lista
                }
                ex is UserRecoverableAuthIOException ->
                    _uiState.update { it.copy(authConsentIntent = ex.intent, pendingBackupBaseId = baseId) }
                else -> mostrarMensaje("Error: ${ex?.message}")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun listarRespaldosDrive() {
        viewModelScope.launch {
            if (!repository.tieneDriveService()) {
                _uiState.update { it.copy(needsGoogleAccount = true, pendingAction = "listar") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.listarRespaldosDrive()
            val ex = result.exceptionOrNull()
            when {
                result.isSuccess -> _uiState.update { it.copy(respaldosDrive = result.getOrThrow()) }
                ex is UserRecoverableAuthIOException ->
                    _uiState.update { it.copy(authConsentIntent = ex.intent, pendingAction = "listar") }
                else -> mostrarMensaje("Error al buscar en Drive: ${ex?.message}")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun descargarRespaldoDrive(fileId: String) {
        viewModelScope.launch {
            if (!repository.tieneDriveService()) {
                _uiState.update { it.copy(needsGoogleAccount = true, pendingAction = "descargar", pendingBackupBaseId = fileId.hashCode().toLong()) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.descargarRespaldo(fileId)
            if (result.isSuccess) {
                _uiState.update { it.copy(baseDescargada = result.getOrThrow()) }
            } else {
                mostrarMensaje("Error al descargar respaldo: ${result.exceptionOrNull()?.message}")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun cerrarDetalleDrive() {
        _uiState.update { it.copy(baseDescargada = null) }
    }

    fun importarBaseLocal() {
        val backup = _uiState.value.baseDescargada ?: return
        viewModelScope.launch {
            repository.importarBase(backup)
            cerrarDetalleDrive()
            mostrarMensaje("Base importada correctamente ✓")
        }
    }

    fun onCuentaSeleccionada(accountName: String, context: Context) {
        repository.inicializarDriveService(context, accountName)
        val accion = _uiState.value.pendingAction
        val baseId = _uiState.value.pendingBackupBaseId
        _uiState.update { it.copy(needsGoogleAccount = false, pendingAction = null, pendingBackupBaseId = null) }
        
        when (accion) {
            "listar" -> listarRespaldosDrive()
            "descargar" -> if (baseId != null) mostrarMensaje("Intenta descargar nuevamente") // Hacky, but avoid string id conversion
            else -> if (baseId != null) respaldarBase(baseId)
        }
    }

    fun onPickAccountCancelled() {
        _uiState.update { it.copy(needsGoogleAccount = false, pendingBackupBaseId = null, pendingAction = null) }
    }

    fun onAuthGranted() {
        val accion = _uiState.value.pendingAction
        val baseId = _uiState.value.pendingBackupBaseId
        _uiState.update { it.copy(authConsentIntent = null, pendingAction = null) }
        
        when (accion) {
            "listar" -> listarRespaldosDrive()
            "descargar" -> mostrarMensaje("Intenta descargar nuevamente")
            else -> if (baseId != null) respaldarBase(baseId)
        }
    }

    fun onAuthConsentMostrado() {
        _uiState.update { it.copy(authConsentIntent = null) }
    }

    fun mensajeMostrado() = _uiState.update { it.copy(mensaje = null) }

    private fun mostrarMensaje(msg: String) = _uiState.update { it.copy(mensaje = msg) }
}

class BasesViewModelFactory(private val repository: AlusheRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BasesViewModel(repository) as T
    }
}
