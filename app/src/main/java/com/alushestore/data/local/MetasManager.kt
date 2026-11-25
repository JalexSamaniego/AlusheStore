package com.alushestore.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Metas de venta de una base. Todo opcional: sin meta no se muestra nada. */
data class MetasConfig(
    val metaMensual: Double? = null,
    /** nombre del vendedor → su meta en esa base */
    val porVendedor: Map<String, Double> = emptyMap()
) {
    val activa: Boolean get() = metaMensual != null || porVendedor.isNotEmpty()
}

/**
 * Guarda las metas en preferencias del teléfono, **una por base**.
 *
 * No viven en Room a propósito: son una preferencia de quien usa el teléfono,
 * no un dato del negocio, y así no hace falta migración.
 *
 * Claves: `base_<id>_meta` y `base_<id>_vendedor_<nombre>`.
 */
class MetasManager private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("alushe_metas", Context.MODE_PRIVATE)

    private val _todas = MutableStateFlow(leerTodas())
    /** baseId → sus metas. Sólo aparecen las bases que tienen alguna. */
    val todas: StateFlow<Map<Long, MetasConfig>> = _todas.asStateFlow()

    fun configDe(baseId: Long): MetasConfig = _todas.value[baseId] ?: MetasConfig()

    private fun leerTodas(): Map<Long, MetasConfig> {
        val porBase = mutableMapOf<Long, MetasConfig>()
        prefs.all.forEach { (k, v) ->
            val m = CLAVE.matchEntire(k) ?: return@forEach
            val monto = (v as? Float)?.toDouble()?.takeIf { it > 0 } ?: return@forEach
            val baseId = m.groupValues[1].toLongOrNull() ?: return@forEach
            val actual = porBase[baseId] ?: MetasConfig()
            val vendedor = m.groupValues[3]
            porBase[baseId] = if (vendedor.isEmpty()) actual.copy(metaMensual = monto)
            else actual.copy(porVendedor = actual.porVendedor + (vendedor to monto))
        }
        return porBase
    }

    /** null o 0 borran la meta correspondiente */
    fun guardar(baseId: Long, metaMensual: Double?, porVendedor: Map<String, Double?>) {
        val edit = prefs.edit()
        val prefijo = "base_${baseId}_"
        // Se limpia lo anterior de esa base: los nombres pueden haber cambiado
        prefs.all.keys.filter { it.startsWith(prefijo) }.forEach { edit.remove(it) }
        if (metaMensual != null && metaMensual > 0) edit.putFloat("${prefijo}meta", metaMensual.toFloat())
        porVendedor.forEach { (nombre, monto) ->
            if (monto != null && monto > 0) edit.putFloat("${prefijo}vendedor_$nombre", monto.toFloat())
        }
        edit.apply()
        // apply() escribe en segundo plano: el valor nuevo se arma aquí mismo
        val nueva = MetasConfig(
            metaMensual?.takeIf { it > 0 },
            porVendedor.mapNotNull { (n, m) -> m?.takeIf { it > 0 }?.let { n to it } }.toMap()
        )
        _todas.value = if (nueva.activa) _todas.value + (baseId to nueva) else _todas.value - baseId
    }

    /**
     * La 2.1.0 guardaba una sola meta para todo el mes. Si quedó alguna, pasa
     * a la base activa para que nadie pierda la que ya había puesto.
     */
    fun adoptarMetaAnterior(baseId: Long) {
        val viejas = prefs.all.filterKeys { it == LEGADO_MENSUAL || it.startsWith(LEGADO_VENDEDOR) }
        if (viejas.isEmpty()) return
        if (!configDe(baseId).activa) {
            val mensual = (viejas[LEGADO_MENSUAL] as? Float)?.toDouble()
            val porVendedor = viejas
                .filterKeys { it.startsWith(LEGADO_VENDEDOR) }
                .mapNotNull { (k, v) -> (v as? Float)?.toDouble()?.let { k.removePrefix(LEGADO_VENDEDOR) to it } }
                .toMap()
            guardar(baseId, mensual, porVendedor)
        }
        prefs.edit().apply { viejas.keys.forEach { remove(it) } }.apply()
    }

    companion object {
        private val CLAVE = Regex("""base_(\d+)_(meta|vendedor_(.+))""")
        private const val LEGADO_MENSUAL = "meta_mensual"
        private const val LEGADO_VENDEDOR = "meta_vendedor_"

        @Volatile
        private var INSTANCE: MetasManager? = null

        fun getInstance(context: Context): MetasManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MetasManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
