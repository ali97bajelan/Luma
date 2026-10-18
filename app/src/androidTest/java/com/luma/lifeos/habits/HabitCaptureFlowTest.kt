package com.luma.lifeos.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.ui.habits.HabitDayRow
import com.luma.lifeos.ui.habits.HabitDetailScreen
import com.luma.lifeos.ui.habits.HabitDetailUiState
import com.luma.lifeos.ui.habits.HabitsUiState
import com.luma.lifeos.ui.habits.PendingHabitLog
import com.luma.lifeos.ui.habits.TodayHabitsCard
import com.luma.lifeos.ui.habits.HabitDetailRoute
import com.luma.lifeos.ui.habits.HabitRow
import com.luma.lifeos.ui.habits.HabitEditorDraft
import com.luma.lifeos.ui.habits.HabitEditorError
import com.luma.lifeos.ui.habits.HabitEditorFieldError
import com.luma.lifeos.ui.habits.HabitEditorRoute
import com.luma.lifeos.ui.habits.HabitEditorScreen
import com.luma.lifeos.ui.habits.HabitEditorState
import com.luma.lifeos.ui.habits.HabitsRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.ui.habits.HabitLogDisabledReason
import com.luma.lifeos.ui.habits.HabitsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HabitCaptureFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: LumaDatabase
    private lateinit var repository: RoomHabitRepository
    private var previousLocale: Locale? = null
    private val zone = ZoneId.of("UTC")
    private val today: LocalDate get() = LocalDate.now(zone)

    @Before fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LumaDatabase::class.java).build()
        repository = RoomHabitRepository(database, Clock.system(zone))
    }

    @After fun tearDown() {
        database.close()
        previousLocale?.let(Locale::setDefault)
    }

    @Test fun createRepeatedlyLogUndoOneAndCorrectAnIndividualHistoryItem() {
        var createdId: UUID? = null
        compose.setContent {
            var screen by remember { mutableStateOf("habits") }
            var selectedDate by remember { mutableStateOf(today) }
            LifeOsTheme {
                when (screen) {
                    "habits" -> HabitsRoute(repository, selectedDate,
                        onSelectDate = { selectedDate = it }, onToday = { selectedDate = today },
                        onCreate = { screen = "editor" },
                        onHabit = { id, day -> createdId = id; selectedDate = day; screen = "detail" },
                        deviceDate = today, deviceZone = zone)
                    "editor" -> HabitEditorRoute(repository, null,
                        onDone = { id -> createdId = id; screen = "detail" }, onBack = { screen = "habits" }, deviceZone = zone)
                    else -> HabitDetailRoute(repository, requireNotNull(createdId), selectedDate,
                        onEdit = { screen = "editor" }, onBack = { screen = "habits" }, deviceZone = zone)
                }
            }
        }

        compose.onNodeWithTag("habit_create").performClick()
        compose.onNodeWithTag("habit_name").performTextInput("Read")
        compose.onNodeWithTag("habit_unit").performTextInput("pages")
        compose.onNodeWithTag("habit_target").performTextClearance()
        compose.onNodeWithTag("habit_target").performTextInput("8")
        compose.onNodeWithTag("habit_save").performClick()
        compose.waitUntil(10_000) { createdId != null }

        val habitId = requireNotNull(createdId)
        compose.onNodeWithTag("habit_detail_back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("habit_log_$habitId")).fetchSemanticsNodes().isNotEmpty() }
        repeat(9) { compose.onNodeWithTag("habit_log_$habitId").performClick() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("9 / 8 pages").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("habit_progress_$habitId", useUnmergedTree = true)
            .assertIsDisplayed().assertContentDescriptionEquals("Read, 9 of target 8 pages")
        compose.onNodeWithText("9 / 8 pages").assertIsDisplayed()
        compose.onNodeWithTag("habit_undo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("8 / 8 pages").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("8 / 8 pages").assertIsDisplayed()

        compose.onNodeWithTag("habit_open_$habitId").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("screen_habit_detail")).fetchSemanticsNodes().isNotEmpty() }
        val existing = runBlocking { repository.history(habitId, null, 0, 50).logs.first() }
        val recordedAt = existing.recordedAt
        val correctionTime = java.time.LocalTime.now(zone).minusMinutes(5).format(DateTimeFormatter.ofPattern("HH:mm"))
        compose.onNodeWithTag("habit_detail_list").performScrollToNode(hasTestTag("habit_edit_log_${existing.id}"))
        compose.onNodeWithTag("habit_edit_log_${existing.id}").performClick()
        compose.onNodeWithTag("habit_correction_time").performTextClearance()
        compose.onNodeWithTag("habit_correction_time").performTextInput(correctionTime)
        compose.onNodeWithTag("habit_save_correction").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                repository.getLog(existing.id)?.let { log ->
                    log.id == existing.id && log.recordedAt == recordedAt &&
                        log.occurredAt?.atZone(zone)?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) == correctionTime
                } == true
            }
        }
    }

    @Test fun noHabitAndNoHabitScheduledAreDifferentStatesAndFutureLogIsDisabled() = runBlocking {
        val id = UUID.randomUUID()
        val start = today.plusDays(1)
        repository.create(id, HabitSaveInput("Future walk", null, start, 1, DayOfWeek.entries.toSet(), zone))

        compose.setContent {
            var selectedDate by remember { mutableStateOf(today) }
            LifeOsTheme {
                HabitsRoute(repository, selectedDate, onSelectDate = { selectedDate = it }, onToday = { selectedDate = today },
                    onCreate = {}, onHabit = { _, _ -> }, deviceDate = today, deviceZone = zone)
            }
        }

        compose.onNodeWithTag("habits_none_scheduled").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasTestTag("habits_empty")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("habit_log_$id").assertIsNotEnabled()
        compose.onNodeWithText("This habit is not scheduled for this date.").assertIsDisplayed()
        compose.onNodeWithTag("day_next").performClick()
        compose.onNodeWithText("You cannot log a future date.").assertIsDisplayed()
        compose.onNodeWithTag("habit_log_$id").assertIsNotEnabled()
        Unit
    }

    @Test fun todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtThree() {
        val rows = buildList {
            repeat(3) { add(previewRow("old-$it", scheduled = false, paused = it == 0)) }
            repeat(4) { add(previewRow("scheduled-$it", scheduled = true)) }
        }
        compose.setContent {
            LifeOsTheme {
                TodayHabitsCard(HabitsUiState(date = today, days = rows, loading = false), today, zone,
                    onViewAll = {}, onCreate = {}, onHabit = { _, _ -> }, onLog = {}, onRetryLog = {},
                    onUndo = {}, onRetry = {})
            }
        }

        compose.onNodeWithTag("habit_row_${rows[3].day.habit.id}").assertIsDisplayed()
        compose.onNodeWithTag("habit_row_${rows[4].day.habit.id}").assertIsDisplayed()
        compose.onNodeWithTag("habit_row_${rows[5].day.habit.id}").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasTestTag("habit_row_${rows[0].day.habit.id}")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("habit_row_${rows[6].day.habit.id}")).fetchSemanticsNodes().size)
    }

    @Test fun committedSubMillisecondLogReconcilesFromSavedRequest() = runBlocking {
        val habitId = UUID.randomUUID()
        val start = today.minusDays(1)
        repository.create(habitId, HabitSaveInput("Precision", null, start, 1, DayOfWeek.entries.toSet(), zone))
        val logId = UUID.randomUUID()
        val now = Instant.now()
        val occurredAt = now.minusSeconds(2).plusNanos(987_654_321)
        val date = occurredAt.atZone(zone).toLocalDate()
        val input = HabitLogInput(habitId, date, zone, occurredAt)
        repository.log(logId, input)
        val saved = SavedStateHandle(mapOf("habit_pending_logs" to arrayListOf(
            "$logId|$habitId|$date|${zone.id}|$occurredAt",
        )))
        val viewModel = HabitsViewModel(repository, saved, java.time.Clock.fixed(now, zone))

        compose.waitUntil(10_000) { viewModel.state.value.pendingLogs.isEmpty() }

        assertEquals(1, repository.history(habitId, date, 0, 20).logs.size)
    }

    @Test fun detailRendersRetryForFailedLogAndUndoForCommittedLog() {
        val habitId = UUID.randomUUID()
        val logId = UUID.randomUUID()
        val pendingId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-01T00:00:00Z")
        val habit = HabitDefinition(habitId, "Read", "pages", today.minusDays(10), createdAt, createdAt)
        val schedule = HabitSchedule(UUID.randomUUID(), habitId, habit.start, 8, DayOfWeek.entries.toSet(), HabitLifecycle.ACTIVE)
        val day = HabitDay(habit, today, zone, schedule, 1, false)
        val log = HabitLog(logId, habitId, today, zone, null, createdAt, createdAt)
        val pending = PendingHabitLog(pendingId, HabitLogInput(habitId, today, zone, null), "write failed")
        var retried: UUID? = null
        var undone: UUID? = null
        val state = HabitsUiState(date = today, trackingZone = zone,
            days = listOf(HabitDayRow(day, canLog = true)), actionError = "write failed",
            pendingLogs = listOf(pending), undoableLog = log,
            detail = HabitDetailUiState(habit = habit, day = day, schedules = listOf(schedule), loading = false))

        compose.setContent {
            LifeOsTheme {
                HabitDetailScreen(state, habitId, today, null, onBack = {}, onEdit = {}, onLog = {},
                    onRetryLog = { retried = it }, onUndoLog = { undone = it }, onSkip = {}, onResumeToday = {},
                    onPauseResume = {}, onCancelPending = {}, onLoadMore = {}, onDeleteHabit = {}, onDeleteLog = {},
                    onEditLog = {}, onChangeDate = {}, onChangeTime = {}, onSaveCorrection = {},
                    onCancelCorrection = {}, onRetry = {})
            }
        }

        compose.onNodeWithTag("habit_detail_pending_$pendingId").assertIsDisplayed()
        compose.onNodeWithTag("habit_detail_retry_$pendingId").performClick()
        compose.onNodeWithTag("habit_detail_undo").performClick()
        compose.onNodeWithTag("habit_detail_action_error").assertIsDisplayed()
        assertEquals(pendingId, retried)
        assertEquals(logId, undone)
    }

    @Test fun historicalDetailUsesTodaysLifecycleForPauseResumeControl() {
        val habitId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-01T00:00:00Z")
        val habit = HabitDefinition(habitId, "Read", null, today.minusDays(10), createdAt, createdAt)
        val active = HabitSchedule(UUID.randomUUID(), habitId, habit.start, 1, DayOfWeek.entries.toSet(), HabitLifecycle.ACTIVE)
        val pausedToday = HabitSchedule(UUID.randomUUID(), habitId, today, 1, DayOfWeek.entries.toSet(), HabitLifecycle.PAUSED)
        val yesterday = today.minusDays(1)
        val historicalDay = HabitDay(habit, yesterday, zone, active, 0, false)
        var requestedLifecycle: HabitLifecycle? = null

        compose.setContent {
            LifeOsTheme {
                HabitDetailScreen(
                    HabitsUiState(trackingZone = zone, detail = HabitDetailUiState(
                        habit = habit, day = historicalDay, schedules = listOf(active, pausedToday), loading = false,
                    )),
                    habitId, yesterday, null, onBack = {}, onEdit = {}, onLog = {}, onRetryLog = {}, onUndoLog = {},
                    onSkip = {}, onResumeToday = {}, onPauseResume = { requestedLifecycle = it }, onCancelPending = {},
                    onLoadMore = {}, onDeleteHabit = {}, onDeleteLog = {}, onEditLog = {}, onChangeDate = {},
                    onChangeTime = {}, onSaveCorrection = {}, onCancelCorrection = {}, onRetry = {},
                )
            }
        }

        compose.onNodeWithTag("habit_pause_resume").assertIsDisplayed()
        compose.onNodeWithText("Resume starting tomorrow").assertIsDisplayed()
        compose.onNodeWithTag("habit_pause_resume").performClick()
        assertEquals(HabitLifecycle.ACTIVE, requestedLifecycle)
    }

    @Test fun defaultUnitPluralizesTargetWhileLogAccessibilityUsesOne() {
        val id = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-01T00:00:00Z")
        val habit = HabitDefinition(id, "Read", null, today.minusDays(10), createdAt, createdAt)
        val schedule = HabitSchedule(UUID.randomUUID(), id, habit.start, 2, DayOfWeek.entries.toSet(), HabitLifecycle.ACTIVE)
        val day = HabitDay(habit, today, zone, schedule, 0, false)

        compose.setContent {
            LifeOsTheme {
                HabitRow(HabitDayRow(day, canLog = true), emptyList(), null,
                    onOpen = {}, onLog = {}, onRetry = {}, compact = false)
            }
        }

        compose.onNodeWithText("0 / 2 times").assertIsDisplayed()
        compose.onNodeWithTag("habit_log_$id", useUnmergedTree = true)
            .assertContentDescriptionEquals("Log one Read time")
    }

    @Test fun futureStartEditAndArchiveUseStartDateWithoutPauseControl() = runBlocking {
        val habitId = UUID.randomUUID()
        val start = today.plusDays(7)
        repository.create(habitId, HabitSaveInput("Future walk", null, start, 2, DayOfWeek.entries.toSet(), zone))

        compose.setContent {
            var screen by remember { mutableStateOf("editor") }
            LifeOsTheme {
                if (screen == "editor") {
                    HabitEditorRoute(repository, habitId, onDone = { screen = "detail" }, onBack = {}, deviceZone = zone)
                } else {
                    HabitDetailRoute(repository, habitId, today, onEdit = {}, onBack = {}, deviceZone = zone)
                }
            }
        }

        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("habit_save")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("habit_target").performTextClearance()
        compose.onNodeWithTag("habit_target").performTextInput("4")
        compose.onNodeWithTag("habit_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("habit_archive")).fetchSemanticsNodes().isNotEmpty() }

        var schedule = runBlocking { repository.schedules(habitId).single() }
        assertEquals(start, schedule.effectiveFrom)
        assertEquals(4, schedule.target)
        compose.onNodeWithTag("habit_pause_resume").assertDoesNotExist()
        compose.onNodeWithTag("habit_archive").performClick()
        compose.waitUntil(10_000) {
            runBlocking { repository.schedules(habitId).single().lifecycle == HabitLifecycle.ARCHIVED }
        }
        schedule = runBlocking { repository.schedules(habitId).single() }
        assertEquals(start, schedule.effectiveFrom)
        assertEquals(HabitLifecycle.ARCHIVED, schedule.lifecycle)
    }

    @Test fun archivedHabitHistoryRemainsReadable() = runBlocking {
        val habitId = UUID.randomUUID()
        val logId = UUID.randomUUID()
        val selectedDate = today.minusDays(1)
        repository.create(habitId, HabitSaveInput("Archived read", null, today.minusDays(10), 1,
            DayOfWeek.entries.toSet(), zone))
        repository.log(logId, HabitLogInput(habitId, selectedDate, zone, null))
        repository.schedule(habitId, HabitScheduleInput(1, DayOfWeek.entries.toSet(), HabitLifecycle.ARCHIVED))

        compose.setContent {
            LifeOsTheme {
                HabitDetailRoute(repository, habitId, selectedDate, onEdit = {}, onBack = {}, deviceZone = zone)
            }
        }

        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("habit_log_$logId")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("habit_log_$logId").assertIsDisplayed()
        compose.onNodeWithTag("habit_pause_resume").assertDoesNotExist()
    }

    @Test fun historyCorrectionMovesLogToAnotherDateWithoutChangingItsIdentity() = runBlocking {
        val habitId = UUID.randomUUID()
        val logId = UUID.randomUUID()
        val originalDate = today.minusDays(2)
        val correctedDate = today.minusDays(1)
        val occurredAt = originalDate.atTime(8, 30).atZone(zone).toInstant()
        repository.create(habitId, HabitSaveInput("Read", "pages", today.minusDays(10), 1,
            DayOfWeek.entries.toSet(), zone))
        repository.log(logId, HabitLogInput(habitId, originalDate, zone, occurredAt))
        val savedLog = repository.getLog(logId)!!

        compose.setContent {
            LifeOsTheme {
                HabitDetailRoute(repository, habitId, today, onEdit = {}, onBack = {}, deviceZone = zone)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("habit_edit_log_$logId")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("habit_edit_log_$logId").performClick()
        compose.onNodeWithTag("habit_correction_date").performTextClearance()
        compose.onNodeWithTag("habit_correction_date").performTextInput(correctedDate.toString())
        compose.onNodeWithTag("habit_save_correction").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.getLog(logId)?.date == correctedDate } }

        val corrected = repository.getLog(logId)!!
        assertEquals(savedLog.id, corrected.id)
        assertEquals(savedLog.recordedAt, corrected.recordedAt)
        assertEquals(correctedDate, corrected.occurredAt?.atZone(zone)?.toLocalDate())
        assertEquals(java.time.LocalTime.of(8, 30), corrected.occurredAt?.atZone(zone)?.toLocalTime())
    }

    @Test fun compactPreviewKeepsSeparateFortyEightDpActionsAtLargeText() {
        val row = previewRow("Read", scheduled = true)
        compose.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, fontScale = 2f)) {
                LifeOsTheme {
                    Box(Modifier.width(320.dp)) {
                        TodayHabitsCard(HabitsUiState(date = today, days = listOf(row), loading = false), today, zone,
                            onViewAll = {}, onCreate = {}, onHabit = { _, _ -> }, onLog = {}, onRetryLog = {},
                            onUndo = {}, onRetry = {})
                    }
                }
            }
        }

        val open = compose.onNodeWithTag("habit_open_${row.day.habit.id}", useUnmergedTree = true)
        val log = compose.onNodeWithTag("habit_log_${row.day.habit.id}", useUnmergedTree = true)
        open.assertIsDisplayed()
        log.assertIsDisplayed()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val openBounds = open.fetchSemanticsNode().boundsInRoot
        val logBounds = log.fetchSemanticsNode().boundsInRoot
        assertEquals(true, openBounds.height / density >= 48f)
        assertEquals(true, logBounds.height / density >= 48f)
        assertEquals(true, openBounds.bottom <= logBounds.top || logBounds.bottom <= openBounds.top ||
            openBounds.right <= logBounds.left || logBounds.right <= openBounds.left)
        assertEquals(true, open.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString().contains("Read"))
        assertEquals(true, log.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString().contains("Log one"))
    }

    @Test fun editorValidationUsesLocalizedVisibleAndAccessibilityCopy() {
        val state = HabitEditorState(
            draft = HabitEditorDraft(start = today),
            loading = false,
            ready = true,
            error = HabitEditorError.INPUT,
            textError = true,
            fieldError = HabitEditorFieldError.NAME_TOO_LONG,
        )
        compose.setContent {
            LifeOsTheme {
                HabitEditorScreen(state, zone, onBack = {}, onRetry = {}, onName = {}, onUnit = {},
                    onTarget = {}, onStart = {}, onWeekdays = {}, onSave = {})
            }
        }

        val message = "Habit names are limited to 100 characters."
        compose.onNodeWithTag("habit_editor_error").assertIsDisplayed()
        compose.onNodeWithTag("habit_name").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, message),
        )
        compose.onNodeWithText(message).assertIsDisplayed()
    }

    private fun previewRow(name: String, scheduled: Boolean, paused: Boolean = false): HabitDayRow {
        val id = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-01T00:00:00Z")
        val habit = HabitDefinition(id, name, null, today.minusDays(10), createdAt, createdAt)
        val weekdays = if (scheduled) DayOfWeek.entries.toSet() else DayOfWeek.entries.toSet() - today.dayOfWeek
        val lifecycle = if (paused) HabitLifecycle.PAUSED else HabitLifecycle.ACTIVE
        val schedule = HabitSchedule(UUID.randomUUID(), id, habit.start, 1, weekdays, lifecycle)
        val day = HabitDay(habit, today, zone, schedule, 0, false)
        return HabitDayRow(day, canLog = day.state == com.luma.lifeos.domain.habit.HabitDayState.NO_RECORDS,
            disabledReason = if (day.state == com.luma.lifeos.domain.habit.HabitDayState.NOT_SCHEDULED) HabitLogDisabledReason.NOT_SCHEDULED else null)
    }
}
