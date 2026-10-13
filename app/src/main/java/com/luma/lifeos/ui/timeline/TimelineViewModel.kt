package com.luma.lifeos.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.TimelinePage
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun timelineDayRange(date: LocalDate, zone: ZoneId): TimeRange? {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    return if (start == end) null else TimeRange(start, end)
}

enum class TimelineError { LOAD, EARLIER, LATER }
data class TimelineState(
    val date: LocalDate? = null, val zone: ZoneId? = null,
    val records: List<LifeEventRecord> = emptyList(), val offset: Int = 0, val totalCount: Int = 0,
    val loaded: Boolean = false, val loading: Boolean = true, val busy: Boolean = false,
    val error: TimelineError? = null,
    val eventCount: Int = totalCount,
    val showAppUsage: Boolean = false,
) {
    val canLoadEarlier: Boolean get() = loaded && offset > 0
    val canLoadLater: Boolean get() = loaded && offset.toLong() + records.size < totalCount
}

/** A bounded day window. Stable LazyColumn keys retain the visible row when offscreen pages are dropped. */
class TimelineViewModel(private val repository: TimelineRepository, private val saved: SavedStateHandle,
    initialShowAppUsage: Boolean = false) : ViewModel() {
    private val mutableState = MutableStateFlow(TimelineState(
        date = saved.get<String>("day")?.let(LocalDate::parse), zone = saved.get<String>("zone")?.let(ZoneId::of),
        showAppUsage = initialShowAppUsage))
    val state = mutableState.asStateFlow()
    private var ready = CompletableDeferred<Unit>()
    private var observation: Job? = null
    private var read: Job? = null
    private var sequence = 0L

    fun startObserving() {
        observe()
        val current = state.value
        if (current.date != null && current.zone != null) {
            if (!current.loaded) selectDay(current.date, current.zone)
            else request(TimelineError.LOAD, current.offset, current.records.size.coerceIn(INITIAL, MAXIMUM))
        }
    }
    fun stopObserving() {
        sequence++; read?.cancel(); observation?.cancel(); observation = null
        mutableState.value = state.value.copy(busy = false)
    }
    private fun observe() {
        if (observation?.isActive == true) return
        ready = CompletableDeferred()
        observation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var first = true
            try {
                repository.observeChanges().collect {
                    if (first) { first = false; ready.complete(Unit) }
                    else if (state.value.date != null) request(TimelineError.LOAD, state.value.offset,
                        state.value.records.size.coerceIn(INITIAL, MAXIMUM))
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                ready.completeExceptionally(failure)
                sequence++; read?.cancel()
                mutableState.value = state.value.copy(loading = false, busy = false, error = TimelineError.LOAD)
            }
        }
    }
    fun selectDay(date: LocalDate, zone: ZoneId) {
        val sameDay = state.value.date == date && state.value.zone == zone
        if (sameDay && (state.value.loaded || read?.isActive == true)) return
        sequence++; read?.cancel()
        val offset = if (sameDay) saved.get<Int>("offset") ?: 0 else 0
        val size = if (sameDay) (saved.get<Int>("window_size") ?: INITIAL).coerceIn(INITIAL, MAXIMUM) else INITIAL
        saved["day"] = date.toString(); saved["zone"] = zone.id
        saved["offset"] = offset; saved["window_size"] = size
        mutableState.value = TimelineState(date = date, zone = zone, offset = offset,
            showAppUsage = state.value.showAppUsage)
        observe(); request(TimelineError.LOAD, offset, size)
    }
    fun setShowAppUsage(show: Boolean) {
        val current = state.value
        if (current.showAppUsage == show) return
        sequence++; read?.cancel()
        saved["offset"] = 0; saved["window_size"] = INITIAL
        mutableState.value = current.copy(records = emptyList(), offset = 0, totalCount = 0, eventCount = 0,
            loaded = false, loading = true, busy = false, error = null, showAppUsage = show)
        if (current.date != null && current.zone != null) {
            observe()
            request(TimelineError.LOAD, 0, INITIAL)
        }
    }
    fun loadLater() {
        val current = state.value
        if (!current.busy && current.canLoadLater) request(TimelineError.LATER, current.offset + current.records.size, PAGE)
    }
    fun loadEarlier() {
        val current = state.value
        if (!current.busy && current.canLoadEarlier) request(TimelineError.EARLIER, maxOf(0, current.offset - PAGE), minOf(PAGE, current.offset))
    }
    fun retry() {
        if (state.value.busy) return
        observe()
        when (state.value.error) {
            TimelineError.LATER -> loadLater()
            TimelineError.EARLIER -> loadEarlier()
            else -> request(TimelineError.LOAD, state.value.offset, state.value.records.size.coerceIn(INITIAL, MAXIMUM))
        }
    }
    private fun request(mode: TimelineError, offset: Int, limit: Int) {
        val current = state.value
        val date = current.date ?: return
        val zone = current.zone ?: return
        val token = ++sequence
        read?.cancel()
        mutableState.value = current.copy(loading = !current.loaded, busy = true, error = null)
        read = viewModelScope.launch {
            try {
                ready.await()
                val range = timelineDayRange(date, zone)
                var page = if (range == null) TimelinePage(emptyList(), 0, 0) else
                    repository.page(range, offset, limit, current.showAppUsage)
                var replace = mode == TimelineError.LOAD
                if (range != null && !replace && page.totalCount != current.totalCount) {
                    // A write may commit just before its invalidation reaches us. Re-read one coherent window.
                    page = repository.page(range, current.offset, minOf(MAXIMUM, current.records.size + PAGE))
                    replace = true
                }
                if (range != null && replace && page.records.isEmpty() && page.totalCount > 0 && page.offset > 0)
                    page = repository.page(range, maxOf(0, page.totalCount - limit), limit)
                ensureActive()
                if (token != sequence) return@launch
                val merged = if (replace) page.records else if (mode == TimelineError.LATER) current.records + page.records
                    else page.records + current.records
                val unique = merged.distinctBy { it.event.id }
                    .sortedWith(compareBy<LifeEventRecord, LifeEvent>(LifeEvent.chronologicalOrder) { it.event }.reversed())
                val dropping = if (!replace && mode == TimelineError.LATER) maxOf(0, unique.size - MAXIMUM) else 0
                val records = if (dropping > 0) unique.drop(dropping) else unique.take(MAXIMUM)
                val start = if (page.totalCount == 0) 0 else
                    (if (!replace && mode == TimelineError.LATER) current.offset else page.offset) + dropping
                saved["offset"] = start; saved["window_size"] = records.size.coerceIn(INITIAL, MAXIMUM)
                mutableState.value = TimelineState(date, zone, records, start, page.totalCount, loaded = true, loading = false,
                    eventCount = page.eventCount, showAppUsage = current.showAppUsage)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (token == sequence) mutableState.value = state.value.copy(loading = false, busy = false, error = mode)
            }
        }
    }
    companion object { const val PAGE = 50; const val INITIAL = 150; const val MAXIMUM = 300 }
}
