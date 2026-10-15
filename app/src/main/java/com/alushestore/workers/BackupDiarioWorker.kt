package com.alushestore.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.repository.BaseBackup
import com.alushestore.data.repository.DriveBackupService
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BackupDiarioWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = AlusheDatabase.getDatabase(ctx)
        val base = db.baseDao().getBaseActiva().first() ?: return Result.success()
        val ventas = db.ventaDao().getVentasByBaseSync(base.id)

        // ── Backup local ──────────────────────────────────────────────────────
        guardarLocalBackup(base.id, base.nombre, ventas)

        // ── Backup Drive (si hay cuenta configurada) ──────────────────────────
        val accountName = ctx
            .getSharedPreferences("alushe_prefs", Context.MODE_PRIVATE)
            .getString("google_account", null)

        if (accountName != null) {
            try {
                val drive = DriveBackupService(ctx, accountName)
                val result = drive.subirBase(base, ventas)
                if (result.isSuccess) {
                    db.baseDao().updateDriveFileId(base.id, result.getOrThrow())
                }
            } catch (_: Exception) {
                // Fallo silencioso — el backup local ya está guardado
            }
        }

        return Result.success()
    }

    private fun guardarLocalBackup(
        baseId: Long,
        baseNombre: String,
        ventas: List<com.alushestore.data.local.entities.VentaEntity>
    ) {
        try {
            val backupDir = File(ctx.getExternalFilesDir(null), "backups").apply { mkdirs() }

            // Mantener solo los últimos 7 días
            backupDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(6)
                ?.forEach { it.delete() }

            val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val archivo = File(backupDir, "backup_${fecha}.json")

            val backup = BaseBackup(
                base = com.alushestore.data.local.entities.BaseEntity(
                    id = baseId,
                    nombre = baseNombre
                ),
                ventas = ventas,
                totalMonto = ventas.sumOf { it.monto },
                cantidadVentas = ventas.size
            )

            archivo.writeText(Gson().toJson(backup))
        } catch (_: Exception) {
            // Si falla escritura local, no bloqueamos el worker
        }
    }

    companion object {
        const val WORK_NAME = "alushe_backup_diario"
    }
}
