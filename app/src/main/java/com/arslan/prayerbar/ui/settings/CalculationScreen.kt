@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.PRAYERS_IN_ORDER
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.isMethodModified
import com.arslan.prayerbar.ui.components.DropdownField
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.Stepper
import io.github.meypod.adhan_kotlin.CalculationMethod
import io.github.meypod.adhan_kotlin.HighLatitudeRule
import io.github.meypod.adhan_kotlin.Madhab
import io.github.meypod.adhan_kotlin.MidnightMethod
import io.github.meypod.adhan_kotlin.PolarCircleResolution
import io.github.meypod.adhan_kotlin.model.Rounding
import io.github.meypod.adhan_kotlin.model.Shafaq

/**
 * Every knob that moves a prayer time, on one screen: the method and its angles first, then the
 * rules that only a few users touch, then what to track and the manual offsets. They used to be
 * split across two tabs, which meant guessing which half a setting lived in.
 */
@Composable
fun CalculationScreen(
    settings: PrayerSettings,
    labelOf: (PrayerName) -> String,
    onMethodSelected: (CalculationMethod) -> Unit,
    onRestoreDefaults: () -> Unit,
    onFajrAngle: (Double) -> Unit,
    onIshaAngle: (Double) -> Unit,
    onIshaInterval: (Int) -> Unit,
    onMaghribAngle: (Double) -> Unit,
    onAdjustment: (PrayerName, Int) -> Unit,
    onResetAdjustments: () -> Unit,
    onMadhab: (Madhab) -> Unit,
    onHighLatitude: (HighLatitudeRule?) -> Unit,
    onShafaq: (Shafaq) -> Unit,
    onPolar: (PolarCircleResolution) -> Unit,
    onRounding: (Rounding) -> Unit,
    onMidnight: (MidnightMethod) -> Unit,
    onToggleTracked: (PrayerName) -> Unit,
    modifier: Modifier = Modifier,
) {
    val params = settings.parameters

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.calc_method)) {
                DropdownField(
                    label = stringResource(R.string.calc_method),
                    options = CalculationMethod.entries,
                    selected = params.method,
                    optionLabel = { it.displayName() },
                    onSelect = onMethodSelected,
                )
                if (params.isMethodModified()) {
                    AssistChip(
                        onClick = onRestoreDefaults,
                        label = { Text(stringResource(R.string.calc_method_modified)) },
                    )
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.calc_fajr_angle)) {
                Stepper(
                    title = stringResource(R.string.calc_fajr_angle),
                    value = "%.1f°".format(params.fajrAngle),
                    onDecrease = { onFajrAngle(params.fajrAngle - 0.5) },
                    onIncrease = { onFajrAngle(params.fajrAngle + 0.5) },
                )
                Stepper(
                    title = stringResource(R.string.calc_isha_angle),
                    value = "%.1f°".format(params.ishaAngle),
                    onDecrease = { onIshaAngle(params.ishaAngle - 0.5) },
                    onIncrease = { onIshaAngle(params.ishaAngle + 0.5) },
                )
                Stepper(
                    title = stringResource(R.string.calc_isha_interval),
                    value = "${params.ishaInterval}",
                    onDecrease = { onIshaInterval(params.ishaInterval - 5) },
                    onIncrease = { onIshaInterval(params.ishaInterval + 5) },
                )
                Stepper(
                    title = stringResource(R.string.calc_maghrib_angle),
                    value = "%.1f°".format(params.maghribAngle),
                    onDecrease = { onMaghribAngle(params.maghribAngle - 0.5) },
                    onIncrease = { onMaghribAngle(params.maghribAngle + 0.5) },
                )
                TextButton(onClick = onRestoreDefaults) {
                    Text(stringResource(R.string.calc_restore_method))
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.calc_rules)) {
                val shafi = stringResource(R.string.advanced_madhab_shafi)
                val hanafi = stringResource(R.string.advanced_madhab_hanafi)
                DropdownField(
                    label = stringResource(R.string.advanced_madhab),
                    options = Madhab.entries,
                    selected = params.madhab,
                    optionLabel = { if (it == Madhab.HANAFI) hanafi else shafi },
                    onSelect = onMadhab,
                )
                val auto = stringResource(R.string.advanced_high_latitude_auto)
                DropdownField(
                    label = stringResource(R.string.advanced_high_latitude),
                    options = listOf<HighLatitudeRule?>(null) + HighLatitudeRule.entries,
                    selected = params.highLatitudeRule,
                    optionLabel = { rule -> rule?.name?.humanize() ?: auto },
                    onSelect = onHighLatitude,
                )
                DropdownField(
                    label = stringResource(R.string.advanced_shafaq),
                    options = Shafaq.entries,
                    selected = params.shafaq,
                    optionLabel = { it.name.humanize() },
                    onSelect = onShafaq,
                )
                DropdownField(
                    label = stringResource(R.string.advanced_polar),
                    options = PolarCircleResolution.entries,
                    selected = params.polarCircleResolution,
                    optionLabel = { it.name.humanize() },
                    onSelect = onPolar,
                )
                DropdownField(
                    label = stringResource(R.string.advanced_rounding),
                    options = Rounding.entries,
                    selected = params.rounding,
                    optionLabel = { it.name.humanize() },
                    onSelect = onRounding,
                )
                DropdownField(
                    label = stringResource(R.string.advanced_midnight),
                    options = MidnightMethod.entries,
                    selected = settings.midnightMethod,
                    optionLabel = { it.name.humanize() },
                    onSelect = onMidnight,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.advanced_tracked)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRAYERS_IN_ORDER.forEach { prayer ->
                        FilterChip(
                            selected = prayer in settings.trackedPrayers,
                            onClick = { onToggleTracked(prayer) },
                            label = { Text(labelOf(prayer)) },
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.calc_adjustments)) {
                PRAYERS_IN_ORDER.forEach { prayer ->
                    val current = settings.adjustments.forPrayer(prayer)
                    Stepper(
                        title = labelOf(prayer),
                        value = if (current > 0) "+$current" else "$current",
                        onDecrease = { onAdjustment(prayer, current - 1) },
                        onIncrease = { onAdjustment(prayer, current + 1) },
                    )
                }
                TextButton(onClick = onResetAdjustments) {
                    Text("0", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Enum constants render as `UMM_AL_QURA`; show them as `Umm Al Qura`. */
fun CalculationMethod.displayName(): String = name
    .split('_')
    .joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { it.uppercase() }
    }

internal fun String.humanize(): String = split(Regex("_|(?<=[a-z])(?=[A-Z])"))
    .joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }
