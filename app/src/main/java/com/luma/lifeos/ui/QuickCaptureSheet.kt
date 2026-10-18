package com.luma.lifeos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import java.time.LocalDate
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(browsedDate: LocalDate? = null, onDismiss: () -> Unit,
    onMood: (LocalDate?) -> Unit, onJournal: (LocalDate?) -> Unit, onMemory: (LocalDate?) -> Unit) {
    var useBrowsedDate by rememberSaveable(browsedDate) { mutableStateOf(false) }
    val captureDate = browsedDate.takeIf { useBrowsedDate }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("capture_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.quick_capture), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.capture_intro))
            if (browsedDate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !useBrowsedDate, onClick = { useBrowsedDate = false },
                        label = { Text(stringResource(R.string.capture_use_now)) },
                        modifier = Modifier.fillMaxWidth().testTag("capture_now"))
                    FilterChip(selected = useBrowsedDate, onClick = { useBrowsedDate = true },
                        label = { Text(stringResource(R.string.capture_add_to_date,
                            browsedDate.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)))) },
                        modifier = Modifier.fillMaxWidth().testTag("capture_on_browsed_date"))
                }
            }
            Button(onClick = { onMood(captureDate) }, modifier = Modifier.fillMaxWidth().testTag("capture_mood")) {
                Text(stringResource(R.string.capture_mood))
            }
            Button(onClick = { onJournal(captureDate) }, modifier = Modifier.fillMaxWidth().testTag("capture_journal")) {
                Text(stringResource(R.string.capture_journal))
            }
            Button(onClick = { onMemory(captureDate) }, modifier = Modifier.fillMaxWidth().testTag("capture_memory")) {
                Text(stringResource(R.string.capture_memory))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("capture_close")) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
