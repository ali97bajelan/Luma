package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.luma.lifeos.domain.focus.FocusMetadata
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusTimerMode
import com.luma.lifeos.domain.focus.createFocusSession
import com.luma.lifeos.focus.FocusRuntimeState
import com.luma.lifeos.focus.FocusScreenState
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FocusSetupContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun presetsAreTimedOnlyAndCustomFocusKeepsTheAuthoredDuration() {
        val setup = renderSetup(
            FocusSetupState(name = "Read chapter", timedMinutesText = "42"),
        )

        compose.onNodeWithTag("focus_preset_15").assertDoesNotExist()
        compose.onNodeWithTag("focus_preset_25").assertDoesNotExist()
        compose.onNodeWithTag("focus_preset_50").assertDoesNotExist()

        compose.onNodeWithTag("focus_mode_timed").performClick()
        compose.onNodeWithTag("focus_preset_15").assertIsDisplayed()
        compose.onNodeWithTag("focus_preset_25").assertIsDisplayed()
        compose.onNodeWithTag("focus_preset_50").assertIsDisplayed()
        compose.onNodeWithTag("focus_preset_50").performClick()
        compose.runOnIdle {
            assertEquals(FocusTimerMode.TIMED, setup.value.timerMode)
            assertEquals("50", setup.value.timedMinutesText)
        }

        compose.onNodeWithTag("focus_mode_count_up").performClick()
        compose.onNodeWithTag("focus_mode_timed").performClick()
        compose.runOnIdle { assertEquals("50", setup.value.timedMinutesText) }

        compose.onNodeWithTag("focus_custom_duration").performClick()
        compose.onNodeWithTag("focus_timed_minutes").assertIsFocused()
        compose.runOnIdle { assertEquals("50", setup.value.timedMinutesText) }
    }

    @Test
    fun selectedTaskCanReturnFromPickerAndBeRemovedWithoutLosingTheDraft() {
        val existingTaskId = UUID.fromString("30000000-0000-0000-0000-000000000001")
        val pickerTask = pickerTask("Available task")
        val repository = FocusPickerTestRepository(listOf(pickerTask))
        val setup = mutableStateOf(
            FocusSetupState(
                name = "My authored name",
                tag = "Work",
                selectedTaskId = existingTaskId,
                selectedTaskTitle = "Existing task",
                taskOrigin = true,
            ),
        )
        val showingPicker = mutableStateOf(false)

        compose.setContent {
            LifeOsTheme {
                if (showingPicker.value) {
                    FocusTaskPickerRoute(
                        repository = repository,
                        isAuthorized = true,
                        onBack = { showingPicker.value = false },
                        onSelectTask = {},
                    )
                } else {
                    FocusSetupHost(
                        setup = setup.value,
                        onOpenTaskPicker = { showingPicker.value = true },
                        onClearTaskSelection = {
                            setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.TaskSelectionCleared)
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("focus_task_selected").assertTextContains("Existing task")
        compose.onNodeWithTag("focus_task_change").performClick()
        compose.onNodeWithTag("focus_task_picker").assertIsDisplayed()
        compose.onNodeWithTag("focus_task_picker_item_${pickerTask.id}").assertIsDisplayed()
        compose.onNodeWithTag("page_back").performClick()

        compose.onNodeWithTag("focus_task_selected").assertTextContains("Existing task")
        compose.runOnIdle {
            assertEquals("My authored name", setup.value.name)
            assertEquals("Work", setup.value.tag)
            assertEquals(existingTaskId, setup.value.selectedTaskId)
        }

        compose.onNodeWithTag("focus_task_remove").performClick()
        compose.runOnIdle {
            assertEquals("My authored name", setup.value.name)
            assertEquals("Work", setup.value.tag)
            assertNull(setup.value.selectedTaskId)
            assertTrue(setup.value.taskOrigin)
        }
    }

    @Test
    fun emptyTaskPickerExplainsThatTaskSelectionIsOptional() {
        compose.setContent {
            LifeOsTheme {
                FocusTaskPickerRoute(
                    repository = FocusPickerTestRepository(emptyList()),
                    isAuthorized = true,
                    onBack = {},
                    onSelectTask = {},
                )
            }
        }

        compose.onNodeWithTag("focus_task_picker_empty")
            .assertTextContains("Choosing a task is optional")
    }

    @Test
    fun usingRecentSettingsConfirmsBeforeReplacingAnAuthoredDraft() {
        val session = createFocusSession(
            sessionId = UUID.fromString("30000000-0000-0000-0000-000000000002"),
            commandId = UUID.fromString("30000000-0000-0000-0000-000000000003"),
            sample = FocusTimeSample(Instant.parse("2026-11-01T09:00:00Z"), 0, "boot-a"),
        ).copy(metadata = FocusMetadata(name = "Previous work"))
        val recentState = FocusRecentWorkState.Ready(
            request = FocusRecentWorkQuery(
                generation = UUID.fromString("30000000-0000-0000-0000-000000000004"),
                includeTaskOrigin = false,
                retry = 0,
            ),
            session = session,
        )
        var requestedSession: UUID? = null

        compose.setContent {
            LifeOsTheme {
                FocusRecentWorkCard(
                    state = recentState,
                    hasUserAuthoredDraft = true,
                    canRepeat = true,
                    onRepeatSession = { requestedSession = it },
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithTag("focus_recent_work_use_settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("focus_recent_replace_confirm").assertIsDisplayed()
        compose.runOnIdle { assertNull(requestedSession) }

        compose.onNodeWithTag("focus_recent_replace_confirm").performClick()
        compose.runOnIdle { assertEquals(session.id, requestedSession) }
    }

    private fun renderSetup(
        initialSetup: FocusSetupState,
        onOpenTaskPicker: (() -> Unit)? = null,
        onClearTaskSelection: () -> Unit = {},
    ): MutableState<FocusSetupState> {
        val setup = mutableStateOf(initialSetup)
        compose.setContent {
            LifeOsTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    FocusSetupContent(
                        state = FocusScreenState(
                            runtime = FocusRuntimeState(initialized = true, loading = false),
                            setup = setup.value,
                        ),
                        onNameChange = { setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.NameChanged(it)) },
                        onNameBlur = { setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.NameBlurred) },
                        onTagChange = { setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.TagChanged(it)) },
                        onTagBlur = { setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.TagBlurred) },
                        onTagAreaExpandedChange = {
                            setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.TagAreaExpandedChanged(it))
                        },
                        onTimerModeChange = {
                            setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.TimerModeChanged(it))
                        },
                        onTimedMinutesChange = {
                            setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.TimedMinutesChanged(it))
                        },
                        onTimedMinutesBlur = {
                            setup.value = reduceFocusSetupState(setup.value, FocusSetupEvent.TimedMinutesBlurred)
                        },
                        onRepeatSession = {},
                        onOpenTaskPicker = onOpenTaskPicker,
                        onClearTaskSelection = onClearTaskSelection,
                        onRetryRecentWork = {},
                    )
                }
            }
        }
        return setup
    }

    @Composable
    private fun FocusSetupHost(
        setup: FocusSetupState,
        onOpenTaskPicker: () -> Unit,
        onClearTaskSelection: () -> Unit,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            FocusSetupContent(
                state = FocusScreenState(
                    runtime = FocusRuntimeState(initialized = true, loading = false),
                    setup = setup,
                ),
                onNameChange = {},
                onNameBlur = {},
                onTagChange = {},
                onTagBlur = {},
                onTagAreaExpandedChange = {},
                onTimerModeChange = {},
                onTimedMinutesChange = {},
                onTimedMinutesBlur = {},
                onRepeatSession = {},
                onOpenTaskPicker = onOpenTaskPicker,
                onClearTaskSelection = onClearTaskSelection,
                onRetryRecentWork = {},
            )
        }
    }

    private fun pickerTask(title: String): TodoTask {
        val createdAt = Instant.parse("2026-11-01T09:00:00Z")
        return TodoTask(
            id = UUID.fromString("30000000-0000-0000-0000-000000000005"),
            title = title,
            note = null,
            dueDate = null,
            dueTime = null,
            completedAt = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            revision = UUID.fromString("30000000-0000-0000-0000-000000000006"),
        )
    }

    private class FocusPickerTestRepository(
        private val tasks: List<TodoTask>,
    ) : TodoRepository {
        override fun observeList(completed: Boolean, limit: Int): Flow<TodoPage> =
            flowOf(TodoPage(emptyList(), 0))

        override fun observeToday(date: LocalDate, limit: Int): Flow<TodoPage> =
            flowOf(TodoPage(emptyList(), 0))

        override suspend fun searchOpen(query: String, offset: Int, limit: Int): TodoPage {
            val matching = tasks.filter { !it.isCompleted && it.title.contains(query, ignoreCase = true) }
            return TodoPage(matching.drop(offset).take(limit), matching.size)
        }

        override suspend fun get(id: UUID): TodoTask? = tasks.firstOrNull { it.id == id }

        override suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask =
            error("The focus picker test repository is read-only")

        override suspend fun update(
            id: UUID,
            expectedRevision: UUID,
            nextRevision: UUID,
            input: TodoInput,
        ): TodoTask = error("The focus picker test repository is read-only")

        override suspend fun setCompleted(
            id: UUID,
            expectedRevision: UUID,
            nextRevision: UUID,
            completed: Boolean,
        ): TodoTask = error("The focus picker test repository is read-only")

        override suspend fun delete(id: UUID, expectedRevision: UUID): Unit =
            error("The focus picker test repository is read-only")
    }
}
