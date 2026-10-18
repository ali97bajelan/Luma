package com.luma.lifeos.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.AppUsageRecordEntity
import com.luma.lifeos.data.entity.LifeEventEntity
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomTimelineRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.EventSource
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HabitTimelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LumaDatabase
    private lateinit var habits: RoomHabitRepository
    private lateinit var timeline: RoomTimelineRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        timeline = RoomTimelineRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun mixedPagesUseOneStableWindowAndPrefixCollidingSourceIds() = runBlocking {
        val date = LocalDate.parse("2026-10-11")
        val zone = ZoneOffset.UTC
        val base = Instant.parse("2026-10-11T08:00:00Z")
        habits = RoomHabitRepository(db, Clock.fixed(base.plusSeconds(86_400), zone))
        val habitId = UUID.randomUUID()
        habits.create(habitId, HabitSaveInput("Reading", "pages", date.minusDays(5), 8,
            DayOfWeek.entries.toSet(), zone))
        db.withTransaction {
            repeat(350) { index ->
                val eventId = UUID(0, index.toLong()).toString()
                val occurred = base.plusSeconds((index / 2).toLong()).toEpochMilli()
                db.lifeEvents().insert(LifeEventEntity(eventId,
                    if (index % 2 == 0) EventType.ACTIVITY else EventType.JOURNAL,
                    occurred, occurred, updatedAt = occurred))
            }
        }
        val logs = (0 until 200).map { index ->
            val id = UUID(0, index.toLong())
            val occurred = if (index < 40) null else base.plusSeconds((index / 2).toLong())
            habits.log(id, HabitLogInput(habitId, date, zone, occurred))
        }

        val pages = mutableListOf<com.luma.lifeos.domain.habit.TimelineFeedPage>()
        var offset = 0
        while (offset < 550) {
            pages += timeline.feed(date, zone, offset, 50, showAppUsage = false)
            offset += 50
        }
        val items = pages.flatMap { it.items }

        assertEquals(550, pages.first().totalCount)
        assertEquals(350, pages.first().eventCount)
        assertEquals(200, pages.first().habitLogCount)
        assertEquals(550, items.size)
        assertEquals(550, items.map { it.key }.distinct().size)
        assertEquals(logs.map { it.id }.toSet(), items.filterIsInstance<TimelineFeedItem.Habit>().map { it.log.id }.toSet())
        assertEquals(40, items.take(40).filterIsInstance<TimelineFeedItem.Habit>().count { it.log.occurredAt == null })
        assertTrue(items.take(40).all { it is TimelineFeedItem.Habit && it.log.occurredAt == null })
        assertTrue(items.drop(40).none { it is TimelineFeedItem.Habit && it.log.occurredAt == null })
        assertEquals((39L downTo 0L).map { "habit:${UUID(0, it)}" }, items.take(40).map { it.key })
        assertEquals("event:${UUID(0, 0)}", items.firstOrNull { it is TimelineFeedItem.Entry && it.record.event.id.value == UUID(0, 0) }?.key)
        assertEquals("habit:${UUID(0, 0)}", items.firstOrNull { it is TimelineFeedItem.Habit && it.log.id == UUID(0, 0) }?.key)

        val expectedTimed = items.drop(40).sortedWith(compareByDescending<TimelineFeedItem> {
            val time: Instant = when (it) {
                is TimelineFeedItem.Entry -> it.record.event.occurredAt
                is TimelineFeedItem.Habit -> requireNotNull(it.log.occurredAt)
            }
            time
        }.thenBy { if (it is TimelineFeedItem.Entry) 0 else 1 }.thenByDescending { it.key.substringAfter(':') })
        assertEquals(expectedTimed.map { it.key }, items.drop(40).map { it.key })
        assertTrue(pages.all { it.items.size <= 50 })
    }

    @Test fun usageVisibilityIsAppliedBeforePagingAndCountsKeepSourcesSeparate() = runBlocking {
        val date = LocalDate.parse("2026-10-11")
        val zone = ZoneOffset.UTC
        val base = Instant.parse("2026-10-11T08:00:00Z")
        habits = RoomHabitRepository(db, Clock.fixed(base.plusSeconds(86_400), zone))
        val habitId = UUID.randomUUID()
        habits.create(habitId, HabitSaveInput("Read", null, date, 1, DayOfWeek.entries.toSet(), zone))
        habits.log(UUID.randomUUID(), HabitLogInput(habitId, date, zone, null))
        val shortUsage = UUID.randomUUID()
        val longUsage = UUID.randomUUID()
        db.withTransaction {
            db.lifeEvents().insert(LifeEventEntity(UUID(0, 1).toString(), EventType.MOOD,
                base.toEpochMilli(), base.toEpochMilli(), updatedAt = base.toEpochMilli()))
            listOf(shortUsage to 60_000L, longUsage to 60_001L).forEachIndexed { index, (id, duration) ->
                val start = base.plusSeconds((index + 1).toLong())
                val eventId = UUID(0, (index + 10).toLong()).toString()
                val end = start.plusSeconds(61)
                db.lifeEvents().insert(LifeEventEntity(eventId, EventType.APP_USAGE, start.toEpochMilli(),
                    start.toEpochMilli(), start.toEpochMilli(), endedAt = end.toEpochMilli(),
                    source = EventSource.USAGE_STATS, sourceReference = "habit-timeline-usage:$index"))
                db.content().insertAppUsage(AppUsageRecordEntity(id.toString(), eventId, start.toEpochMilli(),
                    end.toEpochMilli(), "org.example.$index", duration))
            }
        }

        val hidden = timeline.feed(date, zone, 0, 1, showAppUsage = false)
        assertEquals(2, hidden.totalCount)
        assertEquals(1, hidden.eventCount)
        assertEquals(1, hidden.habitLogCount)
        assertTrue(hidden.items.none { it is TimelineFeedItem.Entry && it.record.event.type == EventType.APP_USAGE })
        val shown = timeline.feed(date, zone, 0, 10, showAppUsage = true)
        assertEquals(3, shown.totalCount)
        assertEquals(1, shown.eventCount)
        assertEquals(1, shown.habitLogCount)
        assertEquals(1, shown.items.filterIsInstance<TimelineFeedItem.Entry>().count { it.record.event.type == EventType.APP_USAGE })
    }

    @Test fun dateOnlyLogAppearsWhenTrackingZoneSkippedThatCivilDay() = runBlocking {
        val skipped = LocalDate.parse("2011-12-30")
        val zone = ZoneId.of("Pacific/Apia")
        val now = Instant.parse("2011-12-30T12:00:00Z")
        habits = RoomHabitRepository(db, Clock.fixed(now, zone))
        val id = UUID.randomUUID()
        habits.create(id, HabitSaveInput("Stretch", null, skipped.minusDays(1), 1,
            DayOfWeek.entries.toSet(), zone))
        val log = habits.log(UUID.randomUUID(), HabitLogInput(id, skipped, zone, null))

        assertEquals(skipped.atStartOfDay(zone).toInstant(), skipped.plusDays(1).atStartOfDay(zone).toInstant())
        val page = timeline.feed(skipped, zone, 0, 50)
        assertEquals(1, page.totalCount)
        assertEquals(0, page.eventCount)
        assertEquals(1, page.habitLogCount)
        assertEquals(log.id, (page.items.single() as TimelineFeedItem.Habit).log.id)
    }
}
