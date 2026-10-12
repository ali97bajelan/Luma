package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EventPersonEntity
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.domain.model.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class HealthPersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var repository: RoomHealthRepository
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val zone = ZoneId.of("Asia/Tehran")
    private val observation = StepObservation(stepWindows(now, zone).last(), 100)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        repository = RoomHealthRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun enable() { repository.setEnabled(true, zone) }
    private suspend fun commit(rows: List<StepObservation>, at: Instant = now) { repository.commit(rows, at, repository.state()) }
    @Test fun retriesUpdateTotalsAndPreserveIdentityAuditMetadataAndLinks() = runBlocking {
        enable(); commit(listOf(observation))
        val old = db.lifeEvents().findBySource(EventSource.HEALTH_CONNECT, observation.window.sourceReference)!!
        val oldChild = db.lifeEvents().getDetails(old.id)!!.activities.single()
        val person = UUID.randomUUID(); RoomPersonRepository(db).create(person, PersonInput("Friend"))
        db.content().insertPeople(listOf(EventPersonEntity(old.id, person.toString())))
        db.lifeEvents().update(old.copy(metadataJson = "{\"custom\":true}"))
        val changed = observation.copy(count = 90, window = observation.window.copy(end = now.plusSeconds(60)))
        commit(listOf(changed), now.plusSeconds(60)); commit(listOf(changed), now.plusSeconds(60))
        val updated = db.lifeEvents().getDetails(old.id)!!
        assertEquals(old.createdAt, updated.event.createdAt); assertEquals(oldChild.id, updated.activities.single().id)
        assertEquals(90.0, updated.activities.single().value, 0.0); assertEquals(1, updated.people.size)
        assertTrue(updated.event.metadataJson.contains("custom"))
        assertEquals(1, db.lifeEvents().countDay(observation.window.start.toEpochMilli(), now.plusSeconds(60).toEpochMilli()))
    }
    @Test fun zeroIsSavedAndSuccessfulMissingDataRemovesOnlyItsDailyImport() = runBlocking {
        enable(); commit(listOf(observation.copy(count = 0)))
        val id = db.lifeEvents().findBySource(EventSource.HEALTH_CONNECT, observation.window.sourceReference)!!.id
        assertEquals(0.0, db.lifeEvents().getDetails(id)!!.activities.single().value, 0.0)
        val manual = LifeEvent(EventId.new(), EventType.MEMORY, now, now)
        RoomLifeEventRepository(db).upsert(manual)
        commit(listOf(observation.copy(count = null)))
        assertNull(db.lifeEvents().get(id)); assertNotNull(db.lifeEvents().get(manual.id.toString()))
        assertEquals(HealthStatus.NO_DATA, repository.state().status); assertEquals(now, repository.state().lastSync)
    }
    @Test fun conflictingBatchRollsBackDataAndCheckpoint() = runBlocking {
        enable(); commit(listOf(observation)); val before = repository.state()
        val other = StepObservation(stepWindows(now, zone).first(), 500)
        val collision = LifeEvent(EventId.new(), EventType.JOURNAL, other.window.start, now,
            source = EventSource.HEALTH_CONNECT, sourceReference = other.window.sourceReference)
        RoomLifeEventRepository(db).upsert(collision)
        assertTrue(runCatching { commit(listOf(observation.copy(count = 200), other), now.plusSeconds(1)) }.isFailure)
        assertEquals(before, repository.state())
        val id = db.lifeEvents().findBySource(EventSource.HEALTH_CONNECT, observation.window.sourceReference)!!.id
        assertEquals(100.0, db.lifeEvents().getDetails(id)!!.activities.single().value, 0.0)
    }
    @Test fun staleCommitAfterDisableReenableIsRejectedAndStateSurvivesRecreation() = runBlocking {
        enable(); commit(listOf(observation)); val old = repository.state()
        repository.setEnabled(false, ZoneId.of("UTC")); repository.setEnabled(true, ZoneId.of("UTC"))
        assertTrue(runCatching { repository.commit(listOf(observation.copy(count = null)), now, old) }.exceptionOrNull() is HealthImportChangedException)
        val recreated = RoomHealthRepository(db)
        assertEquals(zone, recreated.state().importZone); assertEquals(now, recreated.state().lastSync)
        recreated.recordStatus(HealthStatus.ACCESS_REQUIRED, recreated.state().revision)
        assertNotNull(db.lifeEvents().findBySource(EventSource.HEALTH_CONNECT, observation.window.sourceReference))
        assertEquals(now, recreated.state().lastSync)
        recreated.recordStatus(HealthStatus.ERROR, old.revision)
        assertEquals(HealthStatus.ACCESS_REQUIRED, recreated.state().status)
    }
    @Test fun mismatchedZoneAndBackwardCheckpointCannotRewriteHistory() = runBlocking {
        enable(); commit(listOf(observation)); val before = repository.state()
        assertTrue(runCatching { commit(listOf(StepObservation(stepWindows(now, ZoneId.of("UTC")).last(), 50))) }.isFailure)
        assertTrue(runCatching { commit(listOf(observation), now.minusSeconds(1)) }.isFailure)
        assertEquals(before, repository.state())
    }
    @Test fun summariesRebuildFromCorrectedAndRemovedStepSnapshots() = runBlocking {
        enable(); commit(listOf(observation))
        val summaries = RoomDailySummaryRepository(db)
        val date = observation.window.date
        assertEquals("100", summaries.get(date, zone).activities!!.single().total.toPlainString())
        commit(listOf(observation.copy(count = 80)))
        assertEquals("80", summaries.get(date, zone).activities!!.single().total.toPlainString())
        commit(listOf(observation.copy(count = null)))
        assertNull(summaries.get(date, zone).activities)
    }
}
