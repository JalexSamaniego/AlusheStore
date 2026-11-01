package com.alushestore.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.alushestore.MainActivity
import com.alushestore.R
import com.alushestore.data.local.ActiveUserManager
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.ui.components.toMXN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object AlusheWidgetUpdater {

    const val EXTRA_ACTION = "extra_action"
    const val ACTION_POS_SCANNER = "pos_scanner"
    const val ACTION_TIANGUIS = "tianguis"

    fun anclarWidget(context: Context, esExtendido: Boolean): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val provider = if (esExtendido) {
                    ComponentName(context, AlusheWidgetExtended::class.java)
                } else {
                    ComponentName(context, AlusheWidgetCompact::class.java)
                }
                appWidgetManager.requestPinAppWidget(provider, null, null)
                return true
            }
        }
        return false
    }

    fun actualizarWidgets(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)

        val compactIds = appWidgetManager.getAppWidgetIds(
            ComponentName(appContext, AlusheWidgetCompact::class.java)
        )
        val extendedIds = appWidgetManager.getAppWidgetIds(
            ComponentName(appContext, AlusheWidgetExtended::class.java)
        )

        if (compactIds.isEmpty() && extendedIds.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AlusheDatabase.getDatabase(appContext)
                val baseActiva = db.baseDao().getBaseActivaSync()
                val vendedorActivo = ActiveUserManager.getInstance(appContext).vendedorActivo.value

                var totalBase = 0.0
                var totalHoy = 0.0
                val nombreBase = baseActiva?.nombre ?: "Sin base activa"

                if (baseActiva != null) {
                    val ventas = db.ventaDao().getVentasByBaseSync(baseActiva.id)
                    totalBase = ventas.sumOf { it.monto }

                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val inicioHoy = cal.timeInMillis
                    totalHoy = ventas.filter { it.fecha >= inicioHoy }.sumOf { it.monto }
                }

                // Actualizar widgets compactos (4x1)
                compactIds.forEach { widgetId ->
                    val views = RemoteViews(appContext.packageName, R.layout.widget_alushe_compact)
                    views.setTextViewText(R.id.tv_widget_base, nombreBase)
                    views.setTextViewText(R.id.tv_widget_total_hoy, "Hoy: ${totalHoy.toMXN()}")

                    // Click en + Venta -> QuickSaleActivity
                    val intentVenta = Intent(appContext, QuickSaleActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val piVenta = PendingIntent.getActivity(
                        appContext, 101, intentVenta,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_venta, piVenta)

                    // Click en 📷 Escáner -> MainActivity con acción POS
                    val intentScan = Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_ACTION, ACTION_POS_SCANNER)
                    }
                    val piScan = PendingIntent.getActivity(
                        appContext, 102, intentScan,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_scan, piScan)

                    // Click en el root del widget -> abrir App principal
                    val intentApp = Intent(appContext, MainActivity::class.java)
                    val piApp = PendingIntent.getActivity(
                        appContext, 100, intentApp,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_compact_root, piApp)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }

                // Actualizar widgets extendidos (4x2)
                extendedIds.forEach { widgetId ->
                    val views = RemoteViews(appContext.packageName, R.layout.widget_alushe_extended)
                    views.setTextViewText(R.id.tv_extended_base, "• $nombreBase")
                    views.setTextViewText(R.id.tv_extended_vendedor, "👤 $vendedorActivo")
                    views.setTextViewText(R.id.tv_extended_total_base, totalBase.toMXN())
                    views.setTextViewText(R.id.tv_extended_total_hoy, totalHoy.toMXN())

                    // Click en ⚡ Venta Rápida -> QuickSaleActivity
                    val intentVenta = Intent(appContext, QuickSaleActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val piVenta = PendingIntent.getActivity(
                        appContext, 201, intentVenta,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_extended_quick_sale, piVenta)

                    // Click en 📷 POS -> MainActivity con acción POS
                    val intentScan = Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_ACTION, ACTION_POS_SCANNER)
                    }
                    val piScan = PendingIntent.getActivity(
                        appContext, 202, intentScan,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_extended_scan, piScan)

                    // Click en 🏪 Tianguis -> MainActivity con acción Tianguis
                    val intentTianguis = Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_ACTION, ACTION_TIANGUIS)
                    }
                    val piTianguis = PendingIntent.getActivity(
                        appContext, 203, intentTianguis,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_extended_tianguis, piTianguis)

                    // Click en el root del widget -> abrir App principal
                    val intentApp = Intent(appContext, MainActivity::class.java)
                    val piApp = PendingIntent.getActivity(
                        appContext, 200, intentApp,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_extended_root, piApp)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
