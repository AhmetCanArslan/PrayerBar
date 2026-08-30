@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.arslan.prayerbar.prayer

import io.github.meypod.adhan_kotlin.CalculationMethod
import io.github.meypod.adhan_kotlin.CalculationParameters
import io.github.meypod.adhan_kotlin.Coordinates
import io.github.meypod.adhan_kotlin.PrayerTimes
import io.github.meypod.adhan_kotlin.SunnahTimes
import io.github.meypod.adhan_kotlin.data.DateComponents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.time.Instant as KotlinInstant

/** All sharia times of a single local day, already adjusted. */
data class DayTimes(
    val date: LocalDate,
    val zone: ZoneId,
    val times: Map<PrayerName, Instant>,
) {
    fun at(prayer: PrayerName): Instant = times.getValue(prayer)

    /** Times of the given set, chronologically ordered. */
    fun ordered(tracked: Set<PrayerName>): List<Pair<PrayerName, Instant>> =
        PRAYERS_IN_ORDER.filter { it in tracked }.map { it to at(it) }
}

/**
 * Wraps the adhan engine. Mirrors al-azan's `GetShariaTimesUseCase`: per-prayer minute offsets are
 * pushed into the library through [CalculationParameters.prayerAdjustments], while the sunnah
 * (midnight/tahajjud) offsets are applied afterwards.
 */
class PrayerTimesCalculator {

    fun timesFor(
        date: LocalDate,
        settings: PrayerSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DayTimes? {
        val location = settings.activeLocation ?: return null
        return timesFor(date, settings, Coordinates(location.latitude, location.longitude), zone)
    }

    fun timesFor(
        date: LocalDate,
        settings: PrayerSettings,
        coordinates: Coordinates,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DayTimes {
        val parameters = settings.parameters
            .let { params ->
                // Umm al-Qura switches Isha to a fixed 120 minute interval during Ramadan.
                if (params.method == CalculationMethod.UMM_AL_QURA && date.isInRamadan()) {
                    params.copy(ishaInterval = 120)
                } else {
                    params
                }
            }
            .let { it.copy(prayerAdjustments = settings.adjustments.toPrayerAdjustments()) }

        val prayerTimes = PrayerTimes(
            coordinates,
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            parameters,
        )
        val sunnahTimes = SunnahTimes(prayerTimes, settings.midnightMethod)

        val times = mapOf(
            PrayerName.Fajr to prayerTimes.fajr.toJava(),
            PrayerName.Sunrise to prayerTimes.sunrise.toJava(),
            PrayerName.Dhuhr to prayerTimes.dhuhr.toJava(),
            PrayerName.Asr to prayerTimes.asr.toJava(),
            PrayerName.Maghrib to prayerTimes.maghrib.toJava(),
            PrayerName.Isha to prayerTimes.isha.toJava(),
            PrayerName.Midnight to sunnahTimes.middleOfTheNight.toJava()
                .plusSeconds(settings.adjustments.midnight * 60L),
            PrayerName.Tahajjud to sunnahTimes.lastThirdOfTheNight.toJava()
                .plusSeconds(settings.adjustments.tahajjud * 60L),
        )
        return DayTimes(date, zone, times)
    }
}

private fun KotlinInstant.toJava(): Instant = Instant.ofEpochMilli(toEpochMilliseconds())

/** Umm al-Qura Hijri month 9, via `java.time`'s Hijrah chronology. */
internal fun LocalDate.isInRamadan(): Boolean = runCatching {
    HijrahChronology.INSTANCE.date(this).get(ChronoField.MONTH_OF_YEAR) == 9
}.getOrDefault(false)

internal fun LocalDate.hijriDate(): HijrahDate? = runCatching {
    HijrahChronology.INSTANCE.date(this)
}.getOrNull()
