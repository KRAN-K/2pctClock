package app.pct.clock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A digit transition in progress: [prevText] is the old time string, [t] runs 0..1. */
class AnimFrame(val prevText: String, val t: Float)

/**
 * Draws the whole widget into a Bitmap with Canvas: custom fonts, gradient/image backgrounds,
 * animated borders and per-digit transition effects. Used by the widget engine and the live preview.
 */
object StyledRenderer {

    fun timeString(ctx: Context, s: ClockSettings, now: Long): String =
        SimpleDateFormat(s.timePattern(DateFormat.is24HourFormat(ctx)), Locale.getDefault()).format(Date(now))

    fun render(
        ctx: Context,
        s: ClockSettings,
        wDp: Float,
        hDp: Float,
        now: Long,
        anim: AnimFrame?,
        phase: Float,
        night: Boolean,
        maxPixels: Int = 650_000,
    ): Bitmap {
        var ppd = ctx.resources.displayMetrics.density
        val rawArea = wDp * ppd * hDp * ppd
        if (rawArea > maxPixels) ppd *= sqrt(maxPixels / rawArea)
        val w = (wDp * ppd).roundToInt().coerceAtLeast(8)
        val h = (hDp * ppd).roundToInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val colors = Palette.resolve(ctx, s, night)

        val flame = if (s.border == BorderStyle.FIRE) min(24f * ppd, min(w, h) * 0.24f) else 0f
        val box = RectF(flame, flame, w - flame, h - flame)
        val radius = min(CORNER_DP[s.cornerStyle] * ppd, min(box.width(), box.height()) / 2f)
        val bw = s.borderWidth * ppd

        if (s.border == BorderStyle.FIRE) drawFlames(c, box, radius, flame, phase, ppd)
        drawBackground(ctx, c, s, colors, box, radius)
        drawBorder(c, s, box, radius, bw, phase)
        val pad = 12f * ppd + (if (s.border != BorderStyle.NONE) bw else 0f)
        val area = RectF(box).apply { inset(pad, pad) }
        drawContent(ctx, c, s, colors, area, anim, ppd, now, phase)
        return bmp
    }

    // ------------------------------------------------------------------ background

