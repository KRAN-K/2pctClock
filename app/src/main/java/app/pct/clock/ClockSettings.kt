package app.pct.clock

import org.json.JSONArray
import org.json.JSONObject

enum class TimeFormat(val label: String) { SYSTEM("System"), H12("12-hour"), H24("24-hour") }

enum class DateStyle(val label: String, val pattern: String) {
    MDY("M/D/YYYY", "M/d/yyyy"),
    MDY_PADDED("MM/DD/YYYY", "MM/dd/yyyy"),
    MDY_SHORT("M/D/YY", "M/d/yy"),
    DMY("D/M/YYYY", "d/M/yyyy"),
    ISO("YYYY-MM-DD", "yyyy-MM-dd"),
    MEDIUM("Mon D, YYYY", "MMM d, yyyy"),
    LONG("Month D", "MMMM d"),
}

enum class ThemeMode(val label: String) {
    DYNAMIC("Material You"),
    LIGHT("Light"),
    DARK("Dark"),
    CUSTOM("Custom"),
}

enum class ClockAlign(val label: String) { START("Left"), CENTER("Center"), END("Right") }

enum class LayoutStyle(val label: String) { STACKED("Stacked"), SPLIT("Split: time | date") }

enum class DigitAnim(val label: String, val durationMs: Int) {
    NONE("None", 0), ROLL("Roll", 450), FADE("Fade", 450), POP("Pop", 500),
    FLICKER("Flicker", 600), FIRE("Fire", 900), BURN("Burn", 1300), WATER("Water", 900),
}

enum class AnimUnit(val label: String) { MINUTE("Every minute"), SECOND("Every second") }

enum class BorderStyle(val label: String, val animated: Boolean) {
    NONE("None", false),
    SOLID("Solid", false),
    DOUBLE("Double", false),
    GLOW("Neon glow", false),
    RAINBOW("Rainbow", true),
    PULSE("Pulse", true),
    FIRE("Fire", true),
}

enum class FontColorMode(val label: String) {
    THEME("Theme"), CUSTOM("Custom"), GRADIENT("Gradient"), RAINBOW("Rainbow (animated)"),
}

enum class BgMode(val label: String) {
    THEME("Solid"), TRANSPARENT("Transparent"), GRADIENT("Gradient"), IMAGE("Image"),
}

enum class TapType(val label: String) {
    CLOCK("Clock app"),
    ALARMS("Alarms"),
    TIMERS("Timers"),
    CALENDAR("Calendar"),
    APP("An app"),
    SYSTEM_SETTING("A system setting"),
    CLOCK_SETTINGS("2%Clock settings"),
    NONE("Nothing"),
}

val CORNER_DP = intArrayOf(0, 12, 24, 36, 48)
val CORNER_NAMES = listOf("Square", "Soft", "Round", "Rounder", "Pill")

val ZONES = listOf(
    "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles", "America/Denver",
    "America/Phoenix", "America/Chicago", "America/New_York", "America/Toronto",
    "America/Mexico_City", "America/Sao_Paulo", "UTC", "Europe/London", "Europe/Dublin",
    "Europe/Paris", "Europe/Berlin", "Europe/Madrid", "Europe/Rome", "Europe/Athens",
    "Europe/Moscow", "Africa/Lagos", "Africa/Cairo", "Africa/Johannesburg", "Asia/Dubai",
    "Asia/Karachi", "Asia/Kolkata", "Asia/Bangkok", "Asia/Shanghai", "Asia/Hong_Kong",
    "Asia/Singapore", "Asia/Seoul", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland",
)

