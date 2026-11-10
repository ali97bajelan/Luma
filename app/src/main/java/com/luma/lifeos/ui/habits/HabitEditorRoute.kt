package com.luma.lifeos.ui.habits

import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.LumaDatePickerDialog

import com.luma.lifeos.ui.common.LumaFilterChip
import com.luma.lifeos.ui.common.LumaCalendarDatePicker
import com.luma.lifeos.ui.common.calendarPickerBounds
import com.luma.lifeos.ui.common.rememberLumaCalendarDatePickerState
import com.luma.lifeos.calendar.rememberCalendarPolicy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.luma.lifeos.ui.common.LumaOutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.EditorTopBar
import com.luma.lifeos.ui.common.EditorActionRow
import com.luma.lifeos.domain.habit.HabitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaEditorActionFooter
import com.luma.lifeos.ui.common.LumaPrimaryActionButton

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
    val enabled = state.ready && !state.busy && !state.completed
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    fun close() { if (state.busy) return else if (state.hasChanges) discarding = true else onBack() }
    BackHandler { close() }
    Scaffold(
        modifier = Modifier.testTag("screen_habit_editor"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EditorTopBar(stringResource(if (state.editing) R.string.habit_editor_edit else R.string.habit_editor_new),
                "habit_editor_close", !state.busy, ::close)
        },
        bottomBar = {
            LumaEditorActionFooter {
                    state.error?.let { error ->
                        val message = when (error) {
                            HabitEditorError.LOAD -> R.string.habit_load_error
                            HabitEditorError.SAVE -> R.string.habit_save_error
                            HabitEditorError.INPUT -> state.fieldError?.let(::habitFieldErrorMessage)
                                ?: R.string.habit_save_error
                        }
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("habit_editor_error"))
                        if (error == HabitEditorError.LOAD) LumaTextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                    EditorActionRow { actionModifier ->
                        LumaPrimaryActionButton(
                            onClick = onSave,
                            enabled = enabled,
                            modifier = actionModifier.testTag("habit_save"),
                        ) {
                            Text(stringResource(if (state.busy) R.string.saving else R.string.save))
                        }
                    }
            }
        },
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = LumaUi.PageGutter, vertical = LumaUi.SectionGap),
                verticalArrangement = Arrangement.spacedBy(LumaUi.GroupGap),
            ) {
                if (state.loading) androidx.compose.material3.CircularProgressIndicator()
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                    val nameError = state.fieldError?.takeIf {
                        it == HabitEditorFieldError.NAME_REQUIRED || it == HabitEditorFieldError.NAME_TOO_LONG
                    }?.let { stringResource(habitFieldErrorMessage(it)) }
                    LumaOutlinedTextField(state.draft.name, onName, enabled = enabled, singleLine = true,
                        label = { Text(stringResource(R.string.habit_name)) },
                        supportingText = { nameError?.let { Text(it) } },
                        isError = nameError != null,
                        modifier = Modifier.fillMaxWidth().semantics {
                            nameError?.let(::error)
                        }.testTag("habit_name"))
                    val unitError = state.fieldError?.takeIf { it == HabitEditorFieldError.UNIT_TOO_LONG }
                        ?.let { stringResource(habitFieldErrorMessage(it)) }
                    LumaOutlinedTextField(state.draft.unitLabel, onUnit, enabled = enabled, singleLine = true,
                        label = { Text(stringResource(R.string.habit_unit)) },
                        placeholder = { Text(stringResource(R.string.habit_unit_example)) },
                        supportingText = {
                            Text(unitError ?: stringResource(R.string.habit_count_explanation))
                        },
                        isError = unitError != null,
                        modifier = Modifier.fillMaxWidth().semantics {
                            unitError?.let(::error)
                        }.testTag("habit_unit"))
                    val targetError = state.fieldError?.takeIf { it == HabitEditorFieldError.TARGET_RANGE }
                        ?.let { stringResource(habitFieldErrorMessage(it)) }
                    val targetUnit = state.draft.unitLabel.trim()
                    val targetCaption = if (targetUnit.isBlank()) {
                        stringResource(R.string.habit_target_unit_generic_per_day)
                    } else {
                        stringResource(R.string.habit_target_unit_per_day, targetUnit)
                    }
                    LumaOutlinedTextField(
                        value = state.draft.target,
                        onValueChange = onTarget,
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.headlineSmall,
                        label = { Text(stringResource(R.string.habit_target)) },
                        supportingText = { targetError?.let { Text(it) } },
                        trailingIcon = {
                            Surface(
                                shape = CircleShape,
                                color = colors.surfaceContainer,
                                contentColor = colors.onSurfaceVariant,
                            ) {
                                Text(
                                    targetCaption,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.widthIn(max = 136.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        },
                        isError = targetError != null,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            disabledTextColor = colors.onSurfaceVariant,
                            errorTextColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceContainerLow,
                            unfocusedContainerColor = colors.surfaceContainerLow,
                            disabledContainerColor = colors.surfaceContainerLow,
                            errorContainerColor = colors.surfaceContainerLow,
                            cursorColor = colors.primary,
                            errorCursorColor = colors.error,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outlineVariant,
                            disabledBorderColor = colors.outlineVariant,
                            errorBorderColor = colors.error,
                            focusedLabelColor = colors.primary,
                            unfocusedLabelColor = colors.onSurfaceVariant,
                            disabledLabelColor = colors.onSurfaceVariant,
                            errorLabelColor = colors.error,
                            focusedSupportingTextColor = colors.onSurfaceVariant,
                            unfocusedSupportingTextColor = colors.onSurfaceVariant,
                            disabledSupportingTextColor = colors.onSurfaceVariant,
                            errorSupportingTextColor = colors.error,
                        ),
                        modifier = Modifier.fillMaxWidth().semantics { targetError?.let(::error) }
                            .testTag("habit_target"),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                    val startError = state.fieldError?.takeIf { it == HabitEditorFieldError.START_IN_PAST }
                        ?.let { stringResource(habitFieldErrorMessage(it)) }
                    LumaOutlinedButton(
                        onClick = { choosingStart = true },
                        enabled = enabled && !state.editing,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (state.editing) colors.outlineVariant else colors.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.onSurface,
                            disabledContentColor = colors.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { startError?.let(::error) }.testTag("habit_start_date"),
                    ) {
                        val date = com.luma.lifeos.language.localizedDate(state.draft.start, FormatStyle.MEDIUM)
                        Text("${stringResource(R.string.habit_start_date)}: $date", style = MaterialTheme.typography.labelLarge)
                    }
                    startError?.let {
                        Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap)) {
                    Text(
                        stringResource(R.string.habit_weekdays),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.habit_schedule_presets), style = MaterialTheme.typography.labelLarge)
                    val selectedPreset = schedulePreset(state.draft.weekdays)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                        verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                        modifier = Modifier.testTag("habit_schedule_presets"),
                    ) {
                        HabitSchedulePreset.entries.forEach { preset ->
                            LumaFilterChip(
                                selected = selectedPreset == preset,
                                enabled = enabled,
                                onClick = { onWeekdays(schedulePresetDays(preset, state.draft.weekdays)) },
                                leadingIcon = if (selectedPreset == preset) {
                                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                label = { Text(stringResource(schedulePresetLabel(preset))) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.secondary,
                                    selectedLabelColor = colors.onSecondary,
                                    selectedLeadingIconColor = colors.onSecondary,
                                    selectedTrailingIconColor = colors.onSecondary,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .testTag("habit_schedule_preset_${preset.name.lowercase(Locale.ROOT)}"),
                            )
                        }
                    }
                    val weekdaysError = state.fieldError?.takeIf { it == HabitEditorFieldError.WEEKDAYS_REQUIRED }
                        ?.let { stringResource(habitFieldErrorMessage(it)) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                        verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                        modifier = Modifier.semantics { weekdaysError?.let(::error) }.testTag("habit_weekdays"),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            LumaFilterChip(
                                selected = day in state.draft.weekdays,
                                enabled = enabled,
                                onClick = {
                                    onWeekdays(
                                        if (day in state.draft.weekdays) state.draft.weekdays - day
                                        else state.draft.weekdays + day,
                                    )
                                },
                                leadingIcon = if (day in state.draft.weekdays) {
                                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.secondary,
                                    selectedLabelColor = colors.onSecondary,
                                    selectedLeadingIconColor = colors.onSecondary,
                                    selectedTrailingIconColor = colors.onSecondary,
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .semantics {
                                        contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                                    }
                                    .testTag("habit_weekday_${day.value}"),
                            )
                        }
                    }
                    weekdaysError?.let {
                        Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("habit_weekdays_error"))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                    state.effectiveDate?.let { effective ->
                        val futureStart = effective.isAfter(LocalDate.now(state.trackingZone ?: deviceZone))
                        val message = if (state.editing && !futureStart) R.string.habit_effective_tomorrow else R.string.habit_effective_start
                        Text(stringResource(message, com.luma.lifeos.language.localizedDate(effective, FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("habit_effective_date"))
                    }
                }
            }
        }
    }
    if (choosingStart) {
        val initialDate = state.draft.start
        val (minDate, maxDate) = calendarPickerBounds(1900..2100)
        val picker = rememberLumaCalendarDatePickerState(initialDate, initialDate, minDate, maxDate)
        val calendarPolicy = rememberCalendarPolicy()
        val isSelectable: (LocalDate) -> Boolean = { true }
        LumaDatePickerDialog(
            onDismissRequest = { choosingStart = false },
            confirmButton = {
                LumaTextButton(enabled = picker.canConfirm(isSelectable), onClick = {
                    picker.selectedDate?.takeIf { picker.isSelectable(it, isSelectable) }?.let(onStart)
                    choosingStart = false
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = { LumaTextButton(onClick = { choosingStart = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            LumaCalendarDatePicker(picker, calendarPolicy, isSelectable)
        }
    }
    if (discarding) LumaAlertDialog(onDismissRequest = { discarding = false },
        title = { Text(stringResource(R.string.habit_discard_title)) },
        text = { Text(stringResource(R.string.habit_discard_body)) },
        confirmButton = { LumaTextButton(onClick = { discarding = false; onBack() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { LumaTextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_editing)) } })
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

