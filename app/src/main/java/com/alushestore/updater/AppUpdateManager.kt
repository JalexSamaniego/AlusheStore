package com.alushestore.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.alushestore.BuildConfig
import com.alushestore.MainActivity
import com.alushestore.R
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String = "",
    @SerializedName("titulo") val titulo: String = "Actualización disponible",
    @SerializedName("notas") val notas: String = "",
    @SerializedName("apkUrl") val apkUrl: String = "",
    @SerializedName("sha256") val sha256: String? = null
)

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>?
)

data class GitHubAsset(
    @SerializedName("name") val name: String?,
    @SerializedName("browser_download_url") val downloadUrl: String?
)

object AppUpdateManager {
    // Constantes de Notificación
    const val CHANNEL_ID_UPDATES = "alushe_updates"
    const val CHANNEL_NAME_UPDATES = "Actualizaciones de AlusheStore"
    const val NOTIF_ID_UPDATE = 2001
    const val EXTRA_SHOW_UPDATE = "extra_show_update"

    @Volatile
    var pendingUpdatePrompt: Boolean = false

    // Configuración de GitHub
    const val GITHUB_OWNER = "JalexSamaniego"
    const val GITHUB_REPO = "AlusheStore"

    private const val VERSION_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /**
     * Consulta si existe una nueva versión en GitHub.
     * Retorna [UpdateInfo] si hay una versión mayor a la instalada, o null si está al día.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        // 1. Intentar leer version.json (rápido y sin límite de peticiones de GitHub)
        try {
            val url = URL(VERSION_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "AlusheStoreApp")
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val info = Gson().fromJson(json, UpdateInfo::class.java)
                if (info != null && (info.versionCode > BuildConfig.VERSION_CODE || isNewerVersion(info.versionName, BuildConfig.VERSION_NAME))) {
                    return@withContext info
                }
            }
        } catch (_: Exception) {}

        // 2. Si no hay version.json, consultar la API de Releases de GitHub
        try {
            val url = URL(RELEASES_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "AlusheStoreApp")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val release = Gson().fromJson(json, GitHubRelease::class.java)
                val apkAsset = release.assets?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
                val tag = release.tagName?.removePrefix("v")?.trim() ?: ""

                if (apkAsset?.downloadUrl != null && isNewerVersion(tag, BuildConfig.VERSION_NAME)) {
                    return@withContext UpdateInfo(
                        versionCode = BuildConfig.VERSION_CODE + 1,
                        versionName = release.tagName ?: "Nueva versión",
                        titulo = release.name ?: "¡Nueva versión disponible!",
                        notas = release.body ?: "Se han incluido mejoras y novedades en AlusheStore.",
                        apkUrl = apkAsset.downloadUrl
                    )
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Consulta si hay actualización y, de encontrarla, dispara la notificación del sistema.
     */
    suspend fun checkForUpdateAndNotify(context: Context, showNotificationIfFound: Boolean = true): UpdateInfo? {
        val info = checkForUpdate()
        if (info != null && showNotificationIfFound) {
            showUpdateNotification(context, info)
        }
        return info
    }

    /**
     * Crea el canal de notificaciones para actualizaciones si no existe (Android 8+).
     */
    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_UPDATES,
                CHANNEL_NAME_UPDATES,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos automáticos de nuevas versiones de AlusheStore"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Emite una notificación del sistema indicando que hay una nueva versión disponible.
     */
    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo, force: Boolean = false) {
        try {
            initNotificationChannel(context)

            // Evitar spam de notificaciones para la misma versión a menos que sea forzado
            val prefs = context.getSharedPreferences("alushe_prefs", Context.MODE_PRIVATE)
            val lastNotified = prefs.getString("last_notified_update_version", null)
            val currentVersionTag = updateInfo.versionName.ifBlank { "v${updateInfo.versionCode}" }

            if (!force && lastNotified == currentVersionTag) {
                return
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHOW_UPDATE, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIF_ID_UPDATE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smallIcon = R.drawable.ic_notification_update

            val primerParrafoNotas = if (updateInfo.notas.isNotBlank()) {
                updateInfo.notas.lines().filter { it.isNotBlank() }.take(3).joinToString("\n")
            } else {
                "Toca aquí para actualizar a la última versión disponible."
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
                .setSmallIcon(smallIcon)
                .setContentTitle(updateInfo.titulo.ifBlank { "¡Nueva versión disponible!" })
                .setContentText("AlusheStore ${updateInfo.versionName} está lista para actualizar")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(updateInfo.titulo.ifBlank { "¡Nueva versión ${updateInfo.versionName} disponible!" })
                        .bigText("Se detectó una nueva versión disponible en GitHub:\n$primerParrafoNotas")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(0xFF2D7A4F.toInt()) // AlusheGreen
                .build()

            val nm = NotificationManagerCompat.from(context)
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                nm.notify(NOTIF_ID_UPDATE, notification)
                prefs.edit().putString("last_notified_update_version", currentVersionTag).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Compara semver como "1.1.0" vs "1.0.0". Retorna true si remote > local.
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val rParts = remote.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    /**
     * Calcula el hash SHA-256 de un archivo local.
     */
    fun calculateFileSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Valida si el archivo coincide con el hash SHA-256 esperado (sin distinguir mayúsculas).
     */
    fun verifyFileSha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val actual = calculateFileSha256(file)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    /**
     * Descarga el archivo APK en el almacenamiento interno de la app con callback de progreso (0f a 1f)
     * y verificación opcional de integridad SHA-256.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        try {
            var currentUrl = apkUrl
            var conn: HttpURLConnection
            var redirects = 0

            // Seguir redirecciones (GitHub Releases redirige a AWS S3)
            while (true) {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 12000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "AlusheStoreApp")

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    currentUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    redirects++
                    if (redirects > 5) return@withContext Result.failure(Exception("Demasiadas redirecciones al descargar APK"))
                    continue
                }
                break
            }

            val totalBytes = conn.contentLength.toLong()
            val targetFile = File(context.cacheDir, "AlusheStore-update.apk")
            outputFile = targetFile
            if (targetFile.exists()) targetFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            // Verificación criptográfica SHA-256 si viene en el descriptor
            if (!expectedSha256.isNullOrBlank()) {
                val matches = verifyFileSha256(targetFile, expectedSha256)
                if (!matches) {
                    if (targetFile.exists()) targetFile.delete()
                    return@withContext Result.failure(
                        SecurityException("La verificación de integridad SHA-256 falló. El archivo APK está corrupto o alterado y fue eliminado.")
                    )
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            outputFile?.let { if (it.exists()) it.delete() }
            Result.failure(e)
        }
    }

    /**
     * Limpia archivos .apk residuales de actualizaciones previas en la caché de la aplicación.
     */
    fun limpiarApksResiduales(context: Context) {
        try {
            val dirs = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir
            )
            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles { _, name -> name.endsWith(".apk", ignoreCase = true) }?.forEach { apkFile ->
                        apkFile.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Abre el instalador del paquete de Android con el APK descargado.
     * Si no tiene permiso de instalar apps desconocidas en Android 8+, abre la pantalla de ajustes correspondiente.
     */
    fun promptInstall(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permissionIntent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
