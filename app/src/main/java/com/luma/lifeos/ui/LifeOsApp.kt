package com.luma.lifeos.ui

import android.net.Uri
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.ui.review.*
import com.luma.lifeos.ui.timeline.TimelineRoute
import com.luma.lifeos.ui.timeline.EventDetailRoute
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.ui.settings.SettingsRoute
import com.luma.lifeos.ui.settings.SettingsSectionPage
import com.luma.lifeos.ui.settings.IntegrationsSettingsRoute
import com.luma.lifeos.ui.usage.UsageForegroundRefresh
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.luma.lifeos.domain.repository.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.ui.people.PeopleRoute
import com.luma.lifeos.ui.people.PersonDetailRoute
import com.luma.lifeos.ui.people.PersonEditorRoute
import com.luma.lifeos.domain.model.EventType
import java.util.UUID
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.places.PlacesRoute
import com.luma.lifeos.ui.places.PlaceDetailRoute
import com.luma.lifeos.ui.places.PlaceEditorRoute
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    @param:StringRes val heading: Int,
    @param:StringRes val body: Int,
    @param:DrawableRes val icon: Int,
) {
    TODAY("today", R.string.today, R.string.today_heading, R.string.today_body, R.drawable.ic_nav_today),
    TIMELINE("timeline", R.string.timeline, R.string.timeline_heading, R.string.timeline_body, R.drawable.ic_nav_timeline),
    PEOPLE("people", R.string.people, R.string.people, R.string.people_intro, R.drawable.ic_nav_people),
    REVIEW("review", R.string.review, R.string.review_heading, R.string.review_body, R.drawable.ic_nav_review),
    SETTINGS("settings", R.string.settings, R.string.settings_heading, R.string.settings_body, R.drawable.ic_nav_settings),
}

