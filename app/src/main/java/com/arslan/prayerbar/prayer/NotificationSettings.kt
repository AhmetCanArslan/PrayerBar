package com.arslan.prayerbar.prayer

import com.arslan.prayerbar.carrier.CarrierTemplate
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * The ongoing status notification — a surface of its own, like the tile: its own templates, its own
 * switch and its own notification channel, so it can be silenced or hidden without touching the rest
 * of the app.
 */
@Serializable
data class NotificationSettings(
    val enabled: Boolean = false,
    val template: String = "",
    val subtitleTemplate: String = "",
    val showChronometer: Boolean = true,
    val showProgress: Boolean = true,
    val showTimeline: Boolean = true,
    val visiblePrayers: Set<PrayerName> = OBLIGATORY_PRAYERS,
    val showIcon: Boolean = true,
    val usePrayerColor: Boolean = true,
    val colorized: Boolean = false,
    val showHijri: Boolean = false,
    val showLocation: Boolean = true,
) {
    fun titleTemplate(): String = template.ifBlank { PrayerSettings.defaultTemplate() }

    fun bodyTemplate(locale: Locale = Locale.getDefault()): String =
        subtitleTemplate.ifBlank { defaultBodyTemplate(locale) }

    val orderedPrayers: List<PrayerName>
        get() = PRAYERS_IN_ORDER.filter { it in visiblePrayers }

    /**
     * Whether anything drawn drifts between two boundaries. The chronometer is not part of it: the
     * platform ticks that one down on its own, without the app being awake at all.
     */
    val hasCountdown: Boolean
        get() = showProgress ||
            CarrierTemplate.hasCountdownToken(titleTemplate()) ||
            CarrierTemplate.hasCountdownToken(bodyTemplate())

    fun togglePrayer(prayer: PrayerName): NotificationSettings {
        val next = if (prayer in visiblePrayers) visiblePrayers - prayer else visiblePrayers + prayer
        return if (next.isEmpty()) this else copy(visiblePrayers = next)
    }

    companion object {
        /** The shade draws this many lines of an inbox-style notification and folds away the rest. */
        const val MAX_TIMELINE_LINES = 6

        fun defaultBodyTemplate(locale: Locale = Locale.getDefault()): String =
            TemplateToken.Remaining.spelling(locale)
    }
}
