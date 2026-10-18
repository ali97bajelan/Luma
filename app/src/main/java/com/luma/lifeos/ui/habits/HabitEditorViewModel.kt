package com.luma.lifeos.ui.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.HabitTextLimits
import com.luma.lifeos.domain.habit.scheduleFor
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitEditorDraft(
    val name: String = "",
    val unitLabel: String = "",
    val start: LocalDate,
    val target: String = "1",
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val lifecycle: HabitLifecycle = HabitLifecycle.ACTIVE,
)

enum class HabitEditorError { LOAD, SAVE, INPUT }
enum class HabitEditorFieldError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    UNIT_TOO_LONG,
    TARGET_RANGE,
    WEEKDAYS_REQUIRED,
    START_IN_PAST,
}

data class HabitEditorState(
    val draft: HabitEditorDraft,
    val loading: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val editing: Boolean = false,
    val completed: Boolean = false,
    val resultId: UUID? = null,
    val hasChanges: Boolean = false,
    val error: HabitEditorError? = null,
    val textError: Boolean = false,
    val fieldError: HabitEditorFieldError? = null,
    val effectiveDate: LocalDate? = null,
    val trackingZone: ZoneId? = null,
)

class HabitEditorViewModel(
    private val repository: HabitRepository,
    private val habitId: UUID?,
    private val saved: SavedStateHandle,
    private val clock: Clock = Clock.systemUTC(),
    private val deviceZone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val captureId = UUID.fromString(saved.get<String>(CAPTURE_ID)
        ?: UUID.randomUUID().toString().also { saved[CAPTURE_ID] = it })
    private val restoredFieldError = saved.get<String>(FIELD_ERROR)?.let {
        runCatching { HabitEditorFieldError.valueOf(it) }.getOrNull()
    }
    private var editingId: UUID? = habitId
    private var baseline = HabitEditorDraft(start = LocalDate.now(clock.withZone(deviceZone)))
    private val mutableState = MutableStateFlow(HabitEditorState(
        draft = readDraft() ?: baseline,
        editing = habitId != null,
        completed = saved[COMPLETED] ?: false,
        resultId = saved.get<String>(RESULT_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        hasChanges = saved.get<Boolean>(HAS_DRAFT) ?: false,
        textError = saved[TEXT_ERROR] ?: false,
        error = if (restoredFieldError == null) null else HabitEditorError.INPUT,
        fieldError = restoredFieldError,
    ))
    val state = mutableState.asStateFlow()

    init { if (!state.value.completed) load() }

    fun load() {
        if (state.value.ready || state.value.busy || state.value.completed) return
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val found = repository.get(editingId ?: captureId)
                if (habitId != null) requireNotNull(found) { "Habit is unavailable" }
                val zone = repository.trackingZone() ?: deviceZone
                saved[TRACKING_ZONE] = zone.id
                val today = LocalDate.now(clock.withZone(zone))
                if (found != null) {
                    editingId = found.id
                    val versions = repository.schedules(found.id)
                    val applyDate = if (today.isBefore(found.start)) found.start else today.plusDays(1)
                    val schedule = scheduleFor(applyDate, versions) ?: scheduleFor(today, versions)
                        ?: versions.lastOrNull()
                    val loaded = HabitEditorDraft(found.name, found.unitLabel.orEmpty(), found.start,
                        (schedule?.target ?: 1).toString(), schedule?.weekdays ?: DayOfWeek.entries.toSet(),
                        schedule?.lifecycle ?: HabitLifecycle.ACTIVE)
                    baseline = loaded
                    val draft = if (saved.get<Boolean>(HAS_DRAFT) == true) readDraft() ?: loaded else loaded
                    persist(draft)
                    mutableState.update { it.copy(draft = draft, loading = false, ready = true, editing = true,
                        hasChanges = draft != baseline, effectiveDate = effectiveDate(found, today),
                        trackingZone = zone, error = if (it.fieldError == null) null else HabitEditorError.INPUT) }
                } else {
                    val default = HabitEditorDraft(start = today)
                    baseline = default
                    val draft = if (saved.get<Boolean>(HAS_DRAFT) == true) readDraft() ?: default else default
                    mutableState.update { it.copy(draft = draft, loading = false, ready = true, editing = false,
                        hasChanges = draft != baseline, effectiveDate = draft.start, trackingZone = zone,
                        error = if (it.fieldError == null) null else HabitEditorError.INPUT) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(loading = false, ready = false, error = HabitEditorError.LOAD) }
            }
        }
    }

    fun setName(value: String) {
        if (HabitTextLimits.codePointCount(value) > HabitTextLimits.NAME) { rejectText("name"); return }
        change { it.copy(name = value) }
    }

    fun setUnitLabel(value: String) {
        if (HabitTextLimits.codePointCount(value) > HabitTextLimits.UNIT_LABEL) { rejectText("unit"); return }
        change { it.copy(unitLabel = value) }
    }

    fun setTarget(value: String) {
        if (value.length > 3 || value.any { !it.isDigit() }) { rejectText("target"); return }
        change { it.copy(target = value) }
    }

    fun setStart(value: LocalDate) {
        if (state.value.editing) return
        change { it.copy(start = value) }
    }

    fun setWeekdays(value: Set<DayOfWeek>) = change { it.copy(weekdays = value) }

    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.completed) return
        val zone = state.value.effectiveDate?.let { currentZone() } ?: deviceZone
        val today = LocalDate.now(clock.withZone(zone))
        val draft = current.draft
        val target = draft.target.toIntOrNull()
        val failure = when {
            draft.name.isBlank() -> HabitEditorFieldError.NAME_REQUIRED
            HabitTextLimits.codePointCount(draft.name.trim()) > HabitTextLimits.NAME -> HabitEditorFieldError.NAME_TOO_LONG
            draft.unitLabel.isNotBlank() && HabitTextLimits.codePointCount(draft.unitLabel.trim()) > HabitTextLimits.UNIT_LABEL -> HabitEditorFieldError.UNIT_TOO_LONG
            target !in HabitTextLimits.MIN_TARGET..HabitTextLimits.MAX_TARGET -> HabitEditorFieldError.TARGET_RANGE
            draft.weekdays.isEmpty() -> HabitEditorFieldError.WEEKDAYS_REQUIRED
            !current.editing && draft.start.isBefore(today) -> HabitEditorFieldError.START_IN_PAST
            else -> null
        }
        if (failure != null) {
            saved[TEXT_ERROR] = true
            saved[FIELD_ERROR] = failure.name
            mutableState.update { it.copy(error = HabitEditorError.INPUT, textError = true, fieldError = failure) }
            return
        }
        val input = HabitSaveInput(draft.name, draft.unitLabel, draft.start, requireNotNull(target), draft.weekdays, zone)
        saved[PENDING_SAVE] = true
        saved[TEXT_ERROR] = false
        saved[FIELD_ERROR] = null
        mutableState.update { it.copy(busy = true, error = null, fieldError = null, textError = false) }
        viewModelScope.launch {
            try {
                val existingId = editingId ?: repository.get(captureId)?.id
                val id = if (existingId == null) {
                    repository.create(captureId, input).id.also { editingId = it }
                } else {
                    editingId = existingId
                    repository.rename(existingId, input.name, input.unitLabel)
                    val versions = repository.schedules(existingId)
                    val todayAtZone = LocalDate.now(clock.withZone(input.zone))
                    val effectiveDate = if (todayAtZone.isBefore(draft.start)) draft.start else todayAtZone.plusDays(1)
                    val currentSchedule = scheduleFor(effectiveDate, versions) ?: scheduleFor(todayAtZone, versions)
                        ?: versions.lastOrNull()
                    if (currentSchedule == null || currentSchedule.target != input.target ||
                        currentSchedule.weekdays != input.weekdays || currentSchedule.lifecycle != draft.lifecycle) {
                        repository.schedule(existingId, HabitScheduleInput(input.target, input.weekdays, draft.lifecycle))
                    }
                    existingId
                }
                saved[COMPLETED] = true; saved[RESULT_ID] = id.toString(); saved[PENDING_SAVE] = false
                mutableState.update { it.copy(busy = false, completed = true, resultId = id, hasChanges = false, error = null) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                saved[PENDING_SAVE] = false
                mutableState.update { it.copy(busy = false, error = HabitEditorError.SAVE, fieldError = null) }
            }
        }
    }

    private fun currentZone(): ZoneId = runCatching { saved.get<String>(TRACKING_ZONE)?.let(ZoneId::of) }.getOrNull() ?: deviceZone

    private fun effectiveDate(habit: HabitDefinition, today: LocalDate): LocalDate =
        if (today.isBefore(habit.start)) habit.start else today.plusDays(1)

    private fun change(transform: (HabitEditorDraft) -> HabitEditorDraft) {
        val current = state.value
        if (!current.ready || current.busy || current.completed) return
        val draft = transform(current.draft)
        if (draft == current.draft) return
        persist(draft)
        val weekdayError = if (draft.weekdays.isEmpty()) HabitEditorFieldError.WEEKDAYS_REQUIRED else null
        saved[TEXT_ERROR] = weekdayError != null
        saved[FIELD_ERROR] = weekdayError?.name
        mutableState.update { it.copy(draft = draft, hasChanges = draft != baseline,
            error = if (weekdayError == null) null else HabitEditorError.INPUT,
            textError = weekdayError != null, fieldError = weekdayError, effectiveDate = if (it.editing) {
                val zone = currentZone(); val today = LocalDate.now(clock.withZone(zone))
                if (today.isBefore(draft.start)) draft.start else today.plusDays(1)
            } else draft.start) }
    }

    private fun rejectText(field: String) {
        if (!state.value.ready || state.value.busy || state.value.completed) return
        val fieldError = when (field) {
            "name" -> HabitEditorFieldError.NAME_TOO_LONG
            "unit" -> HabitEditorFieldError.UNIT_TOO_LONG
            else -> HabitEditorFieldError.TARGET_RANGE
        }
        saved[TEXT_ERROR] = true
        saved[FIELD_ERROR] = fieldError.name
        mutableState.update { it.copy(error = HabitEditorError.INPUT, textError = true,
            fieldError = fieldError) }
    }

    private fun persist(draft: HabitEditorDraft) {
        saved[HAS_DRAFT] = true
        saved[NAME] = draft.name
        saved[UNIT] = draft.unitLabel
        saved[START] = draft.start.toString()
        saved[TARGET] = draft.target
        saved[WEEKDAYS] = draft.weekdays.sortedBy { it.value }.joinToString(",") { it.value.toString() }
        saved[LIFECYCLE] = draft.lifecycle.name
    }

    private fun readDraft(): HabitEditorDraft? {
        if (saved.get<Boolean>(HAS_DRAFT) != true) return null
        return runCatching {
            HabitEditorDraft(
                name = saved.get<String>(NAME).orEmpty(),
                unitLabel = saved.get<String>(UNIT).orEmpty(),
                start = saved.get<String>(START)?.let(LocalDate::parse) ?: LocalDate.now(clock.withZone(deviceZone)),
                target = saved.get<String>(TARGET) ?: "1",
                weekdays = saved.get<String>(WEEKDAYS)?.split(',')?.mapNotNull { it.toIntOrNull() }
                    ?.mapNotNull { value -> DayOfWeek.entries.firstOrNull { it.value == value } }?.toSet()
                    ?: DayOfWeek.entries.toSet(),
                lifecycle = saved.get<String>(LIFECYCLE)?.let(HabitLifecycle::valueOf) ?: HabitLifecycle.ACTIVE,
            )
        }.getOrNull()
    }

    companion object {
        private const val CAPTURE_ID = "habit_editor_capture_id"
        private const val HAS_DRAFT = "habit_editor_has_draft"
        private const val NAME = "habit_editor_name"
        private const val UNIT = "habit_editor_unit"
        private const val START = "habit_editor_start"
        private const val TARGET = "habit_editor_target"
        private const val WEEKDAYS = "habit_editor_weekdays"
        private const val LIFECYCLE = "habit_editor_lifecycle"
        private const val TEXT_ERROR = "habit_editor_text_error"
        private const val FIELD_ERROR = "habit_editor_field_error"
        private const val PENDING_SAVE = "habit_editor_pending_save"
        private const val COMPLETED = "habit_editor_completed"
        private const val RESULT_ID = "habit_editor_result_id"
        private const val TRACKING_ZONE = "habit_tracking_zone"
    }
}
