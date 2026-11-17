package app.pct.clock

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class ConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val forWidget = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        val initial = SettingsStore.load(this, widgetId)

        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    ConfigScreen(initial, forWidget) { apply(it) }
                }
            }
        }
    }

    private fun apply(s: ClockSettings) {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            SettingsStore.save(this, widgetId, s)
            WidgetRenderer.updateAll(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        } else {
            // Opened from the launcher: new defaults, applied to every existing widget.
            SettingsStore.saveDefaults(this, s)
            val mgr = AppWidgetManager.getInstance(this)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(this, ClockWidgetProvider::class.java))
            ids.forEach { SettingsStore.save(this, it, s) }
            WidgetRenderer.updateAll(this)
        }
        finish()
    }
}

private fun fmt(pattern: String, millis: Long): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(initial: ClockSettings, forWidget: Boolean, onApply: (ClockSettings) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("2%Clock", style = MaterialTheme.typography.headlineMedium)
            Preview(s)

            Section("Time") {
                ChipRow(TimeFormat.entries, s.timeFormat, { it.label }) { s = s.copy(timeFormat = it) }
                SwitchRow("Show seconds", s.showSeconds) { s = s.copy(showSeconds = it) }
                SwitchRow("Show AM/PM", s.showAmPm) { s = s.copy(showAmPm = it) }
            }

            Section("Date") {
                SwitchRow("Show date", s.showDate) { s = s.copy(showDate = it) }
                if (s.showDate) {
                    ChipRow(DateStyle.entries, s.dateStyle, { "${it.label}  ${fmt(it.pattern, now)}" }) {
                        s = s.copy(dateStyle = it)
                    }
                    SwitchRow("Include weekday", s.showWeekday) { s = s.copy(showWeekday = it) }
                }
            }

            Section("Extras") {
                SwitchRow("Next alarm", s.showAlarm) { s = s.copy(showAlarm = it) }
                Text("World clocks", style = MaterialTheme.typography.labelLarge)
                for (i in 0..1) {
                    ZonePicker("Zone ${i + 1}", s.zones.getOrNull(i)) { z ->
                        val slots = arrayOf(s.zones.getOrNull(0), s.zones.getOrNull(1))
                        slots[i] = z
                        s = s.copy(zones = slots.filterNotNull())
                    }
                }
            }

            Section("Style") {
                ChipRow(ThemeMode.entries, s.theme, { it.label }) { s = s.copy(theme = it) }
                if (s.theme == ThemeMode.CUSTOM) {
                    ColorPicker("Text color", s.customTextColor) { s = s.copy(customTextColor = it) }
                    ColorPicker("Background color", s.customBgColor) { s = s.copy(customBgColor = it) }
                }
                Text("Background opacity ${s.bgOpacity}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = s.bgOpacity.toFloat(),
                    onValueChange = { s = s.copy(bgOpacity = it.toInt()) },
                    valueRange = 0f..100f,
                )
                Text("Corners", style = MaterialTheme.typography.labelLarge)
                ChipRow(CORNER_NAMES.indices.toList(), s.cornerStyle, { CORNER_NAMES[it] }) {
                    s = s.copy(cornerStyle = it)
                }
                Text("Font", style = MaterialTheme.typography.labelLarge)
                ChipRow(ClockFont.entries, s.font, { it.label }) { s = s.copy(font = it) }
                Text("Alignment", style = MaterialTheme.typography.labelLarge)
                ChipRow(ClockAlign.entries, s.align, { it.label }) { s = s.copy(align = it) }
                Text("Text size ${(s.sizeScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = s.sizeScale,
                    onValueChange = { s = s.copy(sizeScale = it) },
                    valueRange = 0.5f..1.5f,
                )
            }
        }
        Button(
            onClick = { onApply(s) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(if (forWidget) "Apply" else "Apply to all widgets") }
    }
}

@Composable
private fun Preview(s: ClockSettings) {
    val ctx = LocalContext.current
    val night = isSystemInDarkTheme()
    val colors = remember(s.theme, s.customTextColor, s.customBgColor, night) { Palette.resolve(ctx, s, night) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val system24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val family = when (s.font) {
        ClockFont.SANS, ClockFont.LIGHT -> FontFamily.SansSerif
        ClockFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        ClockFont.SERIF -> FontFamily.Serif
        ClockFont.MONO -> FontFamily.Monospace
    }
    val weight = if (s.font == ClockFont.LIGHT) FontWeight.Light else FontWeight.Normal
    val text = Color(colors.text)
    val sub = Color(colors.sub)
    val align = when (s.align) {
        ClockAlign.START -> Alignment.Start
        ClockAlign.CENTER -> Alignment.CenterHorizontally
        ClockAlign.END -> Alignment.End
    }
    val scale = s.sizeScale

    Box(
        Modifier.fillMaxWidth().height(170.dp)
            .clip(RoundedCornerShape(CORNER_DP[s.cornerStyle].dp))
            .background(Color(colors.bg).copy(alpha = s.bgOpacity / 100f))
            .padding(16.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = align,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    fmt(s.timePattern(system24), now),
                    color = text, fontFamily = family, fontWeight = weight, fontSize = (56 * scale).sp,
                )
                if (s.showAmPmNow(system24)) {
                    Text(
                        " " + fmt("a", now),
                        color = text, fontFamily = family, fontWeight = weight,
                        fontSize = (18 * scale).sp, modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            if (s.showDate) {
                Text(
                    fmt(s.datePattern(), now),
                    color = text, fontFamily = family, fontWeight = weight, fontSize = (18 * scale).sp,
                )
            }
            if (s.showAlarm) {
                Text(
                    "⏰ " + fmt(if (system24) "EEE H:mm" else "EEE h:mm a", now),
                    color = sub, fontFamily = family, fontWeight = weight, fontSize = (14 * scale).sp,
                )
            }
            s.zones.forEach { z ->
                val zoneFmt = SimpleDateFormat(s.zonePattern(system24), Locale.getDefault())
                zoneFmt.timeZone = java.util.TimeZone.getTimeZone(z)
                Text(
                    "${cityName(z)}  ${zoneFmt.format(Date(now))}",
                    color = sub, fontFamily = family, fontWeight = weight, fontSize = (14 * scale).sp,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
private fun ZonePicker(title: String, value: String?, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text("$title: ${value?.let { cityName(it) } ?: "None"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onChange(null); open = false })
            ZONES.forEach { z ->
                DropdownMenuItem(text = { Text(cityName(z)) }, onClick = { onChange(z); open = false })
            }
        }
    }
}

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF1B1B1F, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
    0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF8D6E63,
).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(label: String, color: Int, onChange: (Int) -> Unit) {
    var hex by remember(color) { mutableStateOf(String.format("%06X", color and 0xFFFFFF)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SWATCHES.forEach { c ->
                Box(
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            if (c == color) 3.dp else 1.dp,
                            if (c == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .clickable { onChange(c) },
                )
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                hex = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (hex.length == 6) hex.toIntOrNull(16)?.let { onChange(it or 0xFF000000.toInt()) }
            },
            label = { Text("Hex") },
            prefix = { Text("#") },
            singleLine = true,
        )
    }
}
