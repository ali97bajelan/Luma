package com.luma.lifeos.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.SearchRepository
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.timeline.EventCard
import java.time.ZoneId
import java.time.format.FormatStyle
import kotlinx.coroutines.awaitCancellation

@Composable
fun SearchRoute(repository: SearchRepository, zone: ZoneId, onBack: () -> Unit, onEvent: (LifeEventRecord) -> Unit) {
    val factory = remember(repository) { viewModelFactory { initializer { SearchViewModel(repository, createSavedStateHandle()) } } }
    val vm: SearchViewModel = viewModel(factory = factory)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle, zone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.start(zone)
            try { awaitCancellation() } finally { vm.stop() }
        }
    }
    val observed by vm.state.collectAsStateWithLifecycle()
    val state = if (observed.zone == zone) observed else observed.copy(records = emptyList(), loaded = false, loading = true)
    var filters by rememberSaveable { mutableStateOf(false) }
    val count = state.input.activeFilterCount
    val hasCriteria = state.input.text.isNotBlank() || count > 0
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("screen_search")) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.search_entries_title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("search_title"))
        OutlinedTextField(value = state.input.text, onValueChange = vm::setText, singleLine = true,
            label = { Text(stringResource(R.string.search_text_hint)) },
            supportingText = if (state.input.text.length >= 180) {
                { Text(stringResource(R.string.search_text_help, state.input.text.length), Modifier.testTag("search_text_limit")) }
            } else null,
            modifier = Modifier.fillMaxWidth().testTag("search_text"))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { filters = true }, modifier = Modifier.testTag("search_filters")) { Text(stringResource(R.string.search_filter_count, count)) }
            TextButton(onClick = vm::clearFilters, enabled = count > 0, modifier = Modifier.testTag("search_reset_filters")) { Text(stringResource(R.string.search_reset_filters)) }
            TextButton(onClick = vm::clear, enabled = state.input.text.isNotEmpty() || count > 0,
                modifier = Modifier.testTag("search_clear")) { Text(stringResource(R.string.search_clear)) }
        }
        if (state.loaded && hasCriteria) AccessibleFeedback(stringResource(R.string.search_results, state.totalCount),
            Modifier.testTag("search_result_count"), isError = false)
        if (state.loaded && state.records.isNotEmpty()) Text(stringResource(R.string.entry_order_newest_first),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("search_order"))
        key(state.input, zone) {
            val list = rememberLazyListState()
            LaunchedEffect(list, state.windowVersion) {
                if (state.loaded) list.scrollToItem(0)
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).testTag("search_results_list"),
                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "scope_help") {
                    Text(stringResource(R.string.search_scope_help), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().testTag("search_scope_help"))
                }
                item(key = "active_filters") { ActiveSearchFilters(state, vm::removeFilter) }
                if (state.loading) item(key = "loading") { CircularProgressIndicator(Modifier.testTag("search_loading")) }
                if (state.error) item(key = "error") {
                    Column(Modifier.testTag("search_error")) {
                        AccessibleFeedback(stringResource(R.string.search_error))
                        TextButton(onClick = vm::retry) { Text(stringResource(R.string.retry)) }
                    }
                }
                if (state.loaded && !state.loading && !state.error) {
                    if (!hasCriteria) item(key = "intro") {
                        if (state.totalCount == 0) {
                            Text(stringResource(R.string.search_empty_library), modifier = Modifier.testTag("search_empty_library"))
                        } else {
                            Text(stringResource(R.string.search_prompt), modifier = Modifier.testTag("search_prompt"))
                        }
                    } else if (state.records.isEmpty()) item(key = "empty") {
                        Text(stringResource(R.string.search_empty), modifier = Modifier.testTag("search_empty"))
                    }
                }
                if (state.canLoadEarlier) item(key = "earlier") {
                    TextButton(enabled = !state.loading, onClick = vm::loadEarlier, modifier = Modifier.testTag("search_earlier")) { Text(stringResource(R.string.search_earlier)) }
                }
                items(state.records, key = { it.event.id.toString() }, contentType = { it.event.type }) { record ->
                    Column {
                        Text(record.event.occurredAt.atZone(zone).toLocalDate().format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.labelLarge)
                        EventCard(record, zone, onOpen = { vm.rememberResult(record.event.id); onEvent(record) })
                    }
                }
                if (state.canLoadLater) item(key = "later") {
                    TextButton(enabled = !state.loading, onClick = vm::loadLater, modifier = Modifier.testTag("search_later")) { Text(stringResource(R.string.search_later)) }
                }
            }
        }
    }
    if (filters) SearchFilters(state, vm, onDone = { filters = false })
}
