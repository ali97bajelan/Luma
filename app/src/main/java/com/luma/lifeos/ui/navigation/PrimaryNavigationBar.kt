package com.luma.lifeos.ui.navigation

import com.luma.lifeos.ui.theme.primaryFixed
import com.luma.lifeos.ui.theme.onPrimaryFixed

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.ui.common.LumaUi

@Composable
internal fun PrimaryNavigationBar(
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.testTag("primary_navigation"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        PrimaryDestination.entries.forEach { destination ->
            val fullLabel = stringResource(destination.label)
            NavigationBarItem(
                modifier = Modifier.testTag("nav_${destination.route}"),
                selected = selectedRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(painterResource(destination.icon), contentDescription = null,
                        modifier = Modifier.size(LumaUi.IconSize))
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryFixed,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryFixed,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                label = {
                    Text(
                        text = stringResource(destination.compactLabel),
                        modifier = Modifier
                            .testTag("nav_label_${destination.route}")
                            .semantics { contentDescription = fullLabel },
                        maxLines = 2,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
