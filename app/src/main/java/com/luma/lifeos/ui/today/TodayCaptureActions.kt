package com.luma.lifeos.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayCaptureActions(
    onDateCapture: (EventType) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(EventType.MOOD to R.string.today_capture_mood, EventType.JOURNAL to R.string.today_capture_journal,
            EventType.MEMORY to R.string.today_capture_memory).forEach { (type, label) ->
            FilledTonalButton(onClick = { onDateCapture(type) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("today_capture_${type.name.lowercase()}")) {
                Text(stringResource(label))
            }
        }
    }
}
