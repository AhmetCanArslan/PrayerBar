@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.arslan.prayerbar.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.ui.ShizukuState
import com.arslan.prayerbar.ui.components.SectionCard

@Composable
fun ShizukuScreen(
    state: ShizukuState,
    onGrant: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(
            title = stringResource(R.string.shizuku_title),
            supporting = stringResource(R.string.shizuku_hint),
        ) {
            val status = when {
                state.granted -> stringResource(R.string.shizuku_state_ready)
                state.running -> stringResource(R.string.shizuku_state_no_permission)
                else -> stringResource(R.string.shizuku_state_missing)
            }
            Text(text = status, style = MaterialTheme.typography.bodyLarge)
            if (!state.granted) {
                Button(onClick = onGrant, enabled = state.running) {
                    Text(stringResource(R.string.shizuku_grant))
                }
            }
        }

        SectionCard(title = stringResource(R.string.shizuku_phone_permission)) {
            Text(
                text = if (state.phonePermission) "✓" else "✗",
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            if (!state.phonePermission) {
                Button(onClick = onRequestPhonePermission) {
                    Text(stringResource(R.string.shizuku_grant))
                }
            }
        }

        SectionCard(title = stringResource(R.string.shizuku_exact_alarm)) {
            if (state.canScheduleExact) {
                Text(text = "✓", style = MaterialTheme.typography.titleLargeEmphasized)
            } else {
                Text(
                    text = stringResource(R.string.shizuku_exact_alarm_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) {
                    Text(stringResource(R.string.shizuku_open_settings))
                }
            }
        }
    }
}
