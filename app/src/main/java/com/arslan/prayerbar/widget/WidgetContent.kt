package com.arslan.prayerbar.widget

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.WidgetSettings
import com.arslan.prayerbar.prayer.hijriDate
import com.arslan.prayerbar.prayer.visual
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import java.util.Locale

data class WidgetRow(
    val prayer: PrayerName,
    val label: String,
    val time: String,
    val isNext: Boolean,
    val elapsed: Boolean,
    @param:DrawableRes val iconRes: Int,
    @param:ColorInt val color: Int,
)

data class WidgetContent(
    val configured: Boolean,
    val headline: String,
    val emphasis: String,
    val remainingMillis: Long,
    val time: String,
    val meta: String,
    val progress: Int,
    val showProgress: Boolean,
    @param:DrawableRes val iconRes: Int,
    @param:ColorInt val accent: Int,
    val rows: List<WidgetRow>,
)

object WidgetRenderer {
    fun render(
        context: Context,
        settings: PrayerSettings,
        widget: WidgetSettings,
        next: NextPrayer?,
        today: DayTimes?,
        capacity: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): WidgetContent {
        val fallbackAccent = context.getColor(R.color.widget_accent)
        if (next == null || today == null) {
            return WidgetContent(
                configured = false,
                headline = context.getString(R.string.app_name),
                emphasis = "—",
                remainingMillis = 0L,
                time = "",
                meta = context.getString(R.string.tile_no_location),
                progress = 0,
                showProgress = false,
                iconRes = R.drawable.ic_tile_prayer,
                accent = fallbackAccent,
                rows = emptyList(),
            )
        }

        val visual = next.name.visual
        val accent = if (widget.usePrayerColor) visual.color else fallbackAccent
        val remaining = Duration.between(now, next.at)
        val clock = CountdownFormat.clock(next.at, zone, settings.use24Hour, locale)
        val countdown = if (widget.showSeconds) {
            CountdownFormat.remainingChronometer(remaining, locale)
        } else {
            CountdownFormat.remainingShort(remaining, locale)
        }

        return WidgetContent(
            configured = true,
            headline = context.getString(next.name.labelRes),
            emphasis = if (widget.showCountdown) countdown else clock,
            remainingMillis = remaining.toMillis().coerceAtLeast(0L),
            time = clock,
            meta = meta(context, settings, widget, now, zone),
            progress = (next.progress(now) * 1000f).toInt().coerceIn(0, 1000),
            showProgress = widget.showProgress,
            iconRes = visual.iconRes,
            accent = accent,
            rows = rows(context, settings, widget, next, today, capacity, now, fallbackAccent),
        )
    }

    private fun meta(
        context: Context,
        settings: PrayerSettings,
        widget: WidgetSettings,
        now: Instant,
        zone: ZoneId,
    ): String {
        val parts = buildList {
            if (widget.showLocation) settings.activeLocation?.label?.let(::add)
            if (widget.showHijri) {
                now.atZone(zone).toLocalDate().hijriDate()?.let { date ->
                    add(
                        "%d.%d".format(
                            date[ChronoField.DAY_OF_MONTH],
                            date[ChronoField.MONTH_OF_YEAR],
                        ),
                    )
                }
            }
        }
        return parts.joinToString(" · ")
    }

    private fun rows(
        context: Context,
        settings: PrayerSettings,
        widget: WidgetSettings,
        next: NextPrayer,
        today: DayTimes,
        capacity: Int,
        now: Instant,
        @ColorInt fallbackAccent: Int,
    ): List<WidgetRow> {
        if (capacity <= 0) return emptyList()
        val all = widget.orderedPrayers.map { prayer ->
            val at = today.at(prayer)
            WidgetRow(
                prayer = prayer,
                label = context.getString(prayer.labelRes),
                time = CountdownFormat.clock(at, today.zone, settings.use24Hour),
                isNext = prayer == next.name,
                elapsed = at.isBefore(now),
                iconRes = prayer.visual.iconRes,
                color = if (widget.usePrayerColor) prayer.visual.color else fallbackAccent,
            )
        }
        if (all.size <= capacity) return all
        val upcoming = all.filterNot { it.elapsed }

        if (upcoming.size >= capacity) return upcoming.take(capacity)

        val elapsed = all.filter { it.elapsed }
        return (elapsed.takeLast(capacity - upcoming.size) + upcoming)
    }
}
