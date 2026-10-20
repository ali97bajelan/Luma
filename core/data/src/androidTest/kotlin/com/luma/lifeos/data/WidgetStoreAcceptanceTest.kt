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
}
