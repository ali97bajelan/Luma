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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
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
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.calendar.CalendarRepository
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.widget.WidgetStore
import com.luma.lifeos.ui.habits.HabitsRoute
import com.luma.lifeos.ui.calendar.CalendarRoute
import com.luma.lifeos.ui.habits.HabitEditorRoute
import com.luma.lifeos.ui.habits.HabitDetailRoute
import com.luma.lifeos.ui.navigation.AppRoutes
import com.luma.lifeos.ui.navigation.PrimaryDestination
import com.luma.lifeos.ui.navigation.PrimaryNavigationBar
import com.luma.lifeos.ui.navigation.CatalogEditorResult
import com.luma.lifeos.ui.navigation.CatalogEntity
import com.luma.lifeos.ui.navigation.beginCatalogRequest
import com.luma.lifeos.ui.navigation.cancelCatalogRequest
import com.luma.lifeos.ui.navigation.pendingCatalogRequest
import com.luma.lifeos.ui.navigation.recordCatalogEditorResult
import com.luma.lifeos.ui.navigation.EntryEditorResult
import com.luma.lifeos.ui.navigation.consumeEntryEditorResult
import com.luma.lifeos.ui.navigation.entryEditorResultIdFlow
import com.luma.lifeos.ui.navigation.recordEntryEditorResult
import com.luma.lifeos.ui.more.MoreRoute
import com.luma.lifeos.ui.common.acknowledgeSavedEntry
import com.luma.lifeos.ui.todo.TODO_ACTION_RESULT_KEY
import com.luma.lifeos.ui.todo.TodoActionFeedbackHost
import com.luma.lifeos.ui.todo.TodoEditorRoute
import com.luma.lifeos.ui.todo.TodoListRoute
import com.luma.lifeos.ui.todo.encodeActionResult
import com.luma.lifeos.ui.backup.BACKUP_DEPARTURE_FEEDBACK_KEY
import com.luma.lifeos.ui.backup.BackupDepartureFeedbackHost
import com.luma.lifeos.ui.backup.BackupDepartureSession
import com.luma.lifeos.ui.backup.BackupSettingsCard
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.ui.mood.MoodEditorRoute
import com.luma.lifeos.ui.mood.FileMoodDraftStore
import com.luma.lifeos.ui.mood.MoodDraftStore
import com.luma.lifeos.widget.CaptureKind
import com.luma.lifeos.widget.WidgetDestination
import com.luma.lifeos.widget.WidgetLaunchRequest
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.luma.lifeos.ui.today.TodayRoute
import com.luma.lifeos.ui.today.rememberCurrentDay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityRefresh
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityViewModel
import com.luma.lifeos.domain.habit.HabitReminderRepository
import com.luma.lifeos.reminders.ReminderOpenRequest
import com.luma.lifeos.reminders.ReminderPreferences
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import com.luma.lifeos.ui.settings.ReminderSettingsRoute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

private const val HABITS_SELECTED_DATE_KEY = "habits_selected_date"
private const val TODO_EDITOR_REQUEST_DELETE_KEY = "todo_editor_request_delete"

private fun NavBackStackEntry.matchesEntryDetailFor(id: EventId): Boolean {
    val isDetailRoute = destination.route == "event_detail/{eventId}" || destination.route == "journal_detail/{eventId}"
    return isDetailRoute && arguments?.getString("eventId") == id.toString()
}

