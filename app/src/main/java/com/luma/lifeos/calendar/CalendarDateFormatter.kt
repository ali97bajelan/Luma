package com.luma.lifeos.calendar

import android.icu.text.DateFormat as IcuDateFormat
import android.icu.text.SimpleDateFormat as IcuSimpleDateFormat
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import com.luma.lifeos.domain.calendar.CalendarDateParts
import com.luma.lifeos.domain.calendar.CalendarSystem
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** Calendar-aware, display-only formatting for Calendar's local view policy. */
object CalendarDateFormatter {
    fun formatDate(
        date: LocalDate,
        style: FormatStyle,
        system: CalendarSystem,
        locale: Locale,
    ): String {
        if (system == CalendarSystem.GREGORIAN) {
            return DateTimeFormatter.ofLocalizedDate(style)
                .withLocale(locale)
                .withChronology(IsoChronology.INSTANCE)
                .withDecimalStyle(DecimalStyle.of(locale))
                .format(date)
        }

        val formatter = IcuDateFormat.getDateInstance(style.toIcuStyle(), localeFor(system, locale))
        requireCalendarType(formatter.calendar.type, system)
        formatter.timeZone = IcuTimeZone.getTimeZone("UTC")
        return formatter.format(date.toUtcDate())
    }

    fun formatMonth(date: LocalDate, system: CalendarSystem, locale: Locale): String {
        if (system == CalendarSystem.GREGORIAN) {
            return DateTimeFormatterBuilder().appendPattern("LLLL uuuu").toFormatter(locale)
                .withChronology(IsoChronology.INSTANCE)
                .withDecimalStyle(DecimalStyle.of(locale))
                .format(date)
        }

        val formatter = IcuSimpleDateFormat("LLLL u", localeFor(system, locale))
        requireCalendarType(formatter.calendar.type, system)
        formatter.timeZone = IcuTimeZone.getTimeZone("UTC")
        return formatter.format(date.toUtcDate())
    }

    fun formatMonthName(date: LocalDate, system: CalendarSystem, locale: Locale): String {
        if (system == CalendarSystem.GREGORIAN) {
            return DateTimeFormatter.ofPattern("LLLL", locale)
                .withChronology(IsoChronology.INSTANCE)
                .withDecimalStyle(DecimalStyle.of(locale))
                .format(date)
        }

        val formatter = IcuSimpleDateFormat("LLLL", localeFor(system, locale))
        requireCalendarType(formatter.calendar.type, system)
        formatter.timeZone = IcuTimeZone.getTimeZone("UTC")
        return formatter.format(date.toUtcDate())
    }

    fun formatWeekday(date: LocalDate, system: CalendarSystem, locale: Locale): String {
        if (system == CalendarSystem.GREGORIAN) {
            return DateTimeFormatter.ofPattern("EEE", locale)
                .withChronology(IsoChronology.INSTANCE)
                .withDecimalStyle(DecimalStyle.of(locale))
                .format(date)
        }

        val formatter = IcuSimpleDateFormat("EEE", localeFor(system, locale))
        requireCalendarType(formatter.calendar.type, system)
        formatter.timeZone = IcuTimeZone.getTimeZone("UTC")
        return formatter.format(date.toUtcDate())
    }

    fun formatDayNumber(parts: CalendarDateParts, locale: Locale): String =
        java.text.NumberFormat.getIntegerInstance(locale).format(parts.day)

    private fun localeFor(system: CalendarSystem, locale: Locale): ULocale =
        ULocale.forLocale(locale).setKeywordValue(
            "calendar",
            when (system) {
                CalendarSystem.PERSIAN -> "persian"
                CalendarSystem.GREGORIAN -> "gregorian"
            },
        )

    private fun requireCalendarType(actual: String, system: CalendarSystem) {
        val expected = when (system) {
            CalendarSystem.PERSIAN -> "persian"
            CalendarSystem.GREGORIAN -> "gregorian"
        }
        check(actual == expected) { "Expected ICU $expected calendar, got $actual" }
    }

    private fun FormatStyle.toIcuStyle(): Int = when (this) {
        FormatStyle.FULL -> IcuDateFormat.FULL
        FormatStyle.LONG -> IcuDateFormat.LONG
        FormatStyle.MEDIUM -> IcuDateFormat.MEDIUM
        FormatStyle.SHORT -> IcuDateFormat.SHORT
    }

    private fun LocalDate.toUtcDate(): Date =
        Date.from(atStartOfDay(ZoneOffset.UTC).toInstant())
}
