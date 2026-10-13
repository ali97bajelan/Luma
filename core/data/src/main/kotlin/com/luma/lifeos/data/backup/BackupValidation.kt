package com.luma.lifeos.data.backup

import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.mapping.toDomain
import com.luma.lifeos.domain.model.*
import org.json.JSONObject
import java.util.UUID
import java.time.Instant
import com.luma.lifeos.domain.usage.UsageBucket
import com.luma.lifeos.domain.usage.UsageSession

/** Checks entity/domain invariants which SQLite's generated schema does not enforce. */
internal fun validateBackupRow(table: String, row: Map<String, Any?>) {
    fun s(name: String) = row[name] as String
    fun text(name: String) = row[name] as String?
    fun l(name: String) = row[name] as Long
    fun long(name: String) = row[name] as Long?
    fun int(name: String): Int { val n = l(name); require(n in Int.MIN_VALUE..Int.MAX_VALUE); return n.toInt() }
    fun optionalInt(name: String) = long(name)?.let { require(it in Int.MIN_VALUE..Int.MAX_VALUE); it.toInt() }
    fun number(name: String) = row[name] as Double
    fun source() = EventSource.valueOf(s("source"))
    row.forEach { (name, value) ->
        if (value != null && (name == "id" || (name.endsWith("_id") && name != "zone_id")))
            require(UUID.fromString(value as String).toString() == value) { "Invalid record identity" }
        if (value != null && name.endsWith("_json")) { requireSafeJsonDepth(value as String); JSONObject(value) }
    }
    when (table) {
        "life_event" -> LifeEventEntity(s("id"), EventType.valueOf(s("type")), l("occurred_at"), l("created_at"), l("updated_at"),
            long("ended_at"), source(), text("source_ref"), text("title"), text("note"), s("metadata_json")).toDomain()
        "mood_entry" -> MoodEntryEntity(s("id"), s("life_event_id"), int("mood_score"), optionalInt("energy"), optionalInt("stress"), text("note"))
        "emotion" -> EmotionEntity(s("id"), s("name"))
        "journal_entry" -> JournalEntryEntity(s("id"), s("life_event_id"), s("body"), text("title"))
        "memory" -> MemoryEntity(s("id"), s("life_event_id"), optionalInt("importance"), text("note"))
        "person" -> PersonEntity(s("id"), s("name"), text("note"), text("photo_uri"))
        "place" -> PlaceEntity(s("id"), s("name"), text("note"), row["latitude"] as Double?, row["longitude"] as Double?)
        "tag" -> TagEntity(s("id"), s("name"), s("normalized_name"))
        "media_attachment" -> {
            MediaAttachmentEntity(s("id"), s("life_event_id"), s("relative_path"), s("type"), s("metadata_json"))
            val path = s("relative_path")
            val fileId = path.removePrefix("photos/").removeSuffix(".img")
            require(path == "photos/$fileId.img" && UUID.fromString(fileId).toString() == fileId) { "Unsupported photo path" }
        }
        "activity_record" -> ActivityRecordEntity(s("id"), s("life_event_id"), s("activity_type"), number("value"), s("unit"), source(), text("source_ref"))
        "app_usage_record" -> {
            AppUsageRecordEntity(s("id"), s("life_event_id"), l("bucket_start"), l("bucket_end"), s("package_name"), l("duration_ms"), source())
            UsageBucket(s("package_name"), Instant.ofEpochMilli(l("bucket_start")), Instant.ofEpochMilli(l("bucket_end")), l("duration_ms"))
        }
        "daily_summary" -> DailySummaryEntity(s("date"), s("zone_id"), s("metrics_json"), int("algorithm_version"), l("computed_at"), SummaryFreshness.valueOf(s("freshness")))
        "integration_state" -> IntegrationStateEntity(source(), IntegrationStatus.valueOf(s("status")), long("last_sync_at"), text("checkpoint"))
        "usage_session" -> {
            require(l("ongoing") in 0..1)
            UsageSession(s("package_name"), Instant.ofEpochMilli(l("started_at")), Instant.ofEpochMilli(l("ended_at")), text("app_label"), l("ongoing") == 1L)
        }
        "usage_session_window" -> { require(l("query_end") > l("query_start")); require(l("complete") in 0..1) }
    }
}

/** Protect Android's recursive JSON parser before it receives untrusted text. */
internal fun requireSafeJsonDepth(text: String, maxDepth: Int = 64) {
    var quoted = false; var escaped = false; var depth = 0
    for (character in text) {
        if (quoted) {
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') quoted = false
        } else when (character) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= maxDepth) { "Backup JSON is too deeply nested" } }
            '}', ']' -> { depth--; require(depth >= 0) { "Invalid backup JSON" } }
        }
    }
    require(!quoted && depth == 0) { "Incomplete backup JSON" }
}
