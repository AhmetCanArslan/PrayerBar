package com.arslan.prayerbar.ui

import android.app.Application
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arslan.prayerbar.AppContainer
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.carrier.CarrierNameManager
import com.arslan.prayerbar.carrier.CarrierResult
import com.arslan.prayerbar.carrier.ShizukuHelper
import com.arslan.prayerbar.carrier.SimSlot
import com.arslan.prayerbar.location.CityNames
import com.arslan.prayerbar.location.CoordinateLabel
import com.arslan.prayerbar.location.LocationProvider
import com.arslan.prayerbar.notification.NotificationContent
import com.arslan.prayerbar.notification.NotificationRenderer
import com.arslan.prayerbar.notification.PrayerNotifier
import com.arslan.prayerbar.schedule.CarrierService
import com.arslan.prayerbar.schedule.SurfaceRefresh
import com.arslan.prayerbar.tile.PrayerTileService
import com.arslan.prayerbar.tile.TileContent
import com.arslan.prayerbar.tile.TileRenderer
import com.arslan.prayerbar.prayer.Adjustments
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.NotificationSettings
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.SavedLocation
import com.arslan.prayerbar.prayer.TileSettings
import com.arslan.prayerbar.prayer.WidgetSettings
import com.arslan.prayerbar.widget.PrayerWidgetProvider
import com.arslan.prayerbar.widget.WidgetContent
import com.arslan.prayerbar.widget.WidgetRenderer
import io.github.meypod.adhan_kotlin.CalculationMethod
import io.github.meypod.adhan_kotlin.CalculationParameters
import io.github.meypod.adhan_kotlin.HighLatitudeRule
import io.github.meypod.adhan_kotlin.Madhab
import io.github.meypod.adhan_kotlin.MidnightMethod
import io.github.meypod.adhan_kotlin.PolarCircleResolution
import io.github.meypod.adhan_kotlin.model.Rounding
import io.github.meypod.adhan_kotlin.model.Shafaq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ShizukuState(
    val running: Boolean = false,
    val granted: Boolean = false,
    val phonePermission: Boolean = false,
    val canScheduleExact: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val ignoringBatteryOptimizations: Boolean = true,
)

data class MainUiState(
    val settings: PrayerSettings = PrayerSettings(),
    val now: Instant = Instant.now(),
    val next: NextPrayer? = null,
    val today: DayTimes? = null,
    val tomorrow: DayTimes? = null,
    val previewText: String = "",
    val tilePreview: TileContent? = null,
    val statusBarPreview: String = "",
    val notificationPreview: NotificationContent? = null,
    val widgetIds: List<Int> = emptyList(),
    val simSlots: List<SimSlot> = emptyList(),
    val shizuku: ShizukuState = ShizukuState(),
    val busy: Boolean = false,
    val message: String? = null,
)

class MainViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val cityNames = CityNames(application)
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                recompute()
            }
        }
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(now = Instant.now()) }
                recompute()
                delay(TICK_MILLIS)
            }
        }
        refreshEnvironment()
        nameUnnamedLocations()
        followLocationIfDue()
    }

    /**
     * Opening the app is a good moment to catch up on a move, but only if the fix is due: the
     * tracker's own interval decides, so a quick visit costs nothing.
     */
    private fun followLocationIfDue() {
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) { container.locationTracker.refresh() }
            if (changed) onFollowedLocationMoved(container.settingsRepository.current())
        }
    }

    fun refreshEnvironment() {
        val app = getApplication<Application>()
        _state.update {
            it.copy(
                simSlots = CarrierNameManager.getSimSlots(app),
                widgetIds = PrayerWidgetProvider.ids(app).toList(),
                shizuku = ShizukuState(
                    running = ShizukuHelper.isShizukuAvailable(),
                    granted = ShizukuHelper.hasPermission(),
                    phonePermission = CarrierNameManager.hasPhonePermission(app),
                    canScheduleExact = container.alarmScheduler.canScheduleExact,
                    notificationsAllowed = NotificationManagerCompat.from(app).areNotificationsEnabled(),
                    ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(app),
                ),
            )
        }
    }

    private fun isIgnoringBatteryOptimizations(app: Application): Boolean {
        val power = app.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(app.packageName)
    }

    private fun recompute() {
        val current = _state.value
        val settings = current.settings
        val now = current.now
        val next = container.resolver.resolve(now, settings, zone)
        val today = container.calculator.timesFor(now.atZone(zone).toLocalDate(), settings, zone)
        val tomorrow = container.calculator
            .timesFor(now.atZone(zone).toLocalDate().plusDays(1), settings, zone)
        val preview = next?.let {
            container.carrierApplier.preview(it, settings.template, now, settings.use24Hour)
        }.orEmpty()
        val tilePreview = TileRenderer.render(getApplication(), settings, next, now)
        val statusBarPreview = next?.let {
            container.statusBarApplier
                .preview(it, settings.statusBar.labelTemplate(), now, settings.use24Hour)
        }.orEmpty()
        val notificationPreview = NotificationRenderer.render(
            context = getApplication(),
            settings = settings,
            next = next,
            today = today,
            now = now,
            zone = zone,
        )
        _state.update {
            it.copy(
                next = next,
                today = today,
                tomorrow = tomorrow,
                previewText = preview,
                tilePreview = tilePreview,
                statusBarPreview = statusBarPreview,
                notificationPreview = notificationPreview,
            )
        }
    }

    fun labelOf(prayer: PrayerName): String = container.carrierApplier.labelOf(prayer)

    private fun edit(transform: (PrayerSettings) -> PrayerSettings) {
        viewModelScope.launch { commit(transform) }
    }

    /** The write itself, for the callers that have more to do once it has landed. */
    private suspend fun commit(transform: (PrayerSettings) -> PrayerSettings): PrayerSettings {
        val settings = container.settingsRepository.update(transform)

        PrayerWidgetProvider.refresh(getApplication(), settings)
        // The notification is drawn from the same settings, so every edit reaches the shade.
        if (settings.notification.enabled) CarrierService.start(getApplication())
        rearm()
        return settings
    }

    private fun editParameters(transform: (CalculationParameters) -> CalculationParameters) =
        edit { it.copy(parameters = transform(it.parameters)) }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val settings = container.settingsRepository.update { it.copy(enabled = enabled) }
            if (enabled) {
                startBackgroundWork()
                applyNow(persistent = true)
            } else {
                // The tile and the status notification live off the same service.
                if (!settings.hasActiveSurface) stopBackgroundWork()
                resetNow()
            }
        }
    }

    fun setTileEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val settings = container.settingsRepository
                .update { it.copy(tile = it.tile.copy(enabled = enabled)) }
            if (enabled) {
                startBackgroundWork()
                val outcome = withContext(Dispatchers.IO) {
                    SurfaceRefresh.run(getApplication(), force = true)
                }
                container.alarmScheduler.schedule(outcome.next)
            } else {
                if (!settings.hasActiveSurface) stopBackgroundWork()
                PrayerTileService.refresh(getApplication())
            }
        }
    }

    fun setStatusBarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val settings = container.settingsRepository
                .update { it.copy(statusBar = it.statusBar.copy(enabled = enabled)) }
            if (enabled) {
                startBackgroundWork()
                val outcome = withContext(Dispatchers.IO) {
                    SurfaceRefresh.run(getApplication(), force = true)
                }
                container.alarmScheduler.schedule(outcome.next)
            } else {
                // The icons stay in the bar until somebody takes them out, so this cannot wait
                // for the next refresh.
                withContext(Dispatchers.IO) { container.statusBarApplier.clear() }
                if (!settings.hasActiveSurface) stopBackgroundWork()
            }
        }
    }

    fun setStatusBarTemplate(template: String) {
        viewModelScope.launch {
            val settings = commit { it.copy(statusBar = it.statusBar.copy(template = template)) }
            if (settings.statusBar.enabled) {
                withContext(Dispatchers.IO) { container.statusBarApplier.apply(force = true) }
            }
        }
    }

    private fun startBackgroundWork() {
        container.scheduleSafetyNet()
        CarrierService.start(getApplication())
    }

    private fun stopBackgroundWork() {
        container.cancelSafetyNet()
        CarrierService.stop(getApplication())
        container.alarmScheduler.cancel()
    }

    fun setMethod(method: CalculationMethod) = edit {
        val base = method.parameters
        it.copy(
            parameters = base.copy(
                madhab = it.parameters.madhab,
                highLatitudeRule = it.parameters.highLatitudeRule,
                polarCircleResolution = it.parameters.polarCircleResolution,
                shafaq = it.parameters.shafaq,
                rounding = it.parameters.rounding,
                prayerAdjustments = it.parameters.prayerAdjustments,
            ),
        )
    }

    fun restoreMethodDefaults() = editParameters { params ->
        val base = params.method.parameters
        params.copy(
            fajrAngle = base.fajrAngle,
            ishaAngle = base.ishaAngle,
            ishaInterval = base.ishaInterval,
            maghribAngle = base.maghribAngle,
        )
    }

    fun setFajrAngle(value: Double) = editParameters { it.copy(fajrAngle = value) }
    fun setIshaAngle(value: Double) = editParameters { it.copy(ishaAngle = value) }
    fun setIshaInterval(value: Int) = editParameters { it.copy(ishaInterval = value.coerceAtLeast(0)) }
    fun setMaghribAngle(value: Double) = editParameters { it.copy(maghribAngle = value) }
    fun setMadhab(madhab: Madhab) = editParameters { it.copy(madhab = madhab) }
    fun setHighLatitudeRule(rule: HighLatitudeRule?) = editParameters { it.copy(highLatitudeRule = rule) }
    fun setShafaq(shafaq: Shafaq) = editParameters { it.copy(shafaq = shafaq) }
    fun setPolar(resolution: PolarCircleResolution) =
        editParameters { it.copy(polarCircleResolution = resolution) }
    fun setRounding(rounding: Rounding) = editParameters { it.copy(rounding = rounding) }
    fun setMidnightMethod(method: MidnightMethod) = edit { it.copy(midnightMethod = method) }

    fun setAdjustment(prayer: PrayerName, minutes: Int) = edit {
        it.copy(adjustments = it.adjustments.withPrayer(prayer, minutes.coerceIn(-120, 120)))
    }

    fun resetAdjustments() = edit { it.copy(adjustments = Adjustments()) }

    fun toggleTracked(prayer: PrayerName) = edit { settings ->
        val tracked = settings.trackedPrayers.toMutableSet()
        if (!tracked.add(prayer)) tracked.remove(prayer)
        settings.copy(trackedPrayers = tracked.ifEmpty { settings.trackedPrayers })
    }

    fun setTemplate(template: String) = edit { it.copy(template = template) }

    private fun editTile(transform: (TileSettings) -> TileSettings) = edit {
        it.copy(tile = transform(it.tile))
    }

    fun setTileTemplate(template: String) = editTile { it.copy(template = template) }

    fun setTileSubtitleTemplate(template: String) =
        editTile { it.copy(subtitleTemplate = template) }

    fun setTileShowIcon(show: Boolean) = editTile { it.copy(showIcon = show) }

    fun setTileHighlightMinutes(minutes: Int) =
        editTile { it.copy(highlightMinutes = minutes.coerceAtLeast(0)) }

    fun editNotification(transform: (NotificationSettings) -> NotificationSettings) = edit {
        it.copy(notification = transform(it.notification))
    }

    fun toggleNotificationPrayer(prayer: PrayerName) =
        editNotification { it.togglePrayer(prayer) }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val settings = container.settingsRepository
                .update { it.copy(notification = it.notification.copy(enabled = enabled)) }
            if (enabled) {
                startBackgroundWork()
                val outcome = withContext(Dispatchers.IO) {
                    SurfaceRefresh.run(getApplication(), force = true)
                }
                container.alarmScheduler.schedule(outcome.next)
            } else if (settings.hasActiveSurface || PrayerWidgetProvider.hasWidgets(getApplication())) {
                // Something else still needs the service: hand its notification back to the bare
                // notice instead of tearing everything down.
                CarrierService.start(getApplication())
            } else {
                stopBackgroundWork()
            }
        }
    }

    fun openNotificationChannelSettings() =
        PrayerNotifier.openChannelSettings(getApplication(), _state.value.settings)

    fun editWidget(appWidgetId: Int, transform: (WidgetSettings) -> WidgetSettings) =
        edit { it.withWidget(appWidgetId, transform) }

    fun toggleWidgetPrayer(appWidgetId: Int, prayer: PrayerName) =
        editWidget(appWidgetId) { it.togglePrayer(prayer) }

    fun widgetPreview(appWidgetId: Int, capacity: Int): WidgetContent {
        val current = _state.value
        return WidgetRenderer.render(
            context = getApplication(),
            settings = current.settings,
            widget = current.settings.widget(appWidgetId),
            next = current.next,
            today = current.today,
            capacity = capacity,
            now = current.now,
            zone = zone,
        )
    }

    fun pinWidget() {
        val context = getApplication<Application>()
        val manager = AppWidgetManager.getInstance(context)
        val pinned = runCatching {
            manager.isRequestPinAppWidgetSupported &&
                manager.requestPinAppWidget(
                    ComponentName(context, PrayerWidgetProvider::class.java),
                    null,
                    null,
                )
        }.getOrDefault(false)
        if (!pinned) message(R.string.widget_add_failed)
    }

    fun setUse24Hour(use24Hour: Boolean) = edit { it.copy(use24Hour = use24Hour) }
    fun setTargetSubIds(subIds: List<Int>) = edit { it.copy(targetSubIds = subIds) }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    fun locationPermissionDenied() = message(R.string.location_permission_needed)

    /** Picking a saved place is a decision to stop following the phone. */
    fun selectLocation(id: String) = edit {
        it.copy(activeLocationId = id, tracking = it.tracking.copy(enabled = false))
    }

    /**
     * Turns the followed location on or off. Switching it on takes a fix straight away — waiting
     * for the next surface refresh would leave the screen showing the old city for minutes.
     */
    fun setFollowLocation(enabled: Boolean) {
        viewModelScope.launch {
            commit { it.copy(tracking = it.tracking.copy(enabled = enabled)) }
            if (enabled) refreshFollowedLocation()
        }
    }

    /** A fix that moved reaches the widgets, the label and the alarms as any other edit would. */
    private suspend fun onFollowedLocationMoved(settings: PrayerSettings) {
        PrayerWidgetProvider.refresh(getApplication(), settings)
        if (settings.hasActiveSurface) {
            val outcome = withContext(Dispatchers.IO) {
                SurfaceRefresh.run(getApplication(), force = true)
            }
            container.alarmScheduler.schedule(outcome.next)
        }
    }

    /** Re-reads the fix now, whatever the usual interval would say. */
    fun refreshFollowedLocation() {
        viewModelScope.launch {
            if (!locationProvider.hasPermission()) {
                message(R.string.location_permission_needed)
                return@launch
            }
            _state.update { it.copy(busy = true) }
            val changed = withContext(Dispatchers.IO) {
                container.locationTracker.refresh(force = true)
            }
            _state.update { it.copy(busy = false) }
            val settings = container.settingsRepository.current()
            when {
                // The times have moved with the fix, so every surface is showing stale ones.
                changed -> onFollowedLocationMoved(settings)
                settings.tracking.location == null -> message(R.string.location_gps_failed)
            }
        }
    }

    /**
     * Saves the coordinates straight away and asks the geocoder for the place name afterwards: the
     * lookup is remote and can fail, and waiting on it would leave the screen doing nothing.
     */
    fun addLocation(label: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val location = SavedLocation(
                id = UUID.randomUUID().toString(),
                label = label.ifBlank { CoordinateLabel.of(latitude, longitude) },
                latitude = latitude.coerceIn(-90.0, 90.0),
                longitude = longitude.coerceIn(-180.0, 180.0),
            )
            commit { settings ->
                settings.copy(
                    locations = settings.locations + location,
                    activeLocationId = location.id,
                    // Saving a place of one's own is a choice to use that place, not the phone's.
                    tracking = settings.tracking.copy(enabled = false),
                )
            }
            nameLocations(listOf(location))
        }
    }

    /** Backfills the ones saved before there was a name to save — a coordinate label included. */
    private fun nameUnnamedLocations() {
        viewModelScope.launch {
            nameLocations(container.settingsRepository.current().locations.filter { it.city == null })
        }
    }

    private suspend fun nameLocations(locations: List<SavedLocation>) {
        if (locations.isEmpty()) return
        val names = withContext(Dispatchers.IO) {
            locations.mapNotNull { location ->
                cityNames.cityName(location.latitude, location.longitude)
                    ?.let { location.id to it }
            }
        }.toMap()
        if (names.isEmpty()) return
        commit { settings ->
            settings.copy(
                locations = settings.locations.map { location ->
                    val city = names[location.id] ?: return@map location
                    location.copy(
                        city = city,
                        // A label the user typed stays theirs; a coordinate one was ours to replace.
                        label = if (CoordinateLabel.looksLikeOne(location.label)) city else location.label,
                    )
                },
            )
        }
    }

    fun deleteLocation(id: String) = edit { settings ->
        val remaining = settings.locations.filterNot { it.id == id }
        settings.copy(
            locations = remaining,
            activeLocationId = settings.activeLocationId.takeIf { it != id } ?: remaining.firstOrNull()?.id,
        )
    }

    fun useCurrentLocation(label: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val location = withContext(Dispatchers.IO) { locationProvider.currentLocation() }
            _state.update { it.copy(busy = false) }
            if (location == null) {
                message(R.string.location_gps_failed)
            } else {
                addLocation(label, location.latitude, location.longitude)
            }
        }
    }

    fun applyNow(persistent: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val outcome = withContext(Dispatchers.IO) {
                container.carrierApplier.apply(force = true, persistent = persistent)
            }
            container.alarmScheduler.schedule(outcome.next)
            _state.update { it.copy(busy = false, message = describe(outcome.result)) }
            refreshEnvironment()
        }
    }

    fun resetNow() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.IO) { container.carrierApplier.reset() }
            _state.update { it.copy(busy = false, message = describe(result)) }
        }
    }

    fun addQuickSettingsTile() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            message(R.string.tile_add_failed)
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            message(R.string.tile_add_failed)
            return
        }
        manager.requestAddTileService(
            ComponentName(context, PrayerTileService::class.java),
            context.getString(R.string.tile_name),
            Icon.createWithResource(context, R.drawable.ic_tile_prayer),
            context.mainExecutor,
        ) {  }
    }

    fun restartSystemUi() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ShizukuHelper.restartSystemUi() }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun rearm() {
        val settings = _state.value.settings
        if (!settings.enabled) return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                container.carrierApplier.apply(force = true)
            }
            container.alarmScheduler.schedule(outcome.next)
        }
    }

    private fun message(resId: Int) =
        _state.update { it.copy(message = getApplication<Application>().getString(resId)) }

    private fun describe(result: CarrierResult): String {
        val app = getApplication<Application>()
        return when (result) {
            CarrierResult.Ok -> app.getString(R.string.result_ok)
            CarrierResult.NoShizuku -> app.getString(R.string.result_no_shizuku)
            CarrierResult.Unsupported -> app.getString(R.string.result_unsupported)
            CarrierResult.NoSim -> app.getString(R.string.result_no_sim)
            CarrierResult.NoPhonePermission ->
                app.getString(R.string.result_no_phone_permission)
            is CarrierResult.TransactionFailed -> app.getString(R.string.result_failed, result.reason)
        }
    }

    companion object {
        private const val TICK_MILLIS = 1000L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(application, PrayerBarApp.container(application)) as T
            }
    }
}
