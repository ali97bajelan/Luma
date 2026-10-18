package com.luma.lifeos.ui.habits

import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class HabitLogDisabledReason { FUTURE_DATE, NOT_SCHEDULED, PAUSED, ARCHIVED }
enum class HabitUiError { LOAD, ACTION, VALIDATION }
enum class HabitCorrectionError { DATE, TIME, SAVE, MISSING }

data class HabitDayRow(
    val day: HabitDay,
    val canLog: Boolean,
    val disabledReason: HabitLogDisabledReason? = null,
)

data class PendingHabitLog(
    val id: UUID,
    val input: HabitLogInput,
    val error: String? = null,
)

data class HabitLogCorrectionDraft(
    val logId: UUID,
    val originalDate: LocalDate,
    val originalOccurredAt: Instant?,
    val zone: ZoneId,
    val selectedDate: LocalDate,
    val selectedTime: LocalTime?,
    val timeChanged: Boolean = false,
) {
    val hasChanges: Boolean
        get() = selectedDate != originalDate || selectedTime != originalOccurredAt?.atZone(zone)?.toLocalTime()
}

data class HabitDetailUiState(
    val habit: HabitDefinition? = null,
    val day: HabitDay? = null,
    val schedules: List<HabitSchedule> = emptyList(),
    val pendingSchedule: HabitSchedule? = null,
    val history: List<HabitLog> = emptyList(),
    val historyOffset: Int = 0,
    val historyTotal: Int = 0,
    val loading: Boolean = true,
    val error: HabitUiError? = null,
    val actionError: String? = null,
    val correction: HabitLogCorrectionDraft? = null,
    val correctionError: HabitCorrectionError? = null,
    val correctionBusy: Boolean = false,
    val deleted: Boolean = false,
)

data class HabitsUiState(
    val date: LocalDate? = null,
    val trackingZone: ZoneId? = null,
    val deviceZone: ZoneId? = null,
    val deviceDate: LocalDate? = null,
    val days: List<HabitDayRow> = emptyList(),
    val loading: Boolean = true,
    val error: HabitUiError? = null,
    val actionError: String? = null,
    val pendingLogs: List<PendingHabitLog> = emptyList(),
    val undoableLog: HabitLog? = null,
    val pendingSchedules: Map<UUID, HabitSchedule> = emptyMap(),
    val detail: HabitDetailUiState? = null,
)
