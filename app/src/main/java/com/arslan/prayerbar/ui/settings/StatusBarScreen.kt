package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.statusbar.StatusBarText
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.SwitchRow

/**
 * The status bar surface: the same template as the carrier label, drawn as real SystemUI icons.
 *
 * The preview is built from the very drawables that go into the bar, so what it shows is what
 * lands there — including the characters the glyph set cannot draw, which are reported rather than
 * silently swallowed.
 */
@Composable
fun StatusBarScreen(
    settings: PrayerSettings,
    preview: String,
    hasShizuku: Boolean,
    onEnabled: (Boolean) -> Unit,
    onTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val statusBar = settings.statusBar
    val slots = StatusBarText.segments(preview)
    val dropped = StatusBarText.unsupported(preview)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.status_bar_preview)) {
                StatusBarPreview(slots)
                if (dropped.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.status_bar_unsupported,
                            dropped.joinToString(" ") { "\"$it\"" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (!hasShizuku) {
            item {
                SectionCard(title = stringResource(R.string.nav_status_bar)) {
                    Text(
                        text = stringResource(R.string.status_bar_needs_shizuku),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.status_bar_enabled),
                supporting = stringResource(R.string.status_bar_enabled_hint),
            ) {
                SwitchRow(
                    title = stringResource(R.string.status_bar_enabled),
                    supporting = stringResource(R.string.status_bar_reboot_hint),
                    checked = statusBar.enabled,
                    onCheckedChange = onEnabled,
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.status_bar_template),
                supporting = stringResource(
                    R.string.status_bar_length_hint,
                    StatusBarText.MAX_SLOTS,
                ),
            ) {
                TemplateField(
                    value = statusBar.labelTemplate(),
                    onValueChange = onTemplate,
                    locale = locale,
                )
            }
        }
    }
}

/** A stand-in bar: a clock on the left, the real drawables where the system icons sit. */
@Composable
private fun StatusBarPreview(slots: List<Int>) {
    Surface(
        color = Color(0xFF1B1B1B),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "12:30",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slots.forEach { slot ->
                    Image(
                        painter = painterResource(slot),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.height(16.dp),
                    )
                }
            }
        }
    }
}
