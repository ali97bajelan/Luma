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
        observationJob?.cancel()
        // Invalidate synchronously, before tracking-zone lookup or the new observation can suspend.
        mutableState.update { it.copy(date = date, loadedDate = null,
            selectionGeneration = it.selectionGeneration + 1, days = emptyList(), pendingSchedules = emptyMap(),
            deviceZone = deviceZone, deviceDate = deviceDate, loading = true, error = null) }
        val generation = state.value.selectionGeneration
        selectionJob = viewModelScope.launch {
            try {
                val zone = repository.trackingZone() ?: deviceZone
                val resolvedDate = date ?: LocalDate.now(clock.withZone(zone))
                if (state.value.selectionGeneration != generation) return@launch
                mutableState.update { it.copy(date = resolvedDate, trackingZone = zone, deviceZone = deviceZone,
                    deviceDate = deviceDate, loading = true, error = null) }
                observe(resolvedDate, zone, generation)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { if (it.selectionGeneration != generation) it
                    else it.copy(loading = false, error = HabitUiError.LOAD) }
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

    private fun observe(date: LocalDate, zone: ZoneId, generation: Long) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            repository.observeDay(date)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    mutableState.update { if (it.selectionGeneration != generation) it
                        else it.copy(loadedDate = null, days = emptyList(), loading = false, error = HabitUiError.LOAD) }
                }
                .collect { days ->
                    if (state.value.selectionGeneration != generation) return@collect
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
                        HabitDayRow(day, canLog = reason == null, disabledReason = reason, selectionGeneration = generation)
                    }
                    mutableState.update { if (it.selectionGeneration != generation) it
                        else it.copy(days = rows, loadedDate = date, trackingZone = zone,
                        deviceZone = currentDeviceZone, deviceDate = currentDeviceDate,
                        loading = false, error = null) }
                    refreshPendingSchedules(days.map { it.habit.id }, today, generation)
                }
        }
    }

    fun dateForRow(row: HabitDayRow): LocalDate? {
        val current = state.value
        val date = current.date ?: return null
        if (row.selectionGeneration != current.selectionGeneration || row.day.zone != current.trackingZone ||
            current.rowsFor(date).none { it.day.habit.id == row.day.habit.id } || row.day.date != date) return null
        return date
    }

    fun log(row: HabitDayRow) {
        if (!row.canLog || dateForRow(row) == null) return
        // Recheck eligibility against the latest snapshot, even if the callback came from an older render.
        if (state.value.days.none { it.day.habit.id == row.day.habit.id && it.canLog }) return
        val zone = row.day.zone
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val today = LocalDate.now(clock.withZone(zone))
        if (row.day.date > today) return
        val input = HabitLogInput(row.day.habit.id, row.day.date, zone,
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

    private suspend fun refreshPendingSchedules(ids: List<UUID>, today: LocalDate,
        generation: Long = state.value.selectionGeneration) {
        val pending = linkedMapOf<UUID, com.luma.lifeos.domain.habit.HabitSchedule>()
        ids.forEach { id ->
            runCatching { repository.schedules(id) }.getOrNull()?.let { versions ->
                val start = repository.get(id)?.start
                val effectiveDate = if (start != null && today.isBefore(start)) start else today.plusDays(1)
                scheduleFor(effectiveDate, versions)?.takeIf { it.effectiveFrom == effectiveDate }?.let { pending[id] = it }
            }
        }
        mutableState.update { if (it.selectionGeneration != generation) it else it.copy(pendingSchedules = pending) }
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
            val restoredCorrection = if (current.habit == null) readCorrection() else null
            mutableState.update {
                val latest = it.detail ?: current
                val correction = if (latest.habit == null) restoredCorrection ?: CorrectionRestore(null)
                    else CorrectionRestore(latest.correction, latest.correctionError)
                it.copy(detail = latest.copy(habit = habit, day = day,
                    schedules = schedules, pendingSchedule = pending, history = history.logs,
                    historyOffset = history.offset + history.logs.size, historyTotal = history.total,
                    loading = false, error = null, correction = correction.draft,
                    correctionError = correction.error, correctionBusy = latest.correctionBusy))
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.update { it.copy(detail = (it.detail ?: current).copy(loading = false, error = HabitUiError.LOAD)) }
        }
    }

    fun beginCorrection(log: HabitLog) {
        val draft = HabitLogCorrectionDraft(
            logId = log.id,
            originalDate = log.date,
            originalOccurredAt = log.occurredAt,
            zone = log.zone,
            selectedDate = log.date,
            selectedTime = log.occurredAt?.atZone(log.zone)?.toLocalTime(),
        )
        writeCorrection(draft)
        mutableState.update { it.copy(detail = it.detail?.copy(correction = draft, correctionError = null,
            correctionBusy = false, actionError = null)) }
    }

    fun changeCorrectionDate(value: LocalDate) {
        val draft = state.value.detail?.correction ?: return
        val changed = draft.copy(selectedDate = value)
        writeCorrection(changed)
        mutableState.update { it.copy(detail = it.detail?.copy(correction = changed, correctionError = null, actionError = null)) }
    }

    fun changeCorrectionTime(value: LocalTime?) {
        val draft = state.value.detail?.correction ?: return
        val minute = value?.truncatedTo(ChronoUnit.MINUTES)
        val sameDisplayedMinute = minute != null && draft.selectedTime != null &&
            minute == draft.selectedTime.truncatedTo(ChronoUnit.MINUTES)
        val selectedTime = when {
            value == null -> null
            sameDisplayedMinute -> draft.selectedTime
            else -> minute
        }
        val changed = draft.copy(selectedTime = selectedTime, timeChanged = draft.timeChanged || !sameDisplayedMinute && value != null ||
            value == null && draft.originalOccurredAt != null)
        writeCorrection(changed)
        mutableState.update { it.copy(detail = it.detail?.copy(correction = changed, correctionError = null, actionError = null)) }
    }

    fun cancelCorrection() {
        if (state.value.detail?.correctionBusy == true) return
        clearCorrection()
    }

    fun saveCorrection() {
        val draft = state.value.detail?.correction ?: return
        val detail = state.value.detail ?: return
        if (detail.correctionBusy) return
        if (!draft.hasChanges) {
            clearCorrection()
            return
        }
        val today = LocalDate.now(clock.withZone(draft.zone))
        if (draft.selectedDate.isAfter(today)) {
            mutableState.update { it.copy(detail = it.detail?.copy(correctionError = HabitCorrectionError.DATE)) }
            return
        }
        mutableState.update { it.copy(detail = it.detail?.copy(correctionBusy = true, correctionError = null)) }
        viewModelScope.launch {
            try {
                val log = repository.getLog(draft.logId) ?: run {
                    mutableState.update { it.copy(detail = it.detail?.copy(correctionBusy = false,
                        correctionError = HabitCorrectionError.MISSING)) }
                    return@launch
                }
                val now = clock.instant()
                val time = draft.selectedTime
                val previousOffset = draft.originalOccurredAt?.atZone(draft.zone)?.offset
                val preferredOffset = if (time == null) null else previousOffset?.takeIf {
                    it in draft.zone.rules.getValidOffsets(draft.selectedDate.atTime(time))
                }
                val occurredAt = try {
                    habitLogTime(draft.selectedDate, time, draft.zone, now, preferredOffset)
                } catch (failure: IllegalArgumentException) {
                    val error = if (draft.selectedDate.isAfter(LocalDate.now(clock.withZone(draft.zone))))
                        HabitCorrectionError.DATE else HabitCorrectionError.TIME
                    mutableState.update { it.copy(detail = it.detail?.copy(correctionBusy = false, correctionError = error)) }
                    return@launch
                }
                repository.updateLog(log.id, HabitLogInput(log.habitId, draft.selectedDate, draft.zone, occurredAt))
                clearCorrection()
                mutableState.update { it.copy(actionError = null) }
                refreshDetail()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(detail = it.detail?.copy(correctionBusy = false,
                    correctionError = HabitCorrectionError.SAVE,
                    actionError = failure.message ?: "Unable to update this repetition")) }
            }
        }
    }

    private fun clearCorrection() {
        CORRECTION_KEYS.forEach { saved.remove<String>(it) }
        mutableState.update { it.copy(detail = it.detail?.copy(correction = null, correctionError = null,
            correctionBusy = false, actionError = null)) }
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

    private data class CorrectionRestore(val draft: HabitLogCorrectionDraft?, val error: HabitCorrectionError? = null)

    private suspend fun readCorrection(): CorrectionRestore {
        val id = saved.get<String>(CORRECTION_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return CorrectionRestore(null)
        if (saved.get<Int>(CORRECTION_TYPED_VERSION) == CORRECTION_VERSION) {
            val draft = runCatching {
                HabitLogCorrectionDraft(
                    logId = id,
                    originalDate = LocalDate.parse(requireNotNull(saved.get<String>(CORRECTION_ORIGINAL_DATE))),
                    originalOccurredAt = saved.get<String>(CORRECTION_ORIGINAL_OCCURRED_AT)?.let(java.time.Instant::parse),
                    zone = ZoneId.of(requireNotNull(saved.get<String>(CORRECTION_ZONE))),
                    selectedDate = LocalDate.parse(requireNotNull(saved.get<String>(CORRECTION_SELECTED_DATE))),
                    selectedTime = saved.get<String>(CORRECTION_SELECTED_TIME)?.let(LocalTime::parse),
                    timeChanged = saved.get<Boolean>(CORRECTION_TIME_CHANGED) ?: false,
                )
            }.getOrNull()
            return if (draft != null) CorrectionRestore(draft) else CorrectionRestore(null, HabitCorrectionError.MISSING)
        }
        val legacyDate = saved.get<String>(CORRECTION_DATE) ?: return CorrectionRestore(null)
        val log = repository.getLog(id) ?: return CorrectionRestore(null, HabitCorrectionError.MISSING)
        val selectedDate = runCatching { LocalDate.parse(legacyDate) }.getOrNull()
            ?: return CorrectionRestore(null, HabitCorrectionError.DATE)
        val originalTime = log.occurredAt?.atZone(log.zone)?.toLocalTime()
        val legacyTime = saved.get<String>(CORRECTION_TIME).orEmpty()
        val selectedTime = if (legacyTime.isBlank()) null else runCatching { LocalTime.parse(legacyTime) }.getOrNull()
            ?: return CorrectionRestore(null, HabitCorrectionError.TIME)
        val sameMinute = originalTime != null && selectedTime != null &&
            originalTime.truncatedTo(ChronoUnit.MINUTES) == selectedTime.truncatedTo(ChronoUnit.MINUTES)
        val hydrated = HabitLogCorrectionDraft(
            logId = id,
            originalDate = log.date,
            originalOccurredAt = log.occurredAt,
            zone = log.zone,
            selectedDate = selectedDate,
            selectedTime = if (sameMinute) originalTime else selectedTime?.truncatedTo(ChronoUnit.MINUTES),
            timeChanged = !sameMinute && selectedTime != originalTime,
        )
        writeCorrection(hydrated)
        return CorrectionRestore(hydrated)
    }

    private fun writeCorrection(draft: HabitLogCorrectionDraft) {
        saved[CORRECTION_ID] = draft.logId.toString()
        saved[CORRECTION_TYPED_VERSION] = CORRECTION_VERSION
        saved[CORRECTION_ORIGINAL_DATE] = draft.originalDate.toString()
        saved[CORRECTION_ORIGINAL_OCCURRED_AT] = draft.originalOccurredAt?.toString()
        saved[CORRECTION_ZONE] = draft.zone.id
        saved[CORRECTION_SELECTED_DATE] = draft.selectedDate.toString()
        saved[CORRECTION_SELECTED_TIME] = draft.selectedTime?.toString()
        saved[CORRECTION_TIME_CHANGED] = draft.timeChanged
        saved.remove<String>(CORRECTION_DATE)
        saved.remove<String>(CORRECTION_TIME)
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
        private const val CORRECTION_TYPED_VERSION = "habit_correction_typed_version"
        private const val CORRECTION_ORIGINAL_DATE = "habit_correction_original_date"
        private const val CORRECTION_ORIGINAL_OCCURRED_AT = "habit_correction_original_occurred_at"
        private const val CORRECTION_ZONE = "habit_correction_zone"
        private const val CORRECTION_SELECTED_DATE = "habit_correction_selected_date"
        private const val CORRECTION_SELECTED_TIME = "habit_correction_selected_time"
        private const val CORRECTION_TIME_CHANGED = "habit_correction_time_changed"
        private const val CORRECTION_DATE = "habit_correction_date"
        private const val CORRECTION_TIME = "habit_correction_time"
        private const val CORRECTION_VERSION = 1
        private val CORRECTION_KEYS = listOf(CORRECTION_ID, CORRECTION_TYPED_VERSION, CORRECTION_ORIGINAL_DATE,
            CORRECTION_ORIGINAL_OCCURRED_AT, CORRECTION_ZONE, CORRECTION_SELECTED_DATE, CORRECTION_SELECTED_TIME,
            CORRECTION_TIME_CHANGED, CORRECTION_DATE, CORRECTION_TIME)
    }
}
