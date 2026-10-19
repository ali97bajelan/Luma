package com.luma.lifeos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.ui.habits.HabitDayRow
import com.luma.lifeos.ui.habits.HabitsUiState
import java.util.UUID
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.summary.DailySummary
import com.luma.lifeos.domain.summary.MoodSummary
import com.luma.lifeos.domain.summary.ScaleTotal
import com.luma.lifeos.domain.summary.ActivityTotal
import com.luma.lifeos.domain.usage.AppUsageTotal
import com.luma.lifeos.domain.usage.DailyUsageReport
import com.luma.lifeos.ui.summary.DailySummaryState
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.today.TodayScreen
import com.luma.lifeos.ui.today.TodayUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyDayShowsCaptureScheduledHabitAndRecentEntriesBeforeScrolling() {
        val day = LocalDate.now(ZoneId.of("UTC"))
        val instant = day.atStartOfDay(ZoneId.of("UTC")).toInstant()
        val habit = HabitDefinition(UUID.randomUUID(), "Water", "glasses", day, instant, instant)
        val schedule = HabitSchedule(UUID.randomUUID(), habit.id, day, 10, setOf(day.dayOfWeek), HabitLifecycle.ACTIVE)
        val habits = HabitsUiState(date = day, loadedDate = day, trackingZone = ZoneId.of("UTC"), deviceDate = day,
            days = listOf(HabitDayRow(HabitDay(habit, day, ZoneId.of("UTC"), schedule, 1, false), true)), loading = false)
        compose.setContent {
            LifeOsTheme {
                Scaffold(bottomBar = {
                    Column {
                        Spacer(Modifier.fillMaxWidth().height(72.dp))
                        NavigationBar {}
                    }
                }) { padding ->
                    Box(Modifier.padding(padding)) {
                        TodayScreen(day, ZoneId.of("UTC"), TodayUiState.Empty, {}, {}, {}, habitsState = habits,
                            summaryState = DailySummaryState.Data(DailySummary(day, ZoneId.of("UTC"), 0, 0, 0)))
                    }
                }
            }
        }
        compose.onNodeWithTag("today_capture_mood").assertIsDisplayed()
        compose.onNodeWithTag("habit_log_${habit.id}").assertIsDisplayed()
        compose.onNodeWithTag("today_recent_entries").assertIsDisplayed()
        compose.onNodeWithTag("summary_records").assertDoesNotExist()
    }

    @Test fun anEntryPreviewIsVisibleWithoutScrollingPastTheDailySummary() {
        val day = LocalDate.parse("2026-10-11")
        val zone = ZoneId.of("UTC")
        val instant = Instant.parse("2026-10-11T08:30:00Z")
        val event = LifeEvent(EventId.new(), EventType.MOOD, instant, instant, title = "A calm afternoon",
            note = "A short walk helped me recharge.")
        val summary = DailySummary(day, zone, 1, 0, 0,
            mood = MoodSummary(ScaleTotal(1, 4), ScaleTotal(1, 2), ScaleTotal(1, 3)),
            usage = DailyUsageReport(day, zone, listOf(AppUsageTotal("example.reader", "Reader", 180_000),
                AppUsageTotal("example.music", "Music", 120_000), AppUsageTotal("example.maps.long.package.name", null, 90_000)), 390_000),
            activities = listOf(ActivityTotal("steps", "count", EventSource.HEALTH_CONNECT, 1, BigDecimal("1200")),
                ActivityTotal("distance", "km", EventSource.MANUAL, 1, BigDecimal("4.5"))))
        compose.setContent {
            LifeOsTheme {
                Scaffold(bottomBar = { NavigationBar {} }) { padding ->
                    Box(Modifier.padding(padding)) {
                        TodayScreen(day, zone, TodayUiState.Data(listOf(event)), {}, {}, {},
                            summaryState = DailySummaryState.Data(summary))
                    }
                }
            }
        }
        compose.onNodeWithText("A calm afternoon").assertIsDisplayed()
        compose.onNodeWithText("A short walk helped me recharge.").assertIsDisplayed()
        compose.onNodeWithTag("summary_records").assertDoesNotExist()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_daily_summary"))
        compose.onNodeWithTag("today_daily_summary").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_usage"))
        compose.onNodeWithTag("summary_records").assertExists()
        compose.onNodeWithTag("summary_mood").assertIsDisplayed()
        compose.onNodeWithTag("summary_usage").assertIsDisplayed()
        compose.onNodeWithTag("summary_journals").assertDoesNotExist()
        compose.onNodeWithTag("summary_memories").assertDoesNotExist()
        compose.onNodeWithTag("summary_energy").assertDoesNotExist()
        compose.onNodeWithTag("summary_stress").assertDoesNotExist()
        compose.onNodeWithTag("summary_usage_app_example.reader").assertDoesNotExist()
        compose.onNodeWithTag("summary_activity_steps_count_HEALTH_CONNECT").assertDoesNotExist()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_details"))
        compose.onNodeWithTag("summary_details").performClick()
        compose.onNodeWithTag("summary_journals").assertExists()
        compose.onNodeWithTag("summary_memories").assertExists()
        compose.onNodeWithTag("summary_energy").assertExists()
        compose.onNodeWithTag("summary_stress").assertExists()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_usage_package_example.maps.long.package.name"))
        compose.onNodeWithTag("summary_usage_package_example.maps.long.package.name").assertIsDisplayed()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_activity_steps_count_HEALTH_CONNECT"))
        compose.onNodeWithTag("summary_activity_steps_count_HEALTH_CONNECT").assertIsDisplayed()
    }

    @Test fun todayShowsOnlyTheThreeHighestUsageAppsOverOneMinute() {
        val day = LocalDate.parse("2026-10-11")
        val usage = DailyUsageReport(day, ZoneId.of("UTC"), listOf(
            AppUsageTotal("example.beta", "Beta", 300_000),
            AppUsageTotal("example.alpha", "Alpha", 180_000),
            AppUsageTotal("example.gamma", "Gamma", 90_001),
            AppUsageTotal("example.delta", "Delta", 90_000),
            AppUsageTotal("example.epsilon", "Epsilon", 60_001),
            AppUsageTotal("example.short", "Short", 60_000),
        ), 780_002)
        val summary = DailySummary(day, ZoneId.of("UTC"), 0, 0, 0, usage = usage)
        compose.setContent {
            LifeOsTheme {
                TodayScreen(day, ZoneId.of("UTC"), TodayUiState.Empty, onSelectDate = {}, onToday = {},
                    onRetry = {}, summaryState = DailySummaryState.Data(summary))
            }
        }

        listOf("example.beta", "example.alpha", "example.gamma").forEach { packageName ->
            compose.onNodeWithTag("summary_usage_app_$packageName").assertDoesNotExist()
        }
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_daily_summary"))
        compose.onNodeWithTag("today_daily_summary").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_details"))
        compose.onNodeWithTag("summary_details").performClick()
        listOf("example.beta", "example.alpha", "example.gamma").forEach { packageName ->
            compose.onNodeWithTag("summary_usage_app_$packageName").assertExists()
        }
        listOf("example.delta", "example.epsilon", "example.short").forEach { packageName ->
            compose.onNodeWithTag("summary_usage_app_$packageName").assertDoesNotExist()
        }
        compose.onNodeWithText("apps shown: 3", substring = true).assertExists()
    }

    @Test fun currentDayShortcutsOpenEachCaptureTypeWithoutADatePrompt() {
        val captured = mutableListOf<Pair<EventType, LocalDate?>>()
        compose.setContent {
            LifeOsTheme {
                TodayScreen(LocalDate.now(ZoneId.of("UTC")), ZoneId.of("UTC"), TodayUiState.Empty,
                    {}, {}, {}, onCapture = { type, date -> captured += type to date })
            }
        }
        listOf("mood", "journal", "memory").forEach { type ->
            compose.onNodeWithTag("today_capture_$type").performClick()
            compose.onNodeWithTag("today_capture_date_dialog").assertDoesNotExist()
        }
        compose.runOnIdle {
            assertEquals(listOf(EventType.MOOD to null, EventType.JOURNAL to null, EventType.MEMORY to null), captured)
        }
    }

    @Test fun pastDayShortcutsWaitForAnExplicitDateChoiceAtLargeTextInRtl() {
        val day = LocalDate.parse("2026-10-11")
        val captured = mutableListOf<Pair<EventType, LocalDate?>>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                LifeOsTheme {
                    TodayScreen(day, ZoneId.of("UTC"), TodayUiState.Empty, {}, {}, {},
                        onCapture = { type, date -> captured += type to date })
                }
            }
        }
        listOf("mood" to EventType.MOOD, "journal" to EventType.JOURNAL, "memory" to EventType.MEMORY).forEach { (tag, type) ->
            compose.onNodeWithTag("today_capture_$tag").assertIsDisplayed().performClick()
            compose.onNodeWithTag("today_capture_date_dialog").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, captured.size) }
            compose.onNodeWithTag("today_capture_selected_date").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf(type to day), captured); captured.clear() }
            compose.onNodeWithTag("today_capture_$tag").performClick()
            compose.onNodeWithTag("today_capture_now").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf(type to null), captured); captured.clear() }
        }
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_recent_entries"))
        compose.onNodeWithTag("today_recent_entries").assertIsDisplayed()
    }

    @Test fun summaryFailureRetryRemainsAvailableBehindDisclosure() {
        var retries = 0
        compose.setContent {
            LifeOsTheme {
                TodayScreen(LocalDate.now(ZoneId.of("UTC")), ZoneId.of("UTC"), TodayUiState.Empty,
                    {}, {}, {}, summaryState = DailySummaryState.Error, onSummaryRetry = { retries++ })
            }
        }
        compose.onNodeWithTag("daily_summary_error").assertDoesNotExist()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_daily_summary"))
        compose.onNodeWithTag("today_daily_summary").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("daily_summary_retry"))
        compose.onNodeWithTag("daily_summary_retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("today_daily_summary").performClick()
        compose.onNodeWithTag("daily_summary_error").assertDoesNotExist()
    }

    @Test fun loadingEmptyAndFailureHaveDistinctContentAndRetry() {
        val state = mutableStateOf<TodayUiState>(TodayUiState.Loading)
        var retries = 0
        compose.setContent {
            LifeOsTheme {
                TodayScreen(LocalDate.parse("2026-10-11"), ZoneId.of("UTC"), state.value,
                    onSelectDate = {}, onToday = {}, onRetry = { retries++ })
            }
        }
        compose.onNodeWithTag("today_loading").assertIsDisplayed()
        compose.runOnIdle { state.value = TodayUiState.Empty }
        compose.onNodeWithTag("today_empty").assertIsDisplayed()
        compose.onNodeWithTag("today_loading").assertDoesNotExist()
        compose.runOnIdle { state.value = TodayUiState.Error }
        compose.onNodeWithTag("today_retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun storedEventDisplaysTitleNoteAndTime() {
        val instant = Instant.parse("2026-10-11T08:30:00Z")
        val event = LifeEvent(EventId.new(), EventType.JOURNAL, instant, instant,
            title = "A quiet morning", note = "Walked by the river")
        compose.setContent {
            LifeOsTheme {
                TodayScreen(LocalDate.parse("2026-10-11"), ZoneId.of("UTC"),
                    TodayUiState.Data(listOf(event)), onSelectDate = {}, onToday = {}, onRetry = {})
            }
        }
        compose.onNodeWithTag("today_recent_entries").assertIsDisplayed()
        compose.onNodeWithText("A quiet morning").assertIsDisplayed()
        compose.onNodeWithText("Walked by the river").assertIsDisplayed()
        compose.onNodeWithText("08:30").assertIsDisplayed()
    }

    @Test fun pickerCanOpenAfterNavigatingOutsideItsDefaultYearRange() {
        compose.setContent {
            LifeOsTheme {
                TodayScreen(LocalDate.parse("1899-12-31"), ZoneId.of("UTC"), TodayUiState.Empty,
                    onSelectDate = {}, onToday = {}, onRetry = {})
            }
        }
        compose.onNodeWithTag("day_picker").performClick()
        compose.onNodeWithTag("date_picker_dialog").assertIsDisplayed()
    }
}
