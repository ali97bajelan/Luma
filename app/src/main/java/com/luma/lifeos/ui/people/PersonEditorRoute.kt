package com.luma.lifeos.ui.people

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
import com.luma.lifeos.domain.model.PersonTextLimits
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.EditorTopBar
import com.luma.lifeos.ui.common.EditorActionRow
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.ui.navigation.CatalogEditorResult
import java.text.NumberFormat
import java.util.UUID
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaDestructiveTextButton

@Composable
fun PersonEditorRoute(
    repository: PersonRepository,
    personId: UUID?,
    onCancel: () -> Unit,
    onFinished: (CatalogEditorResult) -> Unit,
    requestId: UUID? = null,
) {
    val factory = remember(repository, personId, requestId) {
        viewModelFactory { initializer { PersonEditorViewModel(repository, personId, createSavedStateHandle(), requestId) } }
    }
    val editor: PersonEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed, state.result) {
        if (state.completed) state.result?.let(onFinished)
    }
    PersonEditorScreen(state, onCancel, editor::save, editor::delete, editor::load, editor::setName, editor::setNote)
}

@Composable
fun PersonEditorScreen(state: PersonEditorState, onCancel: () -> Unit, onSave: () -> Unit,
    onDelete: () -> Unit, onRetry: () -> Unit, onName: (String) -> Unit, onNote: (String) -> Unit) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() { if (!state.busy) { if (state.hasChanges) discarding = true else onCancel() } }
    BackHandler { close() }
    Scaffold(modifier = Modifier.testTag("screen_person_editor"), contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        EditorTopBar(stringResource(if (state.editing) R.string.person_edit else R.string.person_create),
            "person_cancel", !state.busy, ::close)
    }, bottomBar = {
        LumaReadingPane(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.error) {
                    PersonEditorError.SAVE -> AccessibleFeedback(stringResource(R.string.person_save_error), Modifier.testTag("person_action_error"))
                    PersonEditorError.DELETE -> AccessibleFeedback(stringResource(R.string.person_delete_error), Modifier.testTag("person_action_error"))
                    else -> Unit
                }
                EditorActionRow(
                    secondaryAction = if (state.editing) { actionModifier ->
                        TextButton(enabled = enabled, onClick = { deleting = true }, modifier = actionModifier.testTag("person_delete")) {
                            Text(stringResource(R.string.delete))
                        }
                    } else null,
                    primaryAction = { actionModifier ->
                        Button(enabled = enabled && state.draft.name.isNotBlank(), onClick = onSave,
                            modifier = actionModifier.testTag("person_save")) {
                            Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                        }
                    },
                )
            }
        }
    }) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading) CircularProgressIndicator()
                state.error?.let { error ->
                    val message = stringResource(when (error) {
                        PersonEditorError.LOAD -> R.string.people_load_error
                        PersonEditorError.SAVE -> R.string.person_save_error
                        PersonEditorError.DELETE -> R.string.person_delete_error
                        PersonEditorError.TEXT -> R.string.person_text_error
                    })
                    if (error == PersonEditorError.LOAD || (error == PersonEditorError.TEXT && state.textErrors.isEmpty())) {
                        AccessibleFeedback(message, Modifier.testTag("person_error"))
                    }
                    if (error == PersonEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                val nameError = if (PersonTextField.NAME in state.textErrors)
                    stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(PersonTextLimits.NAME)) else null
                LumaOutlinedTextField(state.draft.name, onName, enabled = enabled, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                    isError = nameError != null, supportingText = { nameError?.let { AccessibleFeedback(it) } },
                    label = { Text(stringResource(R.string.person_name)) }, modifier = Modifier.fillMaxWidth()
                        .semantics { nameError?.let { error(it) } }.testTag("person_name"))
                val noteError = if (PersonTextField.NOTE in state.textErrors)
                    stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(PersonTextLimits.NOTE)) else null
                LumaOutlinedTextField(state.draft.note, onNote, enabled = enabled, minLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    isError = noteError != null, supportingText = { noteError?.let { AccessibleFeedback(it) } },
                    label = { Text(stringResource(R.string.person_note)) }, modifier = Modifier.fillMaxWidth().focusRequester(noteFocus)
                        .semantics { noteError?.let { error(it) } }.testTag("person_note"))
            }
        }
    }
    if (deleting) LumaAlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.person_delete_title)) },
        text = { Text(stringResource(R.string.person_delete_body)) },
        confirmButton = { LumaDestructiveTextButton(onClick = { deleting = false; onDelete() }, modifier = Modifier.testTag("person_confirm_delete")) {
            Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    if (discarding) LumaAlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.person_discard_title)) },
        text = { Text(stringResource(R.string.person_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}
