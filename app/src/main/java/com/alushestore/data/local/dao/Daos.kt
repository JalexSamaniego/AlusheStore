package com.alushestore.data.local.dao

import androidx.room.*
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.flow.Flow

// ── BaseDao ───────────────────────────────────────────────────────────────────

@Dao
interface BaseDao {

    @Query("SELECT * FROM bases ORDER BY fechaCreacion DESC")
    fun getAllBases(): Flow<List<BaseEntity>>

    @Query("SELECT * FROM bases WHERE cerrada = 0 ORDER BY fechaCreacion DESC LIMIT 1")
    fun getBaseActiva(): Flow<BaseEntity?>

    @Query("SELECT * FROM bases WHERE cerrada = 0 ORDER BY fechaCreacion DESC LIMIT 1")
    suspend fun getBaseActivaSync(): BaseEntity?

    @Query("SELECT * FROM bases WHERE id = :id")
    suspend fun getBaseById(id: Long): BaseEntity?

    @Query("SELECT * FROM bases WHERE cerrada = 1 ORDER BY fechaCierre DESC")
    fun getBasesCerradas(): Flow<List<BaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBase(base: BaseEntity): Long

    @Update
    suspend fun updateBase(base: BaseEntity)

    @Delete
    suspend fun deleteBase(base: BaseEntity)

    @Query("UPDATE bases SET cerrada = 1, fechaCierre = :fechaCierre WHERE id = :id")
    suspend fun cerrarBase(id: Long, fechaCierre: Long = System.currentTimeMillis())

    @Query("UPDATE bases SET driveFileId = :fileId WHERE id = :id")
    suspend fun updateDriveFileId(id: Long, fileId: String)
}

// ── VentaDao ──────────────────────────────────────────────────────────────────

@Dao
interface VentaDao {

    @Query("SELECT * FROM ventas WHERE baseId = :baseId ORDER BY fecha DESC")
    fun getVentasByBase(baseId: Long): Flow<List<VentaEntity>>

    @Query("SELECT * FROM ventas WHERE baseId = :baseId ORDER BY fecha DESC")
    suspend fun getVentasByBaseSync(baseId: Long): List<VentaEntity>

    @Query("""
        SELECT * FROM ventas 
        WHERE baseId = :baseId 
        AND fecha >= :desde AND fecha <= :hasta 
        ORDER BY fecha DESC
    """)
    fun getVentasByBaseAndFecha(baseId: Long, desde: Long, hasta: Long): Flow<List<VentaEntity>>

    @Query("SELECT SUM(monto) FROM ventas WHERE baseId = :baseId")
    fun getTotalByBase(baseId: Long): Flow<Double?>

    @Query("SELECT SUM(monto) FROM ventas WHERE baseId = :baseId AND fecha >= :desde AND fecha <= :hasta")
    fun getTotalByBaseAndFecha(baseId: Long, desde: Long, hasta: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM ventas WHERE baseId = :baseId")
    fun getCountByBase(baseId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenta(venta: VentaEntity): Long

    @Update
    suspend fun updateVenta(venta: VentaEntity)

    @Delete
    suspend fun deleteVenta(venta: VentaEntity)

    @Query("DELETE FROM ventas WHERE id = :id")
    suspend fun deleteVentaById(id: Long)

    @Query("SELECT * FROM ventas WHERE baseId = :baseId AND fecha >= :hoyInicio AND fecha <= :hoyFin")
    fun getVentasHoy(baseId: Long, hoyInicio: Long, hoyFin: Long): Flow<List<VentaEntity>>
}
