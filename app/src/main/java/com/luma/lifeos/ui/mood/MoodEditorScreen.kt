package com.luma.lifeos.ui.mood

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.language.emotionDisplayName
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.ui.common.EditorTopBar
import com.luma.lifeos.ui.common.EditorActionRow
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.CaptureTimestampRow
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaDestructiveTextButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodEditorScreen(
    state: MoodEditorState,
    editing: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onScore: (Int) -> Unit,
    onEmotion: (UUID) -> Unit,
    onEnergy: (Int?) -> Unit,
    onStress: (Int?) -> Unit,
    onNote: (String) -> Unit,
    onTime: (LocalDateTime, ZoneId) -> Boolean,
    peopleContent: @Composable () -> Unit = {},
    placeContent: @Composable () -> Unit = {},
    onRetryDraft: () -> Unit = {},
) {
    var more by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() {
        if (state.busy) return
        if (state.hasChanges) discarding = true else onCancel()
    }
    var contextExpansionInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.ready, editing, state.draft.personIds, state.draft.placeId) {
        if (state.ready && !contextExpansionInitialized) {
            more = editing || state.draft.personIds.isNotEmpty() || state.draft.placeId != null
            contextExpansionInitialized = true
        }
    }
    BackHandler { close() }
    Scaffold(
        modifier = Modifier.testTag("screen_mood"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EditorTopBar(stringResource(if (editing) R.string.mood_edit else R.string.mood_capture), "mood_cancel", !state.busy, ::close)
        },
        bottomBar = {
            LumaReadingPane(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val actionError = when (state.error) {
                        MoodEditorError.SAVE -> stringResource(R.string.mood_save_error)
                        MoodEditorError.DELETE -> stringResource(R.string.mood_delete_error)
                        MoodEditorError.DELETE_PREPARATION -> stringResource(R.string.mood_delete_preparation_error)
                        else -> null
                    }
                    actionError?.let { AccessibleFeedback(it, Modifier.testTag("mood_error")) }
                    EditorActionRow(
                        secondaryAction = if (editing) { actionModifier ->
                            TextButton(enabled = enabled && state.draftDurable && !state.draftPersisting,
                                onClick = { deleting = true }, modifier = actionModifier.testTag("mood_delete")) {
                                Text(stringResource(if (state.deleteRecovery == MoodDeleteRecovery.STILL_PRESENT)
                                    R.string.mood_delete_retry else R.string.delete))
                            }
                        } else null,
                        primaryAction = { actionModifier ->
                            Button(enabled = enabled && state.draft.score != null && state.draftDurable && !state.draftPersisting,
                                onClick = onSave, modifier = actionModifier.heightIn(min = 56.dp).testTag("mood_save"),
                                shape = CircleShape) {
                                Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val showRecoveryContent = !state.ready &&
                    (state.hasRestoredDraft || state.deleteRecovery in setOf(
                        MoodDeleteRecovery.STATUS_UNKNOWN,
                        MoodDeleteRecovery.STILL_PRESENT,
                        MoodDeleteRecovery.COMPLETION_PENDING,
                        MoodDeleteRecovery.INTENT_MISMATCH,
                    ))
                if (state.loading) CircularProgressIndicator(Modifier.testTag("mood_loading"))
                val deleteRecoveryMessage = when (state.deleteRecovery) {
                    MoodDeleteRecovery.CHECKING -> R.string.mood_delete_checking
                    MoodDeleteRecovery.STATUS_UNKNOWN -> R.string.mood_delete_status_unknown
                    MoodDeleteRecovery.STILL_PRESENT -> R.string.mood_delete_still_present
                    MoodDeleteRecovery.COMPLETION_PENDING -> R.string.mood_delete_completion_error
                    MoodDeleteRecovery.INTENT_MISMATCH -> R.string.mood_delete_intent_mismatch
                    else -> null
                }
                if (deleteRecoveryMessage != null) {
                    AccessibleFeedback(stringResource(deleteRecoveryMessage), Modifier.testTag("mood_delete_recovery"))
                }
                if (state.error == MoodEditorError.LOAD && !state.hasRestoredDraft) {
                    AccessibleFeedback(stringResource(R.string.mood_load_error), Modifier.testTag("mood_error"))
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                if (showRecoveryContent) {
                    when {
                        state.error == MoodEditorError.LOAD && state.deleteRecovery != MoodDeleteRecovery.INTENT_MISMATCH -> AccessibleFeedback(
                            stringResource(R.string.mood_recovery_load_error), Modifier.testTag("mood_recovery_message"))
                        state.error == MoodEditorError.DRAFT_STORAGE && state.deleteRecovery != MoodDeleteRecovery.COMPLETION_PENDING ->
                            AccessibleFeedback(stringResource(R.string.mood_draft_save_error),
                                Modifier.testTag("mood_recovery_message"))
                        deleteRecoveryMessage == null -> AccessibleFeedback(stringResource(R.string.mood_recovery_loading),
                            Modifier.testTag("mood_recovery_message"))
                    }
                    if (state.error == MoodEditorError.LOAD && state.deleteRecovery != MoodDeleteRecovery.INTENT_MISMATCH) {
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("mood_recovery_retry")) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    if (state.deleteRecovery == MoodDeleteRecovery.STATUS_UNKNOWN) {
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("mood_delete_check_retry")) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    if (state.deleteRecovery == MoodDeleteRecovery.COMPLETION_PENDING) {
                        TextButton(onClick = onRetryDraft, modifier = Modifier.heightIn(min = 48.dp).testTag("mood_delete_completion_retry")) {
                            Text(stringResource(R.string.mood_delete_completion_retry))
                        }
                    } else if (state.error == MoodEditorError.DRAFT_STORAGE && state.hasRestoredDraft) {
                        TextButton(onClick = onRetryDraft, modifier = Modifier.heightIn(min = 48.dp).testTag("mood_draft_retry")) {
                            Text(stringResource(R.string.mood_draft_retry))
                        }
                    }
                    MoodRecoveryContent(state)
                }
                if (state.draftStorageError && !showRecoveryContent &&
                    state.deleteRecovery != MoodDeleteRecovery.COMPLETION_PENDING) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccessibleFeedback(stringResource(R.string.mood_draft_save_error), Modifier.weight(1f).testTag("mood_draft_storage_error"))
                        TextButton(onClick = onRetryDraft, modifier = Modifier.heightIn(min = 48.dp).testTag("mood_draft_retry")) {
                            Text(stringResource(R.string.mood_draft_retry))
                        }
                    }
                }
                if (!state.ready && state.draftStorageError && !showRecoveryContent && state.draft.note.isNotEmpty()) {
                    LumaOutlinedTextField(value = state.draft.note, onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.mood_note)) }, minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("mood_note_recovery_preview"))
                }
                if (state.ready) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        CaptureTimestampRow(state.draft.occurredAtMillis, enabled, state.error == MoodEditorError.TIME, onTime)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.mood_question), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() }.testTag("mood_question"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.mood_very_low), style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.mood_great), style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val colors = MaterialTheme.colorScheme
                                (1..5).forEach { score ->
                                    val description = stringResource(R.string.mood_choice, score, stringResource(moodScoreLabel(score)))
                                    val selected = state.draft.score == score
                                    Surface(
                                        modifier = Modifier.weight(1f).widthIn(min = 48.dp).heightIn(min = 48.dp)
                                            .selectable(
                                                selected = selected,
                                                enabled = enabled,
                                                role = Role.RadioButton,
                                                onClick = { onScore(score) },
                                            )
                                            .semantics { contentDescription = description }
                                            .testTag("mood_score_$score"),
                                        shape = RoundedCornerShape(16.dp),
                                        color = when {
                                            selected -> colors.primary
                                            enabled -> colors.surfaceContainerLowest
                                            else -> colors.surfaceContainerLow
                                        },
                                        contentColor = when {
                                            selected -> colors.onPrimary
                                            enabled -> colors.onSurface
                                            else -> colors.onSurfaceVariant
                                        },
                                        border = BorderStroke(1.dp,
                                            if (selected) colors.primary else colors.outlineVariant),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(NumberFormat.getIntegerInstance().format(score),
                                                style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                            state.draft.score?.let { score ->
                                Text(stringResource(R.string.mood_choice, score, stringResource(moodScoreLabel(score))),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("mood_score_description"))
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.mood_emotions), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() }.testTag("mood_emotions"))
                            Text(stringResource(R.string.mood_emotions_hint), style = MaterialTheme.typography.bodySmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                state.emotions.forEach { emotion ->
                                    val selected = emotion.id in state.draft.emotionIds
                                    val colors = MaterialTheme.colorScheme
                                    FilterChip(selected = selected, enabled = enabled,
                                        onClick = { onEmotion(emotion.id) },
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("mood_emotion_${emotion.name}"),
                                        shape = CircleShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surfaceContainerLowest,
                                            labelColor = colors.onSurface,
                                            selectedContainerColor = colors.secondaryContainer,
                                            selectedLabelColor = colors.onSecondaryContainer,
                                            selectedTrailingIconColor = colors.onSecondaryContainer,
                                        ),
                                        trailingIcon = if (selected) {
                                            { Icon(
                                                painter = painterResource(R.drawable.ic_check),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            ) }
                                        } else null,
                                        label = { Text(emotionDisplayName(emotion)) })
                                }
                            }
                        }

                        LumaSectionSurface {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val disclosureState = stringResource(
                                    if (more) R.string.review_details_expanded else R.string.review_details_collapsed,
                                )
                                TextButton(onClick = { more = !more }, enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mood_more")
                                        .semantics { heading(); stateDescription = disclosureState }) {
                                    Text(
                                        stringResource(if (more) R.string.mood_fewer_details else R.string.mood_more_details),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Text(stringResource(R.string.mood_context_help), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (more) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OptionalScale(stringResource(R.string.mood_energy), "energy", state.draft.energy, enabled, onEnergy)
                                    OptionalScale(stringResource(R.string.mood_stress), "stress", state.draft.stress, enabled, onStress)
                                    LumaOutlinedTextField(value = state.draft.note, onValueChange = onNote, enabled = enabled,
                                        label = { Text(stringResource(R.string.mood_note)) }, minLines = 3,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                        modifier = Modifier.fillMaxWidth().testTag("mood_note"))
                                    if (state.noteLimitError) AccessibleFeedback(
                                        stringResource(R.string.mood_note_limit_error), Modifier.testTag("mood_note_limit_error"))
                                    peopleContent()
                                    placeContent()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (deleting) LumaAlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.mood_delete_title)) },
        text = { Text(stringResource(R.string.mood_delete_body)) },
        confirmButton = { LumaDestructiveTextButton(onClick = { deleting = false; onDelete() }, modifier = Modifier.testTag("mood_confirm_delete")) {
            Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (discarding) LumaAlertDialog(
        onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.mood_discard_title)) },
        text = { Text(stringResource(R.string.mood_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } },
    )
}

