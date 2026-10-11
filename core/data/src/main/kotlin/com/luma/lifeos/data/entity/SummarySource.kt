package com.luma.lifeos.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation

data class SummaryEnvelope(val id: String, @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "metadata_json") val metadataJson: String)
data class SummaryMood(@ColumnInfo(name = "mood_score") val score: Int, val energy: Int?, val stress: Int?)

/** Metric-only projection: no bodies, photos, people, tags or emotion labels. */
data class SummarySource(
    @Embedded val event: SummaryEnvelope,
    @Relation(parentColumn = "id", entityColumn = "life_event_id", entity = MoodEntryEntity::class,
        projection = ["mood_score", "energy", "stress"]) val mood: SummaryMood?,
    @Relation(parentColumn = "id", entityColumn = "life_event_id", entity = JournalEntryEntity::class,
        projection = ["id"]) val journals: List<String>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id", entity = MemoryEntity::class,
        projection = ["id"]) val memories: List<String>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id") val activities: List<ActivityRecordEntity>,
    @Relation(parentColumn = "id", entityColumn = "life_event_id") val usage: AppUsageRecordEntity?,
)
