package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.luma.lifeos.ui.common.DetailsDisclosure
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.language.emotionDisplayName
import com.luma.lifeos.ui.timeline.EventCard
import com.luma.lifeos.ui.today.pickerDate
import com.luma.lifeos.ui.today.pickerMillis
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import androidx.compose.ui.unit.Dp
import java.time.*
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WeeklyReviewRoute(repository: WeeklyReviewRepository, today: LocalDate, zone: ZoneId,
    onSources: (ReviewDates, ReviewMetric) -> Unit, onEvent: (LifeEventRecord) -> Unit,
    onPerson: (UUID) -> Unit = {}, onPlace: (UUID) -> Unit = {}, onUsage: (LocalDate) -> Unit = {},
    onMemory: ((LifeEventRecord) -> Unit)? = null, habits: HabitRepository? = null,
    onHabit: (UUID, LocalDate) -> Unit = { _, _ -> },
    integrationAvailability: IntegrationAvailabilityState = IntegrationAvailabilityState(),
    onIntegrations: () -> Unit = {}, onCheckIn: () -> Unit = {},
    captureBottomClearance: Dp = 0.dp) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val period = reviewPeriod(restoredReviewDate(selected, today), today, zone)
    val weekKey = "${period.current.start}|${period.zone.id}"
    val listState = rememberLazyListState()
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var sectionTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var retainedWeekKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(weekKey) {
        val previousWeekKey = retainedWeekKey
        if (previousWeekKey != null && previousWeekKey != weekKey) {
            section = null
            sectionTarget = null
            listState.scrollToItem(0)
        }
        retainedWeekKey = weekKey
    }
    key(period, retry) {
        val observation = remember(repository) { observeWeeklyReview(repository, period) }
        val state by observation.collectAsStateWithLifecycle(WeeklyReviewState.Loading)
        val report = (state as? WeeklyReviewState.Data)?.report
        var habitRetry by remember { mutableIntStateOf(0) }
        val habitState = if (habits != null) {
            val habitObservation = remember(habits, habitRetry) {
                observeHabitWeek(habits, period.current.start, period.current.through)
            }
            val observed by habitObservation.collectAsStateWithLifecycle(ReviewHabitsState.Loading)
            observed
        } else null
        val habitLogCount = (habitState as? ReviewHabitsState.Data)?.comparison?.rows?.sumOf { it.currentRepetitions }
        // A short loading/error list must not clamp the report's restored scroll position.
        val placeholderListState = rememberLazyListState()
        val empty = report?.let { it.current.records == 0 && it.current.usage?.totalMillis == null && it.current.activities.isEmpty() } == true && habits == null
        val keys = report?.let {
            listOf("heading", "overview", "sections") + (if (empty) listOf("empty") else emptyList()) +
                reviewSections(habits != null).drop(1).map { destination -> destination.key }
        }
        LaunchedEffect(keys, sectionTarget) {
            val target = sectionTarget ?: return@LaunchedEffect
            val targetIndex = keys?.indexOf(target) ?: return@LaunchedEffect
            if (targetIndex >= 0) listState.scrollToItem(targetIndex)
            sectionTarget = null
        }
        LaunchedEffect(keys) {
            val itemKeys = keys ?: return@LaunchedEffect
            val sectionKeys = reviewSections(habits != null).map { destination -> destination.key }
            snapshotFlow {
                // Short sections near the end cannot always align at the top. Keep the
                // chosen/restored tab until scrolling resumes, rather than clearing it
                // when a programmatic jump is clamped to the end of the list.
                if (sectionTarget != null || !listState.isScrollInProgress || listState.layoutInfo.totalItemsCount == 0) null
                else listState.firstVisibleItemIndex
            }.distinctUntilChanged().collect { firstVisibleIndex ->
                val first = firstVisibleIndex ?: return@collect
                val active = sectionKeys.lastOrNull { key -> itemKeys.indexOf(key) in 0..first }
                if (section != active) section = active
            }
        }
        Box(Modifier.fillMaxSize().testTag("screen_review")) {
            LumaReadingPane(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().testTag("memory_review_list"), state = if (report == null) placeholderListState else listState,
                    contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = captureBottomClearance),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item(key = "heading") {
                        ReviewHeader(period,
                            onPrevious = { selected = period.current.start.minusWeeks(1).toString() },
                            onNext = { selected = period.current.start.plusWeeks(1).toString() },
                            onThisWeek = { selected = null }, onPick = { picking = true })
                    }
                    when (val current = state) {
                        WeeklyReviewState.Loading -> item(key = "loading") { CircularProgressIndicator() }
                        WeeklyReviewState.Error -> item(key = "error") {
                            AccessibleFeedback(stringResource(R.string.review_error))
                            TextButton(onClick = { retry++ }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("review_retry")) { Text(stringResource(R.string.retry)) }
                        }
                        is WeeklyReviewState.Data -> {
                            val report = current.report
                            val a = report.current; val b = report.previous
                            item(key = "overview") { ReviewOverview(report, repository, onEvent, onSources, habitLogCount,
                                integrationAvailability, onIntegrations, onCheckIn, habitState,
                                onHabits = { section = "habits"; sectionTarget = "habits" }) }
                            stickyHeader(key = "sections") {
                                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                                    ReviewSectionNavigation(section, period, onSection = { key ->
                                        section = key
                                        sectionTarget = key
                                    }, includeHabits = habits != null)
                            }
                        }
                        if (empty) item(key = "empty") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.review_empty), modifier = Modifier.testTag("review_empty"))
                                if (b.records > 0) Text(
                                    stringResource(R.string.review_empty_previous, reviewNumber(b.records)),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("review_empty_previous"))
                            }
                        }
                        item(key = "counts") {
                            ReviewCounts(report, onSources)
                        }
                        item(key = "mood") { ReviewMood(report, onSources, onCheckIn) }
                        item(key = "memories") {
                            ReviewSection(R.string.review_memories, R.drawable.ic_bookmark_outline, "memories") {
                                MemoryHighlights(repository, period, onEvent, onMemory)
                                if (a.memories > 0) TextButton(onClick = { onSources(period.current, ReviewMetric(ReviewMetricKind.MEMORIES)) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.review_all_memories)) }
                            }
                        }
                        item(key = "catalog") {
                            ReviewSection(R.string.review_people_places, R.drawable.ic_nav_people, "catalog",
                                explanation = R.string.review_linked_explanation) {
                                if ((a.people + b.people + a.places + b.places).isEmpty()) {
                                    ReviewEmpty(R.string.review_catalog_empty, "review_catalog_empty")
                                } else {
                                    CatalogPatterns(stringResource(R.string.people), a.people, b.people, period,
                                        ReviewMetricKind.PERSON, onSources, onPerson)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    CatalogPatterns(stringResource(R.string.places), a.places, b.places, period,
                                        ReviewMetricKind.PLACE, onSources, onPlace)
                                }
                            }
                        }
                        item(key = "usage") { ReviewDigital(report, onUsage, integrationAvailability, onIntegrations) }
                        item(key = "activity") { ReviewActivities(report, onSources, integrationAvailability.health, onIntegrations) }
                        if (habits != null) item(key = "habits") {
                            ReviewHabits(requireNotNull(habitState), { habitRetry++ }, onHabit)
                        }
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        val selectedDate = restoredReviewDate(selected, today)
        val picker = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(selectedDate),
            yearRange = minOf(1900, selectedDate.year)..maxOf(2100, today.year),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = pickerDate(utcTimeMillis) <= today
                override fun isSelectableYear(year: Int) = year <= today.year
            })
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { selected = minOf(pickerDate(it), today).toString() }; picking = false
            }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.select_date)) } },
            dismissButton = { TextButton(onClick = { picking = false }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
    }
}