@Composable
fun LifeOsApp(repository: LifeEventRepository, moods: MoodRepository, journals: JournalRepository, memories: MemoryRepository, people: PersonRepository, places: PlaceRepository, photos: PhotoRepository, timeline: TimelineRepository, usage: UsageIntegration? = null, summaries: com.luma.lifeos.domain.repository.DailySummaryRepository? = null, health: com.luma.lifeos.domain.health.HealthIntegration? = null, search: com.luma.lifeos.domain.repository.SearchRepository? = null, reviews: com.luma.lifeos.domain.repository.WeeklyReviewRepository? = null, backup: com.luma.lifeos.domain.backup.BackupService? = null, reset: com.luma.lifeos.ui.privacy.AppDataReset? = null) {
    if (usage != null) UsageForegroundRefresh(usage)
    if (health != null) com.luma.lifeos.ui.health.HealthForegroundRefresh(health)
    val photoScope = rememberCoroutineScope()
    fun cleanPhotos() { photoScope.launch {
        try { photos.cleanup() } catch (failure: Exception) { if (failure is CancellationException) throw failure }
    } }
    LaunchedEffect(photos) {
        try { photos.cleanup() } catch (failure: Exception) { if (failure is CancellationException) throw failure }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val primaryScreen = Destination.entries.any { it.route == backStackEntry?.destination?.route }
    fun editEvent(record: LifeEventRecord) {
        if (!record.canEditContent) return
        navController.navigate(when (record.event.type) {
            EventType.MOOD -> "mood?eventId=${record.event.id}"
            EventType.JOURNAL -> "journal?eventId=${record.event.id}"
            else -> "memory?eventId=${record.event.id}"
        })
    }
    fun openEvent(record: LifeEventRecord) {
        if (record.canEditContent && record.journal != null) navController.navigate("journal_detail/${record.event.id}")
        else if (record.canEditContent) editEvent(record)
        else navController.navigate("event_detail/${record.event.id}")
    }
    val currentDay by rememberCurrentDay()
    // Null follows the current day; a chosen date survives tab switches and recreation.
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var capturing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            if (primaryScreen) Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End) {
                    ExtendedFloatingActionButton(onClick = { capturing = true }, modifier = Modifier.testTag("quick_capture")) {
                        Text(stringResource(R.string.quick_capture))
                    }
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
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
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
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
                    if (destination == Destination.TIMELINE) {
                        TimelineRoute(timeline, selectedDate?.let(LocalDate::parse) ?: currentDay.date, currentDay.zone,
                            onSelectDate = { selectedDate = it.toString() }, onToday = { selectedDate = null },
                            onEvent = ::openEvent, onMemory = { navController.navigate("memory?eventId=$it") },
                            onSearch = search?.let { { navController.navigate("search") } })
                    } else if (destination == Destination.TODAY) {
                        TodayRoute(repository,
                            date = selectedDate?.let(LocalDate::parse) ?: currentDay.date,
                            zone = currentDay.zone,
                            onSelectDate = { selectedDate = it.toString() },
                            onToday = { selectedDate = null },
                            onMoodClick = { navController.navigate("mood?eventId=$it") },
                            onJournalClick = { navController.navigate("journal_detail/$it") },
                            onMemoryClick = { navController.navigate("memory?eventId=$it") },
                            heading = destination.label, screenTag = "screen_${destination.route}",
                            onEventClick = { navController.navigate("event_detail/$it") }, summaries = summaries)
                    } else if (destination == Destination.PEOPLE) {
                        PeopleRoute(people, onCreate = { navController.navigate("person") },
                            onPerson = { navController.navigate("person_detail/$it") })
                    } else if (destination == Destination.REVIEW) {
                        if (reviews != null) WeeklyReviewRoute(reviews, currentDay.date, currentDay.zone,
                            onSources = { dates, metric ->
                                val activity = metric.activity
                                navController.navigate("review_sources/${dates.start}/${dates.through}/${metric.kind.name}" +
                                    "?id=${metric.id ?: ""}&activity=${Uri.encode(activity?.type.orEmpty())}" +
                                    "&unit=${Uri.encode(activity?.unit.orEmpty())}&source=${activity?.source?.name.orEmpty()}")
                            }, onEvent = ::openEvent,
                            onPerson = { navController.navigate("person_detail/$it") },
                            onPlace = { navController.navigate("place_detail/$it") },
                            onUsage = { navController.navigate("review_usage/$it") },
                            onMemory = { navController.navigate("memory?eventId=${it.event.id}") })
                        else MemoryReviewRoute(memories, currentDay.date, currentDay.zone,
                            onMemory = { navController.navigate("memory?eventId=$it") },
                            onJournal = { navController.navigate("journal_detail/$it") })
                    } else if (destination == Destination.SETTINGS) {
                        SettingsRoute(backupAvailable = backup != null, privacyAvailable = reset != null,
                            integrationsAvailable = usage != null || health != null,
                            onLanguage = { navController.navigate("settings_language") },
                            onBackup = { navController.navigate("settings_backup") },
                            onPrivacy = { navController.navigate("privacy") },
                            onIntegrations = { navController.navigate("settings_integrations") })
                    } else {
                        FoundationScreen(destination)
                    }
                }
            }
            composable("review_sources/{start}/{through}/{kind}?id={id}&activity={activity}&unit={unit}&source={source}",
                arguments = listOf("start", "through", "kind", "id", "activity", "unit", "source").map { name ->
                    navArgument(name) { type = NavType.StringType; if (name in setOf("id", "activity", "unit", "source")) defaultValue = "" }
                }) { entry ->
                reviews?.let { reviewRepository ->
                    val args = requireNotNull(entry.arguments)
                    val kind = ReviewMetricKind.valueOf(requireNotNull(args.getString("kind")))
                    val metric = ReviewMetric(kind, args.getString("id")?.takeIf { it.isNotEmpty() }?.let(UUID::fromString),
                        if (kind == ReviewMetricKind.ACTIVITY) ReviewActivityKey(requireNotNull(args.getString("activity")),
                            requireNotNull(args.getString("unit")), com.luma.lifeos.domain.model.EventSource.valueOf(requireNotNull(args.getString("source")))) else null)
                    ReviewSourcesRoute(reviewRepository,
                        ReviewDates(LocalDate.parse(args.getString("start")), LocalDate.parse(args.getString("through"))),
                        currentDay.zone, metric, onBack = { navController.popBackStack() }, onEvent = ::openEvent,
                        onMemory = { navController.navigate("memory?eventId=${it.event.id}") })
                }
            }
            composable("review_usage/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
                usage?.let { ReviewUsageRoute(it, LocalDate.parse(entry.arguments?.getString("date")), currentDay.zone,
                    onBack = { navController.popBackStack() }) }
            }
            composable("search") {
                search?.let { com.luma.lifeos.ui.search.SearchRoute(it, currentDay.zone,
                    onBack = { navController.popBackStack() }, onEvent = ::openEvent) }
            }
            composable("settings_language") {
                SettingsSectionPage(R.string.settings_language_title, onBack = { navController.popBackStack() },
                    screenTag = "screen_settings_language") { com.luma.lifeos.language.LanguageSettingsCard() }
            }
            if (backup != null) composable("settings_backup") {
                SettingsSectionPage(R.string.settings_backup_title, onBack = { navController.popBackStack() },
                    screenTag = "screen_settings_backup") { com.luma.lifeos.ui.backup.BackupSettingsCard(backup) }
            }
            if (reset != null) composable("privacy") {
                com.luma.lifeos.ui.privacy.PrivacySettingsRoute(reset, onBack = { navController.popBackStack() })
            }
            if (usage != null || health != null) composable("settings_integrations") {
                IntegrationsSettingsRoute(usage, health, currentDay.zone, onBack = { navController.popBackStack() })
            }
            composable("places") {
                PlacesRoute(places, onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate("place") },
                    onPlace = { navController.navigate("place_detail/$it") })
            }
            composable("place?placeId={placeId}", arguments = listOf(navArgument("placeId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                PlaceEditorRoute(places, entry.arguments?.getString("placeId")?.let(UUID::fromString),
                    onCancel = { navController.popBackStack() }, onFinished = { deleted ->
                        navController.popBackStack()
                        if (deleted && navController.currentDestination?.route == "place_detail/{placeId}") navController.popBackStack()
                    })
            }
            composable("place_detail/{placeId}", arguments = listOf(navArgument("placeId") { type = NavType.StringType })) { entry ->
                val id = UUID.fromString(requireNotNull(entry.arguments?.getString("placeId")))
                PlaceDetailRoute(places, id, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("place?placeId=$id") })
            }
            composable("person?personId={personId}", arguments = listOf(navArgument("personId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                PersonEditorRoute(people, entry.arguments?.getString("personId")?.let(UUID::fromString),
                    onCancel = { navController.popBackStack() }, onFinished = { deleted ->
                        navController.popBackStack()
                        if (deleted && navController.currentDestination?.route == "person_detail/{personId}") navController.popBackStack()
                    })
            }
            composable("person_detail/{personId}", arguments = listOf(navArgument("personId") { type = NavType.StringType })) { entry ->
                val id = UUID.fromString(requireNotNull(entry.arguments?.getString("personId")))
                PersonDetailRoute(people, id, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("person?personId=$id") }, onEvent = ::openEvent)

            }
            composable("event_detail/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                val id = EventId.parse(requireNotNull(entry.arguments?.getString("eventId")))
                EventDetailRoute(timeline, photos, id, currentDay.zone, onBack = { navController.popBackStack() },
                    onEdit = ::editEvent, onMemory = { navController.navigate("memory?eventId=$id") })
            }
            composable("memory?eventId={eventId}&initialTime={initialTime}", arguments = listOf(
                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("initialTime") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                MemoryEditorRoute(memories, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        cleanPhotos()
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                    }, people = people, onCreatePerson = { navController.navigate("person") },
                        initialTimeMillis = entry.arguments?.getString("initialTime")?.toLongOrNull(),
                        places = places, onCreatePlace = { navController.navigate("place") }, photos = photos)
            }
            composable("journal_detail/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                val id = EventId.parse(requireNotNull(entry.arguments?.getString("eventId")))
                JournalDetailRoute(journals, id, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("journal?eventId=$id") },
                    onMemory = { navController.navigate("memory?eventId=$id") }, photos = photos)
            }
            composable("journal?eventId={eventId}&initialTime={initialTime}", arguments = listOf(
                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("initialTime") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                JournalEditorRoute(journals, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        cleanPhotos()
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                        if (date == null && navController.currentDestination?.route == "journal_detail/{eventId}") {
                            navController.popBackStack()
                        }
                    }, people = people, onCreatePerson = { navController.navigate("person") },
                        initialTimeMillis = entry.arguments?.getString("initialTime")?.toLongOrNull(),
                        places = places, onCreatePlace = { navController.navigate("place") }, photos = photos)
            }
            composable("mood?eventId={eventId}&initialTime={initialTime}", arguments = listOf(
                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("initialTime") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                MoodEditorRoute(moods, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        cleanPhotos()
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                    }, people = people, onCreatePerson = { navController.navigate("person") },
                        initialTimeMillis = entry.arguments?.getString("initialTime")?.toLongOrNull(),
                        places = places, onCreatePlace = { navController.navigate("place") })
            }
        }
    }
    val browsedDate = selectedDate?.let(LocalDate::parse)?.takeIf { it != currentDay.date }
    fun openCapture(route: String, date: LocalDate?) {
        capturing = false
        val target = date?.let { captureInitialTimeMillis(it, currentDay.zone) }
        navController.navigate(if (target == null) route else "$route?initialTime=$target")
    }
    if (capturing) QuickCaptureSheet(browsedDate = browsedDate, onDismiss = { capturing = false }, onMood = { date ->
        openCapture("mood", date)
    }, onJournal = { date ->
        openCapture("journal", date)
    }, onMemory = { date ->
        openCapture("memory", date)
    })
}

private fun captureInitialTimeMillis(date: LocalDate, zone: ZoneId): Long {
    val now = Instant.now()
    return date.atTime(now.atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli()
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
