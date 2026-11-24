package com.alushestore.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Metas de venta del mes. Todo opcional: sin meta no se muestra nada. */
data class MetasConfig(
    val metaMensual: Double? = null,
    /** nombre del vendedor → su meta del mes */
    val porVendedor: Map<String, Double> = emptyMap()
) {
    val activa: Boolean get() = metaMensual != null || porVendedor.isNotEmpty()
}

/**
 * Guarda las metas en preferencias del teléfono.
 *
 * No viven en Room a propósito: son una preferencia de quien usa el teléfono,
 * no un dato del negocio, y así no hace falta migración.
 */
class MetasManager private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("alushe_metas", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(leer())
    val config: StateFlow<MetasConfig> = _config.asStateFlow()

    private fun leer(): MetasConfig {
        val mensual = prefs.getFloat(KEY_MENSUAL, -1f).takeIf { it > 0f }?.toDouble()
        val porVendedor = prefs.all
            .filterKeys { it.startsWith(PREFIJO_VENDEDOR) }
            .mapNotNull { (k, v) ->
                val monto = (v as? Float)?.toDouble()?.takeIf { it > 0 } ?: return@mapNotNull null
                k.removePrefix(PREFIJO_VENDEDOR) to monto
            }
            .toMap()
        return MetasConfig(mensual, porVendedor)
    }

    /** null o 0 borran la meta correspondiente */
    fun guardar(metaMensual: Double?, porVendedor: Map<String, Double?>) {
        val edit = prefs.edit()
        if (metaMensual != null && metaMensual > 0) edit.putFloat(KEY_MENSUAL, metaMensual.toFloat())
        else edit.remove(KEY_MENSUAL)

        // Se limpian las metas por persona anteriores: los nombres pueden haber cambiado
        prefs.all.keys.filter { it.startsWith(PREFIJO_VENDEDOR) }.forEach { edit.remove(it) }
        porVendedor.forEach { (nombre, monto) ->
            if (monto != null && monto > 0) edit.putFloat(PREFIJO_VENDEDOR + nombre, monto.toFloat())
        }
        edit.apply()
        // apply() escribe en segundo plano: el valor nuevo se arma aquí mismo
        _config.value = MetasConfig(
            metaMensual?.takeIf { it > 0 },
            porVendedor.mapNotNull { (n, m) -> m?.takeIf { it > 0 }?.let { n to it } }.toMap()
        )
    }

    companion object {
        private const val KEY_MENSUAL = "meta_mensual"
        private const val PREFIJO_VENDEDOR = "meta_vendedor_"

        @Volatile
        private var INSTANCE: MetasManager? = null

        fun getInstance(context: Context): MetasManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MetasManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
