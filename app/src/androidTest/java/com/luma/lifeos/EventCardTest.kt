package com.luma.lifeos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.timeline.EventCard
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EventCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun severalPopulatedEntriesFitWithoutScrolling() = checkViewport(1f)

    @Test fun severalPopulatedEntriesFitWithLargerText() = checkViewport(1.3f)

    @Test fun largerTextEntriesRemainOperableInRtl() = checkViewport(1.3f, LayoutDirection.Rtl)

    @Test fun markedMoodWithoutMemoryShortcutKeepsItsMarkerPreview() {
        val time = Instant.parse("2026-10-13T12:00:00Z")
        val record = LifeEventRecord(LifeEvent(EventId.new(), EventType.MOOD, time, time),
            mood = MoodDetails(4, note = "Original mood note"),
            memory = MemoryDetails(5, "Why this moment matters"))
        compose.setContent { LifeOsTheme {
            EventCard(record, ZoneOffset.UTC, {}, R.string.event_open)
        } }
        compose.onNodeWithText("Original mood note").assertIsDisplayed()
        compose.onNodeWithText("Why this moment matters").assertIsDisplayed()
        compose.onNodeWithText("Importance: 5/5", substring = true).assertIsDisplayed()
    }

    private fun checkViewport(fontScale: Float, direction: LayoutDirection = LayoutDirection.Ltr) {
        val time = Instant.parse("2026-10-13T12:00:00Z")
        val records = (1..3).map { n ->
            LifeEventRecord(
                LifeEvent(EventId.new(), EventType.MOOD, time, time),
                mood = MoodDetails(4, listOf(Emotion(UUID.randomUUID(), "Calm")), 3, 2, "A quiet walk $n"),
                memory = MemoryDetails(4, "A longer reflection available on open"),
                people = listOf(Person(UUID.randomUUID(), "Sam")),
                place = Place(UUID.randomUUID(), "Park"),
            )
        }
        var opened: EventId? = null
        var remembered: EventId? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction) {
                LifeOsTheme {
                    LazyColumn(Modifier.size(360.dp, 600.dp).testTag("viewport"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(records, key = { it.event.id.toString() }) { record ->
                            EventCard(record, ZoneOffset.UTC, { opened = record.event.id },
                                R.string.event_open, onMemory = { remembered = record.event.id })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("A quiet walk 3").assertIsDisplayed()
        val last = compose.onNodeWithTag("event_${records.last().event.id}").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("viewport").getUnclippedBoundsInRoot()
        assertTrue("Three short entries should fit entirely in the feed viewport; last bottom=${last.bottom}, viewport bottom=${viewport.bottom}",
            last.bottom <= viewport.bottom)
        compose.onNodeWithTag("event_${records.first().event.id}").performClick()
        compose.runOnIdle { assertEquals(records.first().event.id, opened) }
        compose.onNodeWithTag("event_memory_${records.first().event.id}")
            .assertContentDescriptionEquals("Edit memory")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "Remembered moment · Importance: 4/5"))
            .assertTouchHeightIsEqualTo(48.dp)
            .assertTouchWidthIsEqualTo(48.dp)
            .performClick()
        compose.runOnIdle { assertEquals(records.first().event.id, remembered) }
    }
}
