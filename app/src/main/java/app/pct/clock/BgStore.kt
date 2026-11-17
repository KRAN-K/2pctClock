package app.pct.clock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GradientPreset(val key: String, val label: String, val colors: IntArray, val angle: Float)

object BgStore {
    val GRADIENTS = listOf(
        GradientPreset("sunset", "Sunset", intArrayOf(0xFFFF512F.toInt(), 0xFFDD2476.toInt(), 0xFF6A1B9A.toInt()), 135f),
        GradientPreset("ocean", "Ocean", intArrayOf(0xFF2193B0.toInt(), 0xFF6DD5ED.toInt()), 135f),
        GradientPreset("aurora", "Aurora", intArrayOf(0xFF00C9FF.toInt(), 0xFF92FE9D.toInt()), 160f),
        GradientPreset("ember", "Ember", intArrayOf(0xFF8E0E00.toInt(), 0xFFFF6A00.toInt(), 0xFFFFC837.toInt()), 90f),
        GradientPreset("forest", "Forest", intArrayOf(0xFF134E5E.toInt(), 0xFF71B280.toInt()), 135f),
        GradientPreset("midnight", "Midnight", intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()), 135f),
        GradientPreset("royal", "Royal", intArrayOf(0xFF614385.toInt(), 0xFF516395.toInt()), 135f),
        GradientPreset("candy", "Candy", intArrayOf(0xFFFF9A9E.toInt(), 0xFFFAD0C4.toInt(), 0xFFA18CD1.toInt()), 135f),
    )

    fun gradient(key: String): GradientPreset = GRADIENTS.firstOrNull { it.key == key } ?: GRADIENTS[0]

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "bg").also { it.mkdirs() }

    fun bitmap(ctx: Context, name: String): Bitmap? {
        cache.get(name)?.let { return it }
        val f = File(dir(ctx), name)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.also { cache.put(name, it) }
    }

    fun saveFromUri(ctx: Context, uri: Uri): String? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return saveBytes(ctx, bytes)
    }

    fun saveFromUrl(ctx: Context, url: String): String? {
        val conn = URL(url.trim()).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) 2%Clock")
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        if (conn.responseCode != 200) error("Download failed (HTTP ${conn.responseCode})")
        val bytes = conn.inputStream.use { it.readBytes() }
        return saveBytes(ctx, bytes)
    }

    /** Downscale to at most 1600px on the long side and store as JPEG. */
    private fun saveBytes(ctx: Context, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val name = UUID.randomUUID().toString().take(8) + ".jpg"
        File(dir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cache.put(name, bmp)
        return name
    }
}
