@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.PRAYERS_IN_ORDER
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.WidgetSettings
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.SwitchRow
import com.arslan.prayerbar.widget.WidgetContent
import com.arslan.prayerbar.widget.WidgetPalette

const val WIDGET_PREVIEW_CAPACITY = 6

@Composable
fun WidgetScreen(
    widgetIds: List<Int>,
    widgetOf: (Int) -> WidgetSettings,
    previewOf: (Int) -> WidgetContent,
    labelOf: (PrayerName) -> String,
    onEdit: (Int, (WidgetSettings) -> WidgetSettings) -> Unit,
    onTogglePrayer: (Int, PrayerName) -> Unit,
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (widgetIds.isEmpty()) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard(
                    title = stringResource(R.string.widget_none),
                    supporting = stringResource(R.string.widget_none_hint),
                ) {
                    OutlinedButton(onClick = onAddWidget) {
                        Text(stringResource(R.string.widget_add))
                    }
                }
            }
        }
        return
    }

    var selected by remember { mutableIntStateOf(widgetIds.first()) }

    val current = if (selected in widgetIds) selected else widgetIds.first()

    WidgetEditor(
        widget = widgetOf(current),
        preview = previewOf(current),
        labelOf = labelOf,
        onEdit = { transform -> onEdit(current, transform) },
        onTogglePrayer = { prayer -> onTogglePrayer(current, prayer) },
        modifier = modifier,
        header = if (widgetIds.size > 1) {
            {
                SectionCard(title = stringResource(R.string.widget_title)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        widgetIds.forEachIndexed { index, id ->
                            FilterChip(
                                selected = id == current,
                                onClick = { selected = id },
                                label = {
                                    Text(stringResource(R.string.widget_instance, index + 1))
                                },
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun WidgetEditor(
    widget: WidgetSettings,
    preview: WidgetContent,
    labelOf: (PrayerName) -> String,
    onEdit: ((WidgetSettings) -> WidgetSettings) -> Unit,
    onTogglePrayer: (PrayerName) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header != null) item { header() }
        item {
            SectionCard(
                title = stringResource(R.string.widget_title),
                supporting = stringResource(R.string.widget_size_hint),
            ) {
                WidgetPreview(widget = widget, content = preview)
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.widget_content),
                supporting = stringResource(R.string.widget_content_hint),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRAYERS_IN_ORDER.forEach { prayer ->
                        FilterChip(
                            selected = prayer in widget.visiblePrayers,
                            onClick = { onTogglePrayer(prayer) },
                            label = { Text(labelOf(prayer)) },
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.widget_appearance)) {
                SwitchRow(
                    title = stringResource(R.string.widget_show_countdown),
                    supporting = stringResource(R.string.widget_show_countdown_hint),
                    checked = widget.showCountdown,
                    onCheckedChange = { on -> onEdit { it.copy(showCountdown = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_seconds),
                    supporting = stringResource(R.string.widget_show_seconds_hint),
                    checked = widget.showSeconds,
                    onCheckedChange = { on -> onEdit { it.copy(showSeconds = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_progress),
                    supporting = stringResource(R.string.widget_show_progress_hint),
                    checked = widget.showProgress,
                    onCheckedChange = { on -> onEdit { it.copy(showProgress = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_icon),
                    checked = widget.showIcon,
                    onCheckedChange = { on -> onEdit { it.copy(showIcon = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_use_prayer_color),
                    supporting = stringResource(R.string.widget_use_prayer_color_hint),
                    checked = widget.usePrayerColor,
                    onCheckedChange = { on -> onEdit { it.copy(usePrayerColor = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_hijri),
                    checked = widget.showHijri,
                    onCheckedChange = { on -> onEdit { it.copy(showHijri = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_location),
                    checked = widget.showLocation,
                    onCheckedChange = { on -> onEdit { it.copy(showLocation = on) } },
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.widget_background),
                supporting = stringResource(R.string.widget_background_hint),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetSettings.ALPHA_CHOICES.forEach { alpha ->
                        FilterChip(
                            selected = widget.backgroundAlpha == alpha,
                            onClick = { onEdit { it.copy(backgroundAlpha = alpha) } },
                            label = { Text(stringResource(R.string.widget_alpha, alpha)) },
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.widget_follow_system),
                    supporting = stringResource(R.string.widget_follow_system_hint),
                    checked = widget.followSystemTheme,
                    onCheckedChange = { on -> onEdit { it.copy(followSystemTheme = on) } },
                )
            }
        }
    }
}

@Composable
private fun WidgetPreview(widget: WidgetSettings, content: WidgetContent) {
    val palette = WidgetPalette.of(LocalContext.current, widget)
    val surface = Color(palette.surface)
    val onSurface = Color(palette.onSurface)
    val variant = Color(palette.onSurfaceVariant)
    val accent = Color(content.accent)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surface.copy(alpha = widget.backgroundAlpha / 100f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (widget.showIcon) {
                        Icon(
                            painter = painterResource(content.iconRes),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = content.headline,
                        style = MaterialTheme.typography.labelLarge,
                        color = variant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = if (widget.showIcon) 8.dp else 0.dp),
                    )
                }
                Text(
                    text = content.emphasis,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (widget.showCountdown) {
                    Text(
                        text = content.time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface,
                    )
                }
                if (content.meta.isNotEmpty()) {
                    Text(
                        text = content.meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = variant,
                    )
                }
                if (content.showProgress) {
                    LinearProgressIndicator(
                        progress = { content.progress / 1000f },
                        color = accent,
                        trackColor = variant.copy(alpha = 0.25f),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(5.dp),
                    )
                }
            }
            if (content.rows.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    content.rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (widget.showIcon) {
                                Icon(
                                    painter = painterResource(row.iconRes),
                                    contentDescription = null,
                                    tint = Color(row.color)
                                        .copy(alpha = if (row.elapsed) 0.43f else 1f),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                            }
                            Text(
                                text = row.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.isNext) accent else variant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = row.time,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (row.isNext) FontWeight.SemiBold else null,
                                color = when {
                                    row.isNext -> accent
                                    row.elapsed -> variant
                                    else -> onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
