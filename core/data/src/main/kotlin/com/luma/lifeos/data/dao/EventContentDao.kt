package com.luma.lifeos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.luma.lifeos.data.entity.*

@Dao
interface EventContentDao {
    @Insert suspend fun insertMood(entry: MoodEntryEntity)
    @Insert suspend fun insertJournal(entry: JournalEntryEntity)
    @Insert suspend fun insertMemory(entry: MemoryEntity)
    @Insert suspend fun insertActivities(entries: List<ActivityRecordEntity>)
    @Insert suspend fun insertAppUsage(entry: AppUsageRecordEntity)
    @Insert suspend fun insertAttachments(entries: List<MediaAttachmentEntity>)
    @Insert suspend fun insertEmotions(links: List<MoodEmotionEntity>)
    @Insert suspend fun insertPeople(links: List<EventPersonEntity>)
    @Insert suspend fun insertTags(links: List<EventTagEntity>)
    @Insert suspend fun insertPlace(link: EventPlaceEntity)
    @Upsert suspend fun upsertMemory(entry: MemoryEntity)
    @Query("SELECT * FROM memory WHERE id = :id") suspend fun memoryById(id: String): MemoryEntity?

    @Query("SELECT * FROM media_attachment WHERE life_event_id = :eventId ORDER BY id")
    suspend fun attachments(eventId: String): List<MediaAttachmentEntity>
    @Query("SELECT relative_path FROM media_attachment ORDER BY relative_path")
    suspend fun referencedMediaPaths(): List<String>
    @Query("DELETE FROM mood_entry WHERE life_event_id = :eventId") suspend fun deleteMood(eventId: String)
    @Query("DELETE FROM journal_entry WHERE life_event_id = :eventId") suspend fun deleteJournal(eventId: String)
    @Query("DELETE FROM memory WHERE life_event_id = :eventId") suspend fun deleteMemory(eventId: String)
    @Query("DELETE FROM activity_record WHERE life_event_id = :eventId") suspend fun deleteActivities(eventId: String)
    @Query("DELETE FROM app_usage_record WHERE life_event_id = :eventId") suspend fun deleteAppUsage(eventId: String)
    @Query("DELETE FROM media_attachment WHERE life_event_id = :eventId") suspend fun deleteAttachments(eventId: String)
    @Query("DELETE FROM event_person WHERE event_id = :eventId") suspend fun deletePeople(eventId: String)
    @Query("DELETE FROM event_tag WHERE event_id = :eventId") suspend fun deleteTags(eventId: String)
    @Query("DELETE FROM event_place WHERE event_id = :eventId") suspend fun deletePlace(eventId: String)
}
