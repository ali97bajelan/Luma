package com.luma.lifeos.ui.memory

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.MemoryDetails

@Composable
fun MemoryStatus(memory: MemoryDetails) {
    val importance = memory.importance
    Text(if (importance == null) stringResource(R.string.memory_status)
        else stringResource(R.string.memory_status_importance, importance),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("memory_status"))
}
