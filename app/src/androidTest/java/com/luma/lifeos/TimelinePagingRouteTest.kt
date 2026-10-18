package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.timeline.TimelineRoute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelinePagingRouteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibleOlderBoundaryAutomaticallyLoadsTheNextPage() {
        val repository = TwoRowTimelineRepository()
        compose.setContent {
            LifeOsTheme {
                TimelineRoute(repository, LocalDate.parse("2026-10-11"), ZoneId.of("UTC"),
                    onSelectDate = {}, onToday = {}, onEvent = {}, onMemory = {})
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("event_${repository.older.event.id}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("event_${repository.older.event.id}").assertExists()
    }

    @Test fun finalTimelineEntryScrollsAboveTheCaptureFab() {
        val repository = ScrollableTimelineRepository()
        compose.setContent {
            LifeOsTheme {
                Scaffold(
                    bottomBar = { Box(Modifier.fillMaxWidth().height(80.dp)) },
                    floatingActionButton = { Box(Modifier.size(56.dp).testTag("capture_fab_probe")) },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        TimelineRoute(repository, LocalDate.parse("2026-10-11"), ZoneId.of("UTC"),
                            onSelectDate = {}, onToday = {}, onEvent = {}, onMemory = {},
                            captureBottomClearance = 72.dp)
                    }
                }
            }
        }

        compose.onNodeWithTag("screen_timeline")
            .performScrollToNode(hasTestTag("event_${repository.last.event.id}"))
        compose.onNodeWithTag("event_${repository.last.event.id}").assertIsDisplayed()
        val finalEntry = compose.onNodeWithTag("event_${repository.last.event.id}").fetchSemanticsNode().boundsInRoot
        val fab = compose.onNodeWithTag("capture_fab_probe").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { assertTrue("last event overlaps capture FAB: $finalEntry / $fab", finalEntry.bottom <= fab.top) }
    }

    private class TwoRowTimelineRepository : TimelineRepository {
        private val changed = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        private val rangeStart = Instant.parse("2026-10-11T00:00:00Z")
        private val rangeEnd = Instant.parse("2026-10-12T00:00:00Z")
        private val newestTime = Instant.parse("2026-10-11T12:00:00Z")
        private val olderTime = Instant.parse("2026-10-11T11:00:00Z")
        val newest = journalRecord(1, newestTime, "Newest")
        val older = journalRecord(2, olderTime, "Older")

        override fun observeChanges(): Flow<Unit> = changed.asSharedFlow()

        override suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean): TimelinePage {
            require(range.start == rangeStart && range.endExclusive == rangeEnd)
            return when (offset) {
                0 -> TimelinePage(listOf(newest), 0, 2)
                1 -> TimelinePage(listOf(older), 1, 2)
                else -> TimelinePage(emptyList(), offset, 2)
            }
        }

        override suspend fun get(id: EventId): LifeEventRecord? =
            listOf(newest, older).firstOrNull { it.event.id == id }

        private fun journalRecord(id: Long, time: Instant, title: String): LifeEventRecord {
            val eventId = EventId(UUID(0, id))
            return LifeEventRecord(
                LifeEvent(eventId, EventType.JOURNAL, time, time, title = title),
                journal = JournalDetails(title, "Body $title"),
            )
        }
    }

    private class ScrollableTimelineRepository : TimelineRepository {
        val records = (1L..24L).map { index ->
            val time = Instant.parse("2026-10-11T12:00:00Z").plusSeconds(index * 60)
            val id = EventId(UUID(0, index))
            LifeEventRecord(
                LifeEvent(id, EventType.JOURNAL, time, time, title = "Entry $index"),
                journal = JournalDetails("Entry $index", "Body $index"),
            )
        }
        val last = records.last()
        private val changed = flowOf(Unit)

        override fun observeChanges(): Flow<Unit> = changed
        override suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean): TimelinePage =
            TimelinePage(records.drop(offset).take(limit), offset, records.size)
        override suspend fun get(id: EventId): LifeEventRecord? = records.firstOrNull { it.event.id == id }
    }
}
