package com.luma.lifeos.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.focus.FOCUS_HISTORY_PAGE_SIZE
import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusHistoryRow
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

/** Loads bounded history pages and deletes only terminal rows through the repository contract. */
class FocusHistoryViewModel(
    private val repository: FocusRepository,
    private val range: TimeRange?,
    private val savedStateHandle: SavedStateHandle,
    private val processInstanceId: UUID = UUID.randomUUID(),
    private var privateContentAccessAllowed: Boolean = true,
    private val privateContentAccessFlow: Flow<Boolean>? = null,
    private val databaseGenerationFlow: Flow<UUID?>? = null,
) : ViewModel() {
    private var restoredFilters = restoreFocusHistoryFilterState(
        search = savedStateHandle[SEARCH_KEY],
        tagKind = savedStateHandle[TAG_KIND_KEY],
        tagId = savedStateHandle[TAG_ID_KEY],
        offset = savedStateHandle[PAGE_OFFSET_KEY],
    )
    private val mutableState = MutableStateFlow(
        focusHistoryStateForAccess(FocusHistoryUiState(filters = restoredFilters), privateContentAccessAllowed),
    )
    val state: StateFlow<FocusHistoryUiState> = mutableState.asStateFlow()

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
        databaseGenerationFlow?.let { generations ->
            viewModelScope.launch {
                generations.distinctUntilChanged().collect(::onRuntimeGenerationChanged)
            }
        }
        if (privateContentAccessAllowed) {
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
        } else {
            observeTags()
            load(0)
        }
    }

    private fun observeTags() {
        tagsJob?.cancel()
        if (!privateContentAccessAllowed) return
        tagsJob = viewModelScope.launch {
            repository.observeTags()
                .catch { emit(emptyList()) }
                .collect { tags ->
                    if (privateContentAccessAllowed) {
                        mutableState.value = mutableState.value.copy(tags = tags)
                    }
                }
        }
    }

    fun retry() {
        if (privateContentAccessAllowed) load(mutableState.value.filters.offset)
    }

    fun refresh() {
        if (privateContentAccessAllowed) load(mutableState.value.filters.offset)
    }

    fun search(value: String) {
        if (!privateContentAccessAllowed) return
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.SearchChanged(value))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters)
        load(0)
    }

    fun tag(value: FocusTagFilter) {
        if (!privateContentAccessAllowed) return
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.TagChanged(value))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters)
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
