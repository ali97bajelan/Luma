package com.luma.lifeos.ui.memory

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
import com.luma.lifeos.ui.mood.MoodTimeControls
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryEditorScreen(state: MemoryEditorState, onCancel: () -> Unit, onSave: () -> Unit, onRemove: () -> Unit,
    onRetry: () -> Unit, onTitle: (String) -> Unit, onNote: (String) -> Unit, onImportance: (Int?) -> Unit,
    onTime: (LocalDateTime, ZoneId) -> Boolean, peopleContent: @Composable () -> Unit = {}) {
    var removing by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() {
        if (state.busy) return
        if (state.hasChanges) discarding = true else onCancel()
    }
    BackHandler { close() }
    Scaffold(modifier = Modifier.testTag("screen_memory"), bottomBar = {
        Row(Modifier.fillMaxWidth().imePadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !state.busy, onClick = { close() }) { Text(stringResource(R.string.cancel)) }
            if (state.editing) TextButton(enabled = enabled, onClick = { removing = true }, modifier = Modifier.testTag("memory_remove")) {
                Text(stringResource(if (state.direct) R.string.delete else R.string.memory_unmark))
            }
            Button(enabled = enabled && (!state.direct || state.draft.note.isNotBlank()), onClick = onSave,
                modifier = Modifier.weight(1f).testTag("memory_save")) {
                Text(stringResource(if (state.busy) R.string.saving else R.string.save))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (state.editing) R.string.memory_edit else if (state.direct) R.string.memory_capture else R.string.memory_promote),
                style = MaterialTheme.typography.headlineMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    MemoryEditorError.LOAD -> R.string.memory_load_error
                    MemoryEditorError.SAVE -> R.string.memory_save_error
                    MemoryEditorError.REMOVE -> R.string.memory_remove_error
                    MemoryEditorError.TIME -> R.string.mood_time_error
                    MemoryEditorError.TEXT -> R.string.memory_text_error
                }), color = MaterialTheme.colorScheme.error)
                if (error == MemoryEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            state.original?.let { original ->
                Text(stringResource(R.string.memory_original), style = MaterialTheme.typography.titleMedium)
                original.event.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                Text(original.journal?.body ?: original.mood?.note ?: original.event.note.orEmpty(), modifier = Modifier.testTag("memory_original"))
                original.mood?.let { Text(stringResource(R.string.memory_mood_score, it.score)) }
                Text(Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)))
                Text(stringResource(R.string.memory_source_kept), style = MaterialTheme.typography.bodySmall)
            }
            if (state.direct) OutlinedTextField(value = state.draft.title, onValueChange = onTitle, enabled = enabled,
                label = { Text(stringResource(R.string.memory_title)) }, modifier = Modifier.fillMaxWidth().testTag("memory_title"))
            OutlinedTextField(value = state.draft.note, onValueChange = onNote, enabled = enabled, minLines = 4,
                label = { Text(stringResource(if (state.direct) R.string.memory_note else R.string.memory_marker_note)) },
                modifier = Modifier.fillMaxWidth().testTag("memory_note"))
            Text(stringResource(R.string.memory_importance), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.mood_scale_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { value ->
                    FilterChip(selected = state.draft.importance == value, enabled = enabled,
                        onClick = { onImportance(if (state.draft.importance == value) null else value) },
                        label = { Text(stringResource(R.string.memory_importance_value, value)) },
                        modifier = Modifier.testTag("memory_importance_$value"))
                }
            }
            peopleContent()
            if (state.direct) MoodTimeControls(state.draft.occurredAtMillis, enabled, state.error == MemoryEditorError.TIME, onTime)
        }
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false },
        title = { Text(stringResource(if (state.direct) R.string.memory_delete_title else R.string.memory_unmark_title)) },
        text = { Text(stringResource(if (state.direct) R.string.memory_delete_body else R.string.memory_unmark_body)) },
        confirmButton = { TextButton(onClick = { removing = false; onRemove() }, modifier = Modifier.testTag("memory_confirm_remove")) {
            Text(stringResource(if (state.direct) R.string.delete else R.string.memory_unmark)) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) } })
    if (discarding) AlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.mood_discard_title)) }, text = { Text(stringResource(R.string.mood_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}
