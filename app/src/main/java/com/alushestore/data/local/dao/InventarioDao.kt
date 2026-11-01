package com.alushestore.data.local.dao

import androidx.room.*
import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.LoteEntity
import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.flow.Flow

data class ProductoConLote(
    @Embedded val producto: ProductoEntity,
    @Relation(
        parentColumn = "loteId",
        entityColumn = "id"
    )
    val lote: LoteEntity?
)

data class VentaConDetalles(
    @Embedded val venta: VentaEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "ventaId"
    )
    val detalles: List<DetalleVentaEntity>
)

data class LoteConProductos(
    @Embedded val lote: LoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "loteId"
    )
    val productos: List<ProductoEntity>
)

@Dao
interface InventarioDao {

    // ── Lotes ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM lotes WHERE activo = 1 ORDER BY fechaCompra DESC")
    fun getAllLotes(): Flow<List<LoteEntity>>

    @Query("SELECT * FROM lotes WHERE activo = 1 ORDER BY fechaCompra DESC")
    fun getLotesActivos(): Flow<List<LoteEntity>>

    @Query("SELECT * FROM lotes WHERE id = :id")
    fun getLoteById(id: Long): Flow<LoteEntity?>

    @Query("SELECT * FROM lotes WHERE id = :id")
    suspend fun getLoteByIdSync(id: Long): LoteEntity?

    @Query("SELECT * FROM lotes WHERE activo = 1 ORDER BY fechaCompra DESC")
    suspend fun getLotesActivosSync(): List<LoteEntity>

    @Query("SELECT * FROM lotes WHERE activo = 1 ORDER BY fechaCompra DESC")
    suspend fun getAllLotesSync(): List<LoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLote(lote: LoteEntity): Long

    @Update
    suspend fun updateLote(lote: LoteEntity)

    @Delete
    suspend fun deleteLote(lote: LoteEntity)

    // ── Productos ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre ASC")
    fun getAllProductos(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre ASC")
    fun getProductosActivos(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE id = :id")
    fun getProductoById(id: Long): Flow<ProductoEntity?>

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun getProductoByIdSync(id: Long): ProductoEntity?

    @Query("SELECT * FROM productos WHERE sku = :sku AND activo = 1 LIMIT 1")
    fun getProductoBySku(sku: String): Flow<ProductoEntity?>

    @Query("SELECT * FROM productos WHERE sku = :sku AND activo = 1 LIMIT 1")
    suspend fun getProductoBySkuSync(sku: String): ProductoEntity?

    @Query("SELECT * FROM productos WHERE loteId = :loteId AND activo = 1 ORDER BY nombre ASC")
    fun getProductosByLote(loteId: Long): Flow<List<ProductoEntity>>

    @Query("""
        SELECT * FROM productos 
        WHERE activo = 1 AND (nombre LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR categoria LIKE '%' || :query || '%')
        ORDER BY nombre ASC
    """)
    fun buscarProductos(query: String): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE activo = 1")
    suspend fun getAllProductosSync(): List<ProductoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducto(producto: ProductoEntity): Long

    @Update
    suspend fun updateProducto(producto: ProductoEntity)

    @Delete
    suspend fun deleteProducto(producto: ProductoEntity)

    /**
     * Obtiene todos los SKUs para autogenerar el siguiente SKU secuencial
     */
    @Query("SELECT sku FROM productos WHERE sku LIKE 'ALU-%'")
    suspend fun getAllAlusheSkus(): List<String>

    @Query("UPDATE productos SET stockDisponible = MAX(0, stockDisponible - :cantidad), updatedAt = :now WHERE id = :productoId")
    suspend fun descontarStock(productoId: Long, cantidad: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE productos SET stockDisponible = stockDisponible + :cantidad, updatedAt = :now WHERE id = :productoId")
    suspend fun aumentarStock(productoId: Long, cantidad: Int, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE productos 
        SET stockDisponible = MAX(0, stockDisponible - :cantidad),
            stockApartado = stockApartado + :cantidad,
            updatedAt = :now 
        WHERE id = :productoId
    """)
    suspend fun moverStockAApartado(productoId: Long, cantidad: Int = 1, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE productos 
        SET stockDisponible = stockDisponible + :cantidad,
            stockApartado = MAX(0, stockApartado - :cantidad),
            updatedAt = :now 
        WHERE id = :productoId
    """)
    suspend fun liberarStockApartado(productoId: Long, cantidad: Int = 1, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE productos 
        SET stockApartado = MAX(0, stockApartado - :cantidad),
            updatedAt = :now 
        WHERE id = :productoId
    """)
    suspend fun entregarStockApartado(productoId: Long, cantidad: Int = 1, now: Long = System.currentTimeMillis())

    // ── Detalles de Venta POS ─────────────────────────────────────────────────

    @Query("SELECT * FROM detalles_venta WHERE ventaId = :ventaId")
    fun getDetallesByVenta(ventaId: Long): Flow<List<DetalleVentaEntity>>

    @Query("SELECT * FROM detalles_venta WHERE ventaId = :ventaId")
    suspend fun getDetallesByVentaSync(ventaId: Long): List<DetalleVentaEntity>

    @Query("SELECT * FROM detalles_venta WHERE productoId = :productoId")
    fun getDetallesByProducto(productoId: Long): Flow<List<DetalleVentaEntity>>

    @Query("SELECT * FROM detalles_venta")
    fun getAllDetallesVenta(): Flow<List<DetalleVentaEntity>>

    @Query("SELECT * FROM detalles_venta")
    suspend fun getAllDetallesVentaSync(): List<DetalleVentaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetalleVenta(detalle: DetalleVentaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetallesVenta(detalles: List<DetalleVentaEntity>)

    // ── Métricas y Finanzas ───────────────────────────────────────────────────

    @Query("SELECT SUM(costoUnitario * stockDisponible) FROM productos WHERE activo = 1")
    fun getValorTotalInventario(): Flow<Double?>

    @Query("""
        SELECT COALESCE(SUM(dv.precioUnitarioFinal * dv.cantidad), 0.0)
        FROM detalles_venta dv
        INNER JOIN productos p ON dv.productoId = p.id
        WHERE p.loteId = :loteId
    """)
    fun getTotalRecuperadoPorLote(loteId: Long): Flow<Double?>

    @Transaction
    @Query("SELECT * FROM productos WHERE id = :id")
    fun getProductoConLote(id: Long): Flow<ProductoConLote?>

    @Transaction
    @Query("SELECT * FROM lotes WHERE id = :id")
    fun getLoteConProductos(id: Long): Flow<LoteConProductos?>

    @Transaction
    @Query("SELECT * FROM ventas WHERE id = :id")
    fun getVentaConDetalles(id: Long): Flow<VentaConDetalles?>
}
