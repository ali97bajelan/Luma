package com.luma.lifeos.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.focus.FOCUS_HISTORY_PAGE_SIZE
import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusTagFilter
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.time.TimeRange
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface FocusHistoryTargetSessionState {
    val sessionId: UUID?

    data object Hidden : FocusHistoryTargetSessionState {
        override val sessionId: UUID? = null
    }
    data class Loading(override val sessionId: UUID) : FocusHistoryTargetSessionState
    data class NeedsUnlock(override val sessionId: UUID) : FocusHistoryTargetSessionState
    data class Missing(override val sessionId: UUID) : FocusHistoryTargetSessionState
    data class Ready(
        override val sessionId: UUID,
        val session: FocusSession,
        val generation: UUID,
    ) : FocusHistoryTargetSessionState
    data class Error(override val sessionId: UUID) : FocusHistoryTargetSessionState
}

/** Loads bounded history pages and deletes only terminal rows through the repository contract. */
class FocusHistoryViewModel(
    private val repository: FocusRepository,
    private val range: TimeRange?,
    private val savedStateHandle: SavedStateHandle,
    private val processInstanceId: UUID = UUID.randomUUID(),
    private var privateContentAccessAllowed: Boolean = true,
    private val privateContentAccessFlow: Flow<Boolean>? = null,
    private val taskContentAccessFlow: Flow<Boolean>? = null,
    private var taskContentAccessAllowed: Boolean = true,
    private val databaseGenerationFlow: Flow<UUID?>? = null,
    private val productRepository: FocusProductRepository? = null,
    private val requestedSessionId: UUID? = null,
) : ViewModel() {
    private var restoredFilters = if (taskContentAccessAllowed) restoreFocusHistoryFilterState(
        search = savedStateHandle[SEARCH_KEY],
        tagKind = savedStateHandle[TAG_KIND_KEY],
        tagId = savedStateHandle[TAG_ID_KEY],
        offset = savedStateHandle[PAGE_OFFSET_KEY],
    ) else FocusHistoryFilterState()
    private val mutableState = MutableStateFlow(
        focusHistoryStateForAccess(FocusHistoryUiState(filters = restoredFilters), privateContentAccessAllowed),
    )
    val state: StateFlow<FocusHistoryUiState> = mutableState.asStateFlow()
    private val mutableTargetSessionState = MutableStateFlow(
        when {
            requestedSessionId == null -> FocusHistoryTargetSessionState.Hidden
            privateContentAccessAllowed -> FocusHistoryTargetSessionState.Loading(requestedSessionId)
            else -> FocusHistoryTargetSessionState.NeedsUnlock(requestedSessionId)
        },
    )
    val targetSessionState: StateFlow<FocusHistoryTargetSessionState> =
        mutableTargetSessionState.asStateFlow()

    private var targetSessionJob: Job? = null
    private var targetSessionRequest = 0L
    private var loadJob: Job? = null
    private var tagsJob: Job? = null
    private var loadRequest = 0L
    private var activeLoadKey: FocusHistoryLoadKey? = null
    private var knownGeneration: UUID? = savedStateHandle[GENERATION_KEY]
    private var observedRuntimeGeneration: UUID? = null
    private var runtimeGenerationObserved = false

    init {
        privateContentAccessFlow?.let { accessFlow ->
            viewModelScope.launch {
                accessFlow.distinctUntilChanged().collect(::setPrivateContentAccessAllowed)
            }
        }
        taskContentAccessFlow?.let { accessFlow ->
            viewModelScope.launch {
                accessFlow.distinctUntilChanged().collect(::setTaskContentAccessAllowed)
            }
        }
        databaseGenerationFlow?.let { generations ->
            viewModelScope.launch {
                generations.distinctUntilChanged().collect(::onRuntimeGenerationChanged)
            }
        }
        if (!taskContentAccessAllowed) persistFilters(mutableState.value.filters)
        if (requestedSessionId != null) {
            if (privateContentAccessAllowed) loadTargetSession()
        } else if (privateContentAccessAllowed) {
            observeTags()
            load(restoredFilters.offset)
        } else {
            restoredFilters = FocusHistoryFilterState()
            persistFilters(mutableState.value.filters)
        }
    }

    private fun onRuntimeGenerationChanged(generation: UUID?) {
        val changed = if (!runtimeGenerationObserved) {
            runtimeGenerationObserved = true
            observedRuntimeGeneration = generation
            if (knownGeneration == null && generation != null) knownGeneration = generation
            knownGeneration != null && knownGeneration != generation
        } else if (observedRuntimeGeneration != generation) {
            observedRuntimeGeneration = generation
            true
        } else false
        if (changed) invalidateForDatabaseReplacement(generation)
    }

    private fun invalidateForDatabaseReplacement(generation: UUID?) {
        loadRequest++
        loadJob?.cancel()
        tagsJob?.cancel()
        activeLoadKey = null
        knownGeneration = generation
        if (generation == null) savedStateHandle.remove<String>(GENERATION_KEY)
        else savedStateHandle[GENERATION_KEY] = generation
        restoredFilters = FocusHistoryFilterState()
        val cleared = focusHistoryStateForDatabaseReplacement(mutableState.value)
        persistFilters(cleared.filters)
        mutableState.value = cleared
        targetSessionRequest++
        targetSessionJob?.cancel()
        if (requestedSessionId != null) {
            if (!privateContentAccessAllowed) {
                mutableTargetSessionState.value =
                    FocusHistoryTargetSessionState.NeedsUnlock(requestedSessionId)
            } else if (generation != null) {
                loadTargetSession()
            } else {
                mutableTargetSessionState.value =
                    FocusHistoryTargetSessionState.Loading(requestedSessionId)
            }
            return
        }
        if (privateContentAccessAllowed && generation != null) {
            observeTags()
            load(0)
        }
    }

    fun setPrivateContentAccessAllowed(allowed: Boolean) {
        if (privateContentAccessAllowed == allowed) return
        privateContentAccessAllowed = allowed
        if (!allowed) {
            loadRequest++
            loadJob?.cancel()
            tagsJob?.cancel()
            activeLoadKey = null
            val hidden = focusHistoryStateForAccess(mutableState.value, isFocusReadable = false)
            restoredFilters = FocusHistoryFilterState()
            persistFilters(hidden.filters)
            mutableState.value = hidden
            requestedSessionId?.let {
                targetSessionRequest++
                targetSessionJob?.cancel()
                mutableTargetSessionState.value = FocusHistoryTargetSessionState.NeedsUnlock(it)
            }
        } else if (requestedSessionId != null) {
            loadTargetSession()
        } else {
            observeTags()
            load(0)
        }
    }

    private fun observeTags() {
        tagsJob?.cancel()
        if (!privateContentAccessAllowed) return
        tagsJob = viewModelScope.launch {
            val products = productRepository
            if (products == null) {
                repository.observeTags()
                    .catch { emit(emptyList()) }
                    .collect { tags ->
                        if (privateContentAccessAllowed) mutableState.value = mutableState.value.copy(tags = tags)
                    }
            } else {
                products.observeTagCatalog()
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                    }
                    .collect { catalog ->
                        if (!privateContentAccessAllowed) return@collect
                        val current = mutableState.value
                        val selected = current.filters.tag as? FocusTagFilter.Tagged
                        val resolved = selected?.let { catalog.resolve(it.id) }
                        val filters = when {
                            selected != null && resolved != null && resolved.id != selected.id ->
                                current.filters.copy(tag = FocusTagFilter.Tagged(resolved.id), offset = 0)
                            selected != null && resolved == null && catalog.tags.none { it.id == selected.id } ->
                                current.filters.copy(tag = FocusTagFilter.All, offset = 0)
                            else -> current.filters
                        }
                        persistFilters(filters)
                        mutableState.value = current.copy(
                            tags = catalog.tags,
                            filters = filters,
                            tagFilterMergedTo = resolved?.takeIf { selected != null && it.id != selected.id },
                        )
                        if (filters != current.filters) load(0)
                    }
                }
        }
    }

    fun setTaskContentAccessAllowed(allowed: Boolean) {
        if (taskContentAccessAllowed == allowed) return
        taskContentAccessAllowed = allowed
        loadRequest++
        if (requestedSessionId != null) {
            targetSessionRequest++
            targetSessionJob?.cancel()
            mutableTargetSessionState.value =
                if (privateContentAccessAllowed) FocusHistoryTargetSessionState.Loading(requestedSessionId)
                else FocusHistoryTargetSessionState.NeedsUnlock(requestedSessionId)
        }
        loadJob?.cancel()
        tagsJob?.cancel()
        activeLoadKey = null
        restoredFilters = FocusHistoryFilterState()
        val cleared = focusHistoryStateForDatabaseReplacement(mutableState.value)
        persistFilters(cleared.filters)
        mutableState.value = cleared
        if (requestedSessionId != null) {
            if (privateContentAccessAllowed) loadTargetSession()
        } else if (privateContentAccessAllowed) {
            observeTags()
            load(0)
        }
    }

    fun retry() {
        if (!privateContentAccessAllowed) return
        if (requestedSessionId != null) loadTargetSession()
        else load(mutableState.value.filters.offset)
    }

    fun refresh() {
        if (!privateContentAccessAllowed) return
        if (requestedSessionId != null) loadTargetSession()
        else load(mutableState.value.filters.offset)
    }

    private fun loadTargetSession() {
        val sessionId = requestedSessionId ?: return
        targetSessionJob?.cancel()
        val request = ++targetSessionRequest
        if (!privateContentAccessAllowed) {
            mutableTargetSessionState.value = FocusHistoryTargetSessionState.NeedsUnlock(sessionId)
            return
        }
        mutableTargetSessionState.value = FocusHistoryTargetSessionState.Loading(sessionId)
        targetSessionJob = viewModelScope.launch {
            try {
                var attempts = 0
                while (true) {
                    if (request != targetSessionRequest || !privateContentAccessAllowed) return@launch
                    val generation = repository.currentGeneration()
                    val session = repository.terminalSession(sessionId)
                    val taskOriginWhenHidden = session?.taskOrigin
                        ?: if (!taskContentAccessAllowed) repository.sessionHasTaskOrigin(sessionId) else false
                    if (request != targetSessionRequest || !privateContentAccessAllowed) return@launch
                    val generationAfterRead = repository.currentGeneration()
                    if (generation != generationAfterRead) {
                        attempts = Math.incrementExact(attempts)
                        if (attempts >= MAX_GENERATION_RETRIES) {
                            error("Focus database changed repeatedly during target session load")
                        }
                        continue
                    }
                    knownGeneration = generation
                    savedStateHandle[GENERATION_KEY] = generation
                    val requiresTaskUnlock = !taskContentAccessAllowed && taskOriginWhenHidden
                    mutableTargetSessionState.value = when {
                        !privateContentAccessAllowed ->
                            FocusHistoryTargetSessionState.NeedsUnlock(sessionId)
                        requiresTaskUnlock -> FocusHistoryTargetSessionState.NeedsUnlock(sessionId)
                        session == null || session.id != sessionId || !session.isTerminal ->
                            FocusHistoryTargetSessionState.Missing(sessionId)
                        else -> FocusHistoryTargetSessionState.Ready(sessionId, session, generation)
                    }
                    return@launch
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (request == targetSessionRequest) {
                    mutableTargetSessionState.value = FocusHistoryTargetSessionState.Error(sessionId)
                }
            }
        }
    }

    fun search(value: String) {
        if (!privateContentAccessAllowed) return
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.SearchChanged(value))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters, tagFilterMergedTo = null)
        load(0)
    }

    fun tag(value: FocusTagFilter) {
        if (!privateContentAccessAllowed) return
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.TagChanged(value))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters, tagFilterMergedTo = null)
        load(0)
    }

    fun page(offset: Int) {
        if (!privateContentAccessAllowed) return
        require(offset >= 0 && offset % FOCUS_HISTORY_PAGE_SIZE == 0)
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.PageChanged(offset))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters)
        load(offset)
    }

    fun delete(row: FocusHistoryRow) {
        if (!privateContentAccessAllowed) return
        if (!row.session.isTerminal || mutableState.value.deletingSessionId != null) return
        val displayed = mutableState.value.page?.rows?.firstOrNull { it.session.id == row.session.id }?.session
        val generation = mutableState.value.databaseGeneration ?: return
        if (displayed?.revision != row.session.revision) return
        mutableState.value = mutableState.value.copy(
            deletingSessionId = row.session.id,
            deletionFailed = false,
        )
        viewModelScope.launch {
            try {
                val context = FocusWriteContext(
                    databaseGeneration = generation,
                    processInstanceId = processInstanceId,
                )
                when (repository.delete(row.session.id, row.session.revision, context)) {
                    is FocusWriteResult.Applied,
                    is FocusWriteResult.AlreadyApplied -> {
                        val offset = mutableState.value.filters.offset
                        mutableState.value = mutableState.value.copy(
                            deletingSessionId = null,
                            deletionFailed = false,
                        )
                        load(offset)
                    }
                    else -> mutableState.value = mutableState.value.copy(
                        deletingSessionId = null,
                        deletionFailed = true,
                    )
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = mutableState.value.copy(
                    deletingSessionId = null,
                    deletionFailed = true,
                )
            }
        }
    }

    private fun load(requestedOffset: Int) {
        if (!privateContentAccessAllowed) return
        loadJob?.cancel()
        val request = ++loadRequest
        val filters = mutableState.value.filters.copy(offset = requestedOffset)
        persistFilters(filters)
        activeLoadKey = null
        mutableState.value = mutableState.value.copy(
            filters = filters,
            loading = true,
            loadFailed = false,
            page = null,
            databaseGeneration = null,
            deletingSessionId = null,
            deletionFailed = false,
        )
        loadJob = viewModelScope.launch {
            try {
                var generation = repository.currentGeneration()
                if (knownGeneration != null && knownGeneration != generation) {
                    resetForDatabaseReplacement()
                }
                knownGeneration = generation
                var query: FocusHistoryQuery = mutableState.value.filters.query(range)
                var actualOffset = mutableState.value.filters.offset
                var page: com.luma.lifeos.domain.focus.FocusPage
                var attempts = 0
                while (true) {
                    val key = FocusHistoryLoadKey(query, actualOffset, generation, request)
                    activeLoadKey = key
                    page = repository.history(query, actualOffset, FOCUS_HISTORY_PAGE_SIZE)
                    val lastValidOffset = if (page.total == 0) 0
                    else ((page.total - 1) / FOCUS_HISTORY_PAGE_SIZE) * FOCUS_HISTORY_PAGE_SIZE
                    val clampedOffset = actualOffset.coerceAtMost(lastValidOffset)
                    if (clampedOffset != actualOffset) {
                        actualOffset = clampedOffset
                        activeLoadKey = FocusHistoryLoadKey(query, actualOffset, generation, request)
                        page = repository.history(query, actualOffset, FOCUS_HISTORY_PAGE_SIZE)
                    }
                    val afterReadGeneration = repository.currentGeneration()
                    if (generation == afterReadGeneration) break
                    generation = afterReadGeneration
                    resetForDatabaseReplacement()
                    knownGeneration = generation
                    query = mutableState.value.filters.query(range)
                    actualOffset = 0
                    attempts = Math.incrementExact(attempts)
                    if (attempts >= MAX_GENERATION_RETRIES) error("Focus database changed repeatedly during history load")
                }
                val key = FocusHistoryLoadKey(query, actualOffset, generation, request)
                if (request == loadRequest && activeLoadKey?.let { acceptsFocusHistoryResult(key, it) } == true) {
                    knownGeneration = generation
                    savedStateHandle[GENERATION_KEY] = generation
                    val loadedFilters = mutableState.value.filters.copy(offset = page.offset)
                    persistFilters(loadedFilters)
                    mutableState.value = mutableState.value.copy(
                        filters = loadedFilters,
                        loading = false,
                        loadFailed = false,
                        page = page,
                        databaseGeneration = generation,
                    )
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (request == loadRequest) {
                    mutableState.value = mutableState.value.copy(loading = false, loadFailed = true)
                }
            }
        }
    }

    private fun resetForDatabaseReplacement() {
        val reset = focusHistoryStateForDatabaseReplacement(mutableState.value)
        restoredFilters = FocusHistoryFilterState()
        persistFilters(reset.filters)
        mutableState.value = reset.copy(loading = true)
        observeTags()
    }

    private fun persistFilters(filters: FocusHistoryFilterState) {
        savedStateHandle[SEARCH_KEY] = filters.search
        savedStateHandle[PAGE_OFFSET_KEY] = filters.offset
        when (val tag = filters.tag) {
            FocusTagFilter.All -> {
                savedStateHandle[TAG_KIND_KEY] = "all"
                savedStateHandle.remove<String>(TAG_ID_KEY)
            }
            FocusTagFilter.Untagged -> {
                savedStateHandle[TAG_KIND_KEY] = "untagged"
                savedStateHandle.remove<String>(TAG_ID_KEY)
            }
            is FocusTagFilter.Tagged -> {
                savedStateHandle[TAG_KIND_KEY] = "tagged"
                savedStateHandle[TAG_ID_KEY] = tag.id.toString()
            }
        }
    }

    private companion object {
        const val PAGE_OFFSET_KEY = "focus_history_page_offset"
        const val SEARCH_KEY = "focus_history_search"
        const val TAG_KIND_KEY = "focus_history_tag_kind"
        const val TAG_ID_KEY = "focus_history_tag_id"
        const val GENERATION_KEY = "focus_history_database_generation"
        const val MAX_GENERATION_RETRIES = 3
    }
}
