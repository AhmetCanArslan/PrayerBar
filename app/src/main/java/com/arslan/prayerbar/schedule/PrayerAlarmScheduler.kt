package com.arslan.prayerbar.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.arslan.prayerbar.prayer.NextPrayer
import java.time.Instant

/**
 * Arms one exact alarm at the next prayer boundary. The receiver re-arms the following one, so the
 * app wakes roughly six times a day and never polls.
 */
class PrayerAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** The permission only exists from Android 12; before that exact alarms are always allowed. */
    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(next: NextPrayer?) {
        val triggerAt = next?.at?.plusSeconds(BOUNDARY_GRACE_SECONDS)
            ?: Instant.now().plusSeconds(FALLBACK_INTERVAL_SECONDS)
        val triggerMillis = triggerAt.toEpochMilli()
        val pending = pendingIntent()
        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pending,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            }
            Log.d(TAG, "scheduled at $triggerAt exact=$canScheduleExact")
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PrayerTickReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val TAG = "PrayerAlarmScheduler"
        const val REQUEST_CODE = 1001

        /** Fire slightly after the boundary so the label never flips a second early. */
        const val BOUNDARY_GRACE_SECONDS = 2L
        const val FALLBACK_INTERVAL_SECONDS = 60L * 60L
    }
}
