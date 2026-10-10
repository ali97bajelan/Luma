package com.luma.lifeos.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.luma.lifeos.R

private enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    @param:StringRes val heading: Int,
    @param:StringRes val body: Int,
    val symbol: String,
) {
    TODAY("today", R.string.today, R.string.today_heading, R.string.today_body, "◉"),
    TIMELINE("timeline", R.string.timeline, R.string.timeline_heading, R.string.timeline_body, "≡"),
    REVIEW("review", R.string.review, R.string.review_heading, R.string.review_body, "◇"),
    SETTINGS("settings", R.string.settings, R.string.settings_heading, R.string.settings_body, "⚙"),
}

@Composable
fun LifeOsApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${destination.route}"),
                        selected = backStackEntry?.destination?.route == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.symbol) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.TODAY.route,
            modifier = Modifier.padding(padding),
        ) {
            Destination.entries.forEach { destination ->
                composable(destination.route) { FoundationScreen(destination) }
            }
        }
    }
}

@Composable
private fun FoundationScreen(destination: Destination) {
    Column(
        modifier = Modifier.fillMaxSize()
            .testTag("screen_${destination.route}")
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(destination.heading), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(destination.body), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.local_first),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
