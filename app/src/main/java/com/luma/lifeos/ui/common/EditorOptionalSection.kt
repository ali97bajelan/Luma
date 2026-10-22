package com.luma.lifeos.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@Composable
internal fun EditorOptionalSection(
    @StringRes title: Int,
    tag: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    summary: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val disclosureState = stringResource(
        if (expanded) R.string.review_details_expanded else R.string.review_details_collapsed,
    )
    LumaSectionSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { heading(); stateDescription = disclosureState }.testTag(tag),
            ) {
                Text(
                    stringResource(title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!expanded) summary?.invoke()
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun EditorActionRow(
    secondaryAction: (@Composable (Modifier) -> Unit)? = null,
    primaryAction: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        if (maxWidth < 360.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                secondaryAction?.invoke(Modifier.fillMaxWidth().heightIn(min = 48.dp))
                primaryAction(Modifier.fillMaxWidth().heightIn(min = 48.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondaryAction?.invoke(Modifier.widthIn(max = availableWidth * 0.45f).heightIn(min = 48.dp))
                primaryAction(Modifier.weight(1f).heightIn(min = 48.dp))
            }
        }
    }
}
