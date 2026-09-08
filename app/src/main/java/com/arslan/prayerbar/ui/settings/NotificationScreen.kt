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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.notification.NotificationContent
import com.arslan.prayerbar.prayer.CountdownFormat
import com.arslan.prayerbar.prayer.NotificationSettings
import com.arslan.prayerbar.prayer.PRAYERS_IN_ORDER
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.SwitchRow
import java.time.Duration

/**
 * The ongoing status notification. It is its own surface, so it gets its own screen: its own
 * templates, its own switch and its own channel, none of which the carrier label or the tile share.
 */
@Composable
fun NotificationScreen(
    settings: PrayerSettings,
    preview: NotificationContent?,
    notificationsAllowed: Boolean,
    labelOf: (PrayerName) -> String,
    onEnabled: (Boolean) -> Unit,
    onEdit: ((NotificationSettings) -> NotificationSettings) -> Unit,
    onTogglePrayer: (PrayerName) -> Unit,
    onOpenChannelSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val options = settings.notification

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.status_preview),
                supporting = stringResource(R.string.status_expanded_hint),
            ) {
                if (preview != null) NotificationPreview(options, preview)
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.status_enabled),
                supporting = stringResource(R.string.status_enabled_hint),
            ) {
                SwitchRow(
                    title = stringResource(R.string.status_enabled),
                    checked = options.enabled,
                    onCheckedChange = onEnabled,
                )
                if (!notificationsAllowed) {
                    Text(
                        text = stringResource(R.string.notification_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.shizuku_grant))
                    }
                }
                OutlinedButton(onClick = onOpenChannelSettings) {
                    Text(stringResource(R.string.status_channel_settings))
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.status_template)) {
                TemplateField(
                    value = options.titleTemplate(),
                    onValueChange = { template -> onEdit { it.copy(template = template) } },
                    locale = locale,
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.status_subtitle),
                supporting = stringResource(R.string.status_subtitle_hint),
            ) {
                TemplateField(
                    value = options.bodyTemplate(locale),
                    onValueChange = { template -> onEdit { it.copy(subtitleTemplate = template) } },
                    locale = locale,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.status_appearance)) {
                SwitchRow(
                    title = stringResource(R.string.status_chronometer),
                    supporting = stringResource(R.string.status_chronometer_hint),
                    checked = options.showChronometer,
                    onCheckedChange = { on -> onEdit { it.copy(showChronometer = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.status_progress),
                    supporting = stringResource(R.string.status_progress_hint),
                    checked = options.showProgress,
                    onCheckedChange = { on -> onEdit { it.copy(showProgress = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_icon),
                    supporting = stringResource(R.string.tile_show_icon_hint),
                    checked = options.showIcon,
                    onCheckedChange = { on -> onEdit { it.copy(showIcon = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_use_prayer_color),
                    supporting = stringResource(R.string.widget_use_prayer_color_hint),
                    checked = options.usePrayerColor,
                    onCheckedChange = { on -> onEdit { it.copy(usePrayerColor = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.status_colorized),
                    supporting = stringResource(R.string.status_colorized_hint),
                    checked = options.colorized,
                    onCheckedChange = { on -> onEdit { it.copy(colorized = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_location),
                    checked = options.showLocation,
                    onCheckedChange = { on -> onEdit { it.copy(showLocation = on) } },
                )
                SwitchRow(
                    title = stringResource(R.string.widget_show_hijri),
                    checked = options.showHijri,
                    onCheckedChange = { on -> onEdit { it.copy(showHijri = on) } },
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.status_timeline),
                supporting = stringResource(R.string.status_timeline_hint),
            ) {
                SwitchRow(
                    title = stringResource(R.string.status_timeline),
                    checked = options.showTimeline,
                    onCheckedChange = { on -> onEdit { it.copy(showTimeline = on) } },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRAYERS_IN_ORDER.forEach { prayer ->
                        FilterChip(
                            selected = prayer in options.visiblePrayers,
                            onClick = { onTogglePrayer(prayer) },
                            enabled = options.showTimeline,
                            label = { Text(labelOf(prayer)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A stand-in for the real shade entry, expanded: the header line, the two text lines, the bar and
 * the timeline the notification folds away until it is pulled open.
 */
@Composable
private fun NotificationPreview(options: NotificationSettings, content: NotificationContent) {
    val accent = Color(content.accent)
    val colorized = content.colorized
    val container = if (colorized) accent else MaterialTheme.colorScheme.surfaceContainerHighest
    val onContainer = if (colorized) Color.White else MaterialTheme.colorScheme.onSurface
    val variant = if (colorized) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (colorized) onContainer else accent

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(content.iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = header(content),
                    style = MaterialTheme.typography.labelMedium,
                    color = variant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (options.showChronometer && content.configured) {
                    Text(
                        // The platform draws the same shape here, counting itself down every second.
                        text = CountdownFormat.remainingChronometer(
                            Duration.ofMillis(content.remainingMillis),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = variant,
                    )
                }
            }
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = content.text,
                style = MaterialTheme.typography.bodyMedium,
                color = variant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.showProgress) {
                LinearProgressIndicator(
                    progress = { content.progress.toFloat() / NotificationContent.PROGRESS_MAX },
                    color = if (colorized) onContainer else accent,
                    trackColor = variant.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(5.dp),
                )
            }
            if (content.lines.isNotEmpty()) {
                HorizontalDivider(
                    color = variant.copy(alpha = 0.25f),
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                content.lines.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = line.time,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (line.isNext) FontWeight.SemiBold else null,
                            color = if (line.isNext) onContainer else variant,
                            modifier = Modifier.widthIn(min = 64.dp),
                        )
                        Text(
                            text = line.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (line.isNext) FontWeight.SemiBold else null,
                            color = if (line.isNext) onContainer else variant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun header(content: NotificationContent): String {
    val app = stringResource(R.string.app_name)
    return if (content.meta.isBlank()) app else "$app · ${content.meta}"
}
