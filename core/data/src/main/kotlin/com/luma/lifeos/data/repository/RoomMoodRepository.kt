package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EmotionEntity
import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.data.entity.MoodEmotionEntity
import com.luma.lifeos.data.entity.MoodEntryEntity
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.model.Emotion
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.model.MoodNotePolicy
import com.luma.lifeos.domain.repository.MoodRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMoodRepository(private val database: LumaDatabase, private val clock: Clock = Clock.systemUTC()) : MoodRepository {
    private val envelopes = RoomLifeEventRepository(database)
    private val content = database.content()

    override suspend fun prepareEmotions() = database.withTransaction {
        // Keep any existing catalog row with this name; repeated initialization never renames it.
        listOf("Happy", "Calm", "Grateful", "Excited", "Hopeful", "Proud",
            "Sad", "Anxious", "Angry", "Lonely", "Tired", "Overwhelmed").forEach { name ->
            val id = UUID.nameUUIDFromBytes("luma.emotion.$name".toByteArray(Charsets.UTF_8))
            if (database.catalog().emotionNamed(name) == null && database.catalog().emotion(id.toString()) == null) {
                database.catalog().upsertEmotion(EmotionEntity(id.toString(), name))
            }
        }
    }

    override fun observeEmotions(): Flow<List<Emotion>> = database.catalog().observeEmotions()
        .map { rows -> rows.map { Emotion(UUID.fromString(it.id), it.name) } }

    override suspend fun get(id: EventId): LifeEventRecord? =
        database.lifeEvents().getDetails(id.toString())?.takeIf { it.mood != null }?.toRecord()

    override suspend fun create(id: EventId, input: MoodInput): EventId = database.withTransaction {
        val previous = database.lifeEvents().getDetails(id.toString())
        val previousNote = previous?.mood?.entry?.note.orEmpty()
        require(MoodNotePolicy.canAcceptMoodNote(previousNote, input.note.orEmpty())) {
            "Mood note may contain at most ${MoodNotePolicy.LIMIT} UTF-16 units unless it is unchanged or shortened"
        }
        val note = input.note.cleanNote()
        if (previous != null) {
            val record = editable(previous).toRecord()
            val mood = requireNotNull(record.mood)
            require(record.event.occurredAt.toEpochMilli() == input.occurredAt.toEpochMilli() &&
                mood.score == input.score && mood.energy == input.energy && mood.stress == input.stress &&
                mood.note == note && mood.emotions.map { it.id }.toSet() == input.emotionIds &&
                (input.personIds == null || record.people.map { it.id }.toSet() == input.personIds) &&
                (input.placeSelection == null || record.place?.id == input.placeSelection?.id)) {
                "This capture ID already belongs to a different check-in"
            }
            return@withTransaction id
        }
        val now = clock.instant()
        val event = LifeEvent(id, EventType.MOOD, input.occurredAt, now, note = note)
        envelopes.upsertInTransaction(event)
        val entry = MoodEntryEntity(EventId.new().toString(), id.toString(), input.score, input.energy, input.stress, note)
        content.insertMood(entry)
        replaceEmotions(entry.id, input)
        replacePeopleInTransaction(database, id, input.personIds)
        replacePlaceInTransaction(database, id, input.placeSelection)
        database.derived().invalidateSummaries()
        id
    }

    override suspend fun update(id: EventId, input: MoodInput) = database.withTransaction {
        val previous = editable(requireNotNull(database.lifeEvents().getDetails(id.toString())) { "Check-in was deleted" })
        val record = previous.toRecord()
        val previousNote = record.mood?.note.orEmpty()
        require(MoodNotePolicy.canAcceptMoodNote(previousNote, input.note.orEmpty())) {
            "Mood note may contain at most ${MoodNotePolicy.LIMIT} UTF-16 units unless it is unchanged or shortened"
        }
        val note = input.note.cleanNote()
        envelopes.upsertInTransaction(record.event.copy(occurredAt = input.occurredAt, endedAt = null,
            updatedAt = maxOf(clock.instant(), record.event.updatedAt), note = note))
        // Targeted updates preserve memory markers, media, metadata, and all unrelated links.
        val entry = requireNotNull(previous.mood).entry
        content.updateMood(entry.copy(moodScore = input.score, energy = input.energy, stress = input.stress, note = note))
        replaceEmotions(entry.id, input)
        replacePeopleInTransaction(database, id, input.personIds)
        replacePlaceInTransaction(database, id, input.placeSelection)
        database.derived().invalidateSummaries()
    }

    override suspend fun delete(id: EventId) = database.withTransaction {
        editable(requireNotNull(database.lifeEvents().getDetails(id.toString())) { "Check-in was deleted" })
        // Photo cleanup rechecks committed references after deletion; no physical file IO here.
        database.lifeEvents().delete(id.toString())
        database.derived().invalidateSummaries()
    }

    private suspend fun replaceEmotions(moodId: String, input: MoodInput) {
        content.deleteMoodEmotions(moodId)
        content.insertEmotions(input.emotionIds.map { MoodEmotionEntity(moodId, it.toString()) })
    }

    private fun editable(record: EventWithDetails): EventWithDetails {
        require(record.event.type == EventType.MOOD && record.mood != null && record.toRecord().event.isEditable) {
            "Only complete manual mood check-ins can be changed"
        }
        return record
    }

    private fun String?.cleanNote(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
