package com.luma.lifeos.ui

import android.net.Uri
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.ui.review.*
import com.luma.lifeos.ui.timeline.TimelineRoute
import com.luma.lifeos.ui.timeline.EventDetailRoute
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.ui.usage.UsageSettingsRoute
import com.luma.lifeos.ui.usage.UsageForegroundRefresh
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
import androidx.compose.material3.TextButton
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
    PEOPLE("people", R.string.people, R.string.people, R.string.people_intro, "♧"),
    REVIEW("review", R.string.review, R.string.review_heading, R.string.review_body, "◇"),
    SETTINGS("settings", R.string.settings, R.string.settings_heading, R.string.settings_body, "⚙"),
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
    val eventScreen = backStackEntry?.destination?.route in setOf("mood?eventId={eventId}",
        "journal?eventId={eventId}", "journal_detail/{eventId}", "memory?eventId={eventId}",
        "person?personId={personId}", "person_detail/{personId}", "event_detail/{eventId}",
        "privacy", "places", "place?placeId={placeId}", "place_detail/{placeId}")
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
                    } else if (destination == Destination.SETTINGS && usage != null) {
                        UsageSettingsRoute(usage, selectedDate?.let(LocalDate::parse) ?: currentDay.date, currentDay.zone,
                            onSelectDate = { selectedDate = it.toString() }, onToday = { selectedDate = null },
                            onPlaces = { navController.navigate("places") }, health = health, backup = backup,
                            onPrivacy = reset?.let { { navController.navigate("privacy") } })
                    } else {
                        FoundationScreen(destination, onPlaces = { navController.navigate("places") }, backup = backup,
                            onPrivacy = reset?.let { { navController.navigate("privacy") } })
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
            composable("privacy") {
                reset?.let { com.luma.lifeos.ui.privacy.PrivacySettingsRoute(it, onBack = { navController.popBackStack() }) }
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
            composable("memory?eventId={eventId}", arguments = listOf(navArgument("eventId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                MemoryEditorRoute(memories, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        cleanPhotos()
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                    }, people = people, onCreatePerson = { navController.navigate("person") },
                        places = places, onCreatePlace = { navController.navigate("place") }, photos = photos)
            }
            composable("journal_detail/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                val id = EventId.parse(requireNotNull(entry.arguments?.getString("eventId")))
                JournalDetailRoute(journals, id, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("journal?eventId=$id") },
                    onMemory = { navController.navigate("memory?eventId=$id") }, photos = photos)
            }
            composable("journal?eventId={eventId}", arguments = listOf(navArgument("eventId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
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
                        places = places, onCreatePlace = { navController.navigate("place") }, photos = photos)
            }
            composable("mood?eventId={eventId}", arguments = listOf(navArgument("eventId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                MoodEditorRoute(moods, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { date ->
                        cleanPhotos()
                        if (date != null) selectedDate = date.takeUnless { it == currentDay.date }?.toString()
                        navController.popBackStack()
                    }, people = people, onCreatePerson = { navController.navigate("person") },
                        places = places, onCreatePlace = { navController.navigate("place") })
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
private fun FoundationScreen(destination: Destination, onPlaces: () -> Unit, backup: com.luma.lifeos.domain.backup.BackupService?, onPrivacy: (() -> Unit)?) {
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
        if (destination == Destination.SETTINGS && onPrivacy != null) TextButton(onClick = onPrivacy, modifier = Modifier.testTag("settings_privacy")) {
            Text(stringResource(R.string.privacy_title))
        }
        if (destination == Destination.SETTINGS) com.luma.lifeos.language.LanguageSettingsCard()
        if (destination == Destination.SETTINGS && backup != null) com.luma.lifeos.ui.backup.BackupSettingsCard(backup)
        if (destination == Destination.SETTINGS) TextButton(onClick = onPlaces, modifier = Modifier.testTag("settings_places")) {
            Text(stringResource(R.string.places))
        }
        Text(
            stringResource(R.string.local_first),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
