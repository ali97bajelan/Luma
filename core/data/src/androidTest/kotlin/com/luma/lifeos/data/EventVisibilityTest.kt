package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.JournalEntryEntity
import com.luma.lifeos.data.entity.MemoryEntity
import com.luma.lifeos.data.entity.MoodEntryEntity
import com.luma.lifeos.data.entity.PersonEntity
import com.luma.lifeos.data.entity.PlaceEntity
import com.luma.lifeos.data.entity.TagEntity
import com.luma.lifeos.data.repository.EventVisibility
import com.luma.lifeos.data.repository.EventWrite
import com.luma.lifeos.data.repository.RoomCatalogRepository
import com.luma.lifeos.data.repository.RoomEventStore
import com.luma.lifeos.data.repository.RoomEventVisibilityQueries
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.review.ReviewDates
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewMetricKind
import com.luma.lifeos.domain.review.reviewPeriod
import com.luma.lifeos.domain.search.SearchQuery
import com.luma.lifeos.domain.time.TimeRange
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventVisibilityTest {
    private val date = LocalDate.parse("2026-10-10")
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private lateinit var database: LumaDatabase
    private lateinit var events: RoomEventStore
    private lateinit var catalog: RoomCatalogRepository
    private lateinit var visible: RoomEventVisibilityQueries

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LumaDatabase::class.java,
        ).build()
        events = RoomEventStore(database)
        catalog = RoomCatalogRepository(database)
        visible = RoomEventVisibilityQueries(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun withoutMemoryFiltersBeforeCountsPagesHistoriesAndAggregatesWhileKeepingPublicHabits() = runBlocking {
        val person = PersonEntity(id(), "Public friend")
        val place = PlaceEntity(id(), "Public park")
        val privateTag = TagEntity(id(), "Private tag")
        catalog.savePerson(person)
        catalog.savePlace(place)
        catalog.saveTag(privateTag)

        val publicEarlier = event(2, EventType.MOOD, "Shared entry public earlier")
        val privateLegacyJournal = event(1, EventType.JOURNAL, "Shared entry legacy journal")
        val privateMarkedMood = event(3, EventType.MOOD, "Shared entry marked mood")
        val publicLater = event(4, EventType.MOOD, "Shared entry public later")
        events.save(EventWrite(
            event = publicEarlier,
            mood = MoodEntryEntity(id(), publicEarlier.id.toString(), 2, energy = 2),
            personIds = setOf(person.id),
            placeId = place.id,
        ))
        events.save(EventWrite(
            event = privateLegacyJournal,
            journal = JournalEntryEntity(id(), privateLegacyJournal.id.toString(), "Legacy text", "Private body"),
            personIds = setOf(person.id),
            placeId = place.id,
            tagIds = setOf(privateTag.id),
        ))
        events.save(EventWrite(
            event = privateMarkedMood,
            mood = MoodEntryEntity(id(), privateMarkedMood.id.toString(), 5, energy = 5),
            memory = MemoryEntity(id(), privateMarkedMood.id.toString()),
            personIds = setOf(person.id),
            placeId = place.id,
            tagIds = setOf(privateTag.id),
        ))
        events.save(EventWrite(
            event = publicLater,
            mood = MoodEntryEntity(id(), publicLater.id.toString(), 4, energy = 4),
            personIds = setOf(person.id),
            placeId = place.id,
        ))

        val habits = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC))
        val habitId = UUID.randomUUID()
        habits.create(
            habitId,
            HabitSaveInput("Water", "glasses", date, 2, DayOfWeek.entries.toSet(), zone),
        )
        habits.log(UUID.randomUUID(), HabitLogInput(habitId, date, zone, now))

        val range = TimeRange.forDay(date, zone)
        val allTimeline = visible.timelinePage(range, 0, 10, showAppUsage = false, visibility = EventVisibility.ALL)
        assertEquals(4, allTimeline.totalCount)
        assertEquals(4, allTimeline.eventCount)
        assertEquals(4, allTimeline.records.size)

        val firstLockedPage = visible.timelinePage(range, 0, 1, false, EventVisibility.WITHOUT_MEMORY)
        val secondLockedPage = visible.timelinePage(range, 1, 1, false, EventVisibility.WITHOUT_MEMORY)
        assertEquals(2, firstLockedPage.totalCount)
        assertEquals(2, firstLockedPage.eventCount)
        assertEquals(publicLater.id, firstLockedPage.records.single().event.id)
        assertEquals(publicEarlier.id, secondLockedPage.records.single().event.id)

        val allSearch = visible.searchPage(SearchQuery("Shared entry"), zone, 0, 10, EventVisibility.ALL)
        val lockedSearch = visible.searchPage(SearchQuery("Shared entry"), zone, 0, 1, EventVisibility.WITHOUT_MEMORY)
        val lockedSearchSecond = visible.searchPage(SearchQuery("Shared entry"), zone, 1, 1, EventVisibility.WITHOUT_MEMORY)
        assertEquals(4, allSearch.totalCount)
        assertEquals(2, lockedSearch.totalCount)
        assertEquals(publicLater.id, lockedSearch.records.single().event.id)
        assertEquals(publicEarlier.id, lockedSearchSecond.records.single().event.id)

        val feed = visible.timelineFeed(date, zone, 0, 10, false, EventVisibility.WITHOUT_MEMORY)
        assertEquals(3, feed.totalCount)
        assertEquals(2, feed.eventCount)
        assertEquals(1, feed.habitLogCount)
        assertEquals(1, feed.items.count { it is com.luma.lifeos.domain.habit.TimelineFeedItem.Habit })

        val entriesMetric = ReviewMetric(ReviewMetricKind.ENTRIES)
        val lockedReviewSources = visible.reviewSources(
            ReviewDates(date, date), zone, entriesMetric, 0, 10, EventVisibility.WITHOUT_MEMORY,
        )
        assertEquals(2, lockedReviewSources.totalCount)
        assertEquals(setOf(publicEarlier.id, publicLater.id), lockedReviewSources.records.map { it.event.id }.toSet())

        val allSummary = visible.summary(date, zone, EventVisibility.ALL)
        val cachedBeforeFilteredSummary = database.derived().summary(date.toString(), zone.id)
        val lockedSummary = visible.summary(date, zone, EventVisibility.WITHOUT_MEMORY)
        val cachedAfterFilteredSummary = database.derived().summary(date.toString(), zone.id)
        assertEquals(4, allSummary.records)
        assertEquals(2, lockedSummary.records)
        assertEquals(2, lockedSummary.mood?.score?.samples)
        assertEquals(0, lockedSummary.journals)
        assertEquals(0, lockedSummary.memories)
        assertNotNull(cachedBeforeFilteredSummary)
        assertEquals(cachedBeforeFilteredSummary?.metricsJson, cachedAfterFilteredSummary?.metricsJson)

        val period = reviewPeriod(date, date, zone)
        val allReview = visible.observeReview(period, EventVisibility.ALL).first()
        val lockedReview = visible.observeReview(period, EventVisibility.WITHOUT_MEMORY).first()
        assertEquals(4, allReview.current.records)
        assertEquals(2, lockedReview.current.records)
        assertEquals(2, lockedReview.current.mood?.score?.samples)

        val personHistory = visible.observePersonHistory(UUID.fromString(person.id), EventVisibility.WITHOUT_MEMORY).first()
        val placeHistory = visible.observePlaceHistory(UUID.fromString(place.id), EventVisibility.WITHOUT_MEMORY).first()
        assertNotNull(personHistory)
        assertNotNull(placeHistory)
        assertEquals(setOf(publicEarlier.id, publicLater.id), personHistory!!.events.map { it.event.id }.toSet())
        assertEquals(setOf(publicEarlier.id, publicLater.id), placeHistory!!.events.map { it.event.id }.toSet())

        val lockedOptions = visible.observeSearchOptions(EventVisibility.WITHOUT_MEMORY).first()
        assertTrue(lockedOptions.tags.isEmpty())
        assertEquals(listOf("Public friend"), lockedOptions.people.map { it.name })
        assertEquals(listOf("Public park"), lockedOptions.places.map { it.name })
        assertFalse(lockedOptions.people.isEmpty())
    }

    private fun event(number: Long, type: EventType, title: String) = LifeEvent(
        id = EventId.parse("00000000-0000-0000-0000-%012d".format(number)),
        type = type,
        occurredAt = now,
        createdAt = now,
        title = title,
    )

    private fun id() = UUID.randomUUID().toString()
}
