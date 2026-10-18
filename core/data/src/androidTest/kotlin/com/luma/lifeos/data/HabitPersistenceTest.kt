package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.HabitLifecycle
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.parse("2026-10-17")
    private val zone = ZoneId.of("Asia/Tehran")
    private val now = Instant.parse("2026-10-17T08:00:00Z")
    private lateinit var database: LumaDatabase
    private lateinit var habits: RoomHabitRepository
    private var persistentFile: String? = null

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        habits = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After fun tearDown() {
        database.close()
        persistentFile?.let(context::deleteDatabase)
    }

    private fun input(start: LocalDate = today.minusDays(2), target: Int = 2,
        weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet()) =
        HabitSaveInput("Water", "glasses", start, target, weekdays, zone)

    @Test fun retryingOneLogIsIdempotentAndASecondActionAtTheSameInstantCountsSeparately() = runBlocking {
        val habitId = UUID.randomUUID()
        habits.create(habitId, input())
        val firstId = UUID.randomUUID()
        val firstInput = HabitLogInput(habitId, today, zone, null)

        val first = habits.log(firstId, firstInput)
        assertEquals(first, habits.log(firstId, firstInput))
        val second = habits.log(UUID.randomUUID(), firstInput)

        assertNotEquals(first.id, second.id)
        assertNull(first.occurredAt)
        assertEquals(2, habits.observeDay(today).first().single().count)
    }

    @Test fun concurrentIntentionalActionsUseLogRowsAsTheCount() = runBlocking {
        val habitId = UUID.randomUUID()
        habits.create(habitId, input())
        coroutineScope {
            (1..12).map { async { habits.log(UUID.randomUUID(), HabitLogInput(habitId, today, zone, now)) } }.forEach { it.await() }
        }
        assertEquals(12, habits.observeDay(today).first().single().count)
    }

    @Test fun sameDayScheduleEditsReplaceTomorrowAndKeepHistoricalTarget() = runBlocking {
        val id = UUID.randomUUID()
        habits.create(id, input(start = today.minusDays(4), target = 3))

        habits.schedule(id, HabitScheduleInput(8, setOf(DayOfWeek.MONDAY), HabitLifecycle.ACTIVE))
        habits.schedule(id, HabitScheduleInput(10, setOf(DayOfWeek.TUESDAY), HabitLifecycle.ACTIVE))

        val versions = habits.schedules(id)
        assertEquals(2, versions.size)
        assertEquals(3, versions.single { it.effectiveFrom == today.minusDays(4) }.target)
        assertEquals(3, habits.observeDay(today).first().single().schedule?.target)
        assertEquals(10, versions.single { it.effectiveFrom == today.plusDays(1) }.target)
    }

    @Test fun skipChoiceSurvivesAnotherLogAndDeletingOnlyThatLog() = runBlocking {
        val id = UUID.randomUUID()
        habits.create(id, input(start = today.minusDays(3), target = 3))
        val first = habits.log(UUID.randomUUID(), HabitLogInput(id, today, zone, null))
        habits.setSkipRemaining(id, today, true)
        habits.log(UUID.randomUUID(), HabitLogInput(id, today, zone, null))

        habits.deleteLog(first.id)

        val day = habits.observeDay(today).first().single()
        assertEquals(1, day.count)
        assertTrue(day.skipRemaining)
        assertNull(habits.getLog(first.id))
        assertEquals(1, habits.history(id, today, 0, 10).total)
    }

    @Test fun deletingOneOfThreeLogsAfterReopeningLeavesTwoAndRetainsSkipChoice() = runBlocking {
        val fileName = "habit-reopen-${UUID.randomUUID()}.db"
        persistentFile = fileName
        database.close()
        database = Room.databaseBuilder(context, LumaDatabase::class.java, fileName).build()
        habits = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC))

        val habitId = UUID.randomUUID()
        habits.create(habitId, input(start = today.minusDays(3)))
        val input = HabitLogInput(habitId, today, zone, null)
        val logs = (1..3).map { habits.log(UUID.randomUUID(), input) }
        habits.setSkipRemaining(habitId, today, true)
        database.close()

        database = Room.databaseBuilder(context, LumaDatabase::class.java, fileName).build()
        habits = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC))
        habits.deleteLog(logs.first().id)

        val day = habits.observeDay(today).first().single()
        assertEquals(2, day.count)
        assertTrue(day.skipRemaining)
        assertEquals(2, habits.history(habitId, today, 0, 10).total)
        assertNull(habits.getLog(logs.first().id))
        assertEquals(logs[1], habits.log(logs[1].id, input))
        assertEquals(2, habits.observeDay(today).first().single().count)
    }

    @Test fun zoneAndHistoricalScheduleMismatchesAreRejected() = runBlocking {
        val id = UUID.randomUUID()
        habits.create(id, input(start = today.minusDays(10), weekdays = setOf(DayOfWeek.SATURDAY)))

        assertTrue(runCatching { habits.log(UUID.randomUUID(), HabitLogInput(id, today, ZoneId.of("UTC"), null)) }.isFailure)
        assertTrue(runCatching {
            habits.log(UUID.randomUUID(), HabitLogInput(id, today.minusDays(1), zone, null))
        }.isFailure)
        assertEquals(zone, habits.trackingZone())
    }

    @Test fun saveRetryWithChangedPayloadConflictsAndHistoryPagesStayBounded() = runBlocking {
        val id = UUID.randomUUID()
        habits.create(id, input())
        assertTrue(runCatching { habits.create(id, input(target = 9)) }.isFailure)
        repeat(4) { habits.log(UUID.randomUUID(), HabitLogInput(id, today, zone, null)) }

        val page = habits.history(id, today, offset = 1, limit = 2)
        assertEquals(4, page.total)
        assertEquals(1, page.offset)
        assertEquals(2, page.logs.size)
        assertTrue(page.logs.all { it.occurredAt == null })
    }
}
