package com.arslan.prayerbar.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arslan.prayerbar.MainActivity
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the process alive so the screen-wake refresh actually happens.
 *
 * A runtime-registered receiver dies with the process, and the system reclaims an idle app within
 * minutes: after a long idle the label was only refreshed once the user opened the app or the next
 * boundary alarm fired. A foreground service is the only way to hold a live SCREEN_ON registration.
 */
class CarrierService : android.app.Service() {

    private var receiver: ScreenWakeReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(getString(R.string.service_notification_text))
        receiver = ScreenWakeReceiver.register(this)
        Log.d(TAG, "started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Refresh on every (re)start: a boundary alarm or the safety net may have revived us.
        val container = PrayerBarApp.container(this)
        container.launch {
            withContext(Dispatchers.IO) {
                val settings = container.settingsRepository.current()
                if (!settings.enabled) {
                    stopSelf()
                    return@withContext
                }
                val outcome = container.carrierApplier.apply()
                outcome.text?.let { updateNotification(it) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        // IMPORTANCE_LOW: no sound, no heads-up. The notification itself cannot be hidden — a
        // foreground service must show one — but the user can silence the channel.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            // Android forbids hiding a foreground-service notification, but a long-press opens the
            // channel settings where the user can silence or minimise it — say so on the notification.
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text + "\n\n" + getString(R.string.service_notification_hint)),
            )
            .setSubText(getString(R.string.service_notification_hint))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startForegroundCompat(text: String) {
        val notification = notification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
    }

    companion object {
        private const val TAG = "CarrierService"
        private const val CHANNEL_ID = "prayerbar-carrier"
        private const val NOTIFICATION_ID = 42

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
