package com.luma.lifeos.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoField
import com.luma.lifeos.domain.todo.TodoTextLimits
import com.luma.lifeos.domain.todo.TodoValidationReason
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.EditorTopBar
import com.luma.lifeos.ui.common.EditorActionRow
import com.luma.lifeos.ui.today.pickerDate
import com.luma.lifeos.ui.today.pickerMillis
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.FormatStyle
import java.util.UUID
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaDestructiveTextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(
    state: TodoUiState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onTitle: (String) -> Unit,
    onNote: (String) -> Unit,
    onDueDate: (LocalDate?) -> Unit,
    onSetCompleted: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDueTime: (LocalTime?) -> Unit = {},
) {
    var discarding by rememberSaveable { mutableStateOf(false) }
    var reloadConfirmation by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var timePickerSession by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var pickerStartDate by rememberSaveable { mutableStateOf("") }
    val titleFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val noteBringIntoView = remember { BringIntoViewRequester() }
    val timeFocus = remember { FocusRequester() }
    val timeBringIntoView = remember { BringIntoViewRequester() }
    val displayTime = rememberDisplayTimePolicy()
    val canEdit = state.ready && !state.busy && !state.finished && !state.conflict && !state.missing &&
        state.pendingAction != "complete" && state.pendingAction != "reopen" && state.pendingAction != "delete"
    val readOnly = !canEdit

    fun close() {
        if (state.busy) return
        if (state.dirty) discarding = true else onClose()
    }

    LaunchedEffect(state.ready, state.editing) {
        if (state.ready && !state.editing && !state.conflict && !state.missing) titleFocus.requestFocus()
    }
    LaunchedEffect(state.fieldErrors) {
        when {
            TodoField.TITLE in state.fieldErrors -> titleFocus.requestFocus()
            TodoField.NOTE in state.fieldErrors -> {
                noteFocus.requestFocus()
                noteBringIntoView.bringIntoView()
            }
            TodoField.DUE_TIME in state.fieldErrors -> {
                timeFocus.requestFocus()
                timeBringIntoView.bringIntoView()
            }
        }
    }
    LaunchedEffect(canEdit, state.draft.dueDate) {
        if (!canEdit || state.draft.dueDate == null) pickingTime = false
    }

    // The picker gets first chance at Back; the editor consumes Back while a write is pending.
    BackHandler { if (!state.busy) close() }
    BackHandler(enabled = pickingDate) { pickingDate = false }
    BackHandler(enabled = pickingTime) { pickingTime = false }

    Scaffold(
        modifier = Modifier.testTag("screen_todo_editor"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EditorTopBar(
                stringResource(if (state.editing) R.string.todo_edit_title else R.string.todo_new_title),
                "todo_editor_close",
                !state.busy,
                ::close,
            )
        },
        bottomBar = {
            LumaReadingPane(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.error?.let { error ->
                        val message = stringResource(
                            when (error) {
                                TodoEditorError.LOAD -> R.string.todo_load_error
                                TodoEditorError.SAVE -> R.string.todo_save_error
                                TodoEditorError.STATUS -> R.string.todo_status_error
                                TodoEditorError.DELETE -> R.string.todo_delete_error
                            },
                        )
                        AccessibleFeedback(message, Modifier.testTag("todo_editor_action_error"))
                        TextButton(
                            onClick = onRetry,
                            enabled = !state.busy,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("todo_editor_retry"),
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    EditorActionRow(
                        secondaryAction = if (state.editing) { actionModifier ->
                            TextButton(
                                onClick = onRequestDelete,
                                enabled = canEdit && state.pendingAction == null,
                                modifier = actionModifier.testTag("todo_editor_delete"),
                            ) { Text(stringResource(R.string.delete)) }
                        } else null,
                        primaryAction = { actionModifier ->
                            Button(
                                onClick = onSave,
                                enabled = canEdit,
                                modifier = actionModifier.heightIn(min = 56.dp).testTag("todo_editor_save"),
                                shape = CircleShape,
                            ) {
                                Text(
                                    when {
                                        state.busy && state.pendingAction == "delete" -> stringResource(R.string.todo_deleting)
                                        state.busy && state.pendingAction == "complete" -> stringResource(R.string.todo_completing)
                                        state.busy && state.pendingAction == "reopen" -> stringResource(R.string.todo_reopening)
                                        state.busy -> stringResource(R.string.saving)
                                        else -> stringResource(R.string.save)
                                    },
                                )
                            }
                        },
  )
                }
            }
        },
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (state.loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.todo_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (state.conflict) {
                    AccessibleFeedback(stringResource(R.string.todo_conflict), Modifier.testTag("todo_editor_conflict"))
                    TextButton(
                        onClick = {
                            if (state.dirty && !state.canRetryConfirmedReload) reloadConfirmation = true else onReload()
                        },
                        enabled = !state.busy && !state.loading,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("todo_editor_reload"),
                    ) { Text(stringResource(R.string.todo_reload_latest)) }
                }
                if (state.missing) {
                    AccessibleFeedback(stringResource(R.string.todo_missing), Modifier.testTag("todo_editor_missing"))
                }
                val titleError = titleErrorText(state)
                LumaOutlinedTextField(
                    value = state.draft.title,
                    onValueChange = onTitle,
                    enabled = state.ready && !state.busy,
                    readOnly = readOnly,
                    singleLine = true,
                    minLines = 1,
                    maxLines = 1,
                    isError = titleError != null,
                    supportingText = {
                        Column {
                            Text(stringResource(R.string.todo_title_hint))
                            titleError?.let { AccessibleFeedback(it) }
                        }
                    },
                    label = { Text(stringResource(R.string.todo_title_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus)
                        .semantics { titleError?.let { error(it) } }.testTag("todo_editor_title"),
                )

                val noteError = noteErrorText(state)
                LumaOutlinedTextField(
                    value = state.draft.note,
                    onValueChange = onNote,
                    enabled = state.ready && !state.busy,
                    readOnly = readOnly,
                    minLines = 4,
                    isError = noteError != null,
                    supportingText = {
                        Column {
                            Text(stringResource(R.string.todo_note_hint))
                            noteError?.let { AccessibleFeedback(it) }
                        }
                    },
                    label = { Text(stringResource(R.string.todo_note_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocus)
                        .bringIntoViewRequester(noteBringIntoView)
                        .semantics { noteError?.let { error(it) } }.testTag("todo_editor_note"),
                )

                val dueDateError = if (state.fieldErrors[TodoField.DUE_DATE] == TodoValidationReason.OUT_OF_RANGE) {
                    stringResource(R.string.todo_date_out_of_range)
                } else null
                val dueText = state.draft.dueDate?.let { date ->
                    val formatted = date.format(displayTime.date(FormatStyle.LONG))
                    if (date == LocalDate.now()) stringResource(R.string.todo_due_today, formatted) else formatted
                } ?: stringResource(R.string.todo_no_date)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("todo_editor_due_date_card"),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (dueDateError == null) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.error,
                        ),
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.todo_due_date_label),
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                dueText,
                                modifier = Modifier.fillMaxWidth()
                                    .semantics { dueDateError?.let { error(it) } }
                                    .testTag("todo_editor_due_date_value"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        pickerStartDate = (state.draft.dueDate ?: LocalDate.now()).toString()
                                        pickingDate = true
                                    },
                                    enabled = canEdit,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                        .testTag("todo_editor_change_due_date"),
                                ) { Text(stringResource(R.string.todo_change_date)) }
                                if (state.draft.dueDate != null) {
                                    TextButton(
                                        onClick = { onDueDate(null) },
                                        enabled = canEdit,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                            .testTag("todo_editor_clear_due_date"),
                                    ) { Text(stringResource(R.string.todo_remove_date)) }
                                }
                            }
                        }
                    }
                    dueDateError?.let { AccessibleFeedback(it) }
                }

                val dueTimeError = if (state.fieldErrors[TodoField.DUE_TIME] == TodoValidationReason.OUT_OF_RANGE) {
                    stringResource(R.string.todo_time_out_of_range)
                } else null
                if (state.draft.dueDate != null) {
                    Column(
                        Modifier.fillMaxWidth().testTag("todo_editor_due_time_row"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.todo_due_time_label),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val dueTimeText = state.draft.dueTime?.let { displayTime.time().format(it) }
                            ?: stringResource(R.string.todo_no_time)
                        Text(
                            dueTimeText,
                            modifier = Modifier.fillMaxWidth()
                                .semantics { dueTimeError?.let { error(it) } }
                                .focusRequester(timeFocus)
                                .focusable()
                                .bringIntoViewRequester(timeBringIntoView)
                                .testTag("todo_editor_due_time_value"),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    timePickerSession = UUID.randomUUID().toString()
                                    pickingTime = true
                                },
                                enabled = canEdit,
                                modifier = Modifier.heightIn(min = 48.dp).testTag(
                                    if (state.draft.dueTime == null) "todo_editor_add_due_time" else "todo_editor_change_due_time",
                                ),
                            ) {
                                Text(stringResource(if (state.draft.dueTime == null) R.string.todo_add_time else R.string.todo_change_time))
                            }
                            if (state.draft.dueTime != null) {
                                TextButton(
                                    onClick = { onDueTime(null) },
                                    enabled = canEdit,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("todo_editor_clear_due_time"),
                                ) { Text(stringResource(R.string.todo_remove_time)) }
                            }
                        }
                        dueTimeError?.let { AccessibleFeedback(it, Modifier.testTag("todo_due_time_feedback")) }
                    }
                }

                if (state.editing) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(if (state.taskCompleted) R.string.todo_status_completed else R.string.todo_status_open),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val statusEnabled = canEdit && !state.dirty && state.pendingAction == null
                        OutlinedButton(
                            onClick = { onSetCompleted(!state.taskCompleted) },
                            enabled = statusEnabled,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("todo_editor_status"),
                        ) {
                            Text(stringResource(if (state.taskCompleted) R.string.todo_reopen else R.string.todo_mark_complete))
                        }
                        if (state.dirty) {
                            AccessibleFeedback(
                                stringResource(if (state.taskCompleted) R.string.todo_save_before_reopen else R.string.todo_save_before_complete),
                                Modifier.testTag("todo_editor_status_help"),
                                isError = false,
                            )
                        }
                    }
                }
                Spacer(Modifier.heightIn(min = 4.dp))
            }
        }
    }

    if (state.deleteConfirmationRequested) {
        val deleteBody = stringResource(
            if (state.dirty) R.string.todo_delete_dirty_body else R.string.todo_delete_body,
            state.baselineTitle.ifEmpty { state.draft.title },
        )
        LumaAlertDialog(
            onDismissRequest = onCancelDelete,
            modifier = Modifier.testTag("todo_editor_delete_dialog"),
            title = { Text(stringResource(R.string.todo_delete_title)) },
            text = { Text(deleteBody) },
            confirmButton = {
                LumaDestructiveTextButton(onClick = onConfirmDelete, modifier = Modifier.testTag("todo_editor_confirm_delete")) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (discarding) {
        LumaAlertDialog(
            onDismissRequest = { discarding = false },
            modifier = Modifier.testTag("todo_editor_discard_dialog"),
            title = { Text(stringResource(R.string.todo_discard_title)) },
            text = { Text(stringResource(R.string.todo_discard_body)) },
            confirmButton = {
                TextButton(onClick = { discarding = false; onClose() }, modifier = Modifier.testTag("todo_editor_confirm_discard")) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    if (reloadConfirmation) {
        LumaAlertDialog(
            onDismissRequest = { reloadConfirmation = false },
            modifier = Modifier.testTag("todo_editor_reload_dialog"),
            title = { Text(stringResource(R.string.todo_reload_title)) },
            text = { Text(stringResource(R.string.todo_reload_dirty_body)) },
            confirmButton = {
                TextButton(onClick = { reloadConfirmation = false; onReload() }, modifier = Modifier.testTag("todo_editor_confirm_reload")) {
                    Text(stringResource(R.string.todo_reload_latest))
                }
            },
            dismissButton = {
                TextButton(onClick = { reloadConfirmation = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    if (pickingDate) {
        val pickerDate = pickerStartDate.toLocalDateOrNull() ?: state.draft.dueDate ?: LocalDate.now()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = pickerMillis(pickerDate),
            yearRange = minOf(1900, pickerDate.year)..maxOf(2100, pickerDate.year),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            modifier = Modifier.testTag("todo_editor_date_dialog"),
            shape = MaterialTheme.shapes.large,
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            tonalElevation = 0.dp,
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let { onDueDate(pickerDate(it)) }
                        pickingDate = false
                    },
                    modifier = Modifier.testTag("todo_editor_select_date"),
                ) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state = picker) }
    }
    if (pickingTime && state.draft.dueDate != null) {
        key(timePickerSession) {
            val committedTime = state.draft.dueTime ?: LocalTime.of(9, 0)
            val picker = rememberTimePickerState(
                initialHour = committedTime.hour,
                initialMinute = committedTime.minute,
                is24Hour = displayTime.use24Hour,
            )
            val validTime = picker.hour in 0..23 && picker.minute in 0..59
            LumaAlertDialog(
                onDismissRequest = { pickingTime = false },
                modifier = Modifier.testTag("todo_editor_time_dialog"),
                title = { Text(stringResource(R.string.todo_due_time_label)) },
                text = { TimeInput(state = picker, modifier = Modifier.testTag("todo_editor_time_input")) },
                confirmButton = {
                    TextButton(
                        enabled = validTime && canEdit,
                        onClick = {
                            if (validTime && canEdit) {
                                onDueTime(LocalTime.of(picker.hour, picker.minute))
                                pickingTime = false
                            }
                        },
                        modifier = Modifier.testTag("todo_editor_select_time"),
                    ) { Text(stringResource(R.string.select_time)) }
                },
                dismissButton = {
                    TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}

@Composable
private fun titleErrorText(state: TodoUiState): String? = when (state.fieldErrors[TodoField.TITLE]) {
    TodoValidationReason.REQUIRED -> stringResource(R.string.todo_title_required)
    TodoValidationReason.LINE_BREAKS_NOT_ALLOWED -> stringResource(R.string.todo_title_line_break)
    TodoValidationReason.TOO_LONG -> stringResource(
        R.string.todo_title_too_long,
        NumberFormat.getIntegerInstance().format(TodoTextLimits.TITLE),
    )
    TodoValidationReason.OUT_OF_RANGE -> stringResource(R.string.todo_date_out_of_range)
    null -> null
}

@Composable
private fun noteErrorText(state: TodoUiState): String? = when (state.fieldErrors[TodoField.NOTE]) {
    TodoValidationReason.TOO_LONG -> stringResource(
        R.string.todo_note_too_long,
        NumberFormat.getIntegerInstance().format(TodoTextLimits.NOTE),
    )
    else -> null
}

private fun String.toLocalDateOrNull(): LocalDate? =
    takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
