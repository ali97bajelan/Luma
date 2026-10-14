package com.luma.lifeos.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
fun AccessibleFeedback(message: String, modifier: Modifier = Modifier, isError: Boolean = true) {
    Text(
        text = message,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            if (isError) error(message)
        },
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall,
    )
}
