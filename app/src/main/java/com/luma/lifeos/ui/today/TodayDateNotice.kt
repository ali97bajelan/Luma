package com.luma.lifeos.ui.today

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.language.localizedDate
import java.time.LocalDate
import java.time.format.FormatStyle

@Composable
internal fun TodayDateNotice(selectedDate: LocalDate, currentDate: LocalDate, modifier: Modifier = Modifier) {
    if (selectedDate == currentDate) return

    Surface(
        modifier = modifier.fillMaxWidth().testTag("today_date_notice"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            stringResource(
                R.string.today_viewing_date,
                selectedDate.format(localizedDate(FormatStyle.FULL)),
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
