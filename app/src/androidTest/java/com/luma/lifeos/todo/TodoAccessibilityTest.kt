package com.luma.lifeos.todo

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.language.AppLanguage
import com.luma.lifeos.language.AppLanguages
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.todo.TodoListScreen
import com.luma.lifeos.ui.todo.TodoListUiState
import com.luma.lifeos.ui.todo.TodoActionFeedbackCard
import com.luma.lifeos.ui.todo.TodoActionResult
import com.luma.lifeos.ui.todo.TodoListRowError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TodoAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun useEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @After fun restoreEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @Test fun completingReopeningAndDeletingKeepNextControlsReachable() {
        val title = "T07 focus ${System.nanoTime()}"
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_tasks").performClick()
        compose.onNodeWithTag("todo_list_add").performClick()
        compose.onNodeWithTag("todo_editor_title").performTextInput(title)
        compose.onNodeWithTag("todo_editor_save").performClick()
        awaitTag("screen_todo_list")
        compose.onNodeWithText(title).assertExists()

        tapTaskCheckbox(title, R.string.todo_mark_complete)
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithTag("todo_list_add").assertIsDisplayed().assertHasClickAction()

        compose.onNodeWithTag("todo_list_completed_tab").performClick()
        compose.onNodeWithText(title).assertExists()
        tapTaskCheckbox(title, R.string.todo_reopen)
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithTag("todo_list_open_tab").assertIsDisplayed().assertHasClickAction()

        compose.onNodeWithTag("todo_list_open_tab").performClick()
        compose.onNodeWithText(title).performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_status").performClick()
        awaitTag("screen_todo_list")
        compose.onNodeWithTag("todo_list_completed_tab").performClick()
        compose.onNodeWithText(title).performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_delete").performClick()
        compose.onNodeWithTag("todo_editor_confirm_delete").performClick()
        awaitTag("screen_todo_list")
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithTag("todo_list_open_tab").assertIsDisplayed().assertHasClickAction()
    }

    private fun tapTaskCheckbox(title: String, actionResource: Int) {
        var action = ""
        compose.activityRule.scenario.onActivity { action = it.getString(actionResource) }
        val matching = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single { node ->
            val description = node.config[SemanticsProperties.ContentDescription].joinToString(" ")
            description.contains(title) && description.contains(action)
        }
        val tag = matching.config[SemanticsProperties.TestTag]
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    }

    private fun selectLanguage(language: AppLanguage) {
        compose.activityRule.scenario.onActivity { assertTrue(AppLanguages.select(it, language)) }
        compose.waitUntil(15_000) {
            var selected = false
            compose.activityRule.scenario.onActivity { selected = AppLanguages.current(it) == language }
            selected
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

}

class TodoAccessibilitySemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun taskTargetsAndHeadingsRemainDistinctAtNarrowWidthAndLargeText() {
        val today = LocalDate.now()
        val tasks = (0 until 50).map(::task)
        val state = listState(tasks, total = 51, today = today)
        var opened: UUID? = null
        var statusChange: Pair<UUID, Boolean>? = null

        compose.setContent {
            val narrowLargeText = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                fontScale = 2f
            }
            CompositionLocalProvider(LocalConfiguration provides narrowLargeText) {
                LifeOsTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        TodoListScreen(
                            state = state,
                            onBack = {},
                            onAddTask = {},
                            onOpenTask = { opened = it },
                            onSelectCompleted = {},
                            onSetCompleted = { id, completed -> statusChange = id to completed },
                            onRetry = {},
                            onDismissRowError = {},
                            onRetryLoad = {},
                            onLoadMore = {},
                            undoFeedback = {},
                        )
                    }
                }
            }
        }

        val first = tasks.first()
        val checkboxTag = "todo_status_${first.id}"
        compose.onNodeWithTag("todo_group_overdue")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onAllNodesWithTag(checkboxTag, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(checkboxTag, useUnmergedTree = true)
            .assertIsOff()
            .assertHasClickAction()

        compose.onNodeWithTag("todo_open_${first.id}", useUnmergedTree = true)
            .assertHasClickAction()
            .performClick()
        assertEquals(first.id, opened)
        assertNull(statusChange)

        compose.onNodeWithTag(checkboxTag, useUnmergedTree = true).performClick()
        assertEquals(first.id to true, statusChange)

        compose.onNodeWithTag("todo_list_count").assertTextContains("51", substring = true)
        compose.onNodeWithTag("todo_list_rows")
            .performScrollToNode(hasTestTag("todo_list_load_more"))
        compose.onNodeWithTag("todo_list_load_more").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun completingARowLeavesTheNextVisibleTaskActionable() {
        val today = LocalDate.now()
        val first = task(1)
        val second = task(2)
        val state = mutableStateOf(listState(listOf(first, second), total = 2, today = today))

        compose.setContent {
            LifeOsTheme {
                TodoListScreen(
                    state = state.value,
                    onBack = {},
                    onAddTask = {},
                    onOpenTask = {},
                    onSelectCompleted = {},
                    onSetCompleted = { id, _ ->
                        val remaining = state.value.page.items.filterNot { it.id == id }
                        state.value = state.value.copy(page = TodoPage(remaining, remaining.size))
                    },
                    onRetry = {},
                    onDismissRowError = {},
                    onRetryLoad = {},
                    onLoadMore = {},
                    undoFeedback = {},
                )
            }
        }

        compose.onNodeWithTag("todo_status_${first.id}", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("todo_status_${first.id}", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("todo_open_${second.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test fun manyFailuresDoNotHideTasks() {
        val today = LocalDate.now()
        val tasks = (0 until 12).map { task(100 + it) }
        val visibleTask = task(500)
        val state = listState(listOf(visibleTask), total = 1, today = today).copy(
            rowErrors = tasks.associate { it.id to TodoListRowError.CONFLICT },
            rowErrorTitles = tasks.associate { it.id to it.title },
        )

        compose.setContent {
            val largeLandscape = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 640
                screenHeightDp = 320
                fontScale = 2f
            }
            CompositionLocalProvider(LocalConfiguration provides largeLandscape) {
                LifeOsTheme {
                    Box(Modifier.width(640.dp).height(320.dp)) {
                        TodoListScreen(
                            state = state,
                            onBack = {}, onAddTask = {}, onOpenTask = {}, onSelectCompleted = {},
                            onSetCompleted = { _, _ -> }, onRetry = {}, onDismissRowError = {},
                            onRetryLoad = {}, onLoadMore = {}, undoFeedback = {},
                        )
                    }
                }
            }
        }

        val finalRecovery = tasks.last()
        compose.onNodeWithTag("todo_list_rows")
            .performScrollToNode(hasTestTag("todo_row_error_${finalRecovery.id}"))
        compose.onNodeWithTag("todo_row_error_${finalRecovery.id}").assertIsDisplayed()
        compose.onNodeWithTag("todo_list_rows")
            .performScrollToNode(hasTestTag("todo_open_${visibleTask.id}"))
        compose.onNodeWithTag("todo_open_${visibleTask.id}").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun undoRemainsDiscoverableAsAStableListItem() {
        val today = LocalDate.now()
        val task = task(600)
        val result = TodoActionResult(task.id, completed = true, resultingRevision = UUID.randomUUID())
        val state = listState(listOf(task), total = 1, today = today).copy(undoableCompletion = result)
        var undoCalls = 0

        compose.setContent {
            LifeOsTheme {
                TodoListScreen(
                    state = state,
                    onBack = {}, onAddTask = {}, onOpenTask = {}, onSelectCompleted = {},
                    onSetCompleted = { _, _ -> }, onRetry = {}, onDismissRowError = {},
                    onRetryLoad = {}, onLoadMore = {},
                    undoFeedback = {
                        TodoActionFeedbackCard(
                            result = result, busy = false, failed = false, terminal = false,
                            onUndo = { undoCalls++ }, onDismiss = {},
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag("todo_list_rows")
            .performScrollToNode(hasTestTag("todo_completion_feedback"))
        compose.onNodeWithTag("todo_completion_feedback").assertIsDisplayed()
        compose.onNodeWithTag("todo_undo").performClick()
        compose.runOnIdle { assertEquals(1, undoCalls) }
    }

    @Test fun filterChangeKeepsRecoveryActionIdentity() {
        val today = LocalDate.now()
        val task = task(700)
        val state = mutableStateOf(
            listState(listOf(task), total = 1, today = today).copy(
                rowErrors = mapOf(task.id to TodoListRowError.CONFLICT),
                rowErrorTitles = mapOf(task.id to task.title),
            ),
        )
        var dismissed: UUID? = null

        compose.setContent {
            LifeOsTheme {
                TodoListScreen(
                    state = state.value,
                    onBack = {}, onAddTask = {}, onOpenTask = {},
                    onSelectCompleted = { completed -> state.value = state.value.copy(completed = completed) },
                    onSetCompleted = { _, _ -> }, onRetry = {}, onDismissRowError = { dismissed = it },
                    onRetryLoad = {}, onLoadMore = {}, undoFeedback = {},
                )
            }
        }

        compose.onNodeWithTag("todo_list_completed_tab").performClick()
        compose.onNodeWithTag("todo_row_error_${task.id}").assertExists()
        compose.onNodeWithTag("todo_list_open_tab").performClick()
        compose.onNodeWithTag("todo_row_error_${task.id}").assertExists()
        compose.onNodeWithTag("todo_dismiss_${task.id}").performClick()
        compose.runOnIdle { assertEquals(task.id, dismissed) }
    }

    @Test fun recompositionKeepsScrolledRecoveryItemByKey() {
        val today = LocalDate.now()
        val failures = (0 until 8).map { task(800 + it) }
        val state = mutableStateOf(
            listState(listOf(task(900)), total = 1, today = today).copy(
                rowErrors = failures.associate { it.id to TodoListRowError.CONFLICT },
                rowErrorTitles = failures.associate { it.id to it.title },
            ),
        )

        compose.setContent {
            LifeOsTheme {
                TodoListScreen(
                    state = state.value,
                    onBack = {}, onAddTask = {}, onOpenTask = {}, onSelectCompleted = {},
                    onSetCompleted = { _, _ -> }, onRetry = {}, onDismissRowError = {},
                    onRetryLoad = {}, onLoadMore = {}, undoFeedback = {},
                )
            }
        }

        val anchored = failures.last()
        compose.onNodeWithTag("todo_list_rows")
            .performScrollToNode(hasTestTag("todo_row_error_${anchored.id}"))
        compose.runOnIdle {
            val added = task(950)
            state.value = state.value.copy(
                rowErrors = linkedMapOf(added.id to TodoListRowError.CONFLICT) + state.value.rowErrors,
                rowErrorTitles = linkedMapOf(added.id to added.title) + state.value.rowErrorTitles,
            )
        }
        compose.onNodeWithTag("todo_row_error_${anchored.id}").assertIsDisplayed()
    }

    @Test fun feedbackDoesNotAdvanceTaskPaging() {
        val today = LocalDate.now()
        val tasks = (0 until 50).map { task(1_000 + it) }
        val errors = (0 until 12).associate { index ->
            val id = UUID.nameUUIDFromBytes("off-page-$index".toByteArray())
            id to TodoListRowError.CONFLICT
        }
        val titles = errors.keys.associateWith { "Retained $it" }
        var loadMoreCalls = 0
        val state = listState(tasks, total = 51, today = today).copy(rowErrors = errors, rowErrorTitles = titles)

        compose.setContent {
            LifeOsTheme {
                TodoListScreen(
                    state = state,
                    onBack = {}, onAddTask = {}, onOpenTask = {}, onSelectCompleted = {},
                    onSetCompleted = { _, _ -> }, onRetry = {}, onDismissRowError = {},
                    onRetryLoad = {}, onLoadMore = { loadMoreCalls++ }, undoFeedback = {},
                )
            }
        }

        compose.onNodeWithTag("todo_list_rows").performScrollToNode(hasTestTag("todo_list_load_more"))
        compose.onNodeWithTag("todo_list_load_more").performClick()
        compose.runOnIdle { assertEquals(1, loadMoreCalls) }
    }

    private fun listState(items: List<TodoTask>, total: Int, today: LocalDate) = TodoListUiState(
        page = TodoPage(items, total),
        pageLimit = 50,
        today = today,
        loading = false,
        dataFresh = true,
    )

    private fun task(index: Int): TodoTask {
        val created = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index.toLong())
        return TodoTask(
            id = UUID.nameUUIDFromBytes("t07-task-$index".toByteArray()),
            title = "Task $index",
            note = null,
            dueDate = LocalDate.now().minusDays(1),
            completedAt = null,
            createdAt = created,
            updatedAt = created,
            revision = UUID.nameUUIDFromBytes("t07-revision-$index".toByteArray()),
        )
    }
}
