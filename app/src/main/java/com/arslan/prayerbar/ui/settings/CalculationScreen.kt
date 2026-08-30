@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.arslan.prayerbar.ui.components.ChoiceList
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.Stepper
import io.github.meypod.adhan_kotlin.CalculationMethod

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
                if (params.isMethodModified()) {
                    AssistChip(
                        onClick = onRestoreDefaults,
                        label = { Text(stringResource(R.string.calc_method_modified)) },
                    )
                }
                ChoiceList(
                    options = CalculationMethod.entries,
                    selected = params.method,
                    label = { it.displayName() },
                    onSelect = onMethodSelected,
                )
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
