@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.arslan.prayerbar.R
import com.arslan.prayerbar.location.CoordinateLabel
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.ui.components.SectionCard
import com.arslan.prayerbar.ui.components.SwitchRow
import android.text.format.DateUtils

@Composable
fun LocationScreen(
    settings: PrayerSettings,
    busy: Boolean,
    onUseGps: (String) -> Unit,
    onFollowLocation: (Boolean) -> Unit,
    onRefreshFollowed: () -> Unit,
    onAddManual: (String, Double, Double) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    val parsedLat = latitude.toDoubleOrNull()
    val parsedLng = longitude.toDoubleOrNull()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.location_follow_section)) {
                SwitchRow(
                    title = stringResource(R.string.location_follow_title),
                    supporting = stringResource(R.string.location_follow_supporting),
                    checked = settings.tracking.enabled,
                    onCheckedChange = onFollowLocation,
                )
                if (settings.tracking.enabled) {
                    val followed = settings.tracking.location
                    Text(
                        text = followed?.label ?: stringResource(R.string.location_follow_waiting),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(onClick = onRefreshFollowed, enabled = !busy) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null)
                            Text(
                                text = stringResource(R.string.location_follow_refresh),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Text(
                            text = lastReadLabel(settings.tracking.checkedAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.location_title)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.location_label)) },
                    supportingText = { Text(stringResource(R.string.location_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text(stringResource(R.string.location_latitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text(stringResource(R.string.location_longitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onUseGps(label) },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null)
                        Text(
                            text = stringResource(R.string.location_use_gps),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            onAddManual(label, parsedLat ?: 0.0, parsedLng ?: 0.0)
                            label = ""
                            latitude = ""
                            longitude = ""
                        },
                        enabled = parsedLat != null && parsedLng != null,
                    ) {
                        Text(stringResource(R.string.location_save))
                    }
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.location_saved_section)) {
                if (settings.locations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.location_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                settings.locations.forEach { location ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            // While the phone is being followed nothing saved is in use, so no
                            // radio is filled in — picking one here is what switches following off.
                            selected = !settings.tracking.enabled &&
                                location.id == settings.activeLocation?.id,
                            onClick = { onSelect(location.id) },
                        )
                        Text(
                            text = location.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        // The place name, not the coordinates — and nothing at all once the name
                        // is already the label. Coordinates only stand in while no name is known.
                        val detail = when (location.city) {
                            null -> CoordinateLabel.of(location.latitude, location.longitude)
                            location.label -> null
                            else -> location.city
                        }
                        if (detail != null) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDelete(location.id) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.location_delete),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "5 minutes ago", or a plain note while the fix has never been read. */
@Composable
private fun lastReadLabel(checkedAtMillis: Long): String =
    if (checkedAtMillis <= 0L) {
        stringResource(R.string.location_follow_never)
    } else {
        stringResource(
            R.string.location_follow_updated,
            DateUtils.getRelativeTimeSpanString(
                checkedAtMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
        )
    }
