package com.arslan.prayerbar

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arslan.prayerbar.notification.NotificationContent
import com.arslan.prayerbar.notification.NotificationRenderer
import com.arslan.prayerbar.notification.PrayerNotifier
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.NextPrayerResolver
import com.arslan.prayerbar.prayer.NotificationSettings
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.PrayerTimesCalculator
import com.arslan.prayerbar.prayer.SavedLocation
import com.arslan.prayerbar.prayer.TemplateToken
import com.arslan.prayerbar.prayer.visual
import io.github.meypod.adhan_kotlin.CalculationMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationSurfaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    private val istanbul = SavedLocation("home", "Istanbul", 41.0082, 28.9784)
    private val date = LocalDate.of(2026, 8, 30)
    private val afternoon = LocalDateTime.of(date, LocalTime.of(14, 0)).atZone(zone).toInstant()

    private fun settings(options: NotificationSettings = NotificationSettings(enabled = true)) =
        PrayerSettings(
            parameters = CalculationMethod.TURKEY.parameters,
            locations = listOf(istanbul),
            activeLocationId = istanbul.id,
            notification = options,
        )

    private fun content(
        options: NotificationSettings = NotificationSettings(enabled = true),
        settings: PrayerSettings = settings(options),
    ): NotificationContent {
        val today = PrayerTimesCalculator().timesFor(date, settings, zone)
        val next = NextPrayerResolver().resolve(afternoon, settings, zone)
        return NotificationRenderer.render(
            context = context,
            settings = settings,
            next = next,
            today = today,
            now = afternoon,
            zone = zone,
            locale = Locale.ENGLISH,
        )
    }

    private fun asrClock(): String {
        val today = PrayerTimesCalculator().timesFor(date, settings(), zone)!!
        return CountdownFormat.clock(today.at(PrayerName.Asr), zone, use24Hour = true)
    }

    private fun notification(
        options: NotificationSettings = NotificationSettings(enabled = true),
    ): Notification = PrayerNotifier.build(context, settings(options), next = null, now = afternoon)

    @Test
    fun `the title and the body follow their own templates`() {
        val rendered = content(
            NotificationSettings(
                enabled = true,
                template = "{prayer} {time}",
                subtitleTemplate = "{previous} -> {prayer}",
            ),
        )
        assertEquals("Asr " + asrClock(), rendered.title)
        assertEquals("Dhuhr -> Asr", rendered.text)
    }

    @Test
    fun `an empty template falls back to the shared default`() {
        val rendered = content()
        assertEquals("Asr " + asrClock(), rendered.title)
        // The default second line is the {remaining} token on its own.
        assertTrue(rendered.text, rendered.text.endsWith("m"))
    }

    @Test
    fun `the countdown is handed to the platform chronometer`() {
        val rendered = content()
        assertTrue(rendered.showChronometer)
        assertEquals(rendered.remainingMillis, rendered.endsAtMillis - afternoon.toEpochMilli())
        assertTrue(rendered.progress in 1..999)
    }

    @Test
    fun `the timeline holds exactly the times the user ticked, next one marked`() {
        val rendered = content(
            NotificationSettings(
                enabled = true,
                visiblePrayers = setOf(PrayerName.Dhuhr, PrayerName.Asr, PrayerName.Maghrib),
            ),
        )
        assertEquals(
            listOf(PrayerName.Dhuhr, PrayerName.Asr, PrayerName.Maghrib),
            rendered.lines.map { it.prayer },
        )
        assertEquals(listOf(PrayerName.Asr), rendered.lines.filter { it.isNext }.map { it.prayer })
        assertTrue(rendered.lines.first().elapsed)
    }

    @Test
    fun `a timeline longer than the shade drops the times already called`() {
        val rendered = content(
            NotificationSettings(enabled = true, visiblePrayers = PrayerName.entries.toSet()),
        )
        assertEquals(NotificationSettings.MAX_TIMELINE_LINES, rendered.lines.size)
        assertTrue(rendered.lines.none { it.prayer == PrayerName.Fajr })
        assertTrue(rendered.lines.any { it.prayer == PrayerName.Asr })
    }

    @Test
    fun `switching the timeline off leaves the two text lines`() {
        val rendered = content(NotificationSettings(enabled = true, showTimeline = false))
        assertTrue(rendered.lines.isEmpty())
        assertTrue(rendered.title.isNotBlank())
    }

    @Test
    fun `the icon and the accent follow the prayer, unless the user pinned them`() {
        val coloured = content()
        assertEquals(PrayerName.Asr.visual.iconRes, coloured.iconRes)
        assertEquals(PrayerName.Asr.visual.color, coloured.accent)

        val flat = content(
            NotificationSettings(enabled = true, showIcon = false, usePrayerColor = false),
        )
        assertEquals(R.drawable.ic_tile_prayer, flat.iconRes)
        assertNotEquals(coloured.accent, flat.accent)
    }

    @Test
    fun `the subtext carries the location and the hijri date only when asked`() {
        assertEquals("Istanbul", content().meta)
        val bare = content(
            NotificationSettings(enabled = true, showLocation = false, showHijri = false),
        )
        assertEquals("", bare.meta)
        val both = content(NotificationSettings(enabled = true, showHijri = true))
        assertTrue(both.meta, both.meta.startsWith("Istanbul · "))
    }

    @Test
    fun `an unset location prompts instead of failing`() {
        val rendered = NotificationRenderer.render(
            context = context,
            settings = PrayerSettings(notification = NotificationSettings(enabled = true)),
            next = null,
            today = null,
            now = afternoon,
            zone = zone,
        )
        assertFalse(rendered.configured)
        assertEquals(context.getString(R.string.tile_no_location), rendered.text)
        assertTrue(rendered.lines.isEmpty())
        assertFalse(rendered.showChronometer)
    }

    @Test
    fun `the status notification lands in its own channel and stays put`() {
        val posted = notification()
        assertEquals(PrayerNotifier.STATUS_CHANNEL_ID, posted.channelId)
        assertNotEquals(0, posted.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun `with the status off the service keeps its bare notice in the other channel`() {
        val posted = PrayerNotifier.build(
            context,
            settings(NotificationSettings(enabled = false)),
            next = null,
            serviceText = "bare notice",
            now = afternoon,
        )
        assertEquals(PrayerNotifier.SERVICE_CHANNEL_ID, posted.channelId)
        assertEquals(
            "bare notice",
            posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `only a template that drifts asks for a refresh between boundaries`() {
        val still = NotificationSettings(
            enabled = true,
            template = TemplateToken.Prayer.spelling(Locale.ENGLISH),
            subtitleTemplate = TemplateToken.Time.spelling(Locale.ENGLISH),
            showProgress = false,
        )
        assertFalse(still.hasCountdown)
        assertTrue(still.copy(showProgress = true).hasCountdown)
        assertTrue(
            still.copy(subtitleTemplate = TemplateToken.Remaining.spelling(Locale.ENGLISH))
                .hasCountdown,
        )
    }
}
