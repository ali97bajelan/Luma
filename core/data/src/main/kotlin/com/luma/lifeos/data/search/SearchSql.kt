package com.luma.lifeos.data.search

import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.domain.search.SearchQuery
import java.time.ZoneId

/** Only fixed SQL fragments are concatenated; every input is a bind argument. */
internal class SearchSql(query: SearchQuery, zone: ZoneId) {
    val arguments = mutableListOf<Any>()
    val where: String
    init {
        val predicates = mutableListOf("""(type != 'APP_USAGE' OR NOT EXISTS (
            SELECT 1 FROM app_usage_record WHERE life_event_id = life_event.id AND duration_ms = 0))""")
        val bounds = query.bounds(zone)
        bounds.start?.let { predicates += "occurred_at >= ?"; arguments += it.storageBoundary() }
        bounds.endExclusive?.let { predicates += "occurred_at < ?"; arguments += it.storageBoundary() }
        query.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
            val pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            // List subqueries match small catalogs once, then traverse reverse-link indexes.
            predicates += """(title LIKE ? ESCAPE '\' OR id IN (
                SELECT life_event_id FROM journal_entry WHERE title LIKE ? ESCAPE '\') OR id IN (
                SELECT event_id FROM event_tag WHERE tag_id IN (SELECT id FROM tag WHERE name LIKE ? ESCAPE '\')) OR id IN (
                SELECT event_id FROM event_person WHERE person_id IN (SELECT id FROM person WHERE name LIKE ? ESCAPE '\')) OR id IN (
                SELECT event_id FROM event_place WHERE place_id IN (SELECT id FROM place WHERE name LIKE ? ESCAPE '\')))"""
            repeat(5) { arguments += pattern }
        }
        fun selection(table: String, column: String, ids: List<String>) {
            if (ids.isEmpty()) return
            predicates += "id IN (SELECT event_id FROM $table WHERE $column IN (${ids.joinToString(",") { "?" }}))"
            arguments.addAll(ids)
        }
        selection("event_tag", "tag_id", query.tagIds.map { it.toString() }.sorted())
        selection("event_person", "person_id", query.personIds.map { it.toString() }.sorted())
        selection("event_place", "place_id", listOfNotNull(query.placeId?.toString()))
        where = predicates.joinToString(" AND ")
    }
    val countSql get() = "SELECT COUNT(*) FROM life_event WHERE $where"
    val pageSql get() = "SELECT * FROM life_event WHERE $where ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?"
}
