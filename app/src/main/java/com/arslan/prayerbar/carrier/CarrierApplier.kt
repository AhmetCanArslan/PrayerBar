package com.arslan.prayerbar.carrier

import android.content.Context
import android.util.Log
import com.arslan.prayerbar.data.SettingsRepository
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.NextPrayerResolver
import com.arslan.prayerbar.prayer.PrayerName
import java.time.Instant
import java.time.ZoneId

data class ApplyOutcome(
    val text: String?,
    val next: NextPrayer?,
    val result: CarrierResult,
    val skipped: Boolean = false,
)

/**
 * Reads settings, resolves the next prayer, renders the label and writes it to the target SIMs.
 * Called from the alarm receiver, the boot receiver, the safety-net worker and the UI.
 */
class CarrierApplier(
    private val context: Context,
    private val repository: SettingsRepository,
    private val resolver: NextPrayerResolver = NextPrayerResolver(),
) {

    suspend fun apply(
        now: Instant = Instant.now(),
        force: Boolean = false,
        persistent: Boolean = false,
    ): ApplyOutcome {
        val settings = repository.current()
        val next = resolver.resolve(now, settings, ZoneId.systemDefault())
            ?: return ApplyOutcome(null, null, CarrierResult.TransactionFailed("no location set"))

        val text = CarrierTemplate.render(
            template = settings.template,
            next = next,
            now = now,
            labelOf = { context.getString(it.labelRes) },
            use24Hour = settings.use24Hour,
        )

        if (!force && text == settings.lastAppliedText) {
            return ApplyOutcome(text, next, CarrierResult.Ok, skipped = true)
        }

        val result = write(settings.targetSubIds, text, persistent)
        if (result.isOk) {
            repository.update { it.copy(lastAppliedText = text) }
        }
        Log.d(TAG, "apply text=$text result=$result")
        return ApplyOutcome(text, next, result)
    }

    suspend fun reset(): CarrierResult {
        val settings = repository.current()
        val subIds = resolveSubIds(settings.targetSubIds)
        if (subIds.isEmpty()) return CarrierResult.NoSim
        val result = subIds
            .map { CarrierNameManager.resetCarrierName(it) }
            .firstOrNull { !it.isOk } ?: CarrierResult.Ok
        repository.update { it.copy(lastAppliedText = null) }
        return result
    }

    fun preview(next: NextPrayer, template: String, now: Instant, use24Hour: Boolean): String =
        CarrierTemplate.render(
            template = template,
            next = next,
            now = now,
            labelOf = { context.getString(it.labelRes) },
            use24Hour = use24Hour,
        )

    fun labelOf(prayer: PrayerName): String = context.getString(prayer.labelRes)

    private fun write(targetSubIds: List<Int>, text: String, persistent: Boolean): CarrierResult {
        val subIds = resolveSubIds(targetSubIds)
        if (subIds.isEmpty()) return CarrierResult.NoSim
        var failure: CarrierResult? = null
        subIds.forEach { subId ->
            val result = CarrierNameManager.setCarrierName(subId, text, persistent)
            if (!result.isOk && failure == null) failure = result
        }
        return failure ?: CarrierResult.Ok
    }

    private fun resolveSubIds(targetSubIds: List<Int>): List<Int> {
        val active = CarrierNameManager.getSimSlots(context).map { it.subId }
        if (targetSubIds.isEmpty()) return active
        return targetSubIds.filter { it in active }.ifEmpty { active }
    }

    private companion object {
        const val TAG = "CarrierApplier"
    }
}
