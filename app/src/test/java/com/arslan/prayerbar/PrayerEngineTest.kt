package com.arslan.prayerbar

import com.arslan.prayerbar.prayer.Adjustments
import com.arslan.prayerbar.prayer.KerahatKind
import com.arslan.prayerbar.prayer.KerahatSettings
import com.arslan.prayerbar.prayer.kerahatWindows
import com.arslan.prayerbar.prayer.NextPrayerResolver
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.PrayerTimesCalculator
import com.arslan.prayerbar.prayer.SavedLocation
import io.github.meypod.adhan_kotlin.CalculationMethod
import io.github.meypod.adhan_kotlin.Madhab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PrayerEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    private val istanbul = SavedLocation("home", "Istanbul", 41.0082, 28.9784)
    private val date = LocalDate.of(2026, 8, 30)

    private fun settings(
        method: CalculationMethod = CalculationMethod.TURKEY,
        madhab: Madhab = Madhab.SHAFI,
        adjustments: Adjustments = Adjustments(),
    ) = PrayerSettings(
        parameters = method.parameters.copy(madhab = madhab),
        adjustments = adjustments,
        locations = listOf(istanbul),
        activeLocationId = istanbul.id,
    )

    private fun at(hour: Int, minute: Int) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()

    @Test
    fun `next prayer after dhuhr is asr`() {
        val settings = settings()
        val times = PrayerTimesCalculator().timesFor(date, settings, zone)!!
        val justAfterDhuhr = times.at(PrayerName.Dhuhr).plusSeconds(60)

        val next = NextPrayerResolver().resolve(justAfterDhuhr, settings, zone)

        assertEquals(PrayerName.Asr, next?.name)
        assertEquals(PrayerName.Dhuhr, next?.previousName)
    }

    @Test
    fun `after isha it rolls over to tomorrow fajr`() {
        val settings = settings()
        val times = PrayerTimesCalculator().timesFor(date, settings, zone)!!
        val afterIsha = times.at(PrayerName.Isha).plusSeconds(60)

        val next = NextPrayerResolver().resolve(afterIsha, settings, zone)!!
        val tomorrow = PrayerTimesCalculator().timesFor(date.plusDays(1), settings, zone)!!

        assertEquals(PrayerName.Fajr, next.name)
        assertEquals(tomorrow.at(PrayerName.Fajr), next.at)
    }

    @Test
    fun `hanafi asr is later than shafi asr`() {
        val calculator = PrayerTimesCalculator()
        val shafi = calculator.timesFor(date, settings(madhab = Madhab.SHAFI), zone)!!
        val hanafi = calculator.timesFor(date, settings(madhab = Madhab.HANAFI), zone)!!

        assertTrue(hanafi.at(PrayerName.Asr).isAfter(shafi.at(PrayerName.Asr)))
        assertEquals(shafi.at(PrayerName.Dhuhr), hanafi.at(PrayerName.Dhuhr))
    }

    @Test
    fun `manual offsets shift times by exact minutes`() {
        val calculator = PrayerTimesCalculator()
        val base = calculator.timesFor(date, settings(), zone)!!
        val shifted = calculator.timesFor(
            date,
            settings(adjustments = Adjustments(fajr = 7, isha = -3)),
            zone,
        )!!

        assertEquals(
            base.at(PrayerName.Fajr).plusSeconds(7 * 60),
            shifted.at(PrayerName.Fajr),
        )
        assertEquals(
            base.at(PrayerName.Isha).minusSeconds(3 * 60),
            shifted.at(PrayerName.Isha),
        )
    }

    @Test
    fun `untracked prayers are skipped by the resolver`() {
        val settings = settings().copy(
            trackedPrayers = setOf(PrayerName.Fajr, PrayerName.Maghrib),
        )
        val next = NextPrayerResolver().resolve(at(12, 0), settings, zone)

        assertEquals(PrayerName.Maghrib, next?.name)
    }

    @Test
    fun `no location means nothing to resolve`() {
        val next = NextPrayerResolver().resolve(at(12, 0), PrayerSettings(), zone)
        assertEquals(null, next)
    }

    @Test
    fun `kerahat windows hang off sunrise, dhuhr and maghrib`() {
        val settings = settings().copy(
            kerahat = KerahatSettings(sunriseMinutes = 40, zenithMinutes = 30, sunsetMinutes = 50),
        )
        val times = PrayerTimesCalculator().timesFor(date, settings, zone)!!
        val windows = times.kerahatWindows(settings.kerahat)

        assertEquals(KerahatKind.entries.toList(), windows.map { it.kind })
        assertEquals(times.at(PrayerName.Sunrise), windows[0].start)
        assertEquals(times.at(PrayerName.Sunrise).plusSeconds(40 * 60), windows[0].end)
        assertEquals(times.at(PrayerName.Dhuhr).minusSeconds(30 * 60), windows[1].start)
        assertEquals(times.at(PrayerName.Dhuhr), windows[1].end)
        assertEquals(times.at(PrayerName.Maghrib).minusSeconds(50 * 60), windows[2].start)
        assertEquals(times.at(PrayerName.Maghrib), windows[2].end)
    }

    @Test
    fun `switching kerahat off leaves no windows and no extra wake-up`() {
        val settings = settings().copy(kerahat = KerahatSettings(enabled = false))
        val times = PrayerTimesCalculator().timesFor(date, settings, zone)!!
        assertTrue(times.kerahatWindows(settings.kerahat).isEmpty())

        val now = at(14, 0)
        val next = NextPrayerResolver().resolve(now, settings, zone)!!
        assertEquals(null, next.kerahat)
        assertEquals(next.at, next.wakeAt(now))
    }

    @Test
    fun `the resolver reports the running window and then the next one`() {
        val settings = settings()
        val times = PrayerTimesCalculator().timesFor(date, settings, zone)!!
        val resolver = NextPrayerResolver()

        val duringZenith = times.at(PrayerName.Dhuhr).minusSeconds(10 * 60)
        val running = resolver.resolve(duringZenith, settings, zone)!!.kerahat!!
        assertEquals(KerahatKind.Zenith, running.kind)
        assertTrue(running.contains(duringZenith))

        val upcoming = resolver.resolve(at(14, 0), settings, zone)!!.kerahat!!
        assertEquals(KerahatKind.Sunset, upcoming.kind)
        assertTrue(!upcoming.contains(at(14, 0)))

        // After sunset the day has none left, so it is tomorrow's sunrise.
        val afterIsha = times.at(PrayerName.Isha).plusSeconds(60)
        val tomorrow = PrayerTimesCalculator().timesFor(date.plusDays(1), settings, zone)!!
        val overnight = resolver.resolve(afterIsha, settings, zone)!!.kerahat!!
        assertEquals(KerahatKind.Sunrise, overnight.kind)
        assertEquals(tomorrow.at(PrayerName.Sunrise), overnight.start)
    }

    @Test
    fun `a kerahat edge before the next prayer is the earlier wake-up`() {
        val settings = settings()
        val times = PrayerTimesCalculator().timesFor(date, settings, zone)!!
        val resolver = NextPrayerResolver()
        val sunsetWindow = times.kerahatWindows(settings.kerahat).last()

        // Asr has passed, Maghrib is next and the window opens before it.
        val afterAsr = times.at(PrayerName.Asr).plusSeconds(60)
        val beforeWindow = resolver.resolve(afterAsr, settings, zone)!!
        assertEquals(PrayerName.Maghrib, beforeWindow.name)
        assertEquals(sunsetWindow.start, beforeWindow.wakeAt(afterAsr))

        // Inside it, the window closes on the prayer itself.
        val inside = sunsetWindow.start.plusSeconds(60)
        assertEquals(times.at(PrayerName.Maghrib), resolver.resolve(inside, settings, zone)!!.wakeAt(inside))

        // At 14:00 Asr comes before the window does, so the prayer stays the boundary.
        val early = resolver.resolve(at(14, 0), settings, zone)!!
        assertEquals(early.at, early.wakeAt(at(14, 0)))
    }
}
