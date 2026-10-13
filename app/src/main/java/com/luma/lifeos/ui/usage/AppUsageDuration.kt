package com.luma.lifeos.ui.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R

/** App usage on Today and Timeline is shown as whole hours and minutes. */
@Composable
fun appUsageDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    return stringResource(R.string.app_usage_duration, totalMinutes / 60, totalMinutes % 60)
}
