package com.alushestore.data.repository

import android.content.Context
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoConAbonos
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ClienteResumen
import com.alushestore.data.local.entities.TianguisSesionEntity
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Fachada principal de la capa de datos de AlusheStore.
 * Desacopla y coordina los repositorios de dominio específicos:
 * - [BasesVentasRepository] (ventas y administración de bases)
 * - [TianguisRepository] (sesiones de venta en tianguis)
 * - [ClientesRepository] (clientes, apartados, pedidos y abonos)
 * - [BackupRepository] (sincronización con Drive y checkpoints WAL)
 */
class AlusheRepository(
    private val db: AlusheDatabase,
    private var driveService: DriveBackupService? = null
) {
    val backup = BackupRepository(db, driveService)
    val ventas = BasesVentasRepository(db)
    val tianguis = TianguisRepository(db)
    val clientes = ClientesRepository(db, backup, ventas)

    fun inicializarDriveService(context: Context, accountName: String) {
        driveService = DriveBackupService(context, accountName)
        backup.actualizarDriveService(driveService)
    }

    fun tieneDriveService(): Boolean = backup.tieneDriveService()

    // ── Bases ─────────────────────────────────────────────────────────────────

    fun getAllBases(): Flow<List<BaseEntity>> = ventas.getAllBases()
    fun getBaseActiva(): Flow<BaseEntity?> = ventas.getBaseActiva()
    suspend fun getBaseActivaSync(): BaseEntity? = ventas.getBaseActivaSync()
    fun getBasesCerradas(): Flow<List<BaseEntity>> = ventas.getBasesCerradas()
    suspend fun crearBase(nombre: String, notas: String = ""): Long = ventas.crearBase(nombre, notas)
    suspend fun cerrarBase(baseId: Long) = ventas.cerrarBase(baseId)
    suspend fun getBaseById(id: Long): BaseEntity? = ventas.getBaseById(id)
    suspend fun eliminarBase(base: BaseEntity) = ventas.eliminarBase(base)

    // ── Ventas ────────────────────────────────────────────────────────────────

    fun getVentasByBase(baseId: Long): Flow<List<VentaEntity>> = ventas.getVentasByBase(baseId)
    fun getTotalByBase(baseId: Long): Flow<Double?> = ventas.getTotalByBase(baseId)
    fun getCountByBase(baseId: Long): Flow<Int> = ventas.getCountByBase(baseId)
    fun getVentasHoy(baseId: Long): Flow<List<VentaEntity>> = ventas.getVentasHoy(baseId)
    fun getTotalHoy(baseId: Long): Flow<Double?> = ventas.getTotalHoy(baseId)
    suspend fun agregarVenta(baseId: Long, articulo: String, monto: Double, notas: String = ""): Long =
        ventas.agregarVenta(baseId, articulo, monto, notas)
    suspend fun eliminarVenta(ventaId: Long) = ventas.eliminarVenta(ventaId)
    suspend fun editarVenta(venta: VentaEntity) = ventas.editarVenta(venta)

    // ── Drive Backup ──────────────────────────────────────────────────────────

    suspend fun ejecutarWalCheckpoint() = backup.ejecutarWalCheckpoint()
    suspend fun respaldarBase(baseId: Long): Result<String> = backup.respaldarBase(baseId)
    suspend fun listarRespaldosDrive(): Result<List<DriveBackupInfo>> = backup.listarRespaldosDrive()
    suspend fun descargarRespaldo(fileId: String): Result<BaseBackup> = backup.descargarRespaldo(fileId)
    suspend fun importarBase(backupData: BaseBackup) = backup.importarBase(backupData)

    // ── Tianguis ──────────────────────────────────────────────────────────────

    fun getSesionTianguisActiva(): Flow<TianguisSesionEntity?> = tianguis.getSesionTianguisActiva()
    fun getAllSesionesTianguis(): Flow<List<TianguisSesionEntity>> = tianguis.getAllSesionesTianguis()
    fun getVentasTianguisBySesion(sesionId: Long): Flow<List<TianguisVentaEntity>> = tianguis.getVentasTianguisBySesion(sesionId)
    fun getTotalTianguisBySesion(sesionId: Long): Flow<Double?> = tianguis.getTotalTianguisBySesion(sesionId)
    fun getCountTianguisBySesion(sesionId: Long): Flow<Int> = tianguis.getCountTianguisBySesion(sesionId)
    suspend fun iniciarSesionTianguis(nombre: String): Long = tianguis.iniciarSesionTianguis(nombre)
    suspend fun editarNombreSesion(sesion: TianguisSesionEntity, nuevoNombre: String) =
        tianguis.editarNombreSesion(sesion, nuevoNombre)
    suspend fun agregarVentaTianguis(sesionId: Long, descripcion: String, monto: Double): Long =
        tianguis.agregarVentaTianguis(sesionId, descripcion, monto)
    suspend fun editarVentaTianguis(venta: TianguisVentaEntity) = tianguis.editarVentaTianguis(venta)
    suspend fun eliminarVentaTianguis(ventaId: Long) = tianguis.eliminarVentaTianguis(ventaId)
    suspend fun cerrarSesionTianguis(sesionId: Long): TianguisSesionEntity? = tianguis.cerrarSesionTianguis(sesionId)
    suspend fun guardarSesionEnBase(sesionId: Long, baseId: Long): Long = tianguis.guardarSesionEnBase(sesionId, baseId)
    suspend fun getVentaMasAltaTianguis(sesionId: Long): Double? = tianguis.getVentaMasAltaTianguis(sesionId)
    suspend fun getVentasTianguisBySesionSync(sesionId: Long): List<TianguisVentaEntity> =
        tianguis.getVentasTianguisBySesionSync(sesionId)

    // ── Clientes ──────────────────────────────────────────────────────────────

    fun getAllClientes(): Flow<List<ClienteEntity>> = clientes.getAllClientes()
    fun getClienteById(id: Long): Flow<ClienteEntity?> = clientes.getClienteById(id)
    fun getResumenPorCliente(): Flow<List<ClienteResumen>> = clientes.getResumenPorCliente()
    suspend fun agregarCliente(nombre: String, telefono: String = "", notas: String = ""): Long =
        clientes.agregarCliente(nombre, telefono, notas)
    suspend fun editarCliente(cliente: ClienteEntity) = clientes.editarCliente(cliente)
    suspend fun eliminarCliente(cliente: ClienteEntity) = clientes.eliminarCliente(cliente)

    // ── Apartados y Abonos ────────────────────────────────────────────────────

    fun getApartadosByCliente(clienteId: Long): Flow<List<ApartadoEntity>> = clientes.getApartadosByCliente(clienteId)
    fun getApartadosConAbonosByCliente(clienteId: Long): Flow<List<ApartadoConAbonos>> =
        clientes.getApartadosConAbonosByCliente(clienteId)
    fun getAbonosByApartado(apartadoId: Long): Flow<List<AbonoEntity>> = clientes.getAbonosByApartado(apartadoId)
    fun getSaldoPendienteByCliente(clienteId: Long): Flow<Double?> = clientes.getSaldoPendienteByCliente(clienteId)
    fun getCountPendientesByCliente(clienteId: Long): Flow<Int> = clientes.getCountPendientesByCliente(clienteId)

    suspend fun agregarApartado(
        clienteId: Long,
        descripcion: String,
        monto: Double,
        anticipo: Double = 0.0,
        notas: String = "",
        tipo: TipoApartado = TipoApartado.APARTADO,
        baseId: Long? = null
    ): Long = clientes.agregarApartado(clienteId, descripcion, monto, anticipo, notas, tipo, baseId)

    suspend fun editarApartado(apartado: ApartadoEntity) = clientes.editarApartado(apartado)
    suspend fun eliminarApartado(apartadoId: Long) = clientes.eliminarApartado(apartadoId)
    suspend fun marcarApartadoEntregado(apartadoId: Long) = clientes.marcarApartadoEntregado(apartadoId)

    suspend fun registrarAbono(
        apartadoId: Long,
        monto: Double,
        nota: String = "",
        baseId: Long? = null,
        fecha: Long = System.currentTimeMillis()
    ): Long = clientes.registrarAbono(apartadoId, monto, nota, baseId, fecha)

    suspend fun eliminarAbono(abono: AbonoEntity) = clientes.eliminarAbono(abono)

    // ── Drive Backup: Clientes ────────────────────────────────────────────────

    suspend fun respaldarClientes(): Result<String> = backup.respaldarClientes()
    suspend fun descargarClientesRespaldo(): Result<ClientesBackup> = backup.descargarClientesRespaldo()
    suspend fun importarClientes(backupData: ClientesBackup) = backup.importarClientes(backupData)
}
