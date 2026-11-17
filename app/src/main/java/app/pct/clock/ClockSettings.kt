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

enum class ClockFont(val label: String) {
    SANS("Sans"),
    LIGHT("Light"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Mono"),
}

enum class ClockAlign(val label: String) { START("Left"), CENTER("Center"), END("Right") }

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
    val font: ClockFont = ClockFont.LIGHT,
    val align: ClockAlign = ClockAlign.START,
    val sizeScale: Float = 1f,
    val zones: List<String> = emptyList(),
) {
    fun use24(system24: Boolean): Boolean = when (timeFormat) {
        TimeFormat.SYSTEM -> system24
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }

    fun timePattern(system24: Boolean): String =
        (if (use24(system24)) "HH:mm" else "h:mm") + (if (showSeconds) ":ss" else "")

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
        put("font", font.name)
        put("align", align.name)
        put("sizeScale", sizeScale.toDouble())
        put("zones", JSONArray(zones))
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
                    font = o.enumOr("font", d.font),
                    align = o.enumOr("align", d.align),
                    sizeScale = o.optDouble("sizeScale", d.sizeScale.toDouble()).toFloat().coerceIn(0.5f, 1.5f),
                    zones = if (zs == null) emptyList() else (0 until zs.length()).map { zs.getString(it) }.take(2),
                )
            }.getOrDefault(ClockSettings())
        }
    }
}
