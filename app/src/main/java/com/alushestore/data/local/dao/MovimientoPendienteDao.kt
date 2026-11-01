package com.alushestore.data.local.dao

import androidx.room.*
import com.alushestore.data.local.entities.MovimientoPendienteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoPendienteDao {

    @Query("SELECT * FROM movimientos_pendientes ORDER BY creadoEn ASC")
    fun getTodos(): Flow<List<MovimientoPendienteEntity>>

    /** Los que todavía vale la pena reintentar */
    @Query("SELECT * FROM movimientos_pendientes WHERE enConflicto = 0 ORDER BY creadoEn ASC")
    suspend fun getReintentables(): List<MovimientoPendienteEntity>

    @Query("SELECT * FROM movimientos_pendientes WHERE enConflicto = 1 ORDER BY creadoEn ASC")
    fun getConflictos(): Flow<List<MovimientoPendienteEntity>>

    /** Los que todavía se van a reintentar solos */
    @Query("SELECT COUNT(*) FROM movimientos_pendientes WHERE enConflicto = 0")
    fun contarReintentables(): Flow<Int>

    /** Los que el servidor rechazó y necesitan que alguien decida */
    @Query("SELECT COUNT(*) FROM movimientos_pendientes WHERE enConflicto = 1")
    fun contarConflictos(): Flow<Int>

    @Query("DELETE FROM movimientos_pendientes WHERE enConflicto = 1")
    suspend fun descartarConflictos()

    @Query("DELETE FROM movimientos_pendientes WHERE enConflicto = 0")
    suspend fun descartarPendientes()

    /**
     * Piezas comprometidas por producto que aún no se descuentan en el servidor.
     * Sirve para restarlas del stock que se muestra y no vender de más.
     */
    @Query("""
        SELECT productoRemotoId, SUM(CASE WHEN tipo = 'salida' THEN cantidad ELSE -cantidad END) AS piezas
        FROM movimientos_pendientes
        WHERE enConflicto = 0
        GROUP BY productoRemotoId
    """)
    fun getComprometidoPorProducto(): Flow<List<PiezasComprometidas>>

    @Insert
    suspend fun insertar(movimiento: MovimientoPendienteEntity): Long

    @Insert
    suspend fun insertarTodos(movimientos: List<MovimientoPendienteEntity>)

    @Query("DELETE FROM movimientos_pendientes WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Query("UPDATE movimientos_pendientes SET intentos = intentos + 1, ultimoError = :error WHERE id = :id")
    suspend fun marcarIntentoFallido(id: Long, error: String?)

    @Query("UPDATE movimientos_pendientes SET enConflicto = 1, ultimoError = :error, intentos = intentos + 1 WHERE id = :id")
    suspend fun marcarConflicto(id: Long, error: String?)
}

data class PiezasComprometidas(
    val productoRemotoId: Long,
    val piezas: Int
)
