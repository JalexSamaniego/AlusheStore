package com.alushestore.data.local.dao

import androidx.room.*
import com.alushestore.data.local.entities.TianguisSesionEntity
import com.alushestore.data.local.entities.TianguisVentaEntity
import kotlinx.coroutines.flow.Flow

// ── TianguisSesionDao ─────────────────────────────────────────────────────────

@Dao
interface TianguisSesionDao {

    @Query("SELECT * FROM tianguis_sesiones WHERE activa = 1 LIMIT 1")
    fun getSesionActiva(): Flow<TianguisSesionEntity?>

    @Query("SELECT * FROM tianguis_sesiones ORDER BY fechaInicio DESC")
    fun getAllSesiones(): Flow<List<TianguisSesionEntity>>

    @Query("SELECT * FROM tianguis_sesiones WHERE id = :id")
    suspend fun getSesionById(id: Long): TianguisSesionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSesion(sesion: TianguisSesionEntity): Long

    @Update
    suspend fun updateSesion(sesion: TianguisSesionEntity)
}

// ── TianguisVentaDao ──────────────────────────────────────────────────────────

@Dao
interface TianguisVentaDao {

    @Query("SELECT * FROM tianguis_ventas WHERE sesionId = :sesionId ORDER BY fecha DESC")
    fun getVentasBySesion(sesionId: Long): Flow<List<TianguisVentaEntity>>

    @Query("SELECT * FROM tianguis_ventas WHERE sesionId = :sesionId ORDER BY fecha DESC")
    suspend fun getVentasBySesionSync(sesionId: Long): List<TianguisVentaEntity>

    @Query("SELECT SUM(monto) FROM tianguis_ventas WHERE sesionId = :sesionId")
    fun getTotalBySesion(sesionId: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM tianguis_ventas WHERE sesionId = :sesionId")
    fun getCountBySesion(sesionId: Long): Flow<Int>

    @Query("SELECT MAX(monto) FROM tianguis_ventas WHERE sesionId = :sesionId")
    suspend fun getVentaMasAltaBySesion(sesionId: Long): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenta(venta: TianguisVentaEntity): Long

    @Update
    suspend fun updateVenta(venta: TianguisVentaEntity)

    @Query("DELETE FROM tianguis_ventas WHERE id = :id")
    suspend fun deleteVentaById(id: Long)
}
