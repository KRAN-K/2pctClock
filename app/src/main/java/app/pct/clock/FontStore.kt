package app.pct.clock

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Fonts come from three places, identified by a key string:
 *   sys:<name>   built-in Android families
 *   asset:<file> open-license fonts bundled in the APK
 *   file:<file>  fonts the user imported (for example from dafont.com)
 */
object FontStore {
    data class Entry(val key: String, val label: String)

    val SYSTEM = listOf(
        Entry("sys:sans", "Sans"),
        Entry("sys:light", "Light"),
        Entry("sys:condensed", "Condensed"),
        Entry("sys:serif", "Serif"),
        Entry("sys:mono", "Mono"),
    )

    val BUNDLED = listOf(
        Entry("asset:Rye-Regular.ttf", "Rye (western)"),
        Entry("asset:Creepster-Regular.ttf", "Creepster"),
        Entry("asset:Bangers-Regular.ttf", "Bangers"),
        Entry("asset:BungeeInline-Regular.ttf", "Bungee Inline"),
        Entry("asset:Monoton-Regular.ttf", "Monoton"),
        Entry("asset:PressStart2P-Regular.ttf", "Press Start 2P"),
        Entry("asset:Pacifico-Regular.ttf", "Pacifico"),
        Entry("asset:GreatVibes-Regular.ttf", "Great Vibes"),
    )

    private val cache = ConcurrentHashMap<String, Typeface>()

    private fun dir(ctx: Context) = File(ctx.filesDir, "fonts").also { it.mkdirs() }

    fun imported(ctx: Context): List<Entry> =
        (dir(ctx).listFiles() ?: emptyArray())
            .filter { it.extension.lowercase() in setOf("ttf", "otf") }
            .sortedBy { it.name.lowercase() }
            .map { Entry(
                "file:${it.name}",
                it.nameWithoutExtension.replace('_', ' ')
                    .replace(Regex("personal use only", RegexOption.IGNORE_CASE), "").trim(),
            ) }

    fun typeface(ctx: Context, key: String): Typeface = cache.getOrPut(key) {
        runCatching {
            when {
                key.startsWith("asset:") -> Typeface.createFromAsset(ctx.assets, "fonts/" + key.removePrefix("asset:"))
                key.startsWith("file:") -> Typeface.createFromFile(File(dir(ctx), key.removePrefix("file:")))
                else -> when (key) {
                    "sys:sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
                    "sys:light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                    "sys:condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                    "sys:serif" -> Typeface.create("serif", Typeface.NORMAL)
                    "sys:mono" -> Typeface.create("monospace", Typeface.NORMAL)
                    else -> Typeface.DEFAULT
                }
            }
        }.getOrDefault(Typeface.DEFAULT)
    }

    fun remove(ctx: Context, key: String) {
        if (!key.startsWith("file:")) return
        File(dir(ctx), key.removePrefix("file:")).delete()
        cache.remove(key)
    }

    /** Import a .ttf/.otf or a .zip containing them. Returns the keys added. */
    fun importFromUri(ctx: Context, uri: Uri): List<String> {
        var name = "font"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Could not open file")
        return input.use { importStream(ctx, it, name) }
    }

    /** Import from a direct link or a dafont.com page link such as dafont.com/carta-bianca.font */
    fun importFromUrl(ctx: Context, rawUrl: String): List<String> {
        val url = toDownloadUrl(rawUrl.trim())
        var conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) 2%Clock")
        conn.setRequestProperty("Referer", "https://www.dafont.com/")
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        var hops = 0
        while (conn.responseCode in 301..308 && hops++ < 4) {
            val next = conn.getHeaderField("Location") ?: break
            conn = URL(URL(url), next).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) 2%Clock")
        }
        if (conn.responseCode != 200) error("Download failed (HTTP ${conn.responseCode})")
        val fileName = url.substringAfterLast('/').substringBefore('?').ifEmpty { "font.zip" }
        return conn.inputStream.use { importStream(ctx, it, fileName) }
    }

    private fun toDownloadUrl(url: String): String {
        val m = Regex("""dafont\.com/([a-z0-9\-]+)\.font""", RegexOption.IGNORE_CASE).find(url)
        return if (m != null) "https://dl.dafont.com/dl/?f=" + m.groupValues[1].lowercase().replace('-', '_') else url
    }

    private fun importStream(ctx: Context, input: InputStream, name: String): List<String> {
        val bytes = input.readBytes()
        if (bytes.size > 25_000_000) error("File too large")
        val added = mutableListOf<String>()
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val n = e.name.substringAfterLast('/')
                    if (!e.isDirectory && !e.name.startsWith("__MACOSX") && n.lowercase().let { it.endsWith(".ttf") || it.endsWith(".otf") }) {
                        saveFont(ctx, n, zip.readBytes())?.let { added += it }
                    }
                    e = zip.nextEntry
                }
            }
        } else {
            val n = if (name.lowercase().let { it.endsWith(".ttf") || it.endsWith(".otf") }) name else "$name.ttf"
            saveFont(ctx, n, bytes)?.let { added += it }
        }
        if (added.isEmpty()) error("No .ttf or .otf font found")
        return added
    }

    private fun saveFont(ctx: Context, rawName: String, data: ByteArray): String? {
        val safe = rawName.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").replace(' ', '_')
        val f = File(dir(ctx), safe)
        f.writeBytes(data)
        val ok = runCatching { Typeface.createFromFile(f) }.getOrNull() != null &&
            (android.os.Build.VERSION.SDK_INT < 29 ||
                runCatching { android.graphics.fonts.Font.Builder(f).build() }.isSuccess)
        if (!ok) {
            f.delete()
            return null
        }
        cache.remove("file:$safe")
        return "file:$safe"
    }
}
