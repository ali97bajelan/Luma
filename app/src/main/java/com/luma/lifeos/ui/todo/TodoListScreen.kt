package com.luma.lifeos.ui.todo

import com.luma.lifeos.ui.common.LumaButton

import com.luma.lifeos.ui.common.LumaFilterChip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import com.luma.lifeos.ui.common.LumaOutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.PageHeader
import com.luma.lifeos.ui.common.PageNavigation
import com.luma.lifeos.ui.common.LumaListSurface
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.domain.todo.TodoBucket
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoSort
import com.luma.lifeos.domain.todo.TodoTaskRow
import com.luma.lifeos.domain.todo.todoBucket
import java.text.NumberFormat
import java.util.UUID
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    state: TodoListUiState,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (UUID) -> Unit,
    onSelectCompleted: (Boolean) -> Unit,
    onSetCompleted: (UUID, Boolean) -> Unit,
    onRetry: (UUID) -> Unit,
    onDismissRowError: (UUID) -> Unit,
    onRetryLoad: () -> Unit,
    onLoadMore: () -> Unit,
    undoFeedback: @Composable () -> Unit,
    onImportanceFilter: (TodoImportance?) -> Unit = {},
    onSort: (TodoSort) -> Unit = {},
    onResetQuery: () -> Unit = {},
    onCompleteParent: () -> Unit = {},
    onDismissParentOffer: () -> Unit = {},
    onSetExpanded: (UUID, Boolean) -> Unit = { _, _ -> },
    onLoadMoreChildren: (UUID) -> Unit = {},
    onRetryChildren: (UUID) -> Unit = {},
) {
    val stateHolder = rememberSaveableStateHolder()
    val focusRequesters = remember { mutableStateMapOf<UUID, FocusRequester>() }
    val pendingFocus = remember { mutableStateOf<PendingRowFocus?>(null) }
    val addFocusRequester = remember { FocusRequester() }
    val openTabFocusRequester = remember { FocusRequester() }
    val count = NumberFormat.getIntegerInstance().format(state.page.total)
    val listRows = if (state.rowPage.items.isNotEmpty() || state.page.items.isEmpty()) {
        state.rowPage.items
    } else {
        state.page.items.filter { it.parentId == null }.map { TodoTaskRow(it, parent = null, progress = null) }
    }
    val isFlatList = state.completed || state.importanceFilter != null || state.sort == TodoSort.IMPORTANCE
    val groups = remember(listRows, state.today) {
        TodoBucket.entries.map { bucket -> bucket to listRows.filter { todoBucket(it.task, state.today) == bucket } }
    }
    val visibleRows = if (isFlatList) listRows else groups.flatMap { it.second }
    val visibleTasks = remember(visibleRows, state.childrenByRoot, state.expandedRootIds, state.importanceFilter) {
        buildList {
            visibleRows.forEach { row ->
                add(row.task)
                if (state.importanceFilter == null && row.task.parentId == null && row.task.id in state.expandedRootIds) {
                    addAll(state.childrenByRoot[row.task.id]?.items.orEmpty().map { it.task })
                }
            }
        }
    }
    val visibleIds = remember(visibleTasks) { visibleTasks.mapTo(mutableSetOf()) { it.id } }
    val offPageIntents = state.pendingIntents.values.filter { it.taskId !in visibleIds }
    val offPageErrors = state.rowErrors.entries.mapNotNull { (taskId, error) ->
        val title = state.rowErrorTitles[taskId]
        if (taskId !in visibleIds && taskId !in state.pendingIntents && title != null) {
            Triple(taskId, error, title)
        } else null
    }
    val undoItemCount = if (state.undoableCompletion != null) 1 else 0
    val parentOfferItemCount = if (state.parentCompletionOffer != null) 1 else 0
    val recoveryItemCount = undoItemCount + parentOfferItemCount + offPageIntents.size + offPageErrors.size
    val taskLazyIndexes = remember(
        listRows,
        groups,
        isFlatList,
        state.importanceFilter,
        state.expandedRootIds,
        state.childrenByRoot,
        state.loadingChildren,
        state.childLoadErrors,
        recoveryItemCount,
    ) {
        buildMap<UUID, Int> {
            var index = recoveryItemCount
            fun appendRows(rows: List<TodoTaskRow>) {
                rows.forEach { row ->
                    put(row.task.id, index++)
                    val rootId = row.task.id
                    val expandableRoot = state.importanceFilter == null &&
                        row.task.parentId == null && row.progress != null
                    if (expandableRoot && rootId in state.expandedRootIds) {
                        index++ // Subtasks heading.
                        val childPage = state.childrenByRoot[rootId]
                        if (childPage == null && rootId in state.loadingChildren) index++
                        childPage?.items?.forEach { child -> put(child.task.id, index++) }
                        val childError = rootId in state.childLoadErrors
                        if (childError) index++
                        if (childPage != null && childPage.items.size < childPage.total && !childError) index++
                    }
                }
            }
            if (isFlatList) {
                appendRows(listRows)
            } else {
                groups.forEach { (_, rows) ->
                    if (rows.isNotEmpty()) {
                        index++ // Date bucket heading.
                        appendRows(rows)
                    }
                }
            }
        }
    }
    val onRowStatusAction: (UUID, Boolean) -> Unit = { taskId, completed ->
        pendingFocus.value = PendingRowFocus(
            taskId,
            state.completed,
            visibleTasks.indexOfFirst { it.id == taskId },
            completed,
        )
        onSetCompleted(taskId, completed)
    }

    Scaffold(
        modifier = Modifier.testTag("screen_todo_list"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(
                title = stringResource(R.string.todo_list_title),
                navigation = PageNavigation.BACK,
                onNavigate = onBack,
                navigationTag = "todo_list_back",
                actions = {
                    LumaTextButton(
                        onClick = onAddTask,
                        modifier = Modifier.heightIn(min = 48.dp).focusRequester(addFocusRequester)
                            .testTag("todo_list_add"),
                    ) {
                        Text(stringResource(R.string.todo_list_add))
                    }
                },
            )
        },
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
         Column(
            Modifier.fillMaxSize().padding(horizontal = LumaUi.PageGutter),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
         ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                LumaFilterChip(
                    selected = !state.completed,
                    onClick = { onSelectCompleted(false) },
                    label = { Text(stringResource(R.string.todo_list_open_tab)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(openTabFocusRequester)
                        .testTag("todo_list_open_tab"),
                )
                LumaFilterChip(
                    selected = state.completed,
                    onClick = { onSelectCompleted(true) },
                    label = { Text(stringResource(R.string.todo_list_completed_tab)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("todo_list_completed_tab"),
                )
            }
            Text(
                stringResource(R.string.todo_list_filter_heading),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LumaFilterChip(
                    selected = state.importanceFilter == null,
                    onClick = { onImportanceFilter(null) },
                    label = { Text(stringResource(R.string.todo_list_filter_all)) },
                    modifier = Modifier.testTag("todo_list_importance_filter_all"),
                )
                listOf(
                    TodoImportance.HIGH to R.string.todo_importance_high,
                    TodoImportance.MEDIUM to R.string.todo_importance_medium,
                    TodoImportance.LOW to R.string.todo_importance_low,
                    TodoImportance.NONE to R.string.todo_importance_none,
                ).forEach { (importance, labelResource) ->
                    LumaFilterChip(
                        selected = state.importanceFilter == importance,
                        onClick = { onImportanceFilter(importance) },
                        label = { Text(stringResource(labelResource)) },
                        modifier = Modifier.testTag("todo_list_importance_filter_${importance.name.lowercase()}"),
                    )
                }
            }
            Text(
                stringResource(R.string.todo_list_sort_heading),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LumaFilterChip(
                    selected = state.sort == TodoSort.DATE,
                    onClick = { onSort(TodoSort.DATE) },
                    label = { Text(stringResource(R.string.todo_list_sort_date)) },
                    modifier = Modifier.testTag("todo_list_sort_date"),
                )
                LumaFilterChip(
                    selected = state.sort == TodoSort.IMPORTANCE,
                    onClick = { onSort(TodoSort.IMPORTANCE) },
                    label = { Text(stringResource(R.string.todo_list_sort_importance)) },
                    modifier = Modifier.testTag("todo_list_sort_importance"),
                )
            }
            if (state.sort == TodoSort.IMPORTANCE) {
                Text(
                    stringResource(R.string.todo_list_importance_order_heading),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                        .testTag("todo_list_importance_order_heading"),
                )
            }
            Text(
                stringResource(
                    if (state.importanceFilter != null) R.string.todo_list_matching_count
                    else if (state.completed) R.string.todo_list_completed_count else R.string.todo_list_open_count,
                    count,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("todo_list_count"),
            )
            if (state.loadError == TodoListLoadError.REFRESH) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.todo_list_refresh_error),
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                    )
                    LumaTextButton(
                        onClick = onRetryLoad,
                        enabled = !state.refreshing,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("todo_list_retry_refresh"),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            if (state.loading || state.refreshing) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.heightIn(min = 24.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(
                            if (state.loading && state.page.items.isEmpty()) R.string.todo_list_loading
                            else R.string.todo_list_refreshing,
                        ),
                    )
                }
            }
            if (state.loadError == TodoListLoadError.INITIAL) {
                TodoListErrorCard(
                    message = R.string.todo_list_load_error,
                    onRetry = onRetryLoad,
                    retryTag = "todo_list_retry_initial",
                )
            }

            // Do not compose a loading-only list: LazyListState would clamp its saved index to zero.
            if (state.dataFresh || state.page.items.isNotEmpty() || recoveryItemCount > 0) {
                stateHolder.SaveableStateProvider(
                    "${if (state.completed) "completed" else "open"}:" +
                        "${state.importanceFilter?.code ?: -1}:${state.sort.name}",
                ) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(
                        state.completed,
                        visibleIds,
                        state.pendingIntents.keys,
                        state.inFlight,
                        state.dataFresh,
                        state.rowErrors,
                        state.undoableCompletion,
                    ) {
                        val focus = pendingFocus.value ?: return@LaunchedEffect
                        if (focus.completedMode != state.completed) {
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }
                        if (focus.taskId in state.inFlight) return@LaunchedEffect
                        val pendingIntentIndex = offPageIntents.indexOfFirst { it.taskId == focus.taskId }
                        if (pendingIntentIndex >= 0) {
                            listState.animateScrollToItem(undoItemCount + parentOfferItemCount + pendingIntentIndex)
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }
                        if (focus.taskId in state.pendingIntents) return@LaunchedEffect
                        if (!state.dataFresh) return@LaunchedEffect
                        if (focus.taskId in visibleIds) {
                            val matchingResult = state.undoableCompletion?.let {
                                it.taskId == focus.taskId && it.completed == focus.desiredCompleted
                            } == true
                            if (!matchingResult) pendingFocus.value = null
                            return@LaunchedEffect
                        }

                        val matchingUndo = state.undoableCompletion?.let {
                            it.taskId == focus.taskId && it.completed == focus.desiredCompleted
                        } == true
                        if (matchingUndo) {
                            listState.animateScrollToItem(0)
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }
                        val terminalRecoveryIndex = offPageErrors.indexOfFirst { it.first == focus.taskId }
                        if (terminalRecoveryIndex >= 0) {
                            listState.animateScrollToItem(undoItemCount + parentOfferItemCount + offPageIntents.size + terminalRecoveryIndex)
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }

                        val targetIndex = focus.originalIndex.coerceAtMost(visibleTasks.lastIndex)
                        if (targetIndex < 0) {
                            if (state.completed) openTabFocusRequester.requestFocus() else addFocusRequester.requestFocus()
                        } else {
                            val target = visibleTasks[targetIndex]
                            val lazyIndex = taskLazyIndexes[target.id] ?: recoveryItemCount
                            listState.animateScrollToItem(lazyIndex)
                            val requester = snapshotFlow { focusRequesters[target.id] }.filterNotNull().first()
                            requester.requestFocus()
                        }
                        pendingFocus.value = null
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("todo_list_rows"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.undoableCompletion?.let { result ->
                            item(key = "undo:${result.taskId}:${result.resultingRevision}") {
                                undoFeedback()
                            }
                        }
                        state.parentCompletionOffer?.let { offer ->
                            item(key = "complete_parent_offer:${offer.parentTask.id}") {
                                TodoParentCompletionOfferCard(
                                    offer = offer,
                                    enabled = state.dataFresh && !state.undoing &&
                                        offer.parentTask.id !in state.inFlight && offer.parentTask.id !in state.pendingIntents,
                                    onComplete = onCompleteParent,
                                    onDismiss = onDismissParentOffer,
                                )
                            }
                        }
                        offPageIntents.forEach { intent ->
                            item(key = "recovery:intent:${intent.taskId}:${intent.resultingRevision}") {
                                TodoPendingActionCard(
                                    intent = intent,
                                    error = state.rowErrors[intent.taskId],
                                    pending = intent.taskId in state.inFlight,
                                    retryEnabled = state.dataFresh,
                                    onRetry = { onRetry(intent.taskId) },
                                    onOpen = { onOpenTask(intent.taskId) },
                                )
                            }
                        }
                        offPageErrors.forEach { (taskId, error, title) ->
                            item(key = "recovery:task:$taskId") {
                                TodoTerminalRowErrorCard(
                                    taskId = taskId,
                                    title = title,
                                    error = error,
                                    onOpen = { onOpenTask(taskId) },
                                    onDismiss = { onDismissRowError(taskId) },
                                )
                            }
                        }
                        if (state.dataFresh && state.page.items.isEmpty()) {
                            item(key = "empty") {
                                LumaSectionSurface(Modifier.testTag("todo_list_empty")) {
                                    Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap)) {
                                        Text(
                                            stringResource(
                                                when {
                                                    state.importanceFilter != null -> R.string.todo_list_empty_matching
                                                    state.completed -> R.string.todo_list_empty_completed
                                                    else -> R.string.todo_list_empty_open
                                                },
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        if (state.importanceFilter != null || state.sort != TodoSort.DATE) {
                                            LumaTextButton(
                                                onClick = onResetQuery,
                                                modifier = Modifier.testTag("todo_list_reset_query"),
                                            ) {
                                                Text(stringResource(R.string.todo_list_reset_query))
                                            }
                                        }
                                        if (state.completed) {
                                            LumaTextButton(
                                                onClick = { onSelectCompleted(false) },
                                                modifier = Modifier.heightIn(min = 48.dp),
                                            ) {
                                                Text(stringResource(R.string.todo_list_open_tab))
                                            }
                                        } else {
                                            LumaButton(
                                                onClick = onAddTask,
                                                modifier = Modifier.heightIn(min = 48.dp),
                                            ) {
                                                Text(stringResource(R.string.todo_list_add))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (listRows.isNotEmpty()) {
                            if (isFlatList) {
                                todoFamilyRows(
                                    rows = listRows,
                                    state = state,
                                    focusRequesters = focusRequesters,
                                    onOpenTask = onOpenTask,
                                    onStatusAction = onRowStatusAction,
                                    onRetry = onRetry,
                                    onDismissRowError = onDismissRowError,
                                    onSetExpanded = onSetExpanded,
                                    onLoadMoreChildren = onLoadMoreChildren,
                                    onRetryChildren = onRetryChildren,
                                )
                            } else {
                                groups.filter { it.second.isNotEmpty() }.forEach { (bucket, rows) ->
                                    item(key = "bucket:${bucket.name}") {
                                        Text(
                                            stringResource(bucket.headingResource()),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
                                                .testTag("todo_group_${bucket.name.lowercase()}"),
                                        )
                                    }
                                    todoFamilyRows(
                                        rows = rows,
                                        state = state,
                                        focusRequesters = focusRequesters,
                                        onOpenTask = onOpenTask,
                                        onStatusAction = onRowStatusAction,
                                        onRetry = onRetry,
                                        onDismissRowError = onDismissRowError,
                                        onSetExpanded = onSetExpanded,
                                        onLoadMoreChildren = onLoadMoreChildren,
                                        onRetryChildren = onRetryChildren,
                                    )
                                }
                            }
                        }
                        if (state.loadError == TodoListLoadError.PAGE) {
                            item(key = "page_error") {
                                TodoListErrorCard(
                                    message = R.string.todo_list_page_error,
                                    onRetry = onRetryLoad,
                                    retryTag = "todo_list_retry_page",
                                )
                            }
                        }
                        if (state.dataFresh && state.loadError != TodoListLoadError.PAGE &&
                            state.page.items.size < state.page.total
                        ) {
                            item(key = "load_more") {
                                LumaTextButton(
                                    onClick = onLoadMore,
                                    enabled = !state.loadingMore,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("todo_list_load_more"),
                                ) {
                                    if (state.loadingMore) {
                                        CircularProgressIndicator(Modifier.heightIn(min = 20.dp), strokeWidth = 2.dp)
                                    }
                                    Text(
                                        stringResource(
                                            if (state.loadingMore) R.string.todo_list_loading_more
                                            else R.string.todo_list_load_more,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun TodoListRowEntry(
    row: TodoTaskRow,
    state: TodoListUiState,
    focusRequesters: MutableMap<UUID, FocusRequester>,
    allowFamilyExpansion: Boolean,
    onOpenTask: (UUID) -> Unit,
    onStatusAction: (UUID, Boolean) -> Unit,
    onRetry: (UUID) -> Unit,
    onDismissRowError: (UUID) -> Unit,
    onSetExpanded: (UUID, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = row.task
    val rowError = state.rowErrors[task.id]
    val focusRequester = remember(task.id) { FocusRequester() }
    DisposableEffect(task.id, focusRequester) {
        focusRequesters[task.id] = focusRequester
        onDispose {
            if (focusRequesters[task.id] === focusRequester) focusRequesters.remove(task.id)
        }
    }
    val childReadUnavailable = task.parentId?.let { parentId ->
        parentId in state.loadingChildren || parentId in state.childLoadErrors
    } == true
    TodoRow(
        task = task,
        today = state.today,
        completedList = task.isCompleted,
        canOpen = rowError != TodoListRowError.MISSING,
        canMutate = state.dataFresh && !childReadUnavailable && rowError != TodoListRowError.CONFLICT &&
            rowError != TodoListRowError.MISSING,
        pending = task.id in state.inFlight || task.id in state.pendingIntents ||
            (state.undoing && state.undoableCompletion?.taskId == task.id),
        error = rowError,
        onOpen = { onOpenTask(task.id) },
        onSetCompleted = { completed -> onStatusAction(task.id, completed) },
        onRetry = { onRetry(task.id) },
        onDismissError = { onDismissRowError(task.id) },
        focusRequester = focusRequester,
        modifier = modifier,
        compact = task.parentId != null,
        rowContext = row,
    )
    if (allowFamilyExpansion && task.parentId == null && row.progress != null) {
        val expanded = task.id in state.expandedRootIds
        val disclosureState = stringResource(
            if (expanded) R.string.todo_list_subtasks_expanded else R.string.todo_list_subtasks_collapsed,
        )
        LumaTextButton(
            onClick = { onSetExpanded(task.id, !expanded) },
            modifier = Modifier.fillMaxWidth().padding(start = LumaUi.ContentGap).heightIn(min = 48.dp)
                .semantics { stateDescription = disclosureState }
                .testTag("todo_list_subtasks_toggle_${task.id}"),
        ) {
            Text(stringResource(if (expanded) R.string.todo_list_hide_subtasks else R.string.todo_list_show_subtasks))
        }
        val progress = requireNotNull(row.progress)
        if (!state.completed && !task.isCompleted && progress.completed == progress.total &&
            state.parentCompletionOffer?.parentTask?.id != task.id
        ) {
            LumaOutlinedButton(
                onClick = { onStatusAction(task.id, true) },
                enabled = state.dataFresh && !state.undoing && rowError != TodoListRowError.CONFLICT &&
                    rowError != TodoListRowError.MISSING && task.id !in state.inFlight && task.id !in state.pendingIntents,
                modifier = Modifier.fillMaxWidth().padding(start = LumaUi.ContentGap)
                    .testTag("todo_list_complete_parent_${task.id}"),
            ) { Text(stringResource(R.string.todo_complete_parent)) }
        }
    }
}

private fun LazyListScope.todoFamilyRows(
    rows: List<TodoTaskRow>,
    state: TodoListUiState,
    focusRequesters: MutableMap<UUID, FocusRequester>,
    onOpenTask: (UUID) -> Unit,
    onStatusAction: (UUID, Boolean) -> Unit,
    onRetry: (UUID) -> Unit,
    onDismissRowError: (UUID) -> Unit,
    onSetExpanded: (UUID, Boolean) -> Unit,
    onLoadMoreChildren: (UUID) -> Unit,
    onRetryChildren: (UUID) -> Unit,
) {
    rows.forEach { row ->
        val rootId = row.task.id
        val canExpand = state.importanceFilter == null && row.task.parentId == null && row.progress != null
        item(key = "task:$rootId") {
            TodoListRowEntry(
                row = row,
                state = state,
                focusRequesters = focusRequesters,
                allowFamilyExpansion = canExpand,
                onOpenTask = onOpenTask,
                onStatusAction = onStatusAction,
                onRetry = onRetry,
                onDismissRowError = onDismissRowError,
                onSetExpanded = onSetExpanded,
            )
        }
        if (!canExpand || rootId !in state.expandedRootIds) return@forEach

        item(key = "subtasks_header:$rootId") {
            Text(
                stringResource(R.string.todo_subtasks_title),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().padding(start = LumaUi.ContentGap)
                    .semantics { heading() },
            )
        }
        val page = state.childrenByRoot[rootId]
        val loading = rootId in state.loadingChildren
        val error = rootId in state.childLoadErrors
        if (page == null && loading) {
            item(key = "subtasks_loading:$rootId") {
                Text(
                    stringResource(R.string.todo_subtasks_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = LumaUi.ContentGap),
                )
            }
        }
        page?.items?.forEach { child ->
            item(key = "task:${child.task.id}") {
                TodoListRowEntry(
                    row = child,
                    state = state,
                    focusRequesters = focusRequesters,
                    allowFamilyExpansion = false,
                    onOpenTask = onOpenTask,
                    onStatusAction = onStatusAction,
                    onRetry = onRetry,
                    onDismissRowError = onDismissRowError,
                    onSetExpanded = onSetExpanded,
                    modifier = Modifier.padding(start = LumaUi.ContentGap),
                )
            }
        }
        if (error) {
            item(key = "subtasks_error:$rootId") {
                Column(Modifier.padding(start = LumaUi.ContentGap)) {
                    TodoListErrorCard(
                        message = R.string.todo_subtasks_load_error,
                        onRetry = { onRetryChildren(rootId) },
                        retryTag = "todo_list_subtasks_retry_$rootId",
                    )
                }
            }
        }
        if (page != null && page.items.size < page.total && !error) {
            item(key = "subtasks_load_more:$rootId") {
                LumaTextButton(
                    onClick = { onLoadMoreChildren(rootId) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(start = LumaUi.ContentGap).heightIn(min = 48.dp)
                        .testTag("todo_list_subtasks_load_more_$rootId"),
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.heightIn(min = 20.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        stringResource(
                            if (loading) R.string.todo_list_loading_more else R.string.todo_subtasks_load_more,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoParentCompletionOfferCard(
    offer: TodoParentCompletionOffer,
    enabled: Boolean,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    LumaSectionSurface(Modifier.testTag("todo_list_parent_completion_offer_${offer.parentTask.id}")) {
        Column(
            Modifier.fillMaxWidth().padding(LumaUi.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
        ) {
            Text(
                stringResource(R.string.todo_parent_all_subtasks_complete, offer.parentTask.title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.todo_subtask_progress, offer.progress.completed, offer.progress.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LumaButton(
                onClick = onComplete,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("todo_list_complete_parent_${offer.parentTask.id}"),
            ) { Text(stringResource(R.string.todo_complete_parent)) }
            LumaTextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("todo_list_dismiss_parent_offer_${offer.parentTask.id}"),
            ) { Text(stringResource(R.string.todo_parent_offer_not_now)) }
        }
    }
}

private data class PendingRowFocus(
    val taskId: UUID,
    val completedMode: Boolean,
    val originalIndex: Int,
    val desiredCompleted: Boolean,
)

@Composable
private fun TodoPendingActionCard(
    intent: TodoMutationIntent,
    error: TodoListRowError?,
    pending: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    LumaListSurface(Modifier.testTag("todo_pending_${intent.taskId}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
            Text(intent.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    when {
                        pending -> if (intent.completed) R.string.todo_completing else R.string.todo_reopening
                        error == TodoListRowError.CONFLICT -> R.string.todo_list_row_conflict
                        error == TodoListRowError.MISSING -> R.string.todo_list_row_missing
                        else -> R.string.todo_list_row_failed
                    },
                ),
                color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            if (!pending) {
                LumaTextButton(onClick = onRetry, enabled = retryEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.todo_retry_action))
                }
                if (error == TodoListRowError.CONFLICT) {
                    LumaTextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.todo_status_open))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoTerminalRowErrorCard(
    taskId: UUID,
    title: String,
    error: TodoListRowError,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    LumaListSurface(Modifier.testTag("todo_row_error_$taskId")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (error == TodoListRowError.MISSING) R.string.todo_list_row_missing
                    else R.string.todo_list_row_conflict,
                ),
                color = MaterialTheme.colorScheme.error,
            )
            if (error == TodoListRowError.CONFLICT) {
                LumaTextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.todo_status_open))
                }
            }
            LumaTextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.todo_feedback_dismiss))
            }
        }
    }
}

@Composable
private fun TodoListErrorCard(message: Int, onRetry: () -> Unit, retryTag: String) {
    LumaSectionSurface {
        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            LumaTextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag(retryTag)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

private fun TodoBucket.headingResource(): Int = when (this) {
    TodoBucket.OVERDUE -> R.string.todo_group_overdue
    TodoBucket.TODAY -> R.string.todo_group_today
    TodoBucket.UPCOMING -> R.string.todo_group_upcoming
    TodoBucket.NO_DATE -> R.string.todo_group_no_date
}
