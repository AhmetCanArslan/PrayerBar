package com.arslan.prayerbar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.ui.Route

@Composable
fun SettingsScreen(onOpen: (Route) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroup {
            SettingsRow(
                route = Route.Calculation,
                icon = Icons.Rounded.Calculate,
                title = stringResource(R.string.nav_calculation),
                supporting = stringResource(R.string.settings_calculation_hint),
                onOpen = onOpen,
            )
        }
        SettingsGroup {
            SettingsRow(
                route = Route.Format,
                icon = Icons.Rounded.EditNote,
                title = stringResource(R.string.nav_format),
                supporting = stringResource(R.string.settings_format_hint),
                onOpen = onOpen,
            )
            SettingsRow(
                route = Route.Tile,
                icon = Icons.Rounded.GridView,
                title = stringResource(R.string.nav_tile),
                supporting = stringResource(R.string.settings_tile_hint),
                onOpen = onOpen,
            )
            SettingsRow(
                route = Route.Widget,
                icon = Icons.Rounded.Widgets,
                title = stringResource(R.string.nav_widget),
                supporting = stringResource(R.string.settings_widget_hint),
                onOpen = onOpen,
            )
        }
        SettingsGroup {
            SettingsRow(
                route = Route.Permissions,
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.nav_permissions),
                supporting = stringResource(R.string.settings_permissions_hint),
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(
    route: Route,
    icon: ImageVector,
    title: String,
    supporting: String,
    onOpen: (Route) -> Unit,
) {
    ListItem(
        supportingContent = { Text(supporting) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },

        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .settingsContainer(route, MaterialTheme.shapes.extraLarge)
            .clickable { onOpen(route) },
    ) { Text(title) }
}
