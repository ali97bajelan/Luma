package com.luma.lifeos.data.widget

import com.luma.lifeos.domain.widget.WidgetActionResult
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

internal sealed interface WidgetCommandPayload {
    val action: String
    fun encode(): String
    fun fingerprint(): String

    data class HabitLog(
        val habitId: UUID,
        val date: LocalDate,
        val zone: ZoneId,
        val scheduleId: UUID,
        val effectiveFrom: LocalDate,
        val target: Int,
        val weekdayMask: Int,
        val scheduleState: String,
        val skipRemaining: Boolean,
        val expectedCount: Int,
        val logId: UUID,
    ) : WidgetCommandPayload {
        override val action = HABIT_LOG
        override fun encode() = base(action)
            .put("habitId", habitId.toString())
            .put("date", date.toString())
            .put("zone", zone.id)
            .put("scheduleId", scheduleId.toString())
            .put("effectiveFrom", effectiveFrom.toString())
            .put("target", target)
            .put("weekdayMask", weekdayMask)
            .put("scheduleState", scheduleState)
            .put("skipRemaining", skipRemaining)
            .put("expectedCount", expectedCount)
            .put("logId", logId.toString())
            .toString()

        override fun fingerprint() = fingerprintOf(
            action, habitId, date, zone.id, scheduleId, effectiveFrom, target,
            weekdayMask, scheduleState, skipRemaining, expectedCount,
        )
    }

    data class TaskComplete(
        val taskId: UUID,
        val date: LocalDate,
        val expectedRevision: UUID,
        val completedRevision: UUID,
    ) : WidgetCommandPayload {
        override val action = TASK_COMPLETE
        override fun encode() = base(action)
            .put("taskId", taskId.toString())
            .put("date", date.toString())
            .put("expectedRevision", expectedRevision.toString())
            .put("completedRevision", completedRevision.toString())
            .toString()

        override fun fingerprint() = fingerprintOf(action, taskId, date, expectedRevision)
    }

    data class HabitUndo(
        val logId: UUID,
        val habitId: UUID,
        val date: LocalDate,
        val zone: ZoneId,
        val occurredAtMillis: Long?,
        val recordedAtMillis: Long,
        val updatedAtMillis: Long,
    ) : WidgetCommandPayload {
        override val action = HABIT_UNDO
        override fun encode() = base(action)
            .put("logId", logId.toString())
            .put("habitId", habitId.toString())
            .put("date", date.toString())
            .put("zone", zone.id)
            .putNullableLong("occurredAtMillis", occurredAtMillis)
            .put("recordedAtMillis", recordedAtMillis)
            .put("updatedAtMillis", updatedAtMillis)
            .toString()

        override fun fingerprint() = fingerprintOf(
            action, logId, habitId, date, zone.id, occurredAtMillis, recordedAtMillis, updatedAtMillis,
        )
    }

    data class TaskUndo(
        val taskId: UUID,
        val completedRevision: UUID,
        val reopenedRevision: UUID,
    ) : WidgetCommandPayload {
        override val action = TASK_UNDO
        override fun encode() = base(action)
            .put("taskId", taskId.toString())
            .put("completedRevision", completedRevision.toString())
            .put("reopenedRevision", reopenedRevision.toString())
            .toString()

        override fun fingerprint() = fingerprintOf(action, taskId, completedRevision, reopenedRevision)
    }

