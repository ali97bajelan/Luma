package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FOCUS_HISTORY_PAGE_SIZE
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation
import java.time.ZoneId
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun FocusHistoryRoute(
    repository: FocusRepository,
    range: TimeRange?,
    zone: ZoneId,
    onBack: () -> Unit,
) {
    val factory = remember(repository, range) {
        viewModelFactory {
            initializer { FocusHistoryViewModel(repository, range, createSavedStateHandle()) }
        }
    }
    val model = viewModel<FocusHistoryViewModel>(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    FocusHistoryScreen(state, range, zone, model::retry, model::page, model::delete, onBack)
}

@Composable
private fun FocusHistoryScreen(
    state: FocusHistoryUiState,
    range: TimeRange?,
    zone: ZoneId,
    onRetry: () -> Unit,
    onPage: (Int) -> Unit,
    onDelete: (FocusHistoryRow) -> Unit,
    onBack: () -> Unit,
) {
    var expandedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<FocusHistoryRow?>(null) }
    LaunchedEffect(state.loading) {
        if (state.loading) deleteTarget = null
    }
    AppPageScaffold(
        title = stringResource(R.string.focus_history_title),
        navigation = PageNavigation.BACK,
        onNavigate = onBack,
        modifier = Modifier.fillMaxSize().testTag("focus_history_screen"),
    ) { pagePadding ->
        LumaReadingPane(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(pagePadding).testTag("focus_history_list"),
                contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (range != null) item(key = "range") {
                    Text(
                        stringResource(
                            R.string.focus_history_range,
                            range.start.atZone(zone).toLocalDate().format(localizedDate(FormatStyle.MEDIUM)),
                            range.endExclusive.minusNanos(1).atZone(zone).toLocalDate()
                                .format(localizedDate(FormatStyle.MEDIUM)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("focus_history_range"),
                    )
                }
                when {
                    state.loading -> item(key = "loading") {
                        CircularProgressIndicator(Modifier.testTag("focus_history_loading"))
                    }
                    state.loadFailed -> item(key = "error") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccessibleFeedback(stringResource(R.string.focus_history_error))
                            TextButton(onClick = onRetry, modifier = Modifier.testTag("focus_history_retry")) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    state.page?.total == 0 -> item(key = "empty") {
                        Text(
                            stringResource(if (range == null) R.string.focus_history_empty
                                else R.string.focus_history_empty_range),
                            modifier = Modifier.testTag("focus_history_empty"),
                        )
                    }
                    state.page != null -> {
                        val page = state.page
                        items(page.rows, key = { it.session.id.toString() }) { row ->
                            FocusHistoryItem(
                                row = row,
                                range = range,
                                zone = zone,
                                expanded = expandedSessionId == row.session.id.toString(),
                                deleting = state.deletingSessionId == row.session.id,
                                onOpen = {
                                    expandedSessionId = if (expandedSessionId == row.session.id.toString()) null
                                    else row.session.id.toString()
                                },
                                onDelete = { deleteTarget = row },
                            )
                        }
                        if (state.deletionFailed) item(key = "delete_error") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccessibleFeedback(stringResource(R.string.focus_history_delete_failed))
                                TextButton(onClick = onRetry, modifier = Modifier.testTag("focus_history_delete_retry")) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                        if (page.total > FOCUS_HISTORY_PAGE_SIZE) item(key = "pagination") {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    enabled = page.offset > 0,
                                    onClick = { onPage((page.offset - FOCUS_HISTORY_PAGE_SIZE).coerceAtLeast(0)) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_history_previous"),
                                ) { Text(stringResource(R.string.focus_history_previous_page)) }
                                val pageNumber = page.offset / FOCUS_HISTORY_PAGE_SIZE + 1
                                val pageCount = (page.total + FOCUS_HISTORY_PAGE_SIZE - 1) / FOCUS_HISTORY_PAGE_SIZE
                                Text(
                                    stringResource(R.string.focus_history_page, pageNumber, pageCount),
                                    modifier = Modifier.padding(top = 14.dp).testTag("focus_history_page"),
                                )
                                TextButton(
                                    enabled = page.offset + page.rows.size < page.total,
                                    onClick = { onPage(page.offset + FOCUS_HISTORY_PAGE_SIZE) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_history_next"),
                                ) { Text(stringResource(R.string.focus_history_next_page)) }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.focus_history_delete_title)) },
            text = { Text(stringResource(R.string.focus_history_delete_confirm)) },
            confirmButton = {
                TextButton(
                    enabled = state.deletingSessionId == null,
                    onClick = {
                        deleteTarget = null
                        onDelete(target)
                    },
                    modifier = Modifier.testTag("focus_history_delete_confirm"),
                ) { Text(stringResource(R.string.focus_history_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, modifier = Modifier.testTag("focus_history_delete_cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.testTag("focus_history_delete_dialog"),
        )
    }
}

@Composable
private fun FocusHistoryItem(
    row: FocusHistoryRow,
    range: TimeRange?,
    zone: ZoneId,
    expanded: Boolean,
    deleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = remember(zone, Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(zone)
    }
    Card(Modifier.fillMaxWidth().testTag("focus_history_row_${row.session.id}")) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("focus_history_detail")) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatter.format(row.session.startedAt), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (row.session.state == FocusState.INTERRUPTED) R.string.focus_interrupted
                            else R.string.focus_finished,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (range == null) stringResource(
                            R.string.focus_history_total,
                            focusDuration(row.fullDurationMillis),
                        ) else stringResource(
                            R.string.focus_history_contribution,
                            focusDuration(row.rangeContributionMillis),
                            focusDuration(row.fullDurationMillis),
                        ),
                        modifier = Modifier.testTag("focus_history_row_duration"),
                    )
                }
            }
            if (expanded) {
                Text(
                    stringResource(R.string.focus_history_pause_explanation),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (row.session.state == FocusState.INTERRUPTED || row.session.recoveredOrUncertain) {
                    Text(
                        stringResource(R.string.focus_history_recovered),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("focus_history_recovered"),
                    )
                }
                row.session.runs.forEachIndexed { index, run ->
                    val end = requireNotNull(run.endedAt)
                    Text(
                        stringResource(
                            R.string.focus_history_segment,
                            index + 1,
                            formatter.format(run.startedAt),
                            formatter.format(end),
                            focusDuration(run.creditedMillis),
                        ),
                        modifier = Modifier.testTag("focus_history_segment_$index"),
                    )
                }
                TextButton(
                    enabled = !deleting,
                    onClick = onDelete,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_history_delete"),
                ) {
                    Text(stringResource(if (deleting) R.string.focus_saving else R.string.focus_history_delete_action))
                }
            }
        }
    }
}
