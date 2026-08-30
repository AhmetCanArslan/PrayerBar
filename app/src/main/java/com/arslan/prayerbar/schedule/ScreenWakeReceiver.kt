package com.arslan.prayerbar.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.carrier.CarrierTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A countdown token goes stale between prayer boundaries: the alarm only fires roughly six times a
 * day, so `{kalan}` would still read "2sa 10dk" an hour later. Re-render on every screen wake, the
 * only moment the label is actually looked at, instead of polling a minute ticker.
 *
 * ACTION_SCREEN_ON and ACTION_USER_PRESENT cannot be declared in the manifest, so this is
 * registered at runtime by [CarrierService], which is also what keeps the process alive.
 */
class ScreenWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val container = PrayerBarApp.container(context)
        container.launch {
            try {
                withContext(Dispatchers.IO) {
                    val settings = container.settingsRepository.current()
                    if (!settings.enabled) return@withContext
                    // Without a countdown token the text only changes at a boundary; the alarm owns that.
                    if (!CarrierTemplate.hasCountdownToken(settings.template)) return@withContext
                    // force=false: the applier skips the write when the rendered text is unchanged.
                    val outcome = container.carrierApplier.apply()
                    Log.d(TAG, "wake -> ${outcome.text} (${outcome.result}) skipped=${outcome.skipped}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScreenWakeReceiver"

        fun register(context: Context): ScreenWakeReceiver {
            val receiver = ScreenWakeReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                // Fires once a minute, and only while the device is awake — keeps {kalan} honest
                // while the screen stays on without an extra alarm.
                addAction(Intent.ACTION_TIME_TICK)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            return receiver
        }
    }
}
