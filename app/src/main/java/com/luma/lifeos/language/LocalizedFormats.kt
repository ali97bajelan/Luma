package com.luma.lifeos.language

import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/** Localized display only: persisted ISO dates/times remain untouched. */
fun localizedDate(style: FormatStyle): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(style)
        .withLocale(Locale.getDefault())
        .withChronology(java.time.chrono.IsoChronology.INSTANCE)
        .withDecimalStyle(DecimalStyle.of(Locale.getDefault()))

fun localizedTime(use24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter {
    return DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
        .withChronology(java.time.chrono.IsoChronology.INSTANCE)
        .withDecimalStyle(DecimalStyle.of(locale))
}
