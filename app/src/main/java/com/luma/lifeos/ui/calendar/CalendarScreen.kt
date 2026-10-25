package com.luma.lifeos.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.calendar.CalendarFilter
import com.luma.lifeos.domain.calendar.CalendarMode
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.language.DisplayTimePolicy
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation
import com.luma.lifeos.ui.todo.TodayTasksActionState
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun CalendarScreen(
    state: CalendarUiState,
    actions: TodayTasksActionState,
    displayTime: DisplayTimePolicy,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onSetMode: (CalendarMode) -> Unit,
    onMovePeriod: (Int) -> Unit,
    onFilter: (CalendarFilter) -> Unit,
    onRetry: (CalendarSource) -> Unit,
    onLoadMore: () -> Unit,
    onLoadMoreMemories: () -> Unit,
    onTask: (UUID) -> Unit,
    onDeleteTask: (UUID) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onSchedule: (LocalDate) -> Unit,
    onSetCompleted: (TodoTask, Boolean) -> Unit,
    onRetryTask: (UUID) -> Unit,
    onDismissTaskError: (UUID) -> Unit,
    onMemory: (EventId) -> Unit,
    onAddMemory: (LocalDate) -> Unit,
    onAllTasks: () -> Unit,
) {
    var fabHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val bottomClearance = if (fabHeight > 0.dp) fabHeight + 40.dp else 104.dp

    AppPageScaffold(
        title = stringResource(R.string.calendar_heading),
        navigation = PageNavigation.NONE,
        modifier = Modifier.fillMaxSize().testTag("screen_calendar"),
        headerTag = "calendar_header",
        titleTag = "calendar_title",
        actions = {
            TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp).testTag("calendar_today")) {
                Text(stringResource(R.string.today), maxLines = 1)
            }
        },
    ) { pagePadding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(pagePadding)) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("calendar_content"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomClearance),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "calendar_subtitle") {
                        Text(
                            stringResource(R.string.calendar_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item(key = "calendar_grid") {
                        CalendarGrid(state, displayTime, onSelectDate, onSetMode, onMovePeriod, onRetry)
                    }
                    item(key = "calendar_agenda") {
                        CalendarAgenda(
                            state = state,
                            actions = actions,
                            displayTime = displayTime,
                            onFilter = onFilter,
                            onTask = onTask,
                            onDeleteTask = onDeleteTask,
                            onAddTask = onAddTask,
                            onAllTasks = onAllTasks,
                            onLoadMore = onLoadMore,
                            onLoadMoreMemories = onLoadMoreMemories,
                            onSetCompleted = onSetCompleted,
                            onRetry = onRetry,
                            onRetryTask = onRetryTask,
                            onDismissTaskError = onDismissTaskError,
                            onMemory = onMemory,
                            onAddMemory = onAddMemory,
                        )
                    }
                }

                ExtendedFloatingActionButton(
                    onClick = { onSchedule(state.selectedDate) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp)
                        .onGloballyPositioned { coordinates ->
                            val measured = with(density) { coordinates.size.height.toDp() }
                            if (fabHeight != measured) fabHeight = measured
                        }.testTag("calendar_schedule"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(painterResource(R.drawable.ic_calendar_schedule), contentDescription = null)
                    Text(stringResource(R.string.calendar_schedule))
                }
            }
        }
    }
}
