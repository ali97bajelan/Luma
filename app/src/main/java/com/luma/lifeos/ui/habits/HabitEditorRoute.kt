package com.luma.lifeos.ui.habits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun HabitEditorRoute(
    repository: HabitRepository,
    habitId: UUID?,
    onDone: (UUID) -> Unit,
    onBack: () -> Unit,
    deviceZone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
) {
    val factory = androidx.compose.runtime.remember(repository, habitId, deviceZone) {
        viewModelFactory {
            initializer { HabitEditorViewModel(repository, habitId, createSavedStateHandle(), deviceZone = deviceZone) }
        }
    }
    val editor: HabitEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed, state.resultId) {
        if (state.completed) state.resultId?.let(onDone)
    }
    HabitEditorScreen(state, deviceZone, onBack, editor::load, editor::setName, editor::setUnitLabel,
        editor::setTarget, editor::setStart, editor::setWeekdays, editor::save)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(
    state: HabitEditorState,
    deviceZone: java.time.ZoneId,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onName: (String) -> Unit,
    onUnit: (String) -> Unit,
    onTarget: (String) -> Unit,
    onStart: (LocalDate) -> Unit,
    onWeekdays: (Set<DayOfWeek>) -> Unit,
    onSave: () -> Unit,
) {
    var choosingStart by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var trackingDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    val enabled = state.ready && !state.busy && !state.completed
    fun close() { if (state.busy) return else if (state.hasChanges) discarding = true else onBack() }
    BackHandler { close() }
    Scaffold(
        modifier = Modifier.testTag("screen_habit_editor"),
        topBar = {
            TopAppBar(title = { Text(stringResource(if (state.editing) R.string.habit_editor_edit else R.string.habit_editor_new)) },
                navigationIcon = { TextButton(enabled = !state.busy, onClick = ::close) { Text(stringResource(R.string.cancel)) } })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().imePadding().padding(12.dp)) {
                state.error?.let { error ->
                    val message = when (error) {
                        HabitEditorError.LOAD -> R.string.habit_load_error
                        HabitEditorError.SAVE -> R.string.habit_save_error
                        HabitEditorError.INPUT -> state.fieldError?.let(::habitFieldErrorMessage)
                            ?: R.string.habit_save_error
                    }
                    Text(stringResource(message), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("habit_editor_error"))
                    if (error == HabitEditorError.LOAD) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                Button(onClick = onSave, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_save")) {
                    Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.loading) androidx.compose.material3.CircularProgressIndicator()
            state.trackingZone?.let { zone ->
                Text(stringResource(R.string.habit_tracking_zone, zone.id), style = MaterialTheme.typography.labelLarge)
                if (zone != deviceZone) {
                    Text(stringResource(R.string.habit_tracking_zone_mismatch,
                        LocalDate.now(zone).format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)),
                        zone.id,
                        LocalDate.now(deviceZone).format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("habit_tracking_day_difference"))
                }
            }
            val nameError = state.fieldError?.takeIf {
                it == HabitEditorFieldError.NAME_REQUIRED || it == HabitEditorFieldError.NAME_TOO_LONG
            }?.let { stringResource(habitFieldErrorMessage(it)) }
            OutlinedTextField(state.draft.name, onName, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.habit_name)) },
                isError = nameError != null,
                modifier = Modifier.fillMaxWidth().semantics {
                    nameError?.let(::error)
                }.testTag("habit_name"))
            val unitError = state.fieldError?.takeIf { it == HabitEditorFieldError.UNIT_TOO_LONG }
                ?.let { stringResource(habitFieldErrorMessage(it)) }
            OutlinedTextField(state.draft.unitLabel, onUnit, enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.habit_unit)) },
                placeholder = { Text(stringResource(R.string.habit_unit_example)) },
                supportingText = { Text(stringResource(R.string.habit_count_explanation)) },
                isError = unitError != null,
                modifier = Modifier.fillMaxWidth().semantics {
                    unitError?.let(::error)
                }.testTag("habit_unit"))
            val targetError = state.fieldError?.takeIf { it == HabitEditorFieldError.TARGET_RANGE }
                ?.let { stringResource(habitFieldErrorMessage(it)) }
            OutlinedTextField(state.draft.target, onTarget, enabled = enabled, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.habit_target)) },
                isError = targetError != null,
                modifier = Modifier.fillMaxWidth().semantics { targetError?.let(::error) }.testTag("habit_target"))
            val startError = state.fieldError?.takeIf { it == HabitEditorFieldError.START_IN_PAST }
                ?.let { stringResource(habitFieldErrorMessage(it)) }
            OutlinedButton(onClick = { choosingStart = true }, enabled = enabled && !state.editing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { startError?.let(::error) }
                    .testTag("habit_start_date")) {
                val date = state.draft.start.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))
                Text("${stringResource(R.string.habit_start_date)}: $date")
            }
            Text(stringResource(R.string.habit_weekdays), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.habit_schedule_presets), style = MaterialTheme.typography.bodyMedium)
            val selectedPreset = schedulePreset(state.draft.weekdays)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.testTag("habit_schedule_presets")) {
                HabitSchedulePreset.entries.forEach { preset ->
                    FilterChip(selected = selectedPreset == preset, enabled = enabled,
                        onClick = { onWeekdays(schedulePresetDays(preset, state.draft.weekdays)) },
                        label = { Text(stringResource(schedulePresetLabel(preset))) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("habit_schedule_preset_${preset.name.lowercase(Locale.ROOT)}"))
                }
            }
            val weekdaysError = state.fieldError?.takeIf { it == HabitEditorFieldError.WEEKDAYS_REQUIRED }
                ?.let { stringResource(habitFieldErrorMessage(it)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.semantics { weekdaysError?.let(::error) }.testTag("habit_weekdays")) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(selected = day in state.draft.weekdays, enabled = enabled,
                        onClick = { onWeekdays(if (day in state.draft.weekdays) state.draft.weekdays - day else state.draft.weekdays + day) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("habit_weekday_${day.value}"))
                }
            }
            weekdaysError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("habit_weekdays_error")) }
            state.effectiveDate?.let { effective ->
                val futureStart = effective.isAfter(LocalDate.now(state.trackingZone ?: deviceZone))
                val message = if (state.editing && !futureStart) R.string.habit_effective_tomorrow else R.string.habit_effective_start
                Text(stringResource(message, effective.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("habit_effective_date"))
            }
            if (state.trackingZone != null) {
                TextButton(onClick = { trackingDetailsExpanded = !trackingDetailsExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_tracking_zone_disclosure")) {
                    Text(stringResource(R.string.habit_tracking_zone_details), modifier = Modifier.weight(1f))
                    Text(stringResource(if (trackingDetailsExpanded) R.string.habit_other_hide else R.string.habit_other_show))
                }
                if (trackingDetailsExpanded) Text(stringResource(R.string.habit_tracking_zone_note),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("habit_tracking_zone_explanation"))
            }
        }
    }
    if (choosingStart) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.draft.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { choosingStart = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onStart(pickerLocalDate(it)) }
                choosingStart = false
            }) { Text(stringResource(R.string.select_date)) }
        }, dismissButton = { TextButton(onClick = { choosingStart = false }) { Text(stringResource(R.string.cancel)) } }) {
            DatePicker(state = picker)
        }
    }
    if (discarding) AlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.habit_discard_title)) },
        text = { Text(stringResource(R.string.habit_discard_body)) },
        confirmButton = { TextButton(onClick = { discarding = false; onBack() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
}

@StringRes
private fun habitFieldErrorMessage(error: HabitEditorFieldError): Int = when (error) {
    HabitEditorFieldError.NAME_REQUIRED -> R.string.habit_name_required
    HabitEditorFieldError.NAME_TOO_LONG -> R.string.habit_name_limit
    HabitEditorFieldError.UNIT_TOO_LONG -> R.string.habit_unit_limit
    HabitEditorFieldError.TARGET_RANGE -> R.string.habit_target_range
    HabitEditorFieldError.WEEKDAYS_REQUIRED -> R.string.habit_choose_weekday
    HabitEditorFieldError.START_IN_PAST -> R.string.habit_start_future
}

@StringRes
private fun schedulePresetLabel(preset: HabitSchedulePreset): Int = when (preset) {
    HabitSchedulePreset.DAILY -> R.string.habit_preset_daily
    HabitSchedulePreset.WEEKDAYS -> R.string.habit_preset_weekdays
    HabitSchedulePreset.CUSTOM -> R.string.habit_preset_custom
}

private fun pickerLocalDate(millis: Long): LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
