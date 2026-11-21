package com.luma.lifeos.data.dao

import androidx.room.Transaction
import com.luma.lifeos.data.entity.PlaceWithEvents
import com.luma.lifeos.data.entity.PersonWithEvents
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.luma.lifeos.data.entity.EmotionEntity
import com.luma.lifeos.data.entity.PersonEntity
import com.luma.lifeos.data.entity.PlaceEntity
import com.luma.lifeos.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM emotion ORDER BY name, id") fun observeEmotions(): Flow<List<EmotionEntity>>
    @Transaction
    @Query("SELECT * FROM person WHERE id = :id") fun observePersonHistory(id: String): Flow<PersonWithEvents?>
    @Query("SELECT * FROM person WHERE id = :id") fun observePersonHeader(id: String): Flow<PersonEntity?>
    @Query("""SELECT life_event.id FROM life_event INNER JOIN event_person ON event_person.event_id = life_event.id
        WHERE event_person.person_id = :id AND (:includeMemory OR (life_event.type NOT IN ('MEMORY', 'JOURNAL')
        AND NOT EXISTS (SELECT 1 FROM memory WHERE memory.life_event_id = life_event.id)))
        ORDER BY life_event.occurred_at, life_event.id""")
    fun observePersonEventIds(id: String, includeMemory: Boolean): Flow<List<String>>
    @Query("SELECT * FROM person ORDER BY name, id") fun observePeople(): Flow<List<PersonEntity>>
    @Transaction
    @Query("SELECT * FROM place WHERE id = :id") fun observePlaceHistory(id: String): Flow<PlaceWithEvents?>
    @Query("SELECT * FROM place WHERE id = :id") fun observePlaceHeader(id: String): Flow<PlaceEntity?>
    @Query("""SELECT life_event.id FROM life_event INNER JOIN event_place ON event_place.event_id = life_event.id
        WHERE event_place.place_id = :id AND (:includeMemory OR (life_event.type NOT IN ('MEMORY', 'JOURNAL')
        AND NOT EXISTS (SELECT 1 FROM memory WHERE memory.life_event_id = life_event.id)))
        ORDER BY life_event.occurred_at, life_event.id""")
    fun observePlaceEventIds(id: String, includeMemory: Boolean): Flow<List<String>>
    @Query("SELECT * FROM place WHERE id = :id") fun observePlace(id: String): Flow<PlaceEntity?>
    @Query("SELECT * FROM place ORDER BY name, id") fun observePlaces(): Flow<List<PlaceEntity>>
    @Query("SELECT * FROM tag ORDER BY normalized_name, id") fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM emotion WHERE id = :id") suspend fun emotion(id: String): EmotionEntity?
    @Query("SELECT * FROM person WHERE id = :id") suspend fun person(id: String): PersonEntity?
    @Query("SELECT * FROM place WHERE id = :id") suspend fun place(id: String): PlaceEntity?
    @Query("SELECT * FROM tag WHERE id = :id") suspend fun tag(id: String): TagEntity?
    @Query("SELECT * FROM emotion WHERE name = :name LIMIT 1") suspend fun emotionNamed(name: String): EmotionEntity?
    @Query("SELECT * FROM tag WHERE normalized_name = :name LIMIT 1") suspend fun tagNamed(name: String): TagEntity?
    @Upsert suspend fun upsertEmotion(entity: EmotionEntity)
    @Upsert suspend fun upsertPerson(entity: PersonEntity)
    @Upsert suspend fun upsertPlace(entity: PlaceEntity)
    @Upsert suspend fun upsertTag(entity: TagEntity)
    @Query("DELETE FROM emotion WHERE id = :id") suspend fun deleteEmotion(id: String)
    @Query("DELETE FROM person WHERE id = :id") suspend fun deletePerson(id: String)
    @Query("DELETE FROM place WHERE id = :id") suspend fun deletePlace(id: String)
    @Query("DELETE FROM tag WHERE id = :id") suspend fun deleteTag(id: String)
}
