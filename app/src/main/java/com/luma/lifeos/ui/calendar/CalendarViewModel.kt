package com.luma.lifeos.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.calendar.CalendarFilter
import com.luma.lifeos.domain.calendar.CalendarMode
import com.luma.lifeos.domain.calendar.CalendarRepository
import com.luma.lifeos.domain.calendar.calendarRange
import com.luma.lifeos.domain.calendar.moveCalendarSelection
import com.luma.lifeos.domain.calendar.requireCalendarDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Calendar selections and independent read sources, retained by the Calendar back-stack entry. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val taskRetry = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val memoryRetry = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val environmentInitialized = kotlinx.coroutines.flow.MutableStateFlow(false)
    private var environmentReady = false

    private val restoredSelection = readDate(saved.get<String>(KEY_SELECTED_DATE))
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(
        CalendarUiState(
            selectedDate = restoredSelection ?: LocalDate.now(),
            mode = saved.get<String>(KEY_MODE).enumOrDefault(CalendarMode.MONTH),
            filter = saved.get<String>(KEY_FILTER).enumOrDefault(CalendarFilter.ALL),
        ),
    )
    val state = mutableState.asStateFlow()

    init {
        observeTaskPage()
        observeTaskMarkers()
        observeOverdueCount()
        observeMemories()
        observeMemoryMarkers()
    }

    fun selectDate(date: LocalDate) {
        requireCalendarDate(date, "selection")
        val current = mutableState.value
        if (current.selectedDate == date) return
        saved[KEY_SELECTED_DATE] = date.toString()
        mutableState.value = current.copy(
            selectedDate = date,
            pageLimit = PAGE_SIZE,
            memoryPageLimit = PAGE_SIZE,
            loadingMoreMemories = false,
            loadMoreMemoriesError = false,
            loadingMore = false,
            loadMoreError = false,
            taskPage = CalendarDataState.Loading,
            taskMarkers = CalendarDataState.Loading,
            memoryMarkers = CalendarDataState.Loading,
            memoryPage = CalendarDataState.Loading,
        )
    }

    fun setMode(mode: CalendarMode) {
        val current = mutableState.value
        if (current.mode == mode) return
        saved[KEY_MODE] = mode.name
        mutableState.value = current.copy(
            mode = mode,
            taskMarkers = CalendarDataState.Loading,
            memoryMarkers = CalendarDataState.Loading,
        )
    }

    fun setFilter(filter: CalendarFilter) {
        val current = mutableState.value
        if (current.filter == filter) return
        saved[KEY_FILTER] = filter.name
        mutableState.value = current.copy(
            filter = filter,
            pageLimit = PAGE_SIZE,
            memoryPageLimit = PAGE_SIZE,
            loadingMoreMemories = false,
            loadMoreMemoriesError = false,
            loadingMore = false,
            loadMoreError = false,
            taskPage = CalendarDataState.Loading,
        )
    }

    fun movePeriod(delta: Int) {
        val current = mutableState.value
        runCatching { moveCalendarSelection(current.selectedDate, current.mode, delta) }
            .getOrNull()
            ?.let(::selectDate)
    }

    fun goToToday() = selectDate(mutableState.value.today)

    fun loadMore() {
        val current = mutableState.value
        val page = (current.taskPage as? CalendarDataState.Ready)?.value ?: return
        if (current.loadingMore || current.loadMoreError || page.items.size >= page.total) return
        val nextLimit = (current.pageLimit.toLong() + PAGE_SIZE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        mutableState.value = current.copy(pageLimit = nextLimit, loadingMore = true)
    }

    fun loadMoreMemories() {
        val current = mutableState.value
        val page = (current.memoryPage as? CalendarDataState.Ready)?.value ?: return
        if (current.loadingMoreMemories || current.loadMoreMemoriesError || page.items.size >= page.total) return
        val nextLimit = (current.memoryPageLimit.toLong() + PAGE_SIZE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        mutableState.value = current.copy(memoryPageLimit = nextLimit, loadingMoreMemories = true)
    }

    fun retry(source: CalendarSource) {
        when (source) {
            CalendarSource.TASKS -> {
                val current = mutableState.value
                mutableState.value = current.copy(
                    taskPage = CalendarDataState.Loading,
                    taskMarkers = CalendarDataState.Loading,
                    overdueCount = CalendarDataState.Loading,
                    loadingMore = false,
                    loadMoreError = false,
                )
                taskRetry.update { it + 1 }
            }
            CalendarSource.MEMORIES -> {
                mutableState.value = mutableState.value.copy(
                    memoryMarkers = CalendarDataState.Loading,
                    memoryPage = CalendarDataState.Loading,
                    loadingMoreMemories = false,
                    loadMoreMemoriesError = false,
                )
                memoryRetry.update { it + 1 }
            }
        }
    }

    /** Refreshes civil-day, locale-week and restore keys without moving the user's selection. */
    fun setEnvironment(
        today: LocalDate,
        zone: ZoneId,
        firstDay: DayOfWeek,
        restoreGeneration: Int,
    ) {
        requireCalendarDate(today, "today")
        val current = mutableState.value
        val firstEnvironment = !environmentReady
        val restoreChanged = current.restoreGeneration != restoreGeneration
        val civilEnvironmentChanged = current.today != today || current.zone != zone
        val weekEnvironmentChanged = current.firstDay != firstDay
        val selectedDate = if (firstEnvironment && restoredSelection == null) today else current.selectedDate
        environmentReady = true

        if (firstEnvironment && restoredSelection == null) saved[KEY_SELECTED_DATE] = today.toString()
        mutableState.value = current.copy(
            selectedDate = selectedDate,
            today = today,
            zone = zone,
            firstDay = firstDay,
            restoreGeneration = restoreGeneration,
            pageLimit = if (firstEnvironment || restoreChanged || civilEnvironmentChanged) PAGE_SIZE else current.pageLimit,
            memoryPageLimit = if (firstEnvironment || restoreChanged || civilEnvironmentChanged) PAGE_SIZE else current.memoryPageLimit,
            loadingMoreMemories = false,
            loadMoreMemoriesError = false,
            loadingMore = false,
            loadMoreError = false,
            taskPage = if (firstEnvironment || restoreChanged || civilEnvironmentChanged || selectedDate != current.selectedDate) {
                CalendarDataState.Loading
            } else current.taskPage,
            taskMarkers = if (firstEnvironment || restoreChanged || civilEnvironmentChanged || weekEnvironmentChanged || selectedDate != current.selectedDate) {
                CalendarDataState.Loading
            } else current.taskMarkers,
            overdueCount = if (firstEnvironment || restoreChanged || civilEnvironmentChanged || selectedDate != current.selectedDate) {
                CalendarDataState.Loading
            } else current.overdueCount,
            memoryMarkers = if (firstEnvironment || restoreChanged || civilEnvironmentChanged || weekEnvironmentChanged || selectedDate != current.selectedDate) {
                CalendarDataState.Loading
            } else current.memoryMarkers,
            memoryPage = if (firstEnvironment || restoreChanged || civilEnvironmentChanged || selectedDate != current.selectedDate) {
                CalendarDataState.Loading
            } else current.memoryPage,
        )

        if (restoreChanged) {
            taskRetry.update { it + 1 }
            memoryRetry.update { it + 1 }
        }
        environmentInitialized.value = true
    }

    private fun observeTaskPage() = viewModelScope.launch {
        combine(
            mutableState.map { it.pageRequestKey() }.distinctUntilChanged(),
            taskRetry,
            environmentInitialized,
        ) { key, retry, ready -> if (ready) key.copy(retry = retry) else null }
            .filterNotNull()
            .flatMapLatest { request ->
                observeCalendarData {
                    repository.observeTasks(
                    date = request.date,
                    today = request.today,
                    overdueOnly = request.filter == CalendarFilter.OVERDUE,
                    limit = request.limit,
                    )
                }.map { request to it }
            }
            .collect { (request, result) ->
                mutableState.update { current ->
                    if (!request.isCurrentPage(current, taskRetry.value)) return@update current
                    when (result) {
                        CalendarDataState.Loading -> if (current.loadingMore) current else current.copy(taskPage = result)
                        is CalendarDataState.Ready -> current.copy(
                            taskPage = result,
                            loadingMore = false,
                            loadMoreError = false,
                        )
                        CalendarDataState.Error -> if (current.loadingMore && current.taskPage is CalendarDataState.Ready) {
                            current.copy(loadingMore = false, loadMoreError = true)
                        } else current.copy(taskPage = result, loadingMore = false, loadMoreError = false)
                    }
                }
            }
    }

    private fun observeTaskMarkers() = viewModelScope.launch {
        combine(
            mutableState.map { it.markerRequestKey() }.distinctUntilChanged(),
            taskRetry,
            environmentInitialized,
        ) { key, retry, ready -> if (ready) key.copy(retry = retry) else null }
            .filterNotNull()
            .flatMapLatest { request ->
                observeCalendarData { repository.observeTaskMarkers(request.range, request.today) }
                    .map { request to it }
            }
            .collect { (request, result) ->
                mutableState.update { current ->
                    if (!request.isCurrentMarker(current, taskRetry.value)) current
                    else current.copy(taskMarkers = result)
                }
            }
    }

    private fun observeOverdueCount() = viewModelScope.launch {
        combine(
            mutableState.map { it.overdueRequestKey() }.distinctUntilChanged(),
            taskRetry,
            environmentInitialized,
        ) { key, retry, ready -> if (ready) key.copy(retry = retry) else null }
            .filterNotNull()
            .flatMapLatest { request ->
                observeCalendarData { repository.observeOverdueCount(request.today) }.map { request to it }
            }
            .collect { (request, result) ->
                mutableState.update { current ->
                    if (!request.isCurrentOverdue(current, taskRetry.value)) current
                    else current.copy(overdueCount = result)
                }
            }
    }

    private fun observeMemories() = viewModelScope.launch {
        combine(
            mutableState.map { it.memoryPageRequestKey() }.distinctUntilChanged(),
            memoryRetry,
            environmentInitialized,
        ) { key, retry, ready -> if (ready) key.copy(retry = retry) else null }
            .filterNotNull()
            .flatMapLatest { request ->
                observeCalendarData { repository.observeMemories(request.date, request.zone, request.limit) }
                    .map { request to it }
            }
            .collect { (request, result) ->
                mutableState.update { current ->
                    if (!request.isCurrentMemoryPage(current, memoryRetry.value)) current
                    else when (result) {
                        CalendarDataState.Loading -> if (current.loadingMoreMemories) current else current.copy(memoryPage = result)
                        is CalendarDataState.Ready -> current.copy(
                            memoryPage = result,
                            loadingMoreMemories = false,
                            loadMoreMemoriesError = false,
                        )
                        CalendarDataState.Error -> if (current.loadingMoreMemories && current.memoryPage is CalendarDataState.Ready) {
                            current.copy(loadingMoreMemories = false, loadMoreMemoriesError = true)
                        } else current.copy(memoryPage = result, loadingMoreMemories = false, loadMoreMemoriesError = false)
                    }
                }
            }
    }

    private fun observeMemoryMarkers() = viewModelScope.launch {
        combine(
            mutableState.map { it.memoryMarkerRequestKey() }.distinctUntilChanged(),
            memoryRetry,
            environmentInitialized,
        ) { key, retry, ready -> if (ready) key.copy(retry = retry) else null }
            .filterNotNull()
            .flatMapLatest { request ->
                observeCalendarData { repository.observeMemoryMarkers(request.range, request.zone) }
                    .map { request to it }
            }
            .collect { (request, result) ->
                mutableState.update { current ->
                    if (!request.isCurrentMemoryMarker(current, memoryRetry.value)) current
                    else current.copy(memoryMarkers = result)
                }
            }
    }

    private fun CalendarUiState.pageRequestKey() = TaskPageRequest(
        date = selectedDate,
        today = today,
        filter = filter,
        limit = pageLimit,
        zone = zone,
        restoreGeneration = restoreGeneration,
    )

    private fun CalendarUiState.markerRequestKey() = TaskMarkerRequest(
        selectedDate,
        today,
        calendarRange(selectedDate, mode, firstDay),
        zone,
        restoreGeneration,
    )

    private fun CalendarUiState.overdueRequestKey() = OverdueRequest(
        selectedDate,
        today,
        filter,
        zone,
        restoreGeneration,
    )

    private fun CalendarUiState.memoryPageRequestKey() = MemoryPageRequest(
        selectedDate,
        filter,
        memoryPageLimit,
        today,
        zone,
        restoreGeneration,
    )

    private fun CalendarUiState.memoryMarkerRequestKey() = MemoryMarkerRequest(
        selectedDate,
        today,
        calendarRange(selectedDate, mode, firstDay),
        zone,
        restoreGeneration,
    )

    private fun TaskPageRequest.isCurrentPage(current: CalendarUiState, retry: Int) =
        this.retry == retry && copy(retry = retry) == current.pageRequestKey().copy(retry = retry)

    private fun TaskMarkerRequest.isCurrentMarker(current: CalendarUiState, retry: Int) =
        this.retry == retry && copy(retry = retry) == current.markerRequestKey().copy(retry = retry)

    private fun OverdueRequest.isCurrentOverdue(current: CalendarUiState, retry: Int) =
        this.retry == retry && copy(retry = retry) == current.overdueRequestKey().copy(retry = retry)

    private fun MemoryPageRequest.isCurrentMemoryPage(current: CalendarUiState, retry: Int) =
        this.retry == retry && copy(retry = retry) == current.memoryPageRequestKey().copy(retry = retry)

    private fun MemoryMarkerRequest.isCurrentMemoryMarker(current: CalendarUiState, retry: Int) =
        this.retry == retry && copy(retry = retry) == current.memoryMarkerRequestKey().copy(retry = retry)

    private data class TaskPageRequest(
        val date: LocalDate,
        val today: LocalDate,
        val filter: CalendarFilter,
        val limit: Int,
        val zone: ZoneId,
        val restoreGeneration: Int,
        val retry: Int = 0,
    )

    private data class TaskMarkerRequest(
        val selectedDate: LocalDate,
        val today: LocalDate,
        val range: com.luma.lifeos.domain.calendar.CalendarRange,
        val zone: ZoneId,
        val restoreGeneration: Int,
        val retry: Int = 0,
    )

    private data class OverdueRequest(
        val selectedDate: LocalDate,
        val today: LocalDate,
        val filter: CalendarFilter,
        val zone: ZoneId,
        val restoreGeneration: Int,
        val retry: Int = 0,
    )

    private data class MemoryPageRequest(
        val date: LocalDate,
        val filter: CalendarFilter,
        val limit: Int,
        val today: LocalDate,
        val zone: ZoneId,
        val restoreGeneration: Int,
        val retry: Int = 0,
    )

    private data class MemoryMarkerRequest(
        val selectedDate: LocalDate,
        val today: LocalDate,
        val range: com.luma.lifeos.domain.calendar.CalendarRange,
        val zone: ZoneId,
        val restoreGeneration: Int,
        val retry: Int = 0,
    )

    private fun readDate(value: String?): LocalDate? = runCatching {
        LocalDate.parse(requireNotNull(value)).also { requireCalendarDate(it, "saved selection") }
    }.getOrNull()

    private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
        this?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() } ?: default

    private companion object {
        const val PAGE_SIZE = 50
        const val KEY_SELECTED_DATE = "calendar_selected_date"
        const val KEY_MODE = "calendar_mode"
        const val KEY_FILTER = "calendar_filter"
    }
}

private fun <T> Flow<T>.asCalendarDataState(): Flow<CalendarDataState<T>> =
    map<T, CalendarDataState<T>> { CalendarDataState.Ready(it) }
        .onStart { emit(CalendarDataState.Loading) }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            emit(CalendarDataState.Error)
        }

private fun <T> observeCalendarData(observe: () -> Flow<T>): Flow<CalendarDataState<T>> =
    flow { emitAll(observe()) }.asCalendarDataState()
