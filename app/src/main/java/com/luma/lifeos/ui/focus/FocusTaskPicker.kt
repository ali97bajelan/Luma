package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.PageNavigation
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException

@Composable
internal fun FocusTaskPickerRoute(
    repository: TodoRepository,
    isAuthorized: Boolean,
    onBack: () -> Unit,
    onSelectTask: (java.util.UUID) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var nextRequestId by remember { mutableLongStateOf(0L) }
    var state by remember { mutableStateOf(FocusTaskPickerState()) }

    LaunchedEffect(repository, isAuthorized, query, offset, retry) {
        val requestId = ++nextRequestId
        if (!isAuthorized) {
            state = FocusTaskPickerState(query = query, offset = offset, requestId = requestId)
            return@LaunchedEffect
        }
        state = FocusTaskPickerState(query = query, offset = offset, loading = true, requestId = requestId)
        try {
            val page = repository.searchOpen(query, offset, TODO_DEFAULT_PAGE_SIZE)
            if (state.requestId == requestId) {
                state = FocusTaskPickerState(query = query, offset = offset, page = page, requestId = requestId)
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (state.requestId == requestId) {
                state = FocusTaskPickerState(query = query, offset = offset, error = true, requestId = requestId)
            }
        }
    }

    val visibleState = if (
        state.query == query && state.offset == offset && (state.loading || state.error || state.page != null)
    ) state else {
        FocusTaskPickerState(query = query, offset = offset, loading = isAuthorized, requestId = state.requestId)
    }

    AppPageScaffold(
        title = stringResource(R.string.focus_task_picker_title),
        navigation = PageNavigation.BACK,
        onNavigate = onBack,
        modifier = Modifier.fillMaxSize().testTag("focus_task_picker"),
    ) { pagePadding ->
        Column(
            Modifier.fillMaxSize().padding(pagePadding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isAuthorized) {
                Text(stringResource(R.string.focus_task_picker_access_needed))
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { value ->
                        query = value
                        offset = 0
                    },
                    label = { Text(stringResource(R.string.focus_task_picker_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().testTag("focus_task_picker_search"),
                )
                when {
                    visibleState.loading -> CircularProgressIndicator(
                        modifier = Modifier.testTag("focus_task_picker_loading"),
                    )
                    visibleState.error -> {
                        Text(
                            stringResource(R.string.focus_task_picker_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("focus_task_picker_error"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (visibleState.hasPrevious) {
                                TextButton(
                                    onClick = { offset = (offset - TODO_DEFAULT_PAGE_SIZE).coerceAtLeast(0) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_task_picker_previous"),
                                ) { Text(stringResource(R.string.focus_task_picker_previous)) }
                            }
                            TextButton(
                                onClick = { retry += 1 },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_task_picker_retry"),
                            ) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    visibleState.isEmpty -> {
                        Text(
                            stringResource(
                                if (query.isBlank()) R.string.focus_task_picker_empty
                                else R.string.focus_task_picker_no_matches,
                            ),
                            modifier = Modifier.testTag("focus_task_picker_empty"),
                        )
                        if (visibleState.hasPrevious) {
                            TextButton(
                                onClick = { offset = (offset - TODO_DEFAULT_PAGE_SIZE).coerceAtLeast(0) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_task_picker_previous"),
                            ) { Text(stringResource(R.string.focus_task_picker_previous)) }
                        }
                    }
                    else -> {
                        val page = requireNotNull(visibleState.page)
                        val number = NumberFormat.getIntegerInstance()
                        Text(
                            stringResource(
                                R.string.focus_task_picker_results,
                                number.format(offset + 1),
                                number.format(offset + page.items.size),
                                number.format(page.total),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("focus_task_picker_results"),
                        )
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(page.items, key = { it.id.toString() }) { task ->
                                OutlinedButton(
                                    onClick = { onSelectTask(task.id) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                        .testTag("focus_task_picker_item_${task.id}"),
                                ) {
                                    Text(task.title, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = { offset = (offset - TODO_DEFAULT_PAGE_SIZE).coerceAtLeast(0) },
                                enabled = visibleState.hasPrevious,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_task_picker_previous"),
                            ) { Text(stringResource(R.string.focus_task_picker_previous)) }
                            TextButton(
                                onClick = { offset += TODO_DEFAULT_PAGE_SIZE },
                                enabled = visibleState.hasNext,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_task_picker_next"),
                            ) { Text(stringResource(R.string.focus_task_picker_next)) }
                        }
                    }
                }
            }
        }
    }
}
