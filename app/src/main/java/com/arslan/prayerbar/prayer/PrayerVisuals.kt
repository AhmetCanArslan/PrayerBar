package com.arslan.prayerbar.prayer

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.arslan.prayerbar.R

/** The icon and accent colour a day time is drawn with, in the tile and in the app. */
data class PrayerVisual(
    @param:DrawableRes val iconRes: Int,
    @param:ColorInt val color: Int,
)

/**
 * Sky colours, walked round the day: dawn violet, morning and noon gold, afternoon amber, sunset
 * red, then three deepening blues for the night. Adjacent times stay distinguishable at tile size.
 */
val PrayerName.visual: PrayerVisual
    get() = when (this) {
        PrayerName.Fajr -> PrayerVisual(R.drawable.ic_prayer_fajr, 0xFF6C7BD1.toInt())
        PrayerName.Sunrise -> PrayerVisual(R.drawable.ic_prayer_sunrise, 0xFFF2A03D.toInt())
        PrayerName.Dhuhr -> PrayerVisual(R.drawable.ic_prayer_dhuhr, 0xFFF5B301.toInt())
        PrayerName.Asr -> PrayerVisual(R.drawable.ic_prayer_asr, 0xFFE07A3E.toInt())
        PrayerName.Maghrib -> PrayerVisual(R.drawable.ic_prayer_maghrib, 0xFFD1503F.toInt())
        PrayerName.Isha -> PrayerVisual(R.drawable.ic_prayer_isha, 0xFF4A5B9E.toInt())
        PrayerName.Midnight -> PrayerVisual(R.drawable.ic_prayer_midnight, 0xFF344272.toInt())
        PrayerName.Tahajjud -> PrayerVisual(R.drawable.ic_prayer_tahajjud, 0xFF6B4FA0.toInt())
    }
