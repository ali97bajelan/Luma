package com.luma.lifeos.ui.mood

import androidx.compose.runtime.Composable
import com.luma.lifeos.ui.common.CaptureTimestampRow
import java.time.LocalDateTime
import java.time.ZoneId

/** Kept as a forwarding name for existing editor and test call sites. */
@Composable
fun MoodTimeControls(
    milliseconds: Long,
    enabled: Boolean,
    timeError: Boolean,
    onTime: (LocalDateTime, ZoneId) -> Boolean,
) {
    CaptureTimestampRow(milliseconds, enabled, timeError, onTime)
}
