package com.luma.lifeos.data.habit

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitReminderConfig
import com.luma.lifeos.domain.habit.HabitReminderRepository
import com.luma.lifeos.domain.habit.HabitReminderRuntime
import com.luma.lifeos.domain.habit.ReminderPlanningInput
import com.luma.lifeos.domain.habit.ReminderSchedule
import com.luma.lifeos.domain.habit.ReminderSlot
import com.luma.lifeos.domain.habit.dueReminderSlot
import com.luma.lifeos.domain.habit.reminderActionToken
import com.luma.lifeos.domain.habit.reminderSnoozeActionToken
import com.luma.lifeos.domain.habit.snoozedReminder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray

class RoomHabitReminderRepository(
    private val database: LumaDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : HabitReminderRepository {
    private val dao = database.habits()

    override suspend fun configuration(habitId: UUID): HabitReminderConfig? = dao.reminder(habitId.toString())?.toDomain()

    override suspend fun save(habitId: UUID, enabled: Boolean, schedule: ReminderSchedule): HabitReminderConfig = database.withTransaction {
        requireNotNull(dao.habit(habitId.toString())) { "Habit does not exist" }
        val revision = UUID.randomUUID()
        val row = schedule.toEntity(habitId, enabled, revision)
        dao.deleteReminder(habitId.toString())
        dao.insertReminder(row)
        dao.insertReminderRuntime(HabitReminderRuntimeEntity(habitId.toString(), revision.toString(), null, null, null, null, true))
        row.toDomain()
    }

    override suspend fun planningInputs(now: Instant): List<ReminderPlanningInput> {
        val settings = dao.settings() ?: return emptyList()
        val zone = ZoneId.of(settings.trackingZoneId)
        val today = now.atZone(zone).toLocalDate()
        return dao.reminders().mapNotNull { row ->
            dao.habit(row.habitId) ?: return@mapNotNull null
            val config = row.toDomain()
            val runtime = dao.reminderRuntime(row.habitId)?.toDomain()
            ReminderPlanningInput(
                config = config,
                zone = zone,
                schedules = dao.schedules(row.habitId).map(HabitScheduleEntity::toDomain),
                todayCount = dao.reminderLogCount(row.habitId, today.toString()),
                todaySkip = dao.reminderSkip(row.habitId, today.toString()) == true,
                runtime = runtime,
                now = now,
            )
        }
    }

    override suspend fun recordDelivery(slot: ReminderSlot, quietHours: com.luma.lifeos.domain.habit.ReminderQuietHours): Boolean = database.withTransaction {
        val now = clock.instant()
        val input = planningInput(slot.habitId, slot.date, now, quietHours) ?: return@withTransaction false
        val runtime = dao.reminderRuntime(slot.habitId.toString())?.toDomain() ?: return@withTransaction false
        if (runtime.revision != slot.revision || dueReminderSlot(input, slot, now) == null) return@withTransaction false
        if (runtime.lastDeliveredSlot == slot.key) {
            return@withTransaction runtime.consumedActionToken != slot.actionToken &&
                runtime.consumedActionToken != reminderSnoozeActionToken(slot.revision, slot.key)
        }
        dao.upsertReminderRuntime(runtime.toEntity(slot.habitId, lastDelivered = slot.key))
        true
    }

    override suspend fun consumeLogAction(
        slot: ReminderSlot,
        actionId: UUID,
        at: Instant,
        quietHours: com.luma.lifeos.domain.habit.ReminderQuietHours,
    ): HabitLog? = database.withTransaction {
        val habitId = slot.habitId.toString()
        val runtimeRow = dao.reminderRuntime(habitId) ?: return@withTransaction null
        val runtime = runtimeRow.toDomain()
        if (runtime.consumedActionToken == actionId) return@withTransaction dao.log(actionId.toString())?.toDomain()
        if (actionId != slot.actionToken) return@withTransaction null
        val input = planningInput(slot.habitId, slot.date, at, quietHours) ?: return@withTransaction null
        if (runtime.revision != slot.revision || dueReminderSlot(input, slot, at) == null) return@withTransaction null
        if (dao.habit(habitId) == null) return@withTransaction null
        val log = HabitLogEntity(
            id = actionId.toString(), habitId = habitId, habitDate = slot.date.toString(),
            zoneId = input.zone.id, occurredAt = at.toEpochMilli(), recordedAt = at.toEpochMilli(), updatedAt = at.toEpochMilli(),
        )
        if (dao.log(log.id) == null) dao.insertLog(log)
        val saved = requireNotNull(dao.log(log.id))
        dao.upsertReminderRuntime(runtime.toEntity(slot.habitId, consumed = actionId, lastDelivered = slot.key))
        saved.toDomain()
    }

    override suspend fun snooze(
        slot: ReminderSlot,
        at: Instant,
        quietHours: com.luma.lifeos.domain.habit.ReminderQuietHours,
    ): Boolean = database.withTransaction {
        val input = planningInput(slot.habitId, slot.date, at, quietHours) ?: return@withTransaction false
        val runtime = dao.reminderRuntime(slot.habitId.toString())?.toDomain() ?: return@withTransaction false
        if (runtime.revision != slot.revision || runtime.consumedActionToken == reminderActionToken(slot.revision, slot.key) ||
            runtime.consumedActionToken == reminderSnoozeActionToken(slot.revision, slot.key) ||
            dueReminderSlot(input, slot, at) == null) return@withTransaction false
        val snoozed = snoozedReminder(slot, at, input.zone, input.quietHours) ?: return@withTransaction false
        val snoozeToken = reminderSnoozeActionToken(slot.revision, slot.key)
        dao.upsertReminderRuntime(runtime.toEntity(slot.habitId, consumed = snoozeToken, snooze = snoozed))
        true
    }

    override suspend fun markReconciled(habitId: UUID, revision: UUID) = database.withTransaction {
        val row = dao.reminderRuntime(habitId.toString()) ?: return@withTransaction
        if (row.revisionToken == revision.toString()) dao.upsertReminderRuntime(row.copy(needsReconcile = false))
    }

    private suspend fun planningInput(
        habitId: UUID,
        date: LocalDate,
        now: Instant,
        quietHours: com.luma.lifeos.domain.habit.ReminderQuietHours = com.luma.lifeos.domain.habit.ReminderQuietHours(),
    ): ReminderPlanningInput? {
        val setting = dao.settings() ?: return null
        val config = dao.reminder(habitId.toString())?.toDomain() ?: return null
        val zone = ZoneId.of(setting.trackingZoneId)
        val runtime = dao.reminderRuntime(habitId.toString())?.toDomain()
        return ReminderPlanningInput(
            config = config,
            zone = zone,
            schedules = dao.schedules(habitId.toString()).map(HabitScheduleEntity::toDomain),
            todayCount = dao.reminderLogCount(habitId.toString(), date.toString()),
            todaySkip = dao.reminderSkip(habitId.toString(), date.toString()) == true,
            runtime = runtime,
            now = now,
            quietHours = quietHours,
        )
    }
}

private fun HabitReminderEntity.toDomain(): HabitReminderConfig {
    val parsed = when (mode) {
        "TIMES" -> ReminderSchedule.Times(JSONArray(timesJson).let { array -> (0 until array.length()).map(array::getInt).distinct().sorted() })
        "INTERVAL" -> ReminderSchedule.Interval(requireNotNull(intervalMinutes), requireNotNull(startMinute), requireNotNull(endMinute))
        else -> error("Unknown reminder mode")
    }
    return HabitReminderConfig(UUID.fromString(habitId), enabled, parsed, UUID.fromString(revisionToken))
}

private fun ReminderSchedule.toEntity(habitId: UUID, enabled: Boolean, revision: UUID): HabitReminderEntity = when (this) {
    is ReminderSchedule.Times -> HabitReminderEntity(habitId.toString(), enabled, "TIMES", JSONArray(minutes).toString(), null, null, null, revision.toString())
    is ReminderSchedule.Interval -> HabitReminderEntity(habitId.toString(), enabled, "INTERVAL", "[]", minutes, startMinute, endMinute, revision.toString())
}

private fun HabitReminderRuntimeEntity.toDomain() = HabitReminderRuntime(
    revision = UUID.fromString(revisionToken), lastDeliveredSlot = lastDeliveredSlot,
    consumedActionToken = consumedActionToken?.let(UUID::fromString), snoozeSlot = snoozeSlot,
    snoozeUntil = snoozeUntil?.let(Instant::ofEpochMilli), needsReconcile = needsReconcile,
)

private fun HabitReminderRuntime.toEntity(
    habitId: UUID,
    consumed: UUID? = consumedActionToken,
    lastDelivered: String? = lastDeliveredSlot,
    snooze: ReminderSlot? = null,
    clearSnooze: Boolean = false,
) = HabitReminderRuntimeEntity(
    habitId = habitId.toString(), revisionToken = revision.toString(), lastDeliveredSlot = lastDelivered,
    consumedActionToken = consumed?.toString(), snoozeSlot = if (clearSnooze) null else snooze?.key ?: snoozeSlot,
    snoozeUntil = if (clearSnooze) null else snooze?.at?.toEpochMilli() ?: snoozeUntil?.toEpochMilli(), needsReconcile = true,
)
