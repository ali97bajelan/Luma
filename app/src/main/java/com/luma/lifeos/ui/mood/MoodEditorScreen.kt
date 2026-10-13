package com.luma.lifeos.ui.mood

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.language.emotionDisplayName
import com.luma.lifeos.ui.common.EditorTopBar
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

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
        topBar = {
            EditorTopBar(stringResource(if (editing) R.string.mood_edit else R.string.mood_capture), "mood_cancel", !state.busy, ::close)
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().imePadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) TextButton(enabled = enabled, onClick = { deleting = true }, modifier = Modifier.testTag("mood_delete")) {
                    Text(stringResource(R.string.delete))
                }
                Button(enabled = enabled && state.draft.score != null, onClick = onSave,
                    modifier = Modifier.weight(1f).testTag("mood_save")) {
                    Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) CircularProgressIndicator(Modifier.testTag("mood_loading"))
            state.error?.let {
                Text(stringResource(when (it) {
                    MoodEditorError.LOAD -> R.string.mood_load_error
                    MoodEditorError.SAVE -> R.string.mood_save_error
                    MoodEditorError.DELETE -> R.string.mood_delete_error
                    MoodEditorError.TIME -> R.string.mood_time_error
                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("mood_error"))
                if (it == MoodEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (state.ready) {
                Text(stringResource(R.string.mood_question), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { score ->
                        val description = stringResource(R.string.mood_choice, score, stringResource(moodScoreLabel(score)))
                        FilterChip(selected = state.draft.score == score, enabled = enabled,
                            onClick = { onScore(score) },
                            modifier = Modifier.weight(1f).testTag("mood_score_$score")
                                .semantics { contentDescription = description },
                            label = { Text(NumberFormat.getIntegerInstance().format(score)) })
                    }
                }
                state.draft.score?.let { score ->
                    Text(stringResource(R.string.mood_choice, score, stringResource(moodScoreLabel(score))),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("mood_score_description"))
                }
                Text(stringResource(R.string.mood_emotions), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.mood_emotions_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.emotions.forEach { emotion ->
                        FilterChip(selected = emotion.id in state.draft.emotionIds, enabled = enabled,
                            onClick = { onEmotion(emotion.id) }, modifier = Modifier.testTag("mood_emotion_${emotion.name}"),
                            label = { Text(emotionDisplayName(emotion)) })
                    }
                }
                TextButton(onClick = { more = !more }, enabled = !state.busy, modifier = Modifier.testTag("mood_more")) {
                    Text(stringResource(if (more) R.string.mood_fewer_details else R.string.mood_more_details))
                }
                if (more) {
                    OptionalScale(stringResource(R.string.mood_energy), "energy", state.draft.energy, enabled, onEnergy)
                    OptionalScale(stringResource(R.string.mood_stress), "stress", state.draft.stress, enabled, onStress)
                    OutlinedTextField(value = state.draft.note, onValueChange = onNote, enabled = enabled,
                        label = { Text(stringResource(R.string.mood_note)) }, minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        modifier = Modifier.fillMaxWidth().testTag("mood_note"))
                    peopleContent()
                    placeContent()
                    MoodTimeControls(state.draft.occurredAtMillis, enabled, state.error == MoodEditorError.TIME, onTime)
                }
            }
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.mood_delete_title)) },
        text = { Text(stringResource(R.string.mood_delete_body)) },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }, modifier = Modifier.testTag("mood_confirm_delete")) {
            Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (discarding) AlertDialog(
        onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.mood_discard_title)) },
        text = { Text(stringResource(R.string.mood_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onCancel() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionalScale(label: String, tag: String, value: Int?, enabled: Boolean, onValue: (Int?) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.mood_scale_hint), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { score ->
            val description = stringResource(R.string.mood_scale_description, label, score)
            FilterChip(selected = value == score, enabled = enabled, onClick = { onValue(if (score == value) null else score) },
                modifier = Modifier.weight(1f).testTag("mood_${tag}_$score").semantics { contentDescription = description },
                label = { Text(NumberFormat.getIntegerInstance().format(score)) })
        }
    }
}
