package com.arslan.prayerbar.statusbar

import android.content.Context
import android.util.Log
import com.arslan.prayerbar.carrier.CarrierTemplate
import com.arslan.prayerbar.carrier.ShizukuHelper
import com.arslan.prayerbar.data.SettingsRepository
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.NextPrayerResolver
import com.arslan.prayerbar.prayer.PrayerSettings
import java.time.Instant
import java.time.ZoneId

data class StatusBarOutcome(
    val text: String?,
    val result: StatusBarResult,
    val skipped: Boolean = false,
)

/**
 * Renders the label and drives [StatusBarIcons], the same way [com.arslan.prayerbar.carrier
 * .CarrierApplier] drives the carrier label. It shares the template engine with the carrier
 * surface, so a template written for one reads the same in the other.
 *
 * The icons do not survive a reboot — SystemUI keeps them only for the life of the running system
 * — so the boot receiver's forced refresh is what puts them back.
 */
class StatusBarApplier(
    private val context: Context,
    private val repository: SettingsRepository,
    private val resolver: NextPrayerResolver = NextPrayerResolver(),
) {

    suspend fun apply(now: Instant = Instant.now(), force: Boolean = false): StatusBarOutcome {
        val settings = repository.current()
        val statusBar = settings.statusBar
        if (!statusBar.enabled) return turnOff(statusBar.lastAppliedText)

        if (!ShizukuHelper.hasPermission()) {
            return StatusBarOutcome(null, StatusBarResult.NoShizuku)
        }
        val next = resolver.resolve(now, settings, ZoneId.systemDefault())
            ?: return StatusBarOutcome(null, StatusBarResult.NoLocation)

        val rendered = render(settings, next, now)
        val text = StatusBarText.sanitize(rendered)
        val previous = statusBar.lastAppliedText
        if (!force && text == previous) {
            return StatusBarOutcome(text, StatusBarResult.Ok, skipped = true)
        }

        val applied = StatusBarIcons.apply(context, text, rendered)
        if (!applied) return StatusBarOutcome(text, StatusBarResult.WriteFailed)

        store(text)
        Log.d(TAG, "apply text=$text")
        return StatusBarOutcome(text, StatusBarResult.Ok)
    }

    /** Takes the label out of the bar and forgets it, so a later write starts from a clean slot. */
    suspend fun clear(): Boolean {
        repository.current().statusBar.lastAppliedText ?: return true
        val cleared = StatusBarIcons.clear(context)
        if (cleared) store(null)
        return cleared
    }

    /**
     * The label as the template renders it, before [StatusBarText] has had its say — the settings
     * screen needs the raw text to tell the user which characters it had to drop.
     */
    fun preview(next: NextPrayer, template: String, now: Instant, use24Hour: Boolean): String =
        CarrierTemplate.render(
            template = template,
            next = next,
            now = now,
            labelOf = { context.getString(it.labelRes) },
            use24Hour = use24Hour,
            remainingFormat = CountdownFormat::remainingCompact,
        )

    private suspend fun turnOff(previous: String?): StatusBarOutcome {
        if (previous == null) return StatusBarOutcome(null, StatusBarResult.Off)
        if (StatusBarIcons.clear(context)) store(null)
        return StatusBarOutcome(null, StatusBarResult.Off)
    }

    private fun render(settings: PrayerSettings, next: NextPrayer, now: Instant): String =
        CarrierTemplate.render(
            template = settings.statusBar.labelTemplate(),
            next = next,
            now = now,
            labelOf = { context.getString(it.labelRes) },
            use24Hour = settings.use24Hour,
            remainingFormat = CountdownFormat::remainingCompact,
        )

    private suspend fun store(text: String?) {
        repository.update { it.copy(statusBar = it.statusBar.copy(lastAppliedText = text)) }
    }

    private companion object {
        const val TAG = "StatusBarApplier"
    }
}
