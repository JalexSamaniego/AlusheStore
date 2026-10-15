package com.alushestore.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tianguis_sesiones")
data class TianguisSesionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    val fechaInicio: Long = System.currentTimeMillis(),
    val fechaFin: Long? = null,
    val activa: Boolean = true,
    val guardadaEnBase: Boolean = false
)

@Entity(
    tableName = "tianguis_ventas",
    foreignKeys = [
        ForeignKey(
            entity = TianguisSesionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sesionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sesionId"])]
)
data class TianguisVentaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sesionId: Long,
    val descripcion: String,
    val monto: Double,
    val fecha: Long = System.currentTimeMillis()
)
