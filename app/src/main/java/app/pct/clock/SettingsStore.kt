package app.pct.clock

import android.content.Context

object SettingsStore {
    private const val PREFS = "clock_prefs"
    private const val DEFAULTS = "defaults"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Per-widget settings, falling back to the saved defaults, then built-in defaults. */
    fun load(ctx: Context, widgetId: Int): ClockSettings {
        val p = prefs(ctx)
        return ClockSettings.fromJson(p.getString("w_$widgetId", null) ?: p.getString(DEFAULTS, null))
    }

    fun save(ctx: Context, widgetId: Int, s: ClockSettings) {
        prefs(ctx).edit().putString("w_$widgetId", s.toJson()).apply()
    }

    fun saveDefaults(ctx: Context, s: ClockSettings) {
        prefs(ctx).edit().putString(DEFAULTS, s.toJson()).apply()
    }

    fun delete(ctx: Context, widgetIds: IntArray) {
        val e = prefs(ctx).edit()
        widgetIds.forEach { e.remove("w_$it") }
        e.apply()
    }
}
