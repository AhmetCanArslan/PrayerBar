package com.arslan.prayerbar.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.carrier.CarrierTemplate
import com.arslan.prayerbar.notification.PrayerNotifier
import com.arslan.prayerbar.tile.PrayerTileService
import com.arslan.prayerbar.widget.PrayerWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class ScreenWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val container = PrayerBarApp.container(context)
        container.launch {
            try {
                withContext(Dispatchers.IO) {
                    val settings = container.settingsRepository.current()
                    val carrierDrifts = settings.enabled &&
                        CarrierTemplate.hasCountdownToken(settings.template)
                    val tileDrifts = settings.tile.enabled && settings.tile.hasCountdown
                    val widgetsDrift = PrayerWidgetProvider.ids(context)
                        .any { settings.widget(it).hasCountdown }
                    val notificationDrifts = settings.notification.enabled &&
                        settings.notification.hasCountdown
                    if (notificationDrifts) {
                        // The chronometer ticks itself; this is for the bar and the {kalan} tokens.
                        val next = container.resolver
                            .resolve(Instant.now(), settings, ZoneId.systemDefault())
                        PrayerNotifier.post(context, settings, next)
                    }
                    if (carrierDrifts) {
                        val outcome = container.carrierApplier.apply()
                        Log.d(TAG, "wake -> ${outcome.text} (${outcome.result}) skipped=${outcome.skipped}")
                    } else if (tileDrifts) {
                        PrayerTileService.refresh(context)
                        Log.d(TAG, "wake -> tile only")
                    }
                    if (widgetsDrift) PrayerWidgetProvider.refresh(context, settings)
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
