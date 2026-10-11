package com.luma.lifeos.ui.journal

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
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.Tag
import com.luma.lifeos.domain.model.JournalTextLimits
import com.luma.lifeos.ui.mood.MoodTimeControls
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalEditorScreen(state: JournalEditorState, onCancel: () -> Unit, onSave: () -> Unit,
    onDelete: () -> Unit, onRetry: () -> Unit, onTitle: (String) -> Unit, onBody: (String) -> Unit,
    onAddTag: (String) -> Boolean, onRemoveTag: (String) -> Unit,
    onTime: (LocalDateTime, ZoneId) -> Boolean, peopleContent: @Composable () -> Unit = {}, placeContent: @Composable () -> Unit = {}, photosContent: @Composable () -> Unit = {}) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var tagText by rememberSaveable { mutableStateOf("") }
    var tagError by rememberSaveable { mutableStateOf(false) }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() {
        if (state.busy) return
        if (state.hasChanges || tagText.isNotBlank()) discarding = true else onCancel()
    }
    BackHandler { close() }
    Scaffold(modifier = Modifier.testTag("screen_journal"), bottomBar = {
        Row(Modifier.fillMaxWidth().imePadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !state.busy, onClick = { close() }, modifier = Modifier.testTag("journal_cancel")) {
                Text(stringResource(R.string.cancel))
            }
            if (state.editing) TextButton(enabled = enabled, onClick = { deleting = true }, modifier = Modifier.testTag("journal_delete")) {
                Text(stringResource(R.string.delete))
            }
            Button(enabled = enabled && state.draft.body.isNotBlank(), onClick = {
                if (tagText.isNotBlank() && onAddTag(tagText)) tagText = ""
                onSave()
            }, modifier = Modifier.weight(1f).testTag("journal_save")) {
                Text(stringResource(if (state.busy) R.string.saving else R.string.save))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (state.editing) R.string.journal_edit else R.string.journal_capture),
                style = MaterialTheme.typography.headlineMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    JournalEditorError.LOAD -> R.string.journal_load_error
                    JournalEditorError.SAVE -> R.string.journal_save_error
                    JournalEditorError.DELETE -> R.string.journal_delete_error
                    JournalEditorError.TIME -> R.string.mood_time_error
                    JournalEditorError.PHOTO -> R.string.photo_error
                    JournalEditorError.TEXT -> R.string.journal_text_error
                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("journal_error"))
                if (error == JournalEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            // Restored text remains visible even if loading fails.
            OutlinedTextField(value = state.draft.title, onValueChange = onTitle, enabled = enabled,
                label = { Text(stringResource(R.string.journal_title)) }, modifier = Modifier.fillMaxWidth().testTag("journal_title"))
            OutlinedTextField(value = state.draft.body, onValueChange = onBody, enabled = enabled,
                label = { Text(stringResource(R.string.journal_body)) }, minLines = 8,
                supportingText = { Text(stringResource(R.string.journal_body_hint)) },
                modifier = Modifier.fillMaxWidth().testTag("journal_body"))
            Text(stringResource(R.string.journal_tags), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.draft.tagNames.forEach { name ->
                    InputChip(selected = true, enabled = enabled, onClick = { onRemoveTag(name) },
                        label = { Text(stringResource(R.string.journal_remove_tag, name)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = tagText, onValueChange = {
                    if (it.length <= JournalTextLimits.TAG) { tagText = it; tagError = false } else tagError = true
                }, enabled = enabled, singleLine = true, isError = tagError,
                    supportingText = { if (tagError) Text(stringResource(R.string.journal_tag_size_error)) },
                    label = { Text(stringResource(R.string.journal_tag_name)) },
                    modifier = Modifier.weight(1f).testTag("journal_tag_input"))
                TextButton(enabled = enabled && tagText.isNotBlank(), onClick = {
                    if (onAddTag(tagText)) tagText = ""
                }, modifier = Modifier.testTag("journal_add_tag")) { Text(stringResource(R.string.add)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.filter { tag -> state.draft.tagNames.none { Tag.normalize(it) == Tag.normalize(tag.name) } }.forEach { tag ->
                    FilterChip(selected = false, enabled = enabled, onClick = { onAddTag(tag.name) }, label = { Text(tag.name) })
                }
            }
            peopleContent()
            placeContent()
            photosContent()
            MoodTimeControls(state.draft.occurredAtMillis, enabled, state.error == JournalEditorError.TIME, onTime)
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.journal_delete_title)) },
        text = { Text(stringResource(R.string.journal_delete_body)) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }, modifier = Modifier.testTag("journal_confirm_delete")) {
            Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    if (discarding) AlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.journal_discard_title)) },
        text = { Text(stringResource(R.string.journal_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}
