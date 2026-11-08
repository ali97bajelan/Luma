package com.luma.lifeos.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.FocusPreferences
import com.luma.lifeos.focus.FocusViewModel
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusInvitationRepository
import com.luma.lifeos.reminders.FocusInvitationOpenRequest
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusMetadata
import com.luma.lifeos.domain.focus.FocusReflection
import com.luma.lifeos.ui.today.rememberCurrentDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
internal fun FocusRoute(
    viewModel: FocusViewModel,
    preferences: FocusPreferences?,
    allowedFocusApps: AllowedFocusApps?,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    hasPendingDestination: Boolean,
    onBack: () -> Unit,
    onOpenPendingDestination: () -> Unit,
    onOpenTaskPicker: (() -> Unit)? = null,
    focusRepository: FocusRepository? = null,
    focusProductRepository: FocusProductRepository? = null,
    onRequestProtectedFocusUnlock: () -> Unit = {},
    protectedFocusReadable: Boolean = true,
    protectedTasksReadable: Boolean = true,
    invitationRepository: FocusInvitationRepository? = null,
    invitationOpenRequest: FocusInvitationOpenRequest? = null,
    onInvitationOpenHandled: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val resolvedPreferences = remember(context, preferences) { preferences ?: FocusPreferences(context) }
    val resolvedAllowedFocusApps = remember(context, allowedFocusApps) {
        allowedFocusApps ?: AllowedFocusApps(context)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var handledInvitationToken by remember { mutableStateOf<String?>(null) }
    var showInvitationDraftConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(
        invitationOpenRequest?.token,
        state.runtime.session?.state,
        state.runtime.recoveryRequired,
        state.runtime.breakState,
        state.runtime.initialized,
        state.runtime.loading,
        state.runtime.loadFailed,
        state.setup,
        protectedFocusReadable,
        protectedTasksReadable,
    ) {
        val request = invitationOpenRequest ?: return@LaunchedEffect
        if (handledInvitationToken == request.token) return@LaunchedEffect
        if (!state.runtime.initialized || state.runtime.loading || state.runtime.loadFailed) return@LaunchedEffect
        val existingWorkIsOpen =
            state.runtime.session?.state == com.luma.lifeos.domain.focus.FocusState.RUNNING ||
                state.runtime.session?.state == com.luma.lifeos.domain.focus.FocusState.PAUSED ||
                state.runtime.recoveryRequired ||
                state.runtime.breakState != com.luma.lifeos.focus.FocusBreakState.Idle
        if (existingWorkIsOpen) {
            handledInvitationToken = request.token
            onInvitationOpenHandled()
            return@LaunchedEffect
        }
        if (!protectedFocusReadable || (state.setup.taskOrigin && !protectedTasksReadable)) return@LaunchedEffect
        val storedToken = withContext(Dispatchers.IO) {
            invitationRepository?.actionToken(request.slot)
        }
        if (storedToken != request.token) {
            handledInvitationToken = request.token
            onInvitationOpenHandled()
            return@LaunchedEffect
        }
        if (state.setup.hasInvitationDraft()) {
            showInvitationDraftConfirmation = true
        } else {
            viewModel.startAnother()
            handledInvitationToken = request.token
            onInvitationOpenHandled()
        }
    }
    val currentInsights = state.insights
    val presentationState = state.copy(
        runtime = state.runtime.copy(
            session = state.runtime.session?.let { session ->
                if (protectedFocusReadable && (!session.taskOrigin || protectedTasksReadable)) session
                else session.copy(metadata = FocusMetadata(), reflection = FocusReflection(), taskId = null)
            },
        ),
        setup = if (protectedFocusReadable && (!state.setup.taskOrigin || protectedTasksReadable)) {
            state.setup
        } else FocusSetupState(),
        recentWork = state.recentWork.takeIf { recent ->
            val query = recent.query
            protectedFocusReadable && query != null &&
                query.generation == state.runtime.databaseGeneration &&
                query.includeTaskOrigin == protectedTasksReadable &&
                (recent !is FocusRecentWorkState.Ready ||
                    !recent.session.taskOrigin || protectedTasksReadable)
        } ?: FocusRecentWorkState.Hidden,
        tagSuggestions = if (protectedFocusReadable && protectedTasksReadable) state.tagSuggestions else emptyList(),
        taskCompletion = if (protectedFocusReadable && protectedTasksReadable) state.taskCompletion
            else FocusTaskCompletionState(),
        insights = when {
            !protectedFocusReadable -> FocusInsightsState.Unselected
            !protectedTasksReadable && currentInsights is FocusInsightsState.Ready &&
                currentInsights.insights.requiresTaskAccess -> FocusInsightsState.NeedsUnlock(currentInsights.request)
            else -> currentInsights
        },
    )
    val currentDay by rememberCurrentDay()
    val selectedPackages by resolvedPreferences.allowedPackages.collectAsStateWithLifecycle(
        initialValue = resolvedPreferences.selectedPackages(),
    )
    var discoveredApps by remember { mutableStateOf(emptyList<AllowedFocusApps.App>()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resolvedAllowedFocusApps, refreshKey) {
        discoveredApps = try {
            withContext(Dispatchers.IO) { resolvedAllowedFocusApps.discover() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            emptyList()
        }
    }

    LaunchedEffect(currentDay) {
        viewModel.updateViewingContext(currentDay.date, currentDay.zone)
    }

    FocusScreen(
        state = presentationState,
        focusRepository = focusRepository,
        focusProductRepository = focusProductRepository,
        taskContentReadable = protectedTasksReadable,
        allowedApps = discoveredApps.filter { it.packageName in selectedPackages },
        hasPendingDestination = hasPendingDestination,
        onBack = onBack,
        onShowBackPrompt = viewModel::showBackPrompt,
        onDismissBackPrompt = viewModel::dismissBackPrompt,
        onStart = viewModel::start,
        onRepeatSession = viewModel::repeatSession,
        onStartAnother = viewModel::startAnother,
        onNameChange = viewModel::changeName,
        onNameBlur = viewModel::blurName,
        onTagChange = viewModel::changeTag,
        onTagBlur = viewModel::blurTag,
        onTagAreaExpandedChange = viewModel::setTagAreaExpanded,
        onTimerModeChange = viewModel::changeTimerMode,
        onTimedMinutesChange = viewModel::changeTimedMinutes,
        onTimedMinutesBlur = viewModel::blurTimedMinutes,
        onStartAttempted = viewModel::attemptStart,
        onStartBreak = viewModel::startBreak,
        onEndBreak = viewModel::endBreak,
        onPeriodChange = viewModel::changePeriod,
        onRetryInsights = viewModel::retryInsights,
        onRetryRecentWork = viewModel::retryRecentWork,
        onAction = { action -> viewModel.act(action) },
        onLeaveAfterAction = { action -> viewModel.act(action, onSuccess = onBack) },
        onResolveRecovery = viewModel::resolveRecovery,
        onRetry = viewModel::retry,
        onRetryInitialization = viewModel::retryInitialization,
        onEmergencyExit = viewModel::requestEmergencyExit,
        onDismissEmergencyExit = viewModel::dismissEmergencyExit,
        onConfirmEmergencyExit = viewModel::confirmEmergencyExit,
        onLaunchApp = viewModel::launchSelectedApp,
        onOpenPendingDestination = onOpenPendingDestination,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onOpenTaskPicker = onOpenTaskPicker,
        onClearTaskSelection = viewModel::clearTaskSelection,
        onRequestProtectedFocusUnlock = onRequestProtectedFocusUnlock,
        onTerminalSessionMissing = { sessionId, generation ->
            if (viewModel.dismissMissingTerminalSession(sessionId, generation)) onBack()
        },
        onTaskSessionChanged = viewModel::syncTaskCompletionSession,
        onMarkTaskComplete = viewModel::markTaskComplete,
        onRetryTaskCompletion = viewModel::retryTaskCompletion,
        onUnlinkTask = viewModel::unlinkTask,
        onRetryTaskUnlink = viewModel::retryTaskUnlink,
        showInvitationDraftConfirmation = showInvitationDraftConfirmation,
        onConfirmInvitationDraftReplacement = {
            viewModel.startAnother()
            showInvitationDraftConfirmation = false
            invitationOpenRequest?.token?.let { handledInvitationToken = it }
            onInvitationOpenHandled()
        },
        onDismissInvitationDraftReplacement = {
            showInvitationDraftConfirmation = false
            invitationOpenRequest?.token?.let { handledInvitationToken = it }
            onInvitationOpenHandled()
        },
    )
}

private fun FocusSetupState.hasInvitationDraft(): Boolean =
    name.isNotEmpty() || tag.isNotEmpty() ||
        timerMode != com.luma.lifeos.domain.focus.FocusTimerMode.COUNT_UP ||
        timedMinutesText != "25" || repeatSourceSessionId != null ||
        selectedTaskId != null || taskOrigin || taskSelectionLoading ||
        taskSelectionConflict || capturedStart != null || unresolvedStartPending

