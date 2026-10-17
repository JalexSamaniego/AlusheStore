package com.alushestore.data.local.entities

import androidx.room.*

@Entity(
    tableName = "abonos",
    foreignKeys = [
        ForeignKey(
            entity = ApartadoEntity::class,
            parentColumns = ["id"],
            childColumns = ["apartadoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["apartadoId"]),
        Index(value = ["baseId"])
    ]
)
data class AbonoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apartadoId: Long,
    val baseId: Long? = null,
    val monto: Double,
    val fecha: Long = System.currentTimeMillis(),
    val nota: String = ""
)

/** Relación 1 a N entre ApartadoEntity y sus Abonos registrados */
data class ApartadoConAbonos(
    @Embedded val apartado: ApartadoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "apartadoId"
    )
    val abonos: List<AbonoEntity> = emptyList()
) {
    /** Suma total de todos los abonos registrados */
    val totalAbonado: Double get() = abonos.sumOf { it.monto }

    /** Saldo que falta por liquidar */
    val saldoRestante: Double get() = (apartado.monto - totalAbonado).coerceAtLeast(0.0)

    /** Indica si el monto total ha sido cubierto en su totalidad */
    val estaLiquidado: Boolean get() = saldoRestante <= 0.001
}
