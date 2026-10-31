package com.luma.lifeos.lock

import com.luma.lifeos.domain.calendar.CalendarMemories
import com.luma.lifeos.domain.calendar.CalendarRange
import com.luma.lifeos.domain.calendar.CalendarRepository
import com.luma.lifeos.domain.calendar.CalendarTaskMarker
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitHistoryPage
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.HabitWeek
import com.luma.lifeos.domain.model.Person
import com.luma.lifeos.domain.model.PersonHistory
import com.luma.lifeos.domain.model.PersonInput
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ProtectedTodoRepository(
    private val delegate: TodoRepository,
    private val gateway: ProtectedContentGateway,
) : TodoRepository by delegate {
    override fun observeList(completed: Boolean, limit: Int): Flow<TodoPage> = combine(
        delegate.observeList(completed, limit), gateway.snapshot,
    ) { page, state ->
        if (gateway.filterSection(true, LockSection.TASKS, state)) page.copy(items = immutableProjectionList(page.items))
        else TodoPage(emptyList(), 0)
    }

    override fun observeToday(date: LocalDate, limit: Int): Flow<TodoPage> = combine(
        delegate.observeToday(date, limit), gateway.snapshot,
    ) { page, state ->
        if (gateway.filterSection(true, LockSection.TASKS, state)) page.copy(items = immutableProjectionList(page.items))
        else TodoPage(emptyList(), 0)
    }

    override suspend fun get(id: UUID): TodoTask? = gateway.readValue(taskRead(id), { delegate.get(id) }) { value, _ -> value }
    override suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput) =
        gateway.commit(LockOperation.CREATE, setOf(LockSection.TASKS), id.toString()) {
            delegate.create(id, initialRevision, input)
        }
    override suspend fun update(id: UUID, expectedRevision: UUID, nextRevision: UUID, input: TodoInput) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.TASKS), id.toString()) {
            delegate.update(id, expectedRevision, nextRevision, input)
        }
    override suspend fun setCompleted(id: UUID, expectedRevision: UUID, nextRevision: UUID, completed: Boolean) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.TASKS), id.toString()) {
            delegate.setCompleted(id, expectedRevision, nextRevision, completed)
        }
    override suspend fun delete(id: UUID, expectedRevision: UUID) =
        gateway.commit(LockOperation.DELETE, setOf(LockSection.TASKS), id.toString()) {
            delegate.delete(id, expectedRevision)
        }

    private fun taskRead(id: UUID) = LockRequest(
        LockOperation.READ, setOf(LockSection.TASKS), LockContentClassification.CLASSIFIED, objectId = id.toString(),
    )
}

class ProtectedHabitRepository(
    private val delegate: HabitRepository,
    private val gateway: ProtectedContentGateway,
) : HabitRepository by delegate {
    override fun observeDay(date: LocalDate): Flow<List<HabitDay>> = combine(
        delegate.observeDay(date), gateway.snapshot,
    ) { rows, state ->
        immutableProjectionList(rows.takeIf { gateway.filterSection(true, LockSection.HABITS, state) }.orEmpty())
    }

    override fun observeChanges(): Flow<Unit> = combine(delegate.observeChanges(), gateway.snapshot) { _, _ -> Unit }

    override suspend fun trackingZone(): ZoneId? =
        gateway.readValue(habitRead(), { delegate.trackingZone() }) { value, _ -> value }

    override suspend fun get(id: UUID) = gateway.readValue(habitRead(id), { delegate.get(id) }) { value, _ -> value }
    override suspend fun schedules(id: UUID): List<HabitSchedule> =
        gateway.readValue(habitRead(id), { delegate.schedules(id) }) { value, _ ->
            immutableProjectionList(value)
        }.orEmpty()
    override suspend fun getLog(id: UUID): HabitLog? =
        gateway.readValue(habitRead(id), { delegate.getLog(id) }) { value, _ -> value }

    override suspend fun create(id: UUID, input: HabitSaveInput) =
        gateway.commit(LockOperation.CREATE, setOf(LockSection.HABITS), id.toString()) { delegate.create(id, input) }
    override suspend fun rename(id: UUID, name: String, unitLabel: String?) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.HABITS), id.toString()) { delegate.rename(id, name, unitLabel) }
    override suspend fun schedule(id: UUID, input: HabitScheduleInput) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.HABITS), id.toString()) { delegate.schedule(id, input) }
    override suspend fun cancelPendingSchedule(id: UUID) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.HABITS), id.toString()) { delegate.cancelPendingSchedule(id) }
    override suspend fun log(id: UUID, input: com.luma.lifeos.domain.habit.HabitLogInput) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.HABITS), id.toString()) { delegate.log(id, input) }
    override suspend fun updateLog(id: UUID, input: com.luma.lifeos.domain.habit.HabitLogInput) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.HABITS), id.toString()) { delegate.updateLog(id, input) }
    override suspend fun deleteLog(id: UUID) =
        gateway.commit(LockOperation.DELETE, setOf(LockSection.HABITS), id.toString()) { delegate.deleteLog(id) }
    override suspend fun setSkipRemaining(id: UUID, date: LocalDate, skip: Boolean) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.HABITS), id.toString()) {
            delegate.setSkipRemaining(id, date, skip)
        }

    override suspend fun history(id: UUID, date: LocalDate?, offset: Int, limit: Int): HabitHistoryPage =
        gateway.readValue(habitRead(id), { delegate.history(id, date, offset, limit) }) { value, _ ->
            value.copy(logs = immutableProjectionList(value.logs))
        }
            ?: HabitHistoryPage(emptyList(), offset, 0)

    override suspend fun week(start: LocalDate, through: LocalDate): HabitWeek =
        gateway.readValue(habitRead(), { delegate.week(start, through) }) { value, _ ->
            value.copy(rows = immutableProjectionList(value.rows.map { row ->
                row.copy(days = immutableProjectionList(row.days))
            }))
        }
            ?: HabitWeek(start, through, ZoneId.systemDefault(), partial = false, rows = emptyList())

    override suspend fun deleteHabit(id: UUID) =
        gateway.commit(LockOperation.DELETE, setOf(LockSection.HABITS), id.toString()) { delegate.deleteHabit(id) }

    private fun habitRead(id: UUID? = null) = LockRequest(
        LockOperation.READ, setOf(LockSection.HABITS), LockContentClassification.CLASSIFIED, objectId = id?.toString(),
    )
}

