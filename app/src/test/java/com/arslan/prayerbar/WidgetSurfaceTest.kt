package com.arslan.prayerbar

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.arslan.prayerbar.prayer.NextPrayerResolver
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.PrayerTimesCalculator
import com.arslan.prayerbar.prayer.SavedLocation
import com.arslan.prayerbar.prayer.WidgetSettings
import com.arslan.prayerbar.widget.WidgetContent
import com.arslan.prayerbar.widget.WidgetLayoutSpec
import com.arslan.prayerbar.widget.WidgetRenderer
import com.arslan.prayerbar.widget.WidgetShape
import com.arslan.prayerbar.widget.WidgetSizing
import com.arslan.prayerbar.widget.WidgetViews
import io.github.meypod.adhan_kotlin.CalculationMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetSurfaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    private val istanbul = SavedLocation("home", "Istanbul", 41.0082, 28.9784)
    private val date = LocalDate.of(2026, 8, 30)
    private val settings = PrayerSettings(
        parameters = CalculationMethod.TURKEY.parameters,
        locations = listOf(istanbul),
        activeLocationId = istanbul.id,
    )

    private val afternoon = LocalDateTime.of(date, LocalTime.of(14, 0)).atZone(zone).toInstant()

    private fun content(
        widget: WidgetSettings = WidgetSettings(),
        capacity: Int = 8,
    ): WidgetContent {
        val today = PrayerTimesCalculator().timesFor(date, settings, zone)
        val next = NextPrayerResolver().resolve(afternoon, settings, zone)
        return WidgetRenderer.render(
            context = context,
            settings = settings,
            widget = widget,
            next = next,
            today = today,
            capacity = capacity,
            now = afternoon,
            zone = zone,
        )
    }

    private fun inflate(spec: WidgetLayoutSpec, widget: WidgetSettings, content: WidgetContent): View =
        WidgetViews.build(context, spec, widget, content).apply(context, LinearLayout(context))

    private fun textOf(root: View, id: Int): String? =
        root.findViewById<TextView>(id)?.text?.toString()

    @Test
    fun `each cell shape picks its own layout`() {
        assertEquals(WidgetShape.Tiny, WidgetSizing.resolve(70, 70).shape)
        assertEquals(WidgetShape.Small, WidgetSizing.resolve(150, 70).shape)
        assertEquals(WidgetShape.Wide, WidgetSizing.resolve(320, 70).shape)
        assertEquals(WidgetShape.Medium, WidgetSizing.resolve(150, 200).shape)
        assertEquals(WidgetShape.Large, WidgetSizing.resolve(320, 200).shape)
    }

    @Test
    fun `a one cell widget has no room for a list and a tall one does`() {
        assertEquals(0, WidgetSizing.resolve(70, 70).listCapacity)
        assertEquals(0, WidgetSizing.resolve(150, 70).listCapacity)
        assertTrue(WidgetSizing.resolve(320, 300).listCapacity >= 5)
    }

    @Test
    fun `only the strip lays its times out horizontally`() {
        assertTrue(WidgetSizing.resolve(320, 70).listIsHorizontal)
        assertFalse(WidgetSizing.resolve(320, 300).listIsHorizontal)
    }

    @Test
    fun `the countdown points at the next tracked prayer`() {
        val rendered = content()
        assertEquals(context.getString(PrayerName.Asr.labelRes), rendered.headline)
        assertTrue(rendered.progress in 1..999)
    }

    @Test
    fun `switching the countdown off promotes the clock time`() {
        val rendered = content(WidgetSettings(showCountdown = false))
        assertEquals(rendered.time, rendered.emphasis)
    }

    @Test
    fun `the list holds exactly the prayers the user ticked`() {
        val widget = WidgetSettings(visiblePrayers = setOf(PrayerName.Asr, PrayerName.Maghrib))
        assertEquals(
            listOf(PrayerName.Asr, PrayerName.Maghrib),
            content(widget).rows.map { it.prayer },
        )
    }

    @Test
    fun `a short list drops the times already called`() {
        val rows = content(capacity = 3).rows
        assertEquals(3, rows.size)
        assertEquals(listOf(PrayerName.Asr, PrayerName.Maghrib, PrayerName.Isha), rows.map { it.prayer })
        assertTrue(rows.none { it.elapsed })
    }

    @Test
    fun `spare rows are filled with the most recent past times`() {
        val rows = content(capacity = 4).rows
        assertEquals(
            listOf(PrayerName.Dhuhr, PrayerName.Asr, PrayerName.Maghrib, PrayerName.Isha),
            rows.map { it.prayer },
        )
        assertTrue(rows.first().elapsed)
    }

    @Test
    fun `an unset location renders a prompt instead of failing`() {
        val rendered = WidgetRenderer.render(
            context = context,
            settings = PrayerSettings(),
            widget = WidgetSettings(),
            next = null,
            today = null,
            capacity = 8,
            now = afternoon,
            zone = zone,
        )
        assertFalse(rendered.configured)
        assertEquals(context.getString(R.string.tile_no_location), rendered.meta)
        assertTrue(rendered.rows.isEmpty())
    }

    @Test
    fun `every shape inflates and carries the countdown`() {
        val widget = WidgetSettings()
        WidgetShape.entries.forEach { shape ->
            val spec = WidgetLayoutSpec(shape, if (shape == WidgetShape.Wide) 4 else 6)
            val rendered = content(widget, spec.listCapacity)
            val root = inflate(spec, widget, rendered)
            assertEquals(shape.name, rendered.emphasis, textOf(root, R.id.widget_remaining))
            assertEquals(shape.name, rendered.headline, textOf(root, R.id.widget_name))
        }
    }

    @Test
    fun `the list is filled with one view per row`() {
        val spec = WidgetLayoutSpec(WidgetShape.Large, 5)
        val rendered = content(capacity = 5)
        val root = inflate(spec, WidgetSettings(), rendered)
        val list = root.findViewById<LinearLayout>(R.id.widget_list)
        assertEquals(rendered.rows.size, list.childCount)
        assertEquals(
            rendered.rows.first().time,
            list.getChildAt(0).findViewById<TextView>(R.id.widget_row_time).text.toString(),
        )
    }

    @Test
    fun `switching seconds on hands the countdown to the chronometer`() {
        val widget = WidgetSettings(showSeconds = true)
        val spec = WidgetLayoutSpec(WidgetShape.Large, 5)
        val root = inflate(spec, widget, content(widget, 5))
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_remaining).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_chronometer).visibility)
    }

    @Test
    fun `without seconds the chronometer stays out of the way`() {
        val widget = WidgetSettings()
        val spec = WidgetLayoutSpec(WidgetShape.Large, 5)
        val root = inflate(spec, widget, content(widget, 5))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_remaining).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_chronometer).visibility)
    }

    @Test
    fun `the seconds preview is spelled the way a chronometer spells it`() {
        val rendered = content(WidgetSettings(showSeconds = true))
        assertTrue(rendered.emphasis, rendered.emphasis.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?")))
        assertTrue(rendered.remainingMillis > 0L)
    }

    @Test
    fun `hiding a part hides its view rather than blanking it`() {
        val widget = WidgetSettings(showIcon = false, showProgress = false)
        val spec = WidgetLayoutSpec(WidgetShape.Large, 5)
        val root = inflate(spec, widget, content(widget, 5))
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_icon).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_progress).visibility)
    }

    @Test
    fun `a layout missing most of the builder's views still inflates`() {
        val widget = WidgetSettings()
        val root = inflate(WidgetLayoutSpec(WidgetShape.Tiny, 0), widget, content(widget, 0))
        assertNull(root.findViewById<View>(R.id.widget_list))
        assertNull(root.findViewById<View>(R.id.widget_progress))
        assertEquals(content(widget, 0).emphasis, textOf(root, R.id.widget_remaining))
    }
}