    private fun drawBackground(ctx: Context, c: Canvas, s: ClockSettings, colors: WidgetColors, box: RectF, radius: Float) {
        val alpha = s.bgOpacity.coerceIn(0, 100) * 255 / 100
        c.save()
        c.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (s.bgMode) {
            BgMode.TRANSPARENT -> Unit
            BgMode.THEME -> {
                p.color = colors.bg
                p.alpha = alpha
                c.drawRect(box, p)
            }
            BgMode.GRADIENT -> {
                val g = BgStore.gradient(s.gradientKey)
                val rad = Math.toRadians(g.angle.toDouble())
                val half = (abs(box.width() * cos(rad)) + abs(box.height() * sin(rad))).toFloat() / 2f
                val dx = (cos(rad) * half).toFloat()
                val dy = (sin(rad) * half).toFloat()
                p.shader = LinearGradient(
                    box.centerX() - dx, box.centerY() - dy, box.centerX() + dx, box.centerY() + dy,
                    g.colors, null, Shader.TileMode.CLAMP,
                )
                p.alpha = alpha
                c.drawRect(box, p)
            }
            BgMode.IMAGE -> {
                val bm = s.bgImage?.let { BgStore.bitmap(ctx, it) }
                if (bm == null) {
                    p.color = colors.bg
                    p.alpha = alpha
                    c.drawRect(box, p)
                } else {
                    val sc = max(box.width() / bm.width, box.height() / bm.height)
                    val dw = bm.width * sc
                    val dh = bm.height * sc
                    val dst = RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2)
                    p.isFilterBitmap = true
                    p.alpha = alpha
                    c.drawBitmap(bm, null, dst, p)
                    val dim = Paint()
                    dim.color = Color.BLACK
                    dim.alpha = s.bgDim.coerceIn(0, 80) * 255 / 100 * alpha / 255
                    c.drawRect(box, dim)
                }
            }
        }
        c.restore()
    }

    // ------------------------------------------------------------------ borders

    private fun drawBorder(c: Canvas, s: ClockSettings, box: RectF, radius: Float, bw: Float, phase: Float) {
        if (s.border == BorderStyle.NONE) return
        fun ring(inset: Float, paint: Paint) {
            val r = RectF(box).apply { inset(inset, inset) }
            val rr = max(0f, radius - inset)
            c.drawRoundRect(r, rr, rr, paint)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        when (s.border) {
            BorderStyle.SOLID -> {
                p.color = s.borderColor
                p.strokeWidth = bw
                ring(bw / 2, p)
            }
            BorderStyle.DOUBLE -> {
                val sw = max(1f, bw / 2.2f)
                p.color = s.borderColor
                p.strokeWidth = sw
                ring(sw / 2, p)
                ring(bw - sw / 2, p)
            }
            BorderStyle.GLOW, BorderStyle.PULSE -> {
                val k = if (s.border == BorderStyle.PULSE) 0.35f + 0.65f * (0.5f + 0.5f * sin(phase * 3.9f)) else 1f
                p.color = s.borderColor
                p.alpha = (200 * k).toInt()
                p.strokeWidth = bw * 1.8f
                p.maskFilter = BlurMaskFilter(bw * 2.2f, BlurMaskFilter.Blur.NORMAL)
                ring(bw * 0.9f, p)
                p.maskFilter = null
                p.color = ColorUtils.blendARGB(s.borderColor, Color.WHITE, 0.5f)
                p.alpha = (255 * k).toInt()
                p.strokeWidth = bw * 0.7f
                ring(bw * 0.35f, p)
            }
            BorderStyle.RAINBOW -> {
                val sweep = SweepGradient(
                    box.centerX(), box.centerY(),
                    intArrayOf(
                        0xFFFF1744.toInt(), 0xFFFFEA00.toInt(), 0xFF00E676.toInt(),
                        0xFF00B0FF.toInt(), 0xFFD500F9.toInt(), 0xFFFF1744.toInt(),
                    ),
                    null,
                )
                sweep.setLocalMatrix(Matrix().apply { postRotate(phase * 90f, box.centerX(), box.centerY()) })
                p.shader = sweep
                p.strokeWidth = bw
                ring(bw / 2, p)
            }
            BorderStyle.FIRE -> {
                p.color = 0xFFFF6D00.toInt()
                p.strokeWidth = bw * 1.6f
                p.maskFilter = BlurMaskFilter(bw * 1.5f, BlurMaskFilter.Blur.NORMAL)
                ring(bw * 0.8f, p)
                p.maskFilter = null
                p.color = 0xFFFFB300.toInt()
                p.strokeWidth = bw * 0.7f
                ring(bw * 0.35f, p)
            }
            BorderStyle.NONE -> Unit
        }
    }

    /** One flame tongue: wide base, S-curved sides, pointed tip. (dx,dy) = direction, (tx,ty) = along the edge. */
    private fun flamePath(
        path: Path, bx: Float, by: Float, dx: Float, dy: Float, tx: Float, ty: Float,
        w: Float, h: Float, sway: Float,
    ) {
        path.reset()
        val l0x = bx - tx * w / 2
        val l0y = by - ty * w / 2
        val r0x = bx + tx * w / 2
        val r0y = by + ty * w / 2
        val tipx = bx + dx * h + tx * sway
        val tipy = by + dy * h + ty * sway
        path.moveTo(l0x, l0y)
        path.cubicTo(
            l0x - tx * w * 0.15f + dx * h * 0.35f, l0y - ty * w * 0.15f + dy * h * 0.35f,
            bx - tx * w * 0.25f + dx * h * 0.75f + tx * sway * 0.6f, by - ty * w * 0.25f + dy * h * 0.75f + ty * sway * 0.6f,
            tipx, tipy,
        )
        path.cubicTo(
            bx + tx * w * 0.25f + dx * h * 0.7f + tx * sway * 0.6f, by + ty * w * 0.25f + dy * h * 0.7f + ty * sway * 0.6f,
            r0x + tx * w * 0.15f + dx * h * 0.35f, r0y + ty * w * 0.15f + dy * h * 0.35f,
            r0x, r0y,
        )
        path.close()
    }

    private val FLAME_COLORS = intArrayOf(0xFFFFF59D.toInt(), 0xFFFF9100.toInt(), 0xFFE53935.toInt(), 0x00B71C1C)
    private val FLAME_POS = floatArrayOf(0f, 0.35f, 0.72f, 1f)

    /**
     * A wall of fire around the widget: many overlapping tongues that rise upward (leaning outward
     * on the sides), flicker at different rates, with a traveling wave and drifting sparks.
     */
    private fun drawFlames(c: Canvas, box: RectF, radius: Float, flame: Float, phase: Float, ppd: Float) {
        val path = Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) }
        val pm = PathMeasure(path, true)
        val len = pm.length
        if (len <= 0f) return
        val step = 8f * ppd
        val n = max(10, (len / step).toInt())
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val shape = Path()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (pass in 0..1) { // pass 0: big outer flames, pass 1: smaller bright inner flames
            for (i in 0 until n) {
                pm.getPosTan(i * len / n, pos, tan)
                var nx = tan[1]
                var ny = -tan[0]
                // make the normal point away from the box center whatever the path winding is
                if ((pos[0] - box.centerX()) * nx + (pos[1] - box.centerY()) * ny < 0f) {
                    nx = -nx
                    ny = -ny
                }
                val bottom = ny > 0.4f
                var dx = nx * 0.8f
                var dy = ny * 0.8f - (if (bottom) 0f else 0.9f)
                val dl = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                dx /= dl
                dy /= dl
                val s1 = sin(phase * 5.3 + i * 0.83)
                val s2 = sin(phase * 8.1 + i * 1.91)
                val s3 = sin(phase * 2.7 - i * 0.37) // slow traveling wave
                val burst = max(0.0, sin(i * 1.37 + phase * 2.3))
                val noise = (0.5 + 0.25 * s1 + 0.15 * s2 + 0.25 * s3 + 0.35 * burst).coerceIn(0.2, 1.4)
                var h = (flame * noise).toFloat() * (if (bottom) 0.4f else 1f)
                var w = step * 1.9f
                if (pass == 1) {
                    h *= 0.62f
                    w *= 0.55f
                }
                val sway = (sin(phase * 6.0 + i * 0.7) * w * 0.35).toFloat()
                val tipX = pos[0] + dx * h
                val tipY = pos[1] + dy * h
                flamePath(shape, pos[0], pos[1], dx, dy, tan[0], tan[1], w, h, sway)
                p.shader = LinearGradient(
                    pos[0], pos[1], tipX, tipY,
                    if (pass == 0) FLAME_COLORS else intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF9100.toInt(), 0x00FF5722),
                    FLAME_POS, Shader.TileMode.CLAMP,
                )
                c.drawPath(shape, p)
            }
        }
        p.shader = null
        // sparks drifting up from the top edge
        for (k in 0 until 16) {
            val life = (phase * 0.9f + rnd(k * 7 + 1)) % 1f
            val sx = box.left + rnd(k * 7 + 2) * box.width() + sin(life * 6f + k) * 4f * ppd
            val sy = box.top - life * flame * 1.1f
            p.color = ColorUtils.blendARGB(0xFFFFEB3B.toInt(), 0xFFFF3D00.toInt(), life)
            p.alpha = ((1f - life) * 255).toInt()
            c.drawCircle(sx, sy, max(0.8f, (1f - life) * 2.2f * ppd), p)
        }
    }

    // ------------------------------------------------------------------ text

    private fun sampleGradient(cols: IntArray, f: Float): Int {
        val pp = f.coerceIn(0f, 1f) * (cols.size - 1)
        val i = pp.toInt().coerceAtMost(cols.size - 2)
        return ColorUtils.blendARGB(cols[i], cols[i + 1], pp - i)
    }

    /** Text color at [frac] (0..1) across the time line; solid unless gradient/rainbow is chosen. */
    private fun textColorAt(s: ClockSettings, colors: WidgetColors, frac: Float, phase: Float): Int = when (s.fontColorMode) {
        FontColorMode.THEME, FontColorMode.CUSTOM -> colors.text
        FontColorMode.GRADIENT -> sampleGradient(BgStore.gradient(s.fontGradientKey).colors, frac)
        FontColorMode.RAINBOW -> Color.HSVToColor(
            floatArrayOf((((phase * 70f + frac * 220f) % 360f) + 360f) % 360f, 0.7f, 1f),
        )
    }

    private fun advances(str: String, p: Paint): FloatArray {
        val a = FloatArray(str.length)
        p.getTextWidths(str, a)
        return a
    }

    private fun drawContent(
        ctx: Context, c: Canvas, s: ClockSettings, colors: WidgetColors,
        area: RectF, anim: AnimFrame?, ppd: Float, now: Long, phase: Float,
    ) {
        val tf = FontStore.typeface(ctx, s.fontKey)
        val loc = Locale.getDefault()
        val system24 = DateFormat.is24HourFormat(ctx)
        val timeStr = timeString(ctx, s, now)
        val ampm = if (s.showAmPmNow(system24)) SimpleDateFormat("a", loc).format(Date(now)) else ""

        val extras = ArrayList<Pair<String, Int>>() // text, color
        if (s.showDate) extras += SimpleDateFormat(s.datePattern(), loc).format(Date(now)) to textColorAt(s, colors, 0.5f, phase)
        if (s.showAlarm) WidgetRenderer.nextAlarmText(ctx)?.let { extras += it to colors.sub }
        for (z in s.zones.take(2)) {
            val f = SimpleDateFormat(s.zonePattern(system24), loc)
            f.timeZone = TimeZone.getTimeZone(z)
            extras += (cityName(z) + "  " + f.format(Date(now))) to colors.sub
        }

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf }
        fun timeWidth(sz: Float): Float {
            tp.textSize = sz
            val main = advances(timeStr, tp).sum()
            if (ampm.isEmpty()) return main
            tp.textSize = sz * 0.32f
            return main + sz * 0.06f + tp.measureText(ampm)
        }

        var timePx = area.height() / (1.15f + extras.size * 0.40f) * s.sizeScale
        val tw = timeWidth(timePx)
        if (tw > area.width() && tw > 0f) timePx *= area.width() / tw
        timePx = timePx.coerceAtLeast(8f * ppd)
        val subPx = (timePx * 0.30f).coerceIn(11f * ppd, 26f * ppd).coerceAtMost(timePx)

        tp.textSize = timePx
        val r = Rect()
        tp.getTextBounds("0123456789", 0, 10, r)
        val digitH = if (r.height() > 0) r.height().toFloat() else timePx * 0.7f
        val subLine = subPx * 1.25f
        val gap = if (extras.isEmpty()) 0f else timePx * 0.2f
        val total = digitH + gap + extras.size * subLine
        var y = area.centerY() - total / 2f
        val timeBase = y + digitH

        fun startX(lineW: Float): Float = when (s.align) {
            ClockAlign.START -> area.left
            ClockAlign.CENTER -> area.centerX() - lineW / 2f
            ClockAlign.END -> area.right - lineW
        }

        // time, one glyph at a time so changed digits can animate
        val adv = advances(timeStr, tp)
        val lineW = timeWidth(timePx).coerceAtLeast(1f)
        val x0 = startX(lineW)
        var x = x0
        for (i in timeStr.indices) {
            val ch = timeStr[i]
            val oldIdx = (anim?.prevText?.length ?: 0) - (timeStr.length - i)
            val old = if (anim != null && oldIdx >= 0) anim.prevText[oldIdx] else null
            val charColor = textColorAt(s, colors, (x - x0 + adv[i] / 2f) / lineW, phase)
            drawGlyph(c, tf, ch, old, x, adv[i], timeBase, timePx, digitH, charColor, anim, s.animation, i)
            x += adv[i]
        }
        if (ampm.isNotEmpty()) {
            val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = timePx * 0.32f
                color = textColorAt(s, colors, 1f, phase)
            }
            c.drawText(ampm, x + timePx * 0.06f, timeBase, ap)
        }

        // date, alarm, world clocks
        y = timeBase + gap
        for ((text, color) in extras) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = subPx
                this.color = color
            }
            val w = p.measureText(text)
            if (w > area.width() && w > 0f) p.textSize = subPx * area.width() / w
            c.drawText(text, startX(min(w, area.width())), y + subPx, p)
            y += subLine
        }
    }

    // ------------------------------------------------------------------ digit effects

    private fun ease(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

    private fun backOut(t: Float): Float {
        val u = t - 1f
        return 1f + 2.70158f * u * u * u + 1.70158f * u * u
    }

    private fun rnd(seed: Int): Float {
        var x = seed * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177.toInt()
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777216f
    }

    private fun drawGlyph(
        c: Canvas, tf: Typeface, ch: Char, old: Char?, x: Float, adv: Float, base: Float,
        size: Float, digitH: Float, baseColor: Int, anim: AnimFrame?, kind: DigitAnim, idx: Int,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = size
            color = baseColor
        }
        fun g(chr: Char, gx: Float, gy: Float, col: Int, a: Float) {
            p.color = col
            p.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
            c.drawText(chr.toString(), gx, gy, p)
        }
        val animating = anim != null && kind != DigitAnim.NONE && old != null && old != ch
        if (!animating) {
            g(ch, x, base, baseColor, 1f)
            return
        }
        val t = anim!!.t.coerceIn(0f, 1f)
        val o = old!!
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        fun spark(px: Float, py: Float, r: Float, col: Int, a: Float) {
            dot.color = col
            dot.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
            c.drawCircle(px, py, max(0.5f, r), dot)
        }
        val orange = 0xFFFF6D00.toInt()

        when (kind) {
            DigitAnim.ROLL -> {
                val e = ease(t)
                val lh = digitH * 1.5f
                c.save()
                c.clipRect(x - adv * 0.1f, base - digitH * 1.3f, x + adv * 1.1f, base + digitH * 0.35f)
                g(o, x, base - e * lh, baseColor, 1f - e * 0.6f)
                g(ch, x, base + (1f - e) * lh, baseColor, 0.4f + 0.6f * e)
                c.restore()
            }
            DigitAnim.FADE -> {
                g(o, x, base, baseColor, 1f - t)
                g(ch, x, base, baseColor, t)
            }
            DigitAnim.POP -> {
                g(o, x, base, baseColor, 1f - t * 4f)
                val sc = 0.35f + 0.65f * backOut(t)
                c.save()
                c.scale(sc, sc, x + adv / 2f, base - digitH / 2f)
                g(ch, x, base, baseColor, min(1f, t * 3f))
                c.restore()
            }
            DigitAnim.FLICKER -> {
                val frame = (t * 26f).toInt()
                if (t < 0.3f) {
                    g(o, x, base, baseColor, if (rnd(idx * 31 + frame) < 0.45f) 1f else 0.1f)
                } else {
                    g(ch, x, base, baseColor, if (t > 0.85f || rnd(idx * 57 + frame) < t) 1f else 0.08f)
                }
            }
            DigitAnim.FIRE -> {
                // Real flame tongues rise through the digit, the old digit is consumed,
                // and the new one emerges white-hot and cools to its color.
                val env = sin(Math.PI * t).toFloat().coerceAtLeast(0f)
                if (t < 0.45f) {
                    p.setShadowLayer(digitH * 0.25f, 0f, 0f, orange)
                    g(o, x, base, ColorUtils.blendARGB(baseColor, 0xFFFF9100.toInt(), t / 0.45f), 1f - t / 0.45f)
                    p.clearShadowLayer()
                }
                val nt = ((t - 0.3f) / 0.7f).coerceIn(0f, 1f)
                val tongues = 6
                val fpaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val shape = Path()
                for (k in 0 until tongues) {
                    val sd = idx * 131 + k * 17
                    val fx = x + adv * (k + 0.5f) / tongues
                    val flick = 0.6f + 0.4f * sin(t * 22f + rnd(sd) * 6.28f)
                    val h = digitH * (0.55f + 0.9f * rnd(sd + 1)) * env * flick
                    if (h < 2f) continue
                    val w = adv / tongues * 1.9f
                    val sway = sin(t * 14f + k * 1.9f) * w * 0.45f
                    val by = base + digitH * 0.05f
                    flamePath(shape, fx, by, 0f, -1f, 1f, 0f, w, h, sway)
                    fpaint.shader = LinearGradient(fx, by, fx, by - h, FLAME_COLORS, FLAME_POS, Shader.TileMode.CLAMP)
                    c.drawPath(shape, fpaint)
                }
                if (nt > 0f) {
                    p.setShadowLayer(max(0.1f, digitH * 0.35f * (1f - nt)), 0f, 0f, orange)
                    g(ch, x, base, ColorUtils.blendARGB(0xFFFFF59D.toInt(), baseColor, nt), min(1f, nt * 3f))
                    p.clearShadowLayer()
                }
                for (k in 0 until 8) {
                    val sd = idx * 977 + k * 31
                    val life = (t * 1.3f + rnd(sd + 1)) % 1f
                    spark(
                        x + rnd(sd + 2) * adv, base - digitH * 0.3f - life * digitH * 1.0f,
                        (1f - life) * digitH * 0.06f,
                        ColorUtils.blendARGB(0xFFFFEB3B.toInt(), 0xFFFF3D00.toInt(), life), (1f - life) * env,
                    )
                }
            }
            DigitAnim.BURN -> {
                // A burn line sweeps up the digit like paper: intact above, charred band, glowing edge,
                // nothing below. Smoke and ash drift off it, then the new digit forms out of the ash.
                val prog = (t / 0.7f).coerceIn(0f, 1f)
                val band = digitH * 0.2f
                val lineY = base + band * 0.5f - prog * (digitH * 1.15f + band)
                val topY = base - digitH * 1.6f
                val padX = adv * 0.3f
                if (prog < 1f) {
                    c.save()
                    c.clipRect(x - padX, topY, x + adv + padX, lineY - band)
                    g(o, x, base, baseColor, 1f)
                    c.restore()
                    c.save()
                    c.clipRect(x - padX, lineY - band, x + adv + padX, lineY)
                    g(o, x, base, 0xFF5A3A22.toInt(), 1f)
                    c.restore()
                    c.save()
                    c.clipRect(x - padX, lineY - band * 0.4f, x + adv + padX, lineY + band * 0.2f)
                    p.setShadowLayer(digitH * 0.22f, 0f, 0f, orange)
                    g(o, x, base, 0xFFFF6A00.toInt(), 1f)
                    p.clearShadowLayer()
                    c.restore()
                    for (k in 0 until 9) { // smoke
                        val sd = idx * 733 + k * 19
                        val life = (t * 1.6f + rnd(sd)) % 1f
                        spark(
                            x + rnd(sd + 1) * adv + sin(life * 5f + k) * adv * 0.1f, lineY - life * digitH * 0.9f,
                            digitH * (0.03f + 0.05f * life), 0xFF9E9E9E.toInt(), (1f - life) * 0.28f,
                        )
                    }
                    for (k in 0 until 8) { // ash flakes falling from the line
                        val sd = idx * 541 + k * 13
                        val life = (t * 2.0f + rnd(sd)) % 1f
                        spark(
                            x + rnd(sd + 1) * adv, lineY + life * digitH * 0.7f,
                            digitH * 0.025f, 0xFF616161.toInt(), (1f - life) * 0.8f,
                        )
                    }
                    for (k in 0 until 6) { // embers hopping off the edge
                        val sd = idx * 311 + k * 7
                        val life = (t * 2.6f + rnd(sd)) % 1f
                        spark(
                            x + rnd(sd + 1) * adv, lineY - life * digitH * 0.5f,
                            digitH * 0.03f, ColorUtils.blendARGB(0xFFFFC107.toInt(), 0xFFFF3D00.toInt(), life), 1f - life,
                        )
                    }
                }
                val nt = ((t - 0.6f) / 0.3f).coerceIn(0f, 1f)
                if (nt > 0f) {
                    val sc = 0.92f + 0.08f * nt
                    c.save()
                    c.scale(sc, sc, x + adv / 2f, base)
                    g(ch, x, base, ColorUtils.blendARGB(0xFF8D8D8D.toInt(), baseColor, nt), nt)
                    c.restore()
                }
            }
            DigitAnim.WATER -> {
                val blue = 0xFF4FC3F7.toInt()
                val cw = ceil(adv + digitH * 0.4f).toInt().coerceAtLeast(2)
                val chh = ceil(digitH * 1.6f).toInt().coerceAtLeast(2)
                val cell = Bitmap.createBitmap(cw, chh, Bitmap.Config.ARGB_8888)
                val cc = Canvas(cell)
                val ox = digitH * 0.2f
                val oy = digitH * 1.25f
                val sp = Paint().apply { isFilterBitmap = true }
                fun ripple(chr: Char, color: Int, amp: Float, a: Float) {
                    cell.eraseColor(0)
                    p.color = color
                    p.alpha = 255
                    cc.drawText(chr.toString(), ox, oy, p)
                    sp.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
                    var row = 0
                    while (row < chh) {
                        val end = min(chh, row + 2)
                        val dx = amp * sin(row * 0.28f + t * 14f)
                        c.drawBitmap(
                            cell, Rect(0, row, cw, end),
                            RectF(x - ox + dx, base - oy + row, x - ox + dx + cw, base - oy + end), sp,
                        )
                        row += 2
                    }
                }
                if (t < 0.45f) {
                    ripple(o, ColorUtils.blendARGB(baseColor, blue, t / 0.45f), digitH * 0.12f * t / 0.45f, 1f - t / 0.45f)
                }
                val nt = ((t - 0.3f) / 0.7f).coerceIn(0f, 1f)
                if (nt > 0f) ripple(ch, ColorUtils.blendARGB(blue, baseColor, nt), digitH * 0.14f * (1f - nt), nt)
                cell.recycle()
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(1f, digitH * 0.03f)
                    color = blue
                }
                for (k in 0..1) {
                    val rt = t - k * 0.18f
                    if (rt > 0f && rt < 1f) {
                        ring.alpha = ((1f - rt) * 0.6f * 255).toInt()
                        val rad = digitH * (0.2f + rt * 0.8f)
                        c.drawOval(
                            RectF(x + adv / 2f - rad, base - digitH / 2f - rad * 0.5f, x + adv / 2f + rad, base - digitH / 2f + rad * 0.5f),
                            ring,
                        )
                    }
                }
            }
            DigitAnim.NONE -> g(ch, x, base, baseColor, 1f)
        }
    }
}
