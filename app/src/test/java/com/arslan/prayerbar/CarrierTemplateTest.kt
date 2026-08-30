package com.arslan.prayerbar

import com.arslan.prayerbar.carrier.CarrierTemplate
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class CarrierTemplateTest {

    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    private val date = LocalDate.of(2026, 8, 30)
    private val now = LocalDateTime.of(date, java.time.LocalTime.of(15, 0)).atZone(zone).toInstant()
    private val asrAt = LocalDateTime.of(date, java.time.LocalTime.of(15, 42)).atZone(zone).toInstant()
    private val dhuhrAt = LocalDateTime.of(date, java.time.LocalTime.of(12, 20)).atZone(zone).toInstant()

    private val next = NextPrayer(
        name = PrayerName.Asr,
        at = asrAt,
        previousName = PrayerName.Dhuhr,
        previousAt = dhuhrAt,
        today = DayTimes(date, zone, emptyMap()),
    )

    private val labels = mapOf(
        PrayerName.Asr to "İkindi",
        PrayerName.Dhuhr to "Öğle",
    )

    private fun render(template: String, locale: Locale = Locale("tr")) = CarrierTemplate.render(
        template = template,
        next = next,
        now = now,
        labelOf = { labels.getValue(it) },
        zone = zone,
        use24Hour = true,
        locale = locale,
    )

    @Test
    fun `prayer and clock tokens render`() {
        assertEquals("İkindi 15:42", render("{vakit} {saat}"))
        assertEquals("İkindi 15:42", render("{prayer} {time}"))
    }

    @Test
    fun `remaining token is compact and localised`() {
        assertEquals("İkindi · 42dk", render("{vakit} · {kalan}"))
        assertEquals("Asr 42m", CarrierTemplate.render(
            template = "{prayer} {remaining}",
            next = next,
            now = now,
            labelOf = { "Asr" },
            zone = zone,
            locale = Locale.ENGLISH,
        ))
    }

    @Test
    fun `previous prayer token renders`() {
        assertEquals("Öğle → İkindi", render("{onceki} → {vakit}"))
    }

    @Test
    fun `unknown tokens are left untouched`() {
        assertEquals("{bilinmeyen} İkindi", render("{bilinmeyen} {vakit}"))
    }

    @Test
    fun `output is clamped to the carrier label limit`() {
        val long = render("{vakit} ".repeat(20))
        assertTrue(long.length <= 32)
    }
}
