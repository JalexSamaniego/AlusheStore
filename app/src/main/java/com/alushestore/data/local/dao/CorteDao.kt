package com.alushestore.data.local.dao

import androidx.room.*
import com.alushestore.data.local.entities.CorteConVendedores
import com.alushestore.data.local.entities.CorteEntity
import com.alushestore.data.local.entities.CorteVendedorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorteDao {

    @Transaction
    @Query("SELECT * FROM cortes ORDER BY fechaCierre DESC")
    fun getAllCortes(): Flow<List<CorteConVendedores>>

    @Transaction
    @Query("SELECT * FROM cortes WHERE baseId = :baseId")
    fun getCorteByBase(baseId: Long): Flow<CorteConVendedores?>

    @Transaction
    @Query("SELECT * FROM cortes WHERE baseId = :baseId")
    suspend fun getCorteByBaseSync(baseId: Long): CorteConVendedores?

    @Transaction
    @Query("SELECT * FROM cortes WHERE id = :id")
    suspend fun getCorteByIdSync(id: Long): CorteConVendedores?

    /** Cortes que todavía no llegan a la web */
    @Transaction
    @Query("SELECT * FROM cortes WHERE subidoEn IS NULL ORDER BY fechaCierre ASC")
    suspend fun getCortesPendientesDeSubir(): List<CorteConVendedores>

    @Query("SELECT EXISTS(SELECT 1 FROM cortes WHERE baseId = :baseId)")
    suspend fun existeCorteParaBase(baseId: Long): Boolean

    // ABORT y no REPLACE: un corte es inmutable, reemplazarlo borraría su desglose
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCorte(corte: CorteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVendedores(vendedores: List<CorteVendedorEntity>)

    @Query("UPDATE cortes SET subidoEn = :momento WHERE id = :corteId")
    suspend fun marcarSubido(corteId: Long, momento: Long = System.currentTimeMillis())
}
