package com.arslan.prayerbar.prayer

import androidx.annotation.StringRes
import com.arslan.prayerbar.R
import kotlinx.serialization.Serializable
import java.time.Instant

/** The three stretches of the day in which prayer is disliked, each tied to the sun's position. */
enum class KerahatKind(@param:StringRes val labelRes: Int) {
    /** From sunrise until the sun has cleared the horizon. */
    Sunrise(R.string.kerahat_sunrise),

    /** The sun at its highest, up to Dhuhr. */
    Zenith(R.string.kerahat_zenith),

    /** The sun yellowing on its way down, up to Maghrib. */
    Sunset(R.string.kerahat_sunset),
}

/**
 * The windows have no astronomical definition every school agrees on; calendars print them as a
 * fixed number of minutes around sunrise, noon and sunset. 45 is the figure in common use in Turkey,
 * and each one is left adjustable for those who follow another.
 */
@Serializable
data class KerahatSettings(
    val enabled: Boolean = true,
    val sunriseMinutes: Int = DEFAULT_MINUTES,
    val zenithMinutes: Int = DEFAULT_MINUTES,
    val sunsetMinutes: Int = DEFAULT_MINUTES,
) {
    fun minutes(kind: KerahatKind): Int = when (kind) {
        KerahatKind.Sunrise -> sunriseMinutes
        KerahatKind.Zenith -> zenithMinutes
        KerahatKind.Sunset -> sunsetMinutes
    }

    fun withMinutes(kind: KerahatKind, minutes: Int): KerahatSettings {
        val clamped = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        return when (kind) {
            KerahatKind.Sunrise -> copy(sunriseMinutes = clamped)
            KerahatKind.Zenith -> copy(zenithMinutes = clamped)
            KerahatKind.Sunset -> copy(sunsetMinutes = clamped)
        }
    }

    companion object {
        const val DEFAULT_MINUTES = 45
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 90
        const val STEP_MINUTES = 5
    }
}

data class KerahatWindow(
    val kind: KerahatKind,
    val start: Instant,
    val end: Instant,
) {
    fun contains(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)

    /** 0f as the window opens, 1f as it closes. */
    fun progress(now: Instant): Float {
        val span = end.toEpochMilli() - start.toEpochMilli()
        if (span <= 0L) return 0f
        return ((now.toEpochMilli() - start.toEpochMilli()).toFloat() / span).coerceIn(0f, 1f)
    }
}

/** The day's windows in chronological order, or none when the user has switched them off. */
fun DayTimes.kerahatWindows(settings: KerahatSettings): List<KerahatWindow> {
    if (!settings.enabled) return emptyList()
    val sunrise = at(PrayerName.Sunrise)
    val dhuhr = at(PrayerName.Dhuhr)
    val maghrib = at(PrayerName.Maghrib)
    return listOf(
        KerahatWindow(
            KerahatKind.Sunrise,
            sunrise,
            sunrise.plusSeconds(settings.sunriseMinutes * 60L),
        ),
        KerahatWindow(KerahatKind.Zenith, dhuhr.minusSeconds(settings.zenithMinutes * 60L), dhuhr),
        KerahatWindow(
            KerahatKind.Sunset,
            maghrib.minusSeconds(settings.sunsetMinutes * 60L),
            maghrib,
        ),
    )
}

/** The window [now] falls in, or failing that the next one to open. */
fun List<KerahatWindow>.currentOrNext(now: Instant): KerahatWindow? =
    sortedBy { it.start }.firstOrNull { it.end.isAfter(now) }
