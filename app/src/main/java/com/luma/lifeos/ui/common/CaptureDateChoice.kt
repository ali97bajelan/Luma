package com.luma.lifeos.ui.common

import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
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
    val formattedDate = com.luma.lifeos.language.localizedDate(date, FormatStyle.MEDIUM)
    LumaAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("today_capture_date_dialog"),
        title = { Text(stringResource(R.string.today_capture_when)) },
        text = { Text(stringResource(R.string.capture_add_to_date, formattedDate)) },
        confirmButton = {
            LumaTextButton(onClick = onSelectedDate, modifier = Modifier.testTag("today_capture_selected_date")) {
                Text(stringResource(R.string.capture_add_to_date, formattedDate))
            }
        },
        dismissButton = {
            LumaTextButton(onClick = onNow, modifier = Modifier.testTag("today_capture_now")) {
                Text(stringResource(R.string.capture_use_now))
            }
        },
    )
}