class ProtectedPersonRepository(
    private val delegate: PersonRepository,
    private val gateway: ProtectedContentGateway,
) : PersonRepository by delegate {
    override fun observePeople(): Flow<List<Person>> = combine(delegate.observePeople(), gateway.snapshot) { people, state ->
        immutableProjectionList(people.takeIf { gateway.filterSection(true, LockSection.PEOPLE, state) }.orEmpty())
    }

    override fun observeHistory(id: UUID): Flow<PersonHistory?> = combine(delegate.observeHistory(id), gateway.snapshot) { history, state ->
        if (!gateway.filterSection(true, LockSection.PEOPLE, state)) null
        else history?.let { it.copy(events = immutableProjectionList(gateway.projectRecords(it.events, state))) }
    }

    override suspend fun get(id: UUID) = gateway.readValue(personRead(id), { delegate.get(id) }) { value, _ -> value }
    override suspend fun create(id: UUID, input: PersonInput) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.PEOPLE), id.toString()) { delegate.create(id, input) }
    override suspend fun update(id: UUID, input: PersonInput) =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.PEOPLE), id.toString()) { delegate.update(id, input) }
    override suspend fun delete(id: UUID) =
        gateway.commit(LockOperation.DELETE, setOf(LockSection.PEOPLE), id.toString()) { delegate.delete(id) }

    private fun personRead(id: UUID) = LockRequest(
        LockOperation.READ, setOf(LockSection.PEOPLE), LockContentClassification.CLASSIFIED, objectId = id.toString(),
    )
}

class ProtectedCalendarRepository(
    private val delegate: CalendarRepository,
    private val gateway: ProtectedContentGateway,
) : CalendarRepository by delegate {
    override fun observeTaskMarkers(range: CalendarRange, today: LocalDate): Flow<Map<LocalDate, CalendarTaskMarker>> =
        combine(delegate.observeTaskMarkers(range, today), gateway.snapshot) { rows, state ->
            immutableProjectionMap(rows.takeIf { gateway.filterSection(true, LockSection.TASKS, state) }.orEmpty())
        }

    override fun observeMemoryMarkers(range: CalendarRange, zone: ZoneId): Flow<Map<LocalDate, Int>> =
        combine(delegate.observeMemoryMarkers(range, zone), gateway.snapshot) { rows, state ->
            immutableProjectionMap(rows.takeIf { gateway.filterSection(true, LockSection.MEMORIES, state) }.orEmpty())
        }

    override fun observeTasks(date: LocalDate, today: LocalDate, overdueOnly: Boolean, limit: Int): Flow<TodoPage> =
        combine(delegate.observeTasks(date, today, overdueOnly, limit), gateway.snapshot) { page, state ->
            if (gateway.filterSection(true, LockSection.TASKS, state)) page.copy(items = immutableProjectionList(page.items))
            else TodoPage(emptyList(), 0)
        }

    override fun observeOverdueCount(today: LocalDate): Flow<Int> =
        combine(delegate.observeOverdueCount(today), gateway.snapshot) { count, state ->
            count.takeIf { gateway.filterSection(true, LockSection.TASKS, state) } ?: 0
        }

    override fun observeMemories(date: LocalDate, zone: ZoneId, limit: Int): Flow<CalendarMemories> =
        combine(delegate.observeMemories(date, zone, limit), gateway.snapshot) { result, state ->
            if (gateway.filterSection(true, LockSection.MEMORIES, state)) {
                result.copy(items = immutableProjectionList(result.items))
            } else CalendarMemories(0, emptyList())
        }
}

private fun <K, V> immutableProjectionMap(values: Map<K, V>): Map<K, V> =
    java.util.Collections.unmodifiableMap(LinkedHashMap(values))
