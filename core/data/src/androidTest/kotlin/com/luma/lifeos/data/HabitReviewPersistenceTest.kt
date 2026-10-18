package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.domain.habit.HabitDayState
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.compareHabitWeeks
import com.luma.lifeos.domain.habit.reviewTarget
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

class HabitReviewPersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var habits: RoomHabitRepository
    private val today = LocalDate.parse("2026-10-17")
    private val zone = ZoneId.of("Asia/Tehran")
    private val now = Instant.parse("2026-10-17T08:00:00Z")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        habits = RoomHabitRepository(db, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After fun tearDown() = db.close()

    @Test fun weekRetainsHistoricalTargetsCountsSkipAndDoesNotExpectPausedOrFutureDays() = runBlocking {
        val monday = today.with(DayOfWeek.MONDAY)
        val activeId = UUID.randomUUID()
        habits.create(activeId, HabitSaveInput("Water", "glasses", monday.minusWeeks(2), 8,
            DayOfWeek.entries.toSet(), zone))
        val activeInput = HabitLogInput(activeId, today, zone, null)
        repeat(3) { habits.log(UUID.randomUUID(), activeInput) }
        habits.setSkipRemaining(activeId, today, true)
        habits.schedule(activeId, HabitScheduleInput(10, DayOfWeek.entries.toSet(), HabitLifecycle.ACTIVE))

        val pausedId = UUID.randomUUID()
        habits.create(pausedId, HabitSaveInput("Stretch", null, monday.minusDays(7), 4,
            DayOfWeek.entries.toSet(), zone))
        db.openHelper.writableDatabase.execSQL(
            "UPDATE habit_schedule SET state = 'PAUSED' WHERE habit_id = ?",
            arrayOf(pausedId.toString()),
        )
        val futureId = UUID.randomUUID()
        habits.create(futureId, HabitSaveInput("Plan", null, today.plusDays(1), 2,
            DayOfWeek.entries.toSet(), zone))

        val current = habits.week(monday, today)
        val previous = habits.week(monday.minusWeeks(1), today.minusWeeks(1))
        val comparison = compareHabitWeeks(current, previous)
        val water = comparison.rows.single { it.habit.id == activeId }
        val currentWater = requireNotNull(water.current)
        val yesterday = currentWater.days.single { it.date == today.minusDays(1) }
        val skipped = currentWater.days.single { it.date == today }
        val paused = comparison.rows.single { it.habit.id == pausedId }.current!!.days.last()
        val future = comparison.rows.single { it.habit.id == futureId }.current!!.days.last()

        assertEquals(today, current.through)
        assertEquals(today.minusWeeks(1), previous.through)
        assertTrue(current.partial)
        assertFalse(previous.partial)
        assertEquals(8, yesterday.schedule?.target)
        assertEquals(8, skipped.schedule?.target)
        assertEquals(3, skipped.count)
        assertTrue(skipped.skipRemaining)
        assertEquals(HabitDayState.PAUSED, paused.state)
        assertNull(paused.reviewTarget())
        assertNull(future.schedule)
        assertNull(future.reviewTarget())
        assertEquals(3, water.currentRepetitions)
        assertEquals(6, water.currentScheduledDays)
        assertEquals(6, water.previousScheduledDays)
        assertEquals(today.plusDays(1), habits.schedules(activeId).last().effectiveFrom)
        assertEquals(10, habits.schedules(activeId).last().target)
    }
}
