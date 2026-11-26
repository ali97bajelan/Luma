package com.luma.lifeos.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusInvalidReason
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusReflection
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusTimerMode
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.ui.todo.TodoActionResult
import com.luma.lifeos.ui.todo.TodoFamilyActions
import com.luma.lifeos.ui.todo.TodoFamilyActionPhase
import com.luma.lifeos.ui.todo.TodoFamilyActionState
import com.luma.lifeos.ui.todo.TodoFamilyActionType
import com.luma.lifeos.ui.todo.TodoUndoOutcome
import com.luma.lifeos.ui.focus.FocusInsightsState
import com.luma.lifeos.ui.focus.FocusPrivateSurface
import com.luma.lifeos.ui.focus.FocusPeriod
import com.luma.lifeos.ui.focus.FocusSetupEvent
import com.luma.lifeos.ui.focus.FocusSetupState
import com.luma.lifeos.ui.focus.FocusTaskCompletionPhase
import com.luma.lifeos.ui.focus.FocusTaskCompletionState
import com.luma.lifeos.ui.focus.FocusTaskUnlinkPhase
import com.luma.lifeos.ui.focus.PendingFocusTaskCompletion
import com.luma.lifeos.ui.focus.PendingFocusTaskUnlink
import com.luma.lifeos.ui.focus.FocusTagSuggestionsSnapshot
import com.luma.lifeos.ui.focus.FocusRecentWorkQuery
import com.luma.lifeos.ui.focus.FocusRecentWorkState
import com.luma.lifeos.ui.focus.observeFocusRecentWork
import com.luma.lifeos.ui.focus.encodeFocusSetupState
import com.luma.lifeos.ui.focus.focusMetadataForPresentation
import com.luma.lifeos.ui.focus.focusInsightsPresentation
import com.luma.lifeos.ui.focus.focusInsightsQueryFor
import com.luma.lifeos.ui.focus.observeFocusInsights
import com.luma.lifeos.ui.focus.reduceFocusSetupState
import com.luma.lifeos.ui.focus.restoreFocusSetupState
import com.luma.lifeos.ui.focus.visibleFocusTagSuggestions
import com.luma.lifeos.ui.focus.visibleFocusPrivateSurfaces
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusOperationFailure { START, PAUSE, RESUME, END, RECOVERY, CHECKPOINT, UNKNOWN }

data class FocusScreenState(
    val runtime: FocusRuntimeState,
    val busy: Boolean = false,
    val breakBusy: Boolean = false,
    val breakOperationFailed: Boolean = false,
    val operationFailed: Boolean = false,
    val operationFailure: FocusOperationFailure? = null,
    val backPromptVisible: Boolean = false,
    val emergencyPromptVisible: Boolean = false,
    val shortcutUnavailable: Boolean = false,
    val shortcutLabel: String? = null,
    val setup: FocusSetupState = FocusSetupState(),
    val insights: FocusInsightsState = FocusInsightsState.Unselected,
    val tagSuggestions: List<FocusTag> = emptyList(),
    val focusContentReadable: Boolean = false,
    val taskOriginSelected: Boolean = false,
    val taskActionsAllowed: Boolean = false,
    val taskCompletion: FocusTaskCompletionState = FocusTaskCompletionState(),
    val recentWork: FocusRecentWorkState = FocusRecentWorkState.Hidden,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(
    private val runtime: FocusRuntime,
    private val repository: FocusRepository?,
    private val savedStateHandle: SavedStateHandle,
    private val todos: TodoRepository? = null,
    private val products: FocusProductRepository? = null,
) : ViewModel() {
    private sealed interface PendingOperation {
        data class Start(
            val commandId: UUID,
            val sessionId: UUID,
            val input: FocusStartInput,
            val attempt: FocusCoordinator.OperationAttempt,
        ) : PendingOperation
        data class Action(
            val commandId: UUID,
            val session: FocusSession,
            val action: FocusAction,
            val attempt: FocusCoordinator.OperationAttempt,
        ) : PendingOperation
        data class Recovery(
            val session: FocusSession,
            val choice: FocusRecoveryChoice,
            val attempt: FocusCoordinator.OperationAttempt,
        ) : PendingOperation
    }

    private data class FocusTaskCompletionCommandSource(
        val commandId: UUID,
        val sessionId: UUID,
        val sessionRevision: Long,
        val taskId: UUID,
        val generation: UUID,
    )

    private data class LocalState(
        val busy: Boolean = false,
        val breakBusy: Boolean = false,
        val breakOperationFailed: Boolean = false,
        val operationFailed: Boolean = false,
        val operationFailure: FocusOperationFailure? = null,
        val backPromptVisible: Boolean = false,
        val emergencyPromptVisible: Boolean = false,
        val emergencyMarkerSaved: Boolean? = null,
        val shortcutUnavailable: Boolean = false,
        val shortcutLabel: String? = null,
        val setup: FocusSetupState,
        val taskCompletion: FocusTaskCompletionState = FocusTaskCompletionState(),
    )

    private val local = MutableStateFlow(
        LocalState(
            setup = restoreSetupState(savedStateHandle),
            taskCompletion = restoreTaskCompletionUndo(),
        ),
    )
    private val insightState = MutableStateFlow<FocusInsightsState>(FocusInsightsState.Unselected)
    private val recentWorkState = MutableStateFlow<FocusRecentWorkState>(FocusRecentWorkState.Hidden)
    private val recentWorkRetry = MutableStateFlow(0)
    private val tagSuggestions = MutableStateFlow(FocusTagSuggestionsSnapshot(null, emptyList()))
    private val privateContentAccess = MutableStateFlow(false)
    private val taskContentAccess = MutableStateFlow(false)
    private val taskActionAccess = MutableStateFlow(false)
    private val taskAccessReady = MutableStateFlow(false)
    private var taskActionAccessResolved = false
    val taskFamilyActions: TodoFamilyActions? = todos?.let { taskRepository ->
        TodoFamilyActions(
            repository = taskRepository,
            saved = savedStateHandle,
            scope = viewModelScope,
            keyPrefix = "focus_task_family",
            onCompletionApplied = ::acceptTaskCompletionReceipt,
            onDeleteApplied = {},
            onCommandStarted = ::saveTaskCompletionCommandSource,
            beforeCompletionApply = { action ->
                val current = local.value.taskCompletion
                val targetId = action.targetId ?: throw IllegalStateException("Missing focus task target")
                val sessionId = current.sessionId ?: throw TodoConflictException(targetId)
                val sessionRevision = current.sessionRevision ?: throw TodoConflictException(targetId)
                val generation = current.generation ?: throw TodoConflictException(targetId)
                if (current.taskId != targetId || !canPerformTaskActions(generation) ||
                    !isCurrentTaskLinkedSession(sessionId, sessionRevision, targetId, generation)
                ) throw TodoConflictException(targetId)
            },
        )
    }
    private val familyActionState = taskFamilyActions?.state ?: flowOf(TodoFamilyActionState())
    private var privateContentAccessResolved = false
    private var taskContentAccessResolved = false
    private val insightRetry = MutableStateFlow(0)
    private var pendingOperation: PendingOperation? = null
    private var pendingShortcutPackage: String? = null

    private val contentAccess = combine(
        privateContentAccess,
        taskContentAccess,
        taskActionAccess,
        familyActionState,
    ) { canReadFocus, canReadTasks, canManageLinkedTaskActions, familyAction ->
        Triple(canReadFocus, canReadTasks, canManageLinkedTaskActions) to familyAction
    }

    private val baseState: StateFlow<FocusScreenState> = combine(
        runtime.state, local, insightState, tagSuggestions, contentAccess,
    ) { runtimeState, localState, insights, suggestions, accessAndFamily ->
        val (access, familyAction) = accessAndFamily
        val (canReadFocus, canReadTasks, canManageLinkedTaskActions) = access
        val visibleSurfaces = visibleFocusPrivateSurfaces(canReadFocus)
        val canReadFocusSurface = FocusPrivateSurface.FOCUS in visibleSurfaces
        val canReadInsights = FocusPrivateSurface.INSIGHTS in visibleSurfaces
        val selectedQuery = if (canReadInsights) focusInsightsQueryFor(
            localState.setup,
            runtimeState.initialized,
            runtimeState.databaseGeneration,
        ) else null
        val currentTags = if (canReadFocusSurface) {
            visibleFocusTagSuggestions(runtimeState.databaseGeneration, suggestions)
        } else emptyList()
        val presentation = focusInsightsPresentation(
            canReadInsights,
            selectedQuery,
            insights,
            currentTags,
            taskContentAccessAllowed = canReadTasks,
        )
        FocusScreenState(
            runtime = runtimeState.copy(
                session = runtimeState.session?.let { session ->
                    session.copy(
                        metadata = focusMetadataForPresentation(
                            session.metadata,
                            canReadFocusSurface && (!session.taskOrigin || canReadTasks),
                        ),
                        reflection = if (canReadFocusSurface && (!session.taskOrigin || canReadTasks)) {
                            session.reflection
                        } else FocusReflection(),
                        taskId = if (canReadFocusSurface && (!session.taskOrigin || canReadTasks)) {
                            session.taskId
                        } else null,
                    )
                },
                emergencyMarkerSaved = localState.emergencyMarkerSaved ?: runtimeState.emergencyMarkerSaved,
            ),
            busy = localState.busy,
            breakBusy = localState.breakBusy,
            breakOperationFailed = localState.breakOperationFailed,
            operationFailed = localState.operationFailed,
            operationFailure = localState.operationFailure,
            backPromptVisible = localState.backPromptVisible,
            emergencyPromptVisible = localState.emergencyPromptVisible,
            shortcutUnavailable = localState.shortcutUnavailable || runtimeState.shortcutFailed,
            shortcutLabel = localState.shortcutLabel,
            setup = if (canReadFocusSurface && (!localState.setup.taskOrigin || canReadTasks)) {
                localState.setup
            } else FocusSetupState(),
            insights = presentation.state,
            tagSuggestions = presentation.tagSuggestions,
            focusContentReadable = canReadFocusSurface,
            taskOriginSelected = localState.setup.taskOrigin || runtimeState.session?.taskOrigin == true,
            taskActionsAllowed = canReadFocus && canReadTasks && canManageLinkedTaskActions,
            taskCompletion = localState.taskCompletion.copy(
                familyAction = familyAction,
                completionPhase = when (familyAction.phase) {
                    TodoFamilyActionPhase.PREVIEWING -> FocusTaskCompletionPhase.LOADING
                    TodoFamilyActionPhase.AWAITING_CONFIRMATION,
                    TodoFamilyActionPhase.IDLE -> if (
                        localState.taskCompletion.completionPhase == FocusTaskCompletionPhase.LOADING ||
                        localState.taskCompletion.completionPhase == FocusTaskCompletionPhase.SAVING
                    ) FocusTaskCompletionPhase.IDLE else localState.taskCompletion.completionPhase
                    TodoFamilyActionPhase.APPLYING -> FocusTaskCompletionPhase.SAVING
                    TodoFamilyActionPhase.APPLIED -> localState.taskCompletion.completionPhase.takeUnless {
                        it == FocusTaskCompletionPhase.LOADING || it == FocusTaskCompletionPhase.SAVING
                    } ?: FocusTaskCompletionPhase.COMPLETED
                    TodoFamilyActionPhase.RETRYABLE_FAILURE -> FocusTaskCompletionPhase.ERROR
                    TodoFamilyActionPhase.CONFLICT -> FocusTaskCompletionPhase.CONFLICT
                    TodoFamilyActionPhase.MISSING -> FocusTaskCompletionPhase.MISSING
                },
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FocusScreenState(
            runtime = runtime.state.value.copy(
                session = runtime.state.value.session?.let { session ->
                    val canReadSessionContent = privateContentAccess.value &&
                        (!session.taskOrigin || taskContentAccess.value)
                    session.copy(
                        metadata = focusMetadataForPresentation(session.metadata, canReadSessionContent),
                        reflection = if (canReadSessionContent) session.reflection else FocusReflection(),
                        taskId = if (canReadSessionContent) session.taskId else null,
                    )
                },
            ),
            setup = if (privateContentAccess.value &&
                (!local.value.setup.taskOrigin || taskContentAccess.value)
            ) local.value.setup else FocusSetupState(),
            focusContentReadable = privateContentAccess.value,
            taskOriginSelected = local.value.setup.taskOrigin || runtime.state.value.session?.taskOrigin == true,
            taskActionsAllowed = privateContentAccess.value && taskContentAccess.value && taskActionAccess.value,
            taskCompletion = local.value.taskCompletion.copy(
                familyAction = taskFamilyActions?.state?.value ?: TodoFamilyActionState(),
            ),
        ),
    )

    val state: StateFlow<FocusScreenState> = combine(
        baseState,
        recentWorkState,
        privateContentAccess,
        taskContentAccess,
    ) { base, recentWork, canReadFocus, canReadTasks ->
        val query = recentWork.query
        val setupIsEligible = base.runtime.initialized && !base.runtime.loading && !base.runtime.loadFailed &&
            !base.runtime.recoveryRequired &&
            base.runtime.session?.state !in setOf(FocusState.RUNNING, FocusState.PAUSED) &&
            !base.busy && base.setup.capturedStart == null && !base.setup.unresolvedStartPending
        val visibleRecentWork = if (
            !canReadFocus || !setupIsEligible || query == null ||
            query.generation != base.runtime.databaseGeneration ||
            query.includeTaskOrigin != canReadTasks ||
            (recentWork is FocusRecentWorkState.Ready && recentWork.session.taskOrigin && !canReadTasks)
        ) FocusRecentWorkState.Hidden else recentWork
        base.copy(recentWork = visibleRecentWork)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, baseState.value)

    val shellState: StateFlow<FocusScreenState> = state
        .map { screen -> screen.copy(runtime = screen.runtime.copy(displayedMillis = 0L)) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            state.value.copy(runtime = state.value.runtime.copy(displayedMillis = 0L)),
        )

    val displayedMillisFlow: StateFlow<Long> = runtime.state
        .map { it.displayedMillis }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, runtime.state.value.displayedMillis)

    init {
        taskFamilyActions?.let { actions ->
            viewModelScope.launch {
                actions.state.collect { familyAction ->
                    if (familyAction.phase == TodoFamilyActionPhase.IDLE) {
                        readTaskCompletionCommandSource()?.let(::discardPendingTaskCompletionCommand)
                        local.update {
                            if (it.taskCompletion.completionPhase == FocusTaskCompletionPhase.SAVING) {
                                it.copy(taskCompletion = it.taskCompletion.copy(
                                    completionPhase = FocusTaskCompletionPhase.IDLE,
                                ))
                            } else it
                        }
                    } else if (familyAction.phase == TodoFamilyActionPhase.APPLIED) {
                        retryAcceptPendingTaskCompletionReceipt()
                    }
                }
            }
        }
        viewModelScope.launch {
            runtime.state.map { it.databaseReplacementInProgress }.distinctUntilChanged().collect { replacing ->
                if (!replacing) return@collect
                pendingOperation = null
                pendingShortcutPackage = null
                updateSetup(FocusSetupEvent.StartAnotherRequested)
                insightState.value = FocusInsightsState.Unselected
                recentWorkState.value = FocusRecentWorkState.Hidden
                recentWorkRetry.value = 0
                tagSuggestions.value = FocusTagSuggestionsSnapshot(null, emptyList())
                clearTaskCompletionUndo(release = false)
                local.update {
                    it.copy(
                        busy = false,
                        breakBusy = false,
                        breakOperationFailed = false,
                        operationFailed = false,
                        operationFailure = null,
                        backPromptVisible = false,
                        emergencyPromptVisible = false,
                        emergencyMarkerSaved = null,
                        shortcutUnavailable = false,
                        shortcutLabel = null,
                        taskCompletion = FocusTaskCompletionState(),
                    )
                }
            }
        }
        viewModelScope.launch {
            runtime.state.map { it.databaseGeneration }.filterNotNull().distinctUntilChanged().collect { generation ->
                val previous = local.value.setup.databaseGeneration
                if (previous != null && previous != generation) {
                    clearTaskCompletionUndo(release = false)
                    pendingOperation = null
                    pendingShortcutPackage = null
                    recentWorkState.value = FocusRecentWorkState.Hidden
                    local.update {
                        it.copy(
                            operationFailed = false,
                            operationFailure = null,
                            shortcutUnavailable = false,
                            shortcutLabel = null,
                            taskCompletion = FocusTaskCompletionState(),
                        )
                    }
                }
                updateSetup(FocusSetupEvent.GenerationChanged(generation))
            }
        }
        viewModelScope.launch {
            runtime.state.map { it.databaseGeneration }.distinctUntilChanged().collect { generation ->
                if (tagSuggestions.value.databaseGeneration != generation) {
                    tagSuggestions.value = FocusTagSuggestionsSnapshot(generation, emptyList())
                }
            }
        }
        repository?.let { focusRepository ->
            val queries = combine(runtime.state, local, privateContentAccess) { runtimeState, localState, canReadFocus ->
                if (!canReadFocus) null else focusInsightsQueryFor(
                    localState.setup,
                    runtimeState.initialized,
                    runtimeState.databaseGeneration,
                )
            }.distinctUntilChanged()
            val requests = combine(queries, insightRetry) { query, _ -> query }
            viewModelScope.launch {
                observeFocusInsights(focusRepository, requests).collect {
                    insightState.value = if (privateContentAccess.value) it else FocusInsightsState.Unselected
                }
            }
            viewModelScope.launch {
                val tagQueries = combine(
                    privateContentAccess,
                    runtime.state.map { it.databaseGeneration }.distinctUntilChanged(),
                ) { canReadFocus, generation -> if (canReadFocus) generation else null }
                    .distinctUntilChanged()
                tagQueries.flatMapLatest { generation ->
                    if (generation == null) {
                        flowOf(FocusTagSuggestionsSnapshot(null, emptyList()))
                    } else {
                        flow {
                            val current = focusRepository.currentGeneration()
                            if (current != generation) {
                                emit(FocusTagSuggestionsSnapshot(current, emptyList()))
                            }
                            focusRepository.observeTagSuggestions().collect { tags ->
                                val latest = focusRepository.currentGeneration()
                                emit(
                                    if (latest == generation) FocusTagSuggestionsSnapshot(generation, tags)
                                    else FocusTagSuggestionsSnapshot(latest, emptyList()),
                                )
                            }
                        }
                    }
                }.catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(FocusTagSuggestionsSnapshot(null, emptyList()))
                }.collect { tags ->
                    val currentGeneration = runtime.state.value.databaseGeneration
                    tagSuggestions.value = if (
                        privateContentAccess.value && currentGeneration == tags.databaseGeneration
                    ) tags else FocusTagSuggestionsSnapshot(currentGeneration, emptyList())
                }
            }
        }
        repository?.let { focusRepository ->
            val recentQueries = combine(
                runtime.state,
                local,
                privateContentAccess,
                taskContentAccess,
                recentWorkRetry,
            ) { runtimeState, localState, canReadFocus, canReadTasks, retry ->
                val sessionIsOpen = runtimeState.session?.state in setOf(FocusState.RUNNING, FocusState.PAUSED)
                val setupIsEligible = canReadFocus && runtimeState.initialized &&
                    !runtimeState.loading && !runtimeState.loadFailed &&
                    !runtimeState.recoveryRequired && !sessionIsOpen && !localState.busy &&
                    localState.setup.capturedStart == null && !localState.setup.unresolvedStartPending
                val generation = runtimeState.databaseGeneration
                if (setupIsEligible && generation != null) {
                    FocusRecentWorkQuery(generation, canReadTasks, retry)
                } else null
            }.distinctUntilChanged()
            viewModelScope.launch {
                observeFocusRecentWork(focusRepository, recentQueries).collect { observed ->
                    val missing = (recentWorkState.value as? FocusRecentWorkState.Missing)?.takeIf {
                        privateContentAccess.value &&
                            taskContentAccess.value == it.request.includeTaskOrigin &&
                            runtime.state.value.databaseGeneration == it.request.generation &&
                            (observed is FocusRecentWorkState.Hidden || observed.query == it.request)
                    }
                    recentWorkState.value = missing ?: observed
                }
            }
        }
    }

    fun setPrivateContentAccessAllowed(allowed: Boolean) {
        if (privateContentAccessResolved && privateContentAccess.value == allowed) return
        privateContentAccessResolved = true
        privateContentAccess.value = allowed
        updateTaskAccessReadiness()
        if (!allowed) {
            updateSetup(FocusSetupEvent.PrivateContentLocked)
            clearTaskCompletionState()
            insightState.value = FocusInsightsState.Unselected
            recentWorkState.value = FocusRecentWorkState.Hidden
            tagSuggestions.value = FocusTagSuggestionsSnapshot(null, emptyList())
        } else {
            reloadSelectedTaskIfNeeded()
        }
    }

    fun setTaskContentAccessAllowed(allowed: Boolean) {
        if (taskContentAccessResolved && taskContentAccess.value == allowed) return
        taskContentAccessResolved = true
        taskContentAccess.value = allowed
        updateTaskAccessReadiness()
        if (!allowed) {
            val repeatRequestId = local.value.setup.repeatRequestId
            updateSetup(FocusSetupEvent.TaskContentLocked)
            if (repeatRequestId != null) updateSetup(FocusSetupEvent.RepeatLoadFailed(repeatRequestId))
            insightState.value = FocusInsightsState.Unselected
            recentWorkState.value = FocusRecentWorkState.Hidden
            tagSuggestions.value = FocusTagSuggestionsSnapshot(null, emptyList())
            clearTaskCompletionState()
        } else {
            reloadSelectedTaskIfNeeded()
        }
    }

    fun setTaskActionAccessAllowed(allowed: Boolean) {
        if (taskActionAccessResolved && taskActionAccess.value == allowed) return
        taskActionAccessResolved = true
        taskActionAccess.value = allowed
        updateTaskAccessReadiness()
        if (!allowed) clearTaskCompletionState()
    }

    fun syncTaskCompletionSession(
        sessionId: UUID,
        sessionRevision: Long,
        taskId: UUID?,
        generation: UUID?,
    ) {
        val current = local.value.taskCompletion
        if (current.matches(sessionId, sessionRevision, taskId, generation)) return
        taskFamilyActions?.invalidate()
        clearTaskCompletionUndo(release = true)
        local.update {
            it.copy(
                taskCompletion = FocusTaskCompletionState(
                    sessionId = sessionId,
                    sessionRevision = sessionRevision,
                    taskId = taskId,
                    generation = generation,
                    completionPhase = if (taskId != null) FocusTaskCompletionPhase.CHECKING
                    else FocusTaskCompletionPhase.IDLE,
                ),
            )
        }
        if (taskId != null && generation != null) {
            loadTaskCompletionStatus(sessionId, sessionRevision, taskId, generation)
        }
    }

    fun refreshTaskCompletionSession(
        sessionId: UUID,
        sessionRevision: Long,
        taskId: UUID?,
        generation: UUID?,
    ) {
        if (taskId == null || generation == null) return
        val current = local.value.taskCompletion
        if (!current.matches(sessionId, sessionRevision, taskId, generation)) {
            syncTaskCompletionSession(sessionId, sessionRevision, taskId, generation)
            return
        }
        val pendingSource = readTaskCompletionCommandSource()
        val familyAction = taskFamilyActions?.state?.value
        if (pendingSource != null &&
            pendingSource.sessionId == sessionId &&
            pendingSource.sessionRevision == sessionRevision &&
            pendingSource.taskId == taskId &&
            pendingSource.generation == generation &&
            familyAction?.phase == TodoFamilyActionPhase.APPLIED &&
            familyAction.commandId == pendingSource.commandId
        ) {
            retryAcceptPendingTaskCompletionReceipt()
            return
        }
        if (current.undoing || taskFamilyActions?.state?.value?.isActive == true ||
            current.completionPhase in setOf(
                FocusTaskCompletionPhase.CHECKING,
                FocusTaskCompletionPhase.LOADING,
            )
        ) return
        local.update {
            it.copy(taskCompletion = it.taskCompletion.copy(completionPhase = FocusTaskCompletionPhase.CHECKING))
        }
        loadTaskCompletionStatus(sessionId, sessionRevision, taskId, generation)
    }

    private fun loadTaskCompletionStatus(
        sessionId: UUID,
        sessionRevision: Long,
        taskId: UUID,
        generation: UUID,
    ) {
        viewModelScope.launch {
            try {
                if (!canPerformTaskActions(generation)) {
                    clearTaskCompletionState()
                    return@launch
                }
                if (!isCurrentTaskLinkedSession(sessionId, sessionRevision, taskId, generation)) {
                    val latest = local.value.taskCompletion
                    if (latest.matches(sessionId, sessionRevision, taskId, generation)) {
                        clearTaskCompletionUndo(release = true)
                        setTaskCompletionPhase(FocusTaskCompletionPhase.LINK_CONFLICT, clearPending = true)
                    }
                    return@launch
                }
                val task = todos?.get(taskId)
                if (!canPerformTaskActions(generation)) {
                    clearTaskCompletionState()
                    return@launch
                }
                val latest = local.value.taskCompletion
                if (!latest.matches(sessionId, sessionRevision, taskId, generation) || latest.undoing) return@launch
                if (task == null || !task.isCompleted) clearTaskCompletionUndo(release = true)
                setTaskCompletionPhase(
                    when {
                        task == null -> FocusTaskCompletionPhase.MISSING
                        task.isCompleted -> FocusTaskCompletionPhase.COMPLETED
                        else -> FocusTaskCompletionPhase.IDLE
                    },
                    clearPending = true,
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (canPerformTaskActions(generation) &&
                    local.value.taskCompletion.matches(sessionId, sessionRevision, taskId, generation)
                ) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.CHECK_ERROR, clearPending = true)
                } else if (!canPerformTaskActions(generation)) clearTaskCompletionState()
            }
        }
    }

    fun undoTaskCompletion() {
        val current = local.value.taskCompletion
        val result = current.undoableCompletion ?: return
        val commandId = result.completionCommandId ?: return
        val generation = current.generation ?: return
        val sessionId = current.sessionId ?: return
        val sessionRevision = current.sessionRevision ?: return
        val taskId = current.taskId ?: return
        if (taskId != result.taskId || current.undoing || current.undoTerminal ||
            !canPerformTaskActions(generation)
        ) return
        val undoKey = focusTaskUndoCommandKey(commandId)
        val undoCommandId = savedStateHandle.get<String>(undoKey)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { savedStateHandle[undoKey] = it.toString() }
        local.update {
            if (it.taskCompletion.matches(sessionId, sessionRevision, taskId, generation) &&
                it.taskCompletion.undoableCompletion == result
            ) {
                it.copy(taskCompletion = it.taskCompletion.copy(undoing = true, undoFailed = false))
            } else it
        }
        viewModelScope.launch {
            try {
                taskFamilyActions?.undo(commandId, undoCommandId) {
                    val latest = local.value.taskCompletion
                    if (!latest.matches(sessionId, sessionRevision, taskId, generation) ||
                        latest.undoableCompletion != result ||
                        !canPerformTaskActions(generation) ||
                        !isCurrentTaskLinkedSession(sessionId, sessionRevision, taskId, generation)
                    ) throw TodoConflictException(taskId)
                } ?: throw IllegalStateException("Task family completion is unavailable")
                runCatching { todos?.releaseCommandHandle(commandId) }
                runCatching { todos?.releaseCommandHandle(undoCommandId) }
                savedStateHandle.remove<String>(undoKey)
                val latest = local.value.taskCompletion
                if (latest.matches(sessionId, sessionRevision, taskId, generation) &&
                    latest.undoableCompletion == result
                ) {
                    val familyState = taskFamilyActions?.state?.value
                    if (familyState?.phase == TodoFamilyActionPhase.APPLIED &&
                        familyState.commandId == commandId
                    ) taskFamilyActions.invalidate()
                    clearTaskCompletionUndo(expected = result, release = false)
                    local.update {
                        if (it.taskCompletion.matches(sessionId, sessionRevision, taskId, generation)) {
                            it.copy(taskCompletion = it.taskCompletion.copy(
                                completionPhase = FocusTaskCompletionPhase.IDLE,
                                pendingCompletion = null,
                            ))
                        } else it
                    }
                    refreshTaskCompletionSession(sessionId, sessionRevision, taskId, generation)
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                val outcome = when (failure) {
                    is TodoConflictException, is TodoMissingException -> TodoUndoOutcome.STALE
                    else -> TodoUndoOutcome.RETRYABLE_FAILURE
                }
                val latest = local.value.taskCompletion
                val stillCurrent = latest.matches(sessionId, sessionRevision, taskId, generation) &&
                    latest.undoableCompletion == result
                if (stillCurrent) {
                    local.update {
                        if (it.taskCompletion.matches(sessionId, sessionRevision, taskId, generation) &&
                            it.taskCompletion.undoableCompletion == result
                        ) {
                            it.copy(taskCompletion = it.taskCompletion.copy(
                                undoing = false,
                                undoFailed = true,
                                undoTerminal = outcome == TodoUndoOutcome.STALE,
                            ))
                        } else it
                    }
                    if (outcome == TodoUndoOutcome.STALE) {
                        refreshTaskCompletionSession(sessionId, sessionRevision, taskId, generation)
                    }
                }
            }
        }
    }

    fun dismissTaskCompletionUndo() {
        if (local.value.taskCompletion.undoing) return
        clearTaskCompletionUndo(release = true)
    }

    fun markTaskComplete(sessionId: UUID, sessionRevision: Long, taskId: UUID, generation: UUID) {
        if (!canPerformTaskActions(generation) || todos == null || repository == null) return
        val completionState = local.value.taskCompletion
        if (!completionState.matches(sessionId, sessionRevision, taskId, generation)) {
            syncTaskCompletionSession(sessionId, sessionRevision, taskId, generation)
            return
        }
        if (completionState.completionPhase == FocusTaskCompletionPhase.CHECKING ||
            completionState.completionPhase == FocusTaskCompletionPhase.CHECK_ERROR ||
            completionState.completionPhase == FocusTaskCompletionPhase.LINK_CONFLICT ||
            completionState.completionPhase == FocusTaskCompletionPhase.COMPLETED ||
            completionState.completionPhase == FocusTaskCompletionPhase.MISSING ||
            completionState.completionPhase == FocusTaskCompletionPhase.LOADING ||
            completionState.completionPhase == FocusTaskCompletionPhase.SAVING ||
            taskFamilyActions?.state?.value?.isActive == true
        ) return
        local.update {
            it.copy(taskCompletion = it.taskCompletion.copy(
                completionPhase = FocusTaskCompletionPhase.LOADING,
                pendingCompletion = null,
                unlinkPhase = FocusTaskUnlinkPhase.IDLE,
                pendingUnlink = null,
            ))
        }
        viewModelScope.launch {
            try {
                val linkedSessionIsCurrent = isCurrentTaskLinkedSession(
                    sessionId, sessionRevision, taskId, generation,
                )
                if (!canPerformTaskActions(generation)) {
                    clearTaskCompletionState()
                    return@launch
                }
                if (!linkedSessionIsCurrent) {
                    if (local.value.taskCompletion.matches(sessionId, sessionRevision, taskId, generation)) {
                        setTaskCompletionPhase(FocusTaskCompletionPhase.CONFLICT, clearPending = true)
                    }
                    return@launch
                }
                val task = todos.get(taskId)
                if (!canPerformTaskActions(generation)) {
                    clearTaskCompletionState()
                    return@launch
                }
                val latest = local.value.taskCompletion
                if (!latest.matches(sessionId, sessionRevision, taskId, generation)) return@launch
                if (task == null) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.MISSING, clearPending = true)
                    return@launch
                }
                if (task.isCompleted) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.COMPLETED, clearPending = true)
                    return@launch
                }
                local.update {
                    if (it.taskCompletion.matches(sessionId, sessionRevision, taskId, generation)) {
                        it.copy(taskCompletion = it.taskCompletion.copy(
                            completionPhase = FocusTaskCompletionPhase.SAVING,
                        ))
                    } else it
                }
                taskFamilyActions?.complete(taskId, completed = true, title = task.title, expectedRevision = task.revision)
                    ?: setTaskCompletionPhase(FocusTaskCompletionPhase.ERROR, clearPending = true)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (canPerformTaskActions(generation) &&
                    local.value.taskCompletion.matches(sessionId, sessionRevision, taskId, generation)
                ) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.ERROR, clearPending = true)
                } else if (!canPerformTaskActions(generation)) clearTaskCompletionState()
            }
        }
    }

    fun retryTaskCompletion(sessionId: UUID, sessionRevision: Long, taskId: UUID, generation: UUID) {
        val state = local.value.taskCompletion
        if (!state.matches(sessionId, sessionRevision, taskId, generation)) {
            markTaskComplete(sessionId, sessionRevision, taskId, generation)
            return
        }
        if (state.completionPhase == FocusTaskCompletionPhase.CHECK_ERROR ||
            state.completionPhase == FocusTaskCompletionPhase.LINK_CONFLICT ||
            state.completionPhase == FocusTaskCompletionPhase.MISSING
        ) {
            refreshTaskCompletionSession(sessionId, sessionRevision, taskId, generation)
            return
        }
        when (taskFamilyActions?.state?.value?.phase) {
            TodoFamilyActionPhase.RETRYABLE_FAILURE -> taskFamilyActions?.retry()
            TodoFamilyActionPhase.CONFLICT, TodoFamilyActionPhase.MISSING -> taskFamilyActions?.reviewAgain()
            else -> markTaskComplete(sessionId, sessionRevision, taskId, generation)
        }
    }

    fun unlinkTask(sessionId: UUID, sessionRevision: Long, taskId: UUID, generation: UUID) {
        val productRepository = products ?: return
        if (!canPerformTaskActions(generation)) return
        syncTaskCompletionSession(sessionId, sessionRevision, taskId, generation)
        val current = local.value.taskCompletion
        if (current.unlinkPhase == FocusTaskUnlinkPhase.SAVING) return
        val pending = PendingFocusTaskUnlink(
            commandId = UUID.randomUUID(),
            sessionId = sessionId,
            taskId = taskId,
            expectedRevision = sessionRevision,
            generation = generation,
        )
        local.update {
            it.copy(taskCompletion = it.taskCompletion.copy(
                unlinkPhase = FocusTaskUnlinkPhase.SAVING,
                pendingUnlink = pending,
                completionPhase = FocusTaskCompletionPhase.IDLE,
                pendingCompletion = null,
            ))
        }
        viewModelScope.launch { persistTaskUnlink(pending, productRepository) }
    }

    fun retryTaskUnlink(sessionId: UUID, sessionRevision: Long, taskId: UUID, generation: UUID) {
        val current = local.value.taskCompletion
        val pending = current.pendingUnlink
        val productRepository = products ?: return
        if (current.matches(sessionId, sessionRevision, taskId, generation) && pending != null) {
            if (current.unlinkPhase == FocusTaskUnlinkPhase.SAVING) return
            local.update {
                it.copy(taskCompletion = it.taskCompletion.copy(unlinkPhase = FocusTaskUnlinkPhase.SAVING))
            }
            viewModelScope.launch { persistTaskUnlink(pending, productRepository) }
        } else {
            unlinkTask(sessionId, sessionRevision, taskId, generation)
        }
    }

    private suspend fun persistTaskUnlink(
        pending: PendingFocusTaskUnlink,
        productRepository: FocusProductRepository,
    ) {
        if (!canPerformTaskActions(pending.generation)) {
            clearTaskCompletionState()
            return
        }
        try {
            if (productRepository.currentGeneration() != pending.generation ||
                !canPerformTaskActions(pending.generation)
            ) {
                clearTaskCompletionState()
                return
            }
            val result = productRepository.unlinkTask(
                pending.sessionId,
                pending.expectedRevision,
                FocusProductContext(pending.commandId, pending.generation),
            )
            if (!canPerformTaskActions(pending.generation)) {
                clearTaskCompletionState()
                return
            }
            when (result) {
                FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> {
                    if (!canPerformTaskActions(pending.generation)) {
                        clearTaskCompletionState()
                        return
                    }
                    local.update {
                        it.copy(taskCompletion = it.taskCompletion.copy(
                            unlinkPhase = FocusTaskUnlinkPhase.UNLINKED,
                            pendingUnlink = null,
                        ))
                    }
                }
                is FocusProductResult.SessionConflict,
                is FocusProductResult.Invalid -> local.update {
                    it.copy(taskCompletion = it.taskCompletion.copy(
                        unlinkPhase = FocusTaskUnlinkPhase.CONFLICT,
                        pendingUnlink = null,
                    ))
                }
                is FocusProductResult.Conflict,
                is FocusProductResult.InvitationConflict,
                is FocusProductResult.TagCatalogConflict,
                is FocusProductResult.MergeRequired,
                is FocusProductResult.Unavailable -> local.update {
                    it.copy(taskCompletion = it.taskCompletion.copy(unlinkPhase = FocusTaskUnlinkPhase.ERROR))
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (canPerformTaskActions(pending.generation)) {
                local.update {
                    it.copy(taskCompletion = it.taskCompletion.copy(unlinkPhase = FocusTaskUnlinkPhase.ERROR))
                }
            } else clearTaskCompletionState()
        }
    }

    private suspend fun isCurrentTaskLinkedSession(
        sessionId: UUID,
        sessionRevision: Long,
        taskId: UUID,
        generation: UUID,
    ): Boolean {
        if (!canPerformTaskActions(generation)) return false
        val focusRepository = repository ?: return false
        if (focusRepository.currentGeneration() != generation || !canPerformTaskActions(generation)) return false
        val current = focusRepository.terminalSession(sessionId) ?: return false
        if (!canPerformTaskActions(generation)) return false
        return current.revision == sessionRevision && current.taskId == taskId && current.taskOrigin &&
            current.terminalReason in setOf(FocusTerminalReason.USER_STOP, FocusTerminalReason.TARGET_REACHED)
    }

    private val acceptingTaskCompletionReceipts = mutableSetOf<UUID>()

    private fun saveTaskCompletionCommandSource(action: TodoFamilyActionState) {
        if (action.type != TodoFamilyActionType.COMPLETION) return
        val commandId = action.commandId ?: return
        val taskId = action.targetId ?: return
        val current = local.value.taskCompletion
        if (current.taskId != taskId) return
        val sessionId = current.sessionId ?: return
        val sessionRevision = current.sessionRevision ?: return
        val generation = current.generation ?: return
        savedStateHandle[KEY_TASK_COMPLETION_COMMAND] = listOf(
            commandId,
            sessionId,
            sessionRevision,
            taskId,
            generation,
        ).joinToString("|")
    }

    private fun readTaskCompletionCommandSource(): FocusTaskCompletionCommandSource? {
        val parts = savedStateHandle.get<String>(KEY_TASK_COMPLETION_COMMAND)?.split('|') ?: return null
        if (parts.size != 5) return null
        val commandId = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
        val sessionId = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return null
        val sessionRevision = parts[2].toLongOrNull() ?: return null
        val taskId = runCatching { UUID.fromString(parts[3]) }.getOrNull() ?: return null
        val generation = runCatching { UUID.fromString(parts[4]) }.getOrNull() ?: return null
        return FocusTaskCompletionCommandSource(commandId, sessionId, sessionRevision, taskId, generation)
    }

    private fun clearTaskCompletionCommandSource(commandId: UUID) {
        if (readTaskCompletionCommandSource()?.commandId == commandId) {
            savedStateHandle.remove<String>(KEY_TASK_COMPLETION_COMMAND)
        }
    }

    private fun acceptTaskCompletionReceipt(receipt: com.luma.lifeos.domain.todo.TodoCompletionReceipt) {
        val source = readTaskCompletionCommandSource()
        val current = local.value.taskCompletion
        if (current.undoableCompletion?.completionCommandId == receipt.commandId) {
            clearTaskCompletionCommandSource(receipt.commandId)
            return
        }
        if (source == null || source.commandId != receipt.commandId || source.taskId != receipt.targetId) {
            if (receipt.undoAvailable) taskRepositoryRelease(receipt.commandId)
            return
        }
        if (!acceptingTaskCompletionReceipts.add(receipt.commandId)) return
        viewModelScope.launch {
            try {
                taskAccessReady.first { it }
                val runtimeSnapshot = runtime.state.first {
                    it.databaseGeneration != null || it.databaseReplacementInProgress
                }
                val identityMatches = local.value.taskCompletion.matches(
                    source.sessionId,
                    source.sessionRevision,
                    source.taskId,
                    source.generation,
                )
                if (runtimeSnapshot.databaseReplacementInProgress ||
                    runtimeSnapshot.databaseGeneration != source.generation ||
                    !identityMatches ||
                    !canPerformTaskActions(source.generation)
                ) {
                    rejectTaskCompletionReceipt(source, receipt, FocusTaskCompletionPhase.LINK_CONFLICT)
                    return@launch
                }
                if (!isCurrentTaskLinkedSession(
                        source.sessionId,
                        source.sessionRevision,
                        source.taskId,
                        source.generation,
                    )
                ) {
                    rejectTaskCompletionReceipt(source, receipt, FocusTaskCompletionPhase.LINK_CONFLICT)
                    return@launch
                }
                val task = todos?.get(source.taskId)
                val latest = local.value.taskCompletion
                if (!latest.matches(source.sessionId, source.sessionRevision, source.taskId, source.generation)) {
                    rejectTaskCompletionReceipt(source, receipt, FocusTaskCompletionPhase.LINK_CONFLICT)
                    return@launch
                }
                if (!canPerformTaskActions(source.generation)) {
                    rejectTaskCompletionReceipt(source, receipt, FocusTaskCompletionPhase.LINK_CONFLICT)
                    return@launch
                }
                if (task == null) {
                    rejectTaskCompletionReceipt(source, receipt, FocusTaskCompletionPhase.MISSING)
                    return@launch
                }
                if (!task.isCompleted) {
                    rejectTaskCompletionReceipt(source, receipt, FocusTaskCompletionPhase.IDLE)
                    return@launch
                }

                val retainedUndo = latest.undoableCompletion
                if (retainedUndo?.completionCommandId != receipt.commandId) {
                    clearTaskCompletionUndo(release = true)
                }
                val undoable = if (receipt.undoAvailable && receipt.changedTaskCount > 0) {
                    TodoActionResult(
                        receipt.targetId,
                        completed = true,
                        resultingRevision = null,
                        completionCommandId = receipt.commandId,
                    )
                } else null
                if (undoable != null && retainedUndo?.completionCommandId != receipt.commandId) {
                    saveTaskCompletionUndo(latest, undoable)
                } else if (receipt.undoAvailable && undoable == null) {
                    taskRepositoryRelease(receipt.commandId)
                }
                clearTaskCompletionCommandSource(receipt.commandId)
                local.update {
                    if (it.taskCompletion.matches(
                            source.sessionId,
                            source.sessionRevision,
                            source.taskId,
                            source.generation,
                        )
                    ) {
                        it.copy(taskCompletion = it.taskCompletion.copy(
                            completionPhase = FocusTaskCompletionPhase.COMPLETED,
                            pendingCompletion = null,
                            undoableCompletion = undoable ?: it.taskCompletion.undoableCompletion,
                            undoing = false,
                            undoFailed = false,
                            undoTerminal = false,
                        ))
                    } else it
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                val latest = local.value.taskCompletion
                if (latest.matches(source.sessionId, source.sessionRevision, source.taskId, source.generation)) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.CHECK_ERROR, clearPending = true)
                }
            } finally {
                acceptingTaskCompletionReceipts.remove(receipt.commandId)
            }
        }
    }

    private fun rejectTaskCompletionReceipt(
        source: FocusTaskCompletionCommandSource,
        receipt: com.luma.lifeos.domain.todo.TodoCompletionReceipt,
        phase: FocusTaskCompletionPhase,
    ) {
        clearTaskCompletionCommandSource(receipt.commandId)
        val current = local.value.taskCompletion
        current.undoableCompletion
            ?.takeIf { it.completionCommandId == receipt.commandId }
            ?.let { clearTaskCompletionUndo(expected = it, release = false) }
        if (receipt.undoAvailable) taskRepositoryRelease(receipt.commandId)
        if (local.value.taskCompletion.matches(
                source.sessionId,
                source.sessionRevision,
                source.taskId,
                source.generation,
            )
        ) setTaskCompletionPhase(phase, clearPending = true)
    }

    private fun retryAcceptPendingTaskCompletionReceipt() {
        val source = readTaskCompletionCommandSource() ?: return
        val action = taskFamilyActions?.state?.value ?: return
        if (action.phase != TodoFamilyActionPhase.APPLIED || action.commandId != source.commandId) return
        viewModelScope.launch {
            try {
                val receipt = todos?.completionReceipt(source.commandId)
                if (receipt == null) {
                    discardPendingTaskCompletionCommand(source)
                    return@launch
                }
                acceptTaskCompletionReceipt(receipt)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
            }
        }
    }

    private fun discardPendingTaskCompletionCommand(source: FocusTaskCompletionCommandSource) {
        clearTaskCompletionCommandSource(source.commandId)
        taskRepositoryRelease(source.commandId)
        val current = local.value.taskCompletion
        if (!current.matches(
                source.sessionId,
                source.sessionRevision,
                source.taskId,
                source.generation,
            )
        ) return
        if (current.completionPhase !in setOf(
                FocusTaskCompletionPhase.CHECKING,
                FocusTaskCompletionPhase.SAVING,
                FocusTaskCompletionPhase.CHECK_ERROR,
            )
        ) return
        setTaskCompletionPhase(FocusTaskCompletionPhase.IDLE, clearPending = true)
        if (canPerformTaskActions(source.generation)) {
            loadTaskCompletionStatus(
                source.sessionId,
                source.sessionRevision,
                source.taskId,
                source.generation,
            )
        }
    }

    private fun updateTaskAccessReadiness() {
        taskAccessReady.value =
            privateContentAccessResolved && taskContentAccessResolved && taskActionAccessResolved
    }

    private fun canPerformTaskActions(generation: UUID): Boolean =
        privateContentAccess.value && taskContentAccess.value && taskActionAccess.value &&
            !runtime.state.value.databaseReplacementInProgress &&
            runtime.state.value.databaseGeneration == generation

    private fun setTaskCompletionPhase(phase: FocusTaskCompletionPhase, clearPending: Boolean) {
        local.update {
            it.copy(
                taskCompletion = it.taskCompletion.copy(
                    completionPhase = phase,
                    pendingCompletion = if (clearPending) null else it.taskCompletion.pendingCompletion,
                ),
            )
        }
    }

    private fun clearTaskCompletionState() {
        taskFamilyActions?.invalidate()
        clearTaskCompletionUndo(release = true)
        local.update { it.copy(taskCompletion = FocusTaskCompletionState()) }
    }

    private fun saveTaskCompletionUndo(state: FocusTaskCompletionState, result: TodoActionResult) {
        val sessionId = state.sessionId ?: return
        val sessionRevision = state.sessionRevision ?: return
        val taskId = state.taskId ?: return
        val generation = state.generation ?: return
        val commandId = result.completionCommandId ?: return
        savedStateHandle[KEY_TASK_UNDO_RESULT] = listOf(
            sessionId,
            sessionRevision,
            taskId,
            generation,
            commandId,
        ).joinToString("|")
    }

    private fun restoreTaskCompletionUndo(): FocusTaskCompletionState {
        val undoParts = savedStateHandle.get<String>(KEY_TASK_UNDO_RESULT)?.split('|')
        if (undoParts != null && undoParts.size == 5) {
            val sessionId = runCatching { UUID.fromString(undoParts[0]) }.getOrNull()
            val sessionRevision = undoParts[1].toLongOrNull()
            val taskId = runCatching { UUID.fromString(undoParts[2]) }.getOrNull()
            val generation = runCatching { UUID.fromString(undoParts[3]) }.getOrNull()
            val commandId = runCatching { UUID.fromString(undoParts[4]) }.getOrNull()
            if (sessionId != null && sessionRevision != null && taskId != null &&
                generation != null && commandId != null
            ) {
                return FocusTaskCompletionState(
                    sessionId = sessionId,
                    sessionRevision = sessionRevision,
                    taskId = taskId,
                    generation = generation,
                    completionPhase = FocusTaskCompletionPhase.COMPLETED,
                    undoableCompletion = TodoActionResult(
                        taskId,
                        completed = true,
                        resultingRevision = null,
                        completionCommandId = commandId,
                    ),
                )
            }
        }
        val source = readTaskCompletionCommandSource() ?: return FocusTaskCompletionState()
        return FocusTaskCompletionState(
            sessionId = source.sessionId,
            sessionRevision = source.sessionRevision,
            taskId = source.taskId,
            generation = source.generation,
            completionPhase = FocusTaskCompletionPhase.CHECKING,
        )
    }

    private fun clearTaskCompletionUndo(
        expected: TodoActionResult? = null,
        release: Boolean,
    ) {
        val result = local.value.taskCompletion.undoableCompletion
        if (expected != null && result != expected) return
        savedStateHandle.remove<String>(KEY_TASK_UNDO_RESULT)
        result?.completionCommandId?.let { commandId ->
            savedStateHandle.remove<String>(focusTaskUndoCommandKey(commandId))
            if (release) taskRepositoryRelease(commandId)
        }
        local.update {
            it.copy(taskCompletion = it.taskCompletion.copy(
                undoableCompletion = null,
                undoing = false,
                undoFailed = false,
                undoTerminal = false,
            ))
        }
    }

    private fun taskRepositoryRelease(commandId: UUID) {
        todos?.let { repository ->
            viewModelScope.launch { runCatching { repository.releaseCommandHandle(commandId) } }
        }
    }

    private fun focusTaskUndoCommandKey(commandId: UUID) = "focus_task_undo_next_$commandId"


    fun focusOnTask(taskId: UUID) {
        if (!privateContentAccess.value || !taskContentAccess.value || local.value.busy ||
            local.value.setup.repeatLoading || pendingOperation is PendingOperation.Start ||
            runtime.state.value.session?.state in setOf(FocusState.RUNNING, FocusState.PAUSED)
        ) return
        val generation = runtime.state.value.databaseGeneration ?: return
        val requestId = UUID.randomUUID()
        updateSetup(FocusSetupEvent.TaskSelectionRequested(taskId, requestId))
        loadTaskSelection(taskId, requestId, generation)
    }

    fun clearTaskSelection() {
        if (!privateContentAccess.value || !taskContentAccess.value || local.value.busy) return
        updateSetup(FocusSetupEvent.TaskSelectionCleared)
    }

    private fun reloadSelectedTaskIfNeeded() {
        if (!privateContentAccess.value || !taskContentAccess.value) return
        val setup = local.value.setup
        if (setup.selectedTaskId == null || setup.selectedTaskTitle != null || setup.taskSelectionLoading) return
        val generation = runtime.state.value.databaseGeneration ?: return
        val requestId = UUID.randomUUID()
        updateSetup(FocusSetupEvent.TaskSelectionRequested(setup.selectedTaskId, requestId))
        loadTaskSelection(setup.selectedTaskId, requestId, generation)
    }

    private fun loadTaskSelection(taskId: UUID, requestId: UUID, generation: UUID) {
        val taskRepository = todos
        if (taskRepository == null) {
            updateSetup(FocusSetupEvent.TaskSelectionLoadFailed(taskId, requestId))
            return
        }
        viewModelScope.launch {
            try {
                val task = taskRepository.get(taskId)
                if (!privateContentAccess.value || !taskContentAccess.value ||
                    runtime.state.value.databaseGeneration != generation
                ) return@launch
                updateSetup(
                    FocusSetupEvent.TaskSelectionLoaded(
                        taskId = taskId,
                        requestId = requestId,
                        title = task?.title,
                        isOpen = task != null && !task.isCompleted,
                    ),
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (privateContentAccess.value && taskContentAccess.value &&
                    runtime.state.value.databaseGeneration == generation
                ) {
                    updateSetup(FocusSetupEvent.TaskSelectionLoadFailed(taskId, requestId))
                }
            }
        }
    }

    fun changeName(value: String) = updateSetup(FocusSetupEvent.NameChanged(value))
    fun blurName() = updateSetup(FocusSetupEvent.NameBlurred)
    fun changeTag(value: String) = updateSetup(FocusSetupEvent.TagChanged(value))
    fun blurTag() = updateSetup(FocusSetupEvent.TagBlurred)
    fun setTagAreaExpanded(expanded: Boolean) =
        updateSetup(FocusSetupEvent.TagAreaExpandedChanged(expanded))
    fun changeTimerMode(value: FocusTimerMode) = updateSetup(FocusSetupEvent.TimerModeChanged(value))
    fun changeTimedMinutes(value: String) = updateSetup(FocusSetupEvent.TimedMinutesChanged(value))
    fun blurTimedMinutes() = updateSetup(FocusSetupEvent.TimedMinutesBlurred)
    fun attemptStart() = updateSetup(FocusSetupEvent.StartAttempted)
    fun startAnother() {
        if (local.value.busy) return
        pendingShortcutPackage = null
        local.update {
            it.copy(
                operationFailed = false,
                operationFailure = null,
                shortcutUnavailable = false,
                shortcutLabel = null,
            )
        }
        updateSetup(FocusSetupEvent.StartAnotherRequested)
    }

    fun dismissMissingTerminalSession(sessionId: UUID, generation: UUID): Boolean {
        if (!privateContentAccess.value) return false
        return runtime.dismissMissingTerminalSession(sessionId, generation)
    }

    fun startBreak(durationMinutes: Int) {
        if (local.value.breakBusy) return
        local.update { it.copy(breakBusy = true, breakOperationFailed = false) }
        viewModelScope.launch {
            val saved = runtime.startBreak(durationMinutes)
            local.update { it.copy(breakBusy = false, breakOperationFailed = !saved) }
        }
    }

    fun endBreak() {
        if (local.value.breakBusy) return
        local.update { it.copy(breakBusy = true, breakOperationFailed = false) }
        viewModelScope.launch {
            val ended = runtime.endBreak()
            local.update { it.copy(breakBusy = false, breakOperationFailed = !ended) }
        }
    }

    /** Reloads a terminal record under the current Focus grant before creating a repeat draft. */
    fun repeatSession(sessionId: UUID) {
        val focusRepository = repository ?: return
        if (!privateContentAccess.value || local.value.busy || local.value.setup.repeatLoading ||
            pendingOperation is PendingOperation.Start
        ) return
        val generation = runtime.state.value.databaseGeneration ?: return
        if (runtime.state.value.session?.state in setOf(FocusState.RUNNING, FocusState.PAUSED)) return
        val selectedRecentSource = (recentWorkState.value as? FocusRecentWorkState.Ready)
            ?.takeIf {
                it.session.id == sessionId && it.request.generation == generation &&
                    it.request.includeTaskOrigin == taskContentAccess.value
            }
        val requestId = UUID.randomUUID()
        updateSetup(FocusSetupEvent.RepeatRequested(sessionId, requestId))
        viewModelScope.launch {
            try {
                val latestGeneration = focusRepository.currentGeneration()
                if (latestGeneration != generation) {
                    updateSetup(FocusSetupEvent.GenerationChanged(latestGeneration))
                    return@launch
                }
                val source = focusRepository.terminalSession(sessionId)
                val generationAfterRead = focusRepository.currentGeneration()
                if (generationAfterRead != generation) {
                    updateSetup(FocusSetupEvent.GenerationChanged(generationAfterRead))
                    return@launch
                }
                if (!privateContentAccess.value || runtime.state.value.databaseGeneration != generation) {
                    return@launch
                }
                if (source?.taskOrigin == true && !taskContentAccess.value) {
                    updateSetup(FocusSetupEvent.RepeatLoadFailed(requestId))
                    return@launch
                }
                if (runtime.state.value.session?.state in setOf(FocusState.RUNNING, FocusState.PAUSED)) {
                    updateSetup(FocusSetupEvent.RepeatLoadFailed(requestId))
                    return@launch
                }
                if (source == null || !source.isTerminal) {
                    val missingRecentSource = selectedRecentSource?.takeIf {
                        privateContentAccess.value && runtime.state.value.databaseGeneration == generation &&
                            taskContentAccess.value == it.request.includeTaskOrigin &&
                            (!it.session.taskOrigin || taskContentAccess.value)
                    }
                    if (missingRecentSource != null) {
                        recentWorkState.value = FocusRecentWorkState.Missing(missingRecentSource.request, sessionId)
                        updateSetup(FocusSetupEvent.RepeatSourceMissing(requestId))
                    } else {
                        updateSetup(FocusSetupEvent.RepeatLoadFailed(requestId))
                    }
                    return@launch
                }

                var copiedTaskId = source.taskId
                var copiedTaskTitle: String? = null
                val sourceTaskId = source.taskId
                if (sourceTaskId != null) {
                    if (!source.taskOrigin || !taskContentAccess.value) {
                        updateSetup(FocusSetupEvent.RepeatLoadFailed(requestId))
                        return@launch
                    }
                    val task = todos?.get(sourceTaskId)
                    val generationAfterTaskRead = focusRepository.currentGeneration()
                    if (generationAfterTaskRead != generation) {
                        updateSetup(FocusSetupEvent.GenerationChanged(generationAfterTaskRead))
                        return@launch
                    }
                    if (!privateContentAccess.value || !taskContentAccess.value ||
                        runtime.state.value.databaseGeneration != generation
                    ) return@launch
                    if (task == null || task.isCompleted) {
                        copiedTaskId = null
                    } else {
                        copiedTaskTitle = task.title
                    }
                }

                updateSetup(
                    FocusSetupEvent.PrefillRequested(
                        sessionId = sessionId,
                        requestId = requestId,
                        input = FocusStartInput(
                            source.metadata.name.orEmpty(),
                            source.metadata.tag?.name,
                            source.timer,
                            taskId = copiedTaskId,
                            taskOrigin = source.taskOrigin,
                        ),
                        nextStep = source.reflection.nextStep,
                        generation = generation,
                        taskTitle = copiedTaskTitle,
                    ),
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (!privateContentAccess.value || runtime.state.value.databaseGeneration != generation) {
                    return@launch
                }
                updateSetup(FocusSetupEvent.RepeatLoadFailed(requestId))
            }
        }
    }

    fun changePeriod(value: FocusPeriod) = updateSetup(FocusSetupEvent.PeriodChanged(value))
    fun updateViewingContext(date: LocalDate, zone: ZoneId) = updateSetup(
        FocusSetupEvent.ViewingContextChanged(date, zone.id),
    )
    fun retryInsights() { insightRetry.update { it + 1 } }
    fun retryRecentWork() {
        if (recentWorkState.value is FocusRecentWorkState.Missing) {
            recentWorkState.value = FocusRecentWorkState.Hidden
        }
        recentWorkRetry.update { it + 1 }
    }

    private fun updateSetup(event: FocusSetupEvent) {
        val next = reduceFocusSetupState(local.value.setup, event)
        local.update { it.copy(setup = next) }
        encodeFocusSetupState(next).forEach { (key, value) -> savedStateHandle[key] = value }
    }

    fun start(input: FocusStartInput) {
        if (local.value.busy || pendingOperation is PendingOperation.Start) return
        val normalized = runCatching { normalizeFocusStartInput(input) }.getOrElse {
            updateSetup(FocusSetupEvent.StartValidationRejected)
            return
        }
        if (!local.value.setup.canStart) return
        if (local.value.setup.normalizedInput != normalized) return
        if (local.value.setup.taskOrigin && (!privateContentAccess.value || !taskContentAccess.value)) return
        val attempt = runtime.captureUserOperationAttempt() ?: return reportUnavailableAttempt()
        updateSetup(FocusSetupEvent.StartCaptured(normalized))
        if (local.value.setup.capturedStart != normalized) return
        submit(PendingOperation.Start(UUID.randomUUID(), UUID.randomUUID(), normalized, attempt))
    }

    fun act(action: FocusAction, onSuccess: (() -> Unit)? = null) {
        if (local.value.busy) return
        val session = runtime.state.value.session ?: return
        val attempt = runtime.captureUserOperationAttempt() ?: return reportUnavailableAttempt()
        submit(PendingOperation.Action(UUID.randomUUID(), session, action, attempt), onSuccess)
    }

    fun resolveRecovery(choice: FocusRecoveryChoice) {
        if (local.value.busy) return
        val session = runtime.state.value.session ?: return
        val attempt = runtime.captureUserOperationAttempt() ?: return reportUnavailableAttempt()
        submit(PendingOperation.Recovery(session, choice, attempt))
    }

    fun retry() {
        val pending = pendingOperation
        if (pending != null) {
            submit(pending)
            return
        }
        val shortcutPackage = pendingShortcutPackage
        if (shortcutPackage != null) {
            if (runtime.state.value.session?.state == com.luma.lifeos.domain.focus.FocusState.RUNNING) {
                launchSelectedApp(shortcutPackage)
            } else {
                pendingShortcutPackage = null
                local.update {
                    it.copy(shortcutUnavailable = false, shortcutLabel = null)
                }
            }
            return
        }
        if (local.value.busy) return
        viewModelScope.launch {
            local.update {
                it.copy(busy = true, operationFailed = false, operationFailure = null)
            }
            try {
                runtime.checkpointNow()
                val failed = runtime.state.value.checkpointFailed
                local.update {
                    it.copy(
                        busy = false,
                        operationFailed = failed,
                        operationFailure = if (failed) FocusOperationFailure.CHECKPOINT else null,
                    )
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update {
                    it.copy(
                        busy = false,
                        operationFailed = true,
                        operationFailure = FocusOperationFailure.CHECKPOINT,
                    )
                }
            }
        }
    }

    fun retryInitialization() = runtime.retryInitialization()

    private fun submit(operation: PendingOperation, onSuccess: (() -> Unit)? = null) {
        if (local.value.busy) return
        pendingOperation = operation
        local.update { it.copy(busy = true, operationFailed = false, operationFailure = null) }
        viewModelScope.launch {
            try {
                val result = when (operation) {
                    is PendingOperation.Start -> runtime.start(
                        operation.commandId, operation.sessionId, operation.input, operation.attempt,
                    )
                    is PendingOperation.Action -> runtime.act(
                        operation.commandId, operation.session, operation.action, operation.attempt,
                    )
                    is PendingOperation.Recovery -> runtime.resolveRecovery(
                        operation.session, operation.choice, operation.attempt,
                    )
                }
                when (result) {
                    is FocusWriteResult.Conflict -> {
                        pendingOperation = null
                        if (operation is PendingOperation.Start) {
                            updateSetup(
                                if (result.reason == com.luma.lifeos.domain.focus.FocusConflictReason.TASK_SELECTION) {
                                    FocusSetupEvent.TaskSelectionConflict
                                } else {
                                    FocusSetupEvent.StartOutcomeReconciled
                                },
                            )
                        }
                        local.update {
                            it.copy(busy = false, operationFailed = false, operationFailure = null)
                        }
                    }
                    is FocusWriteResult.Applied,
                    is FocusWriteResult.AlreadyApplied,
                    is FocusWriteResult.RecoveryRequired -> {
                        pendingOperation = null
                        if (operation is PendingOperation.Start) {
                            updateSetup(
                                if (result is FocusWriteResult.Applied || result is FocusWriteResult.AlreadyApplied) {
                                    FocusSetupEvent.StartSucceeded
                                } else {
                                    FocusSetupEvent.StartOutcomeReconciled
                                },
                            )
                        }
                        local.update {
                            it.copy(
                                busy = false,
                                operationFailed = false,
                                operationFailure = null,
                                shortcutUnavailable = if (operation is PendingOperation.Action &&
                                    operation.action in setOf(FocusAction.PAUSE, FocusAction.STOP)
                                ) false else it.shortcutUnavailable,
                                shortcutLabel = if (operation is PendingOperation.Action &&
                                    operation.action in setOf(FocusAction.PAUSE, FocusAction.STOP)
                                ) null else it.shortcutLabel,
                            )
                        }
                        if (operation is PendingOperation.Action &&
                            operation.action in setOf(FocusAction.PAUSE, FocusAction.STOP)
                        ) pendingShortcutPackage = null
                        if (result is FocusWriteResult.Applied || result is FocusWriteResult.AlreadyApplied) {
                            onSuccess?.invoke()
                        }
                    }
                    is FocusWriteResult.Invalid -> {
                        if (operation is PendingOperation.Start) {
                            pendingOperation = null
                            val metadataInvalid = result.reason == FocusInvalidReason.INVALID_METADATA
                            updateSetup(
                                if (metadataInvalid) FocusSetupEvent.StartValidationRejected
                                else FocusSetupEvent.StartOutcomeReconciled,
                            )
                            local.update {
                                it.copy(busy = false, operationFailed = false, operationFailure = null)
                            }
                        } else {
                            local.update {
                                it.copy(
                                    busy = false,
                                    operationFailed = true,
                                    operationFailure = operation.failureKind(),
                                )
                            }
                        }
                    }
                    is FocusWriteResult.Unavailable -> {
                        if (operation is PendingOperation.Start) updateSetup(FocusSetupEvent.StartIoFailed)
                        local.update {
                            it.copy(
                                busy = false,
                                operationFailed = true,
                                operationFailure = operation.failureKind(),
                            )
                        }
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (operation is PendingOperation.Start) updateSetup(FocusSetupEvent.StartIoFailed)
                local.update {
                    it.copy(
                        busy = false,
                        operationFailed = true,
                        operationFailure = operation.failureKind(),
                    )
                }
            }
        }
    }

    private fun PendingOperation.failureKind(): FocusOperationFailure = when (this) {
        is PendingOperation.Start -> FocusOperationFailure.START
        is PendingOperation.Action -> when (action) {
            FocusAction.PAUSE -> FocusOperationFailure.PAUSE
            FocusAction.RESUME -> FocusOperationFailure.RESUME
            FocusAction.STOP -> FocusOperationFailure.END
        }
        is PendingOperation.Recovery -> FocusOperationFailure.RECOVERY
    }

    private fun reportUnavailableAttempt() {
        local.update {
            it.copy(operationFailed = true, operationFailure = FocusOperationFailure.UNKNOWN)
        }
    }

    fun showBackPrompt() = local.update { it.copy(backPromptVisible = true) }
    fun dismissBackPrompt() = local.update { it.copy(backPromptVisible = false) }

    fun requestEmergencyExit() {
        viewModelScope.launch {
            val session = runtime.state.value.session
            val saved = session?.let { runtime.markEmergencyExit(it.id) } ?: false
            local.update {
                it.copy(emergencyPromptVisible = true, emergencyMarkerSaved = saved)
            }
        }
    }

    fun dismissEmergencyExit() {
        viewModelScope.launch {
            val runtimeState = runtime.state.value
            val sessionId = runtimeState.session?.id
            if (runtimeState.emergencyMarkerSaved == true && sessionId != null) {
                val cleared = runtime.cancelEmergencyExit(sessionId)
                if (!cleared) return@launch
            }
            local.update { it.copy(emergencyPromptVisible = false, emergencyMarkerSaved = null) }
        }
    }

    fun confirmEmergencyExit() {
        val sessionId = runtime.state.value.session?.id
        if (sessionId == null) runtime.releaseEmergencyExitWithoutSession()
        else runtime.releaseEmergencyExit(sessionId)
        local.update { it.copy(emergencyPromptVisible = false) }
    }

    fun launchSelectedApp(packageName: String, displayName: String? = null) {
        if (local.value.busy) return
        pendingShortcutPackage = packageName
        viewModelScope.launch {
            local.update {
                it.copy(
                    busy = true,
                    shortcutUnavailable = false,
                    shortcutLabel = displayName ?: it.shortcutLabel,
                )
            }
            try {
                when (runtime.launchSelectedApp(packageName)) {
                    FocusShortcutResult.LAUNCHED -> {
                        pendingShortcutPackage = null
                        local.update {
                            it.copy(shortcutUnavailable = false, shortcutLabel = null)
                        }
                    }
                    FocusShortcutResult.NOT_SELECTED,
                    FocusShortcutResult.UNAVAILABLE,
                    FocusShortcutResult.CHECKPOINT_FAILED -> local.update {
                        it.copy(shortcutUnavailable = true)
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update { it.copy(shortcutUnavailable = true) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val KEY_TASK_UNDO_RESULT = "focus_task_undo_result"
        const val KEY_TASK_COMPLETION_COMMAND = "focus_task_completion_command"
        fun restoreSetupState(savedStateHandle: SavedStateHandle): FocusSetupState = restoreFocusSetupState(
            saved = savedStateHandle.keys().associateWith { key -> savedStateHandle.get<Any?>(key) },
            defaultZoneId = ZoneId.systemDefault().id,
            today = LocalDate.now(ZoneId.systemDefault()),
        )
    }
}
