package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.data.widget.RoomWidgetStore
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.widget.WidgetInstance
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.widget.WidgetRow
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetStoreAcceptanceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-10-20T08:00:00Z")
    private val today = LocalDate.parse("2026-10-20")
    private val habitZone = ZoneId.of("Asia/Tehran")
    private lateinit var database: LumaDatabase
    private lateinit var widgets: RoomWidgetStore
    private lateinit var habits: RoomHabitRepository
    private lateinit var todos: RoomTodoRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        widgets = RoomWidgetStore(database)
        habits = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC))
        todos = RoomTodoRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun completedTaskLeavesPreviewButKeepsUndoCommandUsable() = runBlocking {
        val widgetId = 41
        val taskId = UUID.randomUUID()
        val created = todos.create(
            taskId,
            UUID.randomUUID(),
            TodoInput("Widget task", dueDate = today),
        )
        widgets.configure(WidgetInstance(widgetId, WidgetKind.TASKS, showDetails = true))

        val before = requireNotNull(widgets.snapshot(widgetId, now, ZoneOffset.UTC, rowLimit = 3))
        val completeToken = requireNotNull((before.rows.single() as WidgetRow.Task).actionToken)
        val saved = widgets.execute(widgetId, completeToken, now, ZoneOffset.UTC)
        assertEquals(WidgetOutcome.SAVED, saved.outcome)

        val afterComplete = requireNotNull(widgets.snapshot(widgetId, now, ZoneOffset.UTC, rowLimit = 3))
        assertEquals(emptyList<WidgetRow>(), afterComplete.rows)
        assertEquals(WidgetOutcome.SAVED, afterComplete.feedback?.outcome)
        val undoToken = requireNotNull(afterComplete.feedback?.undoToken)
        assertEquals(WidgetOutcome.UNDONE, widgets.execute(widgetId, undoToken, now, ZoneOffset.UTC).outcome)
        assertEquals(false, todos.get(taskId)?.isCompleted)
        assertEquals(created.id, (requireNotNull(widgets.snapshot(widgetId, now, ZoneOffset.UTC, 3)).rows.single() as WidgetRow.Task).id)
    }

    @Test fun reachedHabitTargetKeepsUndoCommandUsableAfterSnapshot() = runBlocking {
        val widgetId = 42
        val habitId = UUID.randomUUID()
        habits.create(
            habitId,
            HabitSaveInput("Widget habit", "minutes", today, 1, DayOfWeek.entries.toSet(), habitZone),
        )
        widgets.configure(WidgetInstance(widgetId, WidgetKind.HABITS, showDetails = true))

        val before = requireNotNull(widgets.snapshot(widgetId, now, ZoneOffset.UTC, rowLimit = 3))
        val logToken = requireNotNull((before.rows.single() as WidgetRow.Habit).actionToken)
        val saved = widgets.execute(widgetId, logToken, now, ZoneOffset.UTC)
        assertEquals(WidgetOutcome.SAVED, saved.outcome)

        val afterLog = requireNotNull(widgets.snapshot(widgetId, now, ZoneOffset.UTC, rowLimit = 3))
        assertEquals(1, (afterLog.rows.single() as WidgetRow.Habit).count)
        val undoToken = requireNotNull(afterLog.feedback?.undoToken)
        assertEquals(WidgetOutcome.UNDONE, widgets.execute(widgetId, undoToken, now, ZoneOffset.UTC).outcome)

        val afterUndo = requireNotNull(widgets.snapshot(widgetId, now, ZoneOffset.UTC, rowLimit = 3))
        assertEquals(0, (afterUndo.rows.single() as WidgetRow.Habit).count)
        assertEquals(WidgetOutcome.UNDONE, afterUndo.feedback?.outcome)
    }

    @Test fun taskSnapshotFetchesEightCandidatesWithDistinctMutationTokens() = runBlocking {
        val widgetId = 43
        val taskIds = (0 until 9).map { index ->
            val id = UUID.randomUUID()
            todos.create(id, UUID.randomUUID(), TodoInput("Widget task ${index + 1}", dueDate = today))
            id
        }
        widgets.configure(WidgetInstance(widgetId, WidgetKind.TASKS, showDetails = true))

        val snapshot = requireNotNull(widgets.snapshot(
            widgetId, now, ZoneOffset.UTC, rowLimit = com.luma.lifeos.domain.widget.WidgetStore.MAX_CANDIDATE_ROWS,
        ))
        val rows = snapshot.rows.filterIsInstance<WidgetRow.Task>()
        assertEquals(9, snapshot.total)
        assertEquals(8, rows.size)
        assertEquals(8, rows.map { it.id }.toSet().size)
        val tokens = rows.mapNotNull { it.actionToken }
        assertEquals(8, tokens.size)
        assertEquals(8, tokens.toSet().size)

        val selected = rows[4]
        assertEquals(WidgetOutcome.SAVED,
            widgets.execute(widgetId, requireNotNull(selected.actionToken), now, ZoneOffset.UTC).outcome)
        assertEquals(true, todos.get(selected.id)?.isCompleted)
        assertEquals(false, todos.get(taskIds.first { it != selected.id })?.isCompleted)

        val noRows = requireNotNull(widgets.snapshot(widgetId, now, ZoneOffset.UTC, rowLimit = 0))
        assertEquals(emptyList<WidgetRow>(), noRows.rows)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { widgets.snapshot(widgetId, now, ZoneOffset.UTC, rowLimit = 9) }
        }
        Unit
    }

    @Test fun collectionPagesUseStableOrderAndReachRowsPastEightAndFiveHundred() = runBlocking {
        val widgetId = 44
        val taskIds = (0 until 500).map { index ->
            val id = UUID.randomUUID()
            todos.create(id, UUID.randomUUID(), TodoInput("Paged task ${index + 1}", dueDate = today))
            id
        }
        widgets.configure(WidgetInstance(widgetId, WidgetKind.TASKS, showDetails = true))

        val expectedOrder = taskIds.sortedBy(UUID::toString)
        val middlePage = requireNotNull(widgets.collectionPage(widgetId, now, ZoneOffset.UTC, offset = 8, limit = 32))
        val middleRows = middlePage.rows.filterIsInstance<WidgetRow.Task>()
        assertEquals(500, middlePage.total)
        assertEquals(expectedOrder.drop(8).take(32), middleRows.map(WidgetRow.Task::id))
        assertEquals(32, middleRows.mapNotNull(WidgetRow.Task::actionToken).toSet().size)

        val lastPage = requireNotNull(widgets.collectionPage(widgetId, now, ZoneOffset.UTC, offset = 480, limit = 32))
        val lastRows = lastPage.rows.filterIsInstance<WidgetRow.Task>()
        assertEquals(expectedOrder.drop(480), lastRows.map(WidgetRow.Task::id))
        assertEquals(20, lastRows.size)
    }

    @Test fun collectionPageRespectsSummaryPrivacyAndRejectsInvalidBounds() = runBlocking {
        val widgetId = 45
        todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Private task", dueDate = today))
        widgets.configure(WidgetInstance(widgetId, WidgetKind.TASKS, showDetails = false))

        val summary = requireNotNull(widgets.collectionPage(widgetId, now, ZoneOffset.UTC, offset = 0, limit = 32))
        assertEquals(1, summary.total)
        assertEquals(emptyList<WidgetRow>(), summary.rows)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { widgets.collectionPage(widgetId, now, ZoneOffset.UTC, offset = -1, limit = 32) }
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { widgets.collectionPage(widgetId, now, ZoneOffset.UTC, offset = 0, limit = 33) }
        }
        Unit
    }

    @Test fun collectionMutationTokenIsBoundToItsExactItemAndDate() = runBlocking {
        val widgetId = 46
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        todos.create(firstId, UUID.randomUUID(), TodoInput("First task", dueDate = today))
        todos.create(secondId, UUID.randomUUID(), TodoInput("Second task", dueDate = today))
        widgets.configure(WidgetInstance(widgetId, WidgetKind.TASKS, showDetails = true))
        val page = requireNotNull(widgets.collectionPage(widgetId, now, ZoneOffset.UTC, offset = 0, limit = 32))
        val first = page.rows.filterIsInstance<WidgetRow.Task>().first { it.id == firstId }
        val token = requireNotNull(first.actionToken)

        assertEquals(true, widgets.isValidCollectionMutation(widgetId, firstId, token, today))
        assertEquals(false, widgets.isValidCollectionMutation(widgetId, secondId, token, today))
        assertEquals(false, widgets.isValidCollectionMutation(widgetId, firstId, token, today.minusDays(1)))
    }

    @Test fun collectionCommandsKeepConflictAndMissingResultsBoundToTheirRecords() = runBlocking {
        val widgetId = 47
        val changedId = UUID.randomUUID()
        val removedId = UUID.randomUUID()
        val changed = todos.create(changedId, UUID.randomUUID(), TodoInput("Changed later", dueDate = today))
        val removed = todos.create(removedId, UUID.randomUUID(), TodoInput("Removed later", dueDate = today))
        widgets.configure(WidgetInstance(widgetId, WidgetKind.TASKS, showDetails = true))
        val rows = requireNotNull(widgets.collectionPage(widgetId, now, ZoneOffset.UTC, 0, 32))
            .rows.filterIsInstance<WidgetRow.Task>()
        val changedToken = requireNotNull(rows.first { it.id == changedId }.actionToken)
        val removedToken = requireNotNull(rows.first { it.id == removedId }.actionToken)

        todos.update(
            changedId,
            changed.revision,
            UUID.randomUUID(),
            TodoInput("Changed elsewhere", dueDate = today),
        )
        todos.delete(removedId, removed.revision)

        assertEquals(WidgetOutcome.CONFLICT, widgets.execute(widgetId, changedToken, now, ZoneOffset.UTC).outcome)
        assertEquals(WidgetOutcome.MISSING, widgets.execute(widgetId, removedToken, now, ZoneOffset.UTC).outcome)
    }
}
