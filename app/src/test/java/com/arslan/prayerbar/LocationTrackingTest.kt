package com.arslan.prayerbar

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.arslan.prayerbar.data.SettingsRepository
import com.arslan.prayerbar.location.LocationTracker
import com.arslan.prayerbar.prayer.LocationTracking
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.SavedLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The followed location: which place the times are calculated for, and how often the radio and the
 * settings are actually touched while a surface refreshes every minute.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocationTrackingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = SettingsRepository(context)
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var now = 1_000_000_000L

    private fun tracker() = LocationTracker(context, repository, now = { now })

    @Before
    fun grantPermissionAndProviders() = kotlinx.coroutines.runBlocking {
        // DataStore outlives a single test in this JVM, so each one starts from the defaults.
        repository.update { PrayerSettings() }
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        Unit
    }

    private fun fixAt(latitude: Double, longitude: Double) {
        val location = Location(LocationManager.NETWORK_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = 20f
            time = System.currentTimeMillis()
        }
        shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location)
    }

    private suspend fun follow() =
        repository.update { it.copy(tracking = it.tracking.copy(enabled = true)) }

    @Test
    fun `the live fix outranks the saved list, and only while it is being followed`() {
        val saved = SavedLocation("saved", "Konya", 37.87, 32.48)
        val fix = SavedLocation(LocationTracking.LOCATION_ID, "İstanbul", 41.0, 28.97)
        val settings = PrayerSettings(
            locations = listOf(saved),
            activeLocationId = saved.id,
            tracking = LocationTracking(enabled = true, location = fix),
        )
        assertEquals(fix, settings.activeLocation)
        assertEquals(saved, settings.copy(tracking = settings.tracking.copy(enabled = false)).activeLocation)
        // Following, but nothing read yet: the saved choice carries the times until a fix lands.
        assertEquals(
            saved,
            settings.copy(tracking = settings.tracking.copy(location = null)).activeLocation,
        )
    }

    @Test
    fun `a fix is only stored once the phone has really moved`() = runTest {
        follow()
        fixAt(41.0082, 28.9784)
        assertTrue(tracker().refresh())
        val first = repository.current().tracking.location!!
        assertEquals(41.0082, first.latitude, 1e-6)

        // Next door, and past the interval: read, but not worth a rewrite.
        now += LocationTracker.MIN_INTERVAL_MILLIS
        fixAt(41.0100, 28.9800)
        assertFalse(tracker().refresh())
        assertEquals(first.latitude, repository.current().tracking.location!!.latitude, 1e-9)

        // Another city: the times have to follow.
        now += LocationTracker.MIN_INTERVAL_MILLIS
        fixAt(39.9334, 32.8597)
        assertTrue(tracker().refresh())
        assertEquals(39.9334, repository.current().tracking.location!!.latitude, 1e-6)
    }

    @Test
    fun `refreshes inside the interval leave the radio alone`() = runTest {
        follow()
        fixAt(41.0082, 28.9784)
        assertTrue(tracker().refresh())

        // A surface refreshing on every tick must not turn into a stream of fixes: a move this
        // large would otherwise be stored at once.
        fixAt(39.9334, 32.8597)
        assertFalse(tracker().refresh())
        assertEquals(41.0082, repository.current().tracking.location!!.latitude, 1e-6)
        // Unless the user asks for it in the app.
        assertTrue(tracker().refresh(force = true))
        assertEquals(39.9334, repository.current().tracking.location!!.latitude, 1e-6)
    }

    @Test
    fun `nothing is read while the phone is not being followed`() = runTest {
        fixAt(41.0082, 28.9784)
        assertFalse(tracker().refresh(force = true))
        assertEquals(null, repository.current().tracking.location)
    }
}
