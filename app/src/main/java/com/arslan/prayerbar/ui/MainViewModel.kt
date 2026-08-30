package com.arslan.prayerbar.ui

import android.app.Application
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
import com.arslan.prayerbar.prayer.Adjustments
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.SavedLocation
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
)

data class MainUiState(
    val settings: PrayerSettings = PrayerSettings(),
    val now: Instant = Instant.now(),
    val next: NextPrayer? = null,
    val today: DayTimes? = null,
    val tomorrow: DayTimes? = null,
    val previewText: String = "",
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
                shizuku = ShizukuState(
                    running = ShizukuHelper.isShizukuAvailable(),
                    granted = ShizukuHelper.hasPermission(),
                    phonePermission = CarrierNameManager.hasPhonePermission(app),
                    canScheduleExact = container.alarmScheduler.canScheduleExact,
                ),
            )
        }
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
        _state.update {
            it.copy(next = next, today = today, tomorrow = tomorrow, previewText = preview)
        }
    }

    fun labelOf(prayer: PrayerName): String = container.carrierApplier.labelOf(prayer)

    // ---- settings mutations -------------------------------------------------

    private fun edit(transform: (PrayerSettings) -> PrayerSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
            rearm()
        }
    }

    private fun editParameters(transform: (CalculationParameters) -> CalculationParameters) =
        edit { it.copy(parameters = transform(it.parameters)) }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(enabled = enabled) }
            if (enabled) {
                container.scheduleSafetyNet()
                applyNow(persistent = true)
            } else {
                container.cancelSafetyNet()
                container.alarmScheduler.cancel()
                resetNow()
            }
        }
    }

    fun setMethod(method: CalculationMethod) = edit {
        // Store a snapshot of the method's canonical parameters, keeping the user's madhab and the
        // other advanced switches — the same trade-off al-azan makes.
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

    // ---- carrier actions ----------------------------------------------------

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
