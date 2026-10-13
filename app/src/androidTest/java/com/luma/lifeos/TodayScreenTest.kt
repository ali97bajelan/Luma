package com.luma.lifeos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.summary.DailySummary
import com.luma.lifeos.domain.usage.AppUsageTotal
import com.luma.lifeos.domain.usage.DailyUsageReport
import com.luma.lifeos.ui.summary.DailySummaryState
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.today.TodayScreen
import com.luma.lifeos.ui.today.TodayUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {
    @get:Rule val compose = createComposeRule()

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
