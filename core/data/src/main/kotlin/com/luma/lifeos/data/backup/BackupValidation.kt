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
import com.luma.lifeos.data.todo.TaskReminderEntity
import com.luma.lifeos.data.todo.toDomain
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.weekdaysFromMask
import com.luma.lifeos.domain.focus.FocusRun
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusOutcome
import com.luma.lifeos.domain.focus.FocusReflection
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimerConfig
import com.luma.lifeos.domain.focus.FocusTimerMode
import com.luma.lifeos.domain.focus.FocusGoalVersion
import com.luma.lifeos.domain.focus.FocusGoalState
import java.time.DayOfWeek
import androidx.sqlite.db.SupportSQLiteDatabase
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
            dueTimeMinutes = optionalInt("due_time_minutes"),
        ).toDomain()
        "task_reminder" -> {
            require(l("enabled") in 0..1 && l("needs_reconcile") in 0..1)
            text("last_delivered_slot")?.let { require(it.isNotBlank()) }
            TaskReminderEntity(
                taskId = s("task_id"), enabled = l("enabled") == 1L, taskRevision = s("task_revision"),
                revisionToken = s("revision_token"), consumedActionToken = text("consumed_action_token"),
                lastDeliveredSlot = text("last_delivered_slot"), snoozeSlot = text("snooze_slot"),
                snoozeUntil = long("snooze_until"), needsReconcile = l("needs_reconcile") == 1L,
            )
        }
        "focus_goal_settings" -> {
            require(int("singleton_id") == 1)
            require(l("enabled") in 0..1 && l("settings_revision") >= 0)
            val monday = text("visible_from_monday")
            val zoneId = text("visible_from_zone_id")
            require((monday == null) == (zoneId == null))
            if (l("enabled") == 1L) require(monday != null)
            monday?.let {
                val date = LocalDate.parse(it)
                require(date.toString() == it && date.dayOfWeek == DayOfWeek.MONDAY)
                zone(requireNotNull(zoneId))
            }
        }
        "focus_goal_version" -> {
            require(int("settings_id") == 1)
            val mask = int("preferred_weekday_mask")
            require(mask in 1..127)
            val days = DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
            FocusGoalVersion(
                id = UUID.fromString(s("id")),
                revision = l("revision"),
                effectiveMonday = date("effective_monday"),
                zoneId = zone("zone_id").id,
                targetMinutes = int("target_minutes"),
                preferredDays = days,
            )
        }
        "focus_tag" -> FocusMetadataArchiveAdapter.validateTag(
            FocusMetadataArchiveAdapter.TagRow(s("id"), s("name"), s("normalized_name")),
        )
        "focus_session" -> {
            val state = FocusState.valueOf(s("state"))
            FocusMetadataArchiveAdapter.validateSession(
                state = state.name,
                openSlot = long("open_slot"),
                name = text("name"),
                taskId = text("task_id"),
                taskOrigin = l("task_origin"),
            )
            require(l("revision") >= 0)
            require(l("recovered_or_uncertain") in 0..1)
            val terminalReason = text("terminal_reason")?.let(FocusTerminalReason::valueOf)
            val timer = FocusTimerConfig(
                mode = FocusTimerMode.valueOf(s("timer_mode")),
                targetMillis = long("target_millis"),
            )
            require(when (state) {
                FocusState.FINISHED -> terminalReason == FocusTerminalReason.USER_STOP ||
                    terminalReason == FocusTerminalReason.TARGET_REACHED
                FocusState.INTERRUPTED -> terminalReason != null && terminalReason !in setOf(
                    FocusTerminalReason.USER_STOP,
                    FocusTerminalReason.TARGET_REACHED,
                )
                else -> false
            }) { "Focus terminal state and reason do not match" }
            require(timer.mode == FocusTimerMode.TIMED || timer.targetMillis == null)
            fun instant(prefix: String): Instant = Instant.ofEpochSecond(
                l("${prefix}_seconds"), int("${prefix}_nanos").toLong().also { require(it in 0..999_999_999) },
            )
            instant("started_at")
            instant("ended_at")
            instant("created_at")
            instant("updated_at")
        }
        "focus_run" -> {
            fun instant(prefix: String): Instant = Instant.ofEpochSecond(
                l("${prefix}_seconds"), int("${prefix}_nanos").toLong().also { require(it in 0..999_999_999) },
            )
            require(text("open_session_id") == null) { "An open focus run cannot be restored" }
            FocusRun(
                id = UUID.fromString(s("id")),
                sessionId = UUID.fromString(s("session_id")),
                ordinal = int("ordinal"),
                startedAt = instant("started_at"),
                endedAt = instant("ended_at"),
                creditedMillis = l("credited_millis"),
                rawStartedAt = instant("raw_started_at"),
                rawEndedAt = instant("raw_ended_at"),
            )
        }
    }
}

