package com.luma.lifeos.domain.calendar

import java.time.LocalDate
import java.time.YearMonth

/** Proleptic ISO/Gregorian arithmetic backed by java.time. */
object GregorianCalendarMath : CalendarMath {
    override val system: CalendarSystem = CalendarSystem.GREGORIAN

    override fun fromIso(date: LocalDate): CalendarDateParts =
        CalendarDateParts(date.year, date.monthValue, date.dayOfMonth)

    override fun toIso(parts: CalendarDateParts): LocalDate {
        val length = monthLength(parts.year, parts.month)
        require(parts.day <= length) {
            "Day ${parts.day} is invalid for Gregorian ${parts.year}-${parts.month}"
        }
        return LocalDate.of(parts.year, parts.month, parts.day)
    }

    override fun monthLength(year: Int, month: Int): Int {
        require(year in MIN_YEAR..MAX_YEAR) {
            "Gregorian year must be between $MIN_YEAR and $MAX_YEAR"
        }
        require(month in 1..12) { "Gregorian month must be between 1 and 12" }
        return YearMonth.of(year, month).lengthOfMonth()
    }

    private const val MIN_YEAR = -999_999_999
    private const val MAX_YEAR = 999_999_999
}
