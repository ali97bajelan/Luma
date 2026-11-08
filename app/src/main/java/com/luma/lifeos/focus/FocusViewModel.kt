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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusScreenState(
    val runtime: FocusRuntimeState,
    val busy: Boolean = false,
    val breakBusy: Boolean = false,
    val breakOperationFailed: Boolean = false,
    val operationFailed: Boolean = false,
    val backPromptVisible: Boolean = false,
    val emergencyPromptVisible: Boolean = false,
    val shortcutUnavailable: Boolean = false,
    val setup: FocusSetupState = FocusSetupState(),
    val insights: FocusInsightsState = FocusInsightsState.Unselected,
    val tagSuggestions: List<FocusTag> = emptyList(),
    val focusContentReadable: Boolean = false,
    val taskOriginSelected: Boolean = false,
    val taskActionsAllowed: Boolean = false,
    val taskCompletion: FocusTaskCompletionState = FocusTaskCompletionState(),
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

    private data class LocalState(
        val busy: Boolean = false,
        val breakBusy: Boolean = false,
        val breakOperationFailed: Boolean = false,
        val operationFailed: Boolean = false,
        val backPromptVisible: Boolean = false,
        val emergencyPromptVisible: Boolean = false,
        val emergencyMarkerSaved: Boolean? = null,
        val shortcutUnavailable: Boolean = false,
        val setup: FocusSetupState,
        val taskCompletion: FocusTaskCompletionState = FocusTaskCompletionState(),
    )

    private val local = MutableStateFlow(LocalState(setup = restoreSetupState(savedStateHandle)))
    private val insightState = MutableStateFlow<FocusInsightsState>(FocusInsightsState.Unselected)
    private val tagSuggestions = MutableStateFlow(FocusTagSuggestionsSnapshot(null, emptyList()))
    private val privateContentAccess = MutableStateFlow(false)
    private val taskContentAccess = MutableStateFlow(false)
    private val taskActionAccess = MutableStateFlow(false)
    private var privateContentAccessResolved = false
    private var taskContentAccessResolved = false
    private val insightRetry = MutableStateFlow(0)
    private var pendingOperation: PendingOperation? = null
    private var pendingShortcutPackage: String? = null

    private val contentAccess = combine(
        privateContentAccess,
        taskContentAccess,
        taskActionAccess,
    ) { canReadFocus, canReadTasks, canManageLinkedTaskActions ->
        Triple(canReadFocus, canReadTasks, canManageLinkedTaskActions)
    }

    val state: StateFlow<FocusScreenState> = combine(
        runtime.state, local, insightState, tagSuggestions, contentAccess,
    ) { runtimeState, localState, insights, suggestions, access ->
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
            backPromptVisible = localState.backPromptVisible,
            emergencyPromptVisible = localState.emergencyPromptVisible,
            shortcutUnavailable = localState.shortcutUnavailable || runtimeState.shortcutFailed,
            setup = if (canReadFocusSurface && (!localState.setup.taskOrigin || canReadTasks)) {
                localState.setup
            } else FocusSetupState(),
            insights = presentation.state,
            tagSuggestions = presentation.tagSuggestions,
            focusContentReadable = canReadFocusSurface,
            taskOriginSelected = localState.setup.taskOrigin || runtimeState.session?.taskOrigin == true,
            taskActionsAllowed = canReadFocus && canReadTasks && canManageLinkedTaskActions,
            taskCompletion = localState.taskCompletion,
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
            taskCompletion = local.value.taskCompletion,
        ),
    )

    init {
        viewModelScope.launch {
            runtime.state.map { it.databaseGeneration }.filterNotNull().distinctUntilChanged().collect { generation ->
                val previous = local.value.setup.databaseGeneration
                if (previous != null && previous != generation) {
                    pendingOperation = null
                    local.update { it.copy(operationFailed = false, taskCompletion = FocusTaskCompletionState()) }
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
    }

    fun setPrivateContentAccessAllowed(allowed: Boolean) {
        if (privateContentAccessResolved && privateContentAccess.value == allowed) return
        privateContentAccessResolved = true
        privateContentAccess.value = allowed
        if (!allowed) {
            updateSetup(FocusSetupEvent.PrivateContentLocked)
            clearTaskCompletionState()
            insightState.value = FocusInsightsState.Unselected
            tagSuggestions.value = FocusTagSuggestionsSnapshot(null, emptyList())
        } else {
            reloadSelectedTaskIfNeeded()
        }
    }

    fun setTaskContentAccessAllowed(allowed: Boolean) {
        if (taskContentAccessResolved && taskContentAccess.value == allowed) return
        taskContentAccessResolved = true
        taskContentAccess.value = allowed
        if (!allowed) {
            val repeatRequestId = local.value.setup.repeatRequestId
            updateSetup(FocusSetupEvent.TaskContentLocked)
            if (repeatRequestId != null) updateSetup(FocusSetupEvent.RepeatLoadFailed(repeatRequestId))
            insightState.value = FocusInsightsState.Unselected
            tagSuggestions.value = FocusTagSuggestionsSnapshot(null, emptyList())
            clearTaskCompletionState()
        } else {
            reloadSelectedTaskIfNeeded()
        }
    }

    fun setTaskActionAccessAllowed(allowed: Boolean) {
        if (taskActionAccess.value == allowed) return
        taskActionAccess.value = allowed
        if (!allowed) clearTaskCompletionState()
    }

    fun syncTaskCompletionSession(
        sessionId: UUID,
        sessionRevision: Long,
        taskId: UUID?,
        generation: UUID?,
    ) {
        val current = local.value.taskCompletion
        if (!current.matches(sessionId, sessionRevision, taskId, generation)) {
            local.update {
                it.copy(
                    taskCompletion = FocusTaskCompletionState(
                        sessionId = sessionId,
                        sessionRevision = sessionRevision,
                        taskId = taskId,
                        generation = generation,
                    ),
                )
            }
        }
    }

    fun markTaskComplete(sessionId: UUID, sessionRevision: Long, taskId: UUID, generation: UUID) {
        if (!canPerformTaskActions(generation) || todos == null || repository == null) return
        syncTaskCompletionSession(sessionId, sessionRevision, taskId, generation)
        val current = local.value.taskCompletion
        if (current.completionPhase == FocusTaskCompletionPhase.LOADING ||
            current.completionPhase == FocusTaskCompletionPhase.SAVING
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
                    setTaskCompletionPhase(FocusTaskCompletionPhase.CONFLICT, clearPending = true)
                    return@launch
                }
                val task = todos.get(taskId)
                if (!canPerformTaskActions(generation)) {
                    clearTaskCompletionState()
                    return@launch
                }
                if (task == null) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.MISSING, clearPending = true)
                } else if (task.isCompleted) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.COMPLETED, clearPending = true)
                } else {
                    val pending = PendingFocusTaskCompletion(
                        taskId = taskId,
                        expectedRevision = task.revision,
                        nextRevision = UUID.randomUUID(),
                        generation = generation,
                    )
                    local.update {
                        it.copy(taskCompletion = it.taskCompletion.copy(
                            completionPhase = FocusTaskCompletionPhase.SAVING,
                            pendingCompletion = pending,
                        ))
                    }
                    persistTaskCompletion(sessionId, sessionRevision, pending)
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (canPerformTaskActions(generation)) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.ERROR, clearPending = true)
                } else clearTaskCompletionState()
            }
        }
    }

    fun retryTaskCompletion(sessionId: UUID, sessionRevision: Long, taskId: UUID, generation: UUID) {
        val state = local.value.taskCompletion
        val pending = state.pendingCompletion
        if (state.matches(sessionId, sessionRevision, taskId, generation) && pending != null) {
            if (state.completionPhase == FocusTaskCompletionPhase.SAVING) return
            local.update {
                it.copy(taskCompletion = it.taskCompletion.copy(completionPhase = FocusTaskCompletionPhase.SAVING))
            }
            viewModelScope.launch { persistTaskCompletion(sessionId, sessionRevision, pending) }
        } else {
            markTaskComplete(sessionId, sessionRevision, taskId, generation)
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

    private suspend fun persistTaskCompletion(
        sessionId: UUID,
        sessionRevision: Long,
        pending: PendingFocusTaskCompletion,
    ) {
        val taskRepository = todos ?: return
        try {
            if (!canPerformTaskActions(pending.generation)) {
                clearTaskCompletionState()
                return
            }
            val linkedSessionIsCurrent = isCurrentTaskLinkedSession(
                sessionId, sessionRevision, pending.taskId, pending.generation,
            )
            if (!canPerformTaskActions(pending.generation)) {
                clearTaskCompletionState()
                return
            }
            if (!linkedSessionIsCurrent) {
                setTaskCompletionPhase(FocusTaskCompletionPhase.CONFLICT, clearPending = true)
                return
            }
            taskRepository.setCompleted(
                pending.taskId,
                pending.expectedRevision,
                pending.nextRevision,
                completed = true,
            )
            if (!canPerformTaskActions(pending.generation)) {
                clearTaskCompletionState()
                return
            }
            local.update {
                it.copy(taskCompletion = it.taskCompletion.copy(
                    completionPhase = FocusTaskCompletionPhase.COMPLETED,
                    pendingCompletion = null,
                ))
            }
        } catch (_: TodoMissingException) {
            if (canPerformTaskActions(pending.generation)) {
                setTaskCompletionPhase(FocusTaskCompletionPhase.MISSING, clearPending = true)
            } else clearTaskCompletionState()
        } catch (_: TodoConflictException) {
            if (!canPerformTaskActions(pending.generation)) {
                clearTaskCompletionState()
                return
            }
            val latest = try {
                taskRepository.get(pending.taskId)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (canPerformTaskActions(pending.generation)) {
                    setTaskCompletionPhase(FocusTaskCompletionPhase.ERROR, clearPending = true)
                } else clearTaskCompletionState()
                return
            }
            if (!canPerformTaskActions(pending.generation)) {
                clearTaskCompletionState()
            } else {
                setTaskCompletionPhase(
                    when {
                        latest == null -> FocusTaskCompletionPhase.MISSING
                        latest.isCompleted -> FocusTaskCompletionPhase.COMPLETED
                        else -> FocusTaskCompletionPhase.CONFLICT
                    },
                    clearPending = true,
                )
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (canPerformTaskActions(pending.generation)) {
                setTaskCompletionPhase(FocusTaskCompletionPhase.ERROR, clearPending = false)
            } else clearTaskCompletionState()
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

    private fun canPerformTaskActions(generation: UUID): Boolean =
        privateContentAccess.value && taskContentAccess.value && taskActionAccess.value &&
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
        local.update { it.copy(taskCompletion = FocusTaskCompletionState()) }
    }


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
    fun startAnother() = updateSetup(FocusSetupEvent.StartAnotherRequested)

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
                if (!privateContentAccess.value || runtime.state.value.databaseGeneration != generation ||
                    (source == null && !taskContentAccess.value) ||
                    (source?.taskOrigin == true && !taskContentAccess.value)
                ) {
                    return@launch
                }
                if (runtime.state.value.session?.state in setOf(FocusState.RUNNING, FocusState.PAUSED)) {
                    updateSetup(FocusSetupEvent.RepeatLoadFailed(requestId))
                    return@launch
                }
                if (source == null || !source.isTerminal) {
                    updateSetup(FocusSetupEvent.RepeatLoadFailed(requestId))
                    return@launch
                }
                updateSetup(
                    FocusSetupEvent.PrefillRequested(
                        sessionId = sessionId,
                        requestId = requestId,
                        input = FocusStartInput(
                            source.metadata.name.orEmpty(),
                            source.metadata.tag?.name,
                            source.timer,
                            taskId = source.taskId,
                            taskOrigin = source.taskOrigin,
                        ),
                        nextStep = source.reflection.nextStep,
                        generation = generation,
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

    private fun updateSetup(event: FocusSetupEvent) {
        val next = reduceFocusSetupState(local.value.setup, event)
        local.update { it.copy(setup = next) }
        encodeFocusSetupState(next).forEach { (key, value) -> savedStateHandle[key] = value }
    }

    fun start(input: FocusStartInput) {
        if (pendingOperation is PendingOperation.Start) return
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
        val session = runtime.state.value.session ?: return
        val attempt = runtime.captureUserOperationAttempt() ?: return reportUnavailableAttempt()
        submit(PendingOperation.Action(UUID.randomUUID(), session, action, attempt), onSuccess)
    }

    fun resolveRecovery(choice: FocusRecoveryChoice) {
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
                local.update { it.copy(shortcutUnavailable = false) }
            }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(busy = true, operationFailed = false) }
            try {
                runtime.checkpointNow()
                local.update { it.copy(busy = false, operationFailed = runtime.state.value.checkpointFailed) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update { it.copy(busy = false, operationFailed = true) }
            }
        }
    }

    fun retryInitialization() = runtime.retryInitialization()

    private fun submit(operation: PendingOperation, onSuccess: (() -> Unit)? = null) {
        pendingOperation = operation
        local.update { it.copy(busy = true, operationFailed = false) }
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
                        local.update { it.copy(busy = false, operationFailed = false) }
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
                                shortcutUnavailable = if (operation is PendingOperation.Action &&
                                    operation.action in setOf(FocusAction.PAUSE, FocusAction.STOP)
                                ) false else it.shortcutUnavailable,
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
                        pendingOperation = null
                        if (operation is PendingOperation.Start) {
                            val metadataInvalid = result.reason == FocusInvalidReason.INVALID_METADATA
                            updateSetup(
                                if (metadataInvalid) FocusSetupEvent.StartValidationRejected
                                else FocusSetupEvent.StartOutcomeReconciled,
                            )
                            local.update { it.copy(busy = false, operationFailed = false) }
                        } else {
                            local.update { it.copy(busy = false, operationFailed = true) }
                        }
                    }
                    is FocusWriteResult.Unavailable -> {
                        if (operation is PendingOperation.Start) updateSetup(FocusSetupEvent.StartIoFailed)
                        local.update { it.copy(busy = false, operationFailed = true) }
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (operation is PendingOperation.Start) updateSetup(FocusSetupEvent.StartIoFailed)
                local.update { it.copy(busy = false, operationFailed = true) }
            }
        }
    }

    private fun reportUnavailableAttempt() {
        local.update { it.copy(operationFailed = true) }
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

    fun launchSelectedApp(packageName: String) {
        pendingShortcutPackage = packageName
        viewModelScope.launch {
            local.update { it.copy(busy = true, shortcutUnavailable = false) }
            try {
                when (runtime.launchSelectedApp(packageName)) {
                    FocusShortcutResult.LAUNCHED -> {
                        pendingShortcutPackage = null
                        local.update { it.copy(shortcutUnavailable = false) }
                    }
                    FocusShortcutResult.NOT_SELECTED,
                    FocusShortcutResult.UNAVAILABLE,
                    FocusShortcutResult.CHECKPOINT_FAILED -> local.update { it.copy(shortcutUnavailable = true) }
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
        fun restoreSetupState(savedStateHandle: SavedStateHandle): FocusSetupState = restoreFocusSetupState(
            saved = savedStateHandle.keys().associateWith { key -> savedStateHandle.get<Any?>(key) },
            defaultZoneId = ZoneId.systemDefault().id,
            today = LocalDate.now(ZoneId.systemDefault()),
        )
    }
}
