package com.luma.lifeos.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.repository.PersonRepository
import java.util.UUID

@Composable
fun PersonEditorRoute(repository: PersonRepository, personId: UUID?, onCancel: () -> Unit, onFinished: (Boolean) -> Unit) {
    val factory = remember(repository, personId) {
        viewModelFactory { initializer { PersonEditorViewModel(repository, personId, createSavedStateHandle()) } }
    }
    val editor: PersonEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onFinished(state.deleted) }
    PersonEditorScreen(state, onCancel, editor::save, editor::delete, editor::load, editor::setName, editor::setNote)
}

@Composable
fun PersonEditorScreen(state: PersonEditorState, onCancel: () -> Unit, onSave: () -> Unit,
    onDelete: () -> Unit, onRetry: () -> Unit, onName: (String) -> Unit, onNote: (String) -> Unit) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() { if (!state.busy) { if (state.hasChanges) discarding = true else onCancel() } }
    BackHandler { close() }
    Scaffold(modifier = Modifier.testTag("screen_person_editor"), bottomBar = {
        Row(Modifier.fillMaxWidth().imePadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !state.busy, onClick = { close() }) { Text(stringResource(R.string.cancel)) }
            if (state.editing) TextButton(enabled = enabled, onClick = { deleting = true }, modifier = Modifier.testTag("person_delete")) {
                Text(stringResource(R.string.delete))
            }
            Button(enabled = enabled && state.draft.name.isNotBlank(), onClick = onSave,
                modifier = Modifier.weight(1f).testTag("person_save")) {
                Text(stringResource(if (state.busy) R.string.saving else R.string.save))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (state.editing) R.string.person_edit else R.string.person_create),
                style = MaterialTheme.typography.headlineMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    PersonEditorError.LOAD -> R.string.people_load_error
                    PersonEditorError.SAVE -> R.string.person_save_error
                    PersonEditorError.DELETE -> R.string.person_delete_error
                    PersonEditorError.TEXT -> R.string.person_text_error
                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("person_error"))
                if (error == PersonEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            OutlinedTextField(state.draft.name, onName, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.person_name)) }, modifier = Modifier.fillMaxWidth().testTag("person_name"))
            OutlinedTextField(state.draft.note, onNote, enabled = enabled, minLines = 4,
                label = { Text(stringResource(R.string.person_note)) }, modifier = Modifier.fillMaxWidth().testTag("person_note"))
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.person_delete_title)) },
        text = { Text(stringResource(R.string.person_delete_body)) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }, modifier = Modifier.testTag("person_confirm_delete")) {
            Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    if (discarding) AlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.person_discard_title)) },
        text = { Text(stringResource(R.string.person_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}
