package com.alushestore.data.repository

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.alushestore.data.local.entities.AbonoEntity
import com.alushestore.data.local.entities.ApartadoEntity
import com.alushestore.data.local.entities.BaseEntity
import com.alushestore.data.local.entities.ClienteEntity
import com.alushestore.data.local.entities.VentaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Modelo que se serializa a JSON y se sube a Drive */
data class BaseBackup(
    val base: BaseEntity,
    val ventas: List<VentaEntity>,
    val totalMonto: Double,
    val cantidadVentas: Int,
    val exportadoEn: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val schemaVersion: Int = 4,
    val timestamp: Long = System.currentTimeMillis()
)

/** Respaldo completo de clientes y sus apartados/pedidos (un solo archivo en Drive) */
data class ClientesBackup(
    val clientes: List<ClienteEntity>,
    val apartados: List<ApartadoEntity>,
    val abonos: List<AbonoEntity> = emptyList(),
    val totalClientes: Int,
    val totalApartados: Int,
    val saldoPendiente: Double,
    val exportadoEn: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val schemaVersion: Int = 4,
    val timestamp: Long = System.currentTimeMillis()
)

class DriveBackupService(
    private val context: Context,
    private val accountName: String
) {
    private val gson = Gson()
    private val appFolderName = "AlusheStore"

    private fun buildDriveService(): Drive {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply { selectedAccountName = accountName }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("AlusheStore").build()
    }

    /**
     * Sube o actualiza el respaldo de una base en Google Drive.
     * Retorna el ID del archivo en Drive.
     */
    suspend fun subirBase(base: BaseEntity, ventas: List<VentaEntity>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService()
                val folderId = obtenerOCrearCarpeta(drive)

                val backup = BaseBackup(
                    base = base,
                    ventas = ventas,
                    totalMonto = ventas.sumOf { it.monto },
                    cantidadVentas = ventas.size
                )
                val jsonContent = gson.toJson(backup)
                val byteContent = ByteArrayContent.fromString("application/json", jsonContent)

                val fileName = "base_${base.id}_${base.nombre.replace(" ", "_")}.json"

                // Si ya existe en Drive, actualizarlo; si no, crearlo
                if (base.driveFileId != null) {
                    drive.files().update(base.driveFileId, null, byteContent).execute()
                    Result.success(base.driveFileId)
                } else {
                    val metadata = File().apply {
                        name = fileName
                        parents = listOf(folderId)
                        mimeType = "application/json"
                    }
                    val file = drive.files().create(metadata, byteContent)
                        .setFields("id")
                        .execute()
                    Result.success(file.id)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Sube o actualiza el respaldo de clientes (un único archivo en la carpeta AlusheStore).
     * Si el archivo ya existe en Drive se sobrescribe, si no se crea.
     */
    suspend fun subirClientes(backup: ClientesBackup): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService()
                val folderId = obtenerOCrearCarpeta(drive)
                val content = ByteArrayContent.fromString("application/json", gson.toJson(backup))

                val existente = drive.files().list()
                    .setQ("name = '$CLIENTES_FILE_NAME' and '$folderId' in parents and trashed = false")
                    .setFields("files(id)")
                    .execute()
                    .files
                    .firstOrNull()

                if (existente != null) {
                    drive.files().update(existente.id, null, content).execute()
                    Result.success(existente.id)
                } else {
                    val metadata = File().apply {
                        name = CLIENTES_FILE_NAME
                        parents = listOf(folderId)
                        mimeType = "application/json"
                    }
                    val file = drive.files().create(metadata, content)
                        .setFields("id")
                        .execute()
                    Result.success(file.id)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Lista todos los respaldos de bases disponibles en Drive
     * (el archivo de clientes tiene otro formato y se excluye).
     */
    suspend fun listarRespaldos(): Result<List<DriveBackupInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService()
                val folderId = obtenerOCrearCarpeta(drive)

                val result = drive.files().list()
                    .setQ("'$folderId' in parents and trashed = false")
                    .setFields("files(id, name, modifiedTime, size)")
                    .execute()

                val backups = result.files.filter { it.name != CLIENTES_FILE_NAME }.map { file ->
                    DriveBackupInfo(
                        fileId = file.id,
                        nombre = file.name,
                        modificadoEn = file.modifiedTime?.value ?: 0L,
                        tamano = file.getSize() ?: 0L
                    )
                }
                Result.success(backups)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Descarga un respaldo específico de Drive.
     */
    suspend fun descargarRespaldo(fileId: String): Result<BaseBackup> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService()
                val outputStream = java.io.ByteArrayOutputStream()
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                val json = outputStream.toString("UTF-8")
                val backup = gson.fromJson(json, BaseBackup::class.java)
                Result.success(backup)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Descarga el archivo de clientes consolidado desde Drive.
     */
    suspend fun descargarClientes(): Result<ClientesBackup> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService()
                val folderId = obtenerOCrearCarpeta(drive)

                val existente = drive.files().list()
                    .setQ("name = '$CLIENTES_FILE_NAME' and '$folderId' in parents and trashed = false")
                    .setFields("files(id)")
                    .execute()
                    .files
                    .firstOrNull()
                    ?: return@withContext Result.failure(Exception("No se encontró el respaldo de clientes en Drive"))

                val outputStream = java.io.ByteArrayOutputStream()
                drive.files().get(existente.id).executeMediaAndDownloadTo(outputStream)
                val json = outputStream.toString("UTF-8")
                val backup = gson.fromJson(json, ClientesBackup::class.java)
                Result.success(backup)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Obtiene o crea la carpeta "AlusheStore" en Drive */
    private fun obtenerOCrearCarpeta(drive: Drive): String {
        val query = "name = '$appFolderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result = drive.files().list().setQ(query).setFields("files(id)").execute()

        return if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            val folder = File().apply {
                name = appFolderName
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(folder).setFields("id").execute().id
        }
    }

    companion object {
        const val CLIENTES_FILE_NAME = "clientes_alushe.json"
    }
}

data class DriveBackupInfo(
    val fileId: String,
    val nombre: String,
    val modificadoEn: Long,
    val tamano: Long
)
