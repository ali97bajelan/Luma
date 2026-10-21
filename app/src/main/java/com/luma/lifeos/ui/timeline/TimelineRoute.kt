package com.luma.lifeos.ui.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppSectionHeading
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.ui.common.entryLogCounts
import com.luma.lifeos.ui.common.EmptyStateCard
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun TimelineRoute(repository: TimelineRepository, date: LocalDate, zone: ZoneId,
    onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit, onEvent: (LifeEventRecord) -> Unit, onMemory: (EventId) -> Unit,
    onSearch: (() -> Unit)? = null, onHabit: (UUID, LocalDate, UUID?) -> Unit = { _, _, _ -> },
    captureBottomClearance: Dp = 0.dp) {
    val context = LocalContext.current.applicationContext
    val usagePreference = remember(context) { TimelineUsagePreference(context) }
    val initialShowAppUsage = remember(usagePreference) { usagePreference.showAppUsage() }
    val factory = remember(repository, initialShowAppUsage) {
        viewModelFactory { initializer { TimelineViewModel(repository, createSavedStateHandle(), initialShowAppUsage) } }
    }
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
                val index = state.items.indexOfFirst { it.key == key }
                if (index >= maxOf(0, state.items.size - 10) || key == "earlier") pager.loadLater()
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("screen_timeline"),
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = captureBottomClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
                    AppSectionHeading(stringResource(R.string.timeline), style = MaterialTheme.typography.headlineLarge)
                    onSearch?.let { TextButton(onClick = it, modifier = Modifier.testTag("timeline_search")) { Text(stringResource(R.string.search_entries_title)) } }
                    DayNavigation(date, onSelectDate, onToday)
                    val usageSwitchLabel = stringResource(R.string.timeline_show_app_usage)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(usageSwitchLabel, modifier = Modifier.weight(1f))
                        Switch(checked = state.showAppUsage, onCheckedChange = { show ->
                            usagePreference.setShowAppUsage(show)
                            pager.setShowAppUsage(show)
                        }, modifier = Modifier.testTag("timeline_show_app_usage")
                            .semantics { contentDescription = usageSwitchLabel })
                    }
                    Text(zone.id, style = MaterialTheme.typography.bodySmall)
                    if (state.loaded) Text(entryLogCounts(state.eventCount, state.habitLogCount),
                        modifier = Modifier.testTag("timeline_counts"))
                    Text(stringResource(R.string.entry_order_newest_first),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("timeline_order"))
                }
            }
            if (state.loading) item(key = "loading") { CircularProgressIndicator(Modifier.testTag("timeline_loading")) }
            if (state.error != null) item(key = "error") {
                Column(Modifier.testTag("timeline_error")) {
                    AccessibleFeedback(stringResource(R.string.timeline_error), Modifier.testTag("timeline_error_message"))
                    TextButton(onClick = pager::retry) { Text(stringResource(R.string.retry)) }
                }
            }
            if (state.loaded && state.items.isEmpty()) item(key = "empty") {
                EmptyStateCard(R.drawable.ic_nav_timeline, R.string.timeline_empty_heading, R.string.timeline_empty,
                    "timeline_empty")
            }
            if (state.canLoadEarlier) item(key = "later") {
                TextButton(enabled = !state.busy, onClick = pager::loadEarlier, modifier = Modifier.testTag("timeline_later")) { Text(stringResource(R.string.timeline_later)) }
            }
            val firstTimed = state.items.indexOfFirst { item ->
                item !is TimelineFeedItem.Habit || item.log.occurredAt != null
            }
            val dateOnlyCount = if (firstTimed < 0) state.items.size else firstTimed
            if (dateOnlyCount > 0) item(key = "date_only_group") {
                Text(stringResource(R.string.habit_time_not_recorded), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("timeline_date_only_group"))
            }
            items(state.items, key = { it.key }, contentType = { item ->
                when (item) {
                    is TimelineFeedItem.Entry -> item.record.event.type
                    is TimelineFeedItem.Habit -> "habit"
                }
            }) { item ->
                when (item) {
                    is TimelineFeedItem.Entry -> EventCard(item.record, zone, onOpen = { onEvent(item.record) },
                        openActionLabel = if (item.record.journal != null) R.string.journal_open else R.string.event_open,
                        onMemory = { onMemory(item.record.event.id) })
                    is TimelineFeedItem.Habit -> HabitTimelineCard(item, zone) {
                        onHabit(item.log.habitId, item.log.date, item.log.id)
                    }
                }
            }
            if (state.busy && state.loaded) item(key = "busy") { CircularProgressIndicator() }
            if (state.canLoadLater) item(key = "earlier") {
                TextButton(enabled = !state.busy, onClick = pager::loadLater, modifier = Modifier.testTag("timeline_earlier")) { Text(stringResource(R.string.timeline_earlier)) }
            }
        }
    }
}
