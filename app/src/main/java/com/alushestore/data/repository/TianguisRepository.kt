package com.alushestore.data.repository

import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.TianguisSesionEntity
import com.alushestore.data.local.entities.TianguisVentaEntity
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.flow.Flow

class TianguisRepository(private val db: AlusheDatabase) {

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
}
