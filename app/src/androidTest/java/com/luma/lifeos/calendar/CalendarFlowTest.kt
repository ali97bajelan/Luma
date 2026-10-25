package com.luma.lifeos.calendar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.MemoryInput
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun calendarOwnsThirdPrimarySlotAndHabitsOpenFromMore() {
        listOf("today", "timeline", "calendar", "review", "more").forEach { route ->
            compose.onNodeWithTag("nav_$route").assertExists()
        }
        compose.onNodeWithTag("nav_habits").assertDoesNotExist()

        compose.onNodeWithTag("nav_calendar").performClick()
        awaitTag("screen_calendar")
        compose.onNodeWithTag("habits_back").assertDoesNotExist()

        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_habits").assertIsDisplayed()
        compose.onNodeWithTag("more_tasks").assertIsDisplayed()
        val habitsTop = compose.onNodeWithTag("more_habits").fetchSemanticsNode().boundsInRoot.top
        val tasksTop = compose.onNodeWithTag("more_tasks").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Habits must appear before Tasks in More", habitsTop < tasksTop)
        compose.onNodeWithTag("more_habits").performClick()
        awaitTag("screen_habits")
        compose.onNodeWithTag("habits_back").assertIsDisplayed().performClick()
        awaitTag("screen_more")
    }

    @Test fun calendarDateAndModeSurviveTabChangesAndBackReturnsToToday() {
        val selectedDate = LocalDate.now().plusDays(2)
        compose.onNodeWithTag("nav_calendar").performClick()
        awaitTag("screen_calendar")
        compose.onNodeWithTag("calendar_day_$selectedDate").performClick()
        compose.onNodeWithTag("calendar_mode_week").performClick()

        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("screen_today").assertIsDisplayed()
        compose.onNodeWithTag("nav_calendar").performClick()
        awaitTag("screen_calendar")
        compose.onNodeWithTag("nav_calendar").assertIsSelected()
        compose.onNodeWithTag("calendar_day_$selectedDate").assertIsSelected()
        compose.onNodeWithTag("calendar_mode_week").assertIsSelected()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitTag("screen_today")
    }

    @Test fun datedCalendarScheduleAndUndatedTimelineCaptureKeepTheirOwnDefaults() {
        val dueDate = LocalDate.now().plusDays(2)
        compose.onNodeWithTag("nav_calendar").performClick()
        awaitTag("screen_calendar")
        compose.onNodeWithTag("calendar_day_$dueDate").performClick()
        compose.onNodeWithTag("calendar_schedule").performClick()
        compose.onNodeWithTag("capture_sheet").assertIsDisplayed()
        val dueCaption = compose.activity.getString(
            R.string.capture_task_due_date,
            dueDate.format(localizedDate(FormatStyle.MEDIUM)),
        )
        compose.onNodeWithText(dueCaption).assertIsDisplayed()
        compose.onNodeWithTag("capture_task").performScrollTo().performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_due_date_value")
            .assertTextEquals(dueDate.format(localizedDate(FormatStyle.LONG)))
        compose.onNodeWithTag("todo_editor_close").performClick()
        awaitTag("screen_calendar")

        compose.onNodeWithTag("nav_timeline").performClick()
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_task").performScrollTo().performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_due_date_value")
            .assertTextEquals(compose.activity.getString(R.string.todo_no_date))
        compose.onNodeWithTag("todo_editor_close").performClick()
        awaitTag("screen_timeline")
    }

    @Test fun taskAndMemoryRowsOpenTheirOriginalRecordsAndReturnToCalendar() {
        val storage = (compose.activity.application as LumaApplication).storage
        val selectedDate = LocalDate.now()
        val taskId = UUID.randomUUID()
        val taskRevision = UUID.randomUUID()
        val eventId = EventId.new()
        val token = UUID.randomUUID().toString()
        val taskTitle = "Calendar QA task $token"
        val memoryTitle = "Calendar QA memory $token"
        val memoryNote = "Saved calendar note $token"
        val zone = java.time.ZoneId.systemDefault()
        runBlocking {
            storage.todos.create(taskId, taskRevision, TodoInput(taskTitle, dueDate = selectedDate, dueTime = LocalTime.of(9, 5)))
            storage.memories.create(
                eventId,
                MemoryInput(memoryNote, selectedDate.atTime(12, 0).atZone(zone).toInstant(), memoryTitle),
            )
        }
        try {
            compose.onNodeWithTag("nav_calendar").performClick()
            awaitTag("screen_calendar")
            val taskTag = "calendar_task_open_$taskId"
            val memoryTag = "calendar_memory_$eventId"
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag(taskTag).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithTag(memoryTag).fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithTag("calendar_content").performScrollToNode(hasTestTag(taskTag))
            compose.onNodeWithTag(taskTag).performClick()
            awaitTag("screen_todo_editor")
            compose.onNodeWithTag("todo_editor_title").assertTextContains(taskTitle)
            compose.onNodeWithTag("todo_editor_close").performClick()
            awaitTag("screen_calendar")

            compose.onNodeWithTag("calendar_content").performScrollToNode(hasTestTag(memoryTag))
            compose.onNodeWithTag(memoryTag).performClick()
            awaitTag("screen_memory")
            compose.onNodeWithTag("memory_title").assertTextContains(memoryTitle)
            compose.onNodeWithTag("memory_note").assertTextContains(memoryNote)
            compose.onNodeWithTag("memory_cancel").performClick()
            awaitTag("screen_calendar")
        } finally {
            runBlocking {
                storage.todos.delete(taskId, taskRevision)
                storage.memories.delete(eventId)
            }
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
