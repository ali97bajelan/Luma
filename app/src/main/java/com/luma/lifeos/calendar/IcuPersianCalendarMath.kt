package com.luma.lifeos.calendar

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import com.luma.lifeos.domain.calendar.CalendarDateParts
import com.luma.lifeos.domain.calendar.CalendarMath
import com.luma.lifeos.domain.calendar.CalendarSystem
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Persian Solar Hijri conversion backed by the public Android ICU calendar implementation.
 *
 * A new UTC calendar is created for every operation because ICU Calendar instances are mutable.
 */
internal class IcuPersianCalendarMath : CalendarMath {
    override val system: CalendarSystem = CalendarSystem.PERSIAN

    override fun fromIso(date: LocalDate): CalendarDateParts {
        val epochMillis = Math.multiplyExact(date.toEpochDay(), MILLIS_PER_DAY)
        val calendar = newCalendar()
        calendar.timeInMillis = epochMillis
        return CalendarDateParts(
            year = calendar.get(Calendar.EXTENDED_YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    override fun toIso(parts: CalendarDateParts): LocalDate {
        val length = monthLength(parts.year, parts.month)
        require(parts.day <= length) {
            "Day ${parts.day} is invalid for Persian ${parts.year}-${parts.month}"
        }

        val calendar = newCalendar()
        setDateFields(calendar, parts)
        val epochMillis = calendar.timeInMillis
        val epochDay = Math.floorDiv(epochMillis, MILLIS_PER_DAY)
        val date = try {
            LocalDate.ofEpochDay(epochDay)
        } catch (failure: DateTimeException) {
            throw IllegalArgumentException("Persian date is outside the supported ISO range", failure)
        }
        require(fromIso(date) == parts) {
            "Persian date cannot be represented exactly by Android ICU"
        }
        return date
    }

    override fun monthLength(year: Int, month: Int): Int {
        require(month in 1..12) { "Persian month must be between 1 and 12" }
        val calendar = newCalendar()
        setDateFields(calendar, CalendarDateParts(year, month, 1))
        val length = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        require(calendar.get(Calendar.EXTENDED_YEAR) == year &&
            calendar.get(Calendar.MONTH) == month - 1 &&
            length in 29..31
        ) {
            "Persian year or month is outside the supported ICU range"
        }
        return length
    }

    private fun setDateFields(calendar: Calendar, parts: CalendarDateParts) {
        calendar.clear()
        calendar.isLenient = false
        calendar.set(Calendar.EXTENDED_YEAR, parts.year)
        calendar.set(Calendar.MONTH, parts.month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, parts.day)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // Force strict field resolution now, so invalid and overflowing ICU fields cannot escape.
        calendar.timeInMillis
        require(calendar.get(Calendar.EXTENDED_YEAR) == parts.year &&
            calendar.get(Calendar.MONTH) == parts.month - 1 &&
            calendar.get(Calendar.DAY_OF_MONTH) == parts.day
        ) {
            "Persian date is outside the supported ICU range"
        }
    }

    private fun newCalendar(): Calendar {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), PERSIAN_LOCALE)
        check(calendar.type == PERSIAN_TYPE) {
            "Android ICU did not create the requested Persian calendar"
        }
        calendar.isLenient = false
        return calendar
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val PERSIAN_TYPE = "persian"
        val PERSIAN_LOCALE: ULocale = ULocale("en@calendar=persian")
    }
}
