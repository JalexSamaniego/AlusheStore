package com.alushestore.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class AlusheWidgetCompact : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        AlusheWidgetUpdater.actualizarWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AlusheWidgetUpdater.actualizarWidgets(context)
    }
}
