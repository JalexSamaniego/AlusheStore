package com.alushestore.data.local.entities

import androidx.room.*

enum class EstadoApartado { PENDIENTE, ENTREGADO, CANCELADO }

/** Apartado = artículo que se aparta del stock · Pedido = artículo que se encarga */
enum class TipoApartado { APARTADO, PEDIDO }

@Entity(tableName = "clientes")
data class ClienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String = "",
    val notas: String = "",
    val fechaCreacion: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "apartados",
    foreignKeys = [ForeignKey(
        entity = ClienteEntity::class,
        parentColumns = ["id"],
        childColumns = ["clienteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["clienteId"])]
)
data class ApartadoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val descripcion: String,
    val monto: Double,
    val anticipo: Double = 0.0,
    val estado: EstadoApartado = EstadoApartado.PENDIENTE,
    val fecha: Long = System.currentTimeMillis(),
    val notas: String = "",
    val tipo: TipoApartado = TipoApartado.APARTADO
) {
    /** Lo que falta por cobrar de este apartado */
    val saldo: Double get() = (monto - anticipo).coerceAtLeast(0.0)
}

/** Resumen por cliente para la lista (apartados pendientes y lo que debe) */
data class ClienteResumen(
    val clienteId: Long,
    val pendientes: Int,
    val saldo: Double
)
