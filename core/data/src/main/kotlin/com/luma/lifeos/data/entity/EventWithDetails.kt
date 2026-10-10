package com.luma.lifeos.data.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class MoodWithEmotions(
    @Embedded val entry: MoodEntryEntity,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(MoodEmotionEntity::class, parentColumn = "mood_id", entityColumn = "emotion_id"),
    )
    val emotions: List<EmotionEntity>,
)

/** Room batches indexed relation queries; DAO reads wrap them in a single transaction. */
data class EventWithDetails(
    @Embedded val event: LifeEventEntity,
    @Relation(parentColumn = "id", entityColumn = "life_event_id", entity = MoodEntryEntity::class)
    val mood: MoodWithEmotions?,
    @Relation(parentColumn = "id", entityColumn = "life_event_id")
    val journal: JournalEntryEntity?,
    @Relation(parentColumn = "id", entityColumn = "life_event_id")
    val memory: MemoryEntity?,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(EventPersonEntity::class, parentColumn = "event_id", entityColumn = "person_id"),
    )
    val people: List<PersonEntity>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(EventPlaceEntity::class, parentColumn = "event_id", entityColumn = "place_id"),
    )
    val places: List<PlaceEntity>,
    @Relation(
        parentColumn = "id", entityColumn = "id",
        associateBy = Junction(EventTagEntity::class, parentColumn = "event_id", entityColumn = "tag_id"),
    )
    val tags: List<TagEntity>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id")
    val attachments: List<MediaAttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id")
    val activities: List<ActivityRecordEntity>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id")
    val appUsage: AppUsageRecordEntity?,
)
