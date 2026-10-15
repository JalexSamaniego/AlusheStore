package com.alushestore.data.repository

import android.content.Context
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ClienteResumen
import com.alushestore.data.local.entities.EstadoApartado
import com.alushestore.data.local.entities.TianguisSesionEntity
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AlusheRepository(
    private val db: AlusheDatabase,
    private var driveService: DriveBackupService? = null
) {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var syncJob: Job? = null

    fun inicializarDriveService(context: Context, accountName: String) {
        driveService = DriveBackupService(context, accountName)
    }

    fun tieneDriveService() = driveService != null
    // ── Bases ─────────────────────────────────────────────────────────────────

    fun getAllBases(): Flow<List<BaseEntity>> = db.baseDao().getAllBases()

    fun getBaseActiva(): Flow<BaseEntity?> = db.baseDao().getBaseActiva()

    fun getBasesCerradas(): Flow<List<BaseEntity>> = db.baseDao().getBasesCerradas()

    suspend fun crearBase(nombre: String, notas: String = ""): Long {
        val base = BaseEntity(nombre = nombre, notas = notas)
        return db.baseDao().insertBase(base)
    }

    suspend fun cerrarBase(baseId: Long) {
        db.baseDao().cerrarBase(baseId)
    }

    suspend fun getBaseById(id: Long): BaseEntity? = db.baseDao().getBaseById(id)

    suspend fun eliminarBase(base: BaseEntity) {
        db.baseDao().deleteBase(base) // Las ventas se eliminan en cascada (Room)
    }

    // ── Ventas ────────────────────────────────────────────────────────────────

    fun getVentasByBase(baseId: Long): Flow<List<VentaEntity>> =
        db.ventaDao().getVentasByBase(baseId)

    fun getTotalByBase(baseId: Long): Flow<Double?> =
        db.ventaDao().getTotalByBase(baseId)

    fun getCountByBase(baseId: Long): Flow<Int> =
        db.ventaDao().getCountByBase(baseId)

    fun getVentasHoy(baseId: Long): Flow<List<VentaEntity>> {
        val hoyInicio = inicioDelDia()
        val hoyFin = finDelDia()
        return db.ventaDao().getVentasHoy(baseId, hoyInicio, hoyFin)
    }

    fun getTotalHoy(baseId: Long): Flow<Double?> {
        val hoyInicio = inicioDelDia()
        val hoyFin = finDelDia()
        return db.ventaDao().getTotalByBaseAndFecha(baseId, hoyInicio, hoyFin)
    }

    suspend fun agregarVenta(baseId: Long, articulo: String, monto: Double, notas: String = ""): Long {
        val venta = VentaEntity(
            baseId = baseId,
            articulo = articulo,
            monto = monto,
            notas = notas
        )
        return db.ventaDao().insertVenta(venta)
    }

    suspend fun eliminarVenta(ventaId: Long) {
        db.ventaDao().deleteVentaById(ventaId)
    }

    suspend fun editarVenta(venta: VentaEntity) {
        db.ventaDao().updateVenta(venta)
    }

    // ── Drive Backup ──────────────────────────────────────────────────────────

    suspend fun respaldarBase(baseId: Long): Result<String> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        val base = db.baseDao().getBaseById(baseId)
            ?: return Result.failure(Exception("Base no encontrada"))
        val ventas = db.ventaDao().getVentasByBaseSync(baseId)

        val result = drive.subirBase(base, ventas)
        if (result.isSuccess) {
            db.baseDao().updateDriveFileId(baseId, result.getOrThrow())
        }
        return result
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
        // Asegurar que sea tratada como cerrada y no choque con existentes (Room reemplazará o insertará)
        val baseImportada = backup.base.copy(cerrada = true)
        db.baseDao().insertBase(baseImportada)
        // Eliminar las ventas que tenga esta base e insertar las de drive
        val ventasViejas = db.ventaDao().getVentasByBaseSync(baseImportada.id)
        ventasViejas.forEach { db.ventaDao().deleteVentaById(it.id) }
        backup.ventas.forEach { db.ventaDao().insertVenta(it) }
    }

    // ── Tianguis ──────────────────────────────────────────────────────────────

    fun getSesionTianguisActiva(): Flow<TianguisSesionEntity?> =
        db.tianguisSesionDao().getSesionActiva()

    fun getAllSesionesTianguis(): Flow<List<TianguisSesionEntity>> =
        db.tianguisSesionDao().getAllSesiones()

    fun getVentasTianguisBySesion(sesionId: Long): Flow<List<TianguisVentaEntity>> =
        db.tianguisVentaDao().getVentasBySesion(sesionId)

    fun getTotalTianguisBySesion(sesionId: Long): Flow<Double?> =
        db.tianguisVentaDao().getTotalBySesion(sesionId)

    fun getCountTianguisBySesion(sesionId: Long): Flow<Int> =
        db.tianguisVentaDao().getCountBySesion(sesionId)

    suspend fun iniciarSesionTianguis(nombre: String): Long {
        val sesion = TianguisSesionEntity(nombre = nombre)
        return db.tianguisSesionDao().insertSesion(sesion)
    }

    suspend fun editarNombreSesion(sesion: TianguisSesionEntity, nuevoNombre: String) {
        db.tianguisSesionDao().updateSesion(sesion.copy(nombre = nuevoNombre))
    }

    suspend fun agregarVentaTianguis(sesionId: Long, descripcion: String, monto: Double): Long {
        val venta = TianguisVentaEntity(sesionId = sesionId, descripcion = descripcion, monto = monto)
        return db.tianguisVentaDao().insertVenta(venta)
    }

    suspend fun editarVentaTianguis(venta: TianguisVentaEntity) {
        db.tianguisVentaDao().updateVenta(venta)
    }

    suspend fun eliminarVentaTianguis(ventaId: Long) {
        db.tianguisVentaDao().deleteVentaById(ventaId)
    }

    suspend fun cerrarSesionTianguis(sesionId: Long): TianguisSesionEntity? {
        val sesion = db.tianguisSesionDao().getSesionById(sesionId) ?: return null
        val cerrada = sesion.copy(activa = false, fechaFin = System.currentTimeMillis())
        db.tianguisSesionDao().updateSesion(cerrada)
        return cerrada
    }

    suspend fun guardarSesionEnBase(sesionId: Long, baseId: Long): Long {
        val sesion = db.tianguisSesionDao().getSesionById(sesionId) ?: return -1
        val total = db.tianguisVentaDao().getVentasBySesionSync(sesionId).sumOf { it.monto }
        val venta = VentaEntity(
            baseId = baseId,
            articulo = sesion.nombre,
            monto = total,
            notas = "Sesión tianguis"
        )
        val ventaId = db.ventaDao().insertVenta(venta)
        db.tianguisSesionDao().updateSesion(sesion.copy(guardadaEnBase = true))
        return ventaId
    }

    suspend fun getVentaMasAltaTianguis(sesionId: Long): Double? =
        db.tianguisVentaDao().getVentaMasAltaBySesion(sesionId)

    suspend fun getVentasTianguisBySesionSync(sesionId: Long): List<TianguisVentaEntity> =
        db.tianguisVentaDao().getVentasBySesionSync(sesionId)

    // ── Clientes ──────────────────────────────────────────────────────────────

    fun getAllClientes(): Flow<List<ClienteEntity>> = db.clienteDao().getAllClientes()

    fun getClienteById(id: Long): Flow<ClienteEntity?> = db.clienteDao().getClienteById(id)

    fun getResumenPorCliente(): Flow<List<ClienteResumen>> = db.apartadoDao().getResumenPorCliente()

    suspend fun agregarCliente(nombre: String, telefono: String = "", notas: String = ""): Long {
        val id = db.clienteDao().insertCliente(
            ClienteEntity(nombre = nombre, telefono = telefono, notas = notas)
        )
        programarRespaldoClientes()
        return id
    }

    suspend fun editarCliente(cliente: ClienteEntity) {
        db.clienteDao().updateCliente(cliente)
        programarRespaldoClientes()
    }

    suspend fun eliminarCliente(cliente: ClienteEntity) {
        db.clienteDao().deleteCliente(cliente) // Sus apartados se eliminan en cascada (Room)
        programarRespaldoClientes()
    }

    // ── Apartados / Pedidos ───────────────────────────────────────────────────

    fun getApartadosByCliente(clienteId: Long): Flow<List<ApartadoEntity>> =
        db.apartadoDao().getApartadosByCliente(clienteId)

    fun getSaldoPendienteByCliente(clienteId: Long): Flow<Double?> =
        db.apartadoDao().getSaldoPendienteByCliente(clienteId)

    fun getCountPendientesByCliente(clienteId: Long): Flow<Int> =
        db.apartadoDao().getCountPendientesByCliente(clienteId)

    suspend fun agregarApartado(
        clienteId: Long,
        descripcion: String,
        monto: Double,
        anticipo: Double = 0.0,
        notas: String = "",
        tipo: TipoApartado = TipoApartado.APARTADO
    ): Long {
        val id = db.apartadoDao().insertApartado(
            ApartadoEntity(
                clienteId = clienteId,
                descripcion = descripcion,
                monto = monto,
                anticipo = anticipo,
                notas = notas,
                tipo = tipo
            )
        )
        programarRespaldoClientes()
        return id
    }

    suspend fun editarApartado(apartado: ApartadoEntity) {
        db.apartadoDao().updateApartado(apartado)
        programarRespaldoClientes()
    }

    suspend fun eliminarApartado(apartadoId: Long) {
        db.apartadoDao().deleteApartadoById(apartadoId)
        programarRespaldoClientes()
    }

    suspend fun marcarApartadoEntregado(apartadoId: Long) {
        db.apartadoDao().updateEstado(apartadoId, EstadoApartado.ENTREGADO.name)
        programarRespaldoClientes()
    }

    // ── Drive Backup: Clientes ────────────────────────────────────────────────

    /** Sube todos los clientes y apartados a Drive (archivo único, se sobrescribe). */
    suspend fun respaldarClientes(): Result<String> {
        val drive = driveService ?: return Result.failure(Exception("No hay sesión de Google activa"))
        val clientes = db.clienteDao().getAllClientesSync()
        val apartados = db.apartadoDao().getAllApartadosSync()
        val backup = ClientesBackup(
            clientes = clientes,
            apartados = apartados,
            totalClientes = clientes.size,
            totalApartados = apartados.size,
            saldoPendiente = apartados
                .filter { it.estado == EstadoApartado.PENDIENTE }
                .sumOf { it.saldo }
        )
        return drive.subirClientes(backup)
    }

    /**
     * Respaldo silencioso tras cada cambio: espera un momento por si hay varios cambios
     * seguidos y sube una sola vez. Corre en un scope propio para no cancelarse al salir
     * de la pantalla. Si falla (sin internet, sin permiso) se ignora; queda el botón manual.
     */
    private fun programarRespaldoClientes() {
        if (driveService == null) return
        syncJob?.cancel()
        syncJob = syncScope.launch {
            delay(2000)
            syncMutex.withLock { respaldarClientes() }
        }
    }

    // ── Helpers de tiempo ─────────────────────────────────────────────────────

    private fun inicioDelDia(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun finDelDia(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}
