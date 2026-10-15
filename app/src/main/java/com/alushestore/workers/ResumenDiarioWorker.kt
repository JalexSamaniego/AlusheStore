package com.alushestore.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alushestore.MainActivity
import com.alushestore.R
import com.alushestore.data.local.AlusheDatabase
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.*

class ResumenDiarioWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = AlusheDatabase.getDatabase(ctx)

        // Base activa
        val base = db.baseDao().getBaseActiva().first() ?: return Result.success()

        // Ventas de hoy
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val hoyInicio = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val hoyFin = cal.timeInMillis

        val ventasHoy = db.ventaDao().getVentasHoy(base.id, hoyInicio, hoyFin).first()

        // Sin ventas → no molestamos
        if (ventasHoy.isEmpty()) return Result.success()

        val total = ventasHoy.sumOf { it.monto }
        val fmt = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_RESUMEN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Resumen del día — ${base.nombre}")
            .setContentText("${ventasHoy.size} ventas · ${fmt.format(total)} total")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Hoy registraste ${ventasHoy.size} ventas por un total de ${fmt.format(total)}. ¡Buen trabajo! 🛍️")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_RESUMEN, notification)

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "alushe_resumen_diario"
        const val CHANNEL_RESUMEN = "alushe_resumen"
        const val NOTIF_ID_RESUMEN = 1001
    }
}
