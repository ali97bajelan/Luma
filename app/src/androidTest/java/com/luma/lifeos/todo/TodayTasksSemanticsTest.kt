package com.luma.lifeos.todo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.summary.DailySummary
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.ui.habits.HabitsUiState
import com.luma.lifeos.ui.summary.DailySummaryState
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.today.TodayScreen
import com.luma.lifeos.ui.today.TodayUiState
import com.luma.lifeos.ui.todo.TodayTasksState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayTasksSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeRowsAndFullEligibleCountAreShownInTheTodayCard() {
        val date = LocalDate.parse("2026-10-19")
        val tasks = (1..3).map { task(it) }
        var allTasks = 0
        var addTask = 0
        compose.setContent {
            LifeOsTheme {
                TodayScreen(
                    date = date,
                    zone = ZoneId.of("UTC"),
                    state = TodayUiState.Empty,
                    onSelectDate = {},
                    onToday = {},
                    onRetry = {},
                    todayTasks = TodayTasksState.Data(tasks, total = 4),
                    onAllTasks = { allTasks++ },
                    onAddTask = { addTask++ },
                )
            }
        }

        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_tasks_card"))
        compose.onNodeWithTag("today_tasks_card").assertIsDisplayed()
        compose.onNodeWithTag("today_tasks_count").assertTextContains("4", substring = true)
        tasks.forEach { compose.onNodeWithText(it.title).assertExists() }
        compose.onNodeWithTag("today_tasks_all").performClick()
        compose.onNodeWithTag("today_tasks_add").performClick()
        compose.runOnIdle {
            assertEquals(1, allTasks)
            assertEquals(1, addTask)
        }
    }

    @Test fun taskPreviewErrorDoesNotHideEntriesHabitsOrSummary() {
        val date = LocalDate.parse("2026-10-19")
        val zone = ZoneId.of("UTC")
        val at = Instant.parse("2026-10-19T08:00:00Z")
        val event = LifeEvent(EventId.new(), EventType.JOURNAL, at, at, title = "Entry survives task error")
        compose.setContent {
            LifeOsTheme {
                TodayScreen(
                    date = date,
                    zone = zone,
                    state = TodayUiState.Data(listOf(event)),
                    onSelectDate = {}, onToday = {}, onRetry = {},
                    summaryState = DailySummaryState.Data(DailySummary(date, zone, 1, 0, 0)),
                    habitsState = HabitsUiState(date = date, trackingZone = zone, deviceDate = date, loading = false),
                    todayTasks = TodayTasksState.Error,
                )
            }
        }

        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_tasks_error"))
        compose.onNodeWithTag("today_tasks_error").assertIsDisplayed()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_habits_card"))
        compose.onNodeWithTag("today_habits_card").assertExists()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasText("Entry survives task error"))
        compose.onNodeWithText("Entry survives task error").assertExists()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_daily_summary"))
        compose.onNodeWithTag("today_daily_summary").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_records"))
        compose.onNodeWithTag("summary_records").assertExists()
    }

    private fun task(index: Int) = TodoTask(
        id = UUID.nameUUIDFromBytes("today-task-$index".toByteArray()),
        title = "Today task $index",
        note = null,
        dueDate = LocalDate.parse("2026-10-19"),
        completedAt = null,
        createdAt = Instant.EPOCH.plusSeconds(index.toLong()),
        updatedAt = Instant.EPOCH.plusSeconds(index.toLong()),
        revision = UUID.nameUUIDFromBytes("today-revision-$index".toByteArray()),
    )
}
