package com.alushestore.data.repository

import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.EstadoApartado
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

        val result = drive.subirBase(base, ventas)
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

    suspend fun importarBase(backup: BaseBackup) {
        val baseImportada = backup.base.copy(cerrada = true)
        db.baseDao().insertBase(baseImportada)
        val ventasViejas = db.ventaDao().getVentasByBaseSync(baseImportada.id)
        ventasViejas.forEach { db.ventaDao().deleteVentaById(it.id) }
        backup.ventas.forEach { db.ventaDao().insertVenta(it) }
        ejecutarWalCheckpoint()
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
