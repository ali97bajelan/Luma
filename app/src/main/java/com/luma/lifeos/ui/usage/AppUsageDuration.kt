package com.luma.lifeos.ui.usage

import androidx.compose.runtime.Composable
import com.luma.lifeos.ui.common.humanReadableDuration

/** Compatibility wrapper for summary surfaces that already display usage durations. */
@Composable
fun appUsageDuration(millis: Long): String = humanReadableDuration(millis)
