package com.luma.lifeos.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun EmptyStateCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    tag: String,
    @StringRes actionLabel: Int? = null,
    onAction: (() -> Unit)? = null,
    actionTag: String? = null,
) {
    LumaSectionSurface(modifier = Modifier.testTag(tag)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
                if (actionLabel != null && onAction != null) {
                    val actionModifier = if (actionTag == null) Modifier else Modifier.testTag(actionTag)
                    TextButton(
                        onClick = onAction,
                        modifier = actionModifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(actionLabel))
                    }
                }
            }
        }
    }
}
