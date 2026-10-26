package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.habit.HabitDayChoiceEntity
import com.luma.lifeos.data.habit.HabitDao
import com.luma.lifeos.data.habit.HabitEntity
import com.luma.lifeos.data.habit.HabitLogEntity
import com.luma.lifeos.data.habit.HabitReminderEntity
import com.luma.lifeos.data.habit.HabitReminderRuntimeEntity
import com.luma.lifeos.data.habit.HabitScheduleEntity
import com.luma.lifeos.data.habit.HabitSettingsEntity
import com.luma.lifeos.data.habit.toDomain
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitHistoryPage
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.HabitWeek
import com.luma.lifeos.domain.habit.HabitWeekRow
import com.luma.lifeos.domain.habit.cancelPendingSchedule
import com.luma.lifeos.domain.habit.nextHabitEffectiveDate
import com.luma.lifeos.domain.habit.replaceScheduleAtEffectiveDate
import com.luma.lifeos.domain.habit.scheduleFor
import com.luma.lifeos.domain.habit.scheduleVersion
import com.luma.lifeos.domain.habit.weekdaysToMask
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHabitRepository(
    private val database: LumaDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : HabitRepository {
    private val dao: HabitDao = database.habits()

    override fun observeDay(date: LocalDate): Flow<List<HabitDay>> =
        dao.observeDayRows(date.toString()).map { rows -> rows.map { it.toDomain(date) } }

    override fun observeChanges(): Flow<Unit> = dao.observeChanges().map { Unit }

    override suspend fun trackingZone(): ZoneId? = dao.settings()?.let { ZoneId.of(it.trackingZoneId) }

    override suspend fun get(id: UUID): HabitDefinition? = dao.habit(id.toString())?.toDomain()

    override suspend fun schedules(id: UUID): List<HabitSchedule> = dao.schedules(id.toString()).map { it.toDomain() }

    override suspend fun getLog(id: UUID): HabitLog? = dao.log(id.toString())?.toDomain()

    override suspend fun create(id: UUID, input: HabitSaveInput): HabitDefinition = database.withTransaction {
        val key = id.toString()
        val existing = dao.habit(key)
        if (existing != null) {
            val initial = dao.schedules(key).firstOrNull { it.effectiveFrom == existing.startDate }
            val matches = existing.name == input.name && existing.unitLabel == input.unitLabel &&
                existing.startDate == input.start.toString() && initial?.target == input.target &&
                initial?.weekdayMask == weekdaysToMask(input.weekdays) && initial?.state == HabitLifecycle.ACTIVE.name
            check(matches) { "Habit create identity was reused with a different payload" }
            check(dao.settings()?.trackingZoneId == input.zone.id) { "Habit tracking zone is fixed after first creation" }
            return@withTransaction existing.toDomain()
        }

        dao.insertSettings(HabitSettingsEntity(trackingZoneId = input.zone.id))
        val settings = requireNotNull(dao.settings())
        check(settings.trackingZoneId == input.zone.id) { "Habit tracking zone is fixed after first creation" }
        val instant = clock.instant()
        val habit = HabitEntity(
            id = key,
            name = input.name,
            unitLabel = input.unitLabel,
            startDate = input.start.toString(),
            createdAt = instant.toEpochMilli(),
            updatedAt = instant.toEpochMilli(),
        )
        dao.insertHabit(habit)
        dao.insertSchedule(HabitScheduleEntity(
            id = UUID.randomUUID().toString(),
            habitId = key,
            effectiveFrom = input.start.toString(),
            target = input.target,
            weekdayMask = weekdaysToMask(input.weekdays),
            state = HabitLifecycle.ACTIVE.name,
        ))
        val revision = UUID.randomUUID().toString()
        dao.insertReminder(HabitReminderEntity(key, false, "TIMES", "[540]", null, null, null, revision))
        dao.insertReminderRuntime(HabitReminderRuntimeEntity(key, revision, null, null, null, null, false))
        habit.toDomain()
    }

    override suspend fun rename(id: UUID, name: String, unitLabel: String?) = database.withTransaction {
        val existing = requireNotNull(dao.habit(id.toString())) { "Habit does not exist" }
        val now = maxOf(clock.millis(), existing.createdAt, existing.updatedAt)
        HabitDefinition(id, name.trim(), unitLabel?.trim()?.takeUnless(String::isEmpty),
            LocalDate.parse(existing.startDate), Instant.ofEpochMilli(existing.createdAt), Instant.ofEpochMilli(now))
        check(dao.rename(id.toString(), name.trim(), unitLabel?.trim()?.takeUnless(String::isEmpty), now) == 1)
        dao.markReminderDirty(id.toString())
        Unit
    }

    override suspend fun schedule(id: UUID, input: HabitScheduleInput): HabitSchedule = database.withTransaction {
        val habit = requireNotNull(dao.habit(id.toString())) { "Habit does not exist" }.toDomain()
        val zone = requireNotNull(trackingZone()) { "Habit tracking zone has not been set" }
        val oldVersions = dao.schedules(id.toString()).map { it.toDomain() }
        val now = clock.instant()
        val replacement = scheduleVersion(UUID.randomUUID(), id, habit.start, now, zone, oldVersions, input)
        val today = now.atZone(zone).toLocalDate()
        val updated = replaceScheduleAtEffectiveDate(oldVersions, replacement, today)
        val previous = oldVersions.singleOrNull { it.effectiveFrom == replacement.effectiveFrom }
        dao.upsertSchedule(HabitScheduleEntity(
            id = (previous?.id ?: replacement.id).toString(),
            habitId = id.toString(),
            effectiveFrom = replacement.effectiveFrom.toString(),
            target = replacement.target,
            weekdayMask = weekdaysToMask(replacement.weekdays),
            state = replacement.lifecycle.name,
        ))
        dao.markReminderDirty(id.toString())
        updated.single { it.effectiveFrom == replacement.effectiveFrom }
    }

    override suspend fun cancelPendingSchedule(id: UUID) = database.withTransaction {
        val habit = requireNotNull(dao.habit(id.toString())) { "Habit does not exist" }.toDomain()
        val zone = requireNotNull(trackingZone()) { "Habit tracking zone has not been set" }
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val effective = nextHabitEffectiveDate(now, zone)
        val current = dao.schedules(id.toString()).map { it.toDomain() }
        cancelPendingSchedule(current, effective, today)
        check(dao.deleteSchedule(id.toString(), effective.toString()) == 1)
        dao.markReminderDirty(id.toString())
        check(habit.start <= today) { "Future-start configuration is edited in place" }
    }

    override suspend fun log(id: UUID, input: HabitLogInput): HabitLog = database.withTransaction {
        val existing = dao.log(id.toString())
        if (existing != null) {
            check(existing.matches(input)) { "Habit log identity was reused with a different payload" }
            return@withTransaction existing.toDomain()
        }
        val habit = requireNotNull(dao.habit(input.habitId.toString())) { "Habit does not exist" }.toDomain()
        validateLogInput(input, habit, dao.schedules(input.habitId.toString()).map { it.toDomain() })
        val now = clock.instant()
        val log = HabitLogEntity(
            id = id.toString(), habitId = input.habitId.toString(), habitDate = input.date.toString(),
            zoneId = input.zone.id, occurredAt = input.occurredAt?.toEpochMilli(),
            recordedAt = now.toEpochMilli(), updatedAt = now.toEpochMilli(),
        )
        dao.insertLog(log)
        dao.markReminderDirty(input.habitId.toString())
        log.toDomain()
    }

    override suspend fun updateLog(id: UUID, input: HabitLogInput): HabitLog = database.withTransaction {
        val previous = requireNotNull(dao.log(id.toString())) { "Habit log does not exist" }
        require(input.habitId.toString() == previous.habitId) { "Habit log cannot move to another habit" }
        val habit = requireNotNull(dao.habit(previous.habitId)) { "Habit does not exist" }.toDomain()
        validateLogInput(input, habit, dao.schedules(previous.habitId).map { it.toDomain() })
        val updatedAt = maxOf(clock.millis(), previous.recordedAt, previous.updatedAt)
        check(dao.updateLog(id.toString(), input.date.toString(), input.zone.id,
            input.occurredAt?.toEpochMilli(), updatedAt) == 1)
        dao.markReminderDirty(previous.habitId)
        requireNotNull(dao.log(id.toString())).toDomain()
    }

    override suspend fun deleteLog(id: UUID) = database.withTransaction {
        val previous = dao.log(id.toString()) ?: return@withTransaction
        dao.deleteLog(id.toString())
        dao.markReminderDirty(previous.habitId)
    }

    override suspend fun setSkipRemaining(id: UUID, date: LocalDate, skip: Boolean) = database.withTransaction {
        val habit = requireNotNull(dao.habit(id.toString())) { "Habit does not exist" }.toDomain()
        val zone = requireNotNull(trackingZone()) { "Habit tracking zone has not been set" }
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        check(!date.isAfter(today)) { "Cannot skip a future habit date" }
        check(date >= habit.start) { "Cannot skip before the habit start date" }
        val schedule = scheduleFor(date, dao.schedules(id.toString()).map { it.toDomain() })
        check(schedule?.lifecycle == HabitLifecycle.ACTIVE && date.dayOfWeek in requireNotNull(schedule).weekdays) {
            "Only an active scheduled habit date can be skipped"
        }
        if (skip) {
            dao.upsertChoice(HabitDayChoiceEntity(id.toString(), date.toString(), true, now.toEpochMilli()))
        } else {
            dao.deleteChoice(id.toString(), date.toString())
        }
        dao.markReminderDirty(id.toString())
        Unit
    }

    override suspend fun history(id: UUID, date: LocalDate?, offset: Int, limit: Int): HabitHistoryPage {
        require(offset >= 0) { "Habit history offset cannot be negative" }
        require(limit in 1..50) { "Habit history limit must be between 1 and 50" }
        requireNotNull(dao.habit(id.toString())) { "Habit does not exist" }
        val rows = dao.history(id.toString(), date?.toString(), offset, limit).map { it.toDomain() }
        val total = dao.historyCount(id.toString(), date?.toString())
        return HabitHistoryPage(rows, offset, total)
    }

    override suspend fun week(start: LocalDate, through: LocalDate): HabitWeek {
        require(!through.isBefore(start)) { "Habit week end precedes its start" }
        require(through.toEpochDay() - start.toEpochDay() < 7) { "Habit week cannot exceed seven dates" }
        val zone = trackingZone() ?: ZoneId.systemDefault()
        val today = clock.instant().atZone(zone).toLocalDate()
        require(!start.isAfter(today)) { "Habit week cannot start in the future" }
        val cappedThrough = minOf(through, today)
        val dates = generateSequence(start) { previous ->
            previous.plusDays(1).takeIf { !it.isAfter(cappedThrough) }
        }.toList()
        val byDate = dates.associateWith { date -> dao.dayRows(date.toString()).map { it.toDomain(date) } }
        val first = byDate.values.firstOrNull().orEmpty()
        val rows = first.map { firstDay ->
            HabitWeekRow(firstDay.habit, dates.map { date ->
                byDate.getValue(date).single { it.habit.id == firstDay.habit.id }
            })
        }
        return HabitWeek(start, cappedThrough, zone, through.isAfter(today) || cappedThrough == today, rows)
    }

    override suspend fun deleteHabit(id: UUID) {
        database.withTransaction { dao.deleteHabit(id.toString()) }
    }

    private suspend fun validateLogInput(input: HabitLogInput, habit: HabitDefinition, versions: List<HabitSchedule>) {
        val zone = requireNotNull(trackingZone()) { "Habit tracking zone has not been set" }
        require(input.zone == zone) { "Habit log zone must match its tracking zone" }
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        require(!input.date.isAfter(today)) { "Habit log date cannot be in the future" }
        require(input.date >= habit.start) { "Habit log cannot precede the habit start date" }
        input.occurredAt?.let { require(!it.isAfter(now)) { "Habit log time cannot be in the future" } }
        val schedule = scheduleFor(input.date, versions)
        require(schedule != null && schedule.lifecycle == HabitLifecycle.ACTIVE && input.date.dayOfWeek in schedule.weekdays) {
            "Habit log date is not an active scheduled day"
        }
    }

    private fun HabitLogEntity.matches(input: HabitLogInput): Boolean =
        habitId == input.habitId.toString() && habitDate == input.date.toString() &&
            zoneId == input.zone.id && occurredAt == input.occurredAt?.toEpochMilli()
}
