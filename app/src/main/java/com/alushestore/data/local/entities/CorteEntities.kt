package com.alushestore.data.local.entities

import androidx.room.*
import java.util.UUID

/**
 * Corte de una base: la foto inmutable de cómo cerró un periodo.
 *
 * Se genera una sola vez, al cerrar la base, y guarda los números ya calculados
 * en lugar de recalcularlos después. Así el estado de cuenta de un mes no cambia
 * aunque más adelante se ajusten costos o precios del catálogo.
 */
@Entity(
    tableName = "cortes",
    foreignKeys = [
        ForeignKey(
            entity = BaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["baseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["baseId"], unique = true),
        Index(value = ["syncUuid"], unique = true)
    ]
)
data class CorteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Base que se cerró. Único: una base tiene a lo sumo un corte. */
    val baseId: Long,

    /** Identificador global estable: evita subir el mismo corte dos veces. */
    val syncUuid: String = UUID.randomUUID().toString(),

    /** Nombre del periodo tal como se llamaba la base (ej. "OCTUBRE-NOVIEMBRE") */
    val nombrePeriodo: String,

    val fechaInicio: Long,
    val fechaCierre: Long,

    // ── Venta ────────────────────────────────────────────────────────────────
    val totalVendido: Double,
    val numVentas: Int,
    val ticketPromedio: Double,

    // ── Utilidad (misma fórmula que la pantalla de Estadísticas) ─────────────
    val costoVendido: Double,
    val utilidadNeta: Double,
    val margenSobreCosto: Int,

    /** Ventas con desglose de inventario y costo conocido */
    val ventasConCosto: Int,
    /** Ventas capturadas a mano, contadas como utilidad del 100% */
    val ventasSinCosto: Int,

    // ── Inventario en el momento del cierre ──────────────────────────────────
    val valorInventarioCierre: Double,
    val piezasInventarioCierre: Int,

    // ── Sincronización con la web ────────────────────────────────────────────
    /** null mientras no se haya subido a Supabase */
    val subidoEn: Long? = null,

    val creadoEn: Long = System.currentTimeMillis()
)

/**
 * Cuánto vendió cada persona dentro de un corte.
 */
@Entity(
    tableName = "corte_vendedores",
    foreignKeys = [
        ForeignKey(
            entity = CorteEntity::class,
            parentColumns = ["id"],
            childColumns = ["corteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["corteId"])]
)
data class CorteVendedorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val corteId: Long,
    val vendedor: String,
    val totalVendido: Double,
    val numVentas: Int
)

/** Corte junto con su desglose por persona */
data class CorteConVendedores(
    @Embedded val corte: CorteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "corteId"
    )
    val vendedores: List<CorteVendedorEntity> = emptyList()
) {
    /**
     * true si el corte se generó mucho después del cierre, es decir, para una base
     * que se cerró antes de que existieran los cortes. En esos casos no se conoce
     * el inventario de aquel momento y no se muestra.
     */
    val esRetroactivo: Boolean
        get() = corte.creadoEn - corte.fechaCierre > UN_DIA_MS

    companion object {
        private const val UN_DIA_MS = 24L * 60 * 60 * 1000
    }
}
