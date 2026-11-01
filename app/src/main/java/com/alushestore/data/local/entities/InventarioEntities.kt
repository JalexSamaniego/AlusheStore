package com.alushestore.data.local.entities

import androidx.room.*
import java.util.UUID

/**
 * Lote de inversión / compra de mercancía.
 * Permite agrupar productos para medir la inversión total y el retorno de inversión (ROI).
 */
@Entity(
    tableName = "lotes",
    indices = [
        Index(value = ["syncUuid"])
    ]
)
data class LoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Identificador global único para sincronización en tiempo real */
    val syncUuid: String = UUID.randomUUID().toString(),

    /** Nombre identificador (ej: "Pedido Noviembre #1", "Bolsas Guadalajara") */
    val nombre: String,

    /** Fecha de la compra o recepción en milisegundos */
    val fechaCompra: Long = System.currentTimeMillis(),

    /** Total invertido en los artículos del lote */
    val inversionMercancia: Double,

    /** Costo de flete, paquetería o traslados para prorrateo entre las piezas */
    val gastosExtraEnvio: Double = 0.0,

    /** Notas opcionales del proveedor, paquetería o seguimiento */
    val notas: String? = null,

    /** true si el lote sigue activo en tienda */
    val activo: Boolean = true
) {
    /** Inversión real total considerando mercancía y gastos de envío */
    val inversionTotal: Double get() = inversionMercancia + gastosExtraEnvio
}

/**
 * Producto individual en inventario (catálogo híbrido físico / web).
 */
@Entity(
    tableName = "productos",
    foreignKeys = [
        ForeignKey(
            entity = LoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["syncUuid"]),
        Index(value = ["loteId"])
    ]
)
data class ProductoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** UUID para sincronización distribuida y resolución de conflictos */
    val syncUuid: String = UUID.randomUUID().toString(),

    /** Código de barras, código web o autogenerado tipo 'ALU-1001' */
    val sku: String,

    /** Nombre comercial del producto */
    val nombre: String,

    /** Categoría opcional (ej: "Bolsas", "Ropa", "Accesorios") */
    val categoria: String? = null,

    /** Lote al que pertenece (null = compra suelta / inventario inicial) */
    val loteId: Long? = null,

    /** Costo unitario de compra */
    val costoUnitario: Double,

    /** Precio de venta sugerido al público */
    val precioVenta: Double,

    /** Piezas listas para venta inmediata */
    val stockDisponible: Int = 0,

    /** Piezas comprometidas / reservadas en apartados activos */
    val stockApartado: Int = 0,

    /** Ubicación física (ej: "General", "Tianguis", "Bodega", "Mostrador") */
    val ubicacion: String = "General",

    /** false si el producto se descontinúa o elimina */
    val activo: Boolean = true,

    /** Timestamp de última modificación para sincronización */
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Margen de ganancia bruto unitario en dinero */
    val margenGanancia: Double get() = precioVenta - costoUnitario

    /** Porcentaje de utilidad sobre el costo (ej: 0.50 = 50%) */
    val porcentajeMargen: Double
        get() = if (costoUnitario > 0) (precioVenta - costoUnitario) / costoUnitario else 0.0

    /** Stock total físico en posesión de la tienda */
    val stockTotal: Int get() = stockDisponible + stockApartado
}

/**
 * Desglose de cada producto vendido en una transacción POS.
 * Congela el costo unitario histórico para calcular con exactitud la ganancia neta.
 */
@Entity(
    tableName = "detalles_venta",
    foreignKeys = [
        ForeignKey(
            entity = VentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["ventaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["ventaId"]),
        Index(value = ["productoId"])
    ]
)
data class DetalleVentaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Venta a la que pertenece */
    val ventaId: Long,

    /** Producto original (puede ser null si el producto se borra del catálogo) */
    val productoId: Long? = null,

    /** Snapshot inmutable del nombre al momento de la venta */
    val nombreProductoSnapshot: String,

    /** Número de unidades vendidas */
    val cantidad: Int = 1,

    /** Costo unitario al que se compró originalmente el producto */
    val costoUnitarioHistorico: Double,

    /** Precio unitario al que se cobró realmente en esta venta */
    val precioUnitarioFinal: Double
) {
    /** Subtotal cobrado por esta línea */
    val subtotalVenta: Double get() = precioUnitarioFinal * cantidad

    /** Costo total de las piezas de esta línea */
    val costoTotal: Double get() = costoUnitarioHistorico * cantidad

    /** Utilidad bruta libre generada por esta línea */
    val gananciaNeta: Double get() = subtotalVenta - costoTotal
}
