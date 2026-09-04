package com.arslan.prayerbar.prayer

import io.github.meypod.adhan_kotlin.CalculationMethod
import io.github.meypod.adhan_kotlin.CalculationParameters
import io.github.meypod.adhan_kotlin.MidnightMethod
import io.github.meypod.adhan_kotlin.PrayerAdjustments
import kotlinx.serialization.Serializable

/** A place prayer times are computed for. Coordinates only — no city database is bundled. */
@Serializable
data class SavedLocation(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

/** Per-prayer manual offsets in minutes, applied on top of the calculated times. */
@Serializable
data class Adjustments(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val sunset: Int = 0,
    val isha: Int = 0,
    val midnight: Int = 0,
    val tahajjud: Int = 0,
) {
    fun toPrayerAdjustments(): PrayerAdjustments = PrayerAdjustments(
        fajr = fajr,
        sunrise = sunrise,
        dhuhr = dhuhr,
        asr = asr,
        maghrib = maghrib,
        sunset = sunset,
        isha = isha,
    )

    fun forPrayer(prayer: PrayerName): Int = when (prayer) {
        PrayerName.Fajr -> fajr
        PrayerName.Sunrise -> sunrise
        PrayerName.Dhuhr -> dhuhr
        PrayerName.Asr -> asr
        PrayerName.Maghrib -> maghrib
        PrayerName.Isha -> isha
        PrayerName.Midnight -> midnight
        PrayerName.Tahajjud -> tahajjud
    }

    fun withPrayer(prayer: PrayerName, minutes: Int): Adjustments = when (prayer) {
        PrayerName.Fajr -> copy(fajr = minutes)
        PrayerName.Sunrise -> copy(sunrise = minutes)
        PrayerName.Dhuhr -> copy(dhuhr = minutes)
        PrayerName.Asr -> copy(asr = minutes)
        PrayerName.Maghrib -> copy(maghrib = minutes)
        PrayerName.Isha -> copy(isha = minutes)
        PrayerName.Midnight -> copy(midnight = minutes)
        PrayerName.Tahajjud -> copy(tahajjud = minutes)
    }
}

/**
 * The Quick Settings tile is its own surface: it has two text lines and an icon where the carrier
 * label has a single cramped string, so it gets its own templates rather than reusing the carrier
 * one — unless the user asks it to follow along.
 */
@Serializable
data class TileSettings(
    /** Blank means "whatever the carrier label says". */
    val template: String = "",
    /** Blank means no second line. */
    val subtitleTemplate: String = "",
    val showIcon: Boolean = true,
    /**
     * Minutes before the prayer at which the tile switches to its active (accent-coloured) state.
     * 0 disables the highlight. This is the only colour Quick Settings lets an app drive.
     */
    val highlightMinutes: Int = 0,
) {
    /** The label template actually used, falling back to the carrier one. */
    fun labelTemplate(carrierTemplate: String): String = template.ifBlank { carrierTemplate }

    companion object {
        val HIGHLIGHT_CHOICES = listOf(0, 5, 10, 15, 30, 60)
    }
}

/** Everything the app persists, stored as a single JSON blob in DataStore. */
@Serializable
data class PrayerSettings(
    val parameters: CalculationParameters = CalculationMethod.TURKEY.parameters,
    val adjustments: Adjustments = Adjustments(),
    val midnightMethod: MidnightMethod = MidnightMethod.SunsetToFajr,
    val locations: List<SavedLocation> = emptyList(),
    val activeLocationId: String? = null,
    val template: String = defaultTemplate(),
    val trackedPrayers: Set<PrayerName> = OBLIGATORY_PRAYERS,
    /** Empty means "every active SIM". */
    val targetSubIds: List<Int> = emptyList(),
    val enabled: Boolean = false,
    val use24Hour: Boolean = true,
    val lastAppliedText: String? = null,
    val tile: TileSettings = TileSettings(),
) {
    val activeLocation: SavedLocation?
        get() = locations.firstOrNull { it.id == activeLocationId } ?: locations.firstOrNull()

    /** Nothing can be computed before a location exists. */
    val isConfigured: Boolean get() = activeLocation != null

    companion object {
        /** Locale-spelled, so an English user is not handed a Turkish-looking template. */
        fun defaultTemplate(locale: java.util.Locale = java.util.Locale.getDefault()): String =
            TemplateToken.Prayer.spelling(locale) + " " + TemplateToken.Time.spelling(locale)
    }
}

/** Whether the user edited the angles away from the selected method's canonical values. */
fun CalculationParameters.isMethodModified(): Boolean {
    val base = method.parameters
    return fajrAngle != base.fajrAngle ||
        ishaAngle != base.ishaAngle ||
        ishaInterval != base.ishaInterval ||
        maghribAngle != base.maghribAngle
}
