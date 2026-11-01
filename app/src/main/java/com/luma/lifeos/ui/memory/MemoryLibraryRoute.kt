package com.luma.lifeos.ui.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.language.DisplayTimePolicy
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaClickableListSurface
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation
import java.time.YearMonth
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
internal fun MemoryLibraryRoute(
    repository: MemoryRepository,
    today: LocalDate,
    zone: ZoneId,
    authorizationEpoch: Pair<Long, Long>,
    onBack: () -> Unit,
    onRecordMemory: () -> Unit,
    onEvent: (LifeEventRecord) -> Unit,
) {
    BackHandler(onBack = onBack)
    val displayTime = rememberDisplayTimePolicy()
    val scope = rememberCoroutineScope()
    val authorizationKey = "${authorizationEpoch.first}:${authorizationEpoch.second}"
    var savedAuthorizationKey by rememberSaveable { mutableStateOf(authorizationKey) }
    var visibleLimit by rememberSaveable { mutableIntStateOf(30) }
    var anchorEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorRecordIndex by rememberSaveable { mutableIntStateOf(0) }
    var anchorOffset by rememberSaveable { mutableIntStateOf(0) }
    var anchorPreviousId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorNextId by rememberSaveable { mutableStateOf<String?>(null) }
    val savedStateMatchesAuthorization = savedAuthorizationKey == authorizationKey
    val authorizedVisibleLimit = if (savedStateMatchesAuthorization) visibleLimit else 30
    LaunchedEffect(authorizationKey) {
        if (!savedStateMatchesAuthorization) {
            visibleLimit = 30
            anchorEventId = null
            anchorRecordIndex = 0
            anchorOffset = 0
            anchorPreviousId = null
            anchorNextId = null
            savedAuthorizationKey = authorizationKey
        }
    }
    val holder = remember(repository, authorizationEpoch) {
        MemoryLibraryStateHolder(repository, authorizedVisibleLimit, scope) { committedLimit ->
            visibleLimit = committedLimit
        }
    }
    DisposableEffect(holder) {
        onDispose(holder::close)
    }
    val state by key(authorizationEpoch) { holder.state.collectAsStateWithLifecycle() }
    val listState = key(authorizationEpoch) { rememberLazyListState() }
    val visibleRecords = (state as? MemoryLibraryUiState.Content)?.records
    val rows = remember(visibleRecords, zone) {
        visibleRecords?.let { memoryLibraryItems(it, zone) }.orEmpty()
    }
    var anchorRestored by remember(authorizationEpoch) { mutableStateOf(false) }
    LaunchedEffect(rows, authorizationEpoch) {
        val entries = rows.filterIsInstance<MemoryLibraryItem.Entry>()
        if (entries.isEmpty()) return@LaunchedEffect
        if (!anchorRestored && anchorEventId == null && anchorPreviousId == null &&
            anchorNextId == null && anchorRecordIndex == 0 && anchorOffset == 0
        ) {
            anchorRestored = true
            return@LaunchedEffect
        }
        val savedAnchorExists = entries.any { it.key == anchorEventId }
        if (anchorRestored && (anchorEventId == null || savedAnchorExists)) return@LaunchedEffect
        val target = entries.firstOrNull { it.key == anchorEventId }
            ?: entries.firstOrNull { it.key == anchorPreviousId }
            ?: entries.firstOrNull { it.key == anchorNextId }
            ?: entries.getOrNull(anchorRecordIndex.coerceIn(0, entries.lastIndex))
        if (target == null) return@LaunchedEffect
        val recordIds = entries.map { it.key }
        val recordIndex = recordIds.indexOf(target.key)
        anchorEventId = target.key
        anchorRecordIndex = recordIndex
        anchorPreviousId = recordIds.getOrNull(recordIndex - 1)
        anchorNextId = recordIds.getOrNull(recordIndex + 1)
        val targetListIndex = rows.indexOfFirst { it.key == target.key }
        if (targetListIndex >= 0) listState.scrollToItem(targetListIndex + 1, -anchorOffset)
        anchorRestored = true
    }
    LaunchedEffect(listState, rows, authorizationEpoch) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info ->
                val entry = rows.firstOrNull { it.key == info.key } as? MemoryLibraryItem.Entry
                entry?.let { it.key to info.offset }
            }
        }.filterNotNull().distinctUntilChanged().collect { (id, itemOffset) ->
            if (!anchorRestored) return@collect
            val recordIds = rows.filterIsInstance<MemoryLibraryItem.Entry>().map { it.key }
            val recordIndex = recordIds.indexOf(id)
            if (recordIndex < 0) return@collect
            anchorEventId = id
            anchorRecordIndex = recordIndex
            anchorOffset = itemOffset
            anchorPreviousId = recordIds.getOrNull(recordIndex - 1)
            anchorNextId = recordIds.getOrNull(recordIndex + 1)
        }
    }

    AppPageScaffold(
        title = stringResource(R.string.memory_library_title),
        navigation = PageNavigation.BACK,
        onNavigate = onBack,
    ) { pagePadding ->
        LumaReadingPane(
            modifier = Modifier.fillMaxSize().padding(pagePadding).testTag("screen_memories"),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("memory_library_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "memory-library-add") {
                    Button(
                        onClick = onRecordMemory,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("memory_library_add"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(stringResource(R.string.memory_capture), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                when (val current = state) {
                    MemoryLibraryUiState.InitialLoading -> item(key = "memory-library-loading") {
                        val label = stringResource(R.string.memory_library_loading)
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp).semantics {
                                    contentDescription = label
                                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate
                                },
                            )
                            Text(label, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    MemoryLibraryUiState.Empty -> item(key = "memory-library-empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.memory_library_empty), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.memory_library_empty_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    MemoryLibraryUiState.InitialError -> item(key = "memory-library-error") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.memory_library_error),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Text(stringResource(R.string.memory_library_error_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(
                                onClick = holder::retry,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("memory_library_retry"),
                            ) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    is MemoryLibraryUiState.Content -> {
                        items(rows, key = MemoryLibraryItem::key) { row ->
                            when (row) {
                                is MemoryLibraryItem.MonthHeading -> {
                                    val label = remember(row.month, displayTime.locale) {
                                        monthHeading(row.month, displayTime.locale)
                                    }
                                    Text(
                                        label,
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                            .semantics { heading() },
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                                is MemoryLibraryItem.Entry -> MemoryLibraryCard(
                                    record = row.record,
                                    today = today,
                                    zone = zone,
                                    displayTime = displayTime,
                                    onClick = { onEvent(row.record) },
                                )
                            }
                        }
                        if (current.hasOlder || current.olderLoad != MemoryOlderLoadState.Idle || current.observationError) {
                            item(key = "memory-library-older-footer") {
                                if (current.observationError) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(stringResource(R.string.memory_library_error),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                        Text(stringResource(R.string.memory_library_error_description),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(
                                            onClick = holder::retry,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                                .testTag("memory_library_observation_retry"),
                                        ) { Text(stringResource(R.string.retry)) }
                                    }
                                } else when (current.olderLoad) {
                                    MemoryOlderLoadState.Idle -> Button(
                                        onClick = holder::loadOlder,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            .testTag("memory_library_load_older"),
                                    ) { Text(stringResource(R.string.memory_library_load_older), maxLines = 2) }
                                    MemoryOlderLoadState.Loading -> Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            .testTag("memory_library_load_older"),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Text(stringResource(R.string.memory_library_loading_older), maxLines = 2)
                                        }
                                    }
                                    MemoryOlderLoadState.Error -> Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(stringResource(R.string.memory_library_older_error),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                        TextButton(
                                            onClick = holder::retry,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                                .testTag("memory_library_older_retry"),
                                        ) { Text(stringResource(R.string.retry)) }
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
private fun MemoryLibraryCard(
    record: LifeEventRecord,
    today: LocalDate,
    zone: ZoneId,
    displayTime: DisplayTimePolicy,
    onClick: () -> Unit,
) {
    val type = record.event.type
    val note = record.memory?.note.orEmpty()
    val noteLines = note.split('\n')
    val noteHeadingIndex = noteLines.indexOfFirst(String::isNotBlank)
    val noteHeading = noteLines.getOrNull(noteHeadingIndex)?.trim()?.takeIf(String::isNotEmpty)
    val title = record.event.title?.takeIf(String::isNotBlank)?.trim()
        ?: noteHeading
        ?: stringResource(when (type) {
            EventType.JOURNAL -> R.string.memory_library_journal_fallback
            EventType.MOOD -> R.string.memory_library_mood_fallback
            else -> R.string.memory_library_memory_fallback
        })
    val preview = if (record.event.title.isNullOrBlank() && noteHeading != null) {
        noteLines.toMutableList().apply { removeAt(noteHeadingIndex) }.joinToString("\n")
    } else note
    val occurredDate = record.event.occurredAt.atZone(zone).toLocalDate()
    val dateText = occurredDate.format(
        DateTimeFormatter.ofPattern(if (displayTime.locale.language == "en") "MMM d" else "d MMM", displayTime.locale)
            .withChronology(IsoChronology.INSTANCE)
            .withDecimalStyle(DecimalStyle.of(displayTime.locale)),
    )
    val source = when (type) {
        EventType.JOURNAL -> stringResource(R.string.memory_library_from_journal)
        EventType.MOOD -> stringResource(R.string.memory_library_from_mood)
        else -> null
    }
    val importance = record.memory?.importance
    val futureDateLabel = stringResource(R.string.memory_library_future_date)
        .takeIf { occurredDate.isAfter(today) }
    val importanceLabel = importance?.let { stringResource(R.string.memory_importance_value, it) }
    val accessibleLabel = listOfNotNull(
        title,
        preview.takeIf(String::isNotBlank),
        source,
        dateText,
        futureDateLabel,
        importanceLabel,
    ).joinToString(", ")

    LumaClickableListSurface(
        onClick = onClick,
        modifier = Modifier.testTag("memory_library_item_${record.event.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleLabel
                role = Role.Button
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (preview.isNotBlank()) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                        overflow = TextOverflow.Ellipsis)
                }
                source?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateText, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (occurredDate.isAfter(today)) {
                    Text(stringResource(R.string.memory_library_future_date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                importance?.let {
                    Text(stringResource(R.string.memory_importance_value, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun monthHeading(month: YearMonth, locale: java.util.Locale): String =
    month.atDay(1).format(
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
            .withChronology(IsoChronology.INSTANCE)
            .withDecimalStyle(DecimalStyle.of(locale)),
    )
