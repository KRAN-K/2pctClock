package app.pct.clock

import android.content.Context
import android.os.Build

data class WidgetColors(val text: Int, val sub: Int, val bg: Int)

object Palette {
    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    fun resolve(ctx: Context, s: ClockSettings, night: Boolean): WidgetColors {
        val base = themed(ctx, s, night)
        if (s.fontColorMode != FontColorMode.CUSTOM) return base
        val text = s.fontColor or 0xFF000000.toInt()
        return base.copy(text = text, sub = withAlpha(text, 0xCC))
    }

    private fun themed(ctx: Context, s: ClockSettings, night: Boolean): WidgetColors = when (s.theme) {
        ThemeMode.DYNAMIC -> dynamic(ctx, night)
        ThemeMode.LIGHT -> WidgetColors(0xFF1B1B1F.toInt(), 0xFF5F6368.toInt(), 0xFFF5F5F5.toInt())
        ThemeMode.DARK -> WidgetColors(0xFFFFFFFF.toInt(), 0xFFBDC1C6.toInt(), 0xFF121212.toInt())
        ThemeMode.CUSTOM -> {
            val text = s.customTextColor or 0xFF000000.toInt()
            WidgetColors(text, withAlpha(text, 0xCC), s.customBgColor or 0xFF000000.toInt())
        }
    }

    private fun dynamic(ctx: Context, night: Boolean): WidgetColors {
        if (Build.VERSION.SDK_INT < 31) return themed(ctx, ClockSettings(theme = ThemeMode.DARK), night)
        return if (night) {
            WidgetColors(
                ctx.getColor(android.R.color.system_accent1_100),
                ctx.getColor(android.R.color.system_accent2_200),
                ctx.getColor(android.R.color.system_neutral1_900),
            )
        } else {
            WidgetColors(
                ctx.getColor(android.R.color.system_accent1_900),
                ctx.getColor(android.R.color.system_accent2_700),
                ctx.getColor(android.R.color.system_accent2_100),
            )
        }
    }
}
