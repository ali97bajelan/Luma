package com.luma.lifeos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
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
        compose.onNodeWithTag("summary_activity_steps_count_HEALTH_CONNECT").assertDoesNotExist()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_details"))
        compose.onNodeWithTag("summary_details").performClick()
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
            compose.onNodeWithTag("summary_usage_app_$packageName").assertIsDisplayed()
        }
        listOf("example.delta", "example.epsilon", "example.short").forEach { packageName ->
            compose.onNodeWithTag("summary_usage_app_$packageName").assertDoesNotExist()
        }
        compose.onNodeWithText("apps shown: 3", substring = true).assertExists()
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
        compose.onNodeWithTag("today_summary").assertIsDisplayed()
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
