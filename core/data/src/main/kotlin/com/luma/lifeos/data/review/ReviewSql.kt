package com.luma.lifeos.data.review

import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.domain.review.*
import java.time.ZoneId

/** Metric predicates are shared by count and page; only input values are interpolated as bindings. */
internal class ReviewSql(dates: ReviewDates, zone: ZoneId, metric: ReviewMetric) {
    val arguments = mutableListOf<Any>(dates.startInstant(zone).storageBoundary(), dates.endInstant(zone).storageBoundary())
    private val predicate = when (metric.kind) {
        ReviewMetricKind.ENTRIES -> "1"
        ReviewMetricKind.JOURNALS -> "EXISTS (SELECT 1 FROM journal_entry WHERE life_event_id = life_event.id)"
        ReviewMetricKind.MEMORIES -> "EXISTS (SELECT 1 FROM memory WHERE life_event_id = life_event.id)"
        ReviewMetricKind.MOOD -> "EXISTS (SELECT 1 FROM mood_entry WHERE life_event_id = life_event.id)"
        ReviewMetricKind.ENERGY -> "EXISTS (SELECT 1 FROM mood_entry WHERE life_event_id = life_event.id AND energy IS NOT NULL)"
        ReviewMetricKind.STRESS -> "EXISTS (SELECT 1 FROM mood_entry WHERE life_event_id = life_event.id AND stress IS NOT NULL)"
        ReviewMetricKind.EMOTION -> {
            arguments += requireNotNull(metric.id).toString()
            "EXISTS (SELECT 1 FROM mood_entry INNER JOIN mood_emotion ON mood_id = mood_entry.id WHERE life_event_id = life_event.id AND emotion_id = ?)"
        }
        ReviewMetricKind.PERSON -> {
            arguments += requireNotNull(metric.id).toString()
            "EXISTS (SELECT 1 FROM event_person WHERE event_id = life_event.id AND person_id = ?)"
        }
        ReviewMetricKind.PLACE -> {
            arguments += requireNotNull(metric.id).toString()
            "EXISTS (SELECT 1 FROM event_place WHERE event_id = life_event.id AND place_id = ?)"
        }
        ReviewMetricKind.ACTIVITY -> {
            val key = requireNotNull(metric.activity)
            arguments.addAll(listOf(key.type, key.unit, key.source.name))
            "EXISTS (SELECT 1 FROM activity_record WHERE life_event_id = life_event.id AND activity_type = ? AND unit = ? AND source = ?)"
        }
    }
    private val where = "type != 'APP_USAGE' AND occurred_at >= ? AND occurred_at < ? AND ($predicate)"
    val countSql get() = "SELECT COUNT(*) FROM life_event WHERE $where"
    val pageSql get() = "SELECT * FROM life_event WHERE $where ORDER BY occurred_at, id LIMIT ? OFFSET ?"
}
