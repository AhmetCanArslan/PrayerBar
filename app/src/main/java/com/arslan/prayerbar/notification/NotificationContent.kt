package com.arslan.prayerbar.notification

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.arslan.prayerbar.R
import com.arslan.prayerbar.carrier.CarrierTemplate
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.NotificationSettings
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.hijriDate
import com.arslan.prayerbar.prayer.visual
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import java.util.Locale

/** One time of the day in the expanded notification. */
data class NotificationLine(
    val prayer: PrayerName,
    val label: String,
    val time: String,
    val isNext: Boolean,
    val elapsed: Boolean,
)

/** Everything the status notification draws, resolved once so the shade and the preview agree. */
data class NotificationContent(
    val configured: Boolean,
    val title: String,
    val text: String,
    val meta: String,
    /** Wall-clock millis the chronometer counts down to. */
    val endsAtMillis: Long,
    val remainingMillis: Long,
    val showChronometer: Boolean,
    val progress: Int,
    val showProgress: Boolean,
    @param:DrawableRes val iconRes: Int,
    @param:ColorInt val accent: Int,
    val colorized: Boolean,
    val lines: List<NotificationLine>,
) {
    companion object {
        /** Progress is carried in permille so the bar moves smoothly between two prayers. */
        const val PROGRESS_MAX = 1000
    }
}

/**
 * Renders [NotificationContent] from settings. Pure, like [com.arslan.prayerbar.tile.TileRenderer]:
 * the service posts it and the settings screen previews it from the same call.
 */
object NotificationRenderer {

    fun render(
        context: Context,
        settings: PrayerSettings,
        next: NextPrayer?,
        today: DayTimes?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): NotificationContent {
        val options = settings.notification
        val fallbackAccent = context.getColor(R.color.widget_accent)
        if (next == null) {
            return NotificationContent(
                configured = false,
                title = context.getString(R.string.app_name),
                text = context.getString(R.string.tile_no_location),
                meta = "",
                endsAtMillis = now.toEpochMilli(),
                remainingMillis = 0L,
                showChronometer = false,
                progress = 0,
                showProgress = false,
                iconRes = R.drawable.ic_tile_prayer,
                accent = fallbackAccent,
                colorized = false,
                lines = emptyList(),
            )
        }

        val visual = next.name.visual
        val remaining = Duration.between(now, next.at).coerceAtLeast(Duration.ZERO)

        return NotificationContent(
            configured = true,
            title = render(context, options.titleTemplate(), settings, next, now, zone, locale),
            text = render(context, options.bodyTemplate(locale), settings, next, now, zone, locale),
            meta = meta(settings, options, now, zone),
            endsAtMillis = next.at.toEpochMilli(),
            remainingMillis = remaining.toMillis(),
            showChronometer = options.showChronometer,
            progress = (next.progress(now) * NotificationContent.PROGRESS_MAX).toInt()
                .coerceIn(0, NotificationContent.PROGRESS_MAX),
            showProgress = options.showProgress,
            iconRes = if (options.showIcon) visual.iconRes else R.drawable.ic_tile_prayer,
            accent = if (options.usePrayerColor) visual.color else fallbackAccent,
            colorized = options.colorized,
            lines = lines(context, settings, options, next, today, now),
        )
    }

    private fun meta(
        settings: PrayerSettings,
        options: NotificationSettings,
        now: Instant,
        zone: ZoneId,
    ): String = buildList {
        if (options.showLocation) settings.activeLocation?.label?.let(::add)
        if (options.showHijri) {
            now.atZone(zone).toLocalDate().hijriDate()?.let { date ->
                add("%d.%d".format(date[ChronoField.DAY_OF_MONTH], date[ChronoField.MONTH_OF_YEAR]))
            }
        }
    }.joinToString(" · ")

    /**
     * The day's times, trimmed to what the shade will actually draw. Once the list is longer than
     * that, the ones already called are dropped first — the point of the list is what is still to come.
     */
    private fun lines(
        context: Context,
        settings: PrayerSettings,
        options: NotificationSettings,
        next: NextPrayer,
        today: DayTimes?,
        now: Instant,
    ): List<NotificationLine> {
        if (!options.showTimeline || today == null) return emptyList()
        val all = options.orderedPrayers.map { prayer ->
            val at = today.at(prayer)
            NotificationLine(
                prayer = prayer,
                label = context.getString(prayer.labelRes),
                time = CountdownFormat.clock(at, today.zone, settings.use24Hour),
                isNext = prayer == next.name,
                elapsed = at.isBefore(now),
            )
        }
        val capacity = NotificationSettings.MAX_TIMELINE_LINES
        if (all.size <= capacity) return all
        val upcoming = all.filterNot { it.elapsed }
        if (upcoming.size >= capacity) return upcoming.take(capacity)
        return all.filter { it.elapsed }.takeLast(capacity - upcoming.size) + upcoming
    }

    private fun render(
        context: Context,
        template: String,
        settings: PrayerSettings,
        next: NextPrayer,
        now: Instant,
        zone: ZoneId,
        locale: Locale,
    ): String = CarrierTemplate.render(
        template = template,
        next = next,
        now = now,
        labelOf = { context.getString(it.labelRes) },
        zone = zone,
        use24Hour = settings.use24Hour,
        locale = locale,
    )
}
