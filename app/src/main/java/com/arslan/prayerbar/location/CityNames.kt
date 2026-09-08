package com.arslan.prayerbar.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Turns coordinates into the name of the place they fall in, so nothing in the app has to show a
 * pair of decimals to say where the times are calculated for.
 *
 * The platform geocoder needs no key and no library, but it is a remote lookup: it can be missing
 * altogether, and it fails while offline. Every failure returns null and the caller keeps whatever
 * it had — a location is never blocked on getting a name.
 */
class CityNames(private val context: Context) {

    suspend fun cityName(
        latitude: Double,
        longitude: Double,
        locale: Locale = Locale.getDefault(),
    ): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = runCatching { Geocoder(context, locale) }.getOrNull() ?: return null
        val addresses = withTimeoutOrNull(TIMEOUT_MILLIS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lookup(geocoder, latitude, longitude)
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1) }.getOrNull()
            }
        }
        return addresses?.firstOrNull()?.cityName()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun lookup(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address>? = suspendCancellableCoroutine { continuation ->
        val listener = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses)
            }

            override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
        runCatching { geocoder.getFromLocation(latitude, longitude, 1, listener) }
            .onFailure { if (continuation.isActive) continuation.resume(null) }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

/**
 * The narrowest name that still reads as a place: the town, else the district, else the province,
 * else the country. Rural coordinates often carry no locality at all, hence the walk outwards.
 */
internal fun Address.cityName(): String? = listOfNotNull(
    locality,
    subAdminArea,
    adminArea,
    countryName,
).firstOrNull { it.isNotBlank() }

/** The label a location falls back to while it has no name of its own. */
object CoordinateLabel {

    fun of(latitude: Double, longitude: Double): String = "%.3f, %.3f".format(latitude, longitude)

    /**
     * Whether a label is one this app wrote itself from coordinates, and may therefore replace with
     * a name the geocoder found later. A label the user typed is left alone.
     */
    fun looksLikeOne(label: String): Boolean = PATTERN.matches(label.trim())

    // Written with the locale's own decimal separator, so a Turkish phone spells it "41,008".
    private val PATTERN = Regex("""-?\d{1,3}[.,]\d+,\s*-?\d{1,3}[.,]\d+""")
}
