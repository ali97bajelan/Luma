package com.luma.lifeos.data.entity

import androidx.room.*

data class ReviewEnvelope(val id: String, @ColumnInfo(name = "occurred_at") val occurredAt: Long)
data class ReviewCatalogName(val id: String, val name: String)
data class ReviewMoodValues(val id: String, @ColumnInfo(name = "mood_score") val score: Int, val energy: Int?, val stress: Int?)
data class ReviewMoodSource(
    @Embedded val values: ReviewMoodValues,
    @Relation(parentColumn = "id", entityColumn = "id", entity = EmotionEntity::class, projection = ["id", "name"],
        associateBy = Junction(MoodEmotionEntity::class, parentColumn = "mood_id", entityColumn = "emotion_id"))
    val emotions: List<ReviewCatalogName>,
)
/** Indexed relation projections, without notes, bodies, media or catalog private fields. */
data class ReviewSource(
    @Embedded val event: ReviewEnvelope,
    @Relation(parentColumn = "id", entityColumn = "life_event_id", entity = MoodEntryEntity::class, projection = ["id", "mood_score", "energy", "stress"])
    val mood: ReviewMoodSource?,
    @Relation(parentColumn = "id", entityColumn = "life_event_id", entity = JournalEntryEntity::class, projection = ["id"])
    val journals: List<String>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id", entity = MemoryEntity::class, projection = ["id"])
    val memories: List<String>,
    @Relation(parentColumn = "id", entityColumn = "id", entity = PersonEntity::class, projection = ["id", "name"],
        associateBy = Junction(EventPersonEntity::class, parentColumn = "event_id", entityColumn = "person_id"))
    val people: List<ReviewCatalogName>,
    @Relation(parentColumn = "id", entityColumn = "id", entity = PlaceEntity::class, projection = ["id", "name"],
        associateBy = Junction(EventPlaceEntity::class, parentColumn = "event_id", entityColumn = "place_id"))
    val places: List<ReviewCatalogName>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id") val activities: List<ActivityRecordEntity>,
)
data class NativeUsageSource(@Embedded val usage: AppUsageRecordEntity, @ColumnInfo(name = "metadata_json") val metadataJson: String)
