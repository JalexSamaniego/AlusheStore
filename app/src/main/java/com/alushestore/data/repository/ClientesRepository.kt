package com.alushestore.data.repository

import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoConAbonos
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.ClienteResumen
import com.alushestore.data.local.entities.EstadoApartado
import com.alushestore.data.local.entities.TipoApartado
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.flow.Flow

class ClientesRepository(
    private val db: AlusheDatabase,
    private val backupRepository: BackupRepository? = null,
    private val basesVentasRepository: BasesVentasRepository? = null
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

    suspend fun eliminarCliente(cliente: ClienteEntity) {
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
        baseId: Long? = null
    ): Long {
        val fecha = System.currentTimeMillis()
        val id = db.apartadoDao().insertApartado(
            ApartadoEntity(
                clienteId = clienteId,
                descripcion = descripcion,
                monto = monto,
                anticipo = anticipo,
                notas = notas,
                tipo = tipo,
                fecha = fecha
            )
        )

        if (anticipo > 0) {
            db.abonoDao().insertAbono(
                AbonoEntity(
                    apartadoId = id,
                    baseId = baseId,
                    monto = anticipo,
                    fecha = fecha,
                    nota = "Anticipo inicial"
                )
            )

            if (baseId != null) {
                val desc = "Anticipo: $descripcion"
                if (basesVentasRepository != null) {
                    basesVentasRepository.agregarVenta(baseId, desc, anticipo, "", fecha)
                } else {
                    db.ventaDao().insertVenta(
                        VentaEntity(baseId = baseId, articulo = desc, monto = anticipo, fecha = fecha)
                    )
                }
            }
        }

        backupRepository?.programarRespaldoClientes()
        return id
    }

    suspend fun editarApartado(apartado: ApartadoEntity) {
        db.apartadoDao().updateApartado(apartado)
        backupRepository?.programarRespaldoClientes()
    }

    suspend fun eliminarApartado(apartadoId: Long) {
        db.apartadoDao().deleteApartadoById(apartadoId)
        backupRepository?.programarRespaldoClientes()
    }

    suspend fun marcarApartadoEntregado(apartadoId: Long) {
        db.apartadoDao().updateEstado(apartadoId, EstadoApartado.ENTREGADO.name)
        backupRepository?.programarRespaldoClientes()
    }

    suspend fun registrarAbono(
        apartadoId: Long,
        monto: Double,
        nota: String = "",
        baseId: Long? = null,
        fecha: Long = System.currentTimeMillis()
    ): Long {
        val id = db.abonoDao().insertAbono(
            AbonoEntity(
                apartadoId = apartadoId,
                baseId = baseId,
                monto = monto,
                fecha = fecha,
                nota = nota.trim()
            )
        )

        val totalAbonado = db.abonoDao().getTotalAbonadoByApartado(apartadoId) ?: 0.0
        db.apartadoDao().updateAnticipo(apartadoId, totalAbonado)

        if (baseId != null) {
            val apartado = db.apartadoDao().getAllApartadosSync().find { it.id == apartadoId }
            val desc = "Abono: ${apartado?.descripcion ?: "Apartado"}${if (nota.isNotBlank()) " ($nota)" else ""}"
            if (basesVentasRepository != null) {
                basesVentasRepository.agregarVenta(baseId, desc, monto, "", fecha)
            } else {
                db.ventaDao().insertVenta(
                    VentaEntity(baseId = baseId, articulo = desc, monto = monto, fecha = fecha)
                )
            }
        }

        backupRepository?.programarRespaldoClientes()
        return id
    }

    suspend fun eliminarAbono(abono: AbonoEntity) {
        db.abonoDao().deleteAbono(abono)
        val totalAbonado = db.abonoDao().getTotalAbonadoByApartado(abono.apartadoId) ?: 0.0
        db.apartadoDao().updateAnticipo(abono.apartadoId, totalAbonado)
        backupRepository?.programarRespaldoClientes()
    }
}
