package com.arslan.prayerbar.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * One-shot GPS fix using the platform LocationManager — no Play Services dependency, mirroring
 * al-azan's approach. A recent last-known fix is accepted to avoid waiting on the radio.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMillis: Long = 15_000L): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        lastKnown(manager)?.let { return it }

        val providers = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf { manager.isProviderEnabled(it) },
            LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) },
        )
        if (providers.isEmpty()) return null

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val executor = Executors.newSingleThreadExecutor()
                val delivered = AtomicBoolean(false)
                val signal = androidx.core.os.CancellationSignal()
                providers.forEach { provider ->
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        signal,
                        executor,
                    ) { location ->
                        if (location != null && delivered.compareAndSet(false, true)) {
                            continuation.resumeIfActive(location)
                        }
                    }
                }
                continuation.invokeOnCancellation {
                    runCatching { signal.cancel() }
                    executor.shutdownNow()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? {
        val maxAge = System.currentTimeMillis() - MAX_LAST_KNOWN_AGE_MILLIS
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { it.time >= maxAge && it.accuracy <= MAX_ACCURACY_METERS }
            .maxByOrNull { it.time }
    }

    private fun CancellableContinuation<Location?>.resumeIfActive(location: Location) {
        if (isActive) resume(location)
    }

    private companion object {
        const val MAX_LAST_KNOWN_AGE_MILLIS = 15L * 60L * 1000L
        const val MAX_ACCURACY_METERS = 500f
    }
}
