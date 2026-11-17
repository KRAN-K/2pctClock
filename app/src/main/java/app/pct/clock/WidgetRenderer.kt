package app.pct.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Builds the widget's RemoteViews.
 *
 * Time, date, AM/PM and world clocks are TextClock views, so the system ticks them
 * (and rolls the date over at midnight) with no alarms or services on our side.
 */
object WidgetRenderer {

    private val BG_RES = intArrayOf(
        R.drawable.bg_r0, R.drawable.bg_r12, R.drawable.bg_r24, R.drawable.bg_r36, R.drawable.bg_r48,
    )

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.getAppWidgetIds(ComponentName(ctx, ClockWidgetProvider::class.java))
            .forEach { render(ctx, mgr, it) }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val s = SettingsStore.load(ctx, id)
        val opts = mgr.getAppWidgetOptions(id)
        val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat()
        val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).toFloat()
        mgr.updateAppWidget(id, build(ctx, id, s, w, h))
    }

    fun build(ctx: Context, id: Int, s: ClockSettings, wDp: Float, hDp: Float): RemoteViews {
        val layout = when (s.font) {
            ClockFont.SANS -> R.layout.clock_widget_sans
            ClockFont.LIGHT -> R.layout.clock_widget_light
            ClockFont.CONDENSED -> R.layout.clock_widget_condensed
            ClockFont.SERIF -> R.layout.clock_widget_serif
            ClockFont.MONO -> R.layout.clock_widget_mono
        }
        val rv = RemoteViews(ctx.packageName, layout)

        // ---- colors (day/night pair; API 34+ switches automatically, older APIs at update time)
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val day = Palette.resolve(ctx, s, false)
        val nt = Palette.resolve(ctx, s, true)
        fun color(viewId: Int, method: String, d: Int, n: Int) {
            if (Build.VERSION.SDK_INT >= 34) {
                rv.setColorInt(viewId, method, d, n)
            } else {
                rv.setInt(viewId, method, if (night) n else d)
            }
        }
        for (v in intArrayOf(R.id.time, R.id.ampm, R.id.date, R.id.zone1_clock, R.id.zone2_clock)) {
            color(v, "setTextColor", day.text, nt.text)
        }
        for (v in intArrayOf(R.id.alarm, R.id.zone1_label, R.id.zone2_label)) {
            color(v, "setTextColor", day.sub, nt.sub)
        }

        // ---- background
        rv.setImageViewResource(R.id.bg, BG_RES[s.cornerStyle.coerceIn(0, BG_RES.lastIndex)])
        color(R.id.bg, "setColorFilter", day.bg, nt.bg)
        rv.setInt(R.id.bg, "setImageAlpha", s.bgOpacity.coerceIn(0, 100) * 255 / 100)

        // ---- time
        val sec = if (s.showSeconds) ":ss" else ""
        val t12 = "h:mm$sec"
        val t24 = "HH:mm$sec"
        val (time12, time24) = when (s.timeFormat) {
            TimeFormat.SYSTEM -> t12 to t24
            TimeFormat.H12 -> t12 to t12
            TimeFormat.H24 -> t24 to t24
        }
        rv.setCharSequence(R.id.time, "setFormat12Hour", time12)
        rv.setCharSequence(R.id.time, "setFormat24Hour", time24)

        val ampmOn = s.showAmPm && s.timeFormat != TimeFormat.H24
        val (ap12, ap24) = when (s.timeFormat) {
            TimeFormat.SYSTEM -> "a" to "" // blank in 24h mode, live
            TimeFormat.H12 -> "a" to "a"
            TimeFormat.H24 -> "" to ""
        }
        rv.setViewVisibility(R.id.ampm, if (ampmOn) View.VISIBLE else View.GONE)
        rv.setCharSequence(R.id.ampm, "setFormat12Hour", ap12)
        rv.setCharSequence(R.id.ampm, "setFormat24Hour", ap24)

        // ---- date (TextClock, so it rolls over at midnight by itself)
        rv.setViewVisibility(R.id.date, if (s.showDate) View.VISIBLE else View.GONE)
        val datePattern = s.datePattern()
        rv.setCharSequence(R.id.date, "setFormat12Hour", datePattern)
        rv.setCharSequence(R.id.date, "setFormat24Hour", datePattern)

        // ---- next alarm
        val alarmText = if (s.showAlarm) nextAlarmText(ctx) else null
        rv.setViewVisibility(R.id.alarm, if (alarmText != null) View.VISIBLE else View.GONE)
        if (alarmText != null) rv.setTextViewText(R.id.alarm, alarmText)

        // ---- world clocks
        val zones = s.zones.take(2)
        val rows = intArrayOf(R.id.zone1_row, R.id.zone2_row)
        val labels = intArrayOf(R.id.zone1_label, R.id.zone2_label)
        val clocks = intArrayOf(R.id.zone1_clock, R.id.zone2_clock)
        val (z12, z24) = when (s.timeFormat) {
            TimeFormat.SYSTEM -> "h:mm a" to "HH:mm"
            TimeFormat.H12 -> "h:mm a" to "h:mm a"
            TimeFormat.H24 -> "HH:mm" to "HH:mm"
        }
        for (i in 0..1) {
            val zone = zones.getOrNull(i)
            rv.setViewVisibility(rows[i], if (zone != null) View.VISIBLE else View.GONE)
            if (zone != null) {
                rv.setTextViewText(labels[i], cityName(zone))
                rv.setString(clocks[i], "setTimeZone", zone)
                rv.setCharSequence(clocks[i], "setFormat12Hour", z12)
                rv.setCharSequence(clocks[i], "setFormat24Hour", z24)
            }
        }

        // ---- responsive sizing from the widget's current size
        val extraRows = (if (s.showDate) 1 else 0) + (if (alarmText != null) 1 else 0) + zones.size
        val padDp = 12f
        val availH = (hDp - 2 * padDp).coerceAtLeast(20f)
        val availW = (wDp - 2 * padDp).coerceAtLeast(40f)
        val chars = if (s.showSeconds) 8f else 5f
        val widthFactor = chars * 0.56f + (if (ampmOn) 0.5f else 0f)
        val byHeight = availH / (1.15f + extraRows * 0.40f)
        val byWidth = availW / widthFactor
        val timeSp = (min(byHeight, byWidth) * s.sizeScale).coerceIn(14f, 160f)
        val subSp = (timeSp * 0.30f).coerceIn(11f, 26f)
        rv.setTextViewTextSize(R.id.time, TypedValue.COMPLEX_UNIT_SP, timeSp)
        rv.setTextViewTextSize(R.id.ampm, TypedValue.COMPLEX_UNIT_SP, timeSp * 0.32f)
        rv.setTextViewTextSize(R.id.date, TypedValue.COMPLEX_UNIT_SP, subSp)
        rv.setTextViewTextSize(R.id.alarm, TypedValue.COMPLEX_UNIT_SP, subSp * 0.85f)
        for (v in labels + clocks) rv.setTextViewTextSize(v, TypedValue.COMPLEX_UNIT_SP, subSp * 0.85f)

        val padPx = (padDp * ctx.resources.displayMetrics.density).toInt()
        rv.setViewPadding(R.id.content, padPx, padPx, padPx, padPx)
        val hGravity = when (s.align) {
            ClockAlign.START -> Gravity.START
            ClockAlign.CENTER -> Gravity.CENTER_HORIZONTAL
            ClockAlign.END -> Gravity.END
        }
        rv.setInt(R.id.content, "setGravity", Gravity.CENTER_VERTICAL or hGravity)

        // ---- tap targets: time/alarm -> clock app, date -> calendar, anywhere else -> settings
        fun pending(code: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
            ctx, id * 10 + code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarms = pending(1, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        val calendar = pending(
            2, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        )
        val settings = pending(
            3,
            Intent(ctx, ConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        )
        rv.setOnClickPendingIntent(R.id.bg, settings)
        rv.setOnClickPendingIntent(R.id.content, settings)
        rv.setOnClickPendingIntent(R.id.time_row, alarms)
        rv.setOnClickPendingIntent(R.id.alarm, alarms)
        rv.setOnClickPendingIntent(R.id.date, calendar)

        return rv
    }

    private fun nextAlarmText(ctx: Context): String? {
        val trigger = ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: return null
        val skeleton = if (DateFormat.is24HourFormat(ctx)) "EEEHm" else "EEEjm"
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        return "⏰ " + SimpleDateFormat(pattern, Locale.getDefault()).format(Date(trigger))
    }
}
