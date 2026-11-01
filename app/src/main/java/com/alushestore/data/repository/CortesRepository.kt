package com.alushestore.data.repository

import androidx.room.withTransaction
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.local.entities.CorteConVendedores
import com.alushestore.data.local.entities.CorteEntity
import com.alushestore.data.local.entities.CorteVendedorEntity
import com.alushestore.domain.CalculadoraMetricas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Cortes de base: la foto del periodo al momento de cerrarlo.
 *
 * El corte se calcula una sola vez y se guarda ya resuelto. No se recalcula
 * después, para que el estado de cuenta de un mes ya repartido no cambie si más
 * adelante se ajustan costos o precios del catálogo.
 */
class CortesRepository(private val db: AlusheDatabase) {

    private val dao = db.corteDao()

    fun getAllCortes(): Flow<List<CorteConVendedores>> = dao.getAllCortes()

    fun getCorteByBase(baseId: Long): Flow<CorteConVendedores?> = dao.getCorteByBase(baseId)

    suspend fun getCorteByBaseSync(baseId: Long): CorteConVendedores? =
        withContext(Dispatchers.IO) { dao.getCorteByBaseSync(baseId) }

    suspend fun getCortesPendientesDeSubir(): List<CorteConVendedores> =
        withContext(Dispatchers.IO) { dao.getCortesPendientesDeSubir() }

    suspend fun marcarSubido(corteId: Long) = withContext(Dispatchers.IO) {
        dao.marcarSubido(corteId)
    }

    /**
     * Cierra la base y deja su corte en el mismo paso atómico.
     *
     * Si la base ya tenía corte, no se genera otro: el corte es inmutable y se
     * devuelve el que ya existía.
     *
     * @return el corte de esa base, recién creado o el que ya estaba.
     */
    suspend fun cerrarBaseConCorte(baseId: Long): CorteConVendedores? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val base = db.baseDao().getBaseById(baseId) ?: return@withTransaction null

            val existente = dao.getCorteByBaseSync(baseId)
            if (existente != null) {
                // La base pudo quedar abierta si algo falló antes; asegurar el cierre.
                if (!base.cerrada) db.baseDao().cerrarBase(baseId)
                return@withTransaction existente
            }

            val fechaCierre = System.currentTimeMillis()
            val corteId = construirCorte(base, fechaCierre, conInventario = true)
            db.baseDao().cerrarBase(baseId, fechaCierre)

            dao.getCorteByIdSync(corteId)
        }
    }

    /**
     * Genera el corte de una base que ya estaba cerrada desde antes de que
     * existieran los cortes.
     *
     * El inventario se deja en cero a propósito: el stock de hoy no es el que
     * había entonces, y preferimos no mostrarlo a mostrar un número falso.
     * El diálogo lo detecta por la distancia entre [CorteEntity.creadoEn] y la
     * fecha de cierre, y oculta esa sección.
     */
    suspend fun generarCorteRetroactivo(baseId: Long): CorteConVendedores? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val base = db.baseDao().getBaseById(baseId) ?: return@withTransaction null
            dao.getCorteByBaseSync(baseId)?.let { return@withTransaction it }

            val fechaCierre = base.fechaCierre ?: base.fechaCreacion
            val corteId = construirCorte(base, fechaCierre, conInventario = false)
            dao.getCorteByIdSync(corteId)
        }
    }

    /**
     * Calcula y guarda el corte de una base. Debe llamarse dentro de una transacción.
     */
    private suspend fun construirCorte(
        base: com.alushestore.data.local.entities.BaseEntity,
        fechaCierre: Long,
        conInventario: Boolean
    ): Long {
        val ventas = db.ventaDao().getVentasByBaseSync(base.id)
        val ventaIds = ventas.map { it.id }.toSet()
        val detalles = db.inventarioDao().getAllDetallesVentaSync()
            .filter { it.ventaId in ventaIds }

        val metricas = CalculadoraMetricas.calcular(ventas, detalles)

        val productos = if (conInventario) {
            db.inventarioDao().getAllProductosSync().filter { it.activo }
        } else {
            emptyList()
        }

        val corteId = dao.insertCorte(
            CorteEntity(
                baseId = base.id,
                nombrePeriodo = base.nombre,
                fechaInicio = base.fechaCreacion,
                fechaCierre = fechaCierre,
                totalVendido = metricas.totalVendido,
                numVentas = metricas.numVentas,
                ticketPromedio = metricas.ticketPromedio,
                costoVendido = metricas.costoVendido,
                utilidadNeta = metricas.utilidadNeta,
                margenSobreCosto = metricas.margenSobreCosto,
                ventasConCosto = metricas.ventasConCosto,
                ventasSinCosto = metricas.ventasSinCosto,
                valorInventarioCierre = productos.sumOf { it.costoUnitario * it.stockDisponible },
                piezasInventarioCierre = productos.sumOf { it.stockDisponible }
            )
        )

        val porVendedor = ventas
            .groupBy { it.vendedor.ifBlank { "PRINCIPAL" } }
            .map { (vendedor, suyas) ->
                CorteVendedorEntity(
                    corteId = corteId,
                    vendedor = vendedor,
                    totalVendido = suyas.sumOf { it.monto },
                    numVentas = suyas.size
                )
            }
            .sortedByDescending { it.totalVendido }

        if (porVendedor.isNotEmpty()) dao.insertVendedores(porVendedor)

        return corteId
    }
}
