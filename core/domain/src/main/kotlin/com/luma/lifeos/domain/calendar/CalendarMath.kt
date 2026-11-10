package com.luma.lifeos.domain.calendar

import java.time.LocalDate

/** Calendar fields for an ISO civil date. Month is 1–12; year may be proleptic and negative. */
data class CalendarDateParts(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    init {
        require(month in 1..12) { "Calendar month must be between 1 and 12" }
        require(day in 1..31) { "Calendar day must be between 1 and 31" }
    }
}

/** Stable identity for a displayed month in one calendar system. */
data class CalendarMonthId(
    val system: CalendarSystem,
    val year: Int,
    val month: Int,
) {
    init {
        require(month in 1..12) { "Calendar month must be between 1 and 12" }
    }
}

/**
 * Converts between calendar fields and canonical ISO civil dates.
 *
 * Implementations must reject invalid calendar dates. Implementations with mutable backing APIs
 * must confine that state to each operation.
 */
interface CalendarMath {
    val system: CalendarSystem

    fun fromIso(date: LocalDate): CalendarDateParts

    fun toIso(parts: CalendarDateParts): LocalDate

    fun monthLength(year: Int, month: Int): Int
}
