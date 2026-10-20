package com.luma.lifeos.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import java.time.LocalDate
import java.time.format.FormatStyle

@Composable
internal fun CaptureDateChoice(
    date: LocalDate,
    onSelectedDate: () -> Unit,
    onNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formattedDate = date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("today_capture_date_dialog"),
        title = { Text(stringResource(R.string.today_capture_when)) },
        text = { Text(stringResource(R.string.capture_add_to_date, formattedDate)) },
        confirmButton = {
            TextButton(onClick = onSelectedDate, modifier = Modifier.testTag("today_capture_selected_date")) {
                Text(stringResource(R.string.capture_add_to_date, formattedDate))
            }
        },
        dismissButton = {
            TextButton(onClick = onNow, modifier = Modifier.testTag("today_capture_now")) {
                Text(stringResource(R.string.capture_use_now))
            }
        },
    )
}
