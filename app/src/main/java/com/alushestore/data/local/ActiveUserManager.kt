package com.alushestore.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor del perfil de usuario activo para etiquetar ventas y abonos en preparación
 * multiusuario y futura sincronización bidireccional con Supabase.
 */
class ActiveUserManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Va antes que los StateFlow a propósito: los inicializadores corren en
    // orden de declaración, así que los nombres ya salen migrados.
    init {
        migrarNombresGenericos()
    }

    /**
     * Los teléfonos que ya venían usándose tienen guardado "Usuario 1" y
     * "Usuario 2". Cambiar sólo el valor por defecto no los tocaría, así que se
     * reemplazan aquí. Un nombre que ustedes hayan puesto a mano no se toca.
     */
    private fun migrarNombresGenericos() {
        val edit = prefs.edit()
        var hubocambio = false

        fun migrar(clave: String) {
            when (prefs.getString(clave, null)) {
                VIEJO_USER_1 -> { edit.putString(clave, DEFAULT_USER_1); hubocambio = true }
                VIEJO_USER_2 -> { edit.putString(clave, DEFAULT_USER_2); hubocambio = true }
            }
        }

        migrar(KEY_USER_1_NAME)
        migrar(KEY_USER_2_NAME)
        migrar(KEY_ACTIVE_USER)

        if (hubocambio) edit.apply()
    }

    private val _vendedorActivo = MutableStateFlow(getVendedorGuardado())
    val vendedorActivo: StateFlow<String> = _vendedorActivo.asStateFlow()

    private val _usuario1 = MutableStateFlow(getUsuario1Guardado())
    val usuario1: StateFlow<String> = _usuario1.asStateFlow()

    private val _usuario2 = MutableStateFlow(getUsuario2Guardado())
    val usuario2: StateFlow<String> = _usuario2.asStateFlow()

    private fun getVendedorGuardado(): String {
        return prefs.getString(KEY_ACTIVE_USER, DEFAULT_USER_1) ?: DEFAULT_USER_1
    }

    private fun getUsuario1Guardado(): String {
        return prefs.getString(KEY_USER_1_NAME, DEFAULT_USER_1) ?: DEFAULT_USER_1
    }

    private fun getUsuario2Guardado(): String {
        return prefs.getString(KEY_USER_2_NAME, DEFAULT_USER_2) ?: DEFAULT_USER_2
    }

    fun setVendedorActivo(nombre: String) {
        val limpio = nombre.trim().ifBlank { DEFAULT_USER_1 }
        prefs.edit().putString(KEY_ACTIVE_USER, limpio).apply()
        _vendedorActivo.value = limpio
    }

    fun alternarVendedor() {
        val actual = _vendedorActivo.value
        val u1 = _usuario1.value
        val u2 = _usuario2.value
        val nuevo = if (actual.equals(u1, ignoreCase = true)) u2 else u1
        setVendedorActivo(nuevo)
    }

    fun configurarNombres(nombre1: String, nombre2: String) {
        val limpio1 = nombre1.trim().ifBlank { DEFAULT_USER_1 }
        val limpio2 = nombre2.trim().ifBlank { DEFAULT_USER_2 }
        prefs.edit()
            .putString(KEY_USER_1_NAME, limpio1)
            .putString(KEY_USER_2_NAME, limpio2)
            .apply()
        _usuario1.value = limpio1
        _usuario2.value = limpio2

        // Si el vendedor activo actual coincide con alguno de los viejos, reajustar
        if (_vendedorActivo.value != limpio1 && _vendedorActivo.value != limpio2) {
            setVendedorActivo(limpio1)
        }
    }

    companion object {
        private const val PREFS_NAME = "alushe_multiusuario_prefs"
        private const val KEY_ACTIVE_USER = "key_vendedor_activo"
        private const val KEY_USER_1_NAME = "key_usuario_1_nombre"
        private const val KEY_USER_2_NAME = "key_usuario_2_nombre"

        const val DEFAULT_USER_1 = "Dulce"
        const val DEFAULT_USER_2 = "Alejandro"

        // Como se llamaban antes, para poder migrar lo ya guardado
        private const val VIEJO_USER_1 = "Usuario 1"
        private const val VIEJO_USER_2 = "Usuario 2"

        @Volatile
        private var INSTANCE: ActiveUserManager? = null

        fun getInstance(context: Context): ActiveUserManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActiveUserManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
