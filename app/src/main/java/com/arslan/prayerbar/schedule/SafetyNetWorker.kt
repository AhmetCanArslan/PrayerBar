package com.arslan.prayerbar.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arslan.prayerbar.PrayerBarApp

/**
 * Covers OEMs that quietly drop exact alarms: refreshes the live surfaces and re-arms the boundary
 * alarm a few times a day.
 */
class SafetyNetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = PrayerBarApp.container(applicationContext)
        val outcome = SurfaceRefresh.run(applicationContext, force = true)
        if (!outcome.active) return Result.success()
        container.alarmScheduler.schedule(outcome.next)
        CarrierService.start(applicationContext)
        return if (outcome.result.isOk) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "prayerbar-safety-net"
    }
}
