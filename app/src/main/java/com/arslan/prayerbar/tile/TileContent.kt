package com.arslan.prayerbar.tile

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.arslan.prayerbar.R
import com.arslan.prayerbar.carrier.CarrierTemplate
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.visual
import java.time.Duration
import java.time.Instant

/** Everything the tile draws, resolved once so the service and the in-app preview cannot drift. */
data class TileContent(
    val label: String,
    val subtitle: String?,
    val prayer: PrayerName?,
    @param:DrawableRes val iconRes: Int,
    @param:ColorInt val color: Int,
    /** True when the prayer is inside the highlight window — the tile's active state. */
    val highlighted: Boolean,
)

/**
 * Renders [TileContent] from settings. Kept apart from `CarrierApplier` because the tile is a
 * separate surface with its own templates, and apart from the service so the settings screen can
 * preview the exact same result without a live tile.
 */
object TileRenderer {

    fun render(
        context: Context,
        settings: PrayerSettings,
        next: NextPrayer?,
        now: Instant = Instant.now(),
    ): TileContent {
        val tile = settings.tile
        val fallbackColor = 0xFF6C7BD1.toInt()
        if (next == null) {
            return TileContent(
                label = context.getString(R.string.app_name),
                subtitle = context.getString(R.string.tile_no_location),
                prayer = null,
                iconRes = R.drawable.ic_tile_prayer,
                color = fallbackColor,
                highlighted = false,
            )
        }

        val visual = next.name.visual
        val remaining = Duration.between(now, next.at)
        val highlighted = tile.highlightMinutes > 0 &&
            !remaining.isNegative &&
            remaining <= Duration.ofMinutes(tile.highlightMinutes.toLong())

        return TileContent(
            label = render(context, tile.labelTemplate(settings.template), settings, next, now),
            subtitle = tile.subtitleTemplate
                .takeIf { it.isNotBlank() }
                ?.let { render(context, it, settings, next, now) }
                ?.takeIf { it.isNotBlank() },
            prayer = next.name,
            iconRes = if (tile.showIcon) visual.iconRes else R.drawable.ic_tile_prayer,
            color = visual.color,
            highlighted = highlighted,
        )
    }

    private fun render(
        context: Context,
        template: String,
        settings: PrayerSettings,
        next: NextPrayer,
        now: Instant,
    ): String = CarrierTemplate.render(
        template = template,
        next = next,
        now = now,
        labelOf = { context.getString(it.labelRes) },
        use24Hour = settings.use24Hour,
    )
}
