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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.FocusNavigationGuard
import com.luma.lifeos.focus.FocusPendingDestination
import com.luma.lifeos.focus.FocusPreferences
import com.luma.lifeos.focus.FocusRuntime
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.focus.FocusRuntimeState
import com.luma.lifeos.focus.FocusScreenState
import com.luma.lifeos.focus.FocusViewModel
import com.luma.lifeos.ui.focus.FocusRoute
import com.luma.lifeos.ui.focus.FocusHistoryRoute
import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockBrowseContext
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.lock.AppLockCoordinator
import com.luma.lifeos.lock.EventEditorKind
import com.luma.lifeos.lock.LockPendingDestination
import com.luma.lifeos.ui.privacy.AppLockGate

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
    focusRuntime: FocusRuntime? = null,
    focusPreferences: FocusPreferences? = null,
    allowedFocusApps: AllowedFocusApps? = null,
    focusRepository: FocusRepository? = null,
    appLockCoordinator: AppLockCoordinator? = null,
    protectedContentGateway: com.luma.lifeos.lock.ProtectedContentGateway? = null,
    onLockUnlockRequested: () -> Unit = {},
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
    val focusViewModel = if (focusRuntime == null) null else {
        val focusFactory = remember(focusRuntime) {
            viewModelFactory { initializer { FocusViewModel(focusRuntime) } }
        }
        viewModel<FocusViewModel>(factory = focusFactory)
    }
    val focusStateHolder = focusViewModel?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(FocusScreenState(FocusRuntimeState(initialized = true, loading = false))) }
    val focusState by focusStateHolder
    var focusNavigationGuard by rememberSaveable { mutableStateOf(FocusNavigationGuard()) }
    fun isFocusGuarded(): Boolean = focusNavigationGuard.isGuarded(focusState.runtime)
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
    var lockHelpRequested by rememberSaveable { mutableStateOf(false) }
    var todoRestoreGeneration by rememberSaveable { mutableStateOf(0) }
    var lastCaptureRestoreGeneration by rememberSaveable { mutableStateOf(todoRestoreGeneration) }
    var captureFabHeight by remember { mutableStateOf(0.dp) }
    val currentRoute = backStackEntry?.destination?.route
    val lockEventId = backStackEntry?.arguments?.getString("eventId")
    val eventScopedRoute = currentRoute in setOf(
        "event_detail/{eventId}", "journal_detail/{eventId}",
        "memory?eventId={eventId}&initialTime={initialTime}",
        "journal?eventId={eventId}&initialTime={initialTime}",
        "mood?eventId={eventId}&initialTime={initialTime}",
        "entry_share/{eventId}/{kind}",
    )
    val classifiedEventSections = remember(currentRoute, lockEventId) {
        mutableStateOf<Set<LockSection>?>(null)
    }
    val lockSnapshotHolder = appLockCoordinator?.snapshot?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(com.luma.lifeos.domain.lock.LockSnapshot()) }
    val lockSnapshot by lockSnapshotHolder
    val protectedSummaryReadable = appLockCoordinator == null || lockSnapshot.decide(
        LockRequest(
            operation = LockOperation.READ,
            requiredSections = LockSection.entries.toSet(),
            contentClassification = LockContentClassification.CLASSIFIED,
        ),
    ) == AccessDecision.Allow
    val protectedFocusReadable = appLockCoordinator == null || lockSnapshot.decide(
        LockRequest(
            operation = LockOperation.READ,
            requiredSections = setOf(LockSection.FOCUS),
            contentClassification = LockContentClassification.CLASSIFIED,
        ),
    ) == AccessDecision.Allow
    val protectedTasksReadable = appLockCoordinator == null || lockSnapshot.decide(
        LockRequest(LockOperation.READ, setOf(LockSection.TASKS), LockContentClassification.CLASSIFIED),
    ) == AccessDecision.Allow
    val protectedHabitsReadable = appLockCoordinator == null || lockSnapshot.decide(
        LockRequest(LockOperation.READ, setOf(LockSection.HABITS), LockContentClassification.CLASSIFIED),
    ) == AccessDecision.Allow
    LaunchedEffect(protectedContentGateway, eventScopedRoute, lockEventId, lockSnapshot.policy?.enabled) {
        classifiedEventSections.value = if (lockSnapshot.policy?.enabled == true && eventScopedRoute && lockEventId != null) {
            try {
                protectedContentGateway?.classifyRecord(EventId.parse(lockEventId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else null
    }
    val previousBackStackEntry = navController.previousBackStackEntry
    val lockContext = remember(currentRoute, backStackEntry, previousBackStackEntry,
        lockSnapshot.activeBrowseContext, classifiedEventSections.value, capturing, widgetQuickCapture) {
        appLockBrowseContext(
            currentRoute,
            backStackEntry,
            previousBackStackEntry?.destination?.route,
            lockSnapshot.activeBrowseContext,
            classifiedEventSections.value,
            capturing || widgetQuickCapture,
        )
    }
    val lockRequest = remember(lockContext) {
        LockRequest(
            operation = appLockOperation(currentRoute),
            requiredSections = lockContext.requiredSections,
            contentClassification = if (lockContext.requiredSections.isEmpty()) {
                LockContentClassification.KNOWN_UNPROTECTED
            } else LockContentClassification.CLASSIFIED,
        )
    }
    LaunchedEffect(appLockCoordinator, lockContext) {
        appLockCoordinator?.onNavigationChanged(lockContext)
    }
    val safeLockHelp = currentRoute == AppRoutes.APP_LOCK_HELP || lockHelpRequested
    val appLockGateVisible = appLockCoordinator != null && !safeLockHelp && when {
        lockSnapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY -> true
        lockSnapshot.policy?.enabled != true -> false
        lockSnapshot.activeBrowseContext != lockContext -> true
        else -> lockSnapshot.decide(lockRequest) != AccessDecision.Allow
    }
    LaunchedEffect(appLockCoordinator, widgetLaunch?.id, reminderLaunch?.token, lockSnapshot.policy?.generation) {
        val coordinator = appLockCoordinator ?: return@LaunchedEffect
        if (lockSnapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
            lockSnapshot.policy?.enabled != true
        ) return@LaunchedEffect
        val pending = widgetLaunch?.let { lockRequestForWidget(it.destination) }
            ?: reminderLaunch?.let(::lockRequestForReminder)
            ?: return@LaunchedEffect
        val decision = coordinator.requestBrowse(pending.first, pending.second)
        if (decision is AccessDecision.NeedUnlock) {
            onLockUnlockRequested()
            widgetLaunch?.let { onWidgetLaunchConsumed(it.id) }
            if (widgetLaunch == null) onReminderLaunchConsumed()
        }
    }
    val focusGateVisible = focusRuntime != null && currentRoute != AppRoutes.FOCUS && (
        (focusState.runtime.loading && !focusState.runtime.emergencyExitConfirmed) ||
            (focusState.runtime.loadFailed && !focusState.runtime.emergencyExitConfirmed) ||
            focusState.runtime.recoveryRequired || focusState.runtime.navigationGuarded
        )
    LaunchedEffect(focusRuntime, focusState.runtime.navigationGuarded, focusState.runtime.initialized) {
        if (focusRuntime != null && focusState.runtime.navigationGuarded) {
            navController.currentBackStackEntryFlow.first()
            if (navController.currentDestination?.route != AppRoutes.FOCUS) {
                navController.navigate(AppRoutes.FOCUS) { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(focusState.runtime.emergencyExitConfirmed, currentRoute) {
        if (focusState.runtime.emergencyExitConfirmed && currentRoute == AppRoutes.FOCUS) {
            if (!navController.popBackStack()) navController.navigate(PrimaryDestination.TODAY.route) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(todoRestoreGeneration) {
        if (lastCaptureRestoreGeneration != todoRestoreGeneration) {
            lastCaptureRestoreGeneration = todoRestoreGeneration
            capturing = false
            widgetQuickCapture = false
            calendarCaptureDueDate = null
        }
    }
    val showCaptureFab = primaryScreen && !isFocusGuarded() &&
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
    fun openFocusHistory(start: LocalDate, through: LocalDate, zone: ZoneId) {
        navController.navigate(
            "${AppRoutes.FOCUS_HISTORY.substringBefore('?')}?start=$start&through=$through&zone=${Uri.encode(zone.id)}",
        )
    }
    fun openHabitsDate(date: LocalDate?) {
        navController.navigate(AppRoutes.HABITS)
        navController.currentBackStackEntry?.takeIf { it.destination.route == AppRoutes.HABITS }?.let { entry ->
            entry.savedStateHandle[HABITS_SELECTED_DATE_KEY] = (date ?: currentDay.date).toString()
        }
    }
    fun routeWidgetDestination(destination: WidgetDestination) {
        val top = navController.currentBackStackEntry
        if (destination != WidgetDestination.QuickCapture) {
            capturing = false
            widgetQuickCapture = false
            calendarCaptureDueDate = null
        }
        when (destination) {
            WidgetDestination.Today -> if (top?.destination?.route != PrimaryDestination.TODAY.route) {
                navController.navigate(PrimaryDestination.TODAY.route)
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
                val isSameNewEditor = top?.destination?.route == pattern && top.arguments?.getString("eventId") == null
                if (!isSameNewEditor) navController.navigate(route)
            }
            WidgetDestination.Habits -> openHabitsDate(null)
            is WidgetDestination.HabitDetail -> navController.navigate("habit_detail/${destination.id}/${destination.date}")
            WidgetDestination.Tasks -> if (todos != null) navController.navigate(AppRoutes.TASKS)
            is WidgetDestination.Task -> if (todos != null) openTodoEditor(taskId = destination.id)
            WidgetDestination.NewHabit -> navController.navigate("habit_editor")
            WidgetDestination.NewTask -> if (todos != null) {
                openTodoEditor(dueDate = LocalDate.now(ZoneId.systemDefault()))
            }
        }
    }
    fun openPendingFocusDestination() {
        val (pending, nextGuard) = focusNavigationGuard.consumePending()
        focusNavigationGuard = nextGuard
        when (pending) {
            is FocusPendingDestination.Widget -> routeWidgetDestination(pending.destination)
            is FocusPendingDestination.Reminder -> when (pending.kind) {
                "habit" -> navController.navigate("habit_detail/${pending.id}/${pending.date}")
                "task" -> if (todos != null) openTodoEditor(UUID.fromString(pending.id))
            }
            is FocusPendingDestination.Route -> navController.navigate(pending.route) {
                launchSingleTop = true
                restoreState = true
            }
            null -> Unit
        }
    }
    Box(Modifier.fillMaxSize()) {
    if (appLockGateVisible) {
        AppLockGate(
            canRequestUnlock = lockSnapshot.loadStatus == com.luma.lifeos.domain.lock.LockLoadStatus.READY,
            focusEmergencyAvailable = currentRoute == AppRoutes.FOCUS && focusViewModel != null,
            focusEmergencyExitReady = focusState.emergencyPromptVisible,
            onUnlock = {
                val existingRequest = appLockCoordinator?.pendingBrowseRequest()
                val decision = if (existingRequest != null) AccessDecision.NeedUnlock(
                    existingRequest.requiredSections,
                    lockSnapshot.policy?.coverage == com.luma.lifeos.domain.lock.LockCoverage.WHOLE_APP,
                ) else appLockCoordinator?.requestBrowse(lockRequest, lockPendingDestination(currentRoute, backStackEntry))
                if (decision is AccessDecision.NeedUnlock ||
                    (decision is AccessDecision.Deny && decision.reason == com.luma.lifeos.domain.lock.AccessDenial.FRESH_AUTHENTICATION_REQUIRED)
                ) onLockUnlockRequested()
            },
            onBackToSafeDestination = {
                appLockCoordinator?.cancelPendingNavigation()
                lockHelpRequested = true
            },
            onRequestFocusEmergencyExit = { focusViewModel?.requestEmergencyExit() },
            onConfirmFocusEmergencyExit = {
                focusViewModel?.confirmEmergencyExit()
                appLockCoordinator?.cancelPendingNavigation()
                lockHelpRequested = true
            },
            onCancelFocusEmergencyExit = { focusViewModel?.dismissEmergencyExit() },
        )
    } else if (lockHelpRequested) {
        SettingsSectionPage(
            title = R.string.app_lock_help_title,
            onBack = { lockHelpRequested = false },
        ) {
            Text(stringResource(R.string.app_lock_help_body))
        }
    } else {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (primaryScreen && !isFocusGuarded()) {
                PrimaryNavigationBar(
                    selectedRoute = currentRoute,
                    onNavigate = { route ->
                        if (isFocusGuarded()) {
                            focusNavigationGuard = focusNavigationGuard.intercept(
                                focusState.runtime,
                                FocusPendingDestination.Route(route),
                            )
                        } else {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
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
                            showProtectedSummary = protectedSummaryReadable,
                            showProtectedFocus = protectedFocusReadable,
                            integrationAvailability = integrationAvailability,
                            onIntegrations = { navController.navigate("settings_integrations") },
                            habits = habits.takeIf { protectedHabitsReadable },
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
                            todos = todos.takeIf { protectedTasksReadable },
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
                            onFocus = if (focusRuntime != null) ({
                                navController.navigate(AppRoutes.FOCUS) { launchSingleTop = true }
                            }) else null,
                            focusPaused = focusState.runtime.session?.state == com.luma.lifeos.domain.focus.FocusState.PAUSED,
                            focusRepository = focusRepository,
                            focusInProgressMillis = focusState.runtime.session?.let {
                                focusState.runtime.displayedMillis.takeIf { _ ->
                                    it.state in setOf(
                                        com.luma.lifeos.domain.focus.FocusState.RUNNING,
                                        com.luma.lifeos.domain.focus.FocusState.PAUSED,
                                    )
                                }
                            },
                            focusInProgressState = focusState.runtime.session?.state?.takeIf {
                                it in setOf(
                                    com.luma.lifeos.domain.focus.FocusState.RUNNING,
                                    com.luma.lifeos.domain.focus.FocusState.PAUSED,
                                )
                            },
                            onFocusHistory = ::openFocusHistory,
                        )
                        PrimaryDestination.REVIEW -> {
                            if (reviews != null) WeeklyReviewRoute(reviews, currentDay.date, currentDay.zone,
                                integrationAvailability = integrationAvailability,
                                onIntegrations = { navController.navigate("settings_integrations") },
                                onCheckIn = { navController.navigate("mood") },
                                focusRepository = focusRepository,
                                onFocusHistory = ::openFocusHistory,
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
                            onFocus = if (focusRuntime != null) ({
                                navController.navigate(AppRoutes.FOCUS) { launchSingleTop = true }
                            }) else null,
                            focusPaused = protectedFocusReadable &&
                                focusState.runtime.session?.state == com.luma.lifeos.domain.focus.FocusState.PAUSED,
                        )
                    }
                }
            }
            composable(AppRoutes.FOCUS) {
                if (focusViewModel != null) {
                    FocusRoute(
                        viewModel = focusViewModel,
                        preferences = focusPreferences,
                        allowedFocusApps = allowedFocusApps,
                        onOpenHistory = {
                            navController.navigate(AppRoutes.FOCUS_HISTORY.substringBefore('?'))
                        },
                        hasPendingDestination = focusNavigationGuard.pendingDestination != null,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(PrimaryDestination.TODAY.route) { launchSingleTop = true }
                            }
                        },
                        onOpenPendingDestination = ::openPendingFocusDestination,
                    )
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
                    focusAvailable = focusRuntime != null,
                    onFocus = { navController.navigate("settings_focus") },
                )
            }
            composable(AppRoutes.APP_LOCK_HELP) {
                com.luma.lifeos.ui.common.AppPageScaffold(
                    title = stringResource(R.string.app_lock_help_title),
                    navigation = com.luma.lifeos.ui.common.PageNavigation.NONE,
                ) { pagePadding ->
                    Text(stringResource(R.string.app_lock_help_body), modifier = Modifier.padding(pagePadding))
                }
            }
            if (focusRuntime != null) composable("settings_focus") {
                val settingsPreferences = remember(applicationContext, focusPreferences) {
                    focusPreferences ?: FocusPreferences(applicationContext)
                }
                val settingsAllowedApps = remember(applicationContext, allowedFocusApps) {
                    allowedFocusApps ?: AllowedFocusApps(applicationContext)
                }
                com.luma.lifeos.ui.focus.FocusSettingsRoute(
                    preferences = settingsPreferences,
                    allowedFocusApps = settingsAllowedApps,
                    isRunning = focusState.runtime.session?.state == com.luma.lifeos.domain.focus.FocusState.RUNNING,
                    onBack = { navController.popBackStack() },
                )
            }
            if (focusRepository != null) composable(
                AppRoutes.FOCUS_HISTORY,
                arguments = listOf("start", "through", "zone").map { name ->
                    navArgument(name) { type = NavType.StringType; defaultValue = "" }
                },
            ) { entry ->
                val args = requireNotNull(entry.arguments)
                val rangeStart = args.getString("start")?.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                val rangeThrough = args.getString("through")?.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                val historyZone = args.getString("zone")?.takeIf(String::isNotBlank)
                    ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: currentDay.zone
                val range = if (rangeStart != null && rangeThrough != null) {
                    TimeRange(
                        rangeStart.atStartOfDay(historyZone).toInstant(),
                        rangeThrough.plusDays(1).atStartOfDay(historyZone).toInstant(),
                    )
                } else null
                FocusHistoryRoute(
                    repository = focusRepository,
                    range = range,
                    zone = historyZone,
                    onBack = { navController.popBackStack() },
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
                    focusRuntime = focusRuntime,
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
            composable("entry_share/{eventId}/{kind}", arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("kind") { type = NavType.StringType },
            )) { entry ->
                val request = remember(entry) { runCatching {
                    com.luma.lifeos.sharing.EntryShareRequest(
                        EventId.parse(requireNotNull(entry.arguments?.getString("eventId"))),
                        com.luma.lifeos.sharing.EntryShareKind.valueOf(requireNotNull(entry.arguments?.getString("kind")))
                    )
                }.getOrNull() }
                if (request != null) com.luma.lifeos.sharing.EntryShareRoute(timeline, request, currentDay.zone) { navController.popBackStack() }
                else androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.luma.lifeos.R.string.share_unavailable))
            }
            composable("event_detail/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                val id = EventId.parse(requireNotNull(entry.arguments?.getString("eventId")))
                EventDetailRoute(timeline, photos, id, currentDay.zone, onBack = { navController.popBackStack() },
                    onEdit = ::editEvent, onMemory = { navController.navigate("memory?eventId=$id") },
                    onShare = { kind -> navController.navigate("entry_share/$id/${kind.name}") })
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
                        }, photos = photos, catalogResultHandle = entry.savedStateHandle,
                        onShare = entry.arguments?.getString("eventId")?.let { id -> { navController.navigate("entry_share/$id/MEMORY") } })
            }
            composable("journal_detail/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                val id = EventId.parse(requireNotNull(entry.arguments?.getString("eventId")))
                JournalDetailRoute(journals, id, onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("journal?eventId=$id") },
                    onMemory = { navController.navigate("memory?eventId=$id") }, photos = photos,
                    onShare = { navController.navigate("entry_share/$id/MEMORY") })
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
                        }, draftStore = resolvedMoodDraftStore, catalogResultHandle = entry.savedStateHandle,
                        onShare = entry.arguments?.getString("eventId")?.let { id -> { navController.navigate("entry_share/$id/MOOD") } })
            }
        }
        LaunchedEffect(widgetLaunch?.id, navController, focusState.runtime.loading,
            focusState.runtime.loadFailed, focusState.runtime.navigationGuarded) {
            val request = widgetLaunch ?: return@LaunchedEffect
            if (focusRuntime != null && (focusState.runtime.loading ||
                    (focusState.runtime.loadFailed && !focusState.runtime.emergencyExitConfirmed))) return@LaunchedEffect
            // The cold-start request waits here until NavHost has installed its graph.
            navController.currentBackStackEntryFlow.first()
            val pending = FocusPendingDestination.Widget(request.destination)
            val nextGuard = focusNavigationGuard.interceptIfGuarded(focusState.runtime, pending)
            if (nextGuard != null) {
                focusNavigationGuard = nextGuard
                onWidgetLaunchConsumed(request.id)
                return@LaunchedEffect
            }
            routeWidgetDestination(request.destination)
            onWidgetLaunchConsumed(request.id)
        }
        LaunchedEffect(reminderLaunch?.token, navController, focusState.runtime.loading,
            focusState.runtime.loadFailed, focusState.runtime.navigationGuarded) {
            val request = reminderLaunch ?: return@LaunchedEffect
            if (focusRuntime != null && (focusState.runtime.loading ||
                    (focusState.runtime.loadFailed && !focusState.runtime.emergencyExitConfirmed))) return@LaunchedEffect
            navController.currentBackStackEntryFlow.first()
            val nextGuard = focusNavigationGuard.interceptIfGuarded(
                focusState.runtime,
                FocusPendingDestination.Reminder(request.kind, request.id.toString(), request.date.toString()),
            )
            if (nextGuard != null) {
                focusNavigationGuard = nextGuard
                onReminderLaunchConsumed()
                return@LaunchedEffect
            }
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
    if (capturing && !focusGateVisible) {
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
    if (focusGateVisible && focusViewModel != null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            FocusRoute(
                viewModel = focusViewModel,
                preferences = focusPreferences,
                allowedFocusApps = allowedFocusApps,
                onOpenHistory = {
                    navController.navigate(AppRoutes.FOCUS_HISTORY.substringBefore('?'))
                },
                hasPendingDestination = focusNavigationGuard.pendingDestination != null,
                onBack = {},
                onOpenPendingDestination = ::openPendingFocusDestination,
            )
        }
    }
    }
    }
}

private fun appLockBrowseContext(
    route: String?,
    entry: NavBackStackEntry?,
    previousRoute: String?,
    previousContext: LockBrowseContext?,
    classifiedEventSections: Set<LockSection>?,
    restrictedCapture: Boolean,
): LockBrowseContext {
    val destination = route ?: AppRoutes.TODAY
    val eventId = entry?.arguments?.getString("eventId")
    val taskId = entry?.arguments?.getString("taskId")
    val habitId = entry?.arguments?.getString("habitId")
    val sections = when (destination) {
        AppRoutes.CALENDAR -> emptySet()
        AppRoutes.HABITS, "habit_editor?habitId={habitId}", "habit_detail/{habitId}/{date}?logId={logId}" ->
            setOf(LockSection.HABITS)
        AppRoutes.TASKS, AppRoutes.TASK_EDITOR -> setOf(LockSection.TASKS)
        AppRoutes.FOCUS, AppRoutes.FOCUS_HISTORY, "settings_focus" -> setOf(LockSection.FOCUS)
        AppRoutes.PEOPLE, "places" -> emptySet()
        "person?personId={personId}", "person_detail/{personId}" -> setOf(LockSection.PEOPLE)
        "place?placeId={placeId}" -> emptySet()
        "place_detail/{placeId}" -> emptySet()
        "event_detail/{eventId}", "journal_detail/{eventId}",
        "memory?eventId={eventId}&initialTime={initialTime}",
        "journal?eventId={eventId}&initialTime={initialTime}",
        "mood?eventId={eventId}&initialTime={initialTime}", "entry_share/{eventId}/{kind}" -> LockSection.entries.toSet()
        else -> if (destination.startsWith("review_sources") || destination.startsWith("review_usage") ||
            destination == "settings_backup" || destination == "privacy"
        ) if (destination.startsWith("review_usage")) setOf(LockSection.HEALTH_USAGE) else emptySet() else emptySet()
    }.let { routeSections ->
        when {
            destination in setOf(
                "event_detail/{eventId}", "journal_detail/{eventId}",
                "memory?eventId={eventId}&initialTime={initialTime}",
                "journal?eventId={eventId}&initialTime={initialTime}",
                "mood?eventId={eventId}&initialTime={initialTime}",
            ) -> classifiedEventSections ?: LockSection.entries.toSet()
            destination in setOf(AppRoutes.TODAY, AppRoutes.TIMELINE) -> emptySet()
            destination == AppRoutes.MORE -> emptySet()
            else -> routeSections
        }
    }
    val nestedContent = destination in setOf(
        "memory?eventId={eventId}&initialTime={initialTime}", "journal_detail/{eventId}",
        "journal?eventId={eventId}&initialTime={initialTime}", "event_detail/{eventId}",
        "mood?eventId={eventId}&initialTime={initialTime}", AppRoutes.TASK_EDITOR,
        "habit_editor?habitId={habitId}", "habit_detail/{habitId}/{date}?logId={logId}",
        "person?personId={personId}", "person_detail/{personId}",
        "place?placeId={placeId}", "place_detail/{placeId}", AppRoutes.FOCUS_HISTORY,
        "entry_share/{eventId}/{kind}",
    )
    val isReviewSubroute = destination.startsWith("review_sources") || destination.startsWith("review_usage")
    val inheritedTopLevel = when (previousRoute) {
        AppRoutes.TODAY, AppRoutes.TIMELINE, AppRoutes.CALENDAR, AppRoutes.REVIEW, AppRoutes.MORE,
        AppRoutes.PEOPLE -> previousRoute
        else -> if (previousRoute?.startsWith("review_sources") == true || previousRoute?.startsWith("review_usage") == true) {
            AppRoutes.REVIEW
        } else previousContext?.topLevelDestination
    }
    val topLevel = if ((nestedContent || isReviewSubroute) && inheritedTopLevel != null) inheritedTopLevel else when (destination) {
        "memory?eventId={eventId}&initialTime={initialTime}", "journal_detail/{eventId}",
        "journal?eventId={eventId}&initialTime={initialTime}", "event_detail/{eventId}",
        "mood?eventId={eventId}&initialTime={initialTime}", "entry_share/{eventId}/{kind}" -> "entries"
        "habit_editor?habitId={habitId}", "habit_detail/{habitId}/{date}?logId={logId}" -> AppRoutes.HABITS
        AppRoutes.TASK_EDITOR -> AppRoutes.TASKS
        AppRoutes.FOCUS_HISTORY -> AppRoutes.FOCUS
        "person?personId={personId}", "person_detail/{personId}" -> AppRoutes.PEOPLE
        else -> destination
    }
    val scope = when (topLevel) {
        "entries" -> "entry:${eventId ?: "new"}"
        AppRoutes.TASKS -> "task:${taskId ?: "list"}"
        AppRoutes.HABITS -> "habit:${habitId ?: "list"}"
        AppRoutes.PEOPLE -> "people:${entry?.arguments?.getString("personId") ?: "list"}"
        else -> topLevel
    }
    return LockBrowseContext(
        topLevelDestination = topLevel,
        authorizationScopeId = scope,
        requiredSections = sections,
        isSettings = destination.startsWith("settings") || destination == "privacy",
        isRestrictedCapture = restrictedCapture || (destination in setOf(
            "memory?eventId={eventId}&initialTime={initialTime}",
            "journal?eventId={eventId}&initialTime={initialTime}",
            "mood?eventId={eventId}&initialTime={initialTime}",
        ) && eventId == null),
    )
}

private fun lockPendingDestination(route: String?, entry: NavBackStackEntry?): LockPendingDestination? = when (route) {
    AppRoutes.TODAY, AppRoutes.TIMELINE, AppRoutes.CALENDAR, AppRoutes.REVIEW, AppRoutes.MORE ->
        LockPendingDestination.Primary(requireNotNull(route))
    "search" -> LockPendingDestination.Search
    AppRoutes.PEOPLE -> LockPendingDestination.People
    "person_detail/{personId}" -> entry?.arguments?.getString("personId")
        ?.let { LockPendingDestination.PersonDetail(it) }
    "person?personId={personId}" -> LockPendingDestination.People
    "place_detail/{placeId}" -> entry?.arguments?.getString("placeId")
        ?.let { LockPendingDestination.PlaceDetail(it) }
    "places", "place?placeId={placeId}" -> null
    AppRoutes.HABITS -> LockPendingDestination.Habits
    AppRoutes.TASKS -> LockPendingDestination.Tasks
    "event_detail/{eventId}", "journal_detail/{eventId}" ->
        entry?.arguments?.getString("eventId")?.let { LockPendingDestination.EventDetail(it) }
    "memory?eventId={eventId}&initialTime={initialTime}" -> LockPendingDestination.EventEditor(
        entry?.arguments?.getString("eventId"), EventEditorKind.MEMORY,
    )
    "journal?eventId={eventId}&initialTime={initialTime}" -> LockPendingDestination.EventEditor(
        entry?.arguments?.getString("eventId"), EventEditorKind.JOURNAL,
    )
    "mood?eventId={eventId}&initialTime={initialTime}" -> LockPendingDestination.EventEditor(
        entry?.arguments?.getString("eventId"), EventEditorKind.MOOD,
    )
    AppRoutes.TASK_EDITOR -> LockPendingDestination.TaskEditor(entry?.arguments?.getString("taskId"))
    "habit_detail/{habitId}/{date}?logId={logId}" -> {
        val args = entry?.arguments
        val habitId = args?.getString("habitId")
        val date = args?.getString("date")
        if (habitId != null && date != null) LockPendingDestination.HabitDetail(habitId, date) else null
    }
    AppRoutes.FOCUS_HISTORY -> LockPendingDestination.FocusHistory(requireNotNull(route))
    else -> null
}

private fun appLockOperation(route: String?): LockOperation = when (route) {
    "entry_share/{eventId}/{kind}" -> LockOperation.SHARE
    "settings_backup" -> LockOperation.BACKUP
    "privacy" -> LockOperation.RESET
    else -> LockOperation.READ
}

private fun lockRequestForWidget(
    destination: WidgetDestination,
): Pair<LockRequest, LockPendingDestination> {
    val pending = when (destination) {
        WidgetDestination.Today -> LockPendingDestination.Primary(AppRoutes.TODAY)
        WidgetDestination.QuickCapture -> LockPendingDestination.CaptureMenu
        is WidgetDestination.Capture -> LockPendingDestination.EventEditor(
            eventId = null,
            kind = when (destination.kind) {
                CaptureKind.MEMORY -> EventEditorKind.MEMORY
                CaptureKind.JOURNAL -> EventEditorKind.JOURNAL
                CaptureKind.MOOD -> EventEditorKind.MOOD
            },
        )
        WidgetDestination.Habits -> LockPendingDestination.Habits
        is WidgetDestination.HabitDetail -> LockPendingDestination.HabitDetail(
            destination.id.toString(), destination.date.toString(),
        )
        WidgetDestination.Tasks -> LockPendingDestination.Tasks
        is WidgetDestination.Task -> LockPendingDestination.TaskEditor(destination.id.toString())
        WidgetDestination.NewHabit -> LockPendingDestination.NewHabit
        WidgetDestination.NewTask -> LockPendingDestination.NewTask
    }
    val sections = when (destination) {
        WidgetDestination.Today -> emptySet()
        WidgetDestination.QuickCapture, is WidgetDestination.Capture -> LockSection.entries.toSet()
        WidgetDestination.Habits, is WidgetDestination.HabitDetail, WidgetDestination.NewHabit -> setOf(LockSection.HABITS)
        WidgetDestination.Tasks, is WidgetDestination.Task, WidgetDestination.NewTask -> setOf(LockSection.TASKS)
    }
    return lockBrowseRequest(sections) to pending
}

private fun lockRequestForReminder(request: ReminderOpenRequest): Pair<LockRequest, LockPendingDestination>? {
    val (section, destination) = when (request.kind) {
        "habit" -> LockSection.HABITS to LockPendingDestination.HabitDetail(
            request.id.toString(), request.date.toString(),
        )
        "task" -> LockSection.TASKS to LockPendingDestination.TaskEditor(request.id.toString())
        else -> return null
    }
    return lockBrowseRequest(setOf(section)) to destination
}

private fun lockBrowseRequest(sections: Set<LockSection>) = LockRequest(
    operation = LockOperation.READ,
    requiredSections = sections,
    contentClassification = if (sections.isEmpty()) LockContentClassification.KNOWN_UNPROTECTED
    else LockContentClassification.CLASSIFIED,
)

private fun captureInitialTimeMillis(date: LocalDate, zone: ZoneId): Long {
    val now = Instant.now()
    return date.atTime(now.atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli()
}
