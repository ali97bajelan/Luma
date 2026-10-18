package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TimelineFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var photos: RoomPhotoRepository
    private lateinit var directory: File
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val time = today.atTime(12, 0).atZone(zone).toInstant()
    private val clock = Clock.fixed(time, zone)
    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("timeline_usage", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        directory = File(context.cacheDir, "timeline-test-${UUID.randomUUID()}")
        photos = RoomPhotoRepository(db, ManagedPhotoFiles(directory)) { error("No photo selection in this fixture") }
    }
    @After fun tearDown() { db.close(); directory.deleteRecursively() }

    @Test fun mixedTimelineOpensSignalsReadOnlyAndManualJournalDetailsAcrossDays() {
        val activity = LifeEvent(EventId.new(), EventType.ACTIVITY, time.minusSeconds(10_800), time,
            source = EventSource.HEALTH_CONNECT, sourceReference = "test:steps")
        val measurement = ActivityRecordEntity(UUID.randomUUID().toString(), activity.id.toString(), "steps", 123.0, "count", activity.source, "test:steps")
        val usage = LifeEvent(EventId.new(), EventType.APP_USAGE, time.minusSeconds(7_200), time,
            endedAt = time.minusSeconds(7_000), source = EventSource.USAGE_STATS, sourceReference = "test:usage")
        val unused = LifeEvent(EventId.new(), EventType.APP_USAGE, time.minusSeconds(14_400), time,
            endedAt = time, source = EventSource.USAGE_STATS, sourceReference = "test:unused")
        val journal = EventId.new()
        runBlocking {
            RoomEventStore(db).save(EventWrite(activity, activities = listOf(measurement)))
            RoomEventStore(db).save(EventWrite(usage, appUsage = AppUsageRecordEntity(UUID.randomUUID().toString(), usage.id.toString(),
                usage.occurredAt.toEpochMilli(), usage.endedAt!!.toEpochMilli(), "org.example.browser", 125_125)))
            RoomEventStore(db).save(EventWrite(unused, appUsage = AppUsageRecordEntity(UUID.randomUUID().toString(), unused.id.toString(),
                unused.occurredAt.toEpochMilli(), time.toEpochMilli(), "org.example.unused", 0)))
            RoomJournalRepository(db, clock).create(journal, JournalInput("Earlier journal body", time.minusSeconds(86_400), "Earlier journal"))
        }
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("timeline_usage", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("show_app_usage", true).commit()
        show()
        compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("event_${activity.id}")
        compose.onNodeWithTag("event_${unused.id}").assertDoesNotExist()
        compose.onNodeWithTag("timeline_counts").assertTextEquals("1 entry · 0 habit logs")
        compose.onNodeWithTag("timeline_order").assertTextEquals("Newest first")
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("event_${activity.id}"))
        compose.onNodeWithTag("event_${activity.id}").performClick()
        awaitTag("screen_event_detail")
        awaitText("Steps: 123 count")
        compose.onNodeWithTag("event_edit").assertDoesNotExist()
        compose.onNodeWithTag("event_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("event_promote").assertDoesNotExist()
        runBlocking { RoomEventStore(db).save(EventWrite(activity.copy(updatedAt = time.plusSeconds(1)), activities = listOf(measurement.copy(value = 124.0)))) }
        awaitText("Steps: 124 count")
        runBlocking { RoomLifeEventRepository(db).delete(activity.id) }
        awaitTag("event_missing")
        compose.onNodeWithTag("event_detail_back").performClick()
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("timeline_counts"))
        compose.onNodeWithTag("timeline_counts").assertTextEquals("0 entries · 0 habit logs")
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("event_${usage.id}"))
        awaitText("2 min")
        compose.onNodeWithTag("event_${usage.id}").performClick()
        awaitTag("screen_event_detail"); awaitText("org.example.browser"); awaitText("2 min")
        awaitText("Exact duration: 0 h 2 min 5.125 s")
        compose.onNodeWithTag("event_edit").assertDoesNotExist()
        compose.onNodeWithTag("event_detail_back").performClick()
        runBlocking {
            db.withTransaction {
                db.openHelper.writableDatabase.execSQL("UPDATE app_usage_record SET duration_ms = 0 WHERE life_event_id = ?", arrayOf(usage.id.toString()))
            }
        }
        awaitTag("timeline_empty")
        compose.onNodeWithTag("event_${usage.id}").assertDoesNotExist()
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("day_previous"))
        compose.onNodeWithTag("day_previous").performClick()
        awaitTag("event_$journal")
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("event_$journal"))
        compose.onNodeWithTag("event_$journal").performClick()
        awaitTag("screen_journal_detail"); awaitText("Earlier journal body")
        compose.onNodeWithTag("journal_edit").assertExists()
        compose.onNodeWithTag("journal_detail_back").performClick()
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("day_picker"))
        compose.onNodeWithTag("day_picker").performClick()
        compose.onNodeWithTag("date_picker_dialog").assertExists()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test fun manualMoodAndMemoryOpenInReadingDetailBeforeEdit() {
        val moodId = EventId.new()
        val memoryId = EventId.new()
        runBlocking {
            RoomMoodRepository(db, clock).create(moodId, MoodInput(4, time, note = "Mood note"))
            RoomMemoryRepository(db, clock).saveMarker(moodId, MemoryMarkerInput(4, "Marker reason"))
            RoomMemoryRepository(db, clock).create(memoryId, MemoryInput("Direct memory note", time, "Direct memory"))
        }
        show()
        compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("event_$moodId")
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("event_$moodId"))
        compose.onNodeWithTag("event_$moodId").performClick()
        awaitTag("screen_event_detail")
        awaitText("Mood note")
        compose.onNodeWithTag("event_edit").assertExists()
        compose.onNodeWithTag("event_promote").assertExists()
        compose.onNodeWithTag("memory_status").assertExists()
        compose.onNodeWithText("Marker reason").assertExists()
        compose.onNodeWithTag("event_edit").performClick()
        awaitTag("screen_mood")
        compose.onNodeWithTag("mood_save").assertExists()
        compose.onNodeWithTag("mood_cancel").performClick()
        awaitTag("screen_event_detail")
        compose.onNodeWithTag("event_detail_back").performClick()
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("event_$memoryId"))
        compose.onNodeWithTag("event_$memoryId").performClick()
        awaitTag("screen_event_detail")
        awaitText("Direct memory note")
        compose.onNodeWithTag("event_edit").assertExists()
        compose.onNodeWithTag("event_edit").performClick()
        awaitTag("screen_memory")
        compose.onNodeWithTag("memory_save").assertExists()
    }

    @Test fun eventBackAndEditStayVisibleAfterLongReadingScroll() {
        val moodId = EventId.new()
        val note = "A long check-in note. ".repeat(420)
        runBlocking { RoomMoodRepository(db, clock).create(moodId, MoodInput(4, time, note = note)) }
        show()
        compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("event_$moodId")
        compose.onNodeWithTag("event_$moodId").performClick()
        awaitTag("event_detail_content")
        compose.onNodeWithTag("event_detail_content").performTouchInput {
            repeat(12) { swipeUp() }
        }
        compose.onNodeWithTag("event_detail_body").assertIsDisplayed()
        compose.onNodeWithTag("event_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("event_edit").assertIsDisplayed()
        compose.onNodeWithTag("event_promote").assertIsDisplayed()
    }

    @Test fun appUsageRowsAreHiddenByDefault() {
        val usage = saveUsage("org.example.browser", 60_001)
        show()
        compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("timeline_counts")

        compose.onNodeWithTag("event_${usage.id}").assertDoesNotExist()
    }

    @Test fun timelineSwitchShowsOnlyAppsOverOneMinuteAndPersistsTheChoice() {
        val oneMinute = saveUsage("org.example.one-minute", 60_000)
        val overOneMinute = saveUsage("org.example.over-one-minute", 60_001)
        show()
        compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("timeline_counts")

        compose.onNodeWithTag("timeline_show_app_usage").assertIsOff()
        compose.onNodeWithTag("event_${oneMinute.id}").assertDoesNotExist()
        compose.onNodeWithTag("event_${overOneMinute.id}").assertDoesNotExist()
        compose.onNodeWithTag("timeline_show_app_usage").performClick()
        compose.onNodeWithTag("timeline_show_app_usage").assertIsOn()
        compose.onNodeWithTag("event_${oneMinute.id}").assertDoesNotExist()
        awaitTag("event_${overOneMinute.id}")
        compose.onNodeWithTag("event_${overOneMinute.id}").assertIsDisplayed()

        val preferences = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("timeline_usage", android.content.Context.MODE_PRIVATE)
        assertTrue(preferences.getBoolean("show_app_usage", false))
    }

    private fun saveUsage(packageName: String, durationMillis: Long): LifeEvent {
        val start = time.minusSeconds(600)
        val end = start.plusMillis(durationMillis)
        val event = LifeEvent(EventId.new(), EventType.APP_USAGE, start, time, endedAt = end,
            source = EventSource.USAGE_STATS, sourceReference = "test:$packageName")
        runBlocking {
            RoomEventStore(db).save(EventWrite(event, appUsage = AppUsageRecordEntity(UUID.randomUUID().toString(), event.id.toString(),
                start.toEpochMilli(), end.toEpochMilli(), packageName, durationMillis)))
        }
        return event
    }

    @Test fun longDayLoadsOlderRowsAndNewerWindowWithoutRenderingTheWholeFixture() {
        runBlocking {
            db.withTransaction {
                (499 downTo 0).forEach { n ->
                    val id = UUID(0, n.toLong()).toString()
                    db.lifeEvents().insert(LifeEventEntity(id, EventType.JOURNAL, time.toEpochMilli(), time.toEpochMilli(), time.toEpochMilli(), title = "Entry $n"))
                    db.content().insertJournal(JournalEntryEntity(UUID(1, n.toLong()).toString(), id, "Entry $n", "Body $n"))
                }
            }
        }
        show(); compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("event_${UUID(0, 499)}")
        // Crossing each page boundary exercises automatic loading and removal of offscreen rows.
        listOf(359, 309, 259, 209, 159, 109, 59, 9, 0).forEach { n ->
            scrollToEvent(UUID(0, n.toLong()).toString())
        }
        scrollToEvent(UUID(0, 0).toString())
        compose.onNodeWithText("Entry 0").assertIsDisplayed()
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("timeline_later"))
        compose.onNodeWithTag("timeline_later").performClick()
        scrollToEvent(UUID(0, 150).toString())
        compose.onNodeWithText("Entry 150").assertIsDisplayed()
    }

    @Test fun largeStoredHistoryKeepsDayNavigationResponsive() {
        runBlocking {
            db.withTransaction {
                (0 until 20_000).forEach { n ->
                    val day = if (n < 500) today else today.minusDays((1 + (n - 500) % 1095).toLong())
                    val occurred = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                    val id = UUID(3, n.toLong()).toString()
                    db.lifeEvents().insert(LifeEventEntity(id, EventType.JOURNAL, occurred, occurred, occurred, title = "Fixture $n"))
                    db.content().insertJournal(JournalEntryEntity(UUID(4, n.toLong()).toString(), id, "Fixture $n", "Stored body $n"))
                }
            }
        }
        show()
        compose.onNodeWithTag("nav_timeline").performClick()
        // Equal timestamps use the newest-first ID tie-breaker as well.
        awaitTag("event_${UUID(3, 499)}")
        val navigation = mutableListOf<Double>()
        repeat(3) { day ->
            val started = SystemClock.elapsedRealtimeNanos()
            compose.onNodeWithTag("day_previous").performClick()
            awaitTag("event_${UUID(3, 19115L + day)}")
            navigation += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        }
        val started = SystemClock.elapsedRealtimeNanos()
        compose.onNodeWithTag("day_today").performClick()
        awaitTag("event_${UUID(3, 499)}")
        navigation += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0

        Log.i("LumaValidation", "Timeline rows=20000 day_navigation_ms=$navigation")
    }
    private fun scrollToEvent(id: String) {
        // Room work is asynchronous to Compose's idle clock. A following page may not be loaded yet.
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("event_$id"))
                true
            } catch (_: AssertionError) { false }
        }
    }

    private fun show() {
        compose.setContent { LifeOsTheme { LifeOsApp(RoomLifeEventRepository(db), RoomMoodRepository(db, clock),
            RoomJournalRepository(db, clock), RoomMemoryRepository(db, clock), RoomPersonRepository(db),
            RoomPlaceRepository(db), photos, RoomTimelineRepository(db), habits = RoomHabitRepository(db)) } }
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
