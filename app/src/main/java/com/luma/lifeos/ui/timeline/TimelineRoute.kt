package com.luma.lifeos.ui.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
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
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.ui.common.entryLogCounts
import com.luma.lifeos.ui.common.EmptyStateCard
import com.luma.lifeos.ui.common.LumaSectionSurface
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
    var viewOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    key(date, zone) {
        val list = rememberLazyListState()
        LaunchedEffect(list, state.records.size, state.offset, state.busy, state.error) {
            if (!state.loaded || state.busy || state.error != null || !state.canLoadLater) return@LaunchedEffect
            snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.key }.collect { key ->
                val index = state.items.indexOfFirst { it.key == key }
                if (index >= maxOf(0, state.items.size - 10) || key == "earlier") pager.loadLater()
            }
        }
        LumaReadingPane(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("screen_timeline"),
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = captureBottomClearance),
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier.padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TimelinePageHeader(onSearch)
                        LumaSectionSurface(modifier = Modifier.testTag("timeline_date_ribbon")) {
                            DayNavigation(date, onSelectDate, onToday)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (state.loaded) Text(entryLogCounts(state.eventCount, state.habitLogCount),
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("timeline_counts"))
                                TimelineViewOptions(
                                    expanded = viewOptionsExpanded,
                                    onExpandedChange = { viewOptionsExpanded = it },
                                    showAppUsage = observed.showAppUsage,
                                    onShowAppUsageChange = { show ->
                                        usagePreference.setShowAppUsage(show)
                                        pager.setShowAppUsage(show)
                                    },
                                    zone = zone,
                                    modifier = if (viewOptionsExpanded) Modifier.fillMaxWidth() else Modifier,
                                )
                            }
                            if (observed.showAppUsage) {
                                Text(
                                    text = stringResource(R.string.timeline_app_usage_active),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("timeline_app_usage_active"),
                                )
                            }
                        }
                    }
                }
                if (state.loading) item(key = "loading") { CircularProgressIndicator(Modifier.testTag("timeline_loading")) }
                if (state.error != null) item(key = "error") {
                    Column(Modifier.testTag("timeline_error").padding(bottom = 16.dp)) {
                        AccessibleFeedback(stringResource(R.string.timeline_error), Modifier.testTag("timeline_error_message"))
                        TextButton(onClick = pager::retry) { Text(stringResource(R.string.retry)) }
                    }
                }
                if (state.loaded && state.items.isEmpty()) item(key = "empty") {
                    Box(Modifier.padding(bottom = 16.dp)) {
                        EmptyStateCard(R.drawable.ic_nav_timeline, R.string.timeline_empty_heading, R.string.timeline_empty,
                            "timeline_empty")
                    }
                }
                if (state.canLoadEarlier) item(key = "later") {
                    TextButton(enabled = !state.busy, onClick = pager::loadEarlier,
                        modifier = Modifier.padding(bottom = 16.dp).testTag("timeline_later")) {
                        Text(stringResource(R.string.timeline_later))
                    }
                }
                val firstTimed = state.items.indexOfFirst { item ->
                    item !is TimelineFeedItem.Habit || item.log.occurredAt != null
                }
                val dateOnlyCount = if (firstTimed < 0) state.items.size else firstTimed
                if (dateOnlyCount > 0) item(key = "date_only_group") {
                    Text(stringResource(R.string.habit_time_not_recorded), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 16.dp).testTag("timeline_date_only_group"))
                }
                itemsIndexed(state.items, key = { _, item -> item.key }, contentType = { _, item ->
                    when (item) {
                        is TimelineFeedItem.Entry -> item.record.event.type
                        is TimelineFeedItem.Habit -> "habit"
                    }
                }) { index, item ->
                    TimelineRailItem(index = index, itemCount = state.items.size, item = item) {
                        when (item) {
                            is TimelineFeedItem.Entry -> EventCard(item.record, zone, onOpen = { onEvent(item.record) },
                                openActionLabel = if (item.record.journal != null) R.string.journal_open else R.string.event_open,
                                onMemory = { onMemory(item.record.event.id) })
                            is TimelineFeedItem.Habit -> HabitTimelineCard(item, zone) {
                                onHabit(item.log.habitId, item.log.date, item.log.id)
                            }
                        }
                    }
                }
                if (state.busy && state.loaded) item(key = "busy") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                if (state.canLoadLater) item(key = "earlier") {
                    TextButton(enabled = !state.busy, onClick = pager::loadLater,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp).testTag("timeline_earlier")) {
                        Text(stringResource(R.string.timeline_earlier))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRailItem(
    index: Int,
    itemCount: Int,
    item: TimelineFeedItem,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.8f
        val railWidth = if (compact) 32.dp else 48.dp
        val nodeSize = if (compact) 28.dp else 40.dp
        val railColor = MaterialTheme.colorScheme.outline
        val layoutDirection = LocalLayoutDirection.current

        Row(
            modifier = Modifier.fillMaxWidth()
                .drawBehind {
                    val centerX = if (layoutDirection == LayoutDirection.Ltr) railWidth.toPx() / 2f
                    else size.width - railWidth.toPx() / 2f
                    val centerY = nodeSize.toPx() / 2f
                    val startY = if (index == 0) centerY else 0f
                    val endY = if (index == itemCount - 1) centerY else size.height
                    if (endY > startY) {
                        drawLine(
                            color = railColor,
                            start = Offset(centerX, startY),
                            end = Offset(centerX, endY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                .padding(bottom = if (index < itemCount - 1) 16.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.width(railWidth).heightIn(min = nodeSize),
                contentAlignment = Alignment.TopCenter,
            ) {
                TimelineTypeNode(item = item, size = nodeSize, isEnd = index == itemCount - 1)
            }
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun TimelineTypeNode(item: TimelineFeedItem, size: Dp, isEnd: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (icon, background, foreground) = when (item) {
        is TimelineFeedItem.Habit -> Triple(R.drawable.ic_nav_habits, colors.secondaryContainer, colors.onSecondaryContainer)
        is TimelineFeedItem.Entry -> when (item.record.event.type) {
            EventType.MOOD -> Triple(R.drawable.ic_review_mood, colors.secondaryContainer, colors.onSecondaryContainer)
            EventType.JOURNAL -> Triple(R.drawable.ic_nav_timeline, colors.primaryContainer, colors.onPrimaryContainer)
            EventType.MEMORY -> Triple(R.drawable.ic_bookmark, colors.tertiaryContainer, colors.onTertiaryContainer)
            EventType.ACTIVITY -> Triple(R.drawable.ic_review_activity, colors.surfaceContainerHighest, colors.onSurface)
            EventType.APP_USAGE -> Triple(R.drawable.ic_review_usage, colors.surfaceContainerLow, colors.onSurface)
        }
    }
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = background,
        contentColor = foreground,
        border = if (isEnd) BorderStroke(2.dp, foreground) else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(if (size < 32.dp) 16.dp else 20.dp))
        }
    }
}

@Composable
private fun TimelinePageHeader(onSearch: (() -> Unit)?) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackSearch = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
        if (onSearch != null && !stackSearch) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppSectionHeading(stringResource(R.string.timeline),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp).testTag("timeline_search")) {
                    Text(stringResource(R.string.search_entries_title))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppSectionHeading(stringResource(R.string.timeline), style = MaterialTheme.typography.titleLarge)
                onSearch?.let {
                    TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp).testTag("timeline_search")) {
                        Text(stringResource(R.string.search_entries_title))
                    }
                }
            }
        }
    }
}