fun cityName(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

data class ClockSettings(
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showSeconds: Boolean = false,
    val showAmPm: Boolean = true,
    val showDate: Boolean = true,
    val dateStyle: DateStyle = DateStyle.MDY,
    val showWeekday: Boolean = false,
    val showAlarm: Boolean = true,
    val theme: ThemeMode = ThemeMode.DYNAMIC,
    val customTextColor: Int = 0xFFFFFFFF.toInt(),
    val customBgColor: Int = 0xFF1B1B1F.toInt(),
    val bgOpacity: Int = 85,
    val cornerStyle: Int = 2,
    val fontKey: String = "sys:light",
    val fontColorMode: FontColorMode = FontColorMode.THEME,
    val fontColor: Int = 0xFFFFD54F.toInt(),
    val fontGradientKey: String = "sunset",
    val align: ClockAlign = ClockAlign.START,
    val sizeScale: Float = 1f,
    val layout: LayoutStyle = LayoutStyle.STACKED,
    val zones: List<String> = emptyList(),
    // background
    val bgMode: BgMode = BgMode.THEME,
    val gradientKey: String = "sunset",
    val bgImage: String? = null,
    val bgDim: Int = 25,
    // border
    val border: BorderStyle = BorderStyle.NONE,
    val borderWidth: Int = 3,
    val borderColor: Int = 0xFFFF7A00.toInt(),
    // motion
    val animation: DigitAnim = DigitAnim.NONE,
    val animUnit: AnimUnit = AnimUnit.MINUTE,
    // taps
    val tapType: TapType = TapType.CLOCK,
    val tapValue: String = "",
    /** What tapping the date does; NONE means "same as the widget tap". */
    val dateTapType: TapType = TapType.CALENDAR,
    val dateTapValue: String = "",
    val showGear: Boolean = true,
) {
    /** Anything the plain RemoteViews/TextClock widget can't draw needs the bitmap renderer. */
    fun needsStyled(): Boolean =
        !fontKey.startsWith("sys:") || fontColorMode == FontColorMode.GRADIENT ||
            fontColorMode == FontColorMode.RAINBOW || bgMode == BgMode.GRADIENT || bgMode == BgMode.IMAGE || animation != DigitAnim.NONE ||
            border != BorderStyle.NONE || layout == LayoutStyle.SPLIT

    /** True when the widget must redraw continuously (not just on digit changes). */
    fun animatedStyle(): Boolean = border.animated || fontColorMode == FontColorMode.RAINBOW

    fun use24(system24: Boolean): Boolean = when (timeFormat) {
        TimeFormat.SYSTEM -> system24
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }

    private fun secondsOn(): Boolean = showSeconds || (animation != DigitAnim.NONE && animUnit == AnimUnit.SECOND)

    fun timePattern(system24: Boolean): String =
        (if (use24(system24)) "HH:mm" else "h:mm") + (if (secondsOn()) ":ss" else "")

    fun zonePattern(system24: Boolean): String = if (use24(system24)) "HH:mm" else "h:mm a"

    fun showAmPmNow(system24: Boolean): Boolean = showAmPm && !use24(system24)

    fun datePattern(): String = (if (showWeekday) "EEE, " else "") + dateStyle.pattern

    fun toJson(): String = JSONObject().apply {
        put("timeFormat", timeFormat.name)
        put("showSeconds", showSeconds)
        put("showAmPm", showAmPm)
        put("showDate", showDate)
        put("dateStyle", dateStyle.name)
        put("showWeekday", showWeekday)
        put("showAlarm", showAlarm)
        put("theme", theme.name)
        put("customTextColor", customTextColor)
        put("customBgColor", customBgColor)
        put("bgOpacity", bgOpacity)
        put("cornerStyle", cornerStyle)
        put("fontKey", fontKey)
        put("fontColorMode", fontColorMode.name)
        put("fontColor", fontColor)
        put("fontGradientKey", fontGradientKey)
        put("align", align.name)
        put("sizeScale", sizeScale.toDouble())
        put("layout", layout.name)
        put("dateTapType", dateTapType.name)
        put("dateTapValue", dateTapValue)
        put("zones", JSONArray(zones))
        put("bgMode", bgMode.name)
        put("gradientKey", gradientKey)
        put("bgImage", bgImage ?: JSONObject.NULL)
        put("bgDim", bgDim)
        put("border", border.name)
        put("borderWidth", borderWidth)
        put("borderColor", borderColor)
        put("animation", animation.name)
        put("animUnit", animUnit.name)
        put("tapType", tapType.name)
        put("tapValue", tapValue)
        put("showGear", showGear)
    }.toString()

    companion object {
        private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, def: T): T =
            runCatching { enumValueOf<T>(optString(key, def.name)) }.getOrDefault(def)

        fun fromJson(json: String?): ClockSettings {
            if (json == null) return ClockSettings()
            return runCatching {
                val d = ClockSettings()
                val o = JSONObject(json)
                val zs = o.optJSONArray("zones")
                // v0.1 stored an enum under "font"; map it to the new key
                val fontKey = if (o.has("fontKey")) o.getString("fontKey")
                else "sys:" + o.optString("font", "LIGHT").lowercase()
                ClockSettings(
                    timeFormat = o.enumOr("timeFormat", d.timeFormat),
                    showSeconds = o.optBoolean("showSeconds", d.showSeconds),
                    showAmPm = o.optBoolean("showAmPm", d.showAmPm),
                    showDate = o.optBoolean("showDate", d.showDate),
                    dateStyle = o.enumOr("dateStyle", d.dateStyle),
                    showWeekday = o.optBoolean("showWeekday", d.showWeekday),
                    showAlarm = o.optBoolean("showAlarm", d.showAlarm),
                    theme = o.enumOr("theme", d.theme),
                    customTextColor = o.optInt("customTextColor", d.customTextColor),
                    customBgColor = o.optInt("customBgColor", d.customBgColor),
                    bgOpacity = o.optInt("bgOpacity", d.bgOpacity).coerceIn(0, 100),
                    cornerStyle = o.optInt("cornerStyle", d.cornerStyle).coerceIn(0, CORNER_DP.size - 1),
                    fontKey = fontKey,
                    fontColorMode = o.enumOr("fontColorMode", d.fontColorMode),
                    fontColor = o.optInt("fontColor", d.fontColor),
                    fontGradientKey = o.optString("fontGradientKey", d.fontGradientKey),
                    align = o.enumOr("align", d.align),
                    sizeScale = o.optDouble("sizeScale", d.sizeScale.toDouble()).toFloat().coerceIn(0.5f, 1.5f),
                    layout = o.enumOr("layout", d.layout),
                    dateTapType = o.enumOr("dateTapType", d.dateTapType),
                    dateTapValue = o.optString("dateTapValue", d.dateTapValue),
                    zones = if (zs == null) emptyList() else (0 until zs.length()).map { zs.getString(it) }.take(2),
                    bgMode = o.enumOr("bgMode", d.bgMode),
                    gradientKey = o.optString("gradientKey", d.gradientKey),
                    bgImage = if (o.isNull("bgImage")) null else o.optString("bgImage").ifEmpty { null },
                    bgDim = o.optInt("bgDim", d.bgDim).coerceIn(0, 80),
                    border = o.enumOr("border", d.border),
                    borderWidth = o.optInt("borderWidth", d.borderWidth).coerceIn(1, 10),
                    borderColor = o.optInt("borderColor", d.borderColor),
                    animation = o.enumOr("animation", d.animation),
                    animUnit = o.enumOr("animUnit", d.animUnit),
                    tapType = o.enumOr("tapType", d.tapType),
                    tapValue = o.optString("tapValue", d.tapValue),
                    showGear = o.optBoolean("showGear", d.showGear),
                )
            }.getOrDefault(ClockSettings())
        }
    }
}
