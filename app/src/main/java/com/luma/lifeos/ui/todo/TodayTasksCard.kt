package com.luma.lifeos.ui.todo

import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.LumaButton
import com.luma.lifeos.ui.common.TodoImportancePresentation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaSectionHeader
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.domain.todo.TodoBucket
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.todoBucket
import java.text.NumberFormat
import java.time.LocalDate
import java.util.UUID

@Composable
fun TodayTasksCard(
    state: TodayTasksState,
    actions: TodayTasksActionState,
    today: LocalDate,
    onTask: (UUID) -> Unit,
    onSetCompleted: (TodoTask, Boolean) -> Unit,
    onAllTasks: () -> Unit,
    onLoadMoreTasks: () -> Unit,
    onAddTask: () -> Unit,
    onRetry: () -> Unit,
    onRetryTask: (UUID) -> Unit,
    onDismissTaskError: (UUID) -> Unit,
) {
    LumaSectionSurface(modifier = Modifier.testTag("today_tasks_card")) {
        LumaSectionHeader(
            title = stringResource(R.string.todo_list_title),
            modifier = Modifier.testTag("today_tasks_heading"),
            actionLabel = stringResource(R.string.todo_today_view_all),
            onAction = onAllTasks,
            actionModifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight).testTag("today_tasks_all"),
        )
        when (state) {
            TodayTasksState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            ) {
                CircularProgressIndicator(Modifier.heightIn(min = 24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.todo_list_loading), modifier = Modifier.testTag("today_tasks_loading"))
            }
            TodayTasksState.Error -> Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                Text(
                    stringResource(R.string.todo_list_load_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("today_tasks_error"),
                )
                LumaTextButton(onClick = onRetry, modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight).testTag("today_tasks_retry")) {
                    Text(stringResource(R.string.retry))
                }
            }
            is TodayTasksState.Data -> {
                val countDescription = if (state.total > 0) {
                    stringResource(R.string.todo_today_count, NumberFormat.getIntegerInstance().format(state.total))
                } else {
                    null
                }
                if (state.total == 0 && state.tasks.isEmpty()) {
                    Text(stringResource(R.string.todo_today_empty), modifier = Modifier.testTag("today_tasks_empty"))
                } else if (state.tasks.isNotEmpty() || state.total > 0) {
                    val bucketRows = listOf(TodoBucket.OVERDUE, TodoBucket.TODAY)
                        .map { bucket ->
                            bucket to state.rows.filter { row ->
                                !row.task.isCompleted && todoBucket(row.task, today) == bucket
                            }
                        }
                        .filter { (_, rows) -> rows.isNotEmpty() }
                    val firstBucket = bucketRows.firstOrNull()?.first
                    firstBucket?.let { bucket ->
                        val groupLabel = stringResource(
                            if (bucket == TodoBucket.OVERDUE) R.string.todo_group_overdue
                            else R.string.todo_group_today,
                        )
                        val groupDescription = if (state.total in 1..2 && countDescription != null) {
                            listOf(groupLabel, countDescription).joinToString(
                                stringResource(R.string.accessibility_label_separator),
                            )
                        } else {
                            groupLabel
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().then(
                                if (state.total in 1..2) Modifier.testTag("today_tasks_count") else Modifier,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                groupLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (bucket == TodoBucket.OVERDUE) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics {
                                    heading()
                                    contentDescription = groupDescription
                                }.testTag("today_tasks_group_${bucket.name.lowercase()}"),
                            )
                            if (state.total > 2) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.tertiary,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f),
                                    ),
                                    modifier = Modifier.testTag("today_tasks_scroll_hint"),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.todo_today_scroll_for_more,
                                            NumberFormat.getIntegerInstance().format(state.total),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.testTag("today_tasks_count").padding(
                                            horizontal = LumaUi.ContentGap,
                                            vertical = LumaUi.RelatedGap,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    val taskListState = rememberLazyListState()
                    val scrollbarTrackColor = MaterialTheme.colorScheme.outlineVariant
                    val scrollbarThumbColor = MaterialTheme.colorScheme.outline
                    LazyColumn(
                        state = taskListState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                            .drawWithContent {
                                drawContent()
                                if (!taskListState.canScrollBackward && !taskListState.canScrollForward) {
                                    return@drawWithContent
                                }
                                val layoutInfo = taskListState.layoutInfo
                                val visibleItems = layoutInfo.visibleItemsInfo
                                val totalItems = layoutInfo.totalItemsCount
                                if (visibleItems.isEmpty() || totalItems == 0) return@drawWithContent

                                val trackInset = LumaUi.RelatedGap.toPx()
                                val trackTop = layoutInfo.viewportStartOffset.toFloat() + trackInset
                                val trackBottom = layoutInfo.viewportEndOffset.toFloat() - trackInset
                                val trackHeight = (trackBottom - trackTop).coerceAtLeast(1f)
                                val averageItemHeight = visibleItems
                                    .map { it.size.toFloat() }
                                    .average()
                                    .toFloat()
                                    .coerceAtLeast(1f)
                                val visibleFraction = (visibleItems.size.toFloat() / totalItems).coerceIn(0f, 1f)
                                val thumbHeight = (trackHeight * visibleFraction)
                                    .coerceAtLeast(LumaUi.IconSize.toPx())
                                    .coerceAtMost(trackHeight)
                                val maxScroll = (averageItemHeight * totalItems - trackHeight).coerceAtLeast(1f)
                                val scrollOffset = (
                                    taskListState.firstVisibleItemIndex * averageItemHeight +
                                        taskListState.firstVisibleItemScrollOffset
                                    ).coerceAtLeast(0f)
                                val thumbTop = trackTop +
                                    (scrollOffset / maxScroll).coerceIn(0f, 1f) * (trackHeight - thumbHeight)
                                val thumbWidth = LumaUi.RelatedGap.toPx()
                                val trackWidth = 1.dp.toPx()
                                val thumbLeft = if (layoutDirection == LayoutDirection.Ltr) {
                                    size.width - thumbWidth
                                } else {
                                    0f
                                }
                                val trackLeft = thumbLeft + (thumbWidth - trackWidth) / 2f

                                drawRoundRect(
                                    color = scrollbarTrackColor.copy(alpha = 0.7f),
                                    topLeft = Offset(trackLeft, trackTop),
                                    size = Size(trackWidth, trackHeight),
                                    cornerRadius = CornerRadius(trackWidth / 2f),
                                )
                                drawRoundRect(
                                    color = scrollbarThumbColor,
                                    topLeft = Offset(thumbLeft, thumbTop),
                                    size = Size(thumbWidth, thumbHeight),
                                    cornerRadius = CornerRadius(thumbWidth / 2f),
                                )
                            }
                            .testTag("today_tasks_scroll"),
                        contentPadding = PaddingValues(end = LumaUi.ContentGap),
                    ) {
                        bucketRows.forEachIndexed { index, (bucket, bucketTasks) ->
                            if (index > 0) {
                                item(key = "today_tasks_group_${bucket.name}") {
                                    Text(
                                        stringResource(
                                            if (bucket == TodoBucket.OVERDUE) R.string.todo_group_overdue
                                            else R.string.todo_group_today,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (bucket == TodoBucket.OVERDUE) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { heading() }
                                            .testTag("today_tasks_group_${bucket.name.lowercase()}"),
                                    )
                                }
                            }
                            items(bucketTasks, key = { it.task.id.toString() }) { row ->
                                val task = row.task
                                val focusRequester = remember(task.id) { FocusRequester() }
                                val rowError = actions.rowErrors[task.id]
                                TodoRow(
                                    task = task,
                                    today = today,
                                    completedList = false,
                                    canOpen = rowError != TodoListRowError.MISSING,
                                    canMutate = rowError != TodoListRowError.CONFLICT && rowError != TodoListRowError.MISSING,
                                    pending = task.id in actions.pendingIntents || task.id in actions.inFlight ||
                                        (actions.familyAction.busy && actions.familyAction.targetId == task.id),
                                    error = rowError,
                                    onOpen = { onTask(task.id) },
                                    onSetCompleted = { onSetCompleted(task, it) },
                                    onRetry = { onRetryTask(task.id) },
                                    onDismissError = { onDismissTaskError(task.id) },
                                    focusRequester = focusRequester,
                                    compact = true,
                                    omitNoneImportance = true,
                                    importancePresentation = TodoImportancePresentation.EXPRESSIVE,
                                    rowContext = row,
                                )
                            }
                        }
                        if (state.tasks.size < state.total) {
                            item(key = "today_tasks_load_more") {
                                LumaTextButton(
                                    onClick = onLoadMoreTasks,
                                    modifier = Modifier.fillMaxWidth()
                                        .heightIn(min = LumaUi.MinTouchTargetHeight)
                                        .testTag("today_tasks_load_more"),
                                ) { Text(stringResource(R.string.calendar_load_more)) }
                            }
                        }
                    }
                }
            }
        }
        val visibleTaskIds = state.visibleTaskIds()
        val offPreviewIntents = actions.pendingIntents.values
            .filter { it.taskId !in visibleTaskIds }
            .sortedBy { it.title.lowercase() }
        val offPreviewErrors = actions.rowFeedback.entries
            .filter { (taskId, _) -> taskId !in visibleTaskIds && taskId !in actions.pendingIntents }
            .sortedBy { it.value.title.lowercase() }
        if (offPreviewIntents.isNotEmpty() || offPreviewErrors.isNotEmpty()) {
            HorizontalDivider()
            Text(
                stringResource(R.string.todo_today_task_feedback),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }.testTag("today_tasks_feedback_heading"),
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                    .testTag("today_tasks_feedback_scroll"),
                verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            ) {
                offPreviewIntents.forEach { intent ->
                    val feedback = actions.rowFeedback[intent.taskId]
                    TodayTaskRecoverySummary(
                        taskId = intent.taskId,
                        title = intent.title,
                        completesTask = intent.completed,
                        error = feedback?.error,
                        pending = intent.taskId in actions.inFlight,
                        retryEnabled = feedback?.error == TodoListRowError.FAILED && intent.taskId !in actions.inFlight,
                        onRetry = { onRetryTask(intent.taskId) },
                        onOpen = { onTask(intent.taskId) },
                        onDismiss = { onDismissTaskError(intent.taskId) },
                    )
                }
                offPreviewErrors.forEach { (taskId, feedback) ->
                    TodayTaskRecoverySummary(
                        taskId = taskId,
                        title = feedback.title,
                        completesTask = feedback.completesTask,
                        error = feedback.error,
                        pending = false,
                        retryEnabled = false,
                        onRetry = {},
                        onOpen = { onTask(taskId) },
                        onDismiss = { onDismissTaskError(taskId) },
                    )
                }
            }
        }
        val addTaskLabel = stringResource(R.string.todo_list_add)
        LumaButton(
            onClick = onAddTask,
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = LumaUi.MinTouchTargetHeight)
                .semantics { contentDescription = addTaskLabel }
                .testTag("today_tasks_add"),
        ) {
            Text(addTaskLabel)
        }
    }
}

