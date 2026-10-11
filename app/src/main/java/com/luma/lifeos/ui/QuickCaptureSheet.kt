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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("capture_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.quick_capture), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.capture_intro))
            Text(stringResource(R.string.capture_mood), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.capture_journal), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.capture_memory), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("capture_close")) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
