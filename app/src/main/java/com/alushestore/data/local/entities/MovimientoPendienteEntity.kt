package com.alushestore.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Movimiento de stock del almacén compartido que todavía no llegó a Supabase.
 *
 * Existe para que el tianguis sin señal no detenga una venta: la venta se cobra
 * y se registra siempre en el teléfono, y el descuento de stock espera aquí
 * hasta que vuelva el internet.
 *
 * No se encolan los ajustes de administración: cambiar stock o precio a ciegas
 * sin ver el estado real del almacén es pedir un descuadre.
 */
@Entity(
    tableName = "movimientos_pendientes",
    indices = [Index(value = ["productoRemotoId"])]
)
data class MovimientoPendienteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** id del producto en la tabla `productos` de Supabase */
    val productoRemotoId: Long,

    /** Copia del nombre para poder mostrar la cola sin pedir el catálogo */
    val nombreProducto: String,

    /** "salida" para una venta, "entrada" para un reverso */
    val tipo: String,

    val cantidad: Int,
    val motivo: String,
    val vendedor: String,

    val creadoEn: Long = System.currentTimeMillis(),

    /** Cuántas veces se intentó mandar */
    val intentos: Int = 0,

    val ultimoError: String? = null,

    /**
     * true cuando el servidor lo rechazó por una razón que no se arregla
     * reintentando (ya no hay stock, la cuenta no tiene permiso). Necesita que
     * una persona decida qué hacer.
     */
    val enConflicto: Boolean = false
)
