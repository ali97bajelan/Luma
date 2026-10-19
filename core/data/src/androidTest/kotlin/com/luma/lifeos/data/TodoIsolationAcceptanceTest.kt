package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomDailySummaryRepository
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomJournalRepository
import com.luma.lifeos.data.repository.RoomMoodRepository
import com.luma.lifeos.data.repository.RoomSearchRepository
import com.luma.lifeos.data.repository.RoomTimelineRepository
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.data.repository.RoomWeeklyReviewRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewMetricKind
import com.luma.lifeos.domain.review.reviewPeriod
import com.luma.lifeos.domain.search.SearchQuery
import com.luma.lifeos.domain.todo.TodoInput
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoIsolationAcceptanceTest {
    @Test fun taskCrudDoesNotChangeEventsHabitsTimelineSummarySearchOrReview() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        try {
            val date = LocalDate.parse("2026-10-19")
            val zone = ZoneOffset.UTC
            val now = Instant.parse("2026-10-19T08:00:00Z")
            val clock = Clock.fixed(now, zone)
            val eventId = EventId.new()
            RoomMoodRepository(database, clock).create(eventId, MoodInput(4, now))
            val habits = RoomHabitRepository(database, clock)
            val habitId = UUID.randomUUID()
            habits.create(
                habitId,
                HabitSaveInput("Water", "glasses", date.minusDays(1), 2, DayOfWeek.entries.toSet(), zone),
            )
            habits.log(UUID.randomUUID(), HabitLogInput(habitId, date, zone, now))

            val timeline = RoomTimelineRepository(database)
            val summary = RoomDailySummaryRepository(database, clock)
            val search = RoomSearchRepository(database)
            val review = RoomWeeklyReviewRepository(database)
            val todayRange = com.luma.lifeos.domain.time.TimeRange.forDay(date, zone)
            val reviewDates = reviewPeriod(date, date, zone).current
            val timelineBefore = timeline.page(todayRange, 0, 50)
            val summaryBefore = summary.get(date, zone)
            val reviewBefore = review.observe(reviewPeriod(date, date, zone)).first().current
            val reviewMoodIdsBefore = review.sources(
                reviewDates,
                zone,
                ReviewMetric(ReviewMetricKind.MOOD),
                0,
            ).records.map { it.event.id }
            val unrelatedCountsBefore = unrelatedTableCounts(database)
            val habitDayBefore = habits.observeDay(date).first()

            suspend fun assertUnchanged() {
                val timelineAfter = timeline.page(todayRange, 0, 50)
                val summaryAfter = summary.get(date, zone)
                val reviewAfter = review.observe(reviewPeriod(date, date, zone)).first().current
                val reviewMoodIdsAfter = review.sources(
                    reviewDates,
                    zone,
                    ReviewMetric(ReviewMetricKind.MOOD),
                    0,
                ).records.map { it.event.id }
                assertEquals(timelineBefore.totalCount, timelineAfter.totalCount)
                assertEquals(timelineBefore.eventCount, timelineAfter.eventCount)
                assertEquals(timelineBefore.records.map { it.event.id }, timelineAfter.records.map { it.event.id })
                assertEquals(summaryBefore.records, summaryAfter.records)
                assertEquals(summaryBefore.journals, summaryAfter.journals)
                assertEquals(summaryBefore.memories, summaryAfter.memories)
                assertEquals(summaryBefore.mood, summaryAfter.mood)
                assertEquals(reviewBefore.records, reviewAfter.records)
                assertEquals(reviewBefore.mood, reviewAfter.mood)
                assertEquals(reviewMoodIdsBefore, reviewMoodIdsAfter)
                assertEquals(unrelatedCountsBefore, unrelatedTableCounts(database))
                assertEquals(habitDayBefore, habits.observeDay(date).first())
                assertEquals(
                    0,
                    search.page(SearchQuery("TD07 task-only search phrase"), zone, 0, 50).totalCount,
                )
            }

            val todos = RoomTodoRepository(database, clock)
            val id = UUID.randomUUID()
            val firstRevision = UUID.randomUUID()
            val created = todos.create(id, firstRevision, TodoInput("TD07 task-only search phrase"))
            assertUnchanged()

            val updated = todos.update(
                id,
                created.revision,
                UUID.randomUUID(),
                TodoInput("TD07 task-only search phrase updated"),
            )
            assertUnchanged()

            val completed = todos.setCompleted(id, updated.revision, UUID.randomUUID(), true)
            assertUnchanged()

            val reopened = todos.setCompleted(id, completed.revision, UUID.randomUUID(), false)
            assertUnchanged()

            todos.delete(id, reopened.revision)
            assertUnchanged()
        } finally {
            database.close()
        }
    }

    private fun unrelatedTableCounts(database: LumaDatabase): Map<String, Int> {
        val tables = listOf(
            "life_event",
            "mood_entry",
            "journal_entry",
            "memory",
            "activity_record",
            "app_usage_record",
            "daily_summary",
            "habit_settings",
            "habit",
            "habit_schedule",
            "habit_log",
            "habit_day_choice",
            "habit_reminder",
            "habit_reminder_runtime",
            "usage_session",
            "usage_session_window",
        )
        return tables.associateWith { table ->
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }
    }
}
