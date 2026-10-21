package com.luma.lifeos.ui.common

import androidx.compose.runtime.Composable
import com.luma.lifeos.ui.common.PageNavigation.CLOSE

@Composable
fun EditorTopBar(title: String, closeTag: String, closeEnabled: Boolean, onClose: () -> Unit) {
    PageHeader(
        title = title,
        navigation = CLOSE,
        onNavigate = onClose,
        navigationEnabled = closeEnabled,
        titleTag = "editor_title",
        navigationTag = closeTag,
    )
}
