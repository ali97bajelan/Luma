package com.luma.lifeos.language

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Formats activity quantities without rounding recorded precision away. */
fun formatActivityQuantity(value: BigDecimal, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 340 }.format(value)

/** Returns a localized signed activity delta, or null when either period is absent. */
fun formatActivityChange(current: BigDecimal?, previous: BigDecimal?, locale: Locale): String? {
    if (current == null || previous == null) return null
    val change = current.subtract(previous)
    return (if (change.signum() > 0) "+" else "") + formatActivityQuantity(change, locale)
}