@Composable
private fun MoodRecoveryContent(state: MoodEditorState) {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(zone)
    val displayTime = rememberDisplayTimePolicy()
    val timeText = if (moment.toLocalDate() == LocalDate.now(zone)) {
        stringResource(R.string.capture_timestamp_today, moment.format(displayTime.time()))
    } else {
        stringResource(R.string.capture_timestamp_value,
            moment.format(displayTime.date(FormatStyle.MEDIUM)), moment.format(displayTime.time()))
    }
    Text(stringResource(R.string.mood_recovery_time, timeText), modifier = Modifier.testTag("mood_recovery_time"))
    state.draft.score?.let { score ->
        Text(stringResource(R.string.mood_choice, score, stringResource(moodScoreLabel(score))),
            modifier = Modifier.testTag("mood_recovery_score"))
    }
    state.draft.energy?.let { value ->
        Text(stringResource(R.string.mood_optional_value, stringResource(R.string.mood_energy), value))
    }
    state.draft.stress?.let { value ->
        Text(stringResource(R.string.mood_optional_value, stringResource(R.string.mood_stress), value))
    }
    val selectedEmotions = state.emotions.filter { it.id in state.draft.emotionIds }
    if (selectedEmotions.isNotEmpty()) {
        val selectedEmotionNames = ArrayList<String>(selectedEmotions.size)
        for (emotion in selectedEmotions) selectedEmotionNames.add(emotionDisplayName(emotion))
        Text(stringResource(R.string.mood_recovery_emotions,
            selectedEmotionNames.joinToString()))
    }
    if (state.draft.emotionIds.size > selectedEmotions.size) {
        Text(stringResource(R.string.mood_recovery_emotions_unavailable))
    }
    if (state.draft.personIds.isNotEmpty() || state.draft.placeId != null) {
        Text(stringResource(R.string.mood_recovery_links_unavailable))
    }
    if (state.draft.note.isNotEmpty()) {
        LumaOutlinedTextField(value = state.draft.note, onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.mood_note)) }, minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("mood_note_recovery"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionalScale(label: String, tag: String, value: Int?, enabled: Boolean, onValue: (Int?) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.mood_scale_hint), style = MaterialTheme.typography.bodySmall)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..5).forEach { score ->
            val description = stringResource(R.string.mood_scale_description, label, score)
            FilterChip(selected = value == score, enabled = enabled, onClick = { onValue(if (score == value) null else score) },
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                    .testTag("mood_${tag}_$score").semantics { contentDescription = description },
                shape = RoundedCornerShape(16.dp),
                label = { Text(NumberFormat.getIntegerInstance().format(score)) })
        }
    }
}
