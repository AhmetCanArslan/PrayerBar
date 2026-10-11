@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.arslan.prayerbar.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.DayTimes
import com.arslan.prayerbar.prayer.KerahatWindow
import com.arslan.prayerbar.prayer.kerahatWindows
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
    val tracked = state.settings.trackedPrayers
    val windows = remember(day, state.settings.kerahat) {
        day?.kerahatWindows(state.settings.kerahat).orEmpty()
    }
    // One timeline: the kerahat windows sit between the prayers they fall between.
    val entries = remember(day, tracked, windows) {
        if (day == null) {
            emptyList()
        } else {
            val prayers = PRAYERS_IN_ORDER.filter { it in tracked }
                .map { DayEntry.Prayer(it, day.at(it)) }
            (prayers + windows.map { DayEntry.Kerahat(it) }).sortedBy { it.at }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CountdownHero(state = state, labelOf = labelOf, onOpenLocation = onOpenLocation)
        }
        state.next?.kerahat?.let { window ->
            item {
                KerahatCard(
                    window = window,
                    now = state.now,
                    use24Hour = state.settings.use24Hour,
                )
            }
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
            if (windows.isNotEmpty()) {
                item {
                    DayStrip(
                        day = day,
                        windows = windows,
                        tracked = tracked,
                        now = state.now.takeIf { !showTomorrow },
                        use24Hour = state.settings.use24Hour,
                    )
                }
            }
            items(items = entries, key = { it.key }) { entry ->
                when (entry) {
                    is DayEntry.Prayer -> PrayerRow(
                        prayer = entry.prayer,
                        day = day,
                        labelOf = labelOf,
                        now = state.now,
                        isNext = !showTomorrow && state.next?.name == entry.prayer,
                        use24Hour = state.settings.use24Hour,
                    )
                    is DayEntry.Kerahat -> KerahatRow(
                        window = entry.window,
                        day = day,
                        now = state.now,
                        use24Hour = state.settings.use24Hour,
                    )
                }
            }
        }
    }
}

private sealed interface DayEntry {
    val at: Instant
    val key: String

    data class Prayer(val prayer: PrayerName, override val at: Instant) : DayEntry {
        override val key: String get() = prayer.name
    }

    data class Kerahat(val window: KerahatWindow) : DayEntry {
        override val at: Instant get() = window.start
        override val key: String get() = "kerahat-${window.kind.name}"
    }
}

/** The window running now, loudly, or the next one to open, quietly. */
@Composable
private fun KerahatCard(window: KerahatWindow, now: Instant, use24Hour: Boolean) {
    val active = window.contains(now)
    val container = if (active) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val onContainer = if (active) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supporting = if (active) onContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val remaining = CountdownFormat.remainingShort(
        Duration.between(now, if (active) window.end else window.start),
    )
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = if (active) onContainer else MaterialTheme.colorScheme.error,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (active) R.string.home_kerahat_active else R.string.home_kerahat_next,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = supporting,
                    )
                    Text(
                        text = stringResource(window.kind.labelRes),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = onContainer,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = window.range(use24Hour),
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer,
                    )
                    Text(
                        text = stringResource(
                            if (active) {
                                R.string.home_kerahat_ends_in
                            } else {
                                R.string.home_kerahat_starts_in
                            },
                            remaining,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = supporting,
                    )
                }
            }
            if (active) {
                LinearProgressIndicator(
                    progress = { window.progress(now) },
                    color = MaterialTheme.colorScheme.error,
                    trackColor = onContainer.copy(alpha = 0.16f),
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun KerahatWindow.range(use24Hour: Boolean): String = stringResource(
    R.string.kerahat_range,
    CountdownFormat.clock(start, use24Hour = use24Hour),
    CountdownFormat.clock(end, use24Hour = use24Hour),
)

/**
 * Fajr to Isha as one bar: the kerahat windows as bands, the tracked times as dots and, for today,
 * a marker where the day has got to.
 */
@Composable
private fun DayStrip(
    day: DayTimes,
    windows: List<KerahatWindow>,
    tracked: Set<PrayerName>,
    now: Instant?,
    use24Hour: Boolean,
) {
    val from = day.at(PrayerName.Fajr)
    val to = day.at(PrayerName.Isha)
    val span = (to.toEpochMilli() - from.toEpochMilli()).toFloat()
    if (span <= 0f) return
    fun fraction(instant: Instant): Float =
        ((instant.toEpochMilli() - from.toEpochMilli()) / span).coerceIn(0f, 1f)

    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val band = MaterialTheme.colorScheme.error
    val dot = MaterialTheme.colorScheme.onSurfaceVariant
    val marker = MaterialTheme.colorScheme.primary
    val dots = PRAYERS_IN_ORDER
        .filter { it in tracked }
        .map { day.at(it) }
        .filter { !it.isBefore(from) && !it.isAfter(to) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
        ) {
            val trackHeight = 8.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            val radius = CornerRadius(trackHeight / 2f)
            drawRoundRect(track, Offset(0f, top), Size(size.width, trackHeight), radius)
            windows.forEach { window ->
                val left = fraction(window.start) * size.width
                val right = fraction(window.end) * size.width
                if (right > left) {
                    drawRoundRect(band, Offset(left, top), Size(right - left, trackHeight), radius)
                }
            }
            val dotRadius = 2.dp.toPx()
            dots.forEach { at ->
                val x = (fraction(at) * size.width).coerceIn(dotRadius * 2, size.width - dotRadius * 2)
                drawCircle(dot, dotRadius, Offset(x, size.height / 2f))
            }
            if (now != null && !now.isBefore(from) && !now.isAfter(to)) {
                val stroke = 3.dp.toPx()
                val x = (fraction(now) * size.width).coerceIn(stroke / 2f, size.width - stroke / 2f)
                drawLine(
                    color = marker,
                    start = Offset(x, stroke / 2f),
                    end = Offset(x, size.height - stroke / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = CountdownFormat.clock(from, day.zone, use24Hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(band),
                )
                Text(
                    text = stringResource(R.string.home_kerahat_legend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = CountdownFormat.clock(to, day.zone, use24Hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/** Slimmer than a prayer row and marked down its edge, so it reads as a span between two times. */
@Composable
private fun KerahatRow(window: KerahatWindow, day: DayTimes, now: Instant, use24Hour: Boolean) {
    val active = window.contains(now)
    val elapsed = !window.end.isAfter(now)
    val container = if (active) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val onContainer = if (active) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 20.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = if (elapsed) 0.38f else 1f),
                    ),
            )
            Text(
                text = stringResource(R.string.kerahat_label) + " · " +
                    stringResource(window.kind.labelRes),
                style = if (active) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = onContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            Text(
                text = stringResource(
                    R.string.kerahat_range,
                    CountdownFormat.clock(window.start, day.zone, use24Hour),
                    CountdownFormat.clock(window.end, day.zone, use24Hour),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
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
