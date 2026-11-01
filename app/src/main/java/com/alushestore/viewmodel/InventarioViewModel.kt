package com.alushestore.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alushestore.data.local.entities.LoteEntity
import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.local.ActiveUserManager
import com.alushestore.data.remote.CatalogoRemotoService
import com.alushestore.data.remote.ProductoRemoto
import com.alushestore.data.remote.SupabaseAuthManager
import com.alushestore.data.repository.AlusheRepository
import com.alushestore.data.repository.LoteRentabilidad
import com.alushestore.domain.ProductoUnificado
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class InventarioUiState(
    val lotes: List<LoteRentabilidad> = emptyList(),
    val lotesRaw: List<LoteEntity> = emptyList(),
    val productos: List<ProductoEntity> = emptyList(),
    val categorias: List<String> = emptyList(),
    val query: String = "",
    val categoriaFiltro: String? = null,
    val modoPrivacidad: Boolean = false,
    val pestañaSeleccionada: Int = 0, // 0: Catálogo, 1: Lotes
    val scannerActivo: Boolean = false,
    val valorTotalInventario: Double = 0.0,
    val totalStockPiezas: Int = 0,
    val totalPiezasApartadas: Int = 0,
    val isLoading: Boolean = true,
    val mensaje: String? = null
)

class InventarioViewModel(
    application: Application,
    private val repository: AlusheRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("alushe_inventario_prefs", Context.MODE_PRIVATE)

    private val _query = MutableStateFlow("")
    private val _categoriaFiltro = MutableStateFlow<String?>(null)
    private val _modoPrivacidad = MutableStateFlow(prefs.getBoolean("modo_privacidad", false))
    private val _pestañaSeleccionada = MutableStateFlow(0)
    private val _scannerActivo = MutableStateFlow(false)
    private val _mensaje = MutableStateFlow<String?>(null)

    // Sheets & Dialogs
    private val _showAddProductoSheet = MutableStateFlow(false)
    val showAddProductoSheet: StateFlow<Boolean> = _showAddProductoSheet.asStateFlow()

    private val _productoEditando = MutableStateFlow<ProductoEntity?>(null)
    val productoEditando: StateFlow<ProductoEntity?> = _productoEditando.asStateFlow()

    private val _showAddLoteSheet = MutableStateFlow(false)
    val showAddLoteSheet: StateFlow<Boolean> = _showAddLoteSheet.asStateFlow()

    // ── Catálogo compartido con alushe.store ─────────────────────────────────
    // Vive en flujos aparte y no dentro de InventarioUiState porque ese estado
    // se arma con un combine de los flujos locales de Room.
    private val catalogoRemoto = CatalogoRemotoService()

    private val _catalogoWeb = MutableStateFlow<List<ProductoRemoto>>(emptyList())
    val catalogoWeb: StateFlow<List<ProductoRemoto>> = _catalogoWeb.asStateFlow()

    private val _cargandoCatalogoWeb = MutableStateFlow(false)
    val cargandoCatalogoWeb: StateFlow<Boolean> = _cargandoCatalogoWeb.asStateFlow()

    private val _errorCatalogoWeb = MutableStateFlow<String?>(null)
    val errorCatalogoWeb: StateFlow<String?> = _errorCatalogoWeb.asStateFlow()

    // ── Sesión compartida y venta contra el stock del almacén ────────────────
    private val auth = SupabaseAuthManager.getInstance(application)
    private val usuarios = ActiveUserManager.getInstance(application)

    val emparejado: StateFlow<Boolean> = auth.emparejado
    val correoSesion: StateFlow<String?> = auth.correo

    /** Producto que se está por vender del catálogo compartido */
    private val _productoParaVender = MutableStateFlow<ProductoUnificado?>(null)
    val productoParaVender: StateFlow<ProductoUnificado?> = _productoParaVender.asStateFlow()

    /** true cuando hace falta emparejar el teléfono antes de poder vender */
    private val _pidiendoEmparejar = MutableStateFlow(false)
    val pidiendoEmparejar: StateFlow<Boolean> = _pidiendoEmparejar.asStateFlow()

    private val _emparejando = MutableStateFlow(false)
    val emparejando: StateFlow<Boolean> = _emparejando.asStateFlow()

    private val _vendiendo = MutableStateFlow(false)
    val vendiendo: StateFlow<Boolean> = _vendiendo.asStateFlow()

    /**
     * Catálogo único: lo del teléfono y lo de alushe.store en una sola lista,
     * hermanado por nombre. Es la fuente que debe consumir la pestaña de POS
     * cuando se absorba la pestaña Web (ver RETOMAR.md).
     *
     * Vive en su propio flujo y no dentro de InventarioUiState porque ese
     * combine ya usa las cinco ranuras de la sobrecarga normal.
     */
    private val _catalogoFusionado: StateFlow<List<ProductoUnificado>> = combine(
        repository.inventario.getProductosActivos(),
        _catalogoWeb
    ) { locales, remotos ->
        ProductoUnificado.fusionar(locales.filter { it.activo }, remotos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Las categorías de los dos orígenes juntas, para los chips de filtro */
    val categoriasUnificadas: StateFlow<List<String>> = _catalogoFusionado
        .map { lista ->
            lista.mapNotNull { it.categoria?.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** El catálogo único ya filtrado por búsqueda y categoría, listo para la lista del POS */
    val catalogoUnificado: StateFlow<List<ProductoUnificado>> = combine(
        _catalogoFusionado,
        _query,
        _categoriaFiltro
    ) { lista, query, categoria ->
        val q = ProductoUnificado.normalizar(query)
        lista.filter { p ->
            val coincideTexto = q.isBlank() ||
                ProductoUnificado.normalizar(p.nombre).contains(q) ||
                p.sku?.lowercase()?.contains(query.trim().lowercase()) == true ||
                p.categoria?.let { ProductoUnificado.normalizar(it).contains(q) } == true
            val coincideCategoria = categoria == null || p.categoria.equals(categoria, ignoreCase = true)
            coincideTexto && coincideCategoria
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _loteEditando = MutableStateFlow<LoteEntity?>(null)
    val loteEditando: StateFlow<LoteEntity?> = _loteEditando.asStateFlow()

    private data class FiltrosBasicos(
        val query: String,
        val categoriaFiltro: String?,
        val modoPrivacidad: Boolean,
        val pestañaSeleccionada: Int,
        val scannerActivo: Boolean
    )

    private val _filtrosFlow = combine(
        _query,
        _categoriaFiltro,
        _modoPrivacidad,
        _pestañaSeleccionada,
        _scannerActivo
    ) { q, cat, priv, pest, scan ->
        FiltrosBasicos(q, cat, priv, pest, scan)
    }

    val uiState: StateFlow<InventarioUiState> = combine(
        repository.inventario.getProductosActivos(),
        repository.inventario.getLotesActivos(),
        repository.inventario.getAllLotesRentabilidad(),
        _filtrosFlow,
        _mensaje
    ) { prods, lotesRaw, lotesRentabilidad, filtros, msg ->

        val prodsActivos = prods.filter { it.activo }
        val lotesActivos = lotesRaw.filter { it.activo }

        val qTrim = filtros.query.trim().lowercase()
        val filtrados = prodsActivos.filter { prod ->
            val matchQuery = if (qTrim.isBlank()) true else {
                prod.nombre.lowercase().contains(qTrim) ||
                        prod.sku.lowercase().contains(qTrim) ||
                        (prod.categoria?.lowercase()?.contains(qTrim) == true)
            }
            val matchCat = if (filtros.categoriaFiltro == null) true else prod.categoria.equals(filtros.categoriaFiltro, ignoreCase = true)
            matchQuery && matchCat
        }

        val categoriasUnicas = prodsActivos.mapNotNull { it.categoria?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        val totalStock = prodsActivos.sumOf { it.stockDisponible }
        val totalApartadas = prodsActivos.sumOf { it.stockApartado }
        val valorTotal = prodsActivos.sumOf { it.costoUnitario * it.stockDisponible }

        InventarioUiState(
            lotes = lotesRentabilidad,
            lotesRaw = lotesActivos,
            productos = filtrados,
            categorias = categoriasUnicas,
            query = filtros.query,
            categoriaFiltro = filtros.categoriaFiltro,
            modoPrivacidad = filtros.modoPrivacidad,
            pestañaSeleccionada = filtros.pestañaSeleccionada,
            scannerActivo = filtros.scannerActivo,
            valorTotalInventario = valorTotal,
            totalStockPiezas = totalStock,
            totalPiezasApartadas = totalApartadas,
            isLoading = false,
            mensaje = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InventarioUiState()
    )

    init {
        // El catálogo compartido se trae al entrar, sin que haya que pedirlo:
        // ahora vive dentro de la pestaña de POS, no en una aparte.
        cargarCatalogoWeb()
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setCategoriaFiltro(cat: String?) {
        _categoriaFiltro.value = if (_categoriaFiltro.value == cat) null else cat
    }

    fun setModoPrivacidad(activado: Boolean) {
        _modoPrivacidad.value = activado
        prefs.edit().putBoolean("modo_privacidad", activado).apply()
    }

    fun toggleModoPrivacidad() {
        setModoPrivacidad(!_modoPrivacidad.value)
    }

    fun setPestaña(idx: Int) {
        _pestañaSeleccionada.value = idx
    }

    fun setScannerActivo(activo: Boolean) {
        _scannerActivo.value = activo
    }

    fun toggleScanner() {
        _scannerActivo.value = !_scannerActivo.value
    }

    fun onBarcodeScanned(code: String) {
        _query.value = code
        _scannerActivo.value = false
        mostrarMensaje("Código escaneado: $code")
    }

    // ── Acciones de Productos ─────────────────────────────────────────────────

    fun abrirAddProductoSheet() {
        _showAddProductoSheet.value = true
    }

    fun cerrarAddProductoSheet() {
        _showAddProductoSheet.value = false
    }

    fun abrirEditProductoSheet(producto: ProductoEntity) {
        _productoEditando.value = producto
    }

    /**
     * Abre la edición desde la lista unificada del POS.
     * Lo que sólo vive en el catálogo web todavía no se puede editar: hace falta
     * sesión autenticada en Supabase (ver RETOMAR.md).
     */
    fun abrirEditDesdeUnificado(p: ProductoUnificado) {
        val idLocal = p.idLocal
        if (idLocal == null) {
            // Lo que sólo vive en la web todavía no se edita, pero sí se vende
            // descontando del almacén compartido.
            _productoParaVender.value = p
            return
        }
        viewModelScope.launch {
            repository.inventario.getProductoById(idLocal).first()?.let {
                _productoEditando.value = it
            }
        }
    }

    // ── Emparejamiento y venta contra el almacén compartido ──────────────────

    fun cerrarVentaWeb() { _productoParaVender.value = null }

    fun abrirEmparejamiento() { _pidiendoEmparejar.value = true }
    fun cerrarEmparejamiento() { _pidiendoEmparejar.value = false }

    fun desemparejar() {
        auth.desemparejar()
        mostrarMensaje("Este teléfono ya no tiene sesión del almacén compartido")
    }

    /** Se hace una sola vez por teléfono; después la sesión se renueva sola. */
    fun emparejar(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            mostrarMensaje("Faltan el correo y la contraseña")
            return
        }
        viewModelScope.launch {
            _emparejando.value = true
            auth.emparejar(email, password)
                .onSuccess {
                    _pidiendoEmparejar.value = false
                    mostrarMensaje("Teléfono emparejado como $it ✓")
                }
                .onFailure { mostrarMensaje(it.message ?: "No se pudo emparejar") }
            _emparejando.value = false
        }
    }

    /**
     * Guarda stock, precio y costo de un producto del catálogo compartido.
     *
     * El stock va como **ajuste** por el RPC, para que quede en la bitácora con
     * el nombre de quien lo cambió. Precio y costo van por PATCH directo, que
     * no mueve inventario y por eso no necesita bitácora.
     *
     * Aquí no se vende: vender es del POS de la pantalla principal.
     */
    fun guardarProductoWeb(
        p: ProductoUnificado,
        nuevoStock: Int?,
        nuevoPrecio: Double?,
        nuevoCosto: Double?
    ) {
        val idRemoto = p.idRemoto
        if (idRemoto == null) {
            mostrarMensaje("Ese producto no está en el catálogo compartido")
            return
        }

        viewModelScope.launch {
            _vendiendo.value = true

            val token = auth.tokenValido()
            if (token == null) {
                _vendiendo.value = false
                _productoParaVender.value = null
                _pidiendoEmparejar.value = true
                mostrarMensaje("Empareja este teléfono para poder editar el catálogo compartido")
                return@launch
            }

            val precioCambia = nuevoPrecio != null && nuevoPrecio != p.precio
            val costoCambia = nuevoCosto != null && nuevoCosto != p.costo
            var todoBien = true

            if (precioCambia || costoCambia) {
                catalogoRemoto.actualizarPrecioYCosto(
                    accessToken = token,
                    productoId = idRemoto,
                    precio = if (precioCambia) nuevoPrecio else null,
                    costo = if (costoCambia) nuevoCosto else null
                ).onFailure {
                    todoBien = false
                    mostrarMensaje(it.message ?: "No se pudo guardar precio y costo")
                }
            }

            if (todoBien && nuevoStock != null && nuevoStock != p.stockDisponible) {
                catalogoRemoto.registrarMovimiento(
                    accessToken = token,
                    productoId = idRemoto,
                    tipo = "ajuste",
                    cantidad = nuevoStock,
                    motivo = "Ajuste desde la app",
                    vendedor = usuarios.vendedorActivo.value
                ).onFailure {
                    todoBien = false
                    mostrarMensaje(it.message ?: "No se pudo ajustar el stock")
                }
            }

            if (todoBien) {
                _productoParaVender.value = null
                cargarCatalogoWeb()
                mostrarMensaje("\"${p.nombre}\" actualizado ✓")
            }

            _vendiendo.value = false
        }
    }

    fun cerrarEditProductoSheet() {
        _productoEditando.value = null
    }

    /**
     * Da de alta un producto.
     *
     * Siempre se crea primero en este teléfono. Si [publicarEnWeb] está
     * prendido, además se publica en alushe.store **con el mismo SKU**, que es
     * lo que deja a los dos lados hermanados de por vida aunque después alguien
     * le cambie el nombre desde el admin web.
     *
     * Si la publicación falla, el producto local se conserva: perder la captura
     * sería peor que quedarse sin publicar, y se puede reintentar.
     */
    fun agregarProducto(
        sku: String,
        nombre: String,
        categoria: String?,
        loteId: Long?,
        costoUnitario: Double,
        precioVenta: Double,
        stockInicial: Int,
        ubicacion: String,
        publicarEnWeb: Boolean = false
    ) {
        if (nombre.isBlank()) {
            mostrarMensaje("El nombre del producto no puede estar vacío")
            return
        }
        viewModelScope.launch {
            try {
                val idLocal = repository.inventario.agregarProducto(
                    sku = sku,
                    nombre = nombre,
                    categoria = categoria,
                    loteId = loteId,
                    costoUnitario = costoUnitario,
                    precioVenta = precioVenta,
                    stockInicial = stockInicial,
                    ubicacion = ubicacion
                )
                repository.backup.programarRespaldoInventario()
                cerrarAddProductoSheet()

                if (!publicarEnWeb) {
                    mostrarMensaje("Producto agregado al catálogo ✓")
                    return@launch
                }

                // El SKU que de verdad quedó: si llegó vacío, lo autogeneró el
                // repositorio y hay que leerlo para mandar ese mismo.
                val skuFinal = sku.trim().ifBlank {
                    repository.inventario.getProductoByIdSync(idLocal)?.sku.orEmpty()
                }

                val token = auth.tokenValido()
                if (token == null) {
                    mostrarMensaje("Producto guardado aquí. Para publicarlo, empareja el teléfono")
                    return@launch
                }

                val r = catalogoRemoto.crearProducto(
                    accessToken = token,
                    nombre = nombre.trim(),
                    precio = precioVenta,
                    stock = stockInicial,
                    sku = skuFinal,
                    categoria = categoria,
                    costo = costoUnitario.takeIf { it > 0.0 }
                )

                if (r.isSuccess) {
                    cargarCatalogoWeb()
                    mostrarMensaje("Publicado en alushe.store ✓")
                } else {
                    mostrarMensaje(
                        "Guardado aquí, pero no se publicó: ${r.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                mostrarMensaje("Error al agregar producto: ${e.message}")
            }
        }
    }

    fun editarProducto(producto: ProductoEntity) {
        viewModelScope.launch {
            try {
                repository.inventario.editarProducto(producto)
                repository.backup.programarRespaldoInventario()
                cerrarEditProductoSheet()
                mostrarMensaje("Producto actualizado ✓")
            } catch (e: Exception) {
                mostrarMensaje("Error al actualizar producto: ${e.message}")
            }
        }
    }

    fun eliminarProducto(producto: ProductoEntity) {
        viewModelScope.launch {
            try {
                repository.inventario.eliminarProducto(producto)
                repository.backup.programarRespaldoInventario()
                mostrarMensaje("Producto retirado del catálogo")
            } catch (e: Exception) {
                mostrarMensaje("Error al eliminar producto: ${e.message}")
            }
        }
    }

    fun autogenerarSku(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val nuevoSku = repository.inventario.generarSiguienteSku()
            onResult(nuevoSku)
        }
    }

    // ── Acciones de Lotes ─────────────────────────────────────────────────────

    fun abrirAddLoteSheet() {
        _showAddLoteSheet.value = true
    }

    fun cerrarAddLoteSheet() {
        _showAddLoteSheet.value = false
    }

    fun abrirEditLoteSheet(lote: LoteEntity) {
        _loteEditando.value = lote
    }

    fun cerrarEditLoteSheet() {
        _loteEditando.value = null
    }

    fun agregarLote(
        nombre: String,
        inversionMercancia: Double,
        gastosExtraEnvio: Double,
        fechaCompra: Long,
        notas: String?
    ) {
        if (nombre.isBlank()) {
            mostrarMensaje("El nombre del lote no puede estar vacío")
            return
        }
        viewModelScope.launch {
            try {
                repository.inventario.agregarLote(
                    nombre = nombre,
                    inversionMercancia = inversionMercancia,
                    gastosExtraEnvio = gastosExtraEnvio,
                    fechaCompra = fechaCompra,
                    notas = notas
                )
                repository.backup.programarRespaldoInventario()
                cerrarAddLoteSheet()
                mostrarMensaje("Lote de inversión creado ✓")
            } catch (e: Exception) {
                mostrarMensaje("Error al agregar lote: ${e.message}")
            }
        }
    }

    fun editarLote(lote: LoteEntity) {
        viewModelScope.launch {
            try {
                repository.inventario.editarLote(lote)
                repository.backup.programarRespaldoInventario()
                cerrarEditLoteSheet()
                mostrarMensaje("Lote actualizado ✓")
            } catch (e: Exception) {
                mostrarMensaje("Error al actualizar lote: ${e.message}")
            }
        }
    }

    fun eliminarLote(lote: LoteEntity) {
        viewModelScope.launch {
            try {
                repository.inventario.eliminarLote(lote)
                repository.backup.programarRespaldoInventario()
                mostrarMensaje("Lote eliminado")
            } catch (e: Exception) {
                mostrarMensaje("Error al eliminar lote: ${e.message}")
            }
        }
    }

    fun calcularProrrateo(gastosEnvio: Double, piezas: Int): Double {
        return repository.inventario.calcularProrrateoEnvio(gastosEnvio, piezas)
    }

    // ── Sincronización Drive ──────────────────────────────────────────────────

    fun respaldarEnDrive() {
        viewModelScope.launch {
            mostrarMensaje("Subiendo inventario a Google Drive...")
            val result = repository.respaldarInventario()
            if (result.isSuccess) {
                mostrarMensaje("Inventario respaldado en Drive con éxito ✓")
            } else {
                mostrarMensaje("Error al respaldar en Drive: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Trae el catálogo de alushe.store. Es solo lectura: usa la clave publishable
     * que el sitio ya expone en público, así que no necesita iniciar sesión.
     */
    fun cargarCatalogoWeb() {
        if (_cargandoCatalogoWeb.value) return
        viewModelScope.launch {
            _cargandoCatalogoWeb.value = true
            _errorCatalogoWeb.value = null

            val resultado = catalogoRemoto.listarProductos()
            resultado
                .onSuccess { productos ->
                    _catalogoWeb.value = productos
                    mostrarMensaje("Catálogo web: ${productos.size} productos ✓")
                }
                .onFailure { e ->
                    _errorCatalogoWeb.value = e.message ?: "No se pudo leer el catálogo web"
                }

            _cargandoCatalogoWeb.value = false
        }
    }

    fun mensajeMostrado() {
        _mensaje.value = null
    }

    private fun mostrarMensaje(msg: String) {
        _mensaje.value = msg
    }
}

class InventarioViewModelFactory(
    private val application: Application,
    private val repository: AlusheRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return InventarioViewModel(application, repository) as T
    }
}
