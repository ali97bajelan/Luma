package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
}
