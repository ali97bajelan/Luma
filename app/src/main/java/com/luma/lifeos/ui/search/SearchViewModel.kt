package com.luma.lifeos.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.SearchRepository
import com.luma.lifeos.domain.search.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchInput(
    val text: String = "", val from: LocalDate? = null, val through: LocalDate? = null,
    val tagIds: Set<UUID> = emptySet(), val personIds: Set<UUID> = emptySet(), val placeId: UUID? = null,
) {
    val invalidDates get() = from != null && through != null && from.isAfter(through)
    val activeFilterCount get() = tagIds.size + personIds.size + (if (placeId == null) 0 else 1) +
        (if (from == null) 0 else 1) + (if (through == null) 0 else 1)
    fun query() = SearchQuery(text, from, through, tagIds, personIds, placeId)
}

data class SearchState(
    val input: SearchInput, val zone: ZoneId = ZoneId.systemDefault(),
    val records: List<LifeEventRecord> = emptyList(), val offset: Int = 0, val totalCount: Int = 0,
    val loading: Boolean = true, val loaded: Boolean = false, val error: Boolean = false,
    val options: SearchOptions = SearchOptions(), val optionsLoading: Boolean = true, val optionsError: Boolean = false,
    val windowVersion: Long = 0,
) {
    val invalidDates get() = input.invalidDates
    val canLoadEarlier get() = loaded && offset > 0
    val canLoadLater get() = loaded && offset.toLong() + records.size < totalCount
}

internal data class SearchViewportAnchor(
    val key: String,
    val index: Int,
    val offset: Int,
)

/** Query generations protect even against adapters that finish after cancellation. */
class SearchViewModel(private val repository: SearchRepository, private val saved: SavedStateHandle) : ViewModel() {
    private fun ids(key: String) = saved.get<ArrayList<String>>(key).orEmpty().map(UUID::fromString).toSet()
    private val input = SearchInput(saved["search_text"] ?: "", saved.get<String>("search_from")?.let(LocalDate::parse),
        saved.get<String>("search_through")?.let(LocalDate::parse), ids("search_tags"), ids("search_people"),
        saved.get<String>("search_place")?.let(UUID::fromString))
    private val mutableState = MutableStateFlow(SearchState(input,
        zone = saved.get<String>("search_zone")?.let(ZoneId::of) ?: ZoneId.systemDefault(),
        offset = saved["search_offset"] ?: 0))
    val state = mutableState.asStateFlow()
    private var observing: Job? = null
    private var optionsJob: Job? = null
    private var request: Job? = null
    private var generation = 0L
    private var active = false
    private var failedDirection = Direction.REPLACE
    private enum class Direction { REPLACE, EARLIER, LATER }

