package com.alushestore.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alushestore.data.local.ActiveUserManager
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.data.remote.CatalogoRemotoService
import com.alushestore.data.remote.ErrorDeNegocio
import com.alushestore.data.remote.ProductoRemoto
import com.alushestore.data.remote.SupabaseAuthManager
import com.alushestore.data.repository.AlusheRepository
import com.alushestore.data.repository.CartItem
import com.alushestore.data.repository.ResultadoMovimiento
import com.alushestore.domain.ProductoUnificado
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

class HomeViewModel(
    private val repository: AlusheRepository,
    private val context: Context? = null
) : ViewModel() {

    private val userManager = context?.let { ActiveUserManager.getInstance(it) }

    val vendedorActivo: StateFlow<String> = userManager?.vendedorActivo
        ?: MutableStateFlow("PRINCIPAL")

    val usuario1: StateFlow<String> = userManager?.usuario1
        ?: MutableStateFlow(ActiveUserManager.DEFAULT_USER_1)

    val usuario2: StateFlow<String> = userManager?.usuario2
        ?: MutableStateFlow(ActiveUserManager.DEFAULT_USER_2)

    // ── Catálogo del POS: lo del teléfono y lo de alushe.store juntos ────────
    private val catalogoRemoto = CatalogoRemotoService()
    private val auth = context?.let { SupabaseAuthManager.getInstance(it) }

    private val _catalogoWeb = MutableStateFlow<List<ProductoRemoto>>(emptyList())

    val productosCatalogo: StateFlow<List<ProductoUnificado>> = combine(
        repository.inventario.getProductosActivos(),
        _catalogoWeb,
        repository.cola.comprometidoPorProducto()
    ) { locales, remotos, comprometido ->
        ProductoUnificado.fusionar(locales.filter { it.activo }, remotos)
            .map { p ->
                // Lo que ya se vendió sin red todavía figura con stock allá.
                // Se resta aquí para no volver a venderlo desde este teléfono.
                val pendiente = p.idRemoto?.let { comprometido[it] } ?: 0
                if (pendiente > 0) {
                    p.copy(stockDisponible = (p.stockDisponible - pendiente).coerceAtLeast(0))
                } else p
            }
            .filter { it.stockDisponible > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cuántos movimientos esperan a que vuelva el internet */
    val movimientosPendientes: StateFlow<Int> = repository.cola.contarPendientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Cuántos quedaron rechazados por el almacén y no se reintentan */
    val movimientosEnConflicto: StateFlow<Int> = repository.cola.contarConflictos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun descartarConflictos() {
        viewModelScope.launch {
            repository.cola.descartarConflictos()
            mostrarMensaje("Avisos descartados. Revisa el stock en Inventario.")
        }
    }

    /** Cancela los descuentos que aún no se mandan al almacén */
    fun descartarPendientes() {
        viewModelScope.launch {
            repository.cola.descartarPendientes()
            mostrarMensaje("Descuentos cancelados. El stock del almacén no cambió.")
        }
    }

    init {
        cargarCatalogoWeb()
        sincronizarCola()
    }

    /** Refresca el catálogo compartido. Silencioso: si no hay red, el POS sigue
     *  funcionando con lo que haya en el teléfono. */
    fun cargarCatalogoWeb() {
        viewModelScope.launch {
            catalogoRemoto.listarProductos().onSuccess { _catalogoWeb.value = it }
        }
    }

    val clientes: StateFlow<List<ClienteEntity>> = repository.clientes
        .getAllClientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                context?.let { com.alushestore.ui.widgets.AlusheWidgetUpdater.actualizarWidgets(it) }
            }
        }
    }

    fun alternarVendedor() {
        userManager?.alternarVendedor()
        context?.let { com.alushestore.ui.widgets.AlusheWidgetUpdater.actualizarWidgets(it) }
        mostrarMensaje("Vendedor activo: ${vendedorActivo.value}")
    }

    fun setVendedorActivo(nombre: String) {
        userManager?.setVendedorActivo(nombre)
        mostrarMensaje("Vendedor activo: $nombre")
    }

    fun configurarVendedores(nombre1: String, nombre2: String) {
        userManager?.configurarNombres(nombre1, nombre2)
        mostrarMensaje("Perfiles de usuario actualizados ✓")
    }

    fun agregarVenta(articulo: String, monto: Double, notas: String = "") {
        viewModelScope.launch {
            val baseId = _uiState.value.baseActiva?.id ?: run {
                // Si no hay base activa, crear una automáticamente
                repository.crearBase("Mi primera base")
            }
            repository.agregarVenta(
                baseId = baseId,
                articulo = articulo,
                monto = monto,
                notas = notas,
                vendedor = vendedorActivo.value
            )
            mostrarMensaje("Venta agregada ✓")
        }
    }

    /**
     * Cobra el carrito. Puede mezclar piezas del teléfono y del almacén compartido.
     *
     * Las del almacén se descuentan primero por el RPC, porque es lo que puede
     * fallar (red, stock, permisos). Si alguna falla, se revierten las que ya
     * habían pasado y no se cobra nada: más vale no vender que dejar el almacén
     * descuadrado.
     */
    fun cobrarVentaPos(items: List<CartItem>, notas: String = "") {
        viewModelScope.launch {
            try {
                val delAlmacen = items.filter { it.esDelAlmacenCompartido }
                var diferidos = 0

                if (delAlmacen.isNotEmpty()) {
                    val token = auth?.tokenValido()

                    if (token == null) {
                        // Sin sesión no se puede descontar, pero la venta no se detiene.
                        encolar(delAlmacen)
                        diferidos = delAlmacen.size
                    } else {
                        val yaDescontados = mutableListOf<CartItem>()
                        var falloLaRed = false

                        for (item in delAlmacen) {
                            val idRemoto = item.producto.idRemoto ?: continue
                            val r = catalogoRemoto.registrarMovimiento(
                                accessToken = token,
                                productoId = idRemoto,
                                tipo = "salida",
                                cantidad = item.cantidad,
                                motivo = "Venta POS",
                                vendedor = vendedorActivo.value
                            )

                            when {
                                r.isSuccess -> yaDescontados += item

                                // El servidor dijo que no: ahí sí no se vende.
                                r.exceptionOrNull() is ErrorDeNegocio -> {
                                    revertir(yaDescontados, token)
                                    mostrarMensaje("${item.producto.nombre}: ${r.exceptionOrNull()?.message}")
                                    return@launch
                                }

                                // Se cayó la red: lo que falte se va a la cola.
                                else -> { falloLaRed = true; break }
                            }
                        }

                        if (falloLaRed) {
                            val faltantes = delAlmacen.filter { it !in yaDescontados }
                            encolar(faltantes)
                            diferidos = faltantes.size
                        }
                    }
                }

                val baseId = _uiState.value.baseActiva?.id ?: run {
                    repository.crearBase("Mi primera base")
                }
                repository.inventario.cobrarVentaPos(
                    baseId = baseId,
                    vendedor = vendedorActivo.value,
                    items = items,
                    notas = notas
                )
                repository.backup.programarRespaldoInventario()
                if (delAlmacen.isNotEmpty()) cargarCatalogoWeb()
                cerrarAddSheet()

                mostrarMensaje(
                    when {
                        diferidos > 0 -> "Venta cobrada ✓ · $diferidos pieza(s) se descontarán del almacén al volver el internet"
                        else -> "Venta POS cobrada con éxito ✓"
                    }
                )
            } catch (e: Exception) {
                mostrarMensaje("Error al cobrar venta POS: ${e.message}")
            }
        }
    }

    /** Manda a la cola los descuentos que no se pudieron hacer ahora */
    private suspend fun encolar(items: List<CartItem>) {
        items.forEach { item ->
            item.producto.idRemoto?.let { id ->
                repository.cola.encolarSalida(
                    productoRemotoId = id,
                    nombreProducto = item.producto.nombre,
                    cantidad = item.cantidad,
                    vendedor = vendedorActivo.value
                )
            }
        }
    }

    /** Vacía la cola si hay sesión y red. Silencioso salvo que algo quede en conflicto. */
    fun sincronizarCola() {
        viewModelScope.launch {
            val token = auth?.tokenValido() ?: return@launch
            val r = repository.cola.procesar(token)
            if (r.enviados > 0) cargarCatalogoWeb()
            if (r.nuevosConflictos > 0) {
                mostrarMensaje("${r.nuevosConflictos} movimiento(s) quedaron en conflicto, revísalos en Inventario")
            }
        }
    }

    /** Devuelve al almacén las piezas que ya se habían descontado de un cobro fallido */
    private suspend fun revertir(hechos: List<CartItem>, token: String) {
        hechos.forEach { hecho ->
            hecho.producto.idRemoto?.let { id ->
                catalogoRemoto.registrarMovimiento(
                    accessToken = token,
                    productoId = id,
                    tipo = "entrada",
                    cantidad = hecho.cantidad,
                    motivo = "Reverso: la venta no se completó",
                    vendedor = vendedorActivo.value
                )
            }
        }
    }

    /**
     * Aparta una pieza para un cliente, sea del teléfono o del almacén
     * compartido.
     *
     * En los dos casos la pieza deja de estar disponible de inmediato. La
     * diferencia es dónde: en Room pasa de disponible a apartada, y en el
     * almacén compartido sale por completo, para que el otro teléfono no la vea.
     */
    fun crearApartadoPos(
        clienteId: Long,
        producto: ProductoUnificado,
        anticipo: Double,
        notas: String = ""
    ) {
        viewModelScope.launch {
            try {
                val idLocal = producto.idLocal
                val idRemoto = producto.idRemoto

                val mensaje = when {
                    idLocal != null -> {
                        repository.inventario.crearApartadoConInventario(
                            clienteId = clienteId,
                            productoId = idLocal,
                            anticipo = anticipo,
                            vendedor = vendedorActivo.value,
                            notas = notas
                        )
                        repository.backup.programarRespaldoInventario()
                        "Apartado creado y stock reservado ✓"
                    }

                    idRemoto != null -> {
                        val r = repository.clientes.crearApartadoCompartido(
                            clienteId = clienteId,
                            productoRemotoId = idRemoto,
                            descripcion = producto.nombre,
                            monto = producto.precio,
                            anticipo = anticipo,
                            vendedor = vendedorActivo.value,
                            notas = notas
                        )
                        cargarCatalogoWeb()
                        if (r.almacen is ResultadoMovimiento.Encolado) {
                            "Apartado creado — la pieza se descuenta del almacén al volver el internet"
                        } else {
                            "Apartado creado y pieza sacada del almacén compartido ✓"
                        }
                    }

                    else -> "Ese artículo no se puede apartar"
                }

                repository.backup.programarRespaldoClientes()
                cerrarAddSheet()
                mostrarMensaje(mensaje)
            } catch (e: Exception) {
                mostrarMensaje("No se creó el apartado: ${e.message}")
            }
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

class HomeViewModelFactory(
    private val repository: AlusheRepository,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, context) as T
        }
        throw IllegalArgumentException("ViewModel desconocido")
    }
}
