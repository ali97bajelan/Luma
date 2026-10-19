package com.luma.lifeos.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.Tag
import com.luma.lifeos.domain.model.JournalTextLimits
import com.luma.lifeos.ui.common.EditorTopBar
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.CaptureTimestampRow
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId

internal fun reduceJournalTagInput(current: String, hasError: Boolean, proposed: String): Pair<String, Boolean> = when {
    proposed.length > JournalTextLimits.TAG -> current to true
    proposed == current -> current to hasError
    else -> proposed to false
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun JournalEditorScreen(state: JournalEditorState, onCancel: () -> Unit, onSave: () -> Unit,
    onDelete: () -> Unit, onRetry: () -> Unit, onTitle: (String) -> Unit, onBody: (String) -> Unit,
    onAddTag: (String) -> Boolean, onRemoveTag: (String) -> Unit,
    onTime: (LocalDateTime, ZoneId) -> Boolean, peopleContent: @Composable () -> Unit = {}, placeContent: @Composable () -> Unit = {}, photosContent: @Composable () -> Unit = {}) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var tagText by rememberSaveable { mutableStateOf("") }
    var tagError by rememberSaveable { mutableStateOf(false) }
    var tagInputBringIntoViewRequest by remember { mutableIntStateOf(0) }
    var more by rememberSaveable { mutableStateOf(false) }
    var contextExpansionInitialized by rememberSaveable { mutableStateOf(false) }
    val tagInputBringIntoViewRequester = remember { BringIntoViewRequester() }
    val photosBringIntoViewRequester = remember { BringIntoViewRequester() }
    val bodyFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val enabled = state.ready && !state.busy && !state.completed
    fun close() {
        if (state.busy) return
        if (state.hasChanges || tagText.isNotBlank()) discarding = true else onCancel()
    }
    LaunchedEffect(state.ready, state.editing, state.draft.personIds, state.draft.placeId, state.draft.photos) {
        if (state.ready && !contextExpansionInitialized) {
            more = state.error == JournalEditorError.PHOTO || state.editing || state.draft.personIds.isNotEmpty() ||
                state.draft.placeId != null || state.draft.photos.isNotEmpty()
            contextExpansionInitialized = true
        }
    }
    LaunchedEffect(state.error) {
        if (state.error == JournalEditorError.PHOTO) more = true
    }
    LaunchedEffect(state.error, more) {
        if (state.error == JournalEditorError.PHOTO && more) photosBringIntoViewRequester.bringIntoView()
    }
    LaunchedEffect(tagInputBringIntoViewRequest) {
        if (tagInputBringIntoViewRequest > 0) tagInputBringIntoViewRequester.bringIntoView()
    }
    fun addPendingTag(): Boolean {
        if (tagText.isBlank()) return true
        if (onAddTag(tagText)) {
            tagText = ""
            tagError = false
            return true
        }
        tagError = true
        tagInputBringIntoViewRequest++
        return false
    }
    BackHandler { close() }
    Scaffold(modifier = Modifier.testTag("screen_journal"), topBar = {
        EditorTopBar(stringResource(if (state.editing) R.string.journal_edit else R.string.journal_capture),
            "journal_cancel", !state.busy, ::close)
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val actionError = when (state.error) {
                JournalEditorError.SAVE -> stringResource(R.string.journal_save_error)
                JournalEditorError.DELETE -> stringResource(R.string.journal_delete_error)
                else -> null
            }
            actionError?.let { AccessibleFeedback(it, Modifier.testTag("journal_error")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.editing) TextButton(enabled = enabled, onClick = { deleting = true }, modifier = Modifier.testTag("journal_delete")) {
                    Text(stringResource(R.string.delete))
                }
                Button(enabled = enabled && state.draft.body.isNotBlank(), onClick = {
                    if (addPendingTag()) onSave()
                }, modifier = Modifier.weight(1f).testTag("journal_save")) {
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
                    JournalEditorError.LOAD -> R.string.journal_load_error
                    JournalEditorError.SAVE -> R.string.journal_save_error
                    JournalEditorError.DELETE -> R.string.journal_delete_error
                    JournalEditorError.TIME -> R.string.mood_time_error
                    JournalEditorError.PHOTO -> R.string.photo_error
                    JournalEditorError.TEXT -> R.string.journal_text_error
                })
                val showBodyError = when {
                    error == JournalEditorError.SAVE || error == JournalEditorError.DELETE -> false
                    error == JournalEditorError.TIME || error == JournalEditorError.PHOTO -> false
                    error == JournalEditorError.TEXT && (state.textErrors.isNotEmpty() || tagError) -> false
                    else -> true
                }
                if (showBodyError) AccessibleFeedback(message, Modifier.testTag("journal_error"))
                if (error == JournalEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (state.ready) CaptureTimestampRow(state.draft.occurredAtMillis, enabled,
                state.error == JournalEditorError.TIME, onTime)
            // Restored text remains visible even if loading fails.
            val titleError = if (JournalTextField.TITLE in state.textErrors)
                stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(JournalTextLimits.TITLE)) else null
            OutlinedTextField(value = state.draft.title, onValueChange = onTitle, enabled = enabled, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                isError = titleError != null,
                supportingText = { titleError?.let { AccessibleFeedback(it) } },
                label = { Text(stringResource(R.string.journal_title)) }, modifier = Modifier.fillMaxWidth()
                    .semantics { titleError?.let { error(it) } }.testTag("journal_title"))
            val bodyError = if (JournalTextField.BODY in state.textErrors)
                stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(JournalTextLimits.BODY)) else null
            OutlinedTextField(value = state.draft.body, onValueChange = onBody, enabled = enabled,
                label = { Text(stringResource(R.string.journal_body)) }, minLines = 8,
                isError = bodyError != null,
                supportingText = {
                    Column {
                        Text(stringResource(R.string.journal_body_hint))
                        bodyError?.let { AccessibleFeedback(it) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth().focusRequester(bodyFocus)
                    .semantics { bodyError?.let { error(it) } }.testTag("journal_body"))
            Text(stringResource(R.string.journal_tags), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }.testTag("journal_tags"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.draft.tagNames.forEach { name ->
                    InputChip(selected = true, enabled = enabled, onClick = { onRemoveTag(name) },
                        label = { Text(stringResource(R.string.journal_remove_tag, name)) })
                }
            }
            val tagFeedback = if (tagError || JournalTextField.TAG in state.textErrors) stringResource(R.string.journal_tag_size_error) else null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = tagText, onValueChange = {
                    val (updatedText, hasError) = reduceJournalTagInput(tagText, tagError, it)
                    tagText = updatedText
                    tagError = hasError
                }, enabled = enabled, singleLine = true, isError = tagFeedback != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (tagText.isBlank()) keyboardController?.hide()
                        else if (addPendingTag()) keyboardController?.hide()
                    }),
                    supportingText = { tagFeedback?.let { AccessibleFeedback(it) } },
                    label = { Text(stringResource(R.string.journal_tag_name)) },
                    modifier = Modifier.weight(1f).bringIntoViewRequester(tagInputBringIntoViewRequester)
                        .semantics { tagFeedback?.let { error(it) } }.testTag("journal_tag_input"))
                TextButton(enabled = enabled && tagText.isNotBlank(), onClick = {
                    addPendingTag()
                }, modifier = Modifier.testTag("journal_add_tag")) { Text(stringResource(R.string.add)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.filter { tag -> state.draft.tagNames.none { Tag.normalize(it) == Tag.normalize(tag.name) } }.forEach { tag ->
                    FilterChip(selected = false, enabled = enabled, onClick = { onAddTag(tag.name) }, label = { Text(tag.name) })
                }
            }
            val disclosureState = stringResource(if (more) R.string.review_details_expanded else R.string.review_details_collapsed)
            TextButton(onClick = { more = !more }, enabled = enabled && contextExpansionInitialized,
                modifier = Modifier.testTag("journal_more").semantics { stateDescription = disclosureState }) {
                Text(stringResource(if (more) R.string.capture_fewer_details else R.string.capture_more_details))
            }
            Text(stringResource(R.string.capture_context_help), style = MaterialTheme.typography.bodySmall)
            if (more) {
                peopleContent()
                placeContent()
                Column(Modifier.bringIntoViewRequester(photosBringIntoViewRequester)) { photosContent() }
            }
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
