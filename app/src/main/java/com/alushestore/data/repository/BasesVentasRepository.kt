package com.alushestore.data.repository

import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class BasesVentasRepository(private val db: AlusheDatabase) {

    // ── Bases ─────────────────────────────────────────────────────────────────

    fun getAllBases(): Flow<List<BaseEntity>> = db.baseDao().getAllBases()

    fun getBaseActiva(): Flow<BaseEntity?> = db.baseDao().getBaseActiva()

    suspend fun getBaseActivaSync(): BaseEntity? = db.baseDao().getBaseActivaSync()

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
        db.baseDao().deleteBase(base)
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

    suspend fun agregarVenta(
        baseId: Long,
        articulo: String,
        monto: Double,
        notas: String = "",
        fecha: Long = System.currentTimeMillis()
    ): Long {
        val venta = VentaEntity(
            baseId = baseId,
            articulo = articulo,
            monto = monto,
            notas = notas,
            fecha = fecha
        )
        return db.ventaDao().insertVenta(venta)
    }

    suspend fun eliminarVenta(ventaId: Long) {
        db.ventaDao().deleteVentaById(ventaId)
    }

    suspend fun editarVenta(venta: VentaEntity) {
        db.ventaDao().updateVenta(venta)
    }

    // ── Helpers de tiempo ─────────────────────────────────────────────────────

    private fun inicioDelDia(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun finDelDia(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}
