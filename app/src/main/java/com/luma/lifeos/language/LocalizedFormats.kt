package com.luma.lifeos.language

import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/** Localized display only: persisted ISO dates/times remain untouched. */
fun localizedDate(style: FormatStyle): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(style).localizedDigits()
fun localizedDateTime(style: FormatStyle): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(style).localizedDigits()
fun localizedTime(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").localizedDigits()
private fun DateTimeFormatter.localizedDigits(): DateTimeFormatter =
    withLocale(Locale.getDefault()).withDecimalStyle(DecimalStyle.of(Locale.getDefault()))
