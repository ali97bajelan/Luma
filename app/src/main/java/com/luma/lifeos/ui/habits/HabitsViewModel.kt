package com.luma.lifeos.ui.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.habitLogTime
import com.luma.lifeos.domain.habit.scheduleFor
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HabitsViewModel(
    private val repository: HabitRepository,
    private val saved: SavedStateHandle,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(
        HabitsUiState(pendingLogs = decodePending(saved.get<ArrayList<String>>(PENDING_LOGS)))
    )
    val state = mutableState.asStateFlow()
    private var selectionJob: Job? = null
    private var observationJob: Job? = null
    private var detailObservationJob: Job? = null
    private var currentDeviceDate: LocalDate? = null
    private var currentDeviceZone: ZoneId? = null
    private var activeDetail: Pair<UUID, LocalDate>? = null
    private val logMutexes = ConcurrentHashMap<UUID, Mutex>()

    init {
        state.value.pendingLogs.forEach { enqueueLog(it.id) }
    }

    fun selectDate(date: LocalDate?, deviceZone: ZoneId, deviceDate: LocalDate? = null) {
        saved[SELECTED_DATE] = date?.toString()
        currentDeviceZone = deviceZone
        currentDeviceDate = deviceDate
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            try {
                val zone = repository.trackingZone() ?: deviceZone
                val resolvedDate = date ?: LocalDate.now(clock.withZone(zone))
                mutableState.update { it.copy(date = resolvedDate, trackingZone = zone, deviceZone = deviceZone,
                    deviceDate = deviceDate, loading = true, error = null) }
                observe(resolvedDate, zone)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(loading = false, error = HabitUiError.LOAD) }
            }
        }
    }

    fun retry() {
        val zone = currentDeviceZone ?: state.value.deviceZone ?: ZoneId.systemDefault()
        selectDate(saved.get<String>(SELECTED_DATE)?.let(LocalDate::parse), zone, currentDeviceDate)
        activeDetail?.let { (habitId, date) ->
            viewModelScope.launch { refreshDetail(habitId, date) }
        }
    }

    private suspend fun observe(date: LocalDate, zone: ZoneId) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            repository.observeDay(date)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    mutableState.update { it.copy(loading = false, error = HabitUiError.LOAD) }
                }
                .collect { days ->
                    val today = LocalDate.now(clock.withZone(zone))
                    val rows = days.map { day ->
                        val schedule = day.schedule
                        val reason = when {
                            day.date.isAfter(today) -> HabitLogDisabledReason.FUTURE_DATE
                            schedule == null || day.date < day.habit.start || day.date.dayOfWeek !in schedule.weekdays ->
                                HabitLogDisabledReason.NOT_SCHEDULED
                            schedule.lifecycle == HabitLifecycle.PAUSED -> HabitLogDisabledReason.PAUSED
                            schedule.lifecycle == HabitLifecycle.ARCHIVED -> HabitLogDisabledReason.ARCHIVED
                            else -> null
                        }
                        HabitDayRow(day, canLog = reason == null, disabledReason = reason)
                    }
                    mutableState.update { it.copy(days = rows, date = date, trackingZone = zone,
                        deviceZone = currentDeviceZone, deviceDate = currentDeviceDate,
                        loading = false, error = null) }
                    refreshPendingSchedules(days.map { it.habit.id }, today)
                }
        }
    }

    fun log(habitId: UUID) {
        val current = state.value
        val row = current.days.firstOrNull { it.day.habit.id == habitId } ?: return
        if (!row.canLog) return
        val zone = row.day.zone
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val today = LocalDate.now(clock.withZone(zone))
        val input = HabitLogInput(habitId, row.day.date, zone,
            occurredAt = if (row.day.date == today) habitLogTime(row.day.date,
                now.atZone(zone).toLocalTime(), zone, now, now.atZone(zone).offset) else null)
        val action = PendingHabitLog(UUID.randomUUID(), input)
        mutableState.update { it.copy(pendingLogs = it.pendingLogs + action, actionError = null) }
        persistPending()
        enqueueLog(action.id)
    }

    fun retryLog(id: UUID) {
        if (state.value.pendingLogs.none { it.id == id }) return
        mutableState.update { current -> current.copy(pendingLogs = current.pendingLogs.map {
            if (it.id == id) it.copy(error = null) else it
        }, actionError = null) }
        persistPending()
        enqueueLog(id)
    }

    private fun enqueueLog(id: UUID) {
        viewModelScope.launch {
            val action = state.value.pendingLogs.firstOrNull { it.id == id } ?: return@launch
            val mutex = logMutexes.getOrPut(action.input.habitId) { Mutex() }
            mutex.withLock {
                val latest = state.value.pendingLogs.firstOrNull { it.id == id } ?: return@withLock
                try {
                    val existing = repository.getLog(id)
                    val committed = if (existing != null) {
                        check(existing.matches(latest.input)) { "Habit log identity was reused with a different payload" }
                        existing
                    } else repository.log(id, latest.input)
                    mutableState.update { current -> current.copy(
                        pendingLogs = current.pendingLogs.filterNot { it.id == id },
                        undoableLog = committed, actionError = null,
                    ) }
                    persistPending()
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    val message = failure.message ?: "Unable to save this repetition"
                    mutableState.update { current -> current.copy(
                        pendingLogs = current.pendingLogs.map { if (it.id == id) it.copy(error = message) else it },
                        actionError = message,
                    ) }
                    persistPending()
                }
            }
        }
    }

    fun undoLog(id: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteLog(id)
                mutableState.update { it.copy(undoableLog = if (it.undoableLog?.id == id) null else it.undoableLog,
                    actionError = null) }
                mutableState.value.detail?.let { refreshDetail() }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(actionError = failure.message ?: "Unable to remove this repetition") }
            }
        }
    }

    fun loadMoreHistory() {
        val current = state.value.detail ?: return
        val habitId = current.habit?.id ?: return
        if (current.loading || current.historyOffset >= current.historyTotal) return
        viewModelScope.launch {
            try {
                val page = repository.history(habitId, null, current.historyOffset, 50)
                mutableState.update { latest ->
                    val detail = latest.detail ?: return@update latest
                    if (detail.habit?.id != habitId) return@update latest
                    latest.copy(detail = detail.copy(history = (detail.history + page.logs).distinctBy { it.id },
                        historyOffset = page.offset + page.logs.size, historyTotal = page.total, actionError = null))
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(detail = it.detail?.copy(actionError = failure.message ?: "Unable to load older repetitions")) }
            }
        }
    }

    fun setSkipRemaining(habitId: UUID, skip: Boolean) {
        val date = state.value.date ?: return
        viewModelScope.launch {
            try {
                repository.setSkipRemaining(habitId, date, skip)
                mutableState.update { it.copy(actionError = null) }
                mutableState.value.detail?.let { refreshDetail() }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(actionError = failure.message ?: "Unable to update today's choice") }
            }
        }
    }

    fun updateSchedule(habitId: UUID, target: Int, weekdays: Set<java.time.DayOfWeek>, lifecycle: HabitLifecycle) {
        viewModelScope.launch {
            try {
                repository.schedule(habitId, HabitScheduleInput(target, weekdays, lifecycle))
                mutableState.update { it.copy(actionError = null) }
                refreshDetail()
                state.value.trackingZone?.let { refreshPendingSchedules(state.value.days.map { row -> row.day.habit.id }, LocalDate.now(clock.withZone(it))) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(actionError = failure.message ?: "Unable to update the habit schedule") }
                mutableState.value.detail?.let { refreshDetail() }
            }
        }
    }

    fun cancelPendingSchedule(habitId: UUID) {
        viewModelScope.launch {
            try {
                repository.cancelPendingSchedule(habitId)
                mutableState.update { it.copy(actionError = null) }
                refreshDetail()
                state.value.trackingZone?.let { refreshPendingSchedules(state.value.days.map { row -> row.day.habit.id }, LocalDate.now(clock.withZone(it))) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(actionError = failure.message ?: "Unable to cancel this change") }
            }
        }
    }

    fun changeLifecycle(habitId: UUID, lifecycle: HabitLifecycle) {
        viewModelScope.launch {
            try {
                val habit = repository.get(habitId) ?: error("Habit is unavailable")
                val versions = repository.schedules(habitId)
                val zone = repository.trackingZone() ?: state.value.trackingZone ?: ZoneId.systemDefault()
                val today = LocalDate.now(clock.withZone(zone))
                val effectiveDate = if (today.isBefore(habit.start)) habit.start else today.plusDays(1)
                val current = scheduleFor(effectiveDate, versions) ?: scheduleFor(today, versions)
                    ?: versions.lastOrNull()
                    ?: error("Habit schedule is unavailable")
                require(!(today.isBefore(habit.start) && lifecycle == HabitLifecycle.PAUSED)) {
                    "A future-start habit cannot be paused before it begins"
                }
                repository.schedule(habitId, HabitScheduleInput(current.target, current.weekdays, lifecycle))
                mutableState.update { it.copy(actionError = null) }
                refreshDetail()
                refreshPendingSchedules(state.value.days.map { it.day.habit.id }, today)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(actionError = failure.message ?: "Unable to update the habit") }
            }
        }
    }

    fun openDetail(habitId: UUID, date: LocalDate, deviceZone: ZoneId) {
        activeDetail = habitId to date
        selectDate(date, deviceZone)
        detailObservationJob?.cancel()
        mutableState.update { it.copy(detail = HabitDetailUiState(loading = true)) }
        detailObservationJob = viewModelScope.launch {
            repository.observeChanges().collect {
                refreshDetail(habitId, date)
            }
        }
        viewModelScope.launch { refreshDetail(habitId, date) }
    }

    private suspend fun refreshPendingSchedules(ids: List<UUID>, today: LocalDate) {
        val pending = linkedMapOf<UUID, com.luma.lifeos.domain.habit.HabitSchedule>()
        ids.forEach { id ->
            runCatching { repository.schedules(id) }.getOrNull()?.let { versions ->
                val start = repository.get(id)?.start
                val effectiveDate = if (start != null && today.isBefore(start)) start else today.plusDays(1)
                scheduleFor(effectiveDate, versions)?.takeIf { it.effectiveFrom == effectiveDate }?.let { pending[id] = it }
            }
        }
        mutableState.update { it.copy(pendingSchedules = pending) }
    }

    private suspend fun refreshDetail(habitId: UUID? = null, date: LocalDate? = null) {
        val current = state.value.detail ?: return
        val id = habitId ?: current.habit?.id ?: return
        val selectedDate = date ?: current.day?.date ?: state.value.date ?: return
        mutableState.update { it.copy(detail = current.copy(loading = true, error = null)) }
        try {
            val habit = repository.get(id) ?: run {
                mutableState.update { it.copy(detail = current.copy(loading = false, deleted = true, habit = null)) }
                return
            }
            val schedules = repository.schedules(id)
            val zone = repository.trackingZone() ?: state.value.trackingZone ?: ZoneId.systemDefault()
            val today = LocalDate.now(clock.withZone(zone))
            val day = repository.observeDay(selectedDate).first().firstOrNull { it.habit.id == id }
            val history = repository.history(id, null, 0, 50)
            val effectiveDate = if (today.isBefore(habit.start)) habit.start else today.plusDays(1)
            val pending = scheduleFor(effectiveDate, schedules)?.takeIf { it.effectiveFrom == effectiveDate }
            val correction = readCorrection()
            mutableState.update { it.copy(detail = (it.detail ?: current).copy(habit = habit, day = day,
                schedules = schedules, pendingSchedule = pending, history = history.logs,
                historyOffset = history.offset + history.logs.size, historyTotal = history.total,
                loading = false, error = null, correction = correction)) }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.update { it.copy(detail = (it.detail ?: current).copy(loading = false, error = HabitUiError.LOAD)) }
        }
    }

    fun beginCorrection(log: HabitLog) {
        val draft = HabitLogCorrectionDraft(log.id, log.date.toString(), log.occurredAt?.atZone(log.zone)?.toLocalTime()?.toString().orEmpty())
        writeCorrection(draft)
        mutableState.update { it.copy(detail = it.detail?.copy(correction = draft, actionError = null)) }
    }

    fun changeCorrectionDate(value: String) {
        val draft = state.value.detail?.correction ?: return
        val changed = draft.copy(date = value)
        writeCorrection(changed)
        mutableState.update { it.copy(detail = it.detail?.copy(correction = changed, actionError = null)) }
    }

    fun changeCorrectionTime(value: String) {
        val draft = state.value.detail?.correction ?: return
        val changed = draft.copy(time = value)
        writeCorrection(changed)
        mutableState.update { it.copy(detail = it.detail?.copy(correction = changed, actionError = null)) }
    }

    fun cancelCorrection() {
        saved[CORRECTION_ID] = null; saved[CORRECTION_DATE] = null; saved[CORRECTION_TIME] = null
        mutableState.update { it.copy(detail = it.detail?.copy(correction = null, actionError = null)) }
    }

    fun saveCorrection() {
        val draft = state.value.detail?.correction ?: return
        viewModelScope.launch {
            try {
                val log = repository.getLog(draft.logId) ?: error("This repetition is no longer available")
                val date = LocalDate.parse(draft.date)
                val time = draft.time.trim().takeIf(String::isNotEmpty)?.let(LocalTime::parse)
                val now = clock.instant()
                val previousOffset = log.occurredAt?.atZone(log.zone)?.offset
                val preferredOffset = if (time == null) null else previousOffset?.takeIf {
                    it in log.zone.rules.getValidOffsets(date.atTime(time))
                }
                val occurredAt = habitLogTime(date, time, log.zone, now, preferredOffset)
                repository.updateLog(log.id, HabitLogInput(log.habitId, date, log.zone, occurredAt))
                cancelCorrection()
                mutableState.update { it.copy(actionError = null) }
                refreshDetail()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(detail = it.detail?.copy(actionError = failure.message ?: "Unable to update this repetition")) }
            }
        }
    }

    fun deleteHabit(habitId: UUID) {
        viewModelScope.launch {
            try {
                repository.deleteHabit(habitId)
                mutableState.update { it.copy(detail = it.detail?.copy(deleted = true, loading = false, actionError = null)) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(detail = it.detail?.copy(actionError = failure.message ?: "Unable to delete this habit")) }
            }
        }
    }

    private fun readCorrection(): HabitLogCorrectionDraft? {
        val id = saved.get<String>(CORRECTION_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        if (saved.get<String>(CORRECTION_DATE) == null) return null
        return HabitLogCorrectionDraft(id, saved.get<String>(CORRECTION_DATE).orEmpty(), saved.get<String>(CORRECTION_TIME).orEmpty())
    }

    private fun writeCorrection(draft: HabitLogCorrectionDraft) {
        saved[CORRECTION_ID] = draft.logId.toString()
        saved[CORRECTION_DATE] = draft.date
        saved[CORRECTION_TIME] = draft.time
    }

    private fun persistPending() {
        saved[PENDING_LOGS] = ArrayList(state.value.pendingLogs.map { pending -> encode(pending) })
    }

    private fun HabitLog.matches(input: HabitLogInput) = habitId == input.habitId && date == input.date &&
        zone == input.zone && occurredAt?.toEpochMilli() == input.occurredAt?.toEpochMilli()

    private fun encode(action: PendingHabitLog): String = listOf(action.id, action.input.habitId,
        action.input.date, action.input.zone.id, action.input.occurredAt ?: "-").joinToString("|")

    private fun decodePending(savedValues: List<String>?): List<PendingHabitLog> = savedValues.orEmpty().mapNotNull { value ->
        runCatching {
            val fields = value.split('|')
            require(fields.size == 5)
            val id = UUID.fromString(fields[0])
            val habitId = UUID.fromString(fields[1])
            val date = LocalDate.parse(fields[2])
            val zone = ZoneId.of(fields[3])
            val occurredAt = fields[4].takeUnless { it == "-" }?.let(java.time.Instant::parse)
            PendingHabitLog(id, HabitLogInput(habitId, date, zone, occurredAt))
        }.getOrNull()
    }

    companion object {
        private const val PENDING_LOGS = "habit_pending_logs"
        private const val SELECTED_DATE = "habit_selected_date"
        private const val CORRECTION_ID = "habit_correction_log_id"
        private const val CORRECTION_DATE = "habit_correction_date"
        private const val CORRECTION_TIME = "habit_correction_time"
    }
}
