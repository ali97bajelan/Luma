package com.luma.lifeos.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.focus.FOCUS_HISTORY_PAGE_SIZE
import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusTagFilter
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.time.TimeRange
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class FocusHistoryUiState(
    val filters: FocusHistoryFilterState = FocusHistoryFilterState(),
    val tags: List<FocusTag> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val page: com.luma.lifeos.domain.focus.FocusPage? = null,
    val databaseGeneration: UUID? = null,
    val deletingSessionId: UUID? = null,
    val deletionFailed: Boolean = false,
)

/** Loads bounded history pages and deletes only terminal rows through the repository contract. */
class FocusHistoryViewModel(
    private val repository: FocusRepository,
    private val range: TimeRange?,
    private val savedStateHandle: SavedStateHandle,
    private val processInstanceId: UUID = UUID.randomUUID(),
) : ViewModel() {
    private val restoredFilters = restoreFocusHistoryFilterState(
        search = savedStateHandle[SEARCH_KEY],
        tagKind = savedStateHandle[TAG_KIND_KEY],
        tagId = savedStateHandle[TAG_ID_KEY],
        offset = savedStateHandle[PAGE_OFFSET_KEY],
    )
    private val mutableState = MutableStateFlow(FocusHistoryUiState(filters = restoredFilters))
    val state: StateFlow<FocusHistoryUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null
    private var tagsJob: Job? = null
    private var loadRequest = 0L
    private var activeLoadKey: FocusHistoryLoadKey? = null
    private var knownGeneration: UUID? = savedStateHandle[GENERATION_KEY]

    init {
        observeTags()
        load(restoredFilters.offset)
    }

    private fun observeTags() {
        tagsJob?.cancel()
        tagsJob = viewModelScope.launch {
            repository.observeTags()
                .catch { emit(emptyList()) }
                .collect { tags -> mutableState.value = mutableState.value.copy(tags = tags) }
        }
    }

    fun retry() = load(mutableState.value.filters.offset)

    fun refresh() = load(mutableState.value.filters.offset)

    fun search(value: String) {
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.SearchChanged(value))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters)
        load(0)
    }

    fun tag(value: FocusTagFilter) {
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.TagChanged(value))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters)
        load(0)
    }

    fun page(offset: Int) {
        require(offset >= 0 && offset % FOCUS_HISTORY_PAGE_SIZE == 0)
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.PageChanged(offset))
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters)
        load(offset)
    }

    fun delete(row: FocusHistoryRow) {
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
        val filters = reduceFocusHistoryFilterState(mutableState.value.filters, FocusHistoryFilterEvent.DatabaseReplaced)
        persistFilters(filters)
        mutableState.value = mutableState.value.copy(filters = filters, tags = emptyList())
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
