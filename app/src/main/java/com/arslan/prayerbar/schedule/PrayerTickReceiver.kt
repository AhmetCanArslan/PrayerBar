package com.arslan.prayerbar.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arslan.prayerbar.PrayerBarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fires at a prayer boundary: refresh whichever surfaces are on, then arm the next boundary. */
class PrayerTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val container = PrayerBarApp.container(context)
        container.launch {
            try {
                withContext(Dispatchers.IO) {
                    val outcome = SurfaceRefresh.run(context, force = true)
                    // Everything is off: a leftover alarm from before, so let the chain end here.
                    if (!outcome.active) return@withContext
                    container.alarmScheduler.schedule(outcome.next)
                    // The process may have been killed since the last boundary; re-arm the wake refresh.
                    CarrierService.start(context)
                    Log.d(TAG, "tick -> ${outcome.text} (${outcome.result})")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PrayerTickReceiver"
    }
}
