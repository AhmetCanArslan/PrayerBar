@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.PRAYERS_IN_ORDER
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.ui.components.ChoiceList
import com.arslan.prayerbar.ui.components.SectionCard
import io.github.meypod.adhan_kotlin.HighLatitudeRule
import io.github.meypod.adhan_kotlin.Madhab
import io.github.meypod.adhan_kotlin.MidnightMethod
import io.github.meypod.adhan_kotlin.PolarCircleResolution
import io.github.meypod.adhan_kotlin.model.Rounding
import io.github.meypod.adhan_kotlin.model.Shafaq

@Composable
fun AdvancedScreen(
    settings: PrayerSettings,
    labelOf: (PrayerName) -> String,
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
            SectionCard(title = stringResource(R.string.advanced_madhab)) {
                // Shape-morphing toggle pair: the selected side rounds into the pressed shape.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToggleButton(
                        checked = params.madhab == Madhab.SHAFI,
                        onCheckedChange = { onMadhab(Madhab.SHAFI) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.advanced_madhab_shafi))
                    }
                    ToggleButton(
                        checked = params.madhab == Madhab.HANAFI,
                        onCheckedChange = { onMadhab(Madhab.HANAFI) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.advanced_madhab_hanafi))
                    }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.advanced_high_latitude)) {
                val auto = stringResource(R.string.advanced_high_latitude_auto)
                ChoiceList(
                    options = listOf<HighLatitudeRule?>(null) + HighLatitudeRule.entries,
                    selected = params.highLatitudeRule,
                    label = { rule -> rule?.name?.humanize() ?: auto },
                    onSelect = onHighLatitude,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.advanced_shafaq)) {
                ChoiceList(
                    options = Shafaq.entries,
                    selected = params.shafaq,
                    label = { it.name.humanize() },
                    onSelect = onShafaq,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.advanced_polar)) {
                ChoiceList(
                    options = PolarCircleResolution.entries,
                    selected = params.polarCircleResolution,
                    label = { it.name.humanize() },
                    onSelect = onPolar,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.advanced_rounding)) {
                ChoiceList(
                    options = Rounding.entries,
                    selected = params.rounding,
                    label = { it.name.humanize() },
                    onSelect = onRounding,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.advanced_midnight)) {
                ChoiceList(
                    options = MidnightMethod.entries,
                    selected = settings.midnightMethod,
                    label = { it.name.humanize() },
                    onSelect = onMidnight,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.advanced_tracked)) {
                androidx.compose.foundation.layout.FlowRow(
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
    }
}

internal fun String.humanize(): String = split(Regex("_|(?<=[a-z])(?=[A-Z])"))
    .joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }
