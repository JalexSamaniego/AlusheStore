package com.alushestore.data.repository

import androidx.room.withTransaction
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.EstadoApartado
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BackupRepository(
    private val db: AlusheDatabase,
    private var driveService: DriveBackupService? = null
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var syncJob: Job? = null

    fun actualizarDriveService(drive: DriveBackupService?) {
        driveService = drive
    }

    fun tieneDriveService(): Boolean = driveService != null

    /**
     * Vuelca transacciones pendientes de SQLite WAL a disco antes de operaciones críticas de respaldo.
     */
    suspend fun ejecutarWalCheckpoint() = withContext(Dispatchers.IO) {
        db.checkpointWal()
    }

    // ── Respaldo de Bases ─────────────────────────────────────────────────────

    suspend fun respaldarBase(baseId: Long): Result<String> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        ejecutarWalCheckpoint()
        val base = db.baseDao().getBaseById(baseId)
            ?: return Result.failure(Exception("Base no encontrada"))
        val ventas = db.ventaDao().getVentasByBaseSync(baseId)
        // Sin los detalles, una base restaurada perdía el costo congelado de
        // cada venta del POS y su utilidad salía como si todo fuera ganancia.
        val detalles = db.inventarioDao().getDetallesByBaseSync(baseId)

        val result = drive.subirBase(base, ventas, detalles)
        if (result.isSuccess) {
            db.baseDao().updateDriveFileId(baseId, result.getOrThrow())
        }
        return result
    }

    /**
     * Respaldo silencioso al abrir la app.
     *
     * Existe porque el respaldo de **ventas** era el único que no tenía nada
     * automático: dependía de que alguien se acordara de tocar la nube. Los de
     * clientes e inventario sí se disparan solos al cambiar algo, pero el
     * dinero no.
     *
     * No avisa nada si sale bien. Si falla tampoco: se vuelve a intentar la
     * próxima vez que se abra, y molestar con un error que el usuario no puede
     * resolver no sirve de nada.
     *
     * **No respalda lo que está vacío.** Esto no es un detalle: los respaldos
     * de clientes e inventario son un archivo único que se sobrescribe, así que
     * subir una base vacía encima del bueno borraría el respaldo justo cuando
     * más falta hace. Si no hay nada que guardar, no se toca Drive.
     */
    suspend fun respaldoDeApertura() = withContext(Dispatchers.IO) {
        if (driveService == null) return@withContext

        syncMutex.withLock {
            ejecutarWalCheckpoint()

            // Ventas de la base activa: lo que no tenía respaldo automático
            val baseActiva = db.baseDao().getBaseActivaSync()
            if (baseActiva != null && db.ventaDao().getVentasByBaseSync(baseActiva.id).isNotEmpty()) {
                runCatching { respaldarBase(baseActiva.id) }
            }

            if (db.clienteDao().getAllClientesSync().isNotEmpty()) {
                runCatching { respaldarClientes() }
            }

            val hayInventario = db.inventarioDao().getAllProductosSync().isNotEmpty() ||
                    db.inventarioDao().getAllLotesSync().isNotEmpty()
            if (hayInventario) {
                runCatching { respaldarInventario() }
            }
        }
    }

    suspend fun listarRespaldosDrive(): Result<List<DriveBackupInfo>> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        return drive.listarRespaldos()
    }

    suspend fun descargarRespaldo(fileId: String): Result<BaseBackup> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        return drive.descargarRespaldo(fileId)
    }

    /**
     * Trae a este teléfono una base respaldada en Drive.
     *
     * Antes se hacía con `INSERT OR REPLACE`, y eso tenía tres problemas:
     * - REPLACE borra la fila vieja, y la cascada se llevaba el **corte** de
     *   esa base.
     * - Las ventas se insertaban con su id original: en otro teléfono ese id
     *   podía ser de una venta de otra base, y la **pisaba**.
     * - Los detalles con costo no viajaban, así que la utilidad salía mal.
     *
     * Ahora nada de lo que ya está en el teléfono se borra ni se reescribe:
     * - Si es la misma base (mismo id y misma fecha de creación), sólo se
     *   agregan las ventas del respaldo que aquí no están. La base, su estado
     *   (activa o cerrada), su corte y sus ventas locales quedan intactos.
     * - Si no existe, se crea cerrada; si su id lo ocupa otra base, entra con
     *   id nuevo.
     * Las ventas conservan su id sólo si está libre, y los detalles se
     * reenlazan a las ventas que de verdad se insertaron.
     *
     * No toca la tabla de productos: un detalle cuyo producto no existe en
     * este teléfono se guarda sin enlace, con su costo y su nombre.
     */
    suspend fun importarBase(backup: BaseBackup) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val original = backup.base
            val local = db.baseDao().getBaseById(original.id)
            val esLaMisma = local != null && local.fechaCreacion == original.fechaCreacion

            // Un respaldo viejo no trae detalles (Gson los deja en null)
            val detallesRespaldo: List<DetalleVentaEntity> =
                (backup.detallesVenta as List<DetalleVentaEntity>?).orEmpty()

            when {
                esLaMisma -> {
                    val yaEstan = db.ventaDao().getVentasByBaseSync(original.id).map { it.id }.toSet()
                    insertarVentasYDetalles(
                        backup.ventas.filter { it.id !in yaEstan },
                        original.id,
                        detallesRespaldo
                    )
                }
                local == null -> {
                    val id = db.baseDao().insertBase(original.copy(cerrada = true))
                    insertarVentasYDetalles(backup.ventas, id, detallesRespaldo)
                }
                else -> {
                    // El id está ocupado por otra base: ésta entra con id nuevo
                    val id = db.baseDao().insertBase(original.copy(id = 0, cerrada = true, driveFileId = null))
                    insertarVentasYDetalles(backup.ventas, id, detallesRespaldo)
                }
            }
        }
        ejecutarWalCheckpoint()
    }

    private suspend fun insertarVentasYDetalles(
        ventas: List<VentaEntity>,
        baseId: Long,
        detalles: List<DetalleVentaEntity>
    ) {
        if (ventas.isEmpty()) return

        val ocupados = ventas.map { it.id }.chunked(500)
            .flatMap { db.ventaDao().getIdsExistentes(it) }.toSet()

        // Sólo las que se insertan: los detalles de las demás ya están aquí
        val idNuevo = mutableMapOf<Long, Long>()
        for (v in ventas) {
            val id = if (v.id in ocupados) 0L else v.id
            idNuevo[v.id] = db.ventaDao().insertVenta(v.copy(id = id, baseId = baseId))
        }

        val productosIds = detalles.mapNotNull { it.productoId }.distinct()
        val existentes = productosIds.chunked(500)
            .flatMap { db.inventarioDao().getProductoIdsExistentes(it) }.toSet()

        for (d in detalles) {
            val ventaId = idNuevo[d.ventaId] ?: continue
            db.inventarioDao().insertDetalleVenta(
                d.copy(
                    id = 0,
                    ventaId = ventaId,
                    productoId = d.productoId?.takeIf { it in existentes }
                )
            )
        }
    }

    // ── Respaldo de Clientes, Apartados y Abonos ──────────────────────────────

    suspend fun respaldarClientes(): Result<String> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        ejecutarWalCheckpoint()
        val clientes = db.clienteDao().getAllClientesSync()
        val apartados = db.apartadoDao().getAllApartadosSync()
        val abonos = db.abonoDao().getAllAbonosSync()
        val backup = ClientesBackup(
            clientes = clientes,
            apartados = apartados,
            abonos = abonos,
            totalClientes = clientes.size,
            totalApartados = apartados.size,
            saldoPendiente = apartados
                .filter { it.estado == EstadoApartado.PENDIENTE }
                .sumOf { it.saldo },
            schemaVersion = 4,
            timestamp = System.currentTimeMillis()
        )
        return drive.subirClientes(backup)
    }

    suspend fun descargarClientesRespaldo(): Result<ClientesBackup> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        return drive.descargarClientes()
    }

    suspend fun importarClientes(backup: ClientesBackup) = withContext(Dispatchers.IO) {
        val existingClientes = db.clienteDao().getAllClientesSync().associateBy { it.id }
        for (cliente in backup.clientes) {
            if (existingClientes.containsKey(cliente.id)) {
                db.clienteDao().updateCliente(cliente)
            } else {
                db.clienteDao().insertCliente(cliente)
            }
        }
        val existingApartados = db.apartadoDao().getAllApartadosSync().associateBy { it.id }
        for (apartado in backup.apartados) {
            if (existingApartados.containsKey(apartado.id)) {
                db.apartadoDao().updateApartado(apartado)
            } else {
                db.apartadoDao().insertApartado(apartado)
            }
        }
        val existingAbonos = db.abonoDao().getAllAbonosSync().associateBy { it.id }
        for (abono in backup.abonos) {
            if (existingAbonos.containsKey(abono.id)) {
                db.abonoDao().updateAbono(abono)
            } else {
                db.abonoDao().insertAbono(abono)
            }
        }
        ejecutarWalCheckpoint()
    }

    /**
     * Respaldo automático debounced con mutex y supervisor scope tras cambios en clientes/apartados.
     */
    fun programarRespaldoClientes() {
        if (driveService == null) return
        syncJob?.cancel()
        syncJob = syncScope.launch {
            delay(2000)
            syncMutex.withLock { respaldarClientes() }
        }
    }

    // ── Respaldo de Inventario y Lotes ───────────────────────────────────────

    suspend fun respaldarInventario(): Result<String> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        ejecutarWalCheckpoint()
        val lotes = db.inventarioDao().getAllLotesSync()
        val productos = db.inventarioDao().getAllProductosSync()
        val backup = InventarioBackup(
            lotes = lotes,
            productos = productos,
            totalLotes = lotes.size,
            totalProductos = productos.size,
            totalStock = productos.sumOf { it.stockTotal },
            valorInventario = productos.sumOf { it.costoUnitario * it.stockDisponible }
        )
        return drive.subirInventario(backup)
    }

    suspend fun descargarInventarioRespaldo(): Result<InventarioBackup> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        return drive.descargarInventario()
    }

    suspend fun importarInventario(backup: InventarioBackup) = withContext(Dispatchers.IO) {
        val existingLotes = db.inventarioDao().getAllLotesSync().associateBy { it.id }
        for (lote in backup.lotes) {
            if (existingLotes.containsKey(lote.id)) {
                db.inventarioDao().updateLote(lote)
            } else {
                db.inventarioDao().insertLote(lote)
            }
        }
        val existingProductos = db.inventarioDao().getAllProductosSync().associateBy { it.id }
        for (prod in backup.productos) {
            if (existingProductos.containsKey(prod.id)) {
                db.inventarioDao().updateProducto(prod)
            } else {
                db.inventarioDao().insertProducto(prod)
            }
        }
        ejecutarWalCheckpoint()
    }

    private var inventarioSyncJob: Job? = null

    fun programarRespaldoInventario() {
        if (driveService == null) return
        inventarioSyncJob?.cancel()
        inventarioSyncJob = syncScope.launch {
            delay(2500)
            syncMutex.withLock { respaldarInventario() }
        }
    }
}
