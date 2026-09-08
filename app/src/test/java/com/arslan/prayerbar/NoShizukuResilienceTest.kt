package com.arslan.prayerbar

import androidx.test.core.app.ApplicationProvider
import com.arslan.prayerbar.carrier.CarrierNameManager
import com.arslan.prayerbar.carrier.CarrierResult
import com.arslan.prayerbar.carrier.ShizukuHelper
import com.arslan.prayerbar.statusbar.StatusBarIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every privileged path must fail safely — never throw — when Shizuku is not running. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoShizukuResilienceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `carrier write reports missing shizuku instead of throwing`() {
        assertEquals(CarrierResult.NoShizuku, CarrierNameManager.setCarrierName(1, "İkindi 15:42"))
        assertEquals(CarrierResult.NoShizuku, CarrierNameManager.resetCarrierName(1))
    }

    @Test
    fun `shizuku helpers report unavailable`() {
        assertFalse(ShizukuHelper.isShizukuAvailable())
        assertFalse(ShizukuHelper.hasPermission())
        assertFalse(ShizukuHelper.executeShellCommand(arrayOf("id")))
    }

    @Test
    fun `status bar write reports failure instead of throwing`() {
        assertFalse(StatusBarIcons.apply(context, "1sa", description = "1 saat kaldi"))
        assertFalse(StatusBarIcons.clear(context))
    }

    @Test
    fun `sim lookup without permission returns empty`() {
        assertTrue(CarrierNameManager.getSimSlots(context).isEmpty())
    }
}
