package com.arslan.prayerbar.prayer

import androidx.annotation.StringRes
import com.arslan.prayerbar.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The sharia day times PrayerBar can display and track.
 *
 * Declaration order is chronological within a day and every next/current lookup relies on it.
 */
@Serializable
enum class PrayerName(
    @param:StringRes val labelRes: Int,
    val isNonPrayer: Boolean = false,
) {
    @SerialName("fajr")
    Fajr(R.string.prayer_fajr),

    @SerialName("sunrise")
    Sunrise(R.string.prayer_sunrise, isNonPrayer = true),

    @SerialName("dhuhr")
    Dhuhr(R.string.prayer_dhuhr),

    @SerialName("asr")
    Asr(R.string.prayer_asr),

    @SerialName("maghrib")
    Maghrib(R.string.prayer_maghrib),

    @SerialName("isha")
    Isha(R.string.prayer_isha),

    /** Middle of the night. */
    @SerialName("midnight")
    Midnight(R.string.prayer_midnight, isNonPrayer = true),

    /** Last third of the night. */
    @SerialName("tahajjud")
    Tahajjud(R.string.prayer_tahajjud, isNonPrayer = true),
}

/** The five obligatory prayers — the default tracking set. */
val OBLIGATORY_PRAYERS: Set<PrayerName> = setOf(
    PrayerName.Fajr,
    PrayerName.Dhuhr,
    PrayerName.Asr,
    PrayerName.Maghrib,
    PrayerName.Isha,
)

val PRAYERS_IN_ORDER: List<PrayerName> = PrayerName.entries.toList()
