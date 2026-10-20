package com.luma.lifeos.todo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.luma.lifeos.ui.todo.TodayTaskRowFeedback
import com.luma.lifeos.ui.todo.TodayTasksActionState
import com.luma.lifeos.ui.todo.TodoListRowError
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
                    habitsState = HabitsUiState(date = date, loadedDate = date, trackingZone = zone, deviceDate = date, loading = false),
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

    @Test fun existingConflictCanOpenFromVisibleAndRetainedFeedback() {
        val date = LocalDate.parse("2026-10-19")
        val visible = task(4)
        val retained = task(5)
        val opened = mutableListOf<UUID>()
        val actions = TodayTasksActionState(
            rowFeedback = mapOf(
                visible.id to TodayTaskRowFeedback(visible.title, TodoListRowError.CONFLICT, completesTask = true),
                retained.id to TodayTaskRowFeedback(retained.title, TodoListRowError.CONFLICT, completesTask = true),
            ),
        )
        compose.setContent {
            LifeOsTheme {
                TodayScreen(
                    date = date,
                    zone = ZoneId.of("UTC"),
                    state = TodayUiState.Empty,
                    onSelectDate = {}, onToday = {}, onRetry = {},
                    todayTasks = TodayTasksState.Data(listOf(visible), total = 1),
                    todayTaskActions = actions,
                    onTask = { opened.add(it) },
                )
            }
        }

        compose.onNodeWithTag("today_task_feedback_open_${visible.id}").performClick()
        compose.runOnIdle { assertEquals(listOf(visible.id), opened) }
        compose.onNodeWithTag("today_tasks_feedback_scroll")
            .performScrollToNode(hasTestTag("today_task_feedback_open_${retained.id}"))
        compose.onNodeWithTag("today_task_feedback_open_${retained.id}").performClick()
        compose.runOnIdle { assertEquals(listOf(visible.id, retained.id), opened) }
        compose.onNodeWithTag("todo_status_${visible.id}", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test fun missingTaskCannotOpenOrCompleteAndDismissDoesNotWrite() {
        val date = LocalDate.parse("2026-10-19")
        val missing = task(6)
        var opened = 0
        var completions = 0
        var dismissals = 0
        compose.setContent {
            LifeOsTheme {
                TodayScreen(
                    date = date,
                    zone = ZoneId.of("UTC"),
                    state = TodayUiState.Empty,
                    onSelectDate = {}, onToday = {}, onRetry = {},
                    todayTasks = TodayTasksState.Data(listOf(missing), total = 1),
                    todayTaskActions = TodayTasksActionState(
                        rowFeedback = mapOf(missing.id to TodayTaskRowFeedback(
                            missing.title, TodoListRowError.MISSING, completesTask = true,
                        )),
                    ),
                    onTask = { opened++ },
                    onSetTaskCompleted = { _, _ -> completions++ },
                    onDismissTaskError = { dismissals++ },
                )
            }
        }

        compose.onNodeWithTag("todo_open_${missing.id}", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("todo_status_${missing.id}", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("today_task_feedback_dismiss_${missing.id}").performClick()
        compose.runOnIdle {
            assertEquals(0, opened)
            assertEquals(0, completions)
            assertEquals(1, dismissals)
        }
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
