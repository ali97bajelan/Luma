package com.luma.lifeos.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.luma.lifeos.domain.repository.LifeEventRepository
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.ui.memory.MemoryEditorRoute
import com.luma.lifeos.ui.memory.MemoryReviewRoute
import com.luma.lifeos.domain.repository.JournalRepository
import com.luma.lifeos.ui.journal.JournalEditorRoute
import com.luma.lifeos.ui.journal.JournalDetailRoute
import com.luma.lifeos.domain.repository.MoodRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.ui.mood.MoodEditorRoute
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.luma.lifeos.ui.today.TodayRoute
import com.luma.lifeos.ui.today.rememberCurrentDay
import java.time.LocalDate

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
fun LifeOsApp(repository: LifeEventRepository, moods: MoodRepository, journals: JournalRepository, memories: MemoryRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val eventScreen = backStackEntry?.destination?.route in setOf("mood?eventId={eventId}",
        "journal?eventId={eventId}", "journal_detail/{eventId}", "memory?eventId={eventId}")
    val currentDay by rememberCurrentDay()
    // Null follows the current day; a chosen date survives tab switches and recreation.
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var capturing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            if (!eventScreen) ExtendedFloatingActionButton(onClick = { capturing = true }, modifier = Modifier.testTag("quick_capture")) {
                Text(stringResource(R.string.quick_capture))
            }
        },
        bottomBar = {
            if (!eventScreen) NavigationBar {
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
                composable(destination.route) {
                    if (destination == Destination.TODAY || destination == Destination.TIMELINE) {
                        TodayRoute(repository,
                            date = selectedDate?.let(LocalDate::parse) ?: currentDay.date,
                            zone = currentDay.zone,
                            onSelectDate = { selectedDate = it.toString() },
                            onToday = { selectedDate = null },
                            onMoodClick = { navController.navigate("mood?eventId=$it") },
                            onJournalClick = { navController.navigate("journal_detail/$it") },
                            onMemoryClick = { navController.navigate("memory?eventId=$it") },
                            heading = destination.label, screenTag = "screen_${destination.route}")
                    } else if (destination == Destination.REVIEW) {
                        MemoryReviewRoute(memories, currentDay.date, currentDay.zone,
                            onMemory = { navController.navigate("memory?eventId=$it") },
                            onJournal = { navController.navigate("journal_detail/$it") })
                    } else {
                        FoundationScreen(destination)
                    }
                }
            }
            composable("memory?eventId={eventId}", arguments = listOf(navArgument("eventId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                MemoryEditorRoute(memories, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                    })
            }
            composable("journal_detail/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                val id = EventId.parse(requireNotNull(entry.arguments?.getString("eventId")))
                JournalDetailRoute(journals, id, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("journal?eventId=$id") },
                    onMemory = { navController.navigate("memory?eventId=$id") })
            }
            composable("journal?eventId={eventId}", arguments = listOf(navArgument("eventId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                JournalEditorRoute(journals, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                        if (date == null && navController.currentDestination?.route == "journal_detail/{eventId}") {
                            navController.popBackStack()
                        }
                    })
            }
            composable("mood?eventId={eventId}", arguments = listOf(navArgument("eventId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                MoodEditorRoute(moods, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                    })
            }
        }
    }
    if (capturing) QuickCaptureSheet(onDismiss = { capturing = false }, onMood = {
        capturing = false
        navController.navigate("mood")
    }, onJournal = {
        capturing = false
        navController.navigate("journal")
    }, onMemory = {
        capturing = false
        navController.navigate("memory")
    })
}

@Composable
private fun FoundationScreen(destination: Destination) {
    Column(
        modifier = Modifier.fillMaxSize()
            .testTag("screen_${destination.route}")
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 100.dp),
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
