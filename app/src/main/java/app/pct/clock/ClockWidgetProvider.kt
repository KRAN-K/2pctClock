package app.pct.clock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetRenderer.render(context, appWidgetManager, it) }
        ClockEngineService.sync(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetRenderer.render(context, appWidgetManager, appWidgetId)
        ClockEngineService.sync(context, refresh = true)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        SettingsStore.delete(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, ClockEngineService::class.java))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> Unit // handled by onUpdate
            else -> {
                WidgetRenderer.updateAll(context)
                ClockEngineService.sync(context)
            }
        }
    }
}
