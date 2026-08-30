package com.arslan.prayerbar.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arslan.prayerbar.PrayerBarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fires at a prayer boundary: rewrite the label, then arm the next boundary. */
class PrayerTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val container = PrayerBarApp.container(context)
        container.launch {
            try {
                withContext(Dispatchers.IO) {
                    val outcome = container.carrierApplier.apply(force = true)
                    container.alarmScheduler.schedule(outcome.next)
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
