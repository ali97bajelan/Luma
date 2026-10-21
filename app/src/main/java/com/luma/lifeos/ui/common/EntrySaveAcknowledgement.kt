package com.luma.lifeos.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import com.luma.lifeos.ui.navigation.EntryEditorResult

internal suspend fun acknowledgeSavedEntry(
    result: EntryEditorResult,
    snackbarHostState: SnackbarHostState,
    message: String,
) {
    if (result !is EntryEditorResult.Saved || snackbarHostState.currentSnackbarData != null) return

    snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
}
