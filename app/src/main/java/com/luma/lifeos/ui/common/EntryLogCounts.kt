package com.luma.lifeos.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R

/** Null keeps an unknown/loading log total from being presented as a recorded zero. */
@Composable
internal fun entryLogCounts(entries: Int, habitLogs: Int?): String {
    val entryCount = pluralStringResource(R.plurals.entry_count, entries, entries)
    return if (habitLogs == null) entryCount else stringResource(R.string.entry_habit_counts, entryCount,
        pluralStringResource(R.plurals.habit_log_count, habitLogs, habitLogs))
}
