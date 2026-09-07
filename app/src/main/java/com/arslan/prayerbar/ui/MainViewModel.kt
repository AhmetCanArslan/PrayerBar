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
import com.arslan.prayerbar.location.LocationProvider
import com.arslan.prayerbar.schedule.CarrierService
import com.arslan.prayerbar.schedule.SurfaceRefresh
import com.arslan.prayerbar.tile.PrayerTileService
import com.arslan.prayerbar.tile.TileContent
import com.arslan.prayerbar.tile.TileRenderer
import com.arslan.prayerbar.prayer.Adjustments
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.NextPrayer
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
        _state.update {
            it.copy(
                next = next,
                today = today,
                tomorrow = tomorrow,
                previewText = preview,
                tilePreview = tilePreview,
            )
        }
    }

    fun labelOf(prayer: PrayerName): String = container.carrierApplier.labelOf(prayer)

    private fun edit(transform: (PrayerSettings) -> PrayerSettings) {
        viewModelScope.launch {
            val settings = container.settingsRepository.update(transform)

            PrayerWidgetProvider.refresh(getApplication(), settings)
            rearm()
        }
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
                if (!settings.tile.enabled) stopBackgroundWork()
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
                if (!settings.enabled) stopBackgroundWork()
                PrayerTileService.refresh(getApplication())
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

    fun selectLocation(id: String) = edit { it.copy(activeLocationId = id) }

    fun addLocation(label: String, latitude: Double, longitude: Double) = edit { settings ->
        val location = SavedLocation(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "%.3f, %.3f".format(latitude, longitude) },
            latitude = latitude.coerceIn(-90.0, 90.0),
            longitude = longitude.coerceIn(-180.0, 180.0),
        )
        settings.copy(
            locations = settings.locations + location,
            activeLocationId = location.id,
        )
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
