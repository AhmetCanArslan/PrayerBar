@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.carrier.CarrierTemplate
import com.arslan.prayerbar.carrier.SimSlot
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.SwitchRow

@Composable
fun FormatScreen(
    settings: PrayerSettings,
    previewText: String,
    simSlots: List<SimSlot>,
    onTemplate: (String) -> Unit,
    onUse24Hour: (Boolean) -> Unit,
    onTargetSubIds: (List<Int>) -> Unit,
    onRestartSystemUi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.format_preview)) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        text = previewText.ifBlank { "—" },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.format_template)) {
                OutlinedTextField(
                    value = settings.template,
                    onValueChange = onTemplate,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.format_presets),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CarrierTemplate.PRESETS.forEach { preset ->
                        FilterChip(
                            selected = settings.template == preset,
                            onClick = { onTemplate(preset) },
                            label = { Text(preset) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.format_tokens),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CarrierTemplate.TOKENS.forEach { token ->
                        AssistChip(
                            onClick = { onTemplate(settings.template + token) },
                            label = { Text(token) },
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.format_clock_24h),
                    checked = settings.use24Hour,
                    onCheckedChange = onUse24Hour,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.format_sim)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.targetSubIds.isEmpty(),
                        onClick = { onTargetSubIds(emptyList()) },
                        label = { Text(stringResource(R.string.format_sim_all)) },
                    )
                    simSlots.forEach { slot ->
                        FilterChip(
                            selected = slot.subId in settings.targetSubIds,
                            onClick = {
                                val current = settings.targetSubIds
                                onTargetSubIds(
                                    if (slot.subId in current) {
                                        current - slot.subId
                                    } else {
                                        current + slot.subId
                                    },
                                )
                            },
                            label = { Text("${slot.slotIndex + 1} · ${slot.carrierName}") },
                        )
                    }
                }
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.format_restart_systemui),
                supporting = stringResource(R.string.format_restart_systemui_hint),
            ) {
                OutlinedButton(onClick = onRestartSystemUi) {
                    Text(stringResource(R.string.format_restart_systemui))
                }
            }
        }
    }
}
