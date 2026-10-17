package com.alushestore.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alushestore.updater.AppUpdateManager

/**
 * Worker en segundo plano para verificar periódicamente si hay una nueva versión de la app en GitHub
 * y lanzar una notificación del sistema cuando se detecte una actualización disponible.
 */
class AppUpdateWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            AppUpdateManager.checkForUpdateAndNotify(ctx, showNotificationIfFound = true)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "alushe_update_checker"
    }
}
