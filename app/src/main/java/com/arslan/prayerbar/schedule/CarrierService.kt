package com.arslan.prayerbar.schedule

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.notification.PrayerNotifier
import com.arslan.prayerbar.tile.TileRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the process alive so the screen-wake refresh actually happens.
 *
 * A runtime-registered receiver dies with the process, and the system reclaims an idle app within
 * minutes: after a long idle the label was only refreshed once the user opened the app or the next
 * boundary alarm fired. A foreground service is the only way to hold a live SCREEN_ON registration.
 *
 * It runs for any of the surfaces — the carrier label, the Quick Settings tile, the status
 * notification or a placed widget — and stops itself once they are all off. Its mandatory
 * notification is built by [PrayerNotifier]: the prayer status when that surface is on, a bare
 * notice otherwise.
 */
class CarrierService : android.app.Service() {

    private var receiver: ScreenWakeReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PrayerNotifier.createChannels(this)
        // Settings live on disk and startForeground cannot wait for them: post whatever was last
        // shown, then let onStartCommand replace it a few milliseconds later.
        startForegroundCompat(PrayerNotifier.placeholder(this))
        receiver = ScreenWakeReceiver.register(this)
        Log.d(TAG, "started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Refresh on every (re)start: a boundary alarm or the safety net may have revived us.
        val container = PrayerBarApp.container(this)
        container.launch {
            withContext(Dispatchers.IO) {
                val settings = container.settingsRepository.current()
                val outcome = SurfaceRefresh.run(this@CarrierService)
                if (!outcome.active) {
                    stopSelf()
                    return@withContext
                }
                // In tile-only mode there is no carrier text to report, so the bare notice borrows
                // the tile's own label rather than sitting on a stale one.
                val text = outcome.text
                    ?: TileRenderer.render(this@CarrierService, settings, outcome.next).label
                // startForeground rather than notify: the status and the bare notice sit in
                // different channels, and a switch between them has to go through the service.
                startForegroundCompat(
                    PrayerNotifier.build(this@CarrierService, settings, outcome.next, text),
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    PrayerNotifier.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(PrayerNotifier.NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.w(TAG, "foreground refused", it) }
    }

    companion object {
        private const val TAG = "CarrierService"

        /** Safe from anywhere: a disabled or already-running service is a no-op. */
        fun start(context: Context) {
            val intent = Intent(context, CarrierService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "start refused", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CarrierService::class.java))
        }
    }
}
