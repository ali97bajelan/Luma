package com.luma.lifeos.ui.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.MemoryTextLimits
import com.luma.lifeos.ui.common.EditorTopBar
import com.luma.lifeos.ui.common.EditorActionRow
import com.luma.lifeos.ui.common.EditorOptionalSection
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.CaptureTimestampRow
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaDestructiveTextButton

@Composable
fun MemoryEditorScreen(state: MemoryEditorState, onCancel: () -> Unit, onSave: () -> Unit, onRemove: () -> Unit,
    onRetry: () -> Unit, onTitle: (String) -> Unit, onNote: (String) -> Unit, onImportance: (Int?) -> Unit,
    onTime: (LocalDateTime, ZoneId) -> Boolean, peopleContent: @Composable () -> Unit = {}, placeContent: @Composable () -> Unit = {}, photosContent: @Composable () -> Unit = {}) {
    var removing by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var contextExpansionInitialized by rememberSaveable { mutableStateOf(false) }
    var importanceExpanded by rememberSaveable { mutableStateOf(false) }
    var importanceExpansionInitialized by rememberSaveable { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    val photosBringIntoViewRequester = remember { BringIntoViewRequester() }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() {
        if (state.busy) return
        if (state.hasChanges) discarding = true else onCancel()
    }
    LaunchedEffect(state.ready, state.editing, state.original, state.draft.personIds, state.draft.placeId, state.draft.photos) {
        if (state.ready && !contextExpansionInitialized) {
            more = state.error == MemoryEditorError.PHOTO || state.editing || state.original != null || state.draft.personIds.isNotEmpty() ||
                state.draft.placeId != null || state.draft.photos.isNotEmpty()
            contextExpansionInitialized = true
        }
    }
    LaunchedEffect(state.ready, state.draft.importance) {
        if (state.ready && !importanceExpansionInitialized) {
            importanceExpanded = state.draft.importance != null
            importanceExpansionInitialized = true
        }
    }
    LaunchedEffect(state.error) {
        if (state.error == MemoryEditorError.PHOTO) more = true
    }
    LaunchedEffect(state.error, more) {
        if (state.error == MemoryEditorError.PHOTO && more) photosBringIntoViewRequester.bringIntoView()
    }
    BackHandler { close() }
    Scaffold(modifier = Modifier.testTag("screen_memory"), contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        EditorTopBar(stringResource(if (state.editing) R.string.memory_edit else if (state.direct) R.string.memory_capture else R.string.memory_promote),
            "memory_cancel", !state.busy, ::close)
    }, bottomBar = {
        LumaReadingPane(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val actionError = when (state.error) {
                    MemoryEditorError.SAVE -> stringResource(R.string.memory_save_error)
                    MemoryEditorError.REMOVE -> stringResource(R.string.memory_remove_error)
                    else -> null
                }
                actionError?.let { AccessibleFeedback(it, Modifier.testTag("memory_action_error")) }
                EditorActionRow(
                    secondaryAction = if (state.editing) { actionModifier ->
                        TextButton(enabled = enabled, onClick = { removing = true }, modifier = actionModifier.testTag("memory_remove")) {
                            Text(stringResource(if (state.direct) R.string.delete else R.string.memory_unmark))
                        }
                    } else null,
                    primaryAction = { actionModifier ->
                        Button(enabled = enabled && (!state.direct || state.draft.note.isNotBlank()), onClick = onSave,
                            modifier = actionModifier.heightIn(min = 56.dp).testTag("memory_save"), shape = CircleShape) {
                            Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                        }
                    },
                )
            }
        }
    }) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (state.loading) CircularProgressIndicator()
                state.error?.let { error ->
                    val message = stringResource(when (error) {
                        MemoryEditorError.LOAD -> R.string.memory_load_error
                        MemoryEditorError.SAVE -> R.string.memory_save_error
                        MemoryEditorError.REMOVE -> R.string.memory_remove_error
                        MemoryEditorError.TIME -> R.string.mood_time_error
                        MemoryEditorError.PHOTO -> R.string.photo_error
                        MemoryEditorError.TEXT -> R.string.memory_text_error
                    })
                    val showBodyError = when {
                        error == MemoryEditorError.SAVE || error == MemoryEditorError.REMOVE -> false
                        error == MemoryEditorError.TIME || error == MemoryEditorError.PHOTO -> false
                        error == MemoryEditorError.TEXT && state.textErrors.isNotEmpty() -> false
                        else -> true
                    }
                    if (showBodyError) AccessibleFeedback(message, Modifier.testTag("memory_error"))
                    if (error == MemoryEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                if (state.ready) CaptureTimestampRow(state.draft.occurredAtMillis, enabled && state.direct,
                    state.error == MemoryEditorError.TIME, onTime)
                state.original?.let { original ->
                    LumaSectionSurface {
                        Text(stringResource(R.string.memory_original), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }.testTag("memory_original_heading"))
                        original.event.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        Text(original.journal?.body ?: original.mood?.note ?: original.event.note.orEmpty(),
                            modifier = Modifier.testTag("memory_original"))
                        original.mood?.let { Text(stringResource(R.string.memory_mood_score, it.score)) }
                        Text(stringResource(R.string.memory_source_kept), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val titleError = if (MemoryTextField.TITLE in state.textErrors)
                    stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(MemoryTextLimits.TITLE)) else null
                if (state.direct) LumaOutlinedTextField(value = state.draft.title, onValueChange = onTitle, enabled = enabled, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                    isError = titleError != null, supportingText = { titleError?.let { AccessibleFeedback(it) } },
                    label = { Text(stringResource(R.string.memory_title)) }, modifier = Modifier.fillMaxWidth()
                        .semantics { titleError?.let { error(it) } }.testTag("memory_title"))
                val noteError = if (MemoryTextField.NOTE in state.textErrors)
                    stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(MemoryTextLimits.NOTE)) else null
                LumaOutlinedTextField(value = state.draft.note, onValueChange = onNote, enabled = enabled, minLines = 5,
                    label = { Text(stringResource(if (state.direct) R.string.memory_note else R.string.memory_marker_note)) },
                    isError = noteError != null, supportingText = { noteError?.let { AccessibleFeedback(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocus)
                        .semantics { noteError?.let { error(it) } }.testTag("memory_note"))
                Column(Modifier.testTag("memory_importance")) {
                    EditorOptionalSection(
                        title = R.string.memory_importance,
                        tag = "memory_importance_toggle",
                        expanded = importanceExpanded,
                        onExpandedChange = { importanceExpanded = it },
                        summary = state.draft.importance?.let { value ->
                            {
                                Text(
                                    stringResource(
                                        R.string.memory_importance_summary,
                                        NumberFormat.getIntegerInstance().format(value),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.mood_scale_hint), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { value ->
                                val description = stringResource(R.string.memory_importance_value, value)
                                FilterChip(selected = state.draft.importance == value, enabled = enabled,
                                    onClick = { onImportance(if (state.draft.importance == value) null else value) },
                                    label = { Text(NumberFormat.getIntegerInstance().format(value)) },
                                    modifier = Modifier.weight(1f).testTag("memory_importance_$value")
                                        .semantics { contentDescription = description })
                            }
                        }
                    }
                }
                LumaSectionSurface {
                    val disclosureState = stringResource(
                        if (more) R.string.review_details_expanded else R.string.review_details_collapsed,
                    )
                    TextButton(
                        onClick = { more = !more },
                        enabled = enabled && contextExpansionInitialized,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { heading(); stateDescription = disclosureState }.testTag("memory_more"),
                    ) {
                        Text(
                            stringResource(if (more) R.string.capture_fewer_details else R.string.capture_more_details),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(stringResource(R.string.capture_context_help), style = MaterialTheme.typography.bodySmall)
                    if (more) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            peopleContent()
                            placeContent()
                            Column(Modifier.bringIntoViewRequester(photosBringIntoViewRequester)) {
                                photosContent()
                            }
                        }
                    }
                }
            }
        }
    }
    if (removing) LumaAlertDialog(onDismissRequest = { removing = false },
        title = { Text(stringResource(if (state.direct) R.string.memory_delete_title else R.string.memory_unmark_title)) },
        text = { Text(stringResource(if (state.direct) R.string.memory_delete_body else R.string.memory_unmark_body)) },
        confirmButton = { LumaDestructiveTextButton(onClick = { removing = false; onRemove() }, modifier = Modifier.testTag("memory_confirm_remove")) {
            Text(stringResource(if (state.direct) R.string.delete else R.string.memory_unmark)) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) } })
    if (discarding) LumaAlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.mood_discard_title)) }, text = { Text(stringResource(R.string.mood_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}
