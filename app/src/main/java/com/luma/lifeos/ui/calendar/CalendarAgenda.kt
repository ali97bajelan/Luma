package com.luma.lifeos.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.calendar.CalendarFilter
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDayState
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.language.DisplayTimePolicy
import com.luma.lifeos.ui.common.LumaSectionHeader
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaListSurface
import com.luma.lifeos.ui.todo.TodayTaskRecoverySummary
import com.luma.lifeos.ui.todo.TodayTasksActionState
import com.luma.lifeos.ui.todo.TodoListRowError
import java.time.LocalDate
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@Composable
internal fun CalendarAgenda(
    state: CalendarUiState,
    actions: TodayTasksActionState,
    displayTime: DisplayTimePolicy,
    onFilter: (CalendarFilter) -> Unit,
    onTask: (UUID) -> Unit,
    onDeleteTask: (UUID) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onAllTasks: () -> Unit,
    onLoadMore: () -> Unit,
    onSetCompleted: (TodoTask, Boolean) -> Unit,
    onRetry: (CalendarSource) -> Unit,
    onRetryTask: (UUID) -> Unit,
    onDismissTaskError: (UUID) -> Unit,
    onHabit: (UUID, LocalDate) -> Unit,
    onJournal: (com.luma.lifeos.domain.model.EventId) -> Unit,
    onWriteReflection: (LocalDate) -> Unit,
    onTimeline: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CalendarDateRibbon(state, displayTime)
        CalendarAgendaFilters(state, displayTime.locale, onFilter)
        if (state.overdueCount is CalendarDataState.Error) {
            CalendarSourceError(
                message = R.string.calendar_tasks_error,
                onRetry = { onRetry(CalendarSource.TASKS) },
                tag = "calendar_overdue_count_error",
            )
        }
        if (state.filter == CalendarFilter.OVERDUE) {
            Text(
                stringResource(R.string.calendar_overdue_as_of, state.today.format(displayTime.date(FormatStyle.MEDIUM))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("calendar_overdue_scope"),
            )
        }
        if (state.filter != CalendarFilter.OVERDUE && state.selectedDate == state.today) {
            when (val carryIn = state.carryInCount) {
                null -> Text(
                    stringResource(R.string.calendar_count_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("calendar_carry_in_unknown"),
                )
                in 1..Int.MAX_VALUE -> Text(
                    pluralStringResource(R.plurals.calendar_overdue_included, carryIn, carryIn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("calendar_carry_in"),
                )
                else -> Unit
            }
        }

        CalendarTasksSection(
            state = state,
            actions = actions,
            displayTime = displayTime,
            onTask = onTask,
            onDeleteTask = onDeleteTask,
            onAddTask = onAddTask,
            onAllTasks = onAllTasks,
            onLoadMore = onLoadMore,
            onSetCompleted = onSetCompleted,
            onRetry = onRetry,
            onRetryTask = onRetryTask,
            onDismissTaskError = onDismissTaskError,
        )
        if (state.filter == CalendarFilter.ALL) {
            CalendarHabitsSection(state, displayTime, onHabit, onRetry)
            CalendarReflectionSection(state, onJournal, onWriteReflection, onTimeline, onRetry)
        }
    }
}

@Composable
private fun CalendarDateRibbon(state: CalendarUiState, displayTime: DisplayTimePolicy) {
    LumaSectionSurface(modifier = Modifier.testTag("calendar_date_ribbon")) {
        Text(
            state.selectedDate.format(displayTime.date(FormatStyle.FULL)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarCountPill(
                count = state.exactTaskCount,
                plurals = R.plurals.calendar_tasks_count,
                label = R.string.calendar_legend_tasks,
                modifier = Modifier.weight(1f),
            )
            CalendarCountPill(
                count = state.selectedHabitCount,
                plurals = R.plurals.calendar_habits_count,
                label = R.string.calendar_legend_habits,
                modifier = Modifier.weight(1f),
            )
            CalendarCountPill(
                count = state.selectedJournalCount,
                plurals = R.plurals.calendar_journals_count,
                label = R.string.calendar_legend_journals,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalendarCountPill(
    count: Int?,
    plurals: Int,
    label: Int,
    modifier: Modifier = Modifier,
) {
    val labelText = stringResource(label)
    val valueText = count?.let { pluralStringResource(plurals, it, it) } ?: stringResource(R.string.calendar_count_unavailable)
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                .semantics { contentDescription = "$labelText. $valueText" },
            verticalArrangement = Arrangement.Center,
        ) {
            Text(labelText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CalendarAgendaFilters(
    state: CalendarUiState,
    locale: Locale,
    onFilter: (CalendarFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarFilter.entries.forEach { filter ->
            val selected = state.filter == filter
            val title = stringResource(
                when (filter) {
                    CalendarFilter.ALL -> R.string.calendar_filter_all
                    CalendarFilter.TASKS -> R.string.calendar_filter_tasks
                    CalendarFilter.OVERDUE -> R.string.calendar_filter_overdue
                },
            )
            val count = when (filter) {
                CalendarFilter.ALL -> state.allItemsCount
                CalendarFilter.TASKS -> state.scheduledTaskCount
                CalendarFilter.OVERDUE -> state.overdueTotal
            }
            val countText = count?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) }
                ?: stringResource(R.string.calendar_count_unavailable)
            FilterChip(
                selected = selected,
                onClick = { onFilter(filter) },
                label = { Text(stringResource(R.string.calendar_filter_with_count, title, countText), maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("calendar_filter_${filter.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun CalendarTasksSection(
    state: CalendarUiState,
    actions: TodayTasksActionState,
    displayTime: DisplayTimePolicy,
    onTask: (UUID) -> Unit,
    onDeleteTask: (UUID) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onAllTasks: () -> Unit,
    onLoadMore: () -> Unit,
    onSetCompleted: (TodoTask, Boolean) -> Unit,
    onRetry: (CalendarSource) -> Unit,
    onRetryTask: (UUID) -> Unit,
    onDismissTaskError: (UUID) -> Unit,
) {
    val page = (state.taskPage as? CalendarDataState.Ready)?.value
    val visibleIds = page?.items?.mapTo(mutableSetOf()) { it.id }.orEmpty()
    LumaSectionSurface(modifier = Modifier.testTag("calendar_tasks_section")) {
        LumaSectionHeader(
            title = stringResource(
                R.string.calendar_filter_with_count,
                stringResource(R.string.calendar_tasks_heading),
                page?.total?.let { java.text.NumberFormat.getIntegerInstance(displayTime.locale).format(it) }
                    ?: stringResource(R.string.calendar_count_unavailable),
            ),
            actionLabel = stringResource(R.string.calendar_add_task),
            onAction = { onAddTask(state.selectedDate) },
            actionModifier = Modifier.heightIn(min = 48.dp).testTag("calendar_add_task"),
        )
        TextButton(
            onClick = onAllTasks,
            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("calendar_all_tasks"),
        ) { Text(stringResource(R.string.calendar_view_all_tasks)) }

        when (val taskPage = state.taskPage) {
            CalendarDataState.Loading -> CalendarSourceLoading(R.string.calendar_tasks_loading, "calendar_tasks_loading")
            CalendarDataState.Error -> CalendarSourceError(
                message = R.string.calendar_tasks_error,
                onRetry = { onRetry(CalendarSource.TASKS) },
                tag = "calendar_tasks_error",
            )
            is CalendarDataState.Ready -> {
                if (taskPage.value.items.isEmpty()) {
                    Text(stringResource(R.string.calendar_tasks_empty), modifier = Modifier.testTag("calendar_tasks_empty"))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        taskPage.value.items.forEach { task ->
                            key(task.id) {
                                CalendarTaskRow(
                                    task = task,
                                    today = state.today,
                                    actions = actions,
                                    displayTime = displayTime,
                                    onOpen = { onTask(task.id) },
                                    onDelete = { onDeleteTask(task.id) },
                                    onSetCompleted = { onSetCompleted(task, it) },
                                    onRetry = { onRetryTask(task.id) },
                                    onDismissError = { onDismissTaskError(task.id) },
                                )
                            }
                        }
                    }
                }
                if (state.loadingMore) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.calendar_tasks_loading_more))
                    }
                } else if (state.loadMoreError) {
                    CalendarSourceError(
                        message = R.string.calendar_tasks_error,
                        onRetry = { onRetry(CalendarSource.TASKS) },
                        tag = "calendar_tasks_more_error",
                    )
                } else if (taskPage.value.items.size < taskPage.value.total) {
                    TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("calendar_tasks_load_more"),
                    ) { Text(stringResource(R.string.calendar_load_more)) }
                }
            }
        }

        val offPageIntents = actions.pendingIntents.values.filter { it.taskId !in visibleIds }
        val offPageFeedback = actions.rowFeedback.filterKeys { it !in visibleIds && it !in actions.pendingIntents }
        if (offPageIntents.isNotEmpty() || offPageFeedback.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("calendar_task_recovery")) {
                Text(
                    stringResource(R.string.todo_today_task_feedback),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                offPageIntents.forEach { intent ->
                    val feedback = actions.rowFeedback[intent.taskId]
                    CalendarTaskRecovery(intent.taskId, intent.title, intent.completed, feedback?.error, intent.taskId in actions.inFlight,
                        onRetryTask, onTask, onDismissTaskError)
                }
                offPageFeedback.forEach { (taskId, feedback) ->
                    CalendarTaskRecovery(
                        taskId = taskId,
                        title = feedback.title,
                        completesTask = feedback.completesTask,
                        error = feedback.error,
                        pending = false,
                        onRetry = onRetryTask,
                        onOpen = onTask,
                        onDismiss = onDismissTaskError,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarTaskRecovery(
    taskId: UUID,
    title: String,
    completesTask: Boolean,
    error: TodoListRowError?,
    pending: Boolean,
    onRetry: (UUID) -> Unit,
    onOpen: (UUID) -> Unit,
    onDismiss: (UUID) -> Unit,
) {
    TodayTaskRecoverySummary(
        taskId = taskId,
        title = title,
        completesTask = completesTask,
        error = error,
        pending = pending,
        retryEnabled = error == TodoListRowError.FAILED && !pending,
        onRetry = { onRetry(taskId) },
        onOpen = { onOpen(taskId) },
        onDismiss = { onDismiss(taskId) },
    )
}

@Composable
private fun CalendarTaskRow(
    task: TodoTask,
    today: LocalDate,
    actions: TodayTasksActionState,
    displayTime: DisplayTimePolicy,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onSetCompleted: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    val pending = task.id in actions.inFlight
    val hasPendingIntent = task.id in actions.pendingIntents
    val rowError = actions.rowErrors[task.id]
    val blocked = rowError == TodoListRowError.CONFLICT || rowError == TodoListRowError.MISSING
    val completionLabel = stringResource(if (task.isCompleted) R.string.todo_reopen else R.string.todo_mark_complete)
    val stateLabel = stringResource(if (task.isCompleted) R.string.todo_status_completed else R.string.todo_status_open)
    val menuDescription = stringResource(R.string.calendar_task_menu_description, task.title)
    val dateBadge = taskDateBadge(task, today, displayTime)
    val dueDate = requireNotNull(task.dueDate)
    val taskFocus = remember(task.id) { FocusRequester() }

    LumaListSurface(modifier = Modifier.testTag("calendar_task_${task.id}")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = onSetCompleted,
                enabled = !pending && !hasPendingIntent && !blocked && !task.isCompleted,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = "${task.title}. $completionLabel"
                    stateDescription = stateLabel
                }.testTag("calendar_task_complete_${task.id}"),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = onOpen,
                    enabled = rowError != TodoListRowError.MISSING,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).focusRequester(taskFocus)
                        .semantics {
                            contentDescription = listOfNotNull(task.title, dateBadge, task.note).joinToString(". ")
                        }.testTag("calendar_task_open_${task.id}"),
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.Start) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Surface(
                        color = if (dueDate.isBefore(today)) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (dueDate.isBefore(today)) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape,
                        ) {
                            Text(dateBadge, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        task.note?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (pending || rowError != null) {
                    Text(
                            stringResource(
                                when {
                                    rowError == TodoListRowError.CONFLICT -> R.string.todo_list_row_conflict
                                    rowError == TodoListRowError.MISSING -> R.string.todo_list_row_missing
                                    rowError == TodoListRowError.FAILED -> R.string.todo_list_row_failed
                                    task.isCompleted -> R.string.todo_reopening
                                    else -> R.string.todo_completing
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rowError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    if (rowError == TodoListRowError.FAILED) {
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.todo_retry_action))
                        }
                    } else if (rowError == TodoListRowError.CONFLICT || rowError == TodoListRowError.MISSING) {
                        if (rowError == TodoListRowError.CONFLICT) {
                            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.todo_status_open))
                            }
                        }
                        TextButton(onClick = onDismissError, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.todo_feedback_dismiss))
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = menuDescription
                    }.testTag("calendar_task_menu_${task.id}"),
                ) {
                    Icon(painterResource(R.drawable.ic_more_vertical), contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.todo_edit_title)) },
                        onClick = { menuExpanded = false; onOpen() },
                        modifier = Modifier.testTag("calendar_task_edit_${task.id}"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuExpanded = false; onDelete() },
                        modifier = Modifier.testTag("calendar_task_delete_${task.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun taskDateBadge(task: TodoTask, today: LocalDate, displayTime: DisplayTimePolicy): String {
    val date = requireNotNull(task.dueDate)
    val dateLabel = date.format(displayTime.date(FormatStyle.MEDIUM))
    val timeLabel = task.dueTime?.let(displayTime.time()::format)
    return when {
        date.isBefore(today) -> stringResource(R.string.calendar_task_overdue, dateLabel + (timeLabel?.let { " · $it" } ?: ""))
        date == today && timeLabel == null -> stringResource(R.string.calendar_task_anytime_today)
        date == today -> stringResource(R.string.calendar_task_due_today_time, requireNotNull(timeLabel))
        timeLabel == null -> stringResource(R.string.calendar_task_anytime_on, dateLabel)
        else -> stringResource(R.string.calendar_task_due_on_time, dateLabel, timeLabel)
    }
}

@Composable
private fun CalendarHabitsSection(
    state: CalendarUiState,
    displayTime: DisplayTimePolicy,
    onHabit: (UUID, LocalDate) -> Unit,
    onRetry: (CalendarSource) -> Unit,
) {
    LumaSectionSurface(modifier = Modifier.testTag("calendar_habits_section")) {
        LumaSectionHeader(title = stringResource(R.string.calendar_habits_heading))
        when (val habits = state.habits) {
            CalendarDataState.Loading -> CalendarSourceLoading(R.string.calendar_habits_loading, "calendar_habits_loading")
            CalendarDataState.Error -> CalendarSourceError(
                R.string.calendar_habits_error,
                { onRetry(CalendarSource.HABITS) },
                "calendar_habits_error",
            )
            is CalendarDataState.Ready -> {
                if (habits.value.isEmpty()) {
                    Text(stringResource(R.string.calendar_habits_empty), modifier = Modifier.testTag("calendar_habits_empty"))
                } else {
                    val zones = habits.value.map { it.zone }.distinct().filter { it != state.zone }
                    zones.forEach { zone ->
                        Text(
                            stringResource(R.string.calendar_habit_tracking_zone, zone.id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        habits.value.forEach { day ->
                            key(day.habit.id) {
                                CalendarHabitRow(day, displayTime.locale) { onHabit(day.habit.id, state.selectedDate) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHabitRow(day: HabitDay, locale: Locale, onClick: () -> Unit) {
    val target = day.schedule?.target
    val count = java.text.NumberFormat.getIntegerInstance(locale).format(day.count)
    val targetCount = target?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it) }
    val statusRes = when {
        day.skipRemaining -> R.string.habit_state_skipped
        day.state == HabitDayState.TARGET_REACHED -> R.string.habit_state_reached
        day.state == HabitDayState.PARTIAL -> R.string.habit_state_partial
        else -> R.string.habit_state_no_records
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("calendar_habit_${day.habit.id}"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(day.habit.name, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (targetCount != null) {
                    Text(stringResource(R.string.habit_progress, count, targetCount), style = MaterialTheme.typography.labelLarge)
                }
                Text(stringResource(statusRes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CalendarReflectionSection(
    state: CalendarUiState,
    onJournal: (com.luma.lifeos.domain.model.EventId) -> Unit,
    onWriteReflection: (LocalDate) -> Unit,
    onTimeline: (LocalDate) -> Unit,
    onRetry: (CalendarSource) -> Unit,
) {
    LumaSectionSurface(modifier = Modifier.testTag("calendar_reflection_section")) {
        LumaSectionHeader(title = stringResource(R.string.calendar_reflection_heading))
        when (val reflections = state.reflections) {
            CalendarDataState.Loading -> CalendarSourceLoading(R.string.calendar_reflection_loading, "calendar_reflection_loading")
            CalendarDataState.Error -> CalendarSourceError(
                R.string.calendar_reflection_error,
                { onRetry(CalendarSource.JOURNALS) },
                "calendar_reflection_error",
            )
            is CalendarDataState.Ready -> {
                val latest = reflections.value.latest
                if (latest == null) {
                    Text(stringResource(R.string.calendar_reflection_empty), modifier = Modifier.testTag("calendar_reflection_empty"))
                    TextButton(
                        onClick = { onWriteReflection(state.selectedDate) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("calendar_write_reflection"),
                    ) { Text(stringResource(R.string.calendar_write_reflection)) }
                } else {
                    val title = latest.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.calendar_reflection_default_title)
                    Surface(
                        onClick = { onJournal(latest.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("calendar_latest_reflection"),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(latest.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (reflections.value.total > 1) {
                        TextButton(
                            onClick = { onTimeline(state.selectedDate) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("calendar_view_all_journals"),
                        ) {
                            Text(pluralStringResource(R.plurals.calendar_view_all_journals, reflections.value.total, reflections.value.total))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSourceLoading(message: Int, tag: String) {
    Row(
        Modifier.fillMaxWidth().testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(stringResource(message))
    }
}

@Composable
private fun CalendarSourceError(message: Int, onRetry: () -> Unit, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(message), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}
