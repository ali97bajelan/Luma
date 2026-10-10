package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EmotionEntity
import com.luma.lifeos.data.entity.PersonEntity
import com.luma.lifeos.data.entity.PlaceEntity
import com.luma.lifeos.data.entity.TagEntity
import com.luma.lifeos.domain.model.EventId

/** Catalog deletion removes links via foreign keys, preserving every linked life event. */
class RoomCatalogRepository(private val database: LumaDatabase) {
    private val dao = database.catalog()
    fun observeEmotions() = dao.observeEmotions()
    fun observePeople() = dao.observePeople()
    fun observePlaces() = dao.observePlaces()
    fun observeTags() = dao.observeTags()
    suspend fun emotion(id: String) = dao.emotion(id)
    suspend fun person(id: String) = dao.person(id)
    suspend fun place(id: String) = dao.place(id)
    suspend fun tag(id: String) = dao.tag(id)

    suspend fun saveEmotion(emotion: EmotionEntity): String = database.withTransaction {
        EventId.parse(emotion.id)
        val existing = dao.emotionNamed(emotion.name)
        require(dao.emotion(emotion.id) == null || existing == null || existing.id == emotion.id) {
            "Emotion ID and name identify different existing entries"
        }
        val canonical = emotion.copy(id = existing?.id ?: emotion.id)
        dao.upsertEmotion(canonical)
        database.derived().invalidateSummaries()
        canonical.id
    }

    suspend fun saveTag(tag: TagEntity): String = database.withTransaction {
        EventId.parse(tag.id)
        val existing = dao.tagNamed(tag.normalizedName)
        require(dao.tag(tag.id) == null || existing == null || existing.id == tag.id) {
            "Tag ID and name identify different existing entries"
        }
        val canonical = tag.copy(id = existing?.id ?: tag.id)
        dao.upsertTag(canonical)
        database.derived().invalidateSummaries()
        canonical.id
    }

    suspend fun savePerson(person: PersonEntity) = mutate {
        EventId.parse(person.id)
        dao.upsertPerson(person)
    }

    suspend fun savePlace(place: PlaceEntity) = mutate {
        EventId.parse(place.id)
        dao.upsertPlace(place)
    }

    suspend fun deleteEmotion(id: String) = mutate { dao.deleteEmotion(id) }
    suspend fun deletePerson(id: String) = mutate { dao.deletePerson(id) }
    suspend fun deletePlace(id: String) = mutate { dao.deletePlace(id) }
    suspend fun deleteTag(id: String) = mutate { dao.deleteTag(id) }

    private suspend fun mutate(block: suspend () -> Unit) {
        database.withTransaction {
            block()
            database.derived().invalidateSummaries()
        }
    }
}
