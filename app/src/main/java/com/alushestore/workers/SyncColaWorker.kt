package com.alushestore.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.remote.CatalogoRemotoService
import com.alushestore.data.remote.SupabaseAuthManager
import com.alushestore.data.repository.ColaMovimientosRepository

/**
 * Vacía la cola de movimientos de stock cuando hay red.
 *
 * Corre aunque la app esté cerrada: si vendiste en el tianguis sin señal y
 * guardaste el teléfono, el descuento llega al almacén igual en cuanto haya
 * internet.
 */
class SyncColaWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val auth = SupabaseAuthManager.getInstance(ctx)
            // Sin emparejar no hay a dónde mandarlo; se reintenta luego porque
            // la cola sigue ahí esperando.
            val token = auth.tokenValido() ?: return Result.retry()

            val cola = ColaMovimientosRepository(
                AlusheDatabase.getDatabase(ctx),
                CatalogoRemotoService()
            )
            val r = cola.procesar(token)

            // Si se cayó la red a medias, que WorkManager lo reintente con su
            // propio backoff en vez de insistir aquí.
            if (r.sinRed) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "alushe_sync_cola"
    }
}
