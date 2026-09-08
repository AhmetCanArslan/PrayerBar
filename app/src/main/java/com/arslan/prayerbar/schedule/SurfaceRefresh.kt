package com.arslan.prayerbar.schedule

import android.content.Context
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.carrier.CarrierResult
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.tile.PrayerTileService
import com.arslan.prayerbar.widget.PrayerWidgetProvider
import java.time.Instant
import java.time.ZoneId

data class RefreshOutcome(
    val active: Boolean,
    val next: NextPrayer? = null,
    val text: String? = null,
    val result: CarrierResult = CarrierResult.Ok,
)

object SurfaceRefresh {
    suspend fun run(
        context: Context,
        force: Boolean = false,
        persistent: Boolean = false,
    ): RefreshOutcome {
        val container = PrayerBarApp.container(context)
        val settings = container.settingsRepository.current()
        if (settings.enabled) {
            val outcome = container.carrierApplier.apply(force = force, persistent = persistent)
            PrayerWidgetProvider.refresh(context, settings)
            return RefreshOutcome(true, outcome.next, outcome.text, outcome.result)
        }
        // The status notification needs no refresh of its own here — the service posts it from the
        // resolved prayer below — but it does keep the service, and this refresh, alive.
        if (!settings.tile.enabled &&
            !settings.notification.enabled &&
            !PrayerWidgetProvider.hasWidgets(context)
        ) {
            return RefreshOutcome(active = false)
        }
        val next = container.resolver
            .resolve(Instant.now(), settings, ZoneId.systemDefault())
        if (settings.tile.enabled) PrayerTileService.refresh(context)
        PrayerWidgetProvider.refresh(context, settings)
        return RefreshOutcome(true, next)
    }
}
