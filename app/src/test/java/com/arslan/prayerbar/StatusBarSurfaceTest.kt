package com.arslan.prayerbar

import androidx.test.core.app.ApplicationProvider
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.PRAYERS_IN_ORDER
import com.arslan.prayerbar.statusbar.Glyphs
import com.arslan.prayerbar.statusbar.StatusBarText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.Locale

/**
 * The status bar spells its label from one drawable per character, so a character with no glyph is
 * simply missing from the bar. These guard the two ways that bites: a countdown or a prayer name
 * that renders a character the atlas never got.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StatusBarSurfaceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `every countdown within the drawn range takes one slot`() {
        val durations = listOf(
            Duration.ZERO,
            Duration.ofMinutes(1),
            Duration.ofMinutes(59),
            Duration.ofHours(1),
            Duration.ofHours(9).plusMinutes(5),
            Duration.ofHours(11).plusMinutes(59),
        )
        listOf(Locale("tr"), Locale.ENGLISH).forEach { locale ->
            durations.forEach { duration ->
                val text = CountdownFormat.remainingCompact(duration, locale)
                assertEquals(
                    "no glyph in \"$text\" for $locale",
                    text,
                    StatusBarText.sanitize(text),
                )
                assertEquals(
                    "\"$text\" should be drawn in one piece, not spelled out",
                    1,
                    StatusBarText.segments(text).size,
                )
            }
        }
    }

    @Test
    fun `a countdown past the drawn range falls back without falling apart`() {
        // Only the first twelve hours have a reading of their own. The thirteenth is not spelled
        // out in full: the hour count matches one chunk and ":05" another, so it costs two slots
        // rather than five.
        val text = CountdownFormat.remainingCompact(Duration.ofHours(13).plusMinutes(5))
        assertEquals("13:05", text)
        assertTrue(StatusBarText.unsupported(text).isEmpty())
        assertEquals(2, StatusBarText.segments(text).size)
    }

    @Test
    fun `every prayer label is drawable`() {
        PRAYERS_IN_ORDER.forEach { prayer ->
            val label = context.getString(prayer.labelRes)
            assertTrue(
                "no glyph in \"$label\"",
                StatusBarText.unsupported(label).isEmpty(),
            )
        }
    }

    @Test
    fun `a chunk is preferred over spelling and the rest still falls back`() {
        assertEquals(1, StatusBarText.segments("1:45").size)
        // "Asr " has no chunk, so it costs a slot a character; the countdown still costs one.
        assertEquals(5, StatusBarText.segments("Asr 1:45").size)
    }

    @Test
    fun `turkish letters have glyphs`() {
        "ÇĞİÖŞÜçğıöşü".forEach { assertTrue("no glyph for $it", Glyphs.has(it)) }
    }

    @Test
    fun `sanitize drops what cannot be drawn and caps the length`() {
        assertEquals("1sa 05dk", StatusBarText.sanitize("  1sa 05dk  "))
        // The tile presets already offer a mosque emoji; it is a surrogate pair with no glyph,
        // and the space it leaves behind must go with it.
        assertEquals("Ikindi", StatusBarText.sanitize("🕌 Ikindi"))
        assertTrue(StatusBarText.unsupported("🕌 Ikindi").isNotEmpty())
        assertEquals("Ikindi", StatusBarText.sanitize("%Ikindi"))
        assertEquals(setOf('%'), StatusBarText.unsupported("%Ikindi"))
        assertEquals(
            StatusBarText.MAX_SLOTS,
            StatusBarText.segments("0123456789012345").size,
        )
    }
}
