package com.arslan.prayerbar.prayer

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock and countdown formatting, kept free of Android APIs so it can be unit tested. */
object CountdownFormat {

    private val FORMAT_24H: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val FORMAT_12H: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    fun clock(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        use24Hour: Boolean = true,
        locale: Locale = Locale.getDefault(),
    ): String = (if (use24Hour) FORMAT_24H else FORMAT_12H)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)

    /** Compact remaining time for the carrier label: `42dk`, `1sa 05dk`, `42m`, `1h 05m`. */
    fun remainingShort(remaining: Duration, locale: Locale = Locale.getDefault()): String {
        val total = remaining.coerceAtLeast(Duration.ZERO)
        val hours = total.toHours()
        val minutes = total.toMinutes() % 60
        val turkish = locale.language.equals("tr", ignoreCase = true)
        val hourUnit = if (turkish) "sa" else "h"
        val minuteUnit = if (turkish) "dk" else "m"
        return if (hours > 0) {
            String.format(locale, "%d%s %02d%s", hours, hourUnit, minutes, minuteUnit)
        } else {
            String.format(locale, "%d%s", minutes, minuteUnit)
        }
    }

    /** `HH:mm:ss` for the on-screen hero countdown. */
    fun remainingClock(remaining: Duration, locale: Locale = Locale.getDefault()): String {
        val total = remaining.coerceAtLeast(Duration.ZERO)
        return String.format(
            locale,
            "%02d:%02d:%02d",
            total.toHours(),
            total.toMinutes() % 60,
            total.seconds % 60,
        )
    }
}
