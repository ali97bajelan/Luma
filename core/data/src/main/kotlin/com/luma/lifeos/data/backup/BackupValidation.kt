package com.luma.lifeos.data.backup

import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.mapping.toDomain
import com.luma.lifeos.domain.model.*
import org.json.JSONObject
import java.util.UUID
import java.time.Instant
import com.luma.lifeos.domain.usage.UsageBucket
import com.luma.lifeos.domain.usage.UsageSession
import com.luma.lifeos.data.habit.*
import com.luma.lifeos.data.todo.TodoEntity
import com.luma.lifeos.data.todo.toDomain
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.weekdaysFromMask
import java.time.LocalDate
import java.time.ZoneId

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
    fun date(name: String) = LocalDate.parse(s(name)).also { require(it.toString() == s(name)) { "Invalid civil date" } }
    fun zone(name: String) = ZoneId.of(s(name)).also { require(it.id == s(name)) { "Invalid timezone identifier" } }
    row.forEach { (name, value) ->
        if (value != null && (name == "id" || (name.endsWith("_id") && name !in setOf("zone_id", "tracking_zone_id", "setting_id"))))
            require(UUID.fromString(value as String).toString() == value) { "Invalid record identity" }
        if (value != null && name.endsWith("_json")) {
            val json = value as String
            requireSafeJsonDepth(json)
            when {
                table == "habit_reminder" && name == "times_json" -> org.json.JSONArray(json)
                else -> JSONObject(json)
            }
        }
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
        "habit_settings" -> HabitSettingsEntity(int("setting_id"), zone("tracking_zone_id").id)
        "habit" -> HabitEntity(s("id"), s("name"), text("unit_label"), date("start_date").toString(), l("created_at"), l("updated_at"))
        "habit_schedule" -> HabitScheduleEntity(s("id"), s("habit_id"), date("effective_from").toString(),
            int("target"), int("weekday_mask"), s("state")).also { weekdaysFromMask(it.weekdayMask); HabitLifecycle.valueOf(it.state) }
        "habit_log" -> {
            val entity = HabitLogEntity(s("id"), s("habit_id"), date("habit_date").toString(), zone("zone_id").id,
                long("occurred_at"), l("recorded_at"), l("updated_at"))
            HabitLog(UUID.fromString(entity.id), UUID.fromString(entity.habitId), date("habit_date"), zone("zone_id"),
                entity.occurredAt?.let(Instant::ofEpochMilli), Instant.ofEpochMilli(entity.recordedAt), Instant.ofEpochMilli(entity.updatedAt))
        }
        "habit_day_choice" -> {
            require(l("skip_remaining") in 0..1)
            HabitDayChoiceEntity(s("habit_id"), date("habit_date").toString(), l("skip_remaining") == 1L, l("updated_at"))
        }
        "habit_reminder" -> validateHabitReminder(row)
        "habit_reminder_runtime" -> {
            require(l("needs_reconcile") in 0..1)
            text("last_delivered_slot")?.let { require(it.isNotBlank()) }
            HabitReminderRuntimeEntity(s("habit_id"), s("revision_token"), text("last_delivered_slot"),
                text("consumed_action_token"), text("snooze_slot"), long("snooze_until"), l("needs_reconcile") == 1L)
        }
        "todo_task" -> TodoEntity(
            id = s("id"),
            title = s("title"),
            note = text("note"),
            dueDate = text("due_date"),
            completedAt = long("completed_at"),
            createdAt = l("created_at"),
            updatedAt = l("updated_at"),
            revisionToken = s("revision_token"),
        ).toDomain()
    }
}

private fun validateHabitReminder(row: Map<String, Any?>) {
    fun s(name: String) = row[name] as String
    fun nullableInt(name: String) = (row[name] as Long?)?.let { require(it in Int.MIN_VALUE..Int.MAX_VALUE); it.toInt() }
    val enabled = row["enabled"] as Long
    require(enabled in 0..1)
    val timesJson = s("times_json")
    requireSafeJsonDepth(timesJson)
    val times = org.json.JSONArray(timesJson)
    val slots = (0 until times.length()).map { index ->
        val value = times.get(index) as? Number ?: error("Reminder time must be an integer")
        val number = value.toDouble()
        require(number.isFinite() && number == number.toInt().toDouble())
        number.toInt()
    }
    when (s("mode")) {
        "TIMES" -> {
            require(nullableInt("interval_minutes") == null && nullableInt("start_minute") == null && nullableInt("end_minute") == null)
            require(slots.size in 1..24 && slots.all { it in 0..1439 })
            require(slots == slots.distinct().sorted())
        }
        "INTERVAL" -> {
            require(slots.isEmpty())
            val interval = requireNotNull(nullableInt("interval_minutes"))
            val start = requireNotNull(nullableInt("start_minute"))
            val end = requireNotNull(nullableInt("end_minute"))
            require(interval in 15..720 && interval % 15 == 0)
            require(start in 0..1439 && end in 1..1440 && start < end)
        }
        else -> error("Unknown reminder mode")
    }
    HabitReminderEntity(s("habit_id"), enabled == 1L, s("mode"), timesJson, nullableInt("interval_minutes"),
        nullableInt("start_minute"), nullableInt("end_minute"), s("revision_token"))
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
