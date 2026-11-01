package com.alushestore.data.repository

import androidx.room.withTransaction
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.dao.LoteConProductos
import com.alushestore.data.local.dao.ProductoConLote
import com.alushestore.data.local.dao.VentaConDetalles
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.EstadoApartado
import com.alushestore.data.local.entities.LoteEntity
import com.alushestore.data.local.entities.ProductoEntity
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.domain.ProductoUnificado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Representa una línea en el carrito de compras del Punto de Venta (POS).
 */
data class CartItem(
    val producto: ProductoUnificado,
    val cantidad: Int = 1,
    val precioUnitario: Double = producto.precio,
    val descuentoUnitario: Double = 0.0
) {
    val precioFinal: Double get() = (precioUnitario - descuentoUnitario).coerceAtLeast(0.0)
    val subtotal: Double get() = precioFinal * cantidad
    val costoTotal: Double get() = producto.costo * cantidad
    val gananciaNeta: Double get() = (precioFinal - producto.costo) * cantidad

    /** Las piezas del almacén compartido se descuentan por el RPC, no en Room */
    val esDelAlmacenCompartido: Boolean get() = producto.idLocal == null && producto.idRemoto != null
}

/**
 * Resumen financiero y de retorno de inversión (ROI) para un Lote.
 */
data class LoteRentabilidad(
    val lote: LoteEntity,
    val totalPiezas: Int,
    val piezasDisponibles: Int,
    val piezasApartadas: Int,
    val totalRecuperado: Double,
    val inversionTotal: Double,
    val balanceNeto: Double,
    val porcentajeRecuperado: Float,
    val estaRecuperado: Boolean
)

