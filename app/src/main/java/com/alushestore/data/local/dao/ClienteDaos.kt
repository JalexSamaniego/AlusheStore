package com.alushestore.data.local.dao

import androidx.room.*
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ClienteResumen
import kotlinx.coroutines.flow.Flow

// ── ClienteDao ────────────────────────────────────────────────────────────────

@Dao
interface ClienteDao {

    @Query("SELECT * FROM clientes ORDER BY nombre COLLATE NOCASE ASC")
    fun getAllClientes(): Flow<List<ClienteEntity>>

    @Query("SELECT * FROM clientes ORDER BY nombre COLLATE NOCASE ASC")
    suspend fun getAllClientesSync(): List<ClienteEntity>

    @Query("SELECT * FROM clientes WHERE id = :id")
    fun getClienteById(id: Long): Flow<ClienteEntity?>

    // ABORT (no REPLACE): reemplazar un cliente borraría sus apartados por la cascada
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCliente(cliente: ClienteEntity): Long

    @Update
    suspend fun updateCliente(cliente: ClienteEntity)

    @Delete
    suspend fun deleteCliente(cliente: ClienteEntity)
}

// ── ApartadoDao ───────────────────────────────────────────────────────────────

@Dao
interface ApartadoDao {

    // Pendientes primero, luego lo más reciente
    @Query("""
        SELECT * FROM apartados WHERE clienteId = :clienteId
        ORDER BY CASE estado WHEN 'PENDIENTE' THEN 0 ELSE 1 END, fecha DESC
    """)
    fun getApartadosByCliente(clienteId: Long): Flow<List<ApartadoEntity>>

    @Query("SELECT * FROM apartados ORDER BY fecha DESC")
    suspend fun getAllApartadosSync(): List<ApartadoEntity>

    @Query("SELECT SUM(monto - anticipo) FROM apartados WHERE clienteId = :clienteId AND estado = 'PENDIENTE'")
    fun getSaldoPendienteByCliente(clienteId: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM apartados WHERE clienteId = :clienteId AND estado = 'PENDIENTE'")
    fun getCountPendientesByCliente(clienteId: Long): Flow<Int>

    @Query("""
        SELECT clienteId, COUNT(*) AS pendientes, SUM(monto - anticipo) AS saldo
        FROM apartados WHERE estado = 'PENDIENTE' GROUP BY clienteId
    """)
    fun getResumenPorCliente(): Flow<List<ClienteResumen>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApartado(apartado: ApartadoEntity): Long

    @Update
    suspend fun updateApartado(apartado: ApartadoEntity)

    @Query("DELETE FROM apartados WHERE id = :id")
    suspend fun deleteApartadoById(id: Long)

    @Query("UPDATE apartados SET estado = :estado WHERE id = :id")
    suspend fun updateEstado(id: Long, estado: String)
}
