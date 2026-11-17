package app.pct.clock

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pending: ClockSettings? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pending?.let { commit(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val forWidget = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        val initial = SettingsStore.load(this, widgetId)

        setContent {
            val ctx = LocalContext.current
            var mode by remember { mutableStateOf(AppPrefs.themeMode(ctx)) }
            var accent by remember { mutableStateOf(AppPrefs.accent(ctx)) }
            val dark = when (mode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                else -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MaterialTheme(colorScheme = appScheme(ctx, dark, mode == AppThemeMode.AMOLED, accent)) {
                Surface(Modifier.fillMaxSize()) {
                    ConfigScreen(
                        initial, forWidget, mode, accent,
                        onMode = { mode = it; AppPrefs.setThemeMode(ctx, it) },
                        onAccent = { accent = it; AppPrefs.setAccent(ctx, it) },
                    ) { apply(it) }
                }
            }
        }
    }

    private fun apply(s: ClockSettings) {
        val needsNotif = s.needsStyled() && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotif) {
            pending = s
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            commit(s)
        }
    }

    private fun commit(s: ClockSettings) {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            SettingsStore.save(this, widgetId, s)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        } else {
            // Opened from the launcher: new defaults, applied to every existing widget.
            SettingsStore.saveDefaults(this, s)
            val mgr = AppWidgetManager.getInstance(this)
            mgr.getAppWidgetIds(ComponentName(this, ClockWidgetProvider::class.java))
                .forEach { SettingsStore.save(this, it, s) }
        }
        WidgetRenderer.updateAll(this)
        ClockEngineService.sync(this, refresh = true)
        finish()
    }
}

private fun accentScheme(seed: Int, dark: Boolean): ColorScheme {
    val a = Color(seed)
    val white = Color.White
    val black = Color.Black
    return if (!dark) {
        lightColorScheme(
            primary = a, onPrimary = white,
            primaryContainer = lerp(a, white, 0.82f), onPrimaryContainer = lerp(a, black, 0.6f),
            secondary = lerp(a, black, 0.15f), onSecondary = white,
            secondaryContainer = lerp(a, white, 0.78f), onSecondaryContainer = lerp(a, black, 0.65f),
            tertiary = a, background = Color(0xFFFCFCFC), surface = Color(0xFFFCFCFC),
        )
    } else {
        darkColorScheme(
            primary = lerp(a, white, 0.35f), onPrimary = lerp(a, black, 0.75f),
            primaryContainer = lerp(a, black, 0.55f), onPrimaryContainer = lerp(a, white, 0.85f),
            secondary = lerp(a, white, 0.3f), onSecondary = lerp(a, black, 0.75f),
            secondaryContainer = lerp(a, black, 0.6f), onSecondaryContainer = lerp(a, white, 0.85f),
            tertiary = lerp(a, white, 0.35f), background = Color(0xFF121212), surface = Color(0xFF121212),
        )
    }
}

private fun appScheme(ctx: Context, dark: Boolean, amoled: Boolean, accent: AppAccent): ColorScheme {
    var cs = if (accent.key == "dynamic" && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        accentScheme(accent.seed, dark)
    }
    if (amoled && dark) cs = cs.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF151515))
    return cs
}

private fun fmt(pattern: String, millis: Long): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

private val TABS = listOf("Clock", "Look", "Font", "Motion", "Taps", "App")

@Composable
fun ConfigScreen(
    initial: ClockSettings,
    forWidget: Boolean,
    appMode: AppThemeMode,
    appAccent: AppAccent,
    onMode: (AppThemeMode) -> Unit,
    onAccent: (AppAccent) -> Unit,
    onApply: (ClockSettings) -> Unit,
) {
    var s by remember { mutableStateOf(initial) }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text(
            "2%Clock", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
        )
        Box(Modifier.padding(16.dp)) { LivePreview(s) }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val set: (ClockSettings) -> Unit = { s = it }
            when (tab) {
                0 -> ClockTab(s, set)
                1 -> LookTab(s, set)
                2 -> FontTab(s, set)
                3 -> MotionTab(s, set)
                4 -> TapsTab(s, set)
                else -> AppTab(appMode, appAccent, onMode, onAccent)
            }
        }
        Button(
            onClick = { onApply(s) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(if (forWidget) "Apply" else "Apply to all widgets") }
    }
}

