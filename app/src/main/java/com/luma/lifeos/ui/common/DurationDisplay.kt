package com.luma.lifeos.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import java.text.NumberFormat

/** Display-only precision for browsing saved durations; raw millisecond values stay unchanged. */
internal sealed interface DurationDisplay {
    data object Zero : DurationDisplay
    data object UnderSecond : DurationDisplay
    data class Seconds(val value: Long) : DurationDisplay
    data class Minutes(val value: Long) : DurationDisplay
    data class HoursMinutes(val hours: Long, val minutes: Long) : DurationDisplay
}

internal fun durationDisplay(millis: Long): DurationDisplay {
    require(millis >= 0) { "Duration must be nonnegative" }
    if (millis == 0L) return DurationDisplay.Zero
    if (millis < 1_000L) return DurationDisplay.UnderSecond
    if (millis < 60_000L) return DurationDisplay.Seconds(millis / 1_000L)

    val totalMinutes = millis / 60_000L
    return if (totalMinutes < 60L) DurationDisplay.Minutes(totalMinutes)
    else DurationDisplay.HoursMinutes(totalMinutes / 60L, totalMinutes % 60L)
}

@Composable
internal fun humanReadableDuration(millis: Long): String {
    val number = NumberFormat.getIntegerInstance()
    return when (val display = durationDisplay(millis)) {
        DurationDisplay.Zero -> stringResource(R.string.app_usage_duration_seconds, number.format(0))
        DurationDisplay.UnderSecond -> stringResource(R.string.app_usage_duration_under_second)
        is DurationDisplay.Seconds -> stringResource(R.string.app_usage_duration_seconds, number.format(display.value))
        is DurationDisplay.Minutes -> stringResource(R.string.app_usage_duration_minutes, number.format(display.value))
        is DurationDisplay.HoursMinutes -> stringResource(R.string.app_usage_duration_hours_minutes,
            number.format(display.hours), number.format(display.minutes))
    }
}

/** Precise hours/minutes and subsecond remainder for explicitly labeled usage details. */
@Composable
internal fun exactDuration(millis: Long): String {
    require(millis >= 0) { "Duration must be nonnegative" }
    val seconds = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }
        .format(millis % 60_000L / 1_000.0)
    return stringResource(R.string.usage_duration,
        millis / 3_600_000L, millis / 60_000L % 60L, seconds)
}
