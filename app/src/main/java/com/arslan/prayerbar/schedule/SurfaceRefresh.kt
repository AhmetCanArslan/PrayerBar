package com.arslan.prayerbar.schedule

import android.content.Context
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.carrier.CarrierResult
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.tile.PrayerTileService
import java.time.Instant
import java.time.ZoneId

/**
 * What one refresh did. [active] is false when nothing is switched on, which is the signal for a
 * caller to stop re-arming alarms it inherited from a previous run.
 */
data class RefreshOutcome(
    val active: Boolean,
    val next: NextPrayer? = null,
    /** The carrier text that was written, or null in tile-only mode. */
    val text: String? = null,
    val result: CarrierResult = CarrierResult.Ok,
)

/**
 * The two surfaces are switched on and off separately, so every waking path — the boundary alarm,
 * boot, the safety net, the foreground service — has the same three-way decision to make: write the
 * carrier label, only redraw the tile, or do nothing at all. It is made here once.
 *
 * The carrier path needs no separate tile poke: [com.arslan.prayerbar.carrier.CarrierApplier]
 * refreshes the tile on its way through.
 */
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
            return RefreshOutcome(true, outcome.next, outcome.text, outcome.result)
        }
        if (!settings.tile.enabled) return RefreshOutcome(active = false)
        val next = container.resolver
            .resolve(Instant.now(), settings, ZoneId.systemDefault())
        PrayerTileService.refresh(context)
        return RefreshOutcome(true, next)
    }
}
