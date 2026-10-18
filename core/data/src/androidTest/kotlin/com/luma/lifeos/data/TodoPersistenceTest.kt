package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.completedTodoTaskComparator
import com.luma.lifeos.domain.todo.openTodoTaskComparator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-10-19T08:00:00Z")
    private lateinit var database: LumaDatabase
    private lateinit var todos: TodoRepository
    private var persistentFile: String? = null

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        todos = RoomTodoRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After fun tearDown() {
        database.close()
        persistentFile?.let(context::deleteDatabase)
    }

    @Test fun createCompleteReopenRecompleteAndDeletePersistAcrossReopening() = runBlocking {
        openPersistentDatabase("todo-lifecycle-${UUID.randomUUID()}.db")
        val id = UUID.randomUUID()
        val initialRevision = UUID.randomUUID()
        val created = todos.create(id, initialRevision, TodoInput("Read a book", "Before bed", LocalDate.parse("2026-10-20")))
        val completed = todos.setCompleted(id, initialRevision, UUID.randomUUID(), true)
        assertTrue(completed.isCompleted)
        database.close()

        reopenPersistentDatabase()
        val persisted = requireNotNull(todos.get(id))
        assertEquals(created.createdAt, persisted.createdAt)
        assertEquals(completed, persisted)

        val reopened = todos.setCompleted(id, persisted.revision, UUID.randomUUID(), false)
        assertFalse(reopened.isCompleted)
        val completedAgain = todos.setCompleted(id, reopened.revision, UUID.randomUUID(), true)
        assertTrue(completedAgain.isCompleted)
        todos.delete(id, completedAgain.revision)
        assertNull(todos.get(id))
        todos.delete(id, UUID.randomUUID())
    }

    @Test fun observedRowsInvalidateAfterCreateAndMutation() = runBlocking {
        val emissions = Channel<com.luma.lifeos.domain.todo.TodoPage>(Channel.UNLIMITED)
        val collection = launch {
            todos.observeList(completed = false).collect { page -> emissions.send(page) }
        }
        try {
            assertEquals(0, withTimeout(5_000) { emissions.receive() }.total)
            val id = UUID.randomUUID()
            val created = todos.create(id, UUID.randomUUID(), TodoInput("Observe this task"))
            val inserted = withTimeout(5_000) { emissions.receive() }
            assertEquals(1, inserted.total)
            assertEquals(created, inserted.items.single())

            todos.setCompleted(id, created.revision, UUID.randomUUID(), true)
            val removedFromOpen = withTimeout(5_000) { emissions.receive() }
            assertEquals(0, removedFromOpen.total)
        } finally {
            collection.cancelAndJoin()
            emissions.close()
        }
    }

    @Test fun createAndUpdateRetriesAreIdempotentAndChangedPayloadConflicts() = runBlocking {
        val id = UUID.randomUUID()
        val initialRevision = UUID.randomUUID()
        val created = todos.create(id, initialRevision, TodoInput("Original"))
        val repeatedCreate = todos.create(id, initialRevision, TodoInput("Original"))
        assertEquals(created, repeatedCreate)
        assertEquals(created.createdAt, repeatedCreate.createdAt)
        assertTrue(
            runCatching { todos.create(id, initialRevision, TodoInput("Different payload")) }
                .exceptionOrNull() is TodoConflictException,
        )

        val nextRevision = UUID.randomUUID()
        val updatedInput = TodoInput("Updated", "Keep the note", LocalDate.parse("2026-10-20"))
        val updated = todos.update(id, initialRevision, nextRevision, updatedInput)
        assertEquals(updated, todos.update(id, initialRevision, nextRevision, updatedInput))
        assertEquals(created.createdAt, updated.createdAt)
        assertTrue(
            runCatching {
                todos.update(id, initialRevision, nextRevision, TodoInput("Different retry"))
            }.exceptionOrNull() is TodoConflictException,
        )
        assertTrue(
            runCatching {
                todos.update(id, initialRevision, UUID.randomUUID(), TodoInput("Stale edit"))
            }.exceptionOrNull() is TodoConflictException,
        )
        assertTrue(
            runCatching { todos.delete(id, initialRevision) }.exceptionOrNull() is TodoConflictException,
        )
        assertTrue(
            runCatching { todos.update(id, nextRevision, nextRevision, TodoInput("Changed without revision")) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertEquals("Updated", requireNotNull(todos.get(id)).title)
        assertEquals(1, todos.observeList(completed = false).first().total)
    }

    @Test fun repeatedDesiredCompletionPreservesTimestampAndRevisionAndStaleRetryConflicts() = runBlocking {
        val id = UUID.randomUUID()
        val initialRevision = UUID.randomUUID()
        val created = todos.create(id, initialRevision, TodoInput("Complete me"))
        val completionRevision = UUID.randomUUID()
        val completed = todos.setCompleted(id, initialRevision, completionRevision, true)
        val retried = todos.setCompleted(id, initialRevision, completionRevision, true)
        assertEquals(completed, retried)

        val sameStateNoOp = todos.setCompleted(id, completed.revision, UUID.randomUUID(), true)
        assertEquals(completed.completedAt, sameStateNoOp.completedAt)
        assertEquals(completed.revision, sameStateNoOp.revision)
        assertEquals(created.createdAt, sameStateNoOp.createdAt)

        val reopened = todos.setCompleted(id, completed.revision, UUID.randomUUID(), false)
        assertFalse(reopened.isCompleted)
        assertTrue(
            runCatching {
                todos.setCompleted(id, initialRevision, completionRevision, true)
            }.exceptionOrNull() is TodoConflictException,
        )
        assertTrue(
            runCatching { todos.setCompleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true) }
                .exceptionOrNull() is TodoMissingException,
        )
    }

    @Test fun observedPrefixesCountsTodayCapAndCompletedOrderingMatchDomainRules() = runBlocking {
        val date = LocalDate.parse("2026-10-19")
        val createdTasks = (0 until 51).map { index ->
            val dueDate = when (index % 4) {
                0 -> date.minusDays(1)
                1 -> date
                2 -> date.plusDays(1)
                else -> null
            }
            todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Task $index", dueDate = dueDate))
        }

        val expectedOpen = createdTasks.sortedWith(openTodoTaskComparator)
        val firstPrefix = todos.observeList(completed = false, limit = 50).first()
        assertEquals(51, firstPrefix.total)
        assertEquals(expectedOpen.take(50), firstPrefix.items)
        assertEquals(expectedOpen, todos.observeList(completed = false, limit = 100).first().items)

        val eligible = createdTasks.filter { !it.isCompleted && it.dueDate?.isAfter(date) == false }
            .sortedWith(openTodoTaskComparator)
        val todayPage = todos.observeToday(date, limit = 3).first()
        assertEquals(eligible.size, todayPage.total)
        assertEquals(eligible.take(3), todayPage.items)

        val completionClock = MutableClock(now)
        todos = RoomTodoRepository(database, completionClock)
        val completedTasks = createdTasks.take(4).map { task ->
            completionClock.current = completionClock.current.plusSeconds(1)
            todos.setCompleted(task.id, task.revision, UUID.randomUUID(), true)
        }
        val completedPage = todos.observeList(completed = true, limit = 50).first()
        assertEquals(completedTasks.sortedWith(completedTodoTaskComparator), completedPage.items)
        assertEquals(4, completedPage.total)
    }

    @Test fun clockRollbackKeepsUpdatedAtMonotonicAndCompletionUsesActualClock() = runBlocking {
        val clock = MutableClock(now)
        todos = RoomTodoRepository(database, clock)
        val created = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Clock rollback"))
        val rolledBackInstant = now.minusSeconds(60)
        clock.current = rolledBackInstant

        val completed = todos.setCompleted(created.id, created.revision, UUID.randomUUID(), true)

        assertEquals(rolledBackInstant, completed.completedAt)
        assertEquals(created.updatedAt, completed.updatedAt)
        assertTrue(completed.updatedAt >= completed.createdAt)
    }

    private fun openPersistentDatabase(fileName: String) {
        persistentFile = fileName
        database.close()
        database = Room.databaseBuilder(context, LumaDatabase::class.java, fileName)
            .addMigrations(*LumaDatabase.MIGRATIONS.toTypedArray())
            .build()
        todos = RoomTodoRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun reopenPersistentDatabase() {
        database = Room.databaseBuilder(context, LumaDatabase::class.java, requireNotNull(persistentFile))
            .addMigrations(*LumaDatabase.MIGRATIONS.toTypedArray())
            .build()
        todos = RoomTodoRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    private class MutableClock(
        var current: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
        override fun instant(): Instant = current
    }
}
