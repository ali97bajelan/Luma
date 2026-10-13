package com.luma.lifeos.ui.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import java.text.NumberFormat

/** App usage uses seconds below one minute so short visits remain visible. */
@Composable
fun appUsageDuration(millis: Long): String {
    if (millis in 1L..999L) return stringResource(R.string.app_usage_duration_under_second)
    if (millis in 1_000L until 60_000L) {
        val seconds = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(millis / 1_000.0)
        return stringResource(R.string.app_usage_duration_seconds, seconds)
    }
    val totalMinutes = millis / 60_000
    return stringResource(R.string.app_usage_duration, totalMinutes / 60, totalMinutes % 60)
}
