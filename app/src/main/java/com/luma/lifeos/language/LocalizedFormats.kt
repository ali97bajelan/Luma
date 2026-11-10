package com.luma.lifeos.language

import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.chrono.IsoChronology
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/** Localized, calendar-aware display only; persisted ISO dates remain untouched. */
@Composable
fun localizedDate(date: LocalDate, style: FormatStyle): String {
    val policy = rememberDisplayTimePolicy()
    return policy.formatDate(date, style)
}

/**
 * Locale-only compatibility formatter for non-UI callers. Compose UI should use
 * localizedDate(date, style) so the Settings calendar preference is observed.
 */
@Deprecated("Use localizedDate(date, style) for human-readable UI dates")
fun localizedDate(style: FormatStyle): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(style)
        .withLocale(Locale.getDefault())
        .withChronology(IsoChronology.INSTANCE)
        .withDecimalStyle(DecimalStyle.of(Locale.getDefault()))

fun localizedTime(use24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter {
    return DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
        .withChronology(IsoChronology.INSTANCE)
        .withDecimalStyle(DecimalStyle.of(locale))
}
