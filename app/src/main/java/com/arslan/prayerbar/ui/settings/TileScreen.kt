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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.PRAYERS_IN_ORDER
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.prayer.PrayerName
import com.arslan.prayerbar.prayer.TileSettings
import com.arslan.prayerbar.prayer.visual
import com.arslan.prayerbar.tile.TileContent
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.SwitchRow

/**
 * Everything about the Quick Settings tile lives here rather than on the carrier-label screen: the
 * two surfaces are independent — different shapes (two lines and an icon versus one cramped string),
 * their own templates, and a switch each — and the user is usually tuning one or the other.
 */
@Composable
fun TileScreen(
    settings: PrayerSettings,
    preview: TileContent?,
    labelOf: (PrayerName) -> String,
    onTemplate: (String) -> Unit,
    onSubtitleTemplate: (String) -> Unit,
    onTileEnabled: (Boolean) -> Unit,
    onShowIcon: (Boolean) -> Unit,
    onHighlightMinutes: (Int) -> Unit,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val tile = settings.tile

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.tile_preview)) {
                if (preview != null) TilePreview(preview)
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.tile_add),
                supporting = stringResource(R.string.tile_hint),
            ) {
                OutlinedButton(onClick = onAddTile) {
                    Text(stringResource(R.string.tile_add))
                }
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.tile_enabled),
                supporting = stringResource(R.string.tile_enabled_hint),
            ) {
                SwitchRow(
                    title = stringResource(R.string.tile_enabled),
                    checked = tile.enabled,
                    onCheckedChange = onTileEnabled,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.tile_label_template)) {
                TemplateField(
                    value = tile.labelTemplate(),
                    onValueChange = onTemplate,
                    locale = locale,
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.tile_subtitle_template),
                supporting = stringResource(R.string.tile_subtitle_hint),
            ) {
                TemplateField(
                    value = tile.subtitleTemplate,
                    onValueChange = onSubtitleTemplate,
                    locale = locale,
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.tile_highlight),
                supporting = stringResource(R.string.tile_highlight_hint),
            ) {
                SwitchRow(
                    title = stringResource(R.string.tile_show_icon),
                    supporting = stringResource(R.string.tile_show_icon_hint),
                    checked = tile.showIcon,
                    onCheckedChange = onShowIcon,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileSettings.HIGHLIGHT_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = tile.highlightMinutes == minutes,
                            onClick = { onHighlightMinutes(minutes) },
                            label = {
                                Text(
                                    if (minutes == 0) {
                                        stringResource(R.string.tile_highlight_off)
                                    } else {
                                        stringResource(R.string.tile_highlight_minutes, minutes)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.tile_icons),
                supporting = stringResource(R.string.tile_color_hint),
            ) {
                PRAYERS_IN_ORDER.forEach { prayer ->
                    val visual = prayer.visual
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(visual.iconRes),
                            contentDescription = null,
                            tint = Color(visual.color),
                            modifier = Modifier.size(24.dp),
                        )
                        Text(labelOf(prayer), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** A stand-in for the real tile, so the colour and the two lines can be judged before adding it. */
@Composable
private fun TilePreview(content: TileContent) {
    val accent = Color(content.color)
    val container = if (content.highlighted) accent else MaterialTheme.colorScheme.surfaceContainerHighest
    val onContainer = if (content.highlighted) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(content.iconRes),
                contentDescription = null,
                tint = if (content.highlighted) onContainer else accent,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                content.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
