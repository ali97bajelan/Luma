package com.luma.lifeos.domain.lock

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MemoryDetails
import com.luma.lifeos.domain.model.MoodDetails
import com.luma.lifeos.domain.model.Person
import java.time.Instant
import java.util.UUID

/** Independent expected visibility oracle for the canonical App Lock coverage choices. */
internal object AppLockFixture {
    private val timestamp = Instant.parse("2026-10-31T06:30:00Z")

    val records: List<FixtureRecord> = listOf(
        FixtureRecord("memory_private", setOf(LockSection.MEMORIES), eventRecord("memory_private", EventType.MEMORY, marker = true, linkedPeople = true)),
        FixtureRecord("journal_public", setOf(LockSection.MEMORIES), eventRecord("journal_public", EventType.JOURNAL)),
        FixtureRecord("journal_marked", setOf(LockSection.MEMORIES), eventRecord("journal_marked", EventType.JOURNAL, marker = true)),
        FixtureRecord("mood_public", setOf(LockSection.MOOD), eventRecord("mood_public", EventType.MOOD)),
        FixtureRecord("mood_marked", setOf(LockSection.MOOD, LockSection.MEMORIES), eventRecord("mood_marked", EventType.MOOD, marker = true)),
        FixtureRecord("journal_person_linked", setOf(LockSection.MEMORIES), eventRecord("journal_person_linked", EventType.JOURNAL, linkedPeople = true)),
        FixtureRecord("health_activity", setOf(LockSection.HEALTH_USAGE), eventRecord("health_activity", EventType.ACTIVITY, EventSource.HEALTH_CONNECT)),
        FixtureRecord("usage_event", setOf(LockSection.HEALTH_USAGE), eventRecord("usage_event", EventType.APP_USAGE, EventSource.USAGE_STATS)),
        FixtureRecord("task_open", setOf(LockSection.TASKS)),
        FixtureRecord("habit_water", setOf(LockSection.HABITS)),
        FixtureRecord("person_en", setOf(LockSection.PEOPLE)),
        FixtureRecord("person_fa", setOf(LockSection.PEOPLE)),
        FixtureRecord("place_fixture", emptySet(), knownUnprotected = true),
        FixtureRecord("focus_saved", setOf(LockSection.FOCUS)),
    )

    val profiles: List<FixtureProfile> = listOf(
        FixtureProfile(
            "memory_section_locked",
            LockPolicy(enabled = true, coverage = LockCoverage.MEMORY_SECTION, generation = 1),
            setOf("memory_private", "journal_public", "journal_marked", "journal_person_linked", "mood_marked"),
        ),
        FixtureProfile(
            "whole_app_locked",
            LockPolicy(enabled = true, coverage = LockCoverage.WHOLE_APP, generation = 1),
            records.map { it.alias }.toSet(),
        ),
        FixtureProfile(
            "disabled",
            LockPolicy(enabled = false, coverage = LockCoverage.WHOLE_APP, generation = 1),
            emptySet(),
        ),
    )

    private fun eventRecord(
        alias: String,
        type: EventType,
        source: EventSource = EventSource.MANUAL,
        marker: Boolean = false,
        linkedPeople: Boolean = false,
    ): LifeEventRecord {
        val event = LifeEvent(
            id = EventId(UUID.nameUUIDFromBytes(alias.toByteArray())),
            type = type,
            occurredAt = timestamp,
            createdAt = timestamp,
            source = source,
            sourceReference = if (source == EventSource.MANUAL) null else "qa-$alias",
        )
        val people = if (linkedPeople) listOf(
            Person(UUID.nameUUIDFromBytes("person-en".toByteArray()), "Synthetic Person"),
            Person(UUID.nameUUIDFromBytes("person-fa".toByteArray()), "شخص آزمایشی"),
        ) else emptyList()
        return LifeEventRecord(
            event = event,
            mood = if (type == EventType.MOOD) MoodDetails(score = 3) else null,
            memory = if (marker || type == EventType.MEMORY) MemoryDetails(importance = 4) else null,
            people = people,
        )
    }

    data class FixtureRecord(
        val alias: String,
        val requiredSections: Set<LockSection>,
        val event: LifeEventRecord? = null,
        val knownUnprotected: Boolean = false,
    )

    data class FixtureProfile(
        val name: String,
        val policy: LockPolicy,
        val hiddenAliases: Set<String>,
    )
}
