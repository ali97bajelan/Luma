package com.luma.lifeos.domain.calendar

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.todo.TodoTextLimits
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val CALENDAR_MAX_RANGE_DAYS = 42
const val CALENDAR_MEMORY_PREVIEW_CODE_POINTS = 160

enum class CalendarMode {
    MONTH,
    WEEK,
}

enum class CalendarFilter {
    ALL,
    TASKS,
    OVERDUE,
}

/** Inclusive range of supported Gregorian civil dates returned by Calendar projections. */
data class CalendarRange(val start: LocalDate, val through: LocalDate) {
    init {
        requireCalendarDate(start, "range start")
        requireCalendarDate(through, "range end")
        require(!through.isBefore(start)) { "Calendar range start must not follow its end" }
        require(ChronoUnit.DAYS.between(start, through) < CALENDAR_MAX_RANGE_DAYS) {
            "Calendar range cannot contain more than $CALENDAR_MAX_RANGE_DAYS dates"
        }
    }
}

/** Exact-date task count. A true overdue flag means at least one task is overdue as of today. */
data class CalendarTaskMarker(val count: Int, val overdue: Boolean) {
    init {
        require(count > 0) { "Calendar task markers must represent at least one task" }
    }
}

data class CalendarMemory(val id: EventId, val title: String?, val preview: String) {
    init {
        require(preview.codePointCount(0, preview.length) <= CALENDAR_MEMORY_PREVIEW_CODE_POINTS) {
            "Calendar memory preview cannot exceed $CALENDAR_MEMORY_PREVIEW_CODE_POINTS code points"
        }
    }
}

data class CalendarMemories(val total: Int, val items: List<CalendarMemory>) {
    init {
        require(total >= items.size) { "Calendar memory total cannot be smaller than its page" }
        require((total == 0) == items.isEmpty()) {
            "Only an empty calendar memory result may have no rows"
        }
    }
}

/** Rejects a civil date outside the supported Gregorian year range. */
fun requireCalendarDate(date: LocalDate, label: String = "date") {
    require(date.year in TodoTextLimits.MIN_YEAR..TodoTextLimits.MAX_YEAR) {
        "Calendar $label must be in years ${TodoTextLimits.MIN_YEAR}–${TodoTextLimits.MAX_YEAR}"
    }
}
