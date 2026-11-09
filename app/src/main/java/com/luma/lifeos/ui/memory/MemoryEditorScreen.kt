package com.luma.lifeos.ui.memory

import com.luma.lifeos.ui.common.LumaUi

import com.luma.lifeos.ui.common.LumaTextButton

import com.luma.lifeos.ui.common.LumaFilterChip
import com.luma.lifeos.ui.common.LumaInputChip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.MemoryTextLimits
import com.luma.lifeos.domain.model.Tag
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
import com.luma.lifeos.ui.common.LumaEditorActionFooter
import com.luma.lifeos.ui.common.LumaPrimaryActionButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryEditorScreen(state: MemoryEditorState, onCancel: () -> Unit, onSave: () -> Unit, onRemove: () -> Unit,
    onRetry: () -> Unit, onTitle: (String) -> Unit, onNote: (String) -> Unit,
    onImportance: (Int?) -> Unit, onTime: (LocalDateTime, ZoneId) -> Boolean,
    onContextNote: (String) -> Unit = {}, onAddTag: (String) -> Boolean = { false },
    onRemoveTag: (String) -> Unit = {}, peopleContent: @Composable () -> Unit = {},
    placeContent: @Composable () -> Unit = {}, photosContent: @Composable () -> Unit = {},
    onShare: (() -> Unit)? = null, shareEnabled: Boolean = false, photoOperationActive: Boolean = false) {
    var removing by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var tagText by rememberSaveable { mutableStateOf("") }
    var tagError by rememberSaveable { mutableStateOf(false) }
    var tagsExpanded by rememberSaveable { mutableStateOf(false) }
    var tagsExpansionInitialized by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var contextExpansionInitialized by rememberSaveable { mutableStateOf(false) }
    var importanceExpanded by rememberSaveable { mutableStateOf(false) }
    var importanceExpansionInitialized by rememberSaveable { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    val photosBringIntoViewRequester = remember { BringIntoViewRequester() }
    val tagInputBringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val enabled = state.ready && !state.busy && !state.completed && !photoOperationActive
    fun close() {
        if (state.busy) return
        if (state.hasChanges || tagText.isNotBlank()) discarding = true else onCancel()
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
    LaunchedEffect(state.ready, state.draft.tagNames, tagText, tagError) {
        if (state.ready && !tagsExpansionInitialized) {
            tagsExpanded = state.draft.tagNames.isNotEmpty() || tagText.isNotBlank() || tagError
            tagsExpansionInitialized = true
        }
    }
    LaunchedEffect(state.error) {
        if (state.error == MemoryEditorError.PHOTO) more = true
    }
    LaunchedEffect(state.error, more) {
        if (state.error == MemoryEditorError.PHOTO && more) photosBringIntoViewRequester.bringIntoView()
    }
    fun addPendingTag(): Boolean {
        if (tagText.isBlank()) return true
        if (onAddTag(tagText)) {
            tagText = ""
            tagError = false
            return true
        }
        tagError = true
        tagsExpanded = true
        return false
    }
    BackHandler(enabled = !photoOperationActive) { close() }
    Scaffold(modifier = Modifier.testTag("screen_memory"), contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        EditorTopBar(stringResource(if (state.editing) R.string.memory_edit else if (state.direct) R.string.memory_capture else R.string.memory_promote),
            "memory_cancel", !state.busy && !photoOperationActive, ::close)
    }, bottomBar = {
        LumaEditorActionFooter {
                val actionError = when (state.error) {
                    MemoryEditorError.SAVE -> stringResource(R.string.memory_save_error)
                    MemoryEditorError.REMOVE -> stringResource(R.string.memory_remove_error)
                    else -> null
                }
                actionError?.let { AccessibleFeedback(it, Modifier.testTag("memory_action_error")) }
                if (onShare != null && state.editing) {
                    LumaTextButton(onClick = onShare, enabled = shareEnabled && !photoOperationActive) { Text(stringResource(R.string.share_action)) }
                    if (!shareEnabled) AccessibleFeedback(stringResource(R.string.share_save_first), isError = false)
                }
                EditorActionRow(
                    secondaryAction = if (state.editing) { actionModifier ->
                        LumaTextButton(enabled = enabled, onClick = { removing = true }, modifier = actionModifier.testTag("memory_remove")) {
                            Text(stringResource(if (state.direct) R.string.delete else R.string.memory_unmark))
                        }
                    } else null,
                    primaryAction = { actionModifier ->
                        LumaPrimaryActionButton(enabled = enabled && (!state.direct || state.draft.note.isNotBlank()), onClick = onSave,
                            modifier = actionModifier.testTag("memory_save")) {
                            Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                        }
                    },
                )
        }
    }) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LumaUi.PageGutter),
            verticalArrangement = Arrangement.spacedBy(LumaUi.GroupGap)) {
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
                    if (error == MemoryEditorError.LOAD) LumaTextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
                    stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(
                        if (state.direct) MemoryTextLimits.NOTE else MemoryTextLimits.CONTEXT_NOTE,
                    )) else null
                LumaOutlinedTextField(value = state.draft.note, onValueChange = onNote, enabled = enabled, minLines = 5,
                    label = { Text(stringResource(if (state.direct) R.string.memory_note else R.string.memory_marker_note)) },
                    isError = noteError != null, supportingText = { noteError?.let { AccessibleFeedback(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocus)
                        .semantics { noteError?.let { error(it) } }.testTag("memory_note"))
                if (state.direct && state.draft.contextNote.isNotEmpty()) {
                    val contextError = if (MemoryTextField.CONTEXT_NOTE in state.textErrors)
                        stringResource(R.string.editor_text_limit_error, NumberFormat.getIntegerInstance().format(MemoryTextLimits.CONTEXT_NOTE)) else null
                    LumaOutlinedTextField(value = state.draft.contextNote, onValueChange = onContextNote, enabled = enabled, minLines = 2,
                        label = { Text(stringResource(R.string.memory_context_note)) },
                        isError = contextError != null, supportingText = { contextError?.let { AccessibleFeedback(it) } },
                        modifier = Modifier.fillMaxWidth().semantics { contextError?.let { error(it) } }.testTag("memory_context_note"))
                }
                Column(Modifier.testTag("memory_tags")) {
                    EditorOptionalSection(
                        title = R.string.memory_tags,
                        tag = "memory_tags_toggle",
                        expanded = tagsExpanded,
                        onExpandedChange = { tagsExpansionInitialized = true; tagsExpanded = it },
                        summary = if (state.draft.tagNames.isEmpty()) null else {
                            {
                                Text(
                                    pluralStringResource(R.plurals.memory_tags_summary, state.draft.tagNames.size, state.draft.tagNames.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                            state.draft.tagNames.forEach { name ->
                                LumaInputChip(selected = true, enabled = enabled, onClick = { onRemoveTag(name) },
                                    label = { Text(stringResource(R.string.memory_remove_tag, name)) })
                            }
                        }
                        val tagFeedback = if (tagError) stringResource(R.string.memory_tag_size_error) else null
                        Row(horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                            LumaOutlinedTextField(value = tagText, onValueChange = {
                                when {
                                    it.length > MemoryTextLimits.TAG -> tagError = true
                                    else -> { tagText = it; tagError = false }
                                }
                            }, enabled = enabled, singleLine = true, isError = tagFeedback != null,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (tagText.isBlank()) keyboardController?.hide()
                                    else if (addPendingTag()) keyboardController?.hide()
                                }),
                                supportingText = { tagFeedback?.let { AccessibleFeedback(it) } },
                                label = { Text(stringResource(R.string.memory_tag_name)) },
                                modifier = Modifier.weight(1f).bringIntoViewRequester(tagInputBringIntoViewRequester)
                                    .semantics { tagFeedback?.let { error(it) } }.testTag("memory_tag_input"))
                            LumaTextButton(enabled = enabled && tagText.isNotBlank(), onClick = { addPendingTag() },
                                modifier = Modifier.testTag("memory_add_tag")) { Text(stringResource(R.string.add)) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                            state.tags.filter { tag -> state.draft.tagNames.none { Tag.normalize(it) == Tag.normalize(tag.name) } }
                                .forEach { tag ->
                                    LumaFilterChip(selected = false, enabled = enabled, onClick = { onAddTag(tag.name) },
                                        label = { Text(tag.name) })
                                }
                        }
                    }
                }
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                            (1..5).forEach { value ->
                                val description = stringResource(R.string.memory_importance_value, value)
                                LumaFilterChip(selected = state.draft.importance == value, enabled = enabled,
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
                    LumaTextButton(
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
                            verticalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap),
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
        dismissButton = { LumaTextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) } })
    if (discarding) LumaAlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.mood_discard_title)) }, text = { Text(stringResource(R.string.mood_discard_body)) },
        confirmButton = { LumaTextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { LumaTextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}