class InventarioRepository(
    private val db: AlusheDatabase
) {
    private val dao = db.inventarioDao()

    // ── Lotes ─────────────────────────────────────────────────────────────────

    fun getAllLotes(): Flow<List<LoteEntity>> = dao.getAllLotes()

    fun getLotesActivos(): Flow<List<LoteEntity>> = dao.getLotesActivos()

    fun getLoteById(id: Long): Flow<LoteEntity?> = dao.getLoteById(id)

    fun getLoteConProductos(id: Long): Flow<LoteConProductos?> = dao.getLoteConProductos(id)

    suspend fun agregarLote(
        nombre: String,
        inversionMercancia: Double,
        gastosExtraEnvio: Double = 0.0,
        fechaCompra: Long = System.currentTimeMillis(),
        notas: String? = null
    ): Long = withContext(Dispatchers.IO) {
        dao.insertLote(
            LoteEntity(
                nombre = nombre.trim(),
                inversionMercancia = inversionMercancia.coerceAtLeast(0.0),
                gastosExtraEnvio = gastosExtraEnvio.coerceAtLeast(0.0),
                fechaCompra = fechaCompra,
                notas = notas?.trim()?.ifBlank { null }
            )
        )
    }

    suspend fun editarLote(lote: LoteEntity) = withContext(Dispatchers.IO) {
        dao.updateLote(lote)
    }

    suspend fun eliminarLote(lote: LoteEntity) = withContext(Dispatchers.IO) {
        try {
            dao.deleteLote(lote)
        } catch (e: Exception) {
            dao.updateLote(lote.copy(activo = false))
        }
    }

    // ── Productos ─────────────────────────────────────────────────────────────

    fun getAllProductos(): Flow<List<ProductoEntity>> = dao.getAllProductos()

    fun getProductosActivos(): Flow<List<ProductoEntity>> = dao.getProductosActivos()

    fun getProductoById(id: Long): Flow<ProductoEntity?> = dao.getProductoById(id)

    fun getProductoConLote(id: Long): Flow<ProductoConLote?> = dao.getProductoConLote(id)

    fun getProductoBySku(sku: String): Flow<ProductoEntity?> = dao.getProductoBySku(sku.trim())

    suspend fun getProductoByIdSync(id: Long): ProductoEntity? =
        withContext(Dispatchers.IO) { dao.getProductoByIdSync(id) }

    fun getProductosByLote(loteId: Long): Flow<List<ProductoEntity>> = dao.getProductosByLote(loteId)

    fun buscarProductos(query: String): Flow<List<ProductoEntity>> = dao.buscarProductos(query.trim())

    suspend fun agregarProducto(
        sku: String,
        nombre: String,
        categoria: String?,
        loteId: Long?,
        costoUnitario: Double,
        precioVenta: Double,
        stockInicial: Int,
        ubicacion: String = "General"
    ): Long = withContext(Dispatchers.IO) {
        val skuFinal = if (sku.isBlank()) generarSiguienteSku() else sku.trim().uppercase()
        dao.insertProducto(
            ProductoEntity(
                sku = skuFinal,
                nombre = nombre.trim(),
                categoria = categoria?.trim()?.ifBlank { null },
                loteId = loteId?.takeIf { it > 0 },
                costoUnitario = costoUnitario.coerceAtLeast(0.0),
                precioVenta = precioVenta.coerceAtLeast(0.0),
                stockDisponible = stockInicial.coerceAtLeast(0),
                ubicacion = ubicacion.trim().ifBlank { "General" }
            )
        )
    }

    suspend fun editarProducto(producto: ProductoEntity) = withContext(Dispatchers.IO) {
        dao.updateProducto(producto.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun eliminarProducto(producto: ProductoEntity) = withContext(Dispatchers.IO) {
        try {
            dao.deleteProducto(producto)
        } catch (e: Exception) {
            dao.updateProducto(producto.copy(activo = false, updatedAt = System.currentTimeMillis()))
        }
    }

    // ── Auxiliares Financieros y SKU ──────────────────────────────────────────

    /**
     * Autogenera el siguiente SKU secuencial único con prefijo 'ALU-1001', 'ALU-1002', etc.
     */
    suspend fun generarSiguienteSku(): String = withContext(Dispatchers.IO) {
        val skus = dao.getAllAlusheSkus()
        var maxNum = 1000
        for (s in skus) {
            val numStr = s.removePrefix("ALU-").trim()
            val num = numStr.toIntOrNull()
            if (num != null && num > maxNum) {
                maxNum = num
            }
        }
        "ALU-${maxNum + 1}"
    }

    /**
     * Sugiere cuánto sumar al costo unitario para prorratear el envío equitativamente entre las piezas.
     */
    fun calcularProrrateoEnvio(gastosExtraEnvio: Double, totalPiezas: Int): Double {
        if (totalPiezas <= 0 || gastosExtraEnvio <= 0.0) return 0.0
        return gastosExtraEnvio / totalPiezas
    }

    // ── Transacción Atómica de Punto de Venta (POS) ───────────────────────────

    /**
     * Cobra una venta de carrito en la Base activa:
     * 1. Inserta la [VentaEntity] principal para que odómetros y reportes sumen al instante.
     * 2. Inserta los [DetalleVentaEntity] congelando el costo unitario histórico de cada artículo.
     * 3. Descuenta atómicamente el stock disponible de cada producto.
     */
    suspend fun cobrarVentaPos(
        baseId: Long,
        vendedor: String,
        items: List<CartItem>,
        notas: String = ""
    ): Long = withContext(Dispatchers.IO) {
        if (items.isEmpty()) throw IllegalArgumentException("El carrito no contiene productos")

        val totalMonto = items.sumOf { it.subtotal }
        val totalPiezas = items.sumOf { it.cantidad }
        val resumen = if (items.size == 1) {
            val item = items.first()
            "${item.producto.nombre} x${item.cantidad}"
        } else {
            val lista = items.take(2).joinToString(", ") { "${it.producto.nombre} x${it.cantidad}" }
            if (items.size > 2) "$totalPiezas arts (POS): $lista (+${items.size - 2} más)" else "$totalPiezas arts (POS): $lista"
        }

        db.withTransaction {
            // 1. Venta principal
            val ventaId = db.ventaDao().insertVenta(
                VentaEntity(
                    baseId = baseId,
                    articulo = resumen,
                    monto = totalMonto,
                    categoria = "POS",
                    notas = notas.trim(),
                    vendedor = vendedor,
                    esVentaPos = true
                )
            )

            // 2. Líneas de detalle
            val detalles = items.map { item ->
                DetalleVentaEntity(
                    ventaId = ventaId,
                    // null cuando la pieza vive sólo en el almacén compartido
                    productoId = item.producto.idLocal,
                    nombreProductoSnapshot = item.producto.nombre,
                    cantidad = item.cantidad,
                    costoUnitarioHistorico = item.producto.costo,
                    precioUnitarioFinal = item.precioFinal
                )
            }
            dao.insertDetallesVenta(detalles)

            // 3. Descontar stock local. Lo del almacén compartido ya se descontó
            //    antes por el RPC, que es quien lleva esa cuenta.
            val now = System.currentTimeMillis()
            for (item in items) {
                item.producto.idLocal?.let { dao.descontarStock(it, item.cantidad, now) }
            }

            ventaId
        }
    }

    // ── Transacciones de Apartado e Inventario ────────────────────────────────

    /**
     * Vincula un producto del catálogo a un nuevo apartado, reservando 1 unidad de stock.
     */
    suspend fun crearApartadoConInventario(
        clienteId: Long,
        productoId: Long,
        anticipo: Double,
        vendedor: String = "PRINCIPAL",
        notas: String = ""
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val producto = dao.getProductoByIdSync(productoId)
                ?: throw IllegalArgumentException("Producto no encontrado en inventario")

            val apartadoId = db.apartadoDao().insertApartado(
                ApartadoEntity(
                    clienteId = clienteId,
                    productoId = productoId,
                    descripcion = producto.nombre,
                    monto = producto.precioVenta,
                    anticipo = anticipo,
                    estado = EstadoApartado.PENDIENTE,
                    vendedor = vendedor,
                    notas = notas.trim(),
                    tipo = TipoApartado.APARTADO
                )
            )

            if (anticipo > 0) {
                db.abonoDao().insertAbono(
                    AbonoEntity(
                        apartadoId = apartadoId,
                        monto = anticipo,
                        nota = "Anticipo inicial"
                    )
                )
            }

            // Mover 1 unidad de disponible a apartado
            dao.moverStockAApartado(productoId, 1)

            apartadoId
        }
    }

    /**
     * Actualiza el estado de un apartado gestionando la reserva/entrega de stock automáticamente.
     *
     * **No usar.** Quedó sin llamar cuando la pantalla de clientes pasó a
     * `editarApartado`, y por eso durante un tiempo cancelar un apartado no
     * devolvía la pieza al stock. El camino bueno, que además sabe de piezas
     * del almacén compartido, es [ClientesRepository.cambiarEstadoApartado].
     */
    @Deprecated(
        "Usa ClientesRepository.cambiarEstadoApartado: éste ignora el almacén compartido",
        ReplaceWith("")
    )
    suspend fun cambiarEstadoApartado(apartadoId: Long, nuevoEstado: EstadoApartado) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val apartado = db.apartadoDao().getApartadoByIdSync(apartadoId) ?: return@withTransaction
            val estadoAnterior = apartado.estado
            if (estadoAnterior == nuevoEstado) return@withTransaction

            val productoId = apartado.productoId
            if (productoId != null) {
                when {
                    // Pasa a ENTREGADO: se entrega la pieza reservada definitivamente
                    nuevoEstado == EstadoApartado.ENTREGADO && estadoAnterior == EstadoApartado.PENDIENTE -> {
                        dao.entregarStockApartado(productoId, 1)
                    }
                    // Pasa a CANCELADO: se libera y regresa al stock disponible
                    nuevoEstado == EstadoApartado.CANCELADO && estadoAnterior == EstadoApartado.PENDIENTE -> {
                        dao.liberarStockApartado(productoId, 1)
                    }
                    // Reactivación (de CANCELADO a PENDIENTE): se vuelve a reservar
                    nuevoEstado == EstadoApartado.PENDIENTE && estadoAnterior == EstadoApartado.CANCELADO -> {
                        dao.moverStockAApartado(productoId, 1)
                    }
                }
            }

            db.apartadoDao().updateApartado(apartado.copy(estado = nuevoEstado))
        }
    }

    // ── Reportes y Rentabilidad ───────────────────────────────────────────────

    /**
     * Calcula la suma del costo de toda la mercancía disponible en stock físico.
     */
    fun getValorTotalInventario(): Flow<Double> = dao.getValorTotalInventario().map { it ?: 0.0 }

    /**
     * Consulta reactiva de retorno de inversión (ROI) para un lote específico.
     */
    fun getRentabilidadLote(loteId: Long): Flow<LoteRentabilidad?> {
        return combine(
            dao.getLoteById(loteId),
            dao.getProductosByLote(loteId),
            dao.getTotalRecuperadoPorLote(loteId)
        ) { lote, productos, totalRecuperado ->
            if (lote == null) return@combine null

            val totalPiezas = productos.sumOf { it.stockTotal }
            val disponibles = productos.sumOf { it.stockDisponible }
            val apartadas = productos.sumOf { it.stockApartado }
            val recuperado = totalRecuperado ?: 0.0
            val inversion = lote.inversionTotal
            val balance = recuperado - inversion
            val pct = if (inversion > 0.0) ((recuperado / inversion) * 100).toFloat() else 0f

            LoteRentabilidad(
                lote = lote,
                totalPiezas = totalPiezas,
                piezasDisponibles = disponibles,
                piezasApartadas = apartadas,
                totalRecuperado = recuperado,
                inversionTotal = inversion,
                balanceNeto = balance,
                porcentajeRecuperado = pct,
                estaRecuperado = balance >= 0.0
            )
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Consulta reactiva de retorno de inversión (ROI) para todos los lotes de inversión.
     */
    fun getAllLotesRentabilidad(): Flow<List<LoteRentabilidad>> {
        return combine(
            dao.getLotesActivos(),
            dao.getProductosActivos(),
            dao.getAllDetallesVenta()
        ) { lotes, productos, detalles ->
            val lotesActivos = lotes.filter { it.activo }
            val prodsActivos = productos.filter { it.activo }
            val productosPorLote = prodsActivos.filter { it.loteId != null }.groupBy { it.loteId!! }
            val ventasPorProducto = detalles.filter { it.productoId != null }.groupBy { it.productoId!! }

            lotesActivos.map { lote ->
                val prods = productosPorLote[lote.id].orEmpty()
                val totalPiezas = prods.sumOf { it.stockTotal }
                val disponibles = prods.sumOf { it.stockDisponible }
                val apartadas = prods.sumOf { it.stockApartado }

                val recuperado = prods.sumOf { prod ->
                    val ventasProd = ventasPorProducto[prod.id].orEmpty()
                    ventasProd.sumOf { it.precioUnitarioFinal * it.cantidad }
                }

                val inversion = lote.inversionTotal
                val balance = recuperado - inversion
                val pct = if (inversion > 0.0) ((recuperado / inversion) * 100).toFloat() else 0f

                LoteRentabilidad(
                    lote = lote,
                    totalPiezas = totalPiezas,
                    piezasDisponibles = disponibles,
                    piezasApartadas = apartadas,
                    totalRecuperado = recuperado,
                    inversionTotal = inversion,
                    balanceNeto = balance,
                    porcentajeRecuperado = pct,
                    estaRecuperado = balance >= 0.0
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    fun getAllDetallesVenta(): Flow<List<DetalleVentaEntity>> = dao.getAllDetallesVenta()

    fun getVentaConDetalles(ventaId: Long): Flow<VentaConDetalles?> = dao.getVentaConDetalles(ventaId)
}