// ---------------------------------------------------------------------- live preview

@Composable
private fun LivePreview(s: ClockSettings) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
        val wDp = maxWidth.value
        val frame by produceState<ImageBitmap?>(null, s, wDp) {
            // Preview clock jumps forward one unit every 3 seconds so the effects are visible.
            val start = System.currentTimeMillis()
            val unitMs = if (s.animUnit == AnimUnit.SECOND) 1_000L else 60_000L
            val secondsShown = s.timePattern(false).endsWith(":ss")
            var lastText = ""
            var prev = ""
            var animStart = 0L
            while (true) {
                val real = System.currentTimeMillis()
                val step = (real - start) / 3000L
                val fake = start + step * unitMs + (real - start) % 3000L
                val text = StyledRenderer.timeString(ctx, s, fake)
                val minuteMode = s.animUnit == AnimUnit.MINUTE && secondsShown
                val changed = lastText.isNotEmpty() &&
                    (if (minuteMode) text.dropLast(3) != lastText.dropLast(3) else text != lastText)
                if (changed && s.animation != DigitAnim.NONE) {
                    prev = lastText
                    animStart = real
                }
                lastText = text
                val since = real - animStart
                val anim = if (animStart > 0 && since < 700) AnimFrame(prev, since / 700f) else null
                val bmp = withContext(Dispatchers.Default) {
                    StyledRenderer.render(ctx, s, wDp, 150f, fake, anim, (real % 600_000L) / 1000f, dark)
                }
                value = bmp.asImageBitmap()
                delay(if (anim != null || s.border.animated) 60L else if (secondsShown) 250L else 500L)
            }
        }
        frame?.let {
            Image(it, contentDescription = "Preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

// ---------------------------------------------------------------------- tabs

@Composable
private fun ClockTab(s: ClockSettings, set: (ClockSettings) -> Unit) {
    val now = System.currentTimeMillis()
    Section("Time") {
        ChipRow(TimeFormat.entries, s.timeFormat, { it.label }) { set(s.copy(timeFormat = it)) }
        SwitchRow("Show seconds", s.showSeconds) { set(s.copy(showSeconds = it)) }
        SwitchRow("Show AM/PM", s.showAmPm) { set(s.copy(showAmPm = it)) }
    }
    Section("Date") {
        SwitchRow("Show date", s.showDate) { set(s.copy(showDate = it)) }
        if (s.showDate) {
            ChipRow(DateStyle.entries, s.dateStyle, { "${it.label}  ${fmt(it.pattern, now)}" }) {
                set(s.copy(dateStyle = it))
            }
            SwitchRow("Include weekday", s.showWeekday) { set(s.copy(showWeekday = it)) }
        }
    }
    Section("Extras") {
        SwitchRow("Next alarm", s.showAlarm) { set(s.copy(showAlarm = it)) }
        Text("World clocks", style = MaterialTheme.typography.labelLarge)
        for (i in 0..1) {
            ZonePicker("Zone ${i + 1}", s.zones.getOrNull(i)) { z ->
                val slots = arrayOf(s.zones.getOrNull(0), s.zones.getOrNull(1))
                slots[i] = z
                set(s.copy(zones = slots.filterNotNull()))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookTab(s: ClockSettings, set: (ClockSettings) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { runCatching { BgStore.saveFromUri(ctx, uri) } }
            busy = false
            val name = r.getOrNull()
            if (name != null) set(s.copy(bgMode = BgMode.IMAGE, bgImage = name)) else toast("Could not read that image")
        }
    }

    Section("Colors") {
        ChipRow(ThemeMode.entries, s.theme, { it.label }) { set(s.copy(theme = it)) }
        if (s.theme == ThemeMode.CUSTOM) {
            ColorPicker("Text color", s.customTextColor) { set(s.copy(customTextColor = it)) }
            ColorPicker("Background color", s.customBgColor) { set(s.copy(customBgColor = it)) }
        }
    }

    Section("Background") {
        ChipRow(BgMode.entries, s.bgMode, { it.label }) { set(s.copy(bgMode = it)) }
        if (s.bgMode == BgMode.GRADIENT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BgStore.GRADIENTS.forEach { g ->
                    val sel = g.key == s.gradientKey
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(width = 64.dp, height = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(g.colors.map { Color(it) }))
                                .border(
                                    if (sel) 3.dp else 1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { set(s.copy(gradientKey = g.key)) },
                        )
                        Text(g.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (s.bgMode == BgMode.IMAGE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose photo") }
            }
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text("Or paste an image link") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(enabled = url.isNotBlank() && !busy, onClick = {
                scope.launch {
                    busy = true
                    val r = withContext(Dispatchers.IO) { runCatching { BgStore.saveFromUrl(ctx, url) } }
                    busy = false
                    val name = r.getOrNull()
                    if (name != null) {
                        set(s.copy(bgMode = BgMode.IMAGE, bgImage = name))
                        url = ""
                    } else {
                        toast(r.exceptionOrNull()?.message ?: "Could not load that image")
                    }
                }
            }) { Text(if (busy) "Loading..." else "Use link") }
            Text(
                if (s.bgImage == null) "No image chosen yet" else "Image set",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Dim image ${s.bgDim}%", style = MaterialTheme.typography.labelLarge)
            Slider(value = s.bgDim.toFloat(), onValueChange = { set(s.copy(bgDim = it.toInt())) }, valueRange = 0f..80f)
        }
        Text("Opacity ${s.bgOpacity}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = s.bgOpacity.toFloat(), onValueChange = { set(s.copy(bgOpacity = it.toInt())) }, valueRange = 0f..100f)
        Text("Corners", style = MaterialTheme.typography.labelLarge)
        ChipRow(CORNER_NAMES.indices.toList(), s.cornerStyle, { CORNER_NAMES[it] }) { set(s.copy(cornerStyle = it)) }
    }

    Section("Border") {
        ChipRow(BorderStyle.entries, s.border, { it.label + if (it.animated) " (animated)" else "" }) {
            set(s.copy(border = it))
        }
        if (s.border != BorderStyle.NONE) {
            Text("Width ${s.borderWidth}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = s.borderWidth.toFloat(), onValueChange = { set(s.copy(borderWidth = it.toInt())) },
                valueRange = 1f..10f,
            )
        }
        if (s.border in listOf(BorderStyle.SOLID, BorderStyle.DOUBLE, BorderStyle.GLOW, BorderStyle.PULSE)) {
            ColorPicker("Border color", s.borderColor) { set(s.copy(borderColor = it)) }
        }
    }

    Section("Layout") {
        Text("Alignment", style = MaterialTheme.typography.labelLarge)
        ChipRow(ClockAlign.entries, s.align, { it.label }) { set(s.copy(align = it)) }
        Text("Text size ${(s.sizeScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = s.sizeScale, onValueChange = { set(s.copy(sizeScale = it)) }, valueRange = 0.5f..1.5f)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontTab(s: ClockSettings, set: (ClockSettings) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fonts by remember { mutableStateOf(FontStore.imported(ctx)) }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    fun finishImport(r: Result<List<String>>) {
        busy = false
        r.onSuccess { keys ->
            fonts = FontStore.imported(ctx)
            set(s.copy(fontKey = keys.firstOrNull { it.contains("regular", ignoreCase = true) } ?: keys.first()))
            toast("Imported ${keys.size} font(s)")
        }.onFailure { toast(it.message ?: "Import failed") }
    }

    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            finishImport(withContext(Dispatchers.IO) { runCatching { FontStore.importFromUri(ctx, uri) } })
        }
    }

    @Composable
    fun fontChips(list: List<FontStore.Entry>) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            list.forEach { e ->
                FilterChip(
                    selected = s.fontKey == e.key,
                    onClick = { set(s.copy(fontKey = e.key)) },
                    label = { Text(e.label, fontFamily = FontFamily(FontStore.typeface(ctx, e.key))) },
                )
            }
        }
    }

    Section("Built-in") { fontChips(FontStore.SYSTEM) }
    Section("Bundled (open license)") { fontChips(FontStore.BUNDLED) }
    Section("Your fonts") {
        if (fonts.isEmpty()) {
            Text("Nothing imported yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            fontChips(fonts)
            if (s.fontKey.startsWith("file:")) {
                OutlinedButton(onClick = {
                    FontStore.remove(ctx, s.fontKey)
                    fonts = FontStore.imported(ctx)
                    set(s.copy(fontKey = "sys:light"))
                }) { Text("Remove selected font") }
            }
        }
        Text(
            "Get fonts from dafont.com, then import the .zip or .ttf here, or paste the font's page link " +
                "(like dafont.com/carta-bianca.font). Check each font's license before using it.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dafont.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Browse dafont") }
            Button(enabled = !busy, onClick = { pickFont.launch(arrayOf("*/*")) }) { Text("Import file") }
        }
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true,
            label = { Text("Font link") }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(enabled = url.isNotBlank() && !busy, onClick = {
            scope.launch {
                busy = true
                finishImport(withContext(Dispatchers.IO) { runCatching { FontStore.importFromUrl(ctx, url) } })
                url = ""
            }
        }) { Text(if (busy) "Importing..." else "Import link") }
    }
}

@Composable
private fun MotionTab(s: ClockSettings, set: (ClockSettings) -> Unit) {
    Section("Digit effect") {
        ChipRow(DigitAnim.entries, s.animation, { it.label }) { set(s.copy(animation = it)) }
        if (s.animation != DigitAnim.NONE) {
            Text("Play when", style = MaterialTheme.typography.labelLarge)
            ChipRow(AnimUnit.entries, s.animUnit, { it.label }) { set(s.copy(animUnit = it)) }
            if (s.animUnit == AnimUnit.SECOND) {
                Text("Seconds are shown on the clock while this is on.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text(
        "Effects, animated borders, custom fonts and backgrounds are drawn by a small background service " +
            "that only runs while the screen is on. Android shows a low-priority notification for it. " +
            "Plain clocks use the system clock view and use no extra battery.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Animated borders are on the Look tab.", style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TapsTab(s: ClockSettings, set: (ClockSettings) -> Unit) {
    Section("When you tap the widget, open") {
        ChipRow(TapType.entries, s.tapType, { it.label }) { set(s.copy(tapType = it, tapValue = "")) }
        when (s.tapType) {
            TapType.APP -> AppPicker(s.tapValue) { set(s.copy(tapValue = it)) }
            TapType.SYSTEM_SETTING -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TapActions.SYSTEM_SETTINGS.forEach { (label, action) ->
                    FilterChip(
                        selected = s.tapValue == action || (s.tapValue.isEmpty() && action == TapActions.SYSTEM_SETTINGS[0].second),
                        onClick = { set(s.copy(tapValue = action)) },
                        label = { Text(label) },
                    )
                }
            }
            else -> Unit
        }
    }
    Section("Quick settings") {
        SwitchRow("Show gear icon on widget", s.showGear) { set(s.copy(showGear = it)) }
        Text(
            "The gear always opens these settings. On Android 12+ you can also long-press the widget and tap Reconfigure.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AppTab(
    mode: AppThemeMode, accent: AppAccent,
    onMode: (AppThemeMode) -> Unit, onAccent: (AppAccent) -> Unit,
) {
    Section("Settings screen theme") {
        ChipRow(AppThemeMode.entries, mode, { it.label }) { onMode(it) }
    }
    Section("Accent color") {
        AccentRow(accent, onAccent)
        Text(accent.label, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------- pieces

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentRow(selected: AppAccent, onSelect: (AppAccent) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppPrefs.ACCENTS.forEach { a ->
            val sel = a.key == selected.key
            val fill: Brush = if (a.key == "dynamic") {
                Brush.sweepGradient(listOf(Color(0xFF6750A4), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF6750A4)))
            } else {
                Brush.linearGradient(listOf(Color(a.seed), Color(a.seed)))
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(fill)
                    .border(
                        if (sel) 3.dp else 1.dp,
                        if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                    .clickable { onSelect(a) },
            )
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

@Composable
private fun AppPicker(value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { TapActions.launchableApps(ctx) }
    var open by remember { mutableStateOf(false) }
    val label = apps.firstOrNull { it.packageName == value }?.label ?: "Choose app"
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            apps.forEach { a ->
                DropdownMenuItem(text = { Text(a.label) }, onClick = { onChange(a.packageName); open = false })
            }
        }
    }
}

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF1B1B1F, 0xFFE53935, 0xFFFF7A00, 0xFFFDD835,
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
            modifier = Modifier.width(180.dp),
        )
    }
}
