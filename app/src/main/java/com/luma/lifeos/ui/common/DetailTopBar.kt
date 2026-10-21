package com.luma.lifeos.ui.common

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

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
    PageHeader(
        title = title,
        navigation = PageNavigation.BACK,
        onNavigate = onBack,
        headerTag = "detail_top_bar",
        titleTag = "detail_top_bar_title",
        navigationTag = backTag,
        modifier = modifier,
        actions = {
            if (onEdit != null) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = requireNotNull(editLabel) }
                        .testTag(requireNotNull(editTag)),
                ) { androidx.compose.material3.Text(stringResource(R.string.detail_edit)) }
            }
        },
    )
}
