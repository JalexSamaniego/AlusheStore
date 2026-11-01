package com.alushestore.data.repository

import com.alushestore.data.remote.CatalogoRemotoService
import com.alushestore.data.remote.ErrorDeNegocio
import com.alushestore.data.remote.SupabaseAuthManager

/** Cómo terminó un intento de mover piezas en el almacén compartido */
sealed interface ResultadoMovimiento {
    /** Llegó a Supabase y el stock de allá ya está correcto */
    data object Enviado : ResultadoMovimiento

    /** No había sesión o no había red: se manda solo cuando vuelva */
    data object Encolado : ResultadoMovimiento

    /** El servidor contestó que no. Reintentar no lo arregla. */
    data class Rechazado(val motivo: String) : ResultadoMovimiento
}

/**
 * Lo único que sabe mover stock del almacén que Jalex y Dulce comparten.
 *
 * Existe para que nadie tenga que repetir en cada pantalla las tres cosas que
 * siempre hay que hacer: pedir un token, distinguir "el servidor dijo que no"
 * de "no hay internet", y encolar lo segundo en vez de perderlo.
 *
 * La regla de siempre: **nunca se detiene la operación del negocio por falta de
 * red.** Vender o apartar se registra aquí de todos modos; lo que espera es el
 * descuento de allá.
 */
class AlmacenCompartidoRepository(
    private val cola: ColaMovimientosRepository,
    private val remoto: CatalogoRemotoService,
    private val auth: SupabaseAuthManager?
) {

    /** false mientras el teléfono no se haya emparejado una vez */
    suspend fun hayCredenciales(): Boolean = auth?.tokenValido() != null

    /**
     * Saca [cantidad] piezas del almacén compartido.
     *
     * Se usa igual para una venta que para un apartado: en los dos casos la
     * pieza deja de estar disponible para el otro teléfono, que es justo lo que
     * se quiere.
     */
    suspend fun sacar(
        productoRemotoId: Long,
        nombreProducto: String,
        cantidad: Int,
        vendedor: String,
        motivo: String
    ): ResultadoMovimiento =
        mover(productoRemotoId, nombreProducto, "salida", cantidad, vendedor, motivo)

    /**
     * Devuelve [cantidad] piezas al almacén compartido.
     *
     * Para un apartado cancelado o una venta que se deshizo. Se encola igual
     * que una salida si no hay red: una devolución perdida le quitaría piezas
     * reales al inventario compartido.
     */
    suspend fun devolver(
        productoRemotoId: Long,
        nombreProducto: String,
        cantidad: Int,
        vendedor: String,
        motivo: String
    ): ResultadoMovimiento =
        mover(productoRemotoId, nombreProducto, "entrada", cantidad, vendedor, motivo)

    private suspend fun mover(
        productoRemotoId: Long,
        nombreProducto: String,
        tipo: String,
        cantidad: Int,
        vendedor: String,
        motivo: String
    ): ResultadoMovimiento {
        if (cantidad <= 0) return ResultadoMovimiento.Enviado

        val token = auth?.tokenValido()
            ?: return encolar(productoRemotoId, nombreProducto, tipo, cantidad, vendedor, motivo)

        val r = remoto.registrarMovimiento(
            accessToken = token,
            productoId = productoRemotoId,
            tipo = tipo,
            cantidad = cantidad,
            motivo = motivo,
            vendedor = vendedor
        )

        return when {
            r.isSuccess -> ResultadoMovimiento.Enviado

            // El servidor contestó y rechazó: sin stock, sin permiso, producto
            // borrado. Encolarlo sólo repetiría el mismo "no" para siempre.
            r.exceptionOrNull() is ErrorDeNegocio ->
                ResultadoMovimiento.Rechazado(
                    r.exceptionOrNull()?.message ?: "El almacén compartido rechazó el movimiento"
                )

            else -> encolar(productoRemotoId, nombreProducto, tipo, cantidad, vendedor, motivo)
        }
    }

    private suspend fun encolar(
        productoRemotoId: Long,
        nombreProducto: String,
        tipo: String,
        cantidad: Int,
        vendedor: String,
        motivo: String
    ): ResultadoMovimiento {
        cola.encolar(
            productoRemotoId = productoRemotoId,
            nombreProducto = nombreProducto,
            tipo = tipo,
            cantidad = cantidad,
            vendedor = vendedor,
            motivo = motivo
        )
        return ResultadoMovimiento.Encolado
    }
}