/** Validates focus tag references and excludes unused catalog entries from portable archives. */
internal fun validateFocusMetadataArchive(sqlite: SupportSQLiteDatabase) {
    val tags = sqlite.query("SELECT id, name, normalized_name FROM focus_tag ORDER BY id").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(FocusMetadataArchiveAdapter.TagRow(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
            }
        }
    }
    val sessionTagIds = sqlite.query("SELECT tag_id FROM focus_session ORDER BY id").use { cursor ->
        buildList { while (cursor.moveToNext()) add(if (cursor.isNull(0)) null else cursor.getString(0)) }
    }
    FocusMetadataArchiveAdapter.validateReferences(tags, sessionTagIds)

    val taskIds = sqlite.query("SELECT id FROM todo_task ORDER BY id").use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
    val taskLinks = sqlite.query("SELECT task_id, task_origin FROM focus_session ORDER BY id").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add((if (cursor.isNull(0)) null else cursor.getString(0)) to (cursor.getLong(1) == 1L))
            }
        }
    }
    FocusMetadataArchiveAdapter.validateTaskReferences(taskIds, taskLinks)
}

/** Validates relationships and overflow after every focus row has been staged. */
internal fun validateFocusArchive(sqlite: SupportSQLiteDatabase): Long {
    fun instant(cursor: android.database.Cursor, seconds: String, nanos: String): Instant? {
        val secondsIndex = cursor.getColumnIndexOrThrow(seconds)
        val nanosIndex = cursor.getColumnIndexOrThrow(nanos)
        if (cursor.isNull(secondsIndex) || cursor.isNull(nanosIndex)) {
            require(cursor.isNull(secondsIndex) && cursor.isNull(nanosIndex)) { "Incomplete focus timestamp" }
            return null
        }
        return Instant.ofEpochSecond(cursor.getLong(secondsIndex), cursor.getLong(nanosIndex))
    }

    val runsBySession = mutableMapOf<String, MutableList<FocusRun>>()
    var runCount = 0L
    sqlite.query("SELECT * FROM focus_run ORDER BY session_id, ordinal").use { cursor ->
        while (cursor.moveToNext()) {
            val sessionId = cursor.getString(1)
            runsBySession.getOrPut(sessionId) { mutableListOf() } += FocusRun(
                id = UUID.fromString(cursor.getString(0)),
                sessionId = UUID.fromString(sessionId),
                ordinal = cursor.getInt(2),
                startedAt = requireNotNull(instant(cursor, "started_at_seconds", "started_at_nanos")),
                endedAt = instant(cursor, "ended_at_seconds", "ended_at_nanos"),
                creditedMillis = cursor.getLong(7),
                rawStartedAt = requireNotNull(instant(cursor, "raw_started_at_seconds", "raw_started_at_nanos")),
                rawEndedAt = instant(cursor, "raw_ended_at_seconds", "raw_ended_at_nanos"),
            )
            runCount = Math.addExact(runCount, 1L)
        }
    }

    var sessionCount = 0L
    var attachedRunCount = 0L
    sqlite.query("SELECT * FROM focus_session ORDER BY id").use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val runs = requireNotNull(runsBySession.remove(id)) { "A focus session has no runs" }
                .sortedBy(FocusRun::ordinal)
            attachedRunCount = Math.addExact(attachedRunCount, runs.size.toLong())
            val session = FocusSession(
                id = UUID.fromString(id),
                state = FocusState.valueOf(cursor.getString(1)),
                revision = cursor.getLong(2),
                startedAt = requireNotNull(instant(cursor, "started_at_seconds", "started_at_nanos")),
                endedAt = instant(cursor, "ended_at_seconds", "ended_at_nanos"),
                createdAt = requireNotNull(instant(cursor, "created_at_seconds", "created_at_nanos")),
                updatedAt = requireNotNull(instant(cursor, "updated_at_seconds", "updated_at_nanos")),
                terminalReason = if (cursor.isNull(11)) null else FocusTerminalReason.valueOf(cursor.getString(11)),
                recoveredOrUncertain = cursor.getLong(12) == 1L,
                runs = runs,
                timer = FocusTimerConfig(
                    mode = FocusTimerMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("timer_mode"))),
                    targetMillis = cursor.getColumnIndex("target_millis").let { index ->
                        if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
                    },
                ),
                reflection = FocusReflection(
                    outcome = cursor.getColumnIndex("outcome").let { index ->
                        if (index < 0 || cursor.isNull(index)) null else FocusOutcome.valueOf(cursor.getString(index))
                    },
                    nextStep = cursor.getColumnIndex("next_step").let { index ->
                        if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
                    },
                ),
                taskId = cursor.getColumnIndex("task_id").let { index ->
                    if (index < 0 || cursor.isNull(index)) null else UUID.fromString(cursor.getString(index))
                },
                taskOrigin = cursor.getLong(cursor.getColumnIndexOrThrow("task_origin")) == 1L,
            )
            session.creditedMillis // Reject aggregate duration overflow before replacement.
            sessionCount = Math.addExact(sessionCount, 1L)
        }
    }
    require(attachedRunCount == runCount && runsBySession.isEmpty()) { "A focus run has no session" }
    return sessionCount
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