    fun start(zone: ZoneId) {
        if (active && state.value.zone == zone) return
        val previous = state.value
        val changedZone = previous.zone != zone
        val resumeOffset = if (changedZone) 0 else saved["search_offset"] ?: previous.offset
        val resumeWindowSize = if (changedZone) 50 else maxOf(
            50,
            previous.records.size,
            saved["search_window_size"] ?: 50,
        )
        stop(); active = true
        saved["search_zone"] = zone.id
        if (changedZone) {
            saved["search_offset"] = 0
            saved["search_window_size"] = 50
            clearReturnViewportAnchor()
        }
        mutableState.value = previous.copy(
            zone = zone,
            offset = resumeOffset,
            records = emptyList(),
            totalCount = 0,
            loaded = false,
            loading = !previous.invalidDates,
            error = false,
        )
        observing = viewModelScope.launch {
            var first = true
            try {
                repository.observeChanges().collect {
                    val initialEmission = first
                    first = false
                    val offset = if (initialEmission) state.value.offset else 0
                    val limit = if (initialEmission) resumeWindowSize else 50
                    begin(Direction.REPLACE, offset, limitOverride = limit)
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                generation++; request?.cancel()
                mutableState.value = state.value.copy(
                    records = emptyList(),
                    totalCount = 0,
                    loading = false,
                    loaded = false,
                    error = true,
                )
            }
        }
        observeOptions()
    }
    fun stop() {
        active = false; generation++; request?.cancel(); observing?.cancel(); optionsJob?.cancel()
    }
    private fun observeOptions() {
        optionsJob?.cancel()
        mutableState.value = state.value.copy(
            options = SearchOptions(),
            optionsLoading = true,
            optionsError = false,
        )
        optionsJob = viewModelScope.launch {
            try {
                repository.observeOptions().collect { mutableState.value = state.value.copy(options = it, optionsLoading = false, optionsError = false) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(optionsLoading = false, optionsError = true)
            }
        }
    }
    fun retryOptions() { if (active) observeOptions() }
    private fun update(input: SearchInput, debounce: Boolean = false) {
        if (input == state.value.input) return
        saved["search_text"] = input.text; saved["search_from"] = input.from?.toString()
        saved["search_through"] = input.through?.toString()
        saved["search_tags"] = ArrayList(input.tagIds.map(UUID::toString))
        saved["search_people"] = ArrayList(input.personIds.map(UUID::toString))
        saved["search_place"] = input.placeId?.toString()
        saved["search_offset"] = 0
        saved["search_window_size"] = 50
        clearReturnViewportAnchor()
        generation++; request?.cancel()
        mutableState.value = state.value.copy(input = input, records = emptyList(), offset = 0, totalCount = 0,
            loading = !input.invalidDates, loaded = false, error = false)
        if (active) begin(Direction.REPLACE, 0, if (debounce) 300 else 0)
    }
    fun setText(text: String) = update(state.value.input.copy(text = text.take(200)), debounce = true)
    fun setFrom(date: LocalDate?) = update(state.value.input.copy(from = date))
    fun setThrough(date: LocalDate?) = update(state.value.input.copy(through = date))
    fun setPlace(id: UUID?) = update(state.value.input.copy(placeId = id))
    private fun toggle(ids: Set<UUID>, id: UUID) = when {
        id in ids -> ids - id
        ids.size < 100 -> ids + id
        else -> ids
    }
    fun toggleTag(id: UUID) = update(state.value.input.copy(tagIds = toggle(state.value.input.tagIds, id)))
    fun togglePerson(id: UUID) = update(state.value.input.copy(personIds = toggle(state.value.input.personIds, id)))
    internal fun removeFilter(key: SearchFilterKey) {
        val input = state.value.input
        val updated = when (key.kind) {
            SearchFilterKind.FROM -> input.copy(from = null)
            SearchFilterKind.THROUGH -> input.copy(through = null)
            SearchFilterKind.TAG -> key.id?.let { input.copy(tagIds = input.tagIds - it) } ?: input
            SearchFilterKind.PERSON -> key.id?.let { input.copy(personIds = input.personIds - it) } ?: input
            SearchFilterKind.PLACE -> if (input.placeId == key.id) input.copy(placeId = null) else input
        }
        update(updated)
    }
    fun clearFilters() = update(state.value.input.copy(
        from = null, through = null, tagIds = emptySet(), personIds = emptySet(), placeId = null,
    ))
    fun clear() = update(SearchInput())
    fun rememberResult(
        id: com.luma.lifeos.domain.model.EventId,
        viewportKey: String? = null,
        viewportIndex: Int? = null,
        viewportOffset: Int = 0,
    ) {
        val current = state.value
        val resultIndex = current.records.indexOfFirst { it.event.id == id }
        if (resultIndex < 0) return

        saved["search_offset"] = current.offset
        saved["search_window_size"] = current.records.size.coerceAtLeast(50)
        val hasCriteria = current.input.text.isNotBlank() || current.input.activeFilterCount > 0
        val introOffset = if (!hasCriteria && current.loaded && !current.loading && !current.error) 1 else 0
        val earlierOffset = if (current.canLoadEarlier) 1 else 0
        saved["search_return_viewport_key"] = viewportKey ?: id.toString()
        saved["search_return_viewport_index"] = viewportIndex
            ?: (2 + introOffset + earlierOffset + resultIndex)
        saved["search_return_viewport_offset"] = viewportOffset
    }

    internal fun consumeReturnViewportAnchor(): SearchViewportAnchor? {
        val key = saved.get<String>("search_return_viewport_key") ?: return null
        val anchor = SearchViewportAnchor(
            key = key,
            index = saved.get<Int>("search_return_viewport_index") ?: 0,
            offset = saved.get<Int>("search_return_viewport_offset") ?: 0,
        )
        clearReturnViewportAnchor()
        return anchor
    }

    private fun clearReturnViewportAnchor() {
        saved.remove<String>("search_return_viewport_key")
        saved.remove<Int>("search_return_viewport_index")
        saved.remove<Int>("search_return_viewport_offset")
    }

    fun loadLater() { val s = state.value; if (!s.loading && s.canLoadLater) begin(Direction.LATER, s.offset + s.records.size) }
    fun loadEarlier() { val s = state.value; if (!s.loading && s.canLoadEarlier) begin(Direction.EARLIER, maxOf(0, s.offset - 50)) }
    fun retry() {
        if (!active) return
        if (observing?.isActive != true) { startAgain(); return }
        val s = state.value
        when (failedDirection) {
            Direction.REPLACE -> begin(Direction.REPLACE, s.offset)
            Direction.EARLIER -> begin(Direction.EARLIER, maxOf(0, s.offset - 50))
            Direction.LATER -> begin(Direction.LATER, s.offset + s.records.size)
        }
    }
    private fun startAgain() { val zone = state.value.zone; active = false; start(zone) }

    private suspend fun loadWindow(
        query: SearchQuery,
        zone: ZoneId,
        offset: Int,
        limit: Int,
    ): SearchPage {
        val records = ArrayList<LifeEventRecord>(limit)
        var totalCount = 0
        var nextOffset = offset
        while (records.size < limit) {
            val page = repository.page(
                query,
                zone,
                nextOffset,
                minOf(50, limit - records.size),
            )
            totalCount = page.totalCount
            records += page.records
            if (page.records.isEmpty() || nextOffset + page.records.size >= totalCount) break
            nextOffset += page.records.size
        }
        return SearchPage(records, offset, totalCount)
    }

    private fun begin(
        direction: Direction,
        offset: Int,
        debounce: Long = 0,
        limitOverride: Int? = null,
    ) {
        if (!active) return
        request?.cancel(); val token = ++generation
        val before = state.value
        if (before.invalidDates) { mutableState.value = before.copy(loading = false, records = emptyList(), loaded = false); return }
        mutableState.value = before.copy(
            records = if (direction == Direction.REPLACE) emptyList() else before.records,
            totalCount = if (direction == Direction.REPLACE) 0 else before.totalCount,
            loading = true,
            loaded = if (direction == Direction.REPLACE) false else before.loaded,
            error = false,
        )
        request = viewModelScope.launch {
            try {
                if (debounce > 0) delay(debounce)
                val limit = when {
                    direction == Direction.EARLIER -> before.offset - offset
                    limitOverride != null -> limitOverride
                    direction == Direction.REPLACE && before.records.isEmpty() ->
                        maxOf(50, saved["search_window_size"] ?: 50)
                    else -> 50
                }
                var page = loadWindow(before.input.query(), before.zone, offset, limit)
                var operation = direction
                if (token != generation) return@launch
                val retainedIds = before.records.map { it.event.id }.toSet()
                val overlaps = direction != Direction.REPLACE && page.records.any { it.event.id in retainedIds }
                if ((offset > 0 && page.records.isEmpty()) ||
                    (direction != Direction.REPLACE && page.totalCount != before.totalCount) || overlaps) {
                    page = loadWindow(before.input.query(), before.zone, 0, limit); operation = Direction.REPLACE
                }
                if (token != generation) return@launch
                val joined = when (operation) {
                    Direction.REPLACE -> page.records
                    Direction.LATER -> before.records + page.records
                    Direction.EARLIER -> page.records + before.records
                }
                val trim = maxOf(0, joined.size - 300)
                val rows = if (operation == Direction.LATER) joined.drop(trim) else joined.take(300)
                val anchor = when (operation) {
                    Direction.REPLACE, Direction.EARLIER -> page.offset
                    Direction.LATER -> before.offset + trim
                }
                saved["search_offset"] = anchor
                saved["search_window_size"] = rows.size
                mutableState.value = state.value.copy(records = rows, offset = anchor, totalCount = page.totalCount,
                    loading = false, loaded = true, error = false,
                    windowVersion = state.value.windowVersion + if (operation == Direction.REPLACE) 1 else 0)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (token == generation) {
                    failedDirection = direction
                    mutableState.value = state.value.copy(loading = false, error = true)
                }
            }
        }
    }
}
