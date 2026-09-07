package com.arslan.prayerbar.prayer

import kotlinx.serialization.Serializable

@Serializable
data class WidgetSettings(
    val visiblePrayers: Set<PrayerName> = OBLIGATORY_PRAYERS,
    val showCountdown: Boolean = true,
    val showSeconds: Boolean = false,
    val showProgress: Boolean = true,
    val showIcon: Boolean = true,
    val usePrayerColor: Boolean = true,
    val showHijri: Boolean = false,
    val showLocation: Boolean = false,
    val backgroundAlpha: Int = 100,
    val followSystemTheme: Boolean = true,
) {
    val orderedPrayers: List<PrayerName>
        get() = PRAYERS_IN_ORDER.filter { it in visiblePrayers }

    val hasCountdown: Boolean get() = showCountdown || showProgress

    fun togglePrayer(prayer: PrayerName): WidgetSettings {
        val next = if (prayer in visiblePrayers) visiblePrayers - prayer else visiblePrayers + prayer
        return if (next.isEmpty()) this else copy(visiblePrayers = next)
    }

    companion object {
        val ALPHA_CHOICES = listOf(0, 25, 50, 75, 100)
    }
}
