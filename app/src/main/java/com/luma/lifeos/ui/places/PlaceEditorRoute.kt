package com.luma.lifeos.ui.places

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.PlaceTextLimits
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.EditorTopBar
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.navigation.CatalogEditorResult
import java.text.NumberFormat
import java.util.UUID

@Composable
fun PlaceEditorRoute(
    repository: PlaceRepository,
    placeId: UUID?,
    onCancel: () -> Unit,
    onFinished: (CatalogEditorResult) -> Unit,
    requestId: UUID? = null,
) {
    val factory = remember(repository, placeId, requestId) {
        viewModelFactory { initializer { PlaceEditorViewModel(repository, placeId, createSavedStateHandle(), requestId) } }
    }
    val editor: PlaceEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed, state.result) {
        if (state.completed) state.result?.let(onFinished)
    }
    PlaceEditorScreen(state, onCancel, editor::save, editor::delete, editor::load, editor::setName, editor::setNote)
}

@Composable
fun PlaceEditorScreen(state: PlaceEditorState, onCancel: () -> Unit, onSave: () -> Unit,
    onDelete: () -> Unit, onRetry: () -> Unit, onName: (String) -> Unit, onNote: (String) -> Unit) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() { if (!state.busy) { if (state.hasChanges) discarding = true else onCancel() } }
    BackHandler { close() }
    Scaffold(modifier = Modifier.testTag("screen_place_editor"), topBar = {
        EditorTopBar(stringResource(if (state.editing) R.string.place_edit else R.string.place_create),
            "place_cancel", !state.busy, ::close)
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.error) {
                PlaceEditorError.SAVE -> AccessibleFeedback(stringResource(R.string.place_save_error), Modifier.testTag("place_action_error"))
                PlaceEditorError.DELETE -> AccessibleFeedback(stringResource(R.string.place_delete_error), Modifier.testTag("place_action_error"))
                else -> Unit
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.editing) TextButton(enabled = enabled, onClick = { deleting = true }, modifier = Modifier.testTag("place_delete")) {
                    Text(stringResource(R.string.delete))
                }
                Button(enabled = enabled && state.draft.name.isNotBlank(), onClick = onSave,
                    modifier = Modifier.weight(1f).testTag("place_save")) {
                    Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) CircularProgressIndicator()
            state.error?.let { error ->
                val message = stringResource(when (error) {
                    PlaceEditorError.LOAD -> R.string.places_load_error
                    PlaceEditorError.SAVE -> R.string.place_save_error
                    PlaceEditorError.DELETE -> R.string.place_delete_error
                    PlaceEditorError.TEXT -> R.string.place_text_error
                })
                if (error == PlaceEditorError.LOAD || (error == PlaceEditorError.TEXT && state.textErrors.isEmpty())) {
                    AccessibleFeedback(message, Modifier.testTag("place_error"))
                }
                if (error == PlaceEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            val nameError = if (PlaceTextField.NAME in state.textErrors)
                stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(PlaceTextLimits.NAME)) else null
            OutlinedTextField(state.draft.name, onName, enabled = enabled, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                isError = nameError != null, supportingText = { nameError?.let { AccessibleFeedback(it) } },
                label = { Text(stringResource(R.string.place_name)) }, modifier = Modifier.fillMaxWidth()
                    .semantics { nameError?.let { error(it) } }.testTag("place_name"))
            val noteError = if (PlaceTextField.NOTE in state.textErrors)
                stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(PlaceTextLimits.NOTE)) else null
            OutlinedTextField(state.draft.note, onNote, enabled = enabled, minLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                isError = noteError != null, supportingText = { noteError?.let { AccessibleFeedback(it) } },
                label = { Text(stringResource(R.string.place_note)) }, modifier = Modifier.fillMaxWidth().focusRequester(noteFocus)
                    .semantics { noteError?.let { error(it) } }.testTag("place_note"))
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.place_delete_title)) },
        text = { Text(stringResource(R.string.place_delete_body)) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }, modifier = Modifier.testTag("place_confirm_delete")) {
            Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    if (discarding) AlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.place_discard_title)) },
        text = { Text(stringResource(R.string.place_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}
