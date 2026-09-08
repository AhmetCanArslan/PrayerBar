package com.arslan.prayerbar

import android.location.Address
import com.arslan.prayerbar.location.CoordinateLabel
import com.arslan.prayerbar.location.cityName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocationNameTest {

    private fun address(
        locality: String? = null,
        subAdminArea: String? = null,
        adminArea: String? = null,
        countryName: String? = null,
    ): Address = Address(Locale.ENGLISH).apply {
        setLocality(locality)
        setSubAdminArea(subAdminArea)
        setAdminArea(adminArea)
        setCountryName(countryName)
    }

    @Test
    fun `the town wins, and the walk outwards covers the middle of nowhere`() {
        assertEquals(
            "Kadıköy",
            address(locality = "Kadıköy", subAdminArea = "Kadıköy", adminArea = "İstanbul").cityName(),
        )
        assertEquals("İstanbul", address(adminArea = "İstanbul", countryName = "Türkiye").cityName())
        assertEquals("Türkiye", address(countryName = "Türkiye").cityName())
        assertNull(address().cityName())
        assertNull(address(locality = " ").cityName())
    }

    @Test
    fun `a label this app wrote from coordinates is one it may replace`() {
        assertTrue(CoordinateLabel.looksLikeOne(CoordinateLabel.of(41.0082, 28.9784)))
        assertTrue(CoordinateLabel.looksLikeOne("41.008, 28.978"))
        // A Turkish phone spells the same fallback with commas for decimals.
        assertTrue(CoordinateLabel.looksLikeOne("41,008, 28,978"))
        assertTrue(CoordinateLabel.looksLikeOne("-33.868, 151.209"))
    }

    @Test
    fun `a name the user typed is left alone`() {
        assertFalse(CoordinateLabel.looksLikeOne("Ev"))
        assertFalse(CoordinateLabel.looksLikeOne("İstanbul"))
        assertFalse(CoordinateLabel.looksLikeOne("Kadıköy, İstanbul"))
        assertFalse(CoordinateLabel.looksLikeOne("41 Burda"))
    }
}
