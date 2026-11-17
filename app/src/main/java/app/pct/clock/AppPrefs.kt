package app.pct.clock

import android.content.Context

/** Look of the settings screen itself (not the widget). */
enum class AppThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED black")
}

class AppAccent(val key: String, val label: String, val seed: Int)

object AppPrefs {
    val ACCENTS = listOf(
        AppAccent("dynamic", "Material You", 0xFF6750A4.toInt()),
        AppAccent("blue", "Blue", 0xFF1E88E5.toInt()),
        AppAccent("teal", "Teal", 0xFF00897B.toInt()),
        AppAccent("green", "Green", 0xFF43A047.toInt()),
        AppAccent("orange", "Orange", 0xFFFB8C00.toInt()),
        AppAccent("red", "Red", 0xFFE53935.toInt()),
        AppAccent("pink", "Pink", 0xFFD81B60.toInt()),
        AppAccent("purple", "Purple", 0xFF8E24AA.toInt()),
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun themeMode(ctx: Context): AppThemeMode =
        runCatching { AppThemeMode.valueOf(prefs(ctx).getString("theme", "SYSTEM")!!) }.getOrDefault(AppThemeMode.SYSTEM)

    fun accent(ctx: Context): AppAccent {
        val key = prefs(ctx).getString("accent", "dynamic")
        return ACCENTS.firstOrNull { it.key == key } ?: ACCENTS[0]
    }

    fun setThemeMode(ctx: Context, m: AppThemeMode) = prefs(ctx).edit().putString("theme", m.name).apply()
    fun setAccent(ctx: Context, a: AppAccent) = prefs(ctx).edit().putString("accent", a.key).apply()
}
