package com.luma.lifeos.data.backup

import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.domain.model.Tag
import java.util.UUID

internal const val FOCUS_SESSION_EXPORT_QUERY =
    """SELECT * FROM focus_session WHERE state IN ('FINISHED', 'INTERRUPTED') AND open_slot IS NULL"""
internal const val FOCUS_SESSION_EXPORT_EXCLUDING_QUERY =
    """SELECT * FROM focus_session WHERE state IN ('FINISHED', 'INTERRUPTED') AND open_slot IS NULL AND id != ?"""

/** Adapts legacy focus rows and validates their portable metadata graph. */
internal object FocusMetadataArchiveAdapter {
    internal data class TagRow(val id: String, val name: String, val normalizedName: String)

    fun focusSessionExportQuery(excludeSessionId: Boolean): String =
        if (excludeSessionId) FOCUS_SESSION_EXPORT_EXCLUDING_QUERY else FOCUS_SESSION_EXPORT_QUERY

    fun importColumns(table: String, schemaVersion: Int, currentColumns: List<String>): List<String> = when {
        table == "todo_task" && schemaVersion == 4 -> currentColumns.filterNot {
            it in setOf("due_time_minutes", "importance_code")
        }
        table == "todo_task" && schemaVersion in 5..12 -> currentColumns.filterNot { it == "importance_code" }
        table == "focus_session" && schemaVersion == 7 -> currentColumns.filterNot {
            it in setOf("name", "tag_id", "timer_mode", "target_millis", "outcome", "next_step", "task_id", "task_origin")
        }
        table == "focus_session" && schemaVersion == 8 -> currentColumns.filterNot {
            it in setOf("timer_mode", "target_millis", "outcome", "next_step", "task_id", "task_origin")
        }
        table == "focus_session" && schemaVersion in 9..10 -> currentColumns.filterNot {
            it in setOf("outcome", "next_step", "task_id", "task_origin")
        }
        table == "focus_session" && schemaVersion == 11 -> currentColumns.filterNot {
            it in setOf("task_id", "task_origin")
        }
        else -> currentColumns
    }

    /** Older focus rows gain metadata, timer and reflection defaults in a current database. */
    fun insertColumns(
        table: String,
        schemaVersion: Int,
        sourceColumns: List<String>,
        currentColumns: List<String>,
    ): List<String> = if (table == "focus_session" && schemaVersion in 7..11) currentColumns else sourceColumns

    fun adaptRow(
        table: String,
        schemaVersion: Int,
        sourceColumns: List<String>,
        sourceValues: List<Any?>,
        insertColumns: List<String>,
    ): List<Any?> {
        require(sourceColumns.size == sourceValues.size)
        if (table != "focus_session" || schemaVersion !in 7..11) return sourceValues
        val source = sourceColumns.zip(sourceValues).toMap()
        val values = source + mapOf(
            "name" to source["name"],
            "tag_id" to source["tag_id"],
            "timer_mode" to (source["timer_mode"] ?: "COUNT_UP"),
            "target_millis" to source["target_millis"],
            "outcome" to source["outcome"],
            "next_step" to source["next_step"],
            "task_id" to source["task_id"],
            "task_origin" to (source["task_origin"] ?: 0L),
        )
        return insertColumns.map { column ->
            require(column in values) { "Unsupported legacy focus session column" }
            values[column]
        }
    }

    fun validateSession(
        state: String,
        openSlot: Long?,
        name: String?,
        taskId: String?,
        taskOrigin: Long,
    ) {
        require(state == "FINISHED" || state == "INTERRUPTED") {
            "Only terminal focus sessions can be restored"
        }
        require(openSlot == null) { "An open focus session cannot be restored" }
        name?.let { validateLabel(it, isTag = false) }
        require(taskOrigin in 0L..1L) { "Invalid task-origin marker" }
        require(taskId == null || taskOrigin == 1L) { "A task-linked session must retain task origin" }
    }

    fun validateTaskReferences(taskIds: Set<String>, links: Iterable<Pair<String?, Boolean>>) {
        links.forEach { (taskId, taskOrigin) ->
            require(taskId == null || taskId in taskIds) { "A focus session references a missing task" }
            require(taskId == null || taskOrigin) { "A task-linked session must retain task origin" }
        }
    }

    fun validateTag(tag: TagRow) {
        require(UUID.fromString(tag.id).toString() == tag.id) { "Invalid focus tag identity" }
        validateLabel(tag.name, isTag = true)
        require(Tag.normalize(tag.name) == tag.normalizedName) { "Focus tag identity is not normalized" }
    }

    fun validateReferences(tags: List<TagRow>, sessionTagIds: List<String?>) {
        tags.forEach(::validateTag)
        require(tags.map(TagRow::id).distinct().size == tags.size) { "Duplicate focus tag identity" }
        require(tags.map(TagRow::normalizedName).distinct().size == tags.size) { "Duplicate normalized focus tag" }

        val ids = tags.mapTo(mutableSetOf(), TagRow::id)
        val referencedIds = sessionTagIds.filterNotNull().toSet()
        require(referencedIds.all { it in ids }) { "A focus session references a missing tag" }
        require(ids.all { it in referencedIds }) { "Backup contains an unused focus tag" }
    }

    fun exportedTagIds(sessionTagIds: Iterable<String?>): Set<String> =
        sessionTagIds.filterNotNull().toSortedSet()

    /** Focus tag rows store their UUID in the first column, matching Room's entity order. */
    fun referencedTagRows(rows: Sequence<List<Any?>>, exportedTagIds: Set<String>): Sequence<List<Any?>> =
        rows.filter { row -> (row.firstOrNull() as? String) in exportedTagIds }

    private fun validateLabel(value: String, isTag: Boolean) {
        val input = if (isTag) FocusStartInput("Focus session", value) else FocusStartInput(value, null)
        val normalized = normalizeFocusStartInput(input)
        val actual = if (isTag) normalized.tagName else normalized.name
        require(actual == value) { "Focus labels must already be trimmed" }
    }
}
