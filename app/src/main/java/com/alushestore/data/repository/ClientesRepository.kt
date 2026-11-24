package com.alushestore.data.repository

import androidx.room.withTransaction
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoConAbonos
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ClienteResumen
import com.alushestore.data.local.entities.DetalleVentaEntity
import com.alushestore.data.local.entities.EstadoApartado
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.data.local.entities.VentaEntity
import com.alushestore.domain.CalculadoraMetricas
import kotlinx.coroutines.flow.Flow

class ClientesRepository(
    private val db: AlusheDatabase,
    private val backupRepository: BackupRepository? = null,
    private val basesVentasRepository: BasesVentasRepository? = null,
    private val almacen: AlmacenCompartidoRepository? = null
) {

    // ── Clientes ──────────────────────────────────────────────────────────────

    fun getAllClientes(): Flow<List<ClienteEntity>> = db.clienteDao().getAllClientes()

    fun getClienteById(id: Long): Flow<ClienteEntity?> = db.clienteDao().getClienteById(id)

    fun getResumenPorCliente(): Flow<List<ClienteResumen>> = db.apartadoDao().getResumenPorCliente()

    suspend fun agregarCliente(nombre: String, telefono: String = "", notas: String = ""): Long {
        val id = db.clienteDao().insertCliente(
            ClienteEntity(nombre = nombre, telefono = telefono, notas = notas)
        )
        backupRepository?.programarRespaldoClientes()
        return id
    }

    suspend fun editarCliente(cliente: ClienteEntity) {
        db.clienteDao().updateCliente(cliente)
        backupRepository?.programarRespaldoClientes()
    }

    /**
     * Borra al cliente con todos sus apartados (cascada de Room).
     *
     * Antes, las piezas que esos apartados tenían reservadas se quedaban
     * apartadas para siempre. Ahora se devuelven primero, igual que al
     * cancelar.
     */
    suspend fun eliminarCliente(cliente: ClienteEntity) {
        db.apartadoDao().getApartadosByClienteSync(cliente.id)
            .forEach { liberarPiezaSiPendiente(it, "Cliente eliminado") }
        db.clienteDao().deleteCliente(cliente)
        backupRepository?.programarRespaldoClientes()
    }

    // ── Apartados y Abonos ────────────────────────────────────────────────────

    fun getApartadosByCliente(clienteId: Long): Flow<List<ApartadoEntity>> =
        db.apartadoDao().getApartadosByCliente(clienteId)

    fun getApartadosConAbonosByCliente(clienteId: Long): Flow<List<ApartadoConAbonos>> =
        db.apartadoDao().getApartadosConAbonosByCliente(clienteId)

    fun getAbonosByApartado(apartadoId: Long): Flow<List<AbonoEntity>> =
        db.abonoDao().getAbonosByApartado(apartadoId)

    fun getSaldoPendienteByCliente(clienteId: Long): Flow<Double?> =
        db.apartadoDao().getSaldoPendienteByCliente(clienteId)

    fun getCountPendientesByCliente(clienteId: Long): Flow<Int> =
        db.apartadoDao().getCountPendientesByCliente(clienteId)

    suspend fun agregarApartado(
        clienteId: Long,
        descripcion: String,
        monto: Double,
        anticipo: Double = 0.0,
        notas: String = "",
        tipo: TipoApartado = TipoApartado.APARTADO,
        baseId: Long? = null,
        vendedor: String = "PRINCIPAL"
    ): Long {
        val fecha = System.currentTimeMillis()
        val apartado = ApartadoEntity(
            clienteId = clienteId,
            descripcion = descripcion,
            monto = monto,
            anticipo = anticipo,
            notas = notas,
            tipo = tipo,
            fecha = fecha,
            vendedor = vendedor
        )

        // Todo o nada: un apartado sin su anticipo, o un anticipo sin su venta
        // en la base, descuadra las cuentas.
        val id = db.withTransaction {
            val nuevoId = db.apartadoDao().insertApartado(apartado)

            if (anticipo > 0) {
                db.abonoDao().insertAbono(
                    AbonoEntity(
                        apartadoId = nuevoId,
                        baseId = baseId,
                        monto = anticipo,
                        fecha = fecha,
                        nota = "Anticipo inicial"
                    )
                )

                if (baseId != null) {
                    registrarIngresoEnBase(
                        apartado = apartado.copy(id = nuevoId),
                        baseId = baseId,
                        monto = anticipo,
                        saldoAntes = monto,
                        fecha = fecha,
                        articulo = "Anticipo: $descripcion"
                    )
                }
            }
            nuevoId
        }

        backupRepository?.programarRespaldoClientes()
        return id
    }

    /** Qué quedó de apartar una pieza que vive en alushe.store */
    data class ApartadoCompartido(val apartadoId: Long, val almacen: ResultadoMovimiento)

    /**
     * Aparta una pieza del almacén compartido para un cliente.
     *
     * La pieza **sale del stock compartido en el momento**, no cuando se
     * entregue. Es la única forma de que el otro teléfono no la venda: desde su
     * punto de vista, esa pieza ya no está.
     *
     * Si el almacén rechaza la salida (ya no quedaban), no se crea el apartado:
     * vale más no prometer nada que prometer algo que no existe.
     *
     * @throws IllegalStateException si el almacén rechazó la salida
     */
    suspend fun crearApartadoCompartido(
        clienteId: Long,
        productoRemotoId: Long,
        descripcion: String,
        monto: Double,
        anticipo: Double,
        vendedor: String,
        notas: String = "",
        costoUnitario: Double? = null
    ): ApartadoCompartido {
        val salida = almacen?.sacar(
            productoRemotoId = productoRemotoId,
            nombreProducto = descripcion,
            cantidad = 1,
            vendedor = vendedor,
            motivo = "Apartado"
        ) ?: ResultadoMovimiento.Encolado

        if (salida is ResultadoMovimiento.Rechazado) {
            throw IllegalStateException(salida.motivo)
        }

        val apartadoId = db.withTransaction {
            val nuevoId = db.apartadoDao().insertApartado(
                ApartadoEntity(
                    clienteId = clienteId,
                    descripcion = descripcion,
                    monto = monto,
                    anticipo = anticipo,
                    estado = EstadoApartado.PENDIENTE,
                    vendedor = vendedor,
                    notas = notas.trim(),
                    tipo = TipoApartado.APARTADO,
                    productoRemotoId = productoRemotoId,
                    costoUnitario = costoUnitario?.takeIf { it > 0.0 }
                )
            )

            if (anticipo > 0) {
                db.abonoDao().insertAbono(
                    AbonoEntity(apartadoId = nuevoId, monto = anticipo, nota = "Anticipo inicial")
                )
            }
            nuevoId
        }

        backupRepository?.programarRespaldoClientes()
        return ApartadoCompartido(apartadoId, salida)
    }

    suspend fun editarApartado(apartado: ApartadoEntity) {
        db.apartadoDao().updateApartado(apartado)
        backupRepository?.programarRespaldoClientes()
    }

    /**
     * Borra el apartado devolviendo antes la pieza que tenía reservada.
     *
     * Antes sólo borraba la fila: si el apartado seguía pendiente, la pieza
     * se quedaba apartada en el teléfono (o fuera del almacén compartido)
     * para siempre. Es el mismo error que tenía cancelar.
     */
    suspend fun eliminarApartado(apartadoId: Long): ResultadoMovimiento? {
        val apartado = db.apartadoDao().getApartadoByIdSync(apartadoId)
        val resultado = apartado?.let { liberarPiezaSiPendiente(it, "Apartado eliminado") }
        db.apartadoDao().deleteApartadoById(apartadoId)
        backupRepository?.programarRespaldoClientes()
        return resultado
    }

    /**
     * Devuelve la pieza de un apartado que sigue pendiente.
     *
     * Un entregado ya se la llevó el cliente y un cancelado ya la devolvió,
     * así que en esos casos no se mueve nada.
     */
    private suspend fun liberarPiezaSiPendiente(
        apartado: ApartadoEntity,
        motivo: String
    ): ResultadoMovimiento? {
        if (apartado.estado != EstadoApartado.PENDIENTE) return null

        apartado.productoId?.let { db.inventarioDao().liberarStockApartado(it, 1) }

        return apartado.productoRemotoId?.let {
            almacen?.devolver(it, apartado.descripcion, 1, apartado.vendedor, motivo)
        }
    }

    suspend fun marcarApartadoEntregado(apartadoId: Long) {
        db.apartadoDao().updateEstado(apartadoId, EstadoApartado.ENTREGADO.name)
        backupRepository?.programarRespaldoClientes()
    }

    /**
     * Cambia el estado de un apartado **moviendo la pieza que tenía reservada**.
     *
     * Antes de esto, cancelar un apartado sólo cambiaba la etiqueta: la pieza se
     * quedaba contada como apartada para siempre y nunca volvía a estar
     * disponible. Con el almacén compartido eso sería peor, porque la pieza le
     * faltaría también al otro teléfono.
     *
     * Funciona igual para una pieza de este teléfono y para una de alushe.store;
     * lo único que cambia es a quién se le pide el movimiento.
     *
     * @return qué pasó con el almacén compartido, o null si la pieza era local
     *         o el apartado no tenía producto asociado.
     */
    suspend fun cambiarEstadoApartado(
        apartado: ApartadoEntity,
        nuevoEstado: EstadoApartado
    ): ResultadoMovimiento? {
        val anterior = apartado.estado
        if (anterior == nuevoEstado) return null

        val local = apartado.productoId
        val remoto = apartado.productoRemotoId

        // Qué hay que hacer con la pieza en cada transición.
        // ENTREGADO no devuelve nada al almacén compartido: esa pieza ya salió
        // cuando se creó el apartado y ahora se la lleva el cliente.
        val devolver = anterior == EstadoApartado.PENDIENTE && nuevoEstado == EstadoApartado.CANCELADO
        val reservar = anterior == EstadoApartado.CANCELADO && nuevoEstado == EstadoApartado.PENDIENTE
        val entregar = anterior == EstadoApartado.PENDIENTE && nuevoEstado == EstadoApartado.ENTREGADO

        var resultado: ResultadoMovimiento? = null

        if (remoto != null && (devolver || reservar)) {
            val motivo = if (devolver) "Apartado cancelado" else "Apartado reactivado"
            resultado = if (devolver) {
                almacen?.devolver(remoto, apartado.descripcion, 1, apartado.vendedor, motivo)
            } else {
                almacen?.sacar(remoto, apartado.descripcion, 1, apartado.vendedor, motivo)
            }

            // Si el almacén rechazó reservar de nuevo (ya no hay piezas porque
            // el otro teléfono las vendió), el apartado no se reactiva: sería
            // prometerle al cliente algo que ya no existe.
            if (reservar && resultado is ResultadoMovimiento.Rechazado) return resultado
        }

        if (local != null) {
            val dao = db.inventarioDao()
            when {
                devolver -> dao.liberarStockApartado(local, 1)
                reservar -> dao.moverStockAApartado(local, 1)
                entregar -> dao.entregarStockApartado(local, 1)
            }
        }

        db.apartadoDao().updateApartado(apartado.copy(estado = nuevoEstado))
        backupRepository?.programarRespaldoClientes()
        return resultado
    }

    suspend fun registrarAbono(
        apartadoId: Long,
        monto: Double,
        nota: String = "",
        baseId: Long? = null,
        fecha: Long = System.currentTimeMillis()
    ): Long {
        val id = db.withTransaction {
            val apartado = db.apartadoDao().getApartadoByIdSync(apartadoId)
            val abonadoAntes = db.abonoDao().getTotalAbonadoByApartado(apartadoId) ?: 0.0

            val nuevoId = db.abonoDao().insertAbono(
                AbonoEntity(
                    apartadoId = apartadoId,
                    baseId = baseId,
                    monto = monto,
                    fecha = fecha,
                    nota = nota.trim()
                )
            )

            db.apartadoDao().updateAnticipo(apartadoId, abonadoAntes + monto)

            if (baseId != null) {
                val desc = "Abono: ${apartado?.descripcion ?: "Apartado"}${if (nota.isNotBlank()) " ($nota)" else ""}"
                if (apartado != null) {
                    registrarIngresoEnBase(
                        apartado = apartado,
                        baseId = baseId,
                        monto = monto,
                        saldoAntes = apartado.monto - abonadoAntes,
                        fecha = fecha,
                        articulo = desc
                    )
                } else {
                    agregarVentaEnBase(baseId, desc, monto, fecha)
                }
            }
            nuevoId
        }

        backupRepository?.programarRespaldoClientes()
        return id
    }

    /**
     * Mete a la base el dinero que entró por un apartado y, si se conoce el
     * costo de la pieza, la parte proporcional de ese costo.
     *
     * Sin el costo, cada abono contaba como utilidad del 100% y la utilidad de
     * los apartados salía inflada. Con él, los abonos de una pieza suman al
     * final exactamente su costo, igual que si se hubiera vendido en el POS.
     *
     * Lo que se pague de más sobre el saldo no lleva costo: la pieza ya quedó
     * cubierta. Sólo lee el costo del producto; nunca lo modifica.
     */
    private suspend fun registrarIngresoEnBase(
        apartado: ApartadoEntity,
        baseId: Long,
        monto: Double,
        saldoAntes: Double,
        fecha: Long,
        articulo: String
    ) {
        val ventaId = agregarVentaEnBase(baseId, articulo, monto, fecha)

        val local = apartado.productoId?.let { db.inventarioDao().getProductoByIdSync(it) }
        val costoParte = CalculadoraMetricas.costoDeAbono(
            costoPieza = apartado.costoUnitario ?: local?.costoUnitario,
            montoApartado = apartado.monto,
            abono = monto,
            saldoAntes = saldoAntes
        ) ?: return

        db.inventarioDao().insertDetalleVenta(
            DetalleVentaEntity(
                ventaId = ventaId,
                // Sólo si el producto sigue existiendo: la llave foránea lo exige
                productoId = local?.id,
                nombreProductoSnapshot = apartado.descripcion,
                cantidad = 1,
                costoUnitarioHistorico = costoParte,
                precioUnitarioFinal = monto
            )
        )
    }

    private suspend fun agregarVentaEnBase(baseId: Long, articulo: String, monto: Double, fecha: Long): Long =
        basesVentasRepository?.agregarVenta(baseId, articulo, monto, "", fecha)
            ?: db.ventaDao().insertVenta(
                VentaEntity(baseId = baseId, articulo = articulo, monto = monto, fecha = fecha)
            )

    suspend fun eliminarAbono(abono: AbonoEntity) {
        db.withTransaction {
            db.abonoDao().deleteAbono(abono)
            val totalAbonado = db.abonoDao().getTotalAbonadoByApartado(abono.apartadoId) ?: 0.0
            db.apartadoDao().updateAnticipo(abono.apartadoId, totalAbonado)
        }
        backupRepository?.programarRespaldoClientes()
    }
}
