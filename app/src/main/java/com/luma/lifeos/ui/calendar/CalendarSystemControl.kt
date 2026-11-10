package com.luma.lifeos.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.calendar.CalendarSystem
import com.luma.lifeos.ui.common.LumaFilterChip
import com.luma.lifeos.ui.common.LumaUi
import androidx.compose.material3.Text

@Composable
internal fun CalendarSystemControl(
    system: CalendarSystem,
    onSelect: (CalendarSystem) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.testTag("calendar_system_switch")) {
        val stackOptions = (maxWidth < 420.dp) || (LocalDensity.current.fontScale >= 1.3f)
        if (stackOptions) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            ) {
                CalendarSystemOption(
                    system = CalendarSystem.PERSIAN,
                    selected = system == CalendarSystem.PERSIAN,
                    label = R.string.calendar_preference_persian,
                    tag = "calendar_system_persian",
                    enabled = enabled,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
                CalendarSystemOption(
                    system = CalendarSystem.GREGORIAN,
                    selected = system == CalendarSystem.GREGORIAN,
                    label = R.string.calendar_preference_gregorian,
                    tag = "calendar_system_gregorian",
                    enabled = enabled,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            ) {
                CalendarSystemOption(
                    system = CalendarSystem.PERSIAN,
                    selected = system == CalendarSystem.PERSIAN,
                    label = R.string.calendar_preference_persian,
                    tag = "calendar_system_persian",
                    enabled = enabled,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                CalendarSystemOption(
                    system = CalendarSystem.GREGORIAN,
                    selected = system == CalendarSystem.GREGORIAN,
                    label = R.string.calendar_preference_gregorian,
                    tag = "calendar_system_gregorian",
                    enabled = enabled,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CalendarSystemOption(
    system: CalendarSystem,
    selected: Boolean,
    label: Int,
    tag: String,
    enabled: Boolean,
    onSelect: (CalendarSystem) -> Unit,
    modifier: Modifier,
) {
    LumaFilterChip(
        selected = selected,
        onClick = { onSelect(system) },
        enabled = enabled,
        label = { Text(stringResource(label)) },
        modifier = modifier.testTag(tag),
    )
}
