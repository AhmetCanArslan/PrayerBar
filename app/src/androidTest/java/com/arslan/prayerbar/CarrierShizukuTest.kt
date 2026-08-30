package com.arslan.prayerbar

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arslan.prayerbar.carrier.CarrierApplier
import com.arslan.prayerbar.carrier.CarrierNameManager
import com.arslan.prayerbar.carrier.ShizukuHelper
import com.arslan.prayerbar.data.SettingsRepository
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.SavedLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end check of the privileged path on a device with Shizuku running: seed a location, render
 * the label, push it through the carrier-config binder, then clear it again.
 */
@RunWith(AndroidJUnit4::class)
class CarrierShizukuTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val istanbul = SavedLocation("test", "Istanbul", 41.0082, 28.9784)

    private fun seed(repository: SettingsRepository) = runBlocking {
        repository.update {
            PrayerSettings(
                locations = listOf(istanbul),
                activeLocationId = istanbul.id,
                template = "{vakit} {saat}",
                enabled = true,
            )
        }
    }

    @Test
    fun shizukuIsRunningAndGranted() {
        assertTrue("Shizuku permission not granted", ShizukuHelper.awaitPermission(10_000L))
        assertTrue("Shizuku not running", ShizukuHelper.isShizukuAvailable())
    }

    @Test
    fun simSlotsAreVisible() {
        assertTrue(CarrierNameManager.hasPhonePermission(context))
        assertTrue("no active SIM", CarrierNameManager.getSimSlots(context).isNotEmpty())
    }

    @Test
    fun applyWritesTheRenderedLabel() = runBlocking {
        assumeTrue(ShizukuHelper.awaitPermission(10_000L))
        val repository = SettingsRepository(context)
        seed(repository)
        val applier = CarrierApplier(context, repository)

        val outcome = applier.apply(force = true, persistent = false)

        assertTrue("result=${outcome.result}", outcome.result.isOk)
        assertTrue("empty label", !outcome.text.isNullOrBlank())
        assertEquals(outcome.text, repository.current().lastAppliedText)
    }

    @Test
    fun theOverrideIsVisibleInCarrierConfig() = runBlocking {
        assumeTrue(ShizukuHelper.awaitPermission(10_000L))
        val repository = SettingsRepository(context)
        seed(repository)
        val applier = CarrierApplier(context, repository)
        val outcome = applier.apply(force = true, persistent = false)
        assumeTrue(outcome.result.isOk)

        val subId = CarrierNameManager.getSimSlots(context).first().subId
        val manager = context.getSystemService(android.telephony.CarrierConfigManager::class.java)
        val config = requireNotNull(manager.getConfigForSubId(subId))

        assertEquals(
            outcome.text,
            config.getString(android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_STRING),
        )
        assertTrue(
            config.getBoolean(
                android.telephony.CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL,
            ),
        )
    }

    @Test
    fun resetClearsTheOverride() = runBlocking {
        assumeTrue(ShizukuHelper.awaitPermission(10_000L))
        val repository = SettingsRepository(context)
        seed(repository)
        val applier = CarrierApplier(context, repository)
        applier.apply(force = true, persistent = false)

        val result = applier.reset()

        assertTrue("result=$result", result.isOk)
        assertEquals(null, repository.current().lastAppliedText)
    }
}