@Composable
internal fun TodayTaskRecoverySummary(
    taskId: UUID,
    title: String,
    completesTask: Boolean,
    error: TodoListRowError?,
    pending: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("today_task_feedback_$taskId"),
        verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(
                R.string.todo_today_task_action,
                stringResource(if (completesTask) R.string.todo_mark_complete else R.string.todo_reopen),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(
                when {
                    pending -> if (completesTask) R.string.todo_completing else R.string.todo_reopening
                    error == TodoListRowError.CONFLICT -> R.string.todo_list_row_conflict
                    error == TodoListRowError.MISSING -> R.string.todo_list_row_missing
                    else -> R.string.todo_list_row_failed
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("today_task_feedback_status_$taskId"),
        )
        if (retryEnabled || error == TodoListRowError.CONFLICT || error == TodoListRowError.MISSING) {
            Column(Modifier.fillMaxWidth()) {
                if (retryEnabled) {
                    LumaTextButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("today_task_feedback_retry_$taskId"),
                    ) {
                        Text(stringResource(R.string.todo_retry_action))
                    }
                }
                if (error == TodoListRowError.CONFLICT) {
                    LumaTextButton(
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth().heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("today_task_feedback_open_$taskId"),
                    ) {
                        Text(stringResource(R.string.todo_status_open))
                    }
                }
                if (error == TodoListRowError.CONFLICT || error == TodoListRowError.MISSING) {
                    LumaTextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("today_task_feedback_dismiss_$taskId"),
                    ) {
                        Text(stringResource(R.string.todo_feedback_dismiss))
                    }
                }
            }
        }
    }
}
