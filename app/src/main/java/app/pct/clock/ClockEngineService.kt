package app.pct.clock

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Draws frames for "styled" widgets (custom fonts, backgrounds, animated borders, digit effects).
 * It only ticks while the screen is on, and stops itself when no styled widget remains.
 */
class ClockEngineService : Service() {

    private class WState(var text: String = "", var prev: String = "", var animStart: Long = 0L, var full: Boolean = false)

    private val handler = Handler(Looper.getMainLooper())
    private val states = HashMap<Int, WState>()
    private var screenOn = true
    private val tick = Runnable { frame() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(tick)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOn = true
                    kick() // draws one fresh frame; keeps ticking only if the phone is unlocked
                }
                Intent.ACTION_TIME_TICK -> if (screenOn) kick()
                else -> { // config / time / timezone changed: redraw everything
                    states.clear()
                    kick()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        running = true
        startAsForeground()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK) // once a minute on the wall clock; no timer needed
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) states.clear()
        kick()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun kick() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Clock effects", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null); enableVibration(false); enableLights(false); setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, ConfigActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("2%Clock effects running")
            .setContentText("Animates your clock while the screen is on")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun msToNext(period: Long, now: Long) = period - (now % period) + 20L

    /** One failed frame (e.g. the launcher refusing a bitmap) must never stop the clock from ticking. */
    private fun frame() {
        var next = 5_000L
        try {
            next = drawFrame()
            lastFrameAt = System.currentTimeMillis()
        } catch (t: Throwable) {
            next = 3_000L
        }
        if (next > 0 && canTick()) handler.postDelayed(tick, next.coerceAtLeast(30L))
        scheduleWatchdog(this)
    }

    /** No point drawing while the screen is off or the lock screen is up (widgets aren't visible there). */
    private fun canTick(): Boolean {
        if (!screenOn) return false
        val km = getSystemService(KeyguardManager::class.java)
        return km == null || !km.isKeyguardLocked
    }

    /** Draws all styled widgets once; returns ms until the next frame, or 0 to stop. */
    private fun drawFrame(): Long {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, ClockWidgetProvider::class.java))
        val now = System.currentTimeMillis()
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        var delay = 60_000L
        var any = false

        for (id in ids) {
            val s = SettingsStore.load(this, id)
            if (!s.needsStyled()) continue
            any = true
            val st = states.getOrPut(id) { WState() }
            if (!st.full) {
                WidgetRenderer.render(this, mgr, id) // full update with click targets
                st.full = true
            }

            val secondsShown = s.timePattern(false).endsWith(":ss")
            val text = StyledRenderer.timeString(this, s, now)
            val key = if (s.animUnit == AnimUnit.MINUTE && secondsShown) text.dropLast(3) else text
            val oldKey = if (s.animUnit == AnimUnit.MINUTE && secondsShown) st.text.dropLast(3) else st.text
            if (st.text.isNotEmpty() && key != oldKey && s.animation != DigitAnim.NONE) {
                st.prev = st.text
                st.animStart = now
            }
            val same = st.text == text
            st.text = text

            val dur = s.animation.durationMs
            val since = now - st.animStart
            val animating = st.animStart > 0 && since < dur
            if (same && !animating && !s.animatedStyle() && st.full) {
                // nothing changed on screen since the last frame: skip the expensive redraw
                delay = min(delay, if (secondsShown) msToNext(1000L, now) else msToNext(60_000L, now))
                continue
            }
            val anim = if (animating) AnimFrame(st.prev, since / dur.toFloat()) else null

            val opts = mgr.getAppWidgetOptions(id)
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat()
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).toFloat()
            val bmp = StyledRenderer.render(this, s, wDp, hDp, now, anim, (now % 600_000L) / 1000f, night, maxPixels = 300_000)
            val rv = RemoteViews(packageName, R.layout.styled_widget)
            rv.setImageViewBitmap(R.id.frame, bmp)
            mgr.partiallyUpdateAppWidget(id, rv)

            val d = when {
                animating -> 60L
                s.animatedStyle() -> 130L
                secondsShown -> msToNext(1000L, now)
                else -> msToNext(60_000L, now)
            }
            delay = min(delay, d)
        }
        states.keys.retainAll(ids.toSet())
        if (!any) {
            stopSelf()
            return 0L
        }
        return delay
    }

    companion object {
        const val ACTION_REFRESH = "app.pct.clock.REFRESH"
        const val ACTION_WATCHDOG = "app.pct.clock.WATCHDOG"
        @Volatile var lastFrameAt = 0L
        @Volatile var running = false

        /**
         * Safety net: a quiet (non-waking) alarm about once a minute. If the engine has died or stalled while
         * the screen is on, the provider redraws the clock so the time can't stay frozen.
         */
        fun scheduleWatchdog(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                ctx, 77, Intent(ctx, ClockWidgetProvider::class.java).setAction(ACTION_WATCHDOG),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching { am.set(AlarmManager.RTC, System.currentTimeMillis() + 60_000L, pi) }
        }
        private const val CHANNEL = "engine"
        private const val NOTIF_ID = 1

        /** Start the engine if any widget needs it, stop it if none does. */
        fun sync(ctx: Context, refresh: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ClockWidgetProvider::class.java))
            val needed = ids.any { SettingsStore.load(ctx, it).needsStyled() }
            val i = Intent(ctx, ClockEngineService::class.java)
            if (refresh) i.action = ACTION_REFRESH
            if (needed && refresh.not() && running) return // already up: don't re-post the notification
            if (needed) {
                runCatching { ContextCompat.startForegroundService(ctx, i) }
            } else {
                ctx.stopService(i)
            }
        }
    }
}
