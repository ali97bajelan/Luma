package com.luma.lifeos.ui.habits

import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitHistoryPage
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.HabitWeek
import com.luma.lifeos.domain.habit.scheduleFor
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal class HabitRepositoryFixture(
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneId.of("UTC")),
    initialZone: ZoneId? = ZoneId.of("UTC"),
) : HabitRepository {
    private val day = LocalDate.parse("2026-10-18")
    private val id = UUID(0, 1)
    private val definition = HabitDefinition(id, "Read", "pages", day.minusDays(20), clock.instant(), clock.instant())
    private val allWeekdays = java.time.DayOfWeek.entries.toSet()
    private val initialSchedule = HabitSchedule(UUID(0, 2), id, definition.start, 8, allWeekdays, HabitLifecycle.ACTIVE)
    val definitions = linkedMapOf(id to definition)
    val versions = linkedMapOf(id to mutableListOf(initialSchedule))
    val logs = linkedMapOf<UUID, HabitLog>()
    val logInputs = mutableListOf<Pair<UUID, HabitLogInput>>()
    val changes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    private val dayFlows = mutableMapOf<LocalDate, MutableStateFlow<List<HabitDay>>>()
    var zone: ZoneId? = initialZone
    var blockLog: CompletableDeferred<Unit>? = null
    var failNextLog = false
    var failAfterLog = false
    var failNextRename = false
    var failNextSchedule = false
    var failNextUpdate = false
    var blockUpdate: CompletableDeferred<Unit>? = null
    var updateCalls = 0
    var failHistoryReads = false
    var createCalls = 0
    private val skips = mutableSetOf<Pair<UUID, LocalDate>>()

    fun habitId(): UUID = id
    fun habitDay(date: LocalDate) = projectDay(date)

    override fun observeDay(date: LocalDate): Flow<List<HabitDay>> =
        dayFlows.getOrPut(date) { MutableStateFlow(projectDay(date)) }

    override fun observeChanges(): Flow<Unit> = changes
    override suspend fun trackingZone(): ZoneId? = zone
    override suspend fun get(id: UUID): HabitDefinition? = definitions[id]
    override suspend fun schedules(id: UUID): List<HabitSchedule> = versions[id].orEmpty().toList()
    override suspend fun getLog(id: UUID): HabitLog? = logs[id]

    override suspend fun create(id: UUID, input: HabitSaveInput): HabitDefinition {
        createCalls++
        definitions[id]?.let { return it }
        zone = input.zone
        val now = clock.instant()
        val created = HabitDefinition(id, input.name, input.unitLabel, input.start, now, now)
        definitions[id] = created
        versions[id] = mutableListOf(HabitSchedule(UUID.randomUUID(), id, input.start, input.target,
            input.weekdays, HabitLifecycle.ACTIVE))
        refresh()
        return created
    }

    override suspend fun rename(id: UUID, name: String, unitLabel: String?) {
        if (failNextRename) { failNextRename = false; error("rename failed") }
        val existing = definitions.getValue(id)
        definitions[id] = existing.copy(name = name.trim(), unitLabel = unitLabel?.trim()?.takeUnless(String::isEmpty), updatedAt = clock.instant())
        refresh()
    }

    override suspend fun schedule(id: UUID, input: HabitScheduleInput): HabitSchedule {
        if (failNextSchedule) { failNextSchedule = false; error("schedule failed") }
        val existing = versions.getValue(id)
        val from = if (clock.instant().atZone(zone ?: ZoneId.of("UTC")).toLocalDate().isBefore(definitions.getValue(id).start)) {
            definitions.getValue(id).start
        } else clock.instant().atZone(zone ?: ZoneId.of("UTC")).toLocalDate().plusDays(1)
        val value = HabitSchedule(UUID.randomUUID(), id, from, input.target, input.weekdays, input.lifecycle)
        existing.removeAll { it.effectiveFrom == from }
        existing += value
        refresh()
        return value
    }

    override suspend fun cancelPendingSchedule(id: UUID) {
        val currentDay = clock.instant().atZone(zone ?: ZoneId.of("UTC")).toLocalDate()
        versions.getValue(id).removeAll { it.effectiveFrom > currentDay }
        refresh()
    }

    override suspend fun log(id: UUID, input: HabitLogInput): HabitLog {
        logInputs += id to input
        blockLog?.await()
        if (failNextLog) { failNextLog = false; error("log failed") }
        val value = logs[id] ?: HabitLog(id, input.habitId, input.date, input.zone, input.occurredAt, clock.instant(), clock.instant())
        logs[id] = value
        refresh()
        changes.emit(Unit)
        if (failAfterLog) { failAfterLog = false; error("committed, response lost") }
        return value
    }

    override suspend fun updateLog(id: UUID, input: HabitLogInput): HabitLog {
        updateCalls++
        blockUpdate?.await()
        if (failNextUpdate) { failNextUpdate = false; error("update failed") }
        val old = logs.getValue(id)
        return old.copy(date = input.date, zone = input.zone, occurredAt = input.occurredAt, updatedAt = clock.instant())
            .also { logs[id] = it; refresh(); changes.emit(Unit) }
    }

    override suspend fun deleteLog(id: UUID) {
        logs.remove(id)
        refresh()
        changes.emit(Unit)
    }

    override suspend fun setSkipRemaining(id: UUID, date: LocalDate, skip: Boolean) {
        if (skip) skips += id to date else skips -= id to date
        refresh()
        changes.emit(Unit)
    }

    override suspend fun history(id: UUID, date: LocalDate?, offset: Int, limit: Int): HabitHistoryPage {
        if (failHistoryReads) error("history unavailable")
        val matching = logs.values.filter { it.habitId == id && (date == null || it.date == date) }
            .sortedWith(compareByDescending<HabitLog> { it.occurredAt ?: it.recordedAt }.thenByDescending { it.id })
        return HabitHistoryPage(matching.drop(offset).take(limit), offset, matching.size)
    }

    override suspend fun week(start: LocalDate, through: LocalDate): HabitWeek = error("Not used by this fixture")
    override suspend fun deleteHabit(id: UUID) {
        definitions.remove(id); versions.remove(id); logs.entries.removeAll { it.value.habitId == id }; refresh(); changes.emit(Unit)
    }

    private fun projectDay(date: LocalDate): List<HabitDay> = definitions.values.map { habit ->
        val schedule = scheduleFor(date, versions[habit.id].orEmpty())
        HabitDay(habit, date, zone ?: ZoneId.of("UTC"), schedule,
            logs.values.count { it.habitId == habit.id && it.date == date }, (habit.id to date) in skips)
    }

    private fun refresh() { dayFlows.forEach { (date, flow) -> flow.value = projectDay(date) } }
}
