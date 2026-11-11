package com.luma.lifeos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.calendar.rememberWeekStartPreferences
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaTextButton
import com.luma.lifeos.ui.common.LumaUi
import java.time.DayOfWeek
import kotlinx.coroutines.launch

private val weekStartOptions = listOf(
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

@Composable
fun WeekStartSettingsCard() {
    val preferences = rememberWeekStartPreferences()
    val selectedDay by preferences.selection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var saveError by rememberSaveable { mutableStateOf(false) }
    var saveErrorRevision by rememberSaveable { mutableIntStateOf(0) }
    var latestRequest by remember { mutableIntStateOf(0) }
    var retryDay by rememberSaveable { mutableStateOf<DayOfWeek?>(null) }

    fun save(value: DayOfWeek) {
        retryDay = value
        saveError = false
        val request = ++latestRequest
        scope.launch {
            val result = preferences.select(value)
            if (request == latestRequest) {
                saveError = result.isFailure
                if (saveError) saveErrorRevision++
            }
        }
    }

    LumaSectionSurface(Modifier.testTag("week_start_settings")) {
        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
            Text(stringResource(R.string.week_start_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.week_start_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                weekStartOptions.forEach { day ->
                    val selected = selectedDay == day
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("week_start_${day.name.lowercase()}")
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { save(day) },
                            )
                            .padding(horizontal = LumaUi.SectionPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            stringResource(weekStartLabel(day)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            if (saveError) key(saveErrorRevision) {
                AccessibleFeedback(
                    stringResource(R.string.week_start_save_error),
                    Modifier.testTag("week_start_error"),
                )
            }
            if (saveError) {
                LumaTextButton(
                    onClick = { save(retryDay ?: selectedDay) },
                    modifier = Modifier.testTag("week_start_retry"),
                ) {
                    Text(stringResource(R.string.week_start_retry))
                }
            }
        }
    }
}

private fun weekStartLabel(day: DayOfWeek): Int = when (day) {
    DayOfWeek.SATURDAY -> R.string.week_start_saturday
    DayOfWeek.SUNDAY -> R.string.week_start_sunday
    DayOfWeek.MONDAY -> R.string.week_start_monday
    DayOfWeek.TUESDAY -> R.string.week_start_tuesday
    DayOfWeek.WEDNESDAY -> R.string.week_start_wednesday
    DayOfWeek.THURSDAY -> R.string.week_start_thursday
    DayOfWeek.FRIDAY -> R.string.week_start_friday
}
