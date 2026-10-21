package com.luma.lifeos.language

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

data class DisplayTimePolicy(val locale: Locale, val use24Hour: Boolean) {
    fun date(style: FormatStyle): DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(style)
        .withLocale(locale)
        .withChronology(IsoChronology.INSTANCE)
        .withDecimalStyle(DecimalStyle.of(locale))

    fun time(): DateTimeFormatter = localizedTime(use24Hour, locale)

    fun dateTime(style: FormatStyle): DateTimeFormatter = DateTimeFormatterBuilder()
        .appendLocalized(style, null)
        .appendLiteral(' ')
        .append(time())
        .toFormatter(locale)
        .withChronology(IsoChronology.INSTANCE)
        .withDecimalStyle(DecimalStyle.of(locale))
}

@Composable
fun rememberDisplayTimePolicy(): DisplayTimePolicy {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(configuration, resumeGeneration) {
        val locale = configuration.locales[0] ?: Locale.getDefault()
        DisplayTimePolicy(locale, DateFormat.is24HourFormat(context))
    }
}
