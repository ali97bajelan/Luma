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
import com.luma.lifeos.R
import com.luma.lifeos.calendar.CalendarPreference
import com.luma.lifeos.calendar.rememberCalendarPolicy
import com.luma.lifeos.calendar.rememberCalendarPreferences
import com.luma.lifeos.domain.calendar.CalendarSystem
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaTextButton
import com.luma.lifeos.ui.common.LumaUi
import kotlinx.coroutines.launch

@Composable
fun CalendarSettingsCard() {
    val preferences = rememberCalendarPreferences()
    val policy = rememberCalendarPolicy(preferences)
    val scope = rememberCoroutineScope()
    var saveError by rememberSaveable { mutableStateOf(value = false) }
    var saveErrorRevision by rememberSaveable { mutableIntStateOf(0) }
    var latestRequest by remember { mutableIntStateOf(0) }
    var retryPreferenceStorage by rememberSaveable { mutableStateOf<String?>(null) }

    fun save(value: CalendarPreference) {
        retryPreferenceStorage = value.storageValue
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

    LumaSectionSurface(Modifier.testTag("calendar_settings")) {
        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
            Text(stringResource(R.string.settings_calendar_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_calendar_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                CalendarPreference.entries.forEach { preference ->
                    val selected = policy.preference == preference
                    val tag = when (preference) {
                        CalendarPreference.SYSTEM -> "calendar_preference_system"
                        CalendarPreference.PERSIAN -> "calendar_preference_persian"
                        CalendarPreference.GREGORIAN -> "calendar_preference_gregorian"
                    }
                    val label = stringResource(when (preference) {
                        CalendarPreference.SYSTEM -> R.string.calendar_preference_system
                        CalendarPreference.PERSIAN -> R.string.calendar_preference_persian
                        CalendarPreference.GREGORIAN -> R.string.calendar_preference_gregorian
                    })
                    val supportingText = if ((preference == CalendarPreference.SYSTEM) && policy.isReady) {
                        stringResource(
                            R.string.calendar_preference_follow_system_summary,
                            stringResource(calendarName(policy.system)),
                        )
                    } else {
                        null
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag(tag)
                            .selectable(
                                selected = selected,
                                enabled = policy.isReady,
                                role = Role.RadioButton,
                                onClick = { save(preference) },
                            )
                            .padding(horizontal = LumaUi.SectionPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap),
                    ) {
                        RadioButton(selected = selected, enabled = policy.isReady, onClick = null)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            supportingText?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (saveError) key(saveErrorRevision) {
                AccessibleFeedback(
                    stringResource(R.string.calendar_preference_save_error),
                    Modifier.testTag("calendar_preference_error"),
                )
            }
            if (saveError) {
                LumaTextButton(
                    onClick = {
                        retryPreferenceStorage?.let { storageValue ->
                            save(CalendarPreference.fromStorage(storageValue))
                        }
                    },
                    modifier = Modifier.testTag("calendar_preference_retry"),
                ) {
                    Text(stringResource(R.string.calendar_preference_retry))
                }
            }
        }
    }
}

private fun calendarName(system: CalendarSystem): Int = when (system) {
    CalendarSystem.PERSIAN -> R.string.calendar_preference_persian
    CalendarSystem.GREGORIAN -> R.string.calendar_preference_gregorian
}
