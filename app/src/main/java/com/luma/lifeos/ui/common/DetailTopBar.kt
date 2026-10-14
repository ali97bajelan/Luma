package com.luma.lifeos.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(
    title: String,
    backTag: String,
    onBack: () -> Unit,
    editTag: String? = null,
    editLabel: String? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    require(onEdit == null || (editTag != null && editLabel != null)) {
        "An edit action needs both a test tag and an accessible label"
    }
    Box(Modifier.testTag("detail_top_bar")) {
        TopAppBar(
            modifier = modifier,
            title = {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.testTag("detail_top_bar_title"))
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag(backTag),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron), contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (onEdit != null) {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = requireNotNull(editLabel) }
                            .testTag(requireNotNull(editTag)),
                    ) { Text(stringResource(R.string.detail_edit)) }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            // LifeOsApp's outer Scaffold already applies the system-bar insets to NavHost.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
    }
}