/** Portable goal rows are validated together, including the singleton and enable boundary. */
internal fun validateFocusGoalsArchive(sqlite: SupportSQLiteDatabase, capturedAt: Instant = Instant.now()) {
    val settingsRows = sqlite.query("SELECT * FROM focus_goal_settings ORDER BY singleton_id").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    FocusGoalState(
                        enabled = cursor.getLong(cursor.getColumnIndexOrThrow("enabled")) == 1L,
                        settingsRevision = cursor.getLong(cursor.getColumnIndexOrThrow("settings_revision")),
                        visibleFromMonday = cursor.getColumnIndex("visible_from_monday").let { index ->
                            if (index < 0 || cursor.isNull(index)) null else LocalDate.parse(cursor.getString(index))
                        },
                        visibleFromZoneId = cursor.getColumnIndex("visible_from_zone_id").let { index ->
                            if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
                        },
                        versions = emptyList(),
                    ),
                )
            }
        }
    }
    require(settingsRows.size == 1) { "Focus goal settings must have exactly one row" }
    val versions = sqlite.query("SELECT * FROM focus_goal_version ORDER BY effective_monday, zone_id, id").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val mask = cursor.getInt(cursor.getColumnIndexOrThrow("preferred_weekday_mask"))
                add(
                    FocusGoalVersion(
                        id = UUID.fromString(cursor.getString(cursor.getColumnIndexOrThrow("id"))),
                        revision = cursor.getLong(cursor.getColumnIndexOrThrow("revision")),
                        effectiveMonday = LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow("effective_monday"))),
                        zoneId = cursor.getString(cursor.getColumnIndexOrThrow("zone_id")),
                        targetMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("target_minutes")),
                        preferredDays = DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet(),
                    ),
                )
            }
        }
    }
    require(versions.map { it.id }.distinct().size == versions.size) { "Duplicate focus goal version" }
    val settings = settingsRows.single()
    val portableState = FocusGoalState(
        enabled = settings.enabled,
        settingsRevision = settings.settingsRevision,
        visibleFromMonday = settings.visibleFromMonday,
        visibleFromZoneId = settings.visibleFromZoneId,
        versions = versions,
    )
    if (portableState.enabled) {
        val visibleFrom = requireNotNull(portableState.visibleFromMonday)
            .atStartOfDay(ZoneId.of(requireNotNull(portableState.visibleFromZoneId))).toInstant()
        require(versions.any { version ->
            version.effectiveMonday.atStartOfDay(ZoneId.of(version.zoneId)).toInstant() >= visibleFrom
        }) { "Enabled focus goals need a version after their visible-from boundary" }
    }
    val pendingCount = versions.count { version ->
        version.effectiveMonday.atStartOfDay(ZoneId.of(version.zoneId)).toInstant().isAfter(capturedAt)
    }
    require(pendingCount <= 1) { "Only one focus goal version may be pending" }
    require(portableState.enabled || pendingCount == 0) { "Disabled focus goals cannot retain a pending version" }
}
