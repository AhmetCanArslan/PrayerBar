package com.arslan.prayerbar.prayer

import com.arslan.prayerbar.carrier.CarrierTemplate
import io.github.meypod.adhan_kotlin.CalculationMethod
import io.github.meypod.adhan_kotlin.CalculationParameters
import io.github.meypod.adhan_kotlin.MidnightMethod
import io.github.meypod.adhan_kotlin.PrayerAdjustments
import kotlinx.serialization.Serializable

@Serializable
data class SavedLocation(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    /** The place the coordinates fall in, once the geocoder has been asked. */
    val city: String? = null,
)

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

@Serializable
data class TileSettings(

    val enabled: Boolean = false,
    val template: String = "",
    val subtitleTemplate: String = "",
    val showIcon: Boolean = true,
    val highlightMinutes: Int = 0,
) {
    fun labelTemplate(): String = template.ifBlank { PrayerSettings.defaultTemplate() }

    val hasCountdown: Boolean
        get() = CarrierTemplate.hasCountdownToken(labelTemplate()) ||
            CarrierTemplate.hasCountdownToken(subtitleTemplate)

    companion object {
        val HIGHLIGHT_CHOICES = listOf(0, 5, 10, 15, 30, 60)
    }
}

@Serializable
data class PrayerSettings(
    val parameters: CalculationParameters = CalculationMethod.TURKEY.parameters,
    val adjustments: Adjustments = Adjustments(),
    val midnightMethod: MidnightMethod = MidnightMethod.SunsetToFajr,
    val locations: List<SavedLocation> = emptyList(),
    val activeLocationId: String? = null,
    val template: String = defaultTemplate(),
    val trackedPrayers: Set<PrayerName> = OBLIGATORY_PRAYERS,
    val targetSubIds: List<Int> = emptyList(),
    val enabled: Boolean = false,
    val use24Hour: Boolean = true,
    val lastAppliedText: String? = null,
    val tile: TileSettings = TileSettings(),
    val notification: NotificationSettings = NotificationSettings(),
    val widgets: Map<Int, WidgetSettings> = emptyMap(),
) {
    val hasActiveSurface: Boolean get() = enabled || tile.enabled || notification.enabled

    fun widget(appWidgetId: Int): WidgetSettings = widgets[appWidgetId] ?: WidgetSettings()

    fun withWidget(appWidgetId: Int, transform: (WidgetSettings) -> WidgetSettings): PrayerSettings =
        copy(widgets = widgets + (appWidgetId to transform(widget(appWidgetId))))

    val activeLocation: SavedLocation?
        get() = locations.firstOrNull { it.id == activeLocationId } ?: locations.firstOrNull()

    val isConfigured: Boolean get() = activeLocation != null

    companion object {
        fun defaultTemplate(locale: java.util.Locale = java.util.Locale.getDefault()): String =
            TemplateToken.Prayer.spelling(locale) + " " + TemplateToken.Time.spelling(locale)
    }
}

fun CalculationParameters.isMethodModified(): Boolean {
    val base = method.parameters
    return fajrAngle != base.fajrAngle ||
        ishaAngle != base.ishaAngle ||
        ishaInterval != base.ishaInterval ||
        maghribAngle != base.maghribAngle
}