    companion object {
        const val HABIT_LOG = "HABIT_LOG"
        const val TASK_COMPLETE = "TASK_COMPLETE"
        const val HABIT_UNDO = "HABIT_UNDO"
        const val TASK_UNDO = "TASK_UNDO"
        private const val VERSION = 1

        fun decode(action: String, value: String): WidgetCommandPayload? = runCatching {
            val json = JSONObject(value)
            require(json.getInt("version") == VERSION)
            require(json.getString("action") == action)
            when (action) {
                HABIT_LOG -> HabitLog(
                    habitId = json.uuid("habitId"),
                    date = json.date("date"),
                    zone = json.zone("zone"),
                    scheduleId = json.uuid("scheduleId"),
                    effectiveFrom = json.date("effectiveFrom"),
                    target = json.getInt("target").also { require(it in 1..999) },
                    weekdayMask = json.getInt("weekdayMask").also { require(it in 1..127) },
                    scheduleState = json.getString("scheduleState").also { require(it in setOf("ACTIVE", "PAUSED", "ARCHIVED")) },
                    skipRemaining = json.getBoolean("skipRemaining"),
                    expectedCount = json.getInt("expectedCount").also { require(it >= 0) },
                    logId = json.uuid("logId"),
                )
                TASK_COMPLETE -> TaskComplete(
                    taskId = json.uuid("taskId"),
                    date = json.date("date"),
                    expectedRevision = json.uuid("expectedRevision"),
                    completedRevision = json.uuid("completedRevision"),
                )
                HABIT_UNDO -> HabitUndo(
                    logId = json.uuid("logId"),
                    habitId = json.uuid("habitId"),
                    date = json.date("date"),
                    zone = json.zone("zone"),
                    occurredAtMillis = json.nullableLong("occurredAtMillis"),
                    recordedAtMillis = json.getLong("recordedAtMillis"),
                    updatedAtMillis = json.getLong("updatedAtMillis"),
                )
                TASK_UNDO -> TaskUndo(
                    taskId = json.uuid("taskId"),
                    completedRevision = json.uuid("completedRevision"),
                    reopenedRevision = json.uuid("reopenedRevision"),
                )
                else -> null
            }
        }.getOrNull()

        private fun base(action: String) = JSONObject()
            .put("version", VERSION)
            .put("action", action)

        private fun fingerprintOf(action: String, vararg values: Any?): String {
            val source = buildString {
                append(action)
                values.forEach { value ->
                    append('\u0000')
                    append(value?.toString() ?: "<null>")
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
            return buildString(digest.size * 2) {
                digest.forEach { byte -> append("%02x".format(byte)) }
            }
        }

        private fun JSONObject.uuid(name: String): UUID = UUID.fromString(getString(name)).also {
            require(it.toString() == getString(name))
        }

        private fun JSONObject.date(name: String): LocalDate = LocalDate.parse(getString(name)).also {
            require(it.toString() == getString(name))
        }

        private fun JSONObject.zone(name: String): ZoneId = ZoneId.of(getString(name)).also {
            require(it.id == getString(name))
        }

        private fun JSONObject.nullableLong(name: String): Long? =
            if (!has(name) || isNull(name)) null else getLong(name)

        private fun JSONObject.putNullableLong(name: String, value: Long?): JSONObject =
            put(name, value ?: JSONObject.NULL)
    }
}

internal fun WidgetCommandEntity.resultOrInvalid(): WidgetActionResult =
    outcomeJson?.let(::decodeWidgetActionResult)
        ?: WidgetActionResult(com.luma.lifeos.domain.widget.WidgetOutcome.INVALID)

internal fun encodeWidgetActionResult(result: com.luma.lifeos.domain.widget.WidgetActionResult): String =
    JSONObject()
        .put("outcome", result.outcome.name)
        .put("retryToken", result.retryToken?.toString() ?: JSONObject.NULL)
        .put("undoToken", result.undoToken?.toString() ?: JSONObject.NULL)
        .toString()

internal fun decodeWidgetActionResult(value: String): com.luma.lifeos.domain.widget.WidgetActionResult? =
    runCatching {
        val json = JSONObject(value)
        fun uuid(name: String) = if (json.isNull(name)) null else UUID.fromString(json.getString(name))
        com.luma.lifeos.domain.widget.WidgetActionResult(
            outcome = com.luma.lifeos.domain.widget.WidgetOutcome.valueOf(json.getString("outcome")),
            retryToken = uuid("retryToken"),
            undoToken = uuid("undoToken"),
        )
    }.getOrNull()
