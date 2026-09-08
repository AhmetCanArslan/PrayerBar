@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.carrier.CarrierTemplate
import com.arslan.prayerbar.prayer.TemplateToken
import java.util.Locale

/**
 * The template editor every text surface shares: a field, the ready-made templates, and a chip per
 * token that appends it. Presets are matched against the current value, so picking one shows as
 * selected until the text is edited by hand.
 */
@Composable
fun TemplateField(
    value: String,
    onValueChange: (String) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
    Label(stringResource(R.string.format_presets))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CarrierTemplate.presetsFor(locale).forEach { preset ->
            FilterChip(
                selected = value == preset,
                onClick = { onValueChange(preset) },
                label = { Text(preset) },
            )
        }
    }
    Label(stringResource(R.string.format_tokens))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TemplateToken.entries.forEach { token ->
            val spelling = token.spelling(locale)
            AssistChip(
                onClick = { onValueChange(value + spelling) },
                label = { Text(spelling) },
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
