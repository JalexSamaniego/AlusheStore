package com.alushestore.data.local.dao

import androidx.room.*
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoConAbonos
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

    @Query("UPDATE apartados SET anticipo = :anticipo WHERE id = :id")
    suspend fun updateAnticipo(id: Long, anticipo: Double)

    @Transaction
    @Query("""
        SELECT * FROM apartados WHERE clienteId = :clienteId
        ORDER BY CASE estado WHEN 'PENDIENTE' THEN 0 ELSE 1 END, fecha DESC
    """)
    fun getApartadosConAbonosByCliente(clienteId: Long): Flow<List<ApartadoConAbonos>>

    @Transaction
    @Query("SELECT * FROM apartados WHERE id = :id")
    suspend fun getApartadoConAbonosById(id: Long): ApartadoConAbonos?
}

// ── AbonoDao ──────────────────────────────────────────────────────────────────

@Dao
interface AbonoDao {

    @Query("SELECT * FROM abonos WHERE apartadoId = :apartadoId ORDER BY fecha ASC")
    fun getAbonosByApartado(apartadoId: Long): Flow<List<AbonoEntity>>

    @Query("SELECT * FROM abonos WHERE apartadoId = :apartadoId ORDER BY fecha ASC")
    suspend fun getAbonosByApartadoSync(apartadoId: Long): List<AbonoEntity>

    @Query("SELECT * FROM abonos ORDER BY fecha ASC")
    suspend fun getAllAbonosSync(): List<AbonoEntity>

    @Query("SELECT SUM(monto) FROM abonos WHERE apartadoId = :apartadoId")
    fun getTotalAbonadoByApartadoFlow(apartadoId: Long): Flow<Double?>

    @Query("SELECT SUM(monto) FROM abonos WHERE apartadoId = :apartadoId")
    suspend fun getTotalAbonadoByApartado(apartadoId: Long): Double?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAbono(abono: AbonoEntity): Long

    @Update
    suspend fun updateAbono(abono: AbonoEntity)

    @Delete
    suspend fun deleteAbono(abono: AbonoEntity)

    @Query("DELETE FROM abonos WHERE id = :id")
    suspend fun deleteAbonoById(id: Long)
}