@Composable private fun ReviewCounts(report: WeeklyReview, onSources: (ReviewDates, ReviewMetric) -> Unit) {
    ReviewValueGroup {
        listOf(ReviewMetricKind.ENTRIES to (report.current.records to report.previous.records),
            ReviewMetricKind.JOURNALS to (report.current.journals to report.previous.journals),
            ReviewMetricKind.MEMORIES to (report.current.memories to report.previous.memories)).forEach { (kind, counts) ->
            val metric = ReviewMetric(kind)
            ReviewValue(stringResource(kind.label()), reviewNumber(counts.first), reviewNumber(counts.second),
                reviewChange(counts.first.toDouble(), counts.second.toDouble()), kind.name.lowercase(),
                { onSources(report.period.current, metric) }, { onSources(report.period.previous, metric) })
        }
    }
}
@Composable private fun ReviewMood(report: WeeklyReview, onSources: (ReviewDates, ReviewMetric) -> Unit, onCheckIn: () -> Unit) {
    ReviewSection(R.string.review_mood, R.drawable.ic_review_mood, "mood",
        explanation = R.string.review_mood_explanation) {
        if (report.current.mood == null && report.previous.mood == null) {
            ReviewEmpty(R.string.metric_no_check_ins_week, "review_mood_empty")
            TextButton(onClick = onCheckIn, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("review_mood_checkin")) {
                Text(stringResource(R.string.review_checkin_action))
            }
        } else ReviewValueGroup {
            listOf(ReviewMetricKind.MOOD to (report.current.mood?.score to report.previous.mood?.score),
                ReviewMetricKind.ENERGY to (report.current.mood?.energy to report.previous.mood?.energy),
                ReviewMetricKind.STRESS to (report.current.mood?.stress to report.previous.mood?.stress)).forEach { (kind, scales) ->
                if (scales.first != null || scales.second != null || report.current.mood != null || report.previous.mood != null) {
                    val currentAbsence = stringResource(if (report.current.mood == null) R.string.metric_no_check_ins_week
                        else if (kind == ReviewMetricKind.MOOD) R.string.review_no_measurement_period
                        else if (kind == ReviewMetricKind.ENERGY) R.string.metric_no_energy_week else R.string.metric_no_stress_week)
                    val previousAbsence = stringResource(if (report.previous.mood == null) R.string.metric_no_check_ins_week
                        else if (kind == ReviewMetricKind.MOOD) R.string.review_no_measurement_period
                        else if (kind == ReviewMetricKind.ENERGY) R.string.metric_no_energy_week else R.string.metric_no_stress_week)
                    ReviewValue(stringResource(kind.label()), reviewScale(scales.first, currentAbsence), reviewScale(scales.second, previousAbsence),
                    reviewChange(scales.first?.average, scales.second?.average), kind.name.lowercase(),
                    scales.first?.let { { onSources(report.period.current, ReviewMetric(kind)) } },
                    scales.second?.let { { onSources(report.period.previous, ReviewMetric(kind)) } })
                }
            }
        }
        if (report.current.moodDays.any { it.mood != null }) {
            DetailsDisclosure(R.string.review_daily_evidence, "review_mood_daily_details") {
                val format = com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)
                report.current.moodDays.filter { it.mood != null }.forEach { day ->
                    Text(day.date.format(format))
                    Text(reviewScale(day.mood?.score, stringResource(R.string.metric_no_check_ins_week)))
                    TextButton(onClick = { onSources(ReviewDates(day.date, day.date), ReviewMetric(ReviewMetricKind.MOOD)) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.review_view_sources))
                    }
                }
            }
        }
        if (report.current.emotions.isNotEmpty() || report.previous.emotions.isNotEmpty()) {
            CatalogPatterns(stringResource(R.string.review_top_emotions), report.current.emotions, report.previous.emotions,
                report.period, ReviewMetricKind.EMOTION, onSources, null)
        }
    }
}
@Composable private fun CatalogPatterns(heading: String, current: List<NamedFrequency>, previous: List<NamedFrequency>, period: ReviewPeriod,
    kind: ReviewMetricKind, onSources: (ReviewDates, ReviewMetric) -> Unit, onDetails: ((UUID) -> Unit)?) {
    Text(heading, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() })
    val names = (current + previous).distinctBy { it.name.id }.sortedWith(compareByDescending<NamedFrequency> {
        current.firstOrNull { row -> row.name.id == it.name.id }?.records ?: 0
    }.thenBy { it.name.name }.thenBy { it.name.id }).take(10)
    if (names.isEmpty()) ReviewEmpty(R.string.review_no_patterns, "review_empty_${kind.name.lowercase()}")
    else ReviewValueGroup {
        names.forEach { name ->
            val a = current.firstOrNull { it.name.id == name.name.id }?.records ?: 0
            val b = previous.firstOrNull { it.name.id == name.name.id }?.records ?: 0
            val metric = ReviewMetric(kind, name.name.id)
            val displayName = if (kind == ReviewMetricKind.EMOTION) emotionDisplayName(name.name.id, name.name.name) else name.name.name
            ReviewValue(displayName, stringResource(R.string.review_frequency, a), stringResource(R.string.review_frequency, b),
                reviewChange(a.toDouble(), b.toDouble()), "${kind.name.lowercase()}_${name.name.id}",
                if (a > 0) ({ onSources(period.current, metric) }) else null,
                if (b > 0) ({ onSources(period.previous, metric) }) else null)
            onDetails?.let { TextButton(onClick = { it(name.name.id) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.review_catalog_detail))
            } }
        }
    }
}
@Composable private fun MemoryHighlights(repository: WeeklyReviewRepository, period: ReviewPeriod,
    onEvent: (LifeEventRecord) -> Unit, onMemory: ((LifeEventRecord) -> Unit)?) {
    var retry by remember { mutableIntStateOf(0) }
    val flow = remember(repository, period, retry) { observeReviewSources(repository, period.current, period.zone,
        ReviewMetric(ReviewMetricKind.MEMORIES), 0, 5) }
    val state by flow.collectAsStateWithLifecycle(ReviewSourcesState.Loading)
    when (val current = state) {
        ReviewSourcesState.Loading -> CircularProgressIndicator()
        ReviewSourcesState.Error -> {
            AccessibleFeedback(stringResource(R.string.review_error))
            TextButton(onClick = { retry++ }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.retry)) }
        }
        is ReviewSourcesState.Data -> {
            if (current.page.records.isEmpty()) ReviewEmpty(R.string.review_memory_empty_short, "review_memories_empty")
            current.page.records.forEach { row ->
                EventCard(row, period.zone, onOpen = { onEvent(row) },
                    openActionLabel = if (row.journal != null) R.string.journal_open else R.string.event_open,
                    onMemory = onMemory?.let { { it(row) } })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
