package com.arslan.prayerbar.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arslan.prayerbar.PrayerBarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Non-persistent overrides are dropped on reboot, and a time-zone or clock change invalidates the
 * armed alarm, so both re-apply and re-arm here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val container = PrayerBarApp.container(context)
        container.launch {
            try {
                withContext(Dispatchers.IO) {
                    val settings = container.settingsRepository.current()
                    if (!settings.enabled) return@withContext
                    val outcome = container.carrierApplier.apply(force = true, persistent = true)
                    container.alarmScheduler.schedule(outcome.next)
                    container.scheduleSafetyNet()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
