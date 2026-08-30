package com.arslan.prayerbar

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arslan.prayerbar.carrier.CarrierApplier
import com.arslan.prayerbar.data.SettingsRepository
import com.arslan.prayerbar.prayer.NextPrayerResolver
import com.arslan.prayerbar.prayer.PrayerTimesCalculator
import com.arslan.prayerbar.schedule.PrayerAlarmScheduler
import com.arslan.prayerbar.schedule.SafetyNetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Hand-rolled container — the app is small enough that a DI framework would only add ceremony. */
class AppContainer(private val context: Context) {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }
    val calculator: PrayerTimesCalculator by lazy { PrayerTimesCalculator() }
    val resolver: NextPrayerResolver by lazy { NextPrayerResolver(calculator) }
    val carrierApplier: CarrierApplier by lazy {
        CarrierApplier(context, settingsRepository, resolver)
    }
    val alarmScheduler: PrayerAlarmScheduler by lazy { PrayerAlarmScheduler(context) }

    private val scope = CoroutineScope(SupervisorJob())

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    fun scheduleSafetyNet() {
        val request = PeriodicWorkRequestBuilder<SafetyNetWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SafetyNetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelSafetyNet() {
        WorkManager.getInstance(context).cancelUniqueWork(SafetyNetWorker.WORK_NAME)
    }
}

class PrayerBarApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        /** Receivers get the application context, which may not be this class under test. */
        fun container(context: Context): AppContainer {
            val app = context.applicationContext
            return if (app is PrayerBarApp) app.container else AppContainer(app)
        }
    }
}
