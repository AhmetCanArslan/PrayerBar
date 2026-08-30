package com.arslan.prayerbar.carrier

import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.hijriDate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Renders the carrier label from a user template. Pure text in, pure text out, so the format can be
 * previewed live in settings and asserted in unit tests.
 */
object CarrierTemplate {

    val PRESETS: List<String> = listOf(
        "{vakit} {saat}",
        "{vakit} · {kalan}",
        "{vakit}",
        "{saat}",
        "🕌 {vakit} {saat}",
        "{onceki} → {vakit} {saat}",
    )

    /** Token names accepted in a template, Turkish and English spellings alike. */
    val TOKENS: List<String> = listOf(
        "{vakit}", "{prayer}",
        "{saat}", "{time}",
        "{kalan}", "{remaining}",
        "{onceki}", "{previous}",
        "{hicri}", "{hijri}",
    )

    /** True when the template holds a token whose value drifts between prayer boundaries. */
    fun hasCountdownToken(template: String): Boolean =
        TOKEN_REGEX.findAll(template).any { it.groupValues[1].lowercase(Locale.ROOT) in COUNTDOWN_TOKENS }

    fun render(
        template: String,
        next: NextPrayer,
        now: Instant,
        labelOf: (PrayerName) -> String,
        zone: ZoneId = ZoneId.systemDefault(),
        use24Hour: Boolean = true,
        locale: Locale = Locale.getDefault(),
    ): String {
        val remaining = Duration.between(now, next.at)
        val previousLabel = next.previousName?.let(labelOf).orEmpty()
        val hijri = now.atZone(zone).toLocalDate().hijriDate()?.let { date ->
            "${date[java.time.temporal.ChronoField.DAY_OF_MONTH]}.${date[java.time.temporal.ChronoField.MONTH_OF_YEAR]}"
        }.orEmpty()

        val replacements = mapOf(
            "vakit" to labelOf(next.name),
            "prayer" to labelOf(next.name),
            "saat" to CountdownFormat.clock(next.at, zone, use24Hour, locale),
            "time" to CountdownFormat.clock(next.at, zone, use24Hour, locale),
            "kalan" to CountdownFormat.remainingShort(remaining, locale),
            "remaining" to CountdownFormat.remainingShort(remaining, locale),
            "onceki" to previousLabel,
            "previous" to previousLabel,
            "hicri" to hijri,
            "hijri" to hijri,
        )

        val rendered = TOKEN_REGEX.replace(template) { match ->
            replacements[match.groupValues[1].lowercase(Locale.ROOT)] ?: match.value
        }
        return rendered.trim().take(CarrierNameManager.MAX_LABEL_LENGTH)
    }

    private val COUNTDOWN_TOKENS = setOf("kalan", "remaining")

    // ICU's regex engine rejects an unescaped closing brace, so escape both.
    private val TOKEN_REGEX = Regex("\\{([A-Za-zçğıöşüÇĞİÖŞÜ]+)\\}")
}
