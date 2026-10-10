package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.SourceIdentity
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var database: LumaDatabase
    private lateinit var events: RoomLifeEventRepository
    private lateinit var store: RoomEventStore
    private lateinit var catalog: RoomCatalogRepository
    private lateinit var derived: RoomDerivedDataRepository
    private val now = Instant.parse("2026-10-10T08:00:00Z")
    private val date = LocalDate.parse("2026-10-10")
    private val zone = ZoneId.of("Asia/Tehran")
    private val day = TimeRange.forDay(date, zone)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java,
        ).build()
        events = RoomLifeEventRepository(database)
        store = RoomEventStore(database)
        catalog = RoomCatalogRepository(database)
        derived = RoomDerivedDataRepository(database)
    }

    @After fun tearDown() { database.close() }

    private fun id(): String = EventId.new().toString()
    private fun event(type: EventType = EventType.JOURNAL): LifeEvent = LifeEvent(
        EventId.new(), type, occurredAt = now, createdAt = now,
    )

    private suspend fun expectFailure(block: suspend () -> Unit) {
        val failure = try { block(); null } catch (error: Exception) { error }
        assertNotNull("Expected the write to fail", failure)
    }

    private fun assertEmptyTable(table: String) {
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Expected cascade for $table", 0, cursor.getInt(0))
        }
    }

    @Test fun eventCrudPreservesCreationIdentityAndMetadata() = runBlocking {
        val original = event()
        val journal = JournalEntryEntity(id(), original.id.toString(), "First entry")
        store.save(EventWrite(original, journal = journal, metadataJson = "{\"draft\":false}"))
        val updated = original.copy(title = "Updated", createdAt = now.plusSeconds(1), updatedAt = now.plusSeconds(2))
        assertEquals(original.id, events.upsert(updated))
        val stored = requireNotNull(events.get(original.id))
        assertEquals("Updated", stored.title)
        assertEquals(original.createdAt, stored.createdAt)
        val details = requireNotNull(store.get(original.id))
        assertEquals(journal, details.journal)
        assertEquals("{\"draft\":false}", details.event.metadataJson)
        events.delete(original.id)
        assertNull(events.get(original.id))
        assertNull(store.get(original.id))
        assertEmptyTable("journal_entry")
    }

    @Test fun observationsEmitCommittedChangesAndUseHalfOpenStableOrdering() = runBlocking {
        withTimeout(5_000) {
            val snapshots = Channel<List<LifeEvent>>(Channel.UNLIMITED)
            val observation = launch {
                events.observe(day).distinctUntilChanged().collect { snapshots.send(it) }
            }
            try {
                assertTrue(snapshots.receive().isEmpty())
                val first = event().copy(id = EventId.parse("00000000-0000-0000-0000-000000000001"))
                val second = first.copy(id = EventId.parse("00000000-0000-0000-0000-000000000002"))
                events.upsert(second)
                assertEquals(listOf(second), snapshots.receive())
                events.upsert(first)
                assertEquals(listOf(first, second), snapshots.receive())
                events.upsert(event().copy(occurredAt = day.endExclusive))
                events.upsert(event().copy(occurredAt = day.start.minusMillis(1)))
                assertEquals(listOf(first, second), events.observe(day).first())
                val atStart = event().copy(occurredAt = day.start)
                events.upsert(atStart)
                assertEquals(listOf(atStart, first, second), events.observe(day).first())
            } finally {
                observation.cancelAndJoin()
                snapshots.close()
            }
        }
    }

    @Test fun fractionalQueryBoundsRoundUpAtStoragePrecision() = runBlocking {
        val original = event()
        events.upsert(original)
        events.upsert(event().copy(occurredAt = now.plusMillis(1)))
        val narrow = TimeRange(now.plusNanos(1), now.plusMillis(1))
        assertTrue(events.observe(narrow).first().isEmpty())
        val includesNextMillisecond = TimeRange(now.plusNanos(1), now.plusMillis(1).plusNanos(1))
        assertEquals(1, events.observe(includesNextMillisecond).first().size)
    }

    @Test fun aggregateCrudLoadsAllRelationsAndReportsRemovedMediaAfterCommit() = runBlocking {
        val original = event()
        val person = PersonEntity(id(), "A friend")
        val place = PlaceEntity(id(), "Park", latitude = 35.7, longitude = 51.4)
        val tag = TagEntity(id(), "Weekend")
        catalog.savePerson(person)
        catalog.savePlace(place)
        catalog.saveTag(tag)
        val journal = JournalEntryEntity(id(), original.id.toString(), "A walk in the park")
        val image = MediaAttachmentEntity(id(), original.id.toString(), "media/${id()}.jpg")
        val write = EventWrite(
            original, journal = journal, personIds = setOf(person.id), placeId = place.id,
            tagIds = setOf(tag.id), attachments = listOf(image),
        )
        store.save(write)
        val loaded = requireNotNull(store.get(original.id))
        assertEquals(journal, loaded.journal)
        assertEquals(listOf(person), loaded.people)
        assertEquals(listOf(place), loaded.places)
        assertEquals(listOf(tag), loaded.tags)
        assertEquals(listOf(image), loaded.attachments)
        assertEquals(listOf(original.id.toString()), store.observe(day).first().map { it.event.id })
        val result = store.save(write.copy(
            event = original.copy(updatedAt = now.plusSeconds(1)),
            journal = journal.copy(body = "Edited"), attachments = emptyList(),
        ))
        assertEquals(listOf(image.relativePath), result.unreferencedMediaPaths)
        assertEquals("Edited", store.get(original.id)?.journal?.body)
        assertTrue(store.referencedMediaPaths().isEmpty())
        store.delete(original.id)
        assertNull(store.get(original.id))
        assertEquals(person, catalog.person(person.id))
        assertEquals(place, catalog.place(place.id))
        assertEquals(tag, catalog.tag(tag.id))
    }

    @Test fun moodEmotionLinksCanBeUpdatedAndCascadeWithTheMood() = runBlocking {
        val first = EmotionEntity(id(), "Calm")
        val second = EmotionEntity(id(), "Grateful")
        catalog.saveEmotion(first)
        catalog.saveEmotion(second)
        val original = event(EventType.MOOD)
        val mood = MoodEntryEntity(id(), original.id.toString(), 4, energy = 3, stress = 1)
        val write = EventWrite(original, mood = mood, emotionIds = setOf(first.id, second.id))
        store.save(write)
        assertEquals(setOf(first, second), store.get(original.id)?.mood?.emotions?.toSet())
        store.save(write.copy(mood = mood.copy(moodScore = 5), emotionIds = setOf(first.id)))
        assertEquals(5, store.get(original.id)?.mood?.entry?.moodScore)
        assertEquals(listOf(first), store.get(original.id)?.mood?.emotions)
        catalog.deleteEmotion(first.id)
        assertTrue(requireNotNull(store.get(original.id)?.mood).emotions.isEmpty())
        events.delete(original.id)
        assertNull(store.get(original.id))
        assertEquals(second, catalog.emotion(second.id))
    }

    @Test fun deletingCatalogItemsRemovesOnlyLinksAndCatalogUpdatesKeepLinks() = runBlocking {
        val person = PersonEntity(id(), "Friend")
        val place = PlaceEntity(id(), "Cafe")
        val tag = TagEntity(id(), "Coffee")
        catalog.savePerson(person)
        catalog.savePlace(place)
        catalog.saveTag(tag)
        val original = event()
        store.save(EventWrite(original, personIds = setOf(person.id), placeId = place.id, tagIds = setOf(tag.id)))
        catalog.savePerson(person.copy(name = "Updated friend"))
        catalog.savePlace(place.copy(note = "A quiet place"))
        catalog.saveTag(tag.copy(name = "COFFEE"))
        val updated = requireNotNull(store.get(original.id))
        assertEquals("Updated friend", updated.people.single().name)
        assertEquals("A quiet place", updated.places.single().note)
        assertEquals("COFFEE", updated.tags.single().name)
        catalog.deletePerson(person.id)
        catalog.deletePlace(place.id)
        catalog.deleteTag(tag.id)
        val unlinked = requireNotNull(store.get(original.id))
        assertTrue(unlinked.people.isEmpty() && unlinked.places.isEmpty() && unlinked.tags.isEmpty())
        assertNotNull(events.get(original.id))
    }

    @Test fun catalogNaturalKeysReturnTheExistingIdWithoutReplacingLinkedRows() = runBlocking {
        val tag = TagEntity(id(), " Weekend ")
        val emotion = EmotionEntity(id(), "Calm")
        catalog.saveTag(tag)
        catalog.saveEmotion(emotion)
        assertEquals(tag.id, catalog.saveTag(TagEntity(id(), "weekend")))
        assertEquals(emotion.id, catalog.saveEmotion(EmotionEntity(id(), "Calm")))
        assertEquals(1, catalog.observeTags().first().size)
        assertEquals(1, catalog.observeEmotions().first().size)
    }

    @Test fun memoryPromotionUpdatesAndRemovalPreserveOriginalJournalAndIdentity() = runBlocking {
        val original = event()
        val journal = JournalEntryEntity(id(), original.id.toString(), "Keep the original text")
        store.save(EventWrite(original, journal = journal))
        val memory = MemoryEntity(id(), original.id.toString(), importance = 4)
        store.promoteToMemory(memory)
        store.promoteToMemory(memory.copy(importance = 5, note = "A notable day"))
        val promoted = requireNotNull(store.get(original.id))
        assertEquals(EventType.JOURNAL, promoted.event.type)
        assertEquals(journal, promoted.journal)
        assertEquals(5, promoted.memory?.importance)
        assertEquals(1, events.observe(day).first().size)
        store.removeMemory(original.id)
        assertNull(store.get(original.id)?.memory)
        assertEquals(journal, store.get(original.id)?.journal)
        val direct = event(EventType.MEMORY)
        store.save(EventWrite(direct, memory = MemoryEntity(id(), direct.id.toString(), note = "Direct memory")))
        assertEquals("Direct memory", store.get(direct.id)?.memory?.note)
    }

    @Test fun failedLinkWriteRollsBackEnvelopeContentAndSummaryInvalidation() = runBlocking {
        val original = event()
        val journal = JournalEntryEntity(id(), original.id.toString(), "Original")
        store.save(EventWrite(original, journal = journal))
        derived.rebuildSummary(date, zone) { summary() }
        expectFailure {
            store.save(EventWrite(
                original.copy(title = "Must roll back", updatedAt = now.plusSeconds(1)),
                journal = journal.copy(body = "Must roll back"), personIds = setOf(id()),
            ))
        }
        assertNull(events.get(original.id)?.title)
        assertEquals("Original", store.get(original.id)?.journal?.body)
        assertEquals(SummaryFreshness.FRESH, derived.summary(date, zone)?.freshness)
        val newEvent = event()
        expectFailure { store.save(EventWrite(newEvent, tagIds = setOf(id()))) }
        assertNull(events.get(newEvent.id))
    }

    @Test fun activityImportRetriesKeepEventIdentityAndAdvanceCheckpointAtomically() = runBlocking {
        val original = event(EventType.ACTIVITY).copy(
            source = EventSource.HEALTH_CONNECT, sourceReference = "steps:one",
        )
        val record = ActivityRecordEntity(id(), original.id.toString(), "steps", 120.0, "count", original.source, "steps:one")
        val state = IntegrationStateEntity(original.source, IntegrationStatus.READY, now.toEpochMilli(), "page:one")
        store.saveImportBatch(listOf(EventWrite(original, activities = listOf(record))), state)
        val retry = original.copy(id = EventId.new(), createdAt = now.plusSeconds(1), updatedAt = now.plusSeconds(1))
        val result = store.saveImportBatch(listOf(EventWrite(
            retry, activities = listOf(record.copy(lifeEventId = retry.id.toString(), value = 150.0)),
        )), state.copy(checkpoint = "page:two"))
        assertEquals(original.id, result.single().eventId)
        assertNull(events.get(retry.id))
        assertEquals(original.createdAt, events.get(original.id)?.createdAt)
        assertEquals(150.0, requireNotNull(store.get(original.id)).activities.single().value, 0.0)
        assertEquals(original.id, events.findBySource(SourceIdentity(original.source, "steps:one"))?.id)
        assertEquals("page:two", derived.integration(original.source)?.checkpoint)
        assertEquals(1, events.observe(day).first().size)
        val failing = event(EventType.ACTIVITY).copy(source = original.source, sourceReference = "steps:two")
        expectFailure {
            store.saveImportBatch(listOf(
                EventWrite(original.copy(updatedAt = now.plusSeconds(2)), activities = listOf(record.copy(value = 999.0))),
                EventWrite(failing, personIds = setOf(id())),
            ), state.copy(checkpoint = "must-not-advance"))
        }
        assertEquals(150.0, requireNotNull(store.get(original.id)).activities.single().value, 0.0)
        assertNull(events.get(failing.id))
        assertEquals("page:two", derived.integration(original.source)?.checkpoint)
        store.delete(original.id)
        assertEmptyTable("activity_record")
    }

    @Test fun sourceIdentityAndTypeChangesAreRejectedWithoutDamagingExistingContent() = runBlocking {
        val original = event()
        events.upsert(original)
        expectFailure { events.upsert(original.copy(source = EventSource.HEALTH_CONNECT, sourceReference = "remote")) }
        expectFailure { events.upsert(original.copy(type = EventType.MOOD)) }
        events.upsert(original.copy(updatedAt = now.plusSeconds(2)))
        expectFailure { events.upsert(original.copy(updatedAt = now.plusSeconds(1))) }
        val imported = event().copy(source = EventSource.HEALTH_CONNECT, sourceReference = "remote")
        events.upsert(imported)
        expectFailure { events.upsert(imported.copy(id = original.id, updatedAt = now.plusSeconds(3))) }
        assertEquals(EventSource.MANUAL, events.get(original.id)?.source)
        assertEquals(imported, events.get(imported.id))
    }

    @Test fun appUsageNaturalKeyRejectsDuplicatesAndAllowsTransactionalEdits() = runBlocking {
        val original = event(EventType.APP_USAGE).copy(
            source = EventSource.USAGE_STATS, sourceReference = "usage:one", endedAt = now.plusSeconds(60),
        )
        val usage = AppUsageRecordEntity(
            id(), original.id.toString(), now.toEpochMilli(), now.plusSeconds(60).toEpochMilli(),
            "com.example.reader", 10_000,
        )
        store.save(EventWrite(original, appUsage = usage))
        store.save(EventWrite(original.copy(updatedAt = now.plusSeconds(1)), appUsage = usage.copy(durationMs = 20_000)))
        assertEquals(20_000L, store.get(original.id)?.appUsage?.durationMs)
        val duplicate = original.copy(id = EventId.new(), sourceReference = "usage:duplicate")
        expectFailure { store.save(EventWrite(duplicate, appUsage = usage.copy(id = id(), lifeEventId = duplicate.id.toString()))) }
        assertNull(events.get(duplicate.id))
        store.delete(original.id)
        assertNull(store.get(original.id))
        assertEmptyTable("app_usage_record")
    }

    private fun summary(zoneId: ZoneId = zone) = DailySummaryEntity(
        date.toString(), zoneId.id, "{\"eventCount\":1}", 1, now.toEpochMilli(),
    )

    @Test fun derivedCachesUseDateAndZoneKeysAndRawWritesMarkThemStale() = runBlocking {
        val utc = ZoneId.of("UTC")
        derived.rebuildSummary(date, zone) { rows ->
            assertTrue(rows.isEmpty())
            summary()
        }
        derived.rebuildSummary(date, utc) { summary(utc) }
        assertEquals(SummaryFreshness.FRESH, derived.observeSummary(date, zone).first()?.freshness)
        val original = event()
        events.upsert(original)
        assertEquals(SummaryFreshness.STALE, derived.summary(date, zone)?.freshness)
        assertEquals(SummaryFreshness.STALE, derived.summary(date, utc)?.freshness)
        derived.rebuildSummary(date, zone) { rows ->
            assertEquals(listOf(original.id.toString()), rows.map { it.event.id })
            summary().copy(algorithmVersion = 2)
        }
        assertEquals(2, derived.summary(date, zone)?.algorithmVersion)
        assertEquals(SummaryFreshness.FRESH, derived.summary(date, zone)?.freshness)
        derived.deleteSummary(date, zone)
        assertNull(derived.summary(date, zone))
        assertNotNull(derived.summary(date, utc))
        val state = IntegrationStateEntity(EventSource.USAGE_STATS, IntegrationStatus.PERMISSION_DENIED)
        derived.saveIntegration(state)
        assertEquals(state, derived.observeIntegrations().first().single())
        derived.saveIntegration(state.copy(status = IntegrationStatus.READY, checkpoint = "next"))
        assertEquals("next", derived.integration(state.source)?.checkpoint)
        derived.deleteIntegration(state.source)
        assertNull(derived.integration(state.source))
    }

    @Test fun eventDeletionCascadesAllDependentTablesButKeepsSharedCatalogEntries() = runBlocking {
        val original = event(EventType.MOOD)
        val emotion = EmotionEntity(id(), "Calm")
        val person = PersonEntity(id(), "Friend")
        val place = PlaceEntity(id(), "Home")
        val tag = TagEntity(id(), "Day")
        catalog.saveEmotion(emotion)
        catalog.savePerson(person)
        catalog.savePlace(place)
        catalog.saveTag(tag)
        val image = MediaAttachmentEntity(id(), original.id.toString(), "media/${id()}.jpg")
        store.save(EventWrite(
            original, mood = MoodEntryEntity(id(), original.id.toString(), 3), emotionIds = setOf(emotion.id),
            memory = MemoryEntity(id(), original.id.toString()), personIds = setOf(person.id),
            placeId = place.id, tagIds = setOf(tag.id), attachments = listOf(image),
        ))
        assertEquals(listOf(image.relativePath), store.delete(original.id))
        val sqlite = database.openHelper.readableDatabase
        listOf("mood_entry", "mood_emotion", "memory", "event_person", "event_place", "event_tag", "media_attachment").forEach { table ->
            assertEmptyTable(table)
        }
        sqlite.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        assertNotNull(catalog.emotion(emotion.id))
        assertNotNull(catalog.person(person.id))
        assertNotNull(catalog.place(place.id))
        assertNotNull(catalog.tag(tag.id))
    }
}
