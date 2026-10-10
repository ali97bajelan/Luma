package com.luma.lifeos.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import java.nio.file.Paths
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Storage timestamps are UTC epoch milliseconds; identifiers are stable text UUIDs. */
@Entity(
    tableName = "life_event",
    indices = [
        Index(value = ["occurred_at", "id"]),
        Index(value = ["type", "occurred_at"]),
        Index(value = ["source", "source_ref"], unique = true),
    ],
)
data class LifeEventEntity(
    @PrimaryKey val id: String,
    val type: EventType,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val source: EventSource = EventSource.MANUAL,
    @ColumnInfo(name = "source_ref") val sourceReference: String? = null,
    val title: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "metadata_json", defaultValue = "'{}'") val metadataJson: String = "{}",
)

@Entity(
    tableName = "mood_entry",
    foreignKeys = [ForeignKey(
        entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["life_event_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["life_event_id"], unique = true)],
)
data class MoodEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "life_event_id") val lifeEventId: String,
    @ColumnInfo(name = "mood_score") val moodScore: Int,
    val energy: Int? = null,
    val stress: Int? = null,
    val note: String? = null,
) {
    init {
        require(moodScore in 1..5)
        require(energy == null || energy in 1..5)
        require(stress == null || stress in 1..5)
    }
}

@Entity(tableName = "emotion", indices = [Index(value = ["name"], unique = true)])
data class EmotionEntity(@PrimaryKey val id: String, val name: String) {
    init { require(name.isNotBlank()) }
}

@Entity(
    tableName = "mood_emotion", primaryKeys = ["mood_id", "emotion_id"],
    foreignKeys = [
        ForeignKey(entity = MoodEntryEntity::class, parentColumns = ["id"], childColumns = ["mood_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EmotionEntity::class, parentColumns = ["id"], childColumns = ["emotion_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["emotion_id"])],
)
data class MoodEmotionEntity(
    @ColumnInfo(name = "mood_id") val moodId: String,
    @ColumnInfo(name = "emotion_id") val emotionId: String,
)

@Entity(
    tableName = "journal_entry",
    foreignKeys = [ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["life_event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["life_event_id"], unique = true)],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "life_event_id") val lifeEventId: String,
    val body: String,
    val title: String? = null,
) {
    init { require(body.isNotBlank()) }
}

/** Promotion adds this row to an existing event; it never copies its journal text. */
@Entity(
    tableName = "memory",
    foreignKeys = [ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["life_event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["life_event_id"], unique = true)],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "life_event_id") val lifeEventId: String,
    val importance: Int? = null,
    val note: String? = null,
) {
    init { require(importance == null || importance in 1..5) }
}

@Entity(tableName = "person", indices = [Index(value = ["name"])])
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String? = null,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
) {
    init { require(name.isNotBlank()) }
}

@Entity(
    tableName = "event_person", primaryKeys = ["event_id", "person_id"],
    foreignKeys = [
        ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["person_id"])],
)
data class EventPersonEntity(
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "person_id") val personId: String,
)

@Entity(tableName = "place", indices = [Index(value = ["name"])])
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    init {
        require(name.isNotBlank())
        require((latitude == null) == (longitude == null))
        require(latitude == null || latitude in -90.0..90.0)
        require(longitude == null || longitude in -180.0..180.0)
    }
}

@Entity(
    tableName = "event_place",
    foreignKeys = [
        ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["place_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["place_id"])],
)
data class EventPlaceEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "place_id") val placeId: String,
)

@Entity(
    tableName = "media_attachment",
    foreignKeys = [ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["life_event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["life_event_id"]), Index(value = ["relative_path"], unique = true)],
)
data class MediaAttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "life_event_id") val lifeEventId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val type: String = "image",
    @ColumnInfo(name = "metadata_json", defaultValue = "'{}'") val metadataJson: String = "{}",
) {
    init {
        require(type == "image")
        val path = Paths.get(relativePath)
        require(relativePath.isNotBlank() && !path.isAbsolute)
        require(path.none { it.toString() == ".." || it.toString() == "." })
        require(path.normalize().toString() == relativePath)
    }
}

@Entity(tableName = "tag", indices = [Index(value = ["normalized_name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String = normalize(name),
) {
    init {
        require(name.isNotBlank())
        require(normalizedName == normalize(name))
    }

    companion object {
        fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}

@Entity(
    tableName = "event_tag", primaryKeys = ["event_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class EventTagEntity(
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(
    tableName = "activity_record",
    foreignKeys = [ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["life_event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["life_event_id"]), Index(value = ["source", "source_ref"], unique = true)],
)
data class ActivityRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "life_event_id") val lifeEventId: String,
    @ColumnInfo(name = "activity_type") val activityType: String,
    val value: Double,
    val unit: String,
    val source: EventSource,
    @ColumnInfo(name = "source_ref") val sourceReference: String? = null,
) {
    init {
        require(activityType.isNotBlank() && unit.isNotBlank())
        require(value.isFinite() && value >= 0)
        require((source == EventSource.MANUAL) == (sourceReference == null))
        require(sourceReference == null || sourceReference.isNotBlank())
    }
}

@Entity(
    tableName = "app_usage_record",
    foreignKeys = [ForeignKey(entity = LifeEventEntity::class, parentColumns = ["id"], childColumns = ["life_event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["life_event_id"], unique = true),
        Index(value = ["source", "package_name", "bucket_start", "bucket_end"], unique = true),
        Index(value = ["bucket_start", "bucket_end"]),
    ],
)
data class AppUsageRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "life_event_id") val lifeEventId: String,
    @ColumnInfo(name = "bucket_start") val bucketStart: Long,
    @ColumnInfo(name = "bucket_end") val bucketEnd: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val source: EventSource = EventSource.USAGE_STATS,
) {
    init {
        require(bucketEnd > bucketStart)
        require(durationMs >= 0 && durationMs <= Math.subtractExact(bucketEnd, bucketStart))
        require(packageName.isNotBlank() && source == EventSource.USAGE_STATS)
    }
}

enum class SummaryFreshness { FRESH, STALE }
enum class IntegrationStatus { NOT_CONFIGURED, READY, PERMISSION_DENIED, UNAVAILABLE, ERROR }

@Entity(tableName = "daily_summary", primaryKeys = ["date", "zone_id"], indices = [Index(value = ["freshness"])])
data class DailySummaryEntity(
    val date: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "metrics_json") val metricsJson: String,
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: Int,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
    val freshness: SummaryFreshness = SummaryFreshness.FRESH,
) {
    init {
        LocalDate.parse(date)
        ZoneId.of(zoneId)
        require(algorithmVersion > 0)
    }
}

@Entity(tableName = "integration_state")
data class IntegrationStateEntity(
    @PrimaryKey val source: EventSource,
    val status: IntegrationStatus = IntegrationStatus.NOT_CONFIGURED,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    val checkpoint: String? = null,
) {
    init { require(source != EventSource.MANUAL) }
}
