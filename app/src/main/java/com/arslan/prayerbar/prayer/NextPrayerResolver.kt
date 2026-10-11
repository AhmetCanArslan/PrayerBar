package com.arslan.prayerbar.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The upcoming tracked prayer plus the one it follows — enough to render a label and to draw the
 * progress between the two.
 */
data class NextPrayer(
    val name: PrayerName,
    val at: Instant,
    val previousName: PrayerName?,
    val previousAt: Instant?,
    val today: DayTimes,
    /** The kerahat window running now, or the next one to open. */
    val kerahat: KerahatWindow? = null,
) {
    /**
     * When a surface that draws the kerahat state has to be redrawn: the prayer boundary, or the
     * window opening or closing if that comes first.
     */
    fun wakeAt(now: Instant): Instant {
        val window = kerahat ?: return at
        val edge = if (window.contains(now)) window.end else window.start
        return if (edge.isAfter(now) && edge.isBefore(at)) edge else at
    }

    /** 0f right after the previous prayer, 1f at [at]. */
    fun progress(now: Instant): Float {
        val from = previousAt ?: return 0f
        val span = at.toEpochMilli() - from.toEpochMilli()
        if (span <= 0L) return 0f
        val done = (now.toEpochMilli() - from.toEpochMilli()).toFloat() / span
        return done.coerceIn(0f, 1f)
    }
}

/**
 * Resolves "the next prayer that has not been called yet". Once Dhuhr passes it reports Asr, and
 * after Isha it rolls over to tomorrow's Fajr. Yesterday is included in the scan because Midnight
 * and Tahajjud belong to the previous prayer day.
 */
class NextPrayerResolver(private val calculator: PrayerTimesCalculator = PrayerTimesCalculator()) {

    fun resolve(
        now: Instant,
        settings: PrayerSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NextPrayer? {
        if (!settings.isConfigured) return null
        val tracked = settings.trackedPrayers.ifEmpty { OBLIGATORY_PRAYERS }
        val today = now.atZone(zone).toLocalDate()
        val days = (-1L..SEARCH_DAYS).mapNotNull { offset ->
            calculator.timesFor(today.plusDays(offset), settings, zone)
        }
        val todayTimes = days.firstOrNull { it.date == today } ?: return null

        val timeline = days
            .flatMap { day -> day.ordered(tracked) }
            .sortedBy { it.second }

        val index = timeline.indexOfFirst { it.second.isAfter(now) }
        if (index < 0) return null
        val (name, at) = timeline[index]
        val previous = timeline.getOrNull(index - 1)
        return NextPrayer(
            name = name,
            at = at,
            previousName = previous?.first,
            previousAt = previous?.second,
            today = todayTimes,
            kerahat = days.flatMap { it.kerahatWindows(settings.kerahat) }.currentOrNext(now),
        )
    }

    /** The most recently elapsed tracked prayer, or null before the first one of the scan window. */
    fun current(
        now: Instant,
        settings: PrayerSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<PrayerName, Instant>? = resolve(now, settings, zone)
        ?.let { next -> next.previousName?.let { it to next.previousAt!! } }

    private companion object {
        const val SEARCH_DAYS = 2L
    }
}