@Composable
fun LifeOsApp(
    repository: LifeEventRepository,
    moods: MoodRepository,
    journals: JournalRepository,
    memories: MemoryRepository,
    people: PersonRepository,
    places: PlaceRepository,
    photos: PhotoRepository,
    timeline: TimelineRepository,
    usage: UsageIntegration? = null,
    summaries: com.luma.lifeos.domain.repository.DailySummaryRepository? = null,
    health: com.luma.lifeos.domain.health.HealthIntegration? = null,
    search: com.luma.lifeos.domain.repository.SearchRepository? = null,
    reviews: com.luma.lifeos.domain.repository.WeeklyReviewRepository? = null,
    backup: com.luma.lifeos.domain.backup.BackupService? = null,
    reset: com.luma.lifeos.ui.privacy.AppDataReset? = null,
    habits: HabitRepository,
    todos: TodoRepository? = null,
    widgetLaunch: WidgetLaunchRequest? = null,
    onWidgetLaunchConsumed: (UUID) -> Unit = {},
    widgetStore: WidgetStore? = null,
    moodDraftStore: MoodDraftStore? = null,
    calendar: CalendarRepository? = null,
    habitReminders: HabitReminderRepository? = null,
    reminderPreferences: ReminderPreferences? = null,
    reminderLaunch: ReminderOpenRequest? = null,
    onReminderLaunchConsumed: () -> Unit = {},
) {
    if (usage != null) UsageForegroundRefresh(usage)
    if (health != null) com.luma.lifeos.ui.health.HealthForegroundRefresh(health)
    val integrationFactory = androidx.compose.runtime.remember(usage, health) {
        viewModelFactory { initializer { IntegrationAvailabilityViewModel(usage, health) } }
    }
    val integrationViewModel: IntegrationAvailabilityViewModel = viewModel(factory = integrationFactory)
    val integrationAvailability by integrationViewModel.state.collectAsStateWithLifecycle()
    IntegrationAvailabilityRefresh(integrationViewModel)
    val photoScope = rememberCoroutineScope()
    fun cleanPhotos() { photoScope.launch {
        try { photos.cleanup() } catch (failure: Exception) { if (failure is CancellationException) throw failure }
    } }
    LaunchedEffect(photos) {
        try { photos.cleanup() } catch (failure: Exception) { if (failure is CancellationException) throw failure }
    }
    val applicationContext = LocalContext.current.applicationContext
    val resolvedMoodDraftStore = remember(applicationContext, moodDraftStore) {
        moodDraftStore ?: FileMoodDraftStore(applicationContext)
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val entrySavedMessage = stringResource(R.string.entry_saved_feedback)
    val primaryScreen = PrimaryDestination.entries.any { it.route == backStackEntry?.destination?.route }
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
        else navController.navigate("event_detail/${record.event.id}")
    }
    val currentDay by rememberCurrentDay()
    // Null follows the current day; a chosen date survives tab switches and recreation.
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(backStackEntry, entrySavedMessage) {
        val editorEntry = backStackEntry ?: return@LaunchedEffect
        editorEntry.savedStateHandle.entryEditorResultIdFlow().collect { resultId ->
            if (resultId == null) return@collect
            val result = editorEntry.savedStateHandle.consumeEntryEditorResult() ?: return@collect
            val caller = navController.previousBackStackEntry
            val removeMatchingDetail = result is EntryEditorResult.Deleted && caller?.matchesEntryDetailFor(result.id) == true
            val removedDetail = removeMatchingDetail && caller != null &&
                navController.popBackStack(caller.destination.id, inclusive = true)
            if (!removedDetail) navController.popBackStack()
            if (result is EntryEditorResult.Saved) {
                photoScope.launch {
                    acknowledgeSavedEntry(result, snackbarHostState, entrySavedMessage)
                }
            }
            if (result !is EntryEditorResult.Deleted) {
                photoScope.launch {
                    val occurredAt = try {
                        repository.get(result.id)?.occurredAt
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        null
                    }
                    occurredAt?.let {
                        selectedDate = it.atZone(currentDay.zone).toLocalDate()
                            .takeUnless { day -> day == currentDay.date }?.toString()
                    }
                }
            }
        }
    }
    var capturing by rememberSaveable { mutableStateOf(false) }
    var widgetQuickCapture by rememberSaveable { mutableStateOf(false) }
    var calendarCaptureDueDate by rememberSaveable { mutableStateOf<String?>(null) }
    var todoRestoreGeneration by rememberSaveable { mutableStateOf(0) }
    var lastCaptureRestoreGeneration by rememberSaveable { mutableStateOf(todoRestoreGeneration) }
    var captureFabHeight by remember { mutableStateOf(0.dp) }
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(todoRestoreGeneration) {
        if (lastCaptureRestoreGeneration != todoRestoreGeneration) {
            lastCaptureRestoreGeneration = todoRestoreGeneration
            capturing = false
            widgetQuickCapture = false
            calendarCaptureDueDate = null
        }
    }
    val showCaptureFab = primaryScreen &&
        (currentRoute == PrimaryDestination.TIMELINE.route || currentRoute == PrimaryDestination.REVIEW.route)
    val captureBottomClearance = if (showCaptureFab && captureFabHeight > 0.dp) captureFabHeight + 16.dp else 0.dp
    fun openCapture(route: String, date: LocalDate?) {
        capturing = false
        widgetQuickCapture = false
        calendarCaptureDueDate = null
        val target = date?.let { captureInitialTimeMillis(it, currentDay.zone) }
        navController.navigate(if (target == null) route else "$route?initialTime=$target")
    }
    fun openTodoEditor(taskId: UUID? = null, dueDate: LocalDate? = null, requestDelete: Boolean = false) {
        navController.navigate("task_editor?taskId=${taskId?.toString().orEmpty()}&dueDate=${dueDate?.toString().orEmpty()}")
        if (requestDelete) {
            navController.currentBackStackEntry?.savedStateHandle?.set(TODO_EDITOR_REQUEST_DELETE_KEY, true)
        }
    }
    fun openHabitsDate(date: LocalDate?) {
        navController.navigate(AppRoutes.HABITS)
        navController.currentBackStackEntry?.takeIf { it.destination.route == AppRoutes.HABITS }?.let { entry ->
            entry.savedStateHandle[HABITS_SELECTED_DATE_KEY] = (date ?: currentDay.date).toString()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (primaryScreen) {
                PrimaryNavigationBar(
                    selectedRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (showCaptureFab) {
                val density = LocalDensity.current
                ExtendedFloatingActionButton(
                    onClick = { capturing = true },
                    shape = RoundedCornerShape(percent = 50),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.widthIn(max = 240.dp).onGloballyPositioned { coordinates ->
                        val measuredHeight = with(density) { coordinates.size.height.toDp() }
                        if (captureFabHeight != measuredHeight) captureFabHeight = measuredHeight
                    }.testTag("quick_capture"),
                ) {
                    Text(stringResource(R.string.quick_capture), maxLines = 2)
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = PrimaryDestination.TODAY.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            PrimaryDestination.entries.forEach { destination ->
                composable(destination.route) { destinationEntry ->
                    when (destination) {
                        PrimaryDestination.CALENDAR -> {
                            if (calendar != null && todos != null) {
                                CalendarRoute(
                                    repository = calendar,
                                    todos = todos,
                                    today = currentDay.date,
                                    zone = currentDay.zone,
                                    restoreGeneration = todoRestoreGeneration,
                                    onTask = { id -> openTodoEditor(taskId = id) },
                                    onDeleteTask = { id -> openTodoEditor(taskId = id, requestDelete = true) },
                                    onAddTask = { date -> openTodoEditor(dueDate = date) },
                                    onSchedule = { date ->
                                        calendarCaptureDueDate = date.toString()
                                        widgetQuickCapture = false
                                        capturing = true
                                    },
                                    onMemory = { id -> navController.navigate("memory?eventId=$id") },
                                    onAddMemory = { date -> openCapture("memory", date) },
                                    onAllTasks = { navController.navigate(AppRoutes.TASKS) },
                                    onTaskActionResult = { result ->
                                        destinationEntry.savedStateHandle.set(
                                            TODO_ACTION_RESULT_KEY,
                                            result.encodeActionResult(),
                                        )
                                    },
                                )
                            } else {
                                com.luma.lifeos.ui.common.AppPageScaffold(
                                    title = stringResource(R.string.calendar_heading),
                                    navigation = com.luma.lifeos.ui.common.PageNavigation.NONE,
                                ) { pagePadding ->
                                    Text(
                                        stringResource(R.string.calendar_unavailable),
                                        modifier = Modifier.padding(pagePadding),
                                    )
                                }
                            }
                        }
                        PrimaryDestination.TIMELINE -> TimelineRoute(
                            timeline,
                            selectedDate?.let(LocalDate::parse) ?: currentDay.date,
                            currentDay.zone,
                            onSelectDate = { selectedDate = it.toString() },
                            onToday = { selectedDate = null },
                            onEvent = ::openEvent,
                            onMemory = { navController.navigate("memory?eventId=$it") },
                            onSearch = search?.let { { navController.navigate("search") } },
                            onHabit = { id, habitDate, logId ->
                                navController.navigate("habit_detail/$id/$habitDate" + (logId?.let { "?logId=$it" } ?: ""))
                            },
                            captureBottomClearance = captureBottomClearance,
                        )
                        PrimaryDestination.TODAY -> TodayRoute(
                            repository,
                            date = selectedDate?.let(LocalDate::parse) ?: currentDay.date,
                            zone = currentDay.zone,
                            onSelectDate = { selectedDate = it.toString() },
                            onToday = { selectedDate = null },
                            onMoodClick = { navController.navigate("mood?eventId=$it") },
                            onJournalClick = { navController.navigate("journal_detail/$it") },
                            onMemoryClick = { navController.navigate("memory?eventId=$it") },
                            heading = destination.label,
                            screenTag = "screen_${destination.route}",
                            onEventClick = { navController.navigate("event_detail/$it") },
                            summaries = summaries,
                            integrationAvailability = integrationAvailability,
                            onIntegrations = { navController.navigate("settings_integrations") },
                            habits = habits,
                            habitsDate = selectedDate?.let(LocalDate::parse),
                            deviceDate = currentDay.date,
                            onHabits = { openHabitsDate(selectedDate?.let(LocalDate::parse) ?: currentDay.date) },
                            onCreateHabit = { navController.navigate("habit_editor") },
                            onHabit = { id, habitDate -> navController.navigate("habit_detail/$id/$habitDate") },
                            onHabitOpenDate = ::openHabitsDate,
                            onCapture = { type, date -> openCapture(when (type) {
                                EventType.MOOD -> "mood"
                                EventType.JOURNAL -> "journal"
                                EventType.MEMORY -> "memory"
                                else -> error("Unsupported capture type: $type")
                            }, date) },
                            todos = todos,
                            restoreGeneration = todoRestoreGeneration,
                            onTask = { id -> openTodoEditor(taskId = id) },
                            onAllTasks = { navController.navigate(AppRoutes.TASKS) },
                            onAddTask = { dueDate -> openTodoEditor(dueDate = dueDate) },
                            onTaskActionResult = { result ->
                                destinationEntry.savedStateHandle.set(
                                    TODO_ACTION_RESULT_KEY,
                                    result.encodeActionResult(),
                                )
                            },
                            snackbarHostState = snackbarHostState,
                        )
                        PrimaryDestination.REVIEW -> {
                            if (reviews != null) WeeklyReviewRoute(reviews, currentDay.date, currentDay.zone,
                                integrationAvailability = integrationAvailability,
                                onIntegrations = { navController.navigate("settings_integrations") },
                                onCheckIn = { navController.navigate("mood") },
                                onSources = { dates, metric ->
                                    val activity = metric.activity
                                    navController.navigate("review_sources/${dates.start}/${dates.through}/${metric.kind.name}" +
                                        "?id=${metric.id ?: ""}&activity=${Uri.encode(activity?.type.orEmpty())}" +
                                        "&unit=${Uri.encode(activity?.unit.orEmpty())}&source=${activity?.source?.name.orEmpty()}")
                                },
                                onEvent = ::openEvent,
                                onPerson = { navController.navigate("person_detail/$it") },
                                onPlace = { navController.navigate("place_detail/$it") },
                                onUsage = { navController.navigate("review_usage/$it") },
                                onMemory = { navController.navigate("memory?eventId=${it.event.id}") },
                                habits = habits,
                                onHabit = { id, habitDate -> navController.navigate("habit_detail/$id/$habitDate") },
                                captureBottomClearance = captureBottomClearance,
                            ) else MemoryReviewRoute(memories, currentDay.date, currentDay.zone,
                                onMemory = { navController.navigate("memory?eventId=$it") },
                                onJournal = { navController.navigate("journal_detail/$it") },
                                captureBottomClearance = captureBottomClearance)
                        }
                        PrimaryDestination.MORE -> MoreRoute(
                            onPeoplePlaces = { navController.navigate(AppRoutes.PEOPLE) },
                            onSettings = { navController.navigate(AppRoutes.SETTINGS) },
                            onTasks = if (todos != null) ({ navController.navigate(AppRoutes.TASKS) }) else null,
                            onHabits = { openHabitsDate(currentDay.date) },
                        )
                    }
                }
            }
            composable(AppRoutes.HABITS) { entry ->
                val legacyDate = remember(selectedDate, currentDay.date) {
                    selectedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: currentDay.date
                }
                val savedDate by entry.savedStateHandle
                    .getStateFlow(HABITS_SELECTED_DATE_KEY, legacyDate.toString())
                    .collectAsStateWithLifecycle()
                val habitsDate = remember(savedDate, currentDay.date) {
                    runCatching { LocalDate.parse(savedDate) }.getOrDefault(currentDay.date)
                }
                LaunchedEffect(entry, savedDate) {
                    if (entry.savedStateHandle.get<String>(HABITS_SELECTED_DATE_KEY) == null) {
                        entry.savedStateHandle[HABITS_SELECTED_DATE_KEY] = savedDate
                    }
                }
                HabitsRoute(
                    repository = habits,
                    date = habitsDate,
                    onSelectDate = { entry.savedStateHandle[HABITS_SELECTED_DATE_KEY] = it.toString() },
                    onToday = { entry.savedStateHandle[HABITS_SELECTED_DATE_KEY] = currentDay.date.toString() },
                    onCreate = { navController.navigate("habit_editor") },
                    onHabit = { id, habitDate -> navController.navigate("habit_detail/$id/$habitDate") },
                    deviceDate = currentDay.date,
                    deviceZone = currentDay.zone,
                    onBack = { navController.popBackStack() },
                    snackbarHostState = snackbarHostState,
                )
            }
            if (todos != null) {
                composable(AppRoutes.TASKS) {
                    TodoListRoute(
                        repository = todos,
                        today = currentDay.date,
                        restoreGeneration = todoRestoreGeneration,
                        snackbarHostState = snackbarHostState,
                        onBack = { navController.popBackStack() },
                        onAddTask = { openTodoEditor() },
                        onOpenTask = { taskId -> openTodoEditor(taskId) },
                    )
                }
                composable(
                    AppRoutes.TASK_EDITOR,
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("dueDate") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val taskId = entry.arguments?.getString("taskId")?.takeIf(String::isNotEmpty)?.let(UUID::fromString)
                    val dueDate = entry.arguments?.getString("dueDate")?.takeIf(String::isNotEmpty)?.let(LocalDate::parse)
                    val requestDelete by entry.savedStateHandle
                        .getStateFlow(TODO_EDITOR_REQUEST_DELETE_KEY, false)
                        .collectAsStateWithLifecycle()
                    TodoEditorRoute(
                        repository = todos,
                        taskId = taskId,
                        initialDueDate = dueDate,
                        onClose = { result ->
                            if (navController.currentDestination?.route == AppRoutes.TASK_EDITOR) {
                                if (result != null) {
                                    navController.previousBackStackEntry?.savedStateHandle?.set(
                                        TODO_ACTION_RESULT_KEY,
                                        result.encodeActionResult(),
                                    )
                                }
                                navController.popBackStack()
                            }
                        },
                        restoreGeneration = todoRestoreGeneration,
                        requestDelete = requestDelete,
                    )
                }
            }
            composable(AppRoutes.PEOPLE) {
                PeopleRoute(
                    people,
                    places,
                    onCreatePerson = { navController.navigate("person") },
                    onPerson = { navController.navigate("person_detail/$it") },
                    onCreatePlace = { navController.navigate("place") },
                    onPlace = { navController.navigate("place_detail/$it") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.SETTINGS) {
                SettingsRoute(
                    backupAvailable = backup != null,
                    privacyAvailable = reset != null,
                    integrationsAvailable = usage != null || health != null,
                    onLanguage = { navController.navigate("settings_language") },
                    onBackup = { navController.navigate("settings_backup") },
                    onPrivacy = { navController.navigate("privacy") },
                    onIntegrations = { navController.navigate("settings_integrations") },
                    onBack = { navController.popBackStack() },
                    widgetsAvailable = widgetStore != null,
                    onWidgets = { navController.navigate("settings_widgets") },
                    remindersAvailable = reminderPreferences != null,
                    onReminders = { navController.navigate("settings_reminders") },
                )
            }
            if (reminderPreferences != null) composable("settings_reminders") {
                ReminderSettingsRoute(reminderPreferences, onBack = { navController.popBackStack() })
            }
            if (widgetStore != null) composable("settings_widgets") {
                com.luma.lifeos.ui.widgets.WidgetSettingsRoute(
                    widgetStore = widgetStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("review_sources/{start}/{through}/{kind}?id={id}&activity={activity}&unit={unit}&source={source}",
                arguments = listOf("start", "through", "kind", "id", "activity", "unit", "source").map { name ->
                    navArgument(name) { type = NavType.StringType; if (name in setOf("id", "activity", "unit", "source")) defaultValue = "" }
                }) { entry ->
                val args = requireNotNull(entry.arguments)
                val kind = ReviewMetricKind.valueOf(requireNotNull(args.getString("kind")))
                val metric = ReviewMetric(kind, args.getString("id")?.takeIf { it.isNotEmpty() }?.let(UUID::fromString),
                    if (kind == ReviewMetricKind.ACTIVITY) ReviewActivityKey(requireNotNull(args.getString("activity")),
                        requireNotNull(args.getString("unit")), com.luma.lifeos.domain.model.EventSource.valueOf(requireNotNull(args.getString("source")))) else null)
                ReviewSourcesDestination(reviews,
                    ReviewDates(LocalDate.parse(args.getString("start")), LocalDate.parse(args.getString("through"))),
                    currentDay.zone, metric, onBack = { navController.popBackStack() }, onEvent = ::openEvent,
                    onMemory = { navController.navigate("memory?eventId=${it.event.id}") })
            }
            composable("review_usage/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
                ReviewUsageDestination(usage, LocalDate.parse(entry.arguments?.getString("date")), currentDay.zone,
                    onBack = { navController.popBackStack() })
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
                BackupSettingsCard(
                    service = backup,
                    onRestored = {
                        todoRestoreGeneration += 1
                        enqueueReminderReconciliation(applicationContext)
                        snackbarHostState.currentSnackbarData?.dismiss()
                    },
                    onBack = { departure ->
                        if (navController.currentDestination?.route == "settings_backup") {
                            val settingsEntry = navController.previousBackStackEntry
                                ?.takeIf { it.destination.route == AppRoutes.SETTINGS }
                            departure?.let {
                                settingsEntry?.savedStateHandle?.set(
                                    BACKUP_DEPARTURE_FEEDBACK_KEY,
                                    "${BackupDepartureSession.id}:${it.encode()}",
                                )
                            }
                            navController.popBackStack()
                        }
                    },
                )
            }
            if (reset != null) composable("privacy") {
                com.luma.lifeos.ui.privacy.PrivacySettingsRoute(reset, onBack = { navController.popBackStack() })
            }
            if (usage != null || health != null) composable("settings_integrations") {
                IntegrationsSettingsRoute(usage, health, currentDay.zone,
                    onBack = { navController.popBackStack() }, availability = integrationAvailability,
                    onRefreshAccess = integrationViewModel::refreshAccess,
                )
            }
            composable("places") {
                PlacesRoute(places, onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate("place") },
                    onPlace = { navController.navigate("place_detail/$it") })
            }
            composable("habit_editor?habitId={habitId}", arguments = listOf(navArgument("habitId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                HabitEditorRoute(habits, entry.arguments?.getString("habitId")?.let(UUID::fromString),
                    onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() },
                    deviceZone = currentDay.zone)
            }
            composable("habit_detail/{habitId}/{date}?logId={logId}", arguments = listOf(
                navArgument("habitId") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType },
                navArgument("logId") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                val habitId = UUID.fromString(requireNotNull(entry.arguments?.getString("habitId")))
                val habitDate = LocalDate.parse(requireNotNull(entry.arguments?.getString("date")))
                val logId = entry.arguments?.getString("logId")?.let(UUID::fromString)
                HabitDetailRoute(habits, habitId, habitDate, logId, reminderRepository = habitReminders,
                    snackbarHostState = snackbarHostState,
                    onEdit = { id -> navController.navigate("habit_editor?habitId=$id") },
                    onBack = { navController.popBackStack() }, onOpenDate = ::openHabitsDate, deviceZone = currentDay.zone)
            }
            composable("place?placeId={placeId}", arguments = listOf(navArgument("placeId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                val requestId = navController.previousBackStackEntry?.savedStateHandle
                    ?.pendingCatalogRequest(CatalogEntity.PLACE)
                PlaceEditorRoute(places, entry.arguments?.getString("placeId")?.let(UUID::fromString),
                    onCancel = {
                        navController.previousBackStackEntry?.savedStateHandle?.cancelCatalogRequest(CatalogEntity.PLACE, requestId)
                        navController.popBackStack()
                    }, onFinished = { result ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.recordCatalogEditorResult(CatalogEntity.PLACE, requestId, result)
                        navController.popBackStack()
                        if (result is CatalogEditorResult.Deleted && navController.currentDestination?.route == "place_detail/{placeId}") {
                            navController.popBackStack()
                        }
                    }, requestId = requestId)
            }
            composable("place_detail/{placeId}", arguments = listOf(navArgument("placeId") { type = NavType.StringType })) { entry ->
                val id = UUID.fromString(requireNotNull(entry.arguments?.getString("placeId")))
                PlaceDetailRoute(places, id, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("place?placeId=$id") }, onEvent = ::openEvent)
            }
            composable("person?personId={personId}", arguments = listOf(navArgument("personId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })) { entry ->
                val requestId = navController.previousBackStackEntry?.savedStateHandle
                    ?.pendingCatalogRequest(CatalogEntity.PERSON)
                PersonEditorRoute(people, entry.arguments?.getString("personId")?.let(UUID::fromString),
                    onCancel = {
                        navController.previousBackStackEntry?.savedStateHandle?.cancelCatalogRequest(CatalogEntity.PERSON, requestId)
                        navController.popBackStack()
                    }, onFinished = { result ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.recordCatalogEditorResult(CatalogEntity.PERSON, requestId, result)
                        navController.popBackStack()
                        if (result is CatalogEditorResult.Deleted && navController.currentDestination?.route == "person_detail/{personId}") {
                            navController.popBackStack()
                        }
                    }, requestId = requestId)
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
                    onFinished = { result ->
                        cleanPhotos()
                        entry.savedStateHandle.recordEntryEditorResult(result)
                    }, people = people, onCreatePerson = {
                        entry.savedStateHandle.beginCatalogRequest(CatalogEntity.PERSON)
                        navController.navigate("person")
                    },
                        initialTimeMillis = entry.arguments?.getString("initialTime")?.toLongOrNull(),
                        places = places, onCreatePlace = {
                            entry.savedStateHandle.beginCatalogRequest(CatalogEntity.PLACE)
                            navController.navigate("place")
                        }, photos = photos, catalogResultHandle = entry.savedStateHandle)
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
                    onFinished = { result ->
                        cleanPhotos()
                        entry.savedStateHandle.recordEntryEditorResult(result)
                    }, people = people, onCreatePerson = {
                        entry.savedStateHandle.beginCatalogRequest(CatalogEntity.PERSON)
                        navController.navigate("person")
                    },
                        initialTimeMillis = entry.arguments?.getString("initialTime")?.toLongOrNull(),
                        places = places, onCreatePlace = {
                            entry.savedStateHandle.beginCatalogRequest(CatalogEntity.PLACE)
                            navController.navigate("place")
                        }, photos = photos, catalogResultHandle = entry.savedStateHandle)
            }
            composable("mood?eventId={eventId}&initialTime={initialTime}", arguments = listOf(
                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("initialTime") { type = NavType.StringType; nullable = true; defaultValue = null },
            )) { entry ->
                MoodEditorRoute(moods, entry.arguments?.getString("eventId")?.let(EventId::parse),
                    onCancel = { navController.popBackStack() },
                    onFinished = { result ->
                        cleanPhotos()
                        entry.savedStateHandle.recordEntryEditorResult(result)
                    }, people = people, onCreatePerson = {
                        entry.savedStateHandle.beginCatalogRequest(CatalogEntity.PERSON)
                        navController.navigate("person")
                    },
                        initialTimeMillis = entry.arguments?.getString("initialTime")?.toLongOrNull(),
                        places = places, onCreatePlace = {
                            entry.savedStateHandle.beginCatalogRequest(CatalogEntity.PLACE)
                            navController.navigate("place")
                        }, draftStore = resolvedMoodDraftStore, catalogResultHandle = entry.savedStateHandle)
            }
        }
        LaunchedEffect(widgetLaunch?.id, navController) {
            val request = widgetLaunch ?: return@LaunchedEffect
            // The cold-start request waits here until NavHost has installed its graph.
            navController.currentBackStackEntryFlow.first()
            val top = navController.currentBackStackEntry
            if (request.destination != WidgetDestination.QuickCapture) {
                capturing = false
                widgetQuickCapture = false
                calendarCaptureDueDate = null
            }
            when (val destination = request.destination) {
                WidgetDestination.Today -> {
                    if (top?.destination?.route != PrimaryDestination.TODAY.route) {
                        navController.navigate(PrimaryDestination.TODAY.route)
                    }
                }
                WidgetDestination.QuickCapture -> {
                    widgetQuickCapture = true
                    capturing = true
                    calendarCaptureDueDate = null
                }
                is WidgetDestination.Capture -> {
                    val route = when (destination.kind) {
                        CaptureKind.MOOD -> "mood"
                        CaptureKind.MEMORY -> "memory"
                        CaptureKind.JOURNAL -> "journal"
                    }
                    val pattern = when (destination.kind) {
                        CaptureKind.MOOD -> "mood?eventId={eventId}&initialTime={initialTime}"
                        CaptureKind.MEMORY -> "memory?eventId={eventId}&initialTime={initialTime}"
                        CaptureKind.JOURNAL -> "journal?eventId={eventId}&initialTime={initialTime}"
                    }
                    val isSameNewEditor = top?.destination?.route == pattern &&
                        top.arguments?.getString("eventId") == null
                    if (!isSameNewEditor) navController.navigate(route)
                }
                WidgetDestination.Habits -> openHabitsDate(null)
                is WidgetDestination.HabitDetail -> navController.navigate(
                    "habit_detail/${destination.id}/${destination.date}",
                )
                WidgetDestination.Tasks -> if (todos != null) navController.navigate(AppRoutes.TASKS)
                is WidgetDestination.Task -> if (todos != null) openTodoEditor(taskId = destination.id)
                WidgetDestination.NewHabit -> navController.navigate("habit_editor")
                WidgetDestination.NewTask -> if (todos != null) {
                    openTodoEditor(dueDate = LocalDate.now(ZoneId.systemDefault()))
                }
            }
            onWidgetLaunchConsumed(request.id)
        }
        LaunchedEffect(reminderLaunch?.token, navController) {
            val request = reminderLaunch ?: return@LaunchedEffect
            navController.currentBackStackEntryFlow.first()
            when (request.kind) {
                "habit" -> navController.navigate("habit_detail/${request.id}/${request.date}")
                "task" -> if (todos != null) openTodoEditor(request.id)
            }
            onReminderLaunchConsumed()
        }
        TodoActionFeedbackHost(backStackEntry, todos, snackbarHostState, todoRestoreGeneration)
        BackupDepartureFeedbackHost(backStackEntry, snackbarHostState, BackupDepartureSession.id)
    }
    val captureRecordDate = if (calendarCaptureDueDate != null) {
        LocalDate.parse(calendarCaptureDueDate).takeIf { it != currentDay.date }
    } else {
        selectedDate?.let(LocalDate::parse)?.takeIf { it != currentDay.date }
    }
    val browsedDate = captureRecordDate.takeUnless { widgetQuickCapture }
    if (capturing) {
        QuickCaptureSheet(
            browsedDate = browsedDate,
            onDismiss = {
                capturing = false
                widgetQuickCapture = false
                calendarCaptureDueDate = null
            },
            onMood = { date -> openCapture("mood", date) },
            onJournal = { date -> openCapture("journal", date) },
            onMemory = { date -> openCapture("memory", date) },
            onTask = if (todos != null && calendarCaptureDueDate == null) ({
                capturing = false
                widgetQuickCapture = false
                calendarCaptureDueDate = null
                openTodoEditor()
            }) else null,
            onHabit = { date ->
                capturing = false
                widgetQuickCapture = false
                calendarCaptureDueDate = null
                openHabitsDate(date)
            },
            taskDueDate = calendarCaptureDueDate?.let(LocalDate::parse),
            onScheduledTask = if (todos != null && calendarCaptureDueDate != null) ({ date ->
                capturing = false
                widgetQuickCapture = false
                calendarCaptureDueDate = null
                openTodoEditor(dueDate = date)
            }) else null,
        )
    }
}

private fun captureInitialTimeMillis(date: LocalDate, zone: ZoneId): Long {
    val now = Instant.now()
    return date.atTime(now.atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli()
}
