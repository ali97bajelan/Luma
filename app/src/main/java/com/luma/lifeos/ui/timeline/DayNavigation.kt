package com.luma.lifeos.ui.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.today.pickerDate
import com.luma.lifeos.ui.today.pickerMillis
import java.time.LocalDate
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNavigation(date: LocalDate, onSelect: (LocalDate) -> Unit, onToday: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f
        val dateText = date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = date != LocalDate.MIN, onClick = { onSelect(date.minusDays(1)) },
                        modifier = Modifier.testTag("day_previous")) {
                        Icon(painterResource(R.drawable.ic_chevron), contentDescription = stringResource(R.string.previous_day))
                    }
                    TextButton(
                        onClick = { picking = true },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("day_picker"),
                    ) {
                        Text(dateText, modifier = Modifier.fillMaxWidth().testTag("day_selected"),
                            textAlign = TextAlign.Center)
                    }
                    IconButton(enabled = date != LocalDate.MAX, onClick = { onSelect(date.plusDays(1)) },
                        modifier = Modifier.testTag("day_next")) {
                        Icon(painterResource(R.drawable.ic_chevron_forward), contentDescription = stringResource(R.string.next_day))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp).testTag("day_today")) {
                        Text(stringResource(R.string.today), maxLines = 1)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = date != LocalDate.MIN, onClick = { onSelect(date.minusDays(1)) },
                    modifier = Modifier.testTag("day_previous")) {
                    Icon(painterResource(R.drawable.ic_chevron), contentDescription = stringResource(R.string.previous_day))
                }
                TextButton(onClick = { picking = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("day_picker")) {
                    Text(dateText, modifier = Modifier.testTag("day_selected"), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp).testTag("day_today")) {
                    Text(stringResource(R.string.today), maxLines = 1)
                }
                IconButton(enabled = date != LocalDate.MAX, onClick = { onSelect(date.plusDays(1)) },
                    modifier = Modifier.testTag("day_next")) {
                    Icon(painterResource(R.drawable.ic_chevron_forward), contentDescription = stringResource(R.string.next_day))
                }
            }
        }
    }
    if (picking) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(date), yearRange = minOf(1900, date.year)..maxOf(2100, date.year))
        DatePickerDialog(modifier = Modifier.testTag("date_picker_dialog"), onDismissRequest = { picking = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onSelect(pickerDate(it)) }; picking = false
            }) { Text(stringResource(R.string.select_date)) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } }) {
            DatePicker(state = picker)
        }
    }
}
