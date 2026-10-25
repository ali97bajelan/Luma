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
    onLoadMoreMemories: () -> Unit,
    onSetCompleted: (TodoTask, Boolean) -> Unit,
    onRetry: (CalendarSource) -> Unit,
    onRetryTask: (UUID) -> Unit,
    onDismissTaskError: (UUID) -> Unit,
    onMemory: (com.luma.lifeos.domain.model.EventId) -> Unit,
    onAddMemory: (LocalDate) -> Unit,
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
            CalendarMemoriesSection(state, onMemory, onAddMemory, onLoadMoreMemories, onRetry)
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
                count = state.selectedMemoryCount,
                plurals = R.plurals.calendar_memories_count,
                label = R.string.calendar_legend_memories,
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
private fun CalendarMemoriesSection(
    state: CalendarUiState,
    onMemory: (com.luma.lifeos.domain.model.EventId) -> Unit,
    onAddMemory: (LocalDate) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: (CalendarSource) -> Unit,
) {
    LumaSectionSurface(modifier = Modifier.testTag("calendar_memories_section")) {
        LumaSectionHeader(
            title = stringResource(R.string.calendar_memories_heading),
            actionLabel = stringResource(R.string.calendar_add_memory),
            onAction = { onAddMemory(state.selectedDate) },
            actionModifier = Modifier.heightIn(min = 48.dp).testTag("calendar_add_memory"),
        )
        when (val memories = state.memoryPage) {
            CalendarDataState.Loading -> CalendarSourceLoading(R.string.calendar_memories_loading, "calendar_memories_loading")
            CalendarDataState.Error -> CalendarSourceError(
                R.string.calendar_memories_error,
                { onRetry(CalendarSource.MEMORIES) },
                "calendar_memories_error",
            )
            is CalendarDataState.Ready -> {
                if (memories.value.items.isEmpty()) {
                    Text(stringResource(R.string.calendar_memories_empty), modifier = Modifier.testTag("calendar_memories_empty"))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        memories.value.items.forEach { memory ->
                            key(memory.id) {
                                Surface(
                                    onClick = { onMemory(memory.id) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("calendar_memory_${memory.id}"),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            memory.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.calendar_memory_default_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (memory.preview.isNotBlank()) {
                                            Text(memory.preview, style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                when {
                    state.loadingMoreMemories -> CalendarSourceLoading(
                        R.string.calendar_memories_loading, "calendar_memories_loading_more",
                    )
                    state.loadMoreMemoriesError -> CalendarSourceError(
                        R.string.calendar_memories_error,
                        { onRetry(CalendarSource.MEMORIES) },
                        "calendar_memories_more_error",
                    )
                    memories.value.items.size < memories.value.total -> TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("calendar_memories_load_more"),
                    ) { Text(stringResource(R.string.calendar_load_more)) }
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
