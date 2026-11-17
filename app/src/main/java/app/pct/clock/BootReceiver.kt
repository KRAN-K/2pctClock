package app.pct.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the effects engine after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetRenderer.updateAll(context)
        ClockEngineService.sync(context)
    }
}
