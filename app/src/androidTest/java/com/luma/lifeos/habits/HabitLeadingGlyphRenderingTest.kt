package com.luma.lifeos.habits

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.ui.habits.HabitDayRow
import com.luma.lifeos.ui.habits.HabitRow
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HabitLeadingGlyphRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun habitNameAndStatusRenderLikeUnclippedText() {
        val id = UUID.randomUUID()
        val today = LocalDate.parse("2026-10-25")
        val now = Instant.parse("2026-10-25T12:00:00Z")
        val zone = ZoneId.of("UTC")
        val habit = HabitDefinition(id, "check", null, today, now, now)
        val schedule = HabitSchedule(UUID.randomUUID(), id, today, 7, DayOfWeek.entries.toSet(), HabitLifecycle.ACTIVE)
        val day = HabitDay(habit, today, zone, schedule, count = 0, skipRemaining = false)

        compose.setContent {
            LifeOsTheme {
                Column(Modifier.fillMaxWidth()) {
                    HabitRow(
                        row = HabitDayRow(day, canLog = true),
                        pending = emptyList(),
                        pendingSchedule = null,
                        onOpen = {},
                        onLog = {},
                        onRetry = {},
                        compact = true,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Column {
                            Text(
                                "check",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("habit_reference_name"),
                            )
                            Text(
                                "No check-in recorded",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("habit_reference_status"),
                            )
                        }
                    }
                }
            }
        }

        assertChildMatchesReference("check", "habit_reference_name", "habit name", id)
        assertChildMatchesReference(
            "No check-in recorded", "habit_reference_status", "habit status", id,
        )
    }

    private fun assertChildMatchesReference(
        text: String,
        referenceTag: String,
        label: String,
        id: UUID,
    ) {
        val button = compose.onNodeWithTag("habit_open_$id")
        val buttonBounds = button.fetchSemanticsNode().boundsInRoot
        val textNode = compose.onNode(
            hasText(text) and hasAnyAncestor(hasTestTag("habit_open_$id")),
            useUnmergedTree = true,
        )
        val textBounds = textNode.fetchSemanticsNode().boundsInRoot
        val actual = button.captureToImage().toPixelMap()
        val expected = compose.onNodeWithTag(referenceTag).captureToImage().toPixelMap()
        val left = (textBounds.left - buttonBounds.left).roundToInt()
        val top = (textBounds.top - buttonBounds.top).roundToInt()
        assertEquals("$label width", expected.width, textBounds.width.roundToInt())
        assertEquals("$label height", expected.height, textBounds.height.roundToInt())
        val expectedBackground = expected[0, 0]
        val actualBackground = actual[left, top]
        var coverageMismatches = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val expectedInk = colorDistance(expected[x, y], expectedBackground) > 0.12f
                val actualInk = colorDistance(actual[left + x, top + y], actualBackground) > 0.12f
                if (expectedInk != actualInk) coverageMismatches++
            }
        }
        assertTrue(
            "$label leading glyph clipping: $coverageMismatches pixels differ; button=$buttonBounds " +
                "text=$textBounds crop=($left,$top)",
            coverageMismatches <= 6,
        )
    }

    private fun colorDistance(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)

}
