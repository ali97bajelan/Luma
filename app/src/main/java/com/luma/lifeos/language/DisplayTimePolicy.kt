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
import com.luma.lifeos.calendar.CalendarDateFormatter
import com.luma.lifeos.calendar.CalendarPolicy
import com.luma.lifeos.calendar.rememberCalendarPolicy
import com.luma.lifeos.domain.calendar.CalendarSystem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class DisplayTimePolicy(
    val locale: Locale,
    val use24Hour: Boolean,
    val calendarSystem: CalendarSystem,
) {
    fun formatDate(date: LocalDate, style: FormatStyle): String =
        CalendarDateFormatter.formatDate(date, style, calendarSystem, locale)

    /** The caller supplies the existing display zone by converting the instant first. */
    fun formatDateTime(value: ZonedDateTime, style: FormatStyle): String =
        formatDateTime(value.toLocalDateTime(), style)

    /** Formats a local timestamp without changing the caller's zone or date semantics. */
    fun formatDateTime(value: LocalDateTime, style: FormatStyle): String =
        "${formatDate(value.toLocalDate(), style)} ${value.toLocalTime().format(time())}"

    fun time(): DateTimeFormatter = localizedTime(use24Hour, locale)
}

@Composable
fun rememberDisplayTimePolicy(): DisplayTimePolicy {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val calendarPolicy: CalendarPolicy = rememberCalendarPolicy()
    var resumeGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(configuration, resumeGeneration, calendarPolicy.system) {
        val locale = configuration.locales[0] ?: Locale.getDefault()
        DisplayTimePolicy(locale, DateFormat.is24HourFormat(context), calendarPolicy.system)
    }
}

