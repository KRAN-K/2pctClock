package app.pct.clock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings

class AppEntry(val label: String, val packageName: String)

object TapActions {
    val SYSTEM_SETTINGS = listOf(
        "All settings" to Settings.ACTION_SETTINGS,
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Display" to Settings.ACTION_DISPLAY_SETTINGS,
        "Sound" to Settings.ACTION_SOUND_SETTINGS,
        "Date & time" to Settings.ACTION_DATE_SETTINGS,
        "Battery saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
    )

    fun settingsIntent(ctx: Context, widgetId: Int): Intent =
        Intent(ctx, ConfigActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private val CLOCK_PACKAGES = listOf(
        "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
        "com.oneplus.deskclock", "com.huawei.deskclock", "com.coloros.alarmclock", "com.vivo.alarmclock",
    )

    /**
     * Implicit alarm/timer intents are silently dropped on some phones, which looks like "nothing happens".
     * Resolve the actual clock activity and target it explicitly, then fall back to launching the clock app,
     * and finally to our own settings so a tap always does something.
     */
    private fun clockIntent(ctx: Context, widgetId: Int, action: String): Intent {
        val pm = ctx.packageManager
        val match = pm.queryIntentActivities(Intent(action), 0)
            .firstOrNull { it.activityInfo.packageName != "android" && it.activityInfo.exported }
        if (match != null) return Intent(action).setClassName(match.activityInfo.packageName, match.activityInfo.name)
        for (pkg in CLOCK_PACKAGES) pm.getLaunchIntentForPackage(pkg)?.let { return it }
        return settingsIntent(ctx, widgetId)
    }

    private fun calendarIntent(ctx: Context, widgetId: Int): Intent {
        val pm = ctx.packageManager
        val main = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        if (main.resolveActivity(pm) != null) return main
        val view = Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        if (view.resolveActivity(pm) != null) return view
        for (pkg in listOf("com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar"))
            pm.getLaunchIntentForPackage(pkg)?.let { return it }
        return settingsIntent(ctx, widgetId)
    }

    private fun intentFor(ctx: Context, widgetId: Int, type: TapType, value: String): Intent? = when (type) {
        TapType.ALARMS -> clockIntent(ctx, widgetId, AlarmClock.ACTION_SHOW_ALARMS)
        TapType.TIMERS -> clockIntent(ctx, widgetId, AlarmClock.ACTION_SHOW_TIMERS)
        TapType.CALENDAR -> calendarIntent(ctx, widgetId)
        TapType.APP -> ctx.packageManager.getLaunchIntentForPackage(value)
            ?: clockIntent(ctx, widgetId, AlarmClock.ACTION_SHOW_ALARMS)
        TapType.SYSTEM_SETTING -> Intent(value.ifEmpty { Settings.ACTION_SETTINGS })
        TapType.CLOCK_SETTINGS -> settingsIntent(ctx, widgetId)
        TapType.NONE -> null
    }

    fun tapPending(ctx: Context, widgetId: Int, s: ClockSettings): PendingIntent? =
        intentFor(ctx, widgetId, s.tapType, s.tapValue)?.let { pending(ctx, widgetId * 10 + 1, it) }

    /** Tap on the date; "Nothing" here means it behaves like the rest of the widget. */
    fun datePending(ctx: Context, widgetId: Int, s: ClockSettings): PendingIntent? =
        if (s.dateTapType == TapType.NONE) tapPending(ctx, widgetId, s)
        else intentFor(ctx, widgetId, s.dateTapType, s.dateTapValue)?.let { pending(ctx, widgetId * 10 + 3, it) }

    fun gearPending(ctx: Context, widgetId: Int): PendingIntent =
        pending(ctx, widgetId * 10 + 2, settingsIntent(ctx, widgetId))

    private fun pending(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            ctx, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun launchableApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
