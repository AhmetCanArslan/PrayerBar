package com.arslan.prayerbar.location

import android.content.Context
import android.location.Location
import com.arslan.prayerbar.data.SettingsRepository
import com.arslan.prayerbar.prayer.LocationTracking
import com.arslan.prayerbar.prayer.SavedLocation

/**
 * Keeps the followed location up to date while the app is running its surfaces.
 *
 * Every refresh of a surface offers to re-read the fix, but the work is cheap in the common case:
 * the radio is only asked once every [MIN_INTERVAL_MILLIS], and the settings are only written when
 * the phone has actually moved [MIN_DISTANCE_METERS] — a countdown ticking once a second must not
 * turn into a stream of GPS polls or DataStore writes.
 */
class LocationTracker(
    context: Context,
    private val repository: SettingsRepository,
    private val provider: LocationProvider = LocationProvider(context),
    private val cityNames: CityNames = CityNames(context),
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * @return true when the stored location changed, so the caller knows the prayer times it has
     * just computed are stale and the surfaces need redrawing.
     */
    suspend fun refresh(force: Boolean = false): Boolean {
        val tracking = repository.current().tracking
        if (!tracking.enabled || !provider.hasPermission()) return false
        val moment = now()
        if (!force && moment - tracking.checkedAtMillis < MIN_INTERVAL_MILLIS) return false

        // Short wait in the background: a surface refresh must not sit on the radio, and a recent
        // last-known fix — the common case — comes back at once anyway. The user asking for it in
        // the app is the one who can afford to wait.
        val fix = provider.currentLocation(if (force) FOREGROUND_TIMEOUT_MILLIS else BACKGROUND_TIMEOUT_MILLIS)
        if (fix == null) {
            // Stamp the attempt anyway: a phone indoors would otherwise be polled on every tick.
            stamp(moment)
            return false
        }
        val previous = tracking.location
        if (previous != null && distanceTo(previous, fix) < MIN_DISTANCE_METERS) {
            stamp(moment)
            return false
        }

        val city = cityNames.cityName(fix.latitude, fix.longitude)
        val location = SavedLocation(
            id = LocationTracking.LOCATION_ID,
            label = city ?: CoordinateLabel.of(fix.latitude, fix.longitude),
            latitude = fix.latitude,
            longitude = fix.longitude,
            city = city,
        )
        repository.update {
            it.copy(tracking = it.tracking.copy(location = location, checkedAtMillis = moment))
        }
        return true
    }

    private suspend fun stamp(moment: Long) {
        repository.update { it.copy(tracking = it.tracking.copy(checkedAtMillis = moment)) }
    }

    private fun distanceTo(location: SavedLocation, fix: Location): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            fix.latitude,
            fix.longitude,
            results,
        )
        return results[0]
    }

    companion object {
        /** Far enough that the prayer times shift by a minute or so; nearer than that is noise. */
        const val MIN_DISTANCE_METERS = 3_000f
        const val MIN_INTERVAL_MILLIS = 20L * 60L * 1000L
        private const val BACKGROUND_TIMEOUT_MILLIS = 6_000L
        private const val FOREGROUND_TIMEOUT_MILLIS = 20_000L
    }
}
