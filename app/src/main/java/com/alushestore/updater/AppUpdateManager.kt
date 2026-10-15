package com.alushestore.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.alushestore.BuildConfig
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
    @SerializedName("apkUrl") val apkUrl: String = ""
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
     * Descarga el archivo APK en el almacenamiento interno de la app con callback de progreso (0f a 1f).
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
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
                    if (redirects > 5) return@withContext null
                    continue
                }
                break
            }

            val totalBytes = conn.contentLength.toLong()
            val outputFile = File(context.cacheDir, "AlusheStore-update.apk")
            if (outputFile.exists()) outputFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
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
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
