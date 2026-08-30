@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.arslan.prayerbar.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.PRAYERS_IN_ORDER
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.ui.MainUiState
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.SwitchRow
import androidx.compose.ui.res.stringResource
import java.time.Duration
import java.time.Instant

@Composable
fun HomeScreen(
    state: MainUiState,
    labelOf: (PrayerName) -> String,
    onToggleEnabled: (Boolean) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTomorrow by remember { mutableStateOf(false) }
    val day = if (showTomorrow) state.tomorrow else state.today

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CountdownHero(state = state, labelOf = labelOf, onOpenLocation = onOpenLocation)
        }
        item {
            CarrierCard(
                state = state,
                onToggleEnabled = onToggleEnabled,
                onApply = onApply,
                onReset = onReset,
                onRefresh = onRefresh,
            )
        }
        item {
            DayToggle(
                showTomorrow = showTomorrow,
                onSelect = { showTomorrow = it },
            )
        }
        if (day != null) {
            items(
                items = PRAYERS_IN_ORDER.filter { it in state.settings.trackedPrayers },
                key = { it.name },
            ) { prayer ->
                PrayerRow(
                    prayer = prayer,
                    day = day,
                    labelOf = labelOf,
                    now = state.now,
                    isNext = !showTomorrow && state.next?.name == prayer,
                    use24Hour = state.settings.use24Hour,
                )
            }
        }
    }
}

@Composable
private fun CountdownHero(
    state: MainUiState,
    labelOf: (PrayerName) -> String,
    onOpenLocation: () -> Unit,
) {
    val next = state.next
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "heroScale",
    )
    val progress = next?.progress(state.now) ?: 0f
    val spin by animateFloatAsState(
        targetValue = progress * 30f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "heroSpin",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .scale(scale)
                .rotate(spin)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { pressed = !pressed },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.rotate(-spin),
            ) {
                if (next == null) {
                    Text(
                        text = stringResource(R.string.home_no_location),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    androidx.compose.material3.TextButton(onClick = onOpenLocation) {
                        Text(stringResource(R.string.home_set_location))
                    }
                } else {
                    Text(
                        text = labelOf(next.name),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = CountdownFormat.remainingClock(Duration.between(state.now, next.at)),
                        style = MaterialTheme.typography.displayMediumEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = CountdownFormat.clock(
                            next.at,
                            use24Hour = state.settings.use24Hour,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CarrierCard(
    state: MainUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.home_carrier_preview)) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Text(
                text = state.previewText.ifBlank { "—" },
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }

        SwitchRow(
            title = stringResource(R.string.home_enabled),
            checked = state.settings.enabled,
            onCheckedChange = onToggleEnabled,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = onApply, enabled = !state.busy) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.home_apply))
            }
            FilledTonalIconButton(onClick = onReset, enabled = !state.busy) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.home_reset))
            }
            FilledTonalIconButton(onClick = onRefresh, enabled = !state.busy) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.home_refresh))
            }
            if (state.busy) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun DayToggle(showTomorrow: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.ToggleButton(
            checked = !showTomorrow,
            onCheckedChange = { onSelect(false) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.home_today))
        }
        androidx.compose.material3.ToggleButton(
            checked = showTomorrow,
            onCheckedChange = { onSelect(true) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.home_tomorrow))
        }
    }
}

@Composable
private fun PrayerRow(
    prayer: PrayerName,
    day: DayTimes,
    labelOf: (PrayerName) -> String,
    now: Instant,
    isNext: Boolean,
    use24Hour: Boolean,
) {
    val elapsed = day.at(prayer).isBefore(now)
    val container = when {
        isNext -> MaterialTheme.colorScheme.tertiaryContainer
        elapsed -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val onContainer = when {
        isNext -> MaterialTheme.colorScheme.onTertiaryContainer
        elapsed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        shape = if (isNext) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = labelOf(prayer),
                style = if (isNext) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = onContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CountdownFormat.clock(day.at(prayer), day.zone, use24Hour),
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
            )
        }
    }
}
