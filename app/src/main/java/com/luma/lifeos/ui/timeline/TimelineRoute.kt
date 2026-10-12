package com.luma.lifeos.ui.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.TimelineRepository
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TimelineRoute(repository: TimelineRepository, date: LocalDate, zone: ZoneId,
    onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit, onEvent: (LifeEventRecord) -> Unit, onMemory: (EventId) -> Unit,
    onSearch: (() -> Unit)? = null) {
    val factory = remember(repository) { viewModelFactory { initializer { TimelineViewModel(repository, createSavedStateHandle()) } } }
    val pager: TimelineViewModel = viewModel(factory = factory)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pager, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            pager.startObserving()
            try { awaitCancellation() } finally { pager.stopObserving() }
        }
    }
    val observed by pager.state.collectAsStateWithLifecycle()
    LaunchedEffect(pager, date, zone) { pager.selectDay(date, zone) }
    // A late previous-day result must never be displayed beneath the new selected date.
    val state = if (observed.date == date && observed.zone == zone) observed else TimelineState(date, zone)
    key(date, zone) {
        val list = rememberLazyListState()
        LaunchedEffect(list, state.records.size, state.offset, state.busy, state.error) {
            if (!state.loaded || state.busy || state.error != null || !state.canLoadLater) return@LaunchedEffect
            snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.key }.collect { key ->
                val index = state.records.indexOfFirst { it.event.id.toString() == key }
                if (index >= maxOf(0, state.records.size - 10) || key == "later") pager.loadLater()
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("screen_timeline"),
            contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.timeline), style = MaterialTheme.typography.headlineLarge)
                    onSearch?.let { TextButton(onClick = it, modifier = Modifier.testTag("timeline_search")) { Text(stringResource(R.string.search)) } }
                    DayNavigation(date, onSelectDate, onToday)
                    Text(zone.id, style = MaterialTheme.typography.bodySmall)
                    if (state.loaded) Text(pluralStringResource(R.plurals.daily_events, state.eventCount, state.eventCount),
                        modifier = Modifier.testTag("timeline_event_count"))
                }
            }
            if (state.loading) item(key = "loading") { CircularProgressIndicator(Modifier.testTag("timeline_loading")) }
            if (state.error != null) item(key = "error") {
                Column(Modifier.testTag("timeline_error")) {
                    Text(stringResource(R.string.timeline_error))
                    TextButton(onClick = pager::retry) { Text(stringResource(R.string.retry)) }
                }
            }
            if (state.loaded && state.records.isEmpty()) item(key = "empty") { Text(stringResource(R.string.timeline_empty), modifier = Modifier.testTag("timeline_empty")) }
            if (state.canLoadEarlier) item(key = "earlier") {
                TextButton(enabled = !state.busy, onClick = pager::loadEarlier, modifier = Modifier.testTag("timeline_earlier")) { Text(stringResource(R.string.timeline_earlier)) }
            }
            items(state.records, key = { it.event.id.toString() }, contentType = { it.event.type }) { record ->
                EventCard(record, zone, onOpen = { onEvent(record) }, onMemory = { onMemory(record.event.id) })
            }
            if (state.busy && state.loaded) item(key = "busy") { CircularProgressIndicator() }
            if (state.canLoadLater) item(key = "later") {
                TextButton(enabled = !state.busy, onClick = pager::loadLater, modifier = Modifier.testTag("timeline_later")) { Text(stringResource(R.string.timeline_later)) }
            }
        }
    }
}
