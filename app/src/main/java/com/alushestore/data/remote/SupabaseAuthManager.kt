package com.alushestore.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.alushestore.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private data class RespuestaToken(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    val user: UsuarioSupabase? = null,
    @SerializedName("error_description") val errorDescripcion: String? = null,
    @SerializedName("msg") val mensaje: String? = null
)

data class UsuarioSupabase(val id: String? = null, val email: String? = null)

/**
 * Sesión de Supabase para la app, con **emparejamiento único**.
 *
 * El correo y la contraseña se escriben una sola vez por teléfono. A partir de
 * ahí sólo se guarda el refresh token, cifrado, y la sesión se renueva sola.
 * Nunca se guarda la contraseña, y nada de esto viaja dentro del APK.
 *
 * El email de la sesión es el que `registrar_movimiento` escribe en la bitácora
 * como `usuario`, así que conviene una cuenta por persona para saber quién
 * movió cada pieza.
 */
class SupabaseAuthManager private constructor(context: Context) {

    private val gson = Gson()
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY
    private val mutex = Mutex()

    private val prefs: SharedPreferences = crearPrefsCifradas(context.applicationContext)

    private var accessToken: String? = null
    private var expiraEn: Long = 0L

    private val _emparejado = MutableStateFlow(prefs.getString(KEY_REFRESH, null) != null)
    val emparejado: StateFlow<Boolean> = _emparejado.asStateFlow()

    private val _correo = MutableStateFlow(prefs.getString(KEY_EMAIL, null))
    val correo: StateFlow<String?> = _correo.asStateFlow()

    /**
     * Empareja este teléfono. Es lo único que pide credenciales, una sola vez.
     * La contraseña se usa para obtener el refresh token y se descarta.
     */
    suspend fun emparejar(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Falta configurar Supabase en local.properties"))
        }
        try {
            val cuerpo = gson.toJson(mapOf("email" to email.trim(), "password" to password))
            val r = postAuth("token?grant_type=password", cuerpo)

            val refresh = r.refreshToken
            val access = r.accessToken
            if (refresh == null || access == null) {
                return@withContext Result.failure(Exception(mensajeDeError(r)))
            }

            prefs.edit()
                .putString(KEY_REFRESH, refresh)
                .putString(KEY_EMAIL, r.user?.email ?: email.trim())
                .apply()

            accessToken = access
            expiraEn = System.currentTimeMillis() + (r.expiresIn ?: 3600L) * 1000
            _emparejado.value = true
            _correo.value = r.user?.email ?: email.trim()

            Result.success(_correo.value ?: email.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Olvida la sesión de este teléfono. No toca la cuenta en Supabase. */
    fun desemparejar() {
        prefs.edit().remove(KEY_REFRESH).remove(KEY_EMAIL).apply()
        accessToken = null
        expiraEn = 0L
        _emparejado.value = false
        _correo.value = null
    }

    /**
     * Token válido para llamar a la API, renovándolo si hace falta.
     * Devuelve null si el teléfono no está emparejado o el refresh ya no sirve.
     */
    suspend fun tokenValido(): String? = mutex.withLock {
        val vigente = accessToken
        if (vigente != null && System.currentTimeMillis() < expiraEn - MARGEN_MS) return vigente

        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null

        return try {
            withContext(Dispatchers.IO) {
                val cuerpo = gson.toJson(mapOf("refresh_token" to refresh))
                val r = postAuth("token?grant_type=refresh_token", cuerpo)

                val access = r.accessToken ?: return@withContext null
                // Supabase rota el refresh token en cada renovación
                r.refreshToken?.let { prefs.edit().putString(KEY_REFRESH, it).apply() }
                accessToken = access
                expiraEn = System.currentTimeMillis() + (r.expiresIn ?: 3600L) * 1000
                access
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun postAuth(ruta: String, cuerpoJson: String): RespuestaToken {
        val conn = (URL("$baseUrl/auth/v1/$ruta").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { it.write(cuerpoJson.toByteArray()) }
            val texto = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            return gson.fromJson(texto, RespuestaToken::class.java) ?: RespuestaToken()
        } finally {
            conn.disconnect()
        }
    }

    private fun mensajeDeError(r: RespuestaToken): String = when {
        !r.errorDescripcion.isNullOrBlank() -> r.errorDescripcion
        !r.mensaje.isNullOrBlank() -> r.mensaje
        else -> "No se pudo iniciar sesión"
    }

    private fun crearPrefsCifradas(ctx: Context): SharedPreferences {
        // API de security-crypto 1.0.0 (estable). MasterKeys está marcada como
        // deprecada en favor de MasterKey, que sólo existe en las alpha.
        @Suppress("DEPRECATION")
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        @Suppress("DEPRECATION")
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            alias,
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val PREFS_NAME = "alushe_sesion_supabase"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EMAIL = "email"

        /** Se renueva un minuto antes de expirar, para no llegar justos */
        private const val MARGEN_MS = 60_000L
        private const val TIMEOUT_MS = 15_000

        @Volatile
        private var INSTANCE: SupabaseAuthManager? = null

        fun getInstance(context: Context): SupabaseAuthManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseAuthManager(context).also { INSTANCE = it }
            }
    }
}
