package com.alushestore.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Base = un "periodo" de ventas (ej: "Tianguis Mayo", "Semana 1 Junio")
 */
@Entity(tableName = "bases")
data class BaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nombre: String,

    /** Fecha de creación en milisegundos */
    val fechaCreacion: Long = System.currentTimeMillis(),

    /** Fecha en que se cerró el periodo (null = abierta / activa) */
    val fechaCierre: Long? = null,

    /** true = cerrada, sus datos ya están respaldados en Drive */
    val cerrada: Boolean = false,

    /** ID del archivo en Google Drive (null = no respaldada aún) */
    val driveFileId: String? = null,

    /** Notas opcionales para la base */
    val notas: String = ""
)

/**
 * Venta individual dentro de una Base
 */
@Entity(
    tableName = "ventas",
    foreignKeys = [
        ForeignKey(
            entity = BaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["baseId"],
            onDelete = ForeignKey.CASCADE   // Si se borra la base, se borran sus ventas
        )
    ],
    indices = [Index(value = ["baseId"])]
)
data class VentaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** A qué base pertenece */
    val baseId: Long,

    /** Descripción del artículo vendido */
    val articulo: String,

    /** Monto en MXN */
    val monto: Double,

    /** Fecha y hora de la venta en milisegundos */
    val fecha: Long = System.currentTimeMillis(),

    /** Categoría (para versiones futuras) */
    val categoria: String = "General",

    /** Notas opcionales */
    val notas: String = ""
)
