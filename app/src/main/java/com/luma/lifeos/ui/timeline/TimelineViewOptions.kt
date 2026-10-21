package com.luma.lifeos.ui.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaSectionSurface
import java.time.ZoneId

@Composable
internal fun TimelineViewOptions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showAppUsage: Boolean,
    onShowAppUsageChange: (Boolean) -> Unit,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val disclosureState = stringResource(
        if (expanded) R.string.review_details_expanded else R.string.review_details_collapsed,
    )

    Column(modifier = modifier) {
        TextButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.heightIn(min = 48.dp)
                .testTag("timeline_view_options")
                .semantics { stateDescription = disclosureState },
        ) {
            Text(stringResource(if (expanded) R.string.timeline_hide_view_options else R.string.timeline_view_options))
        }

        if (expanded) {
            LumaSectionSurface(modifier = Modifier.testTag("timeline_view_options_panel")) {
                val usageSwitchLabel = stringResource(R.string.timeline_show_app_usage)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackSwitch = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f
                    if (stackSwitch) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(usageSwitchLabel, style = MaterialTheme.typography.bodyLarge)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TimelineAppUsageSwitch(
                                    checked = showAppUsage,
                                    label = usageSwitchLabel,
                                    onCheckedChange = onShowAppUsageChange,
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(usageSwitchLabel, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge)
                            TimelineAppUsageSwitch(
                                checked = showAppUsage,
                                label = usageSwitchLabel,
                                onCheckedChange = onShowAppUsageChange,
                            )
                        }
                    }
                }
                Text(zone.id, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("timeline_timezone"))
                Text(stringResource(R.string.entry_order_newest_first),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("timeline_order"))
            }
        }
    }
}

@Composable
private fun TimelineAppUsageSwitch(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.testTag("timeline_show_app_usage")
            .semantics { contentDescription = label },
    )
}
