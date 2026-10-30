package com.luma.lifeos.ui.focus

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun focusDuration(millis: Long): String {
    require(millis >= 0)
    return if (millis in 1 until 60_000) {
        stringResource(R.string.focus_duration_under_minute)
    } else {
        DateUtils.formatElapsedTime(millis / 1_000)
    }
}

internal fun focusPercentage(value: BigDecimal): String = NumberFormat.getNumberInstance(Locale.getDefault()).run {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
    format(value)
}

@Composable
internal fun focusContributorCount(count: Int): String =
    pluralStringResource(R.plurals.focus_contributing_sessions, count, count)

@Composable
internal fun focusInterruptedCount(count: Int): String =
    pluralStringResource(R.plurals.focus_interrupted_count, count, count)
