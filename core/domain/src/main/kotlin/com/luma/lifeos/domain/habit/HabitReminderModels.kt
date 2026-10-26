package com.luma.lifeos.domain.habit

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

sealed interface ReminderSchedule {
    data class Times(val minutes: List<Int>) : ReminderSchedule {
        init { require(minutes.isNotEmpty() && minutes.size <= 24 && minutes == minutes.distinct().sorted() && minutes.all { it in 0..1439 }) }
    }

    data class Interval(val minutes: Int, val startMinute: Int, val endMinute: Int) : ReminderSchedule {
        init {
            require(minutes in 15..720 && minutes % 15 == 0)
            require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute)
        }
    }
}

data class HabitReminderConfig(val habitId: UUID, val enabled: Boolean, val schedule: ReminderSchedule, val revision: UUID)

data class ReminderSlot(
    val habitId: UUID,
    val date: LocalDate,
    val at: Instant,
    val expiresAt: Instant,
    val key: String,
    val revision: UUID,
    val actionToken: UUID,
) {
    init { require(expiresAt > at && key.isNotBlank()) }
}

data class HabitReminderRuntime(
    val revision: UUID,
    val lastDeliveredSlot: String?,
    val consumedActionToken: UUID?,
    val snoozeSlot: String?,
    val snoozeUntil: Instant?,
    val needsReconcile: Boolean,
) {
    init { require((snoozeSlot == null) == (snoozeUntil == null)) }
}

data class ReminderQuietHours(val enabled: Boolean = true, val startMinute: Int = 22 * 60, val endMinute: Int = 8 * 60) {
    init { require(startMinute in 0..1439 && endMinute in 0..1439) }
}

data class ReminderPlanningInput(
    val config: HabitReminderConfig,
    val zone: ZoneId,
    val schedules: List<HabitSchedule>,
    val todayCount: Int,
    val todaySkip: Boolean,
    val runtime: HabitReminderRuntime?,
    val now: Instant,
    val quietHours: ReminderQuietHours = ReminderQuietHours(),
)

interface HabitReminderRepository {
    suspend fun configuration(habitId: UUID): HabitReminderConfig?
    suspend fun save(habitId: UUID, enabled: Boolean, schedule: ReminderSchedule): HabitReminderConfig
    suspend fun planningInputs(now: Instant): List<ReminderPlanningInput>
    suspend fun recordDelivery(slot: ReminderSlot, quietHours: ReminderQuietHours = ReminderQuietHours()): Boolean
    suspend fun consumeLogAction(
        slot: ReminderSlot,
        actionId: UUID,
        at: Instant,
        quietHours: ReminderQuietHours = ReminderQuietHours(),
    ): HabitLog?
    suspend fun snooze(slot: ReminderSlot, at: Instant, quietHours: ReminderQuietHours = ReminderQuietHours()): Boolean
    suspend fun markReconciled(habitId: UUID, revision: UUID)
}
