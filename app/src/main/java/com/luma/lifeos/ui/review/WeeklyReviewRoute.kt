package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.language.emotionDisplayName
import com.luma.lifeos.ui.timeline.EventCard
import com.luma.lifeos.ui.today.pickerDate
import com.luma.lifeos.ui.today.pickerMillis
import java.time.*
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReviewRoute(repository: WeeklyReviewRepository, today: LocalDate, zone: ZoneId,
    onSources: (ReviewDates, ReviewMetric) -> Unit, onEvent: (LifeEventRecord) -> Unit,
    onPerson: (UUID) -> Unit = {}, onPlace: (UUID) -> Unit = {}, onUsage: (LocalDate) -> Unit = {},
    onMemory: ((LifeEventRecord) -> Unit)? = null) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val period = reviewPeriod(restoredReviewDate(selected, today), today, zone)
    val format = com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)
    key(period, retry) {
        val observation = remember(repository) { observeWeeklyReview(repository, period) }
        val state by observation.collectAsStateWithLifecycle(WeeklyReviewState.Loading)
        Box(Modifier.fillMaxSize().testTag("screen_review")) {
            LazyColumn(Modifier.fillMaxSize().testTag("memory_review_list"),
                contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "heading") {
                    Text(stringResource(R.string.review_weekly_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.review_saved_explanation))
                    TextButton(onClick = { picking = true }, modifier = Modifier.testTag("review_picker")) {
                        Text(stringResource(R.string.memory_review_range, period.current.start.format(format), period.current.through.format(format)))
                    }
                    Text(stringResource(R.string.review_comparison_range, period.previous.start.format(format), period.previous.through.format(format)))
                    Text(zone.id, style = MaterialTheme.typography.bodySmall)
                    if (period.partial) Text(stringResource(R.string.review_partial))
                    TextButton(enabled = period.current.start.year > -999999998,
                        onClick = { selected = period.current.start.minusWeeks(1).toString() }, modifier = Modifier.testTag("review_previous")) {
                        Text(stringResource(R.string.review_previous_week))
                    }
                    TextButton(onClick = { selected = null }, modifier = Modifier.testTag("review_this_week")) { Text(stringResource(R.string.review_this_week)) }
                    TextButton(enabled = period.current.start.plusWeeks(1) <= today,
                        onClick = { selected = period.current.start.plusWeeks(1).toString() }, modifier = Modifier.testTag("review_next")) {
                        Text(stringResource(R.string.review_next_week))
                    }
                }
                when (val current = state) {
                    WeeklyReviewState.Loading -> item(key = "loading") { CircularProgressIndicator() }
                    WeeklyReviewState.Error -> item(key = "error") {
                        Text(stringResource(R.string.review_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retry++ }, modifier = Modifier.testTag("review_retry")) { Text(stringResource(R.string.retry)) }
                    }
                    is WeeklyReviewState.Data -> {
                        val report = current.report
                        val a = report.current; val b = report.previous
                        if (a.records == 0 && a.usage?.totalMillis == null && a.activities.isEmpty()) item(key = "empty") {
                            Text(stringResource(R.string.review_empty), modifier = Modifier.testTag("review_empty"))
                        }
                        item(key = "counts") {
                            ReviewCounts(report, onSources)
                        }
                        item(key = "mood") { ReviewMood(report, onSources) }
                        item(key = "memories") {
                            Text(stringResource(R.string.review_memories), style = MaterialTheme.typography.titleLarge)
                            MemoryHighlights(repository, period, onEvent, onMemory)
                            TextButton(onClick = { onSources(period.current, ReviewMetric(ReviewMetricKind.MEMORIES)) }) { Text(stringResource(R.string.review_all_memories)) }
                        }
                        item(key = "people") { CatalogPatterns(stringResource(R.string.people), a.people, b.people, period,
                            ReviewMetricKind.PERSON, onSources, onPerson) }
                        item(key = "places") { CatalogPatterns(stringResource(R.string.places), a.places, b.places, period,
                            ReviewMetricKind.PLACE, onSources, onPlace) }
                        item(key = "usage") { ReviewDigital(report, onUsage) }
                        item(key = "activity") { ReviewActivities(report, onSources) }
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
            }) { Text(stringResource(R.string.select_date)) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
    }
}

@Composable private fun ReviewCounts(report: WeeklyReview, onSources: (ReviewDates, ReviewMetric) -> Unit) {
    listOf(ReviewMetricKind.ENTRIES to (report.current.records to report.previous.records),
        ReviewMetricKind.JOURNALS to (report.current.journals to report.previous.journals),
        ReviewMetricKind.MEMORIES to (report.current.memories to report.previous.memories)).forEach { (kind, counts) ->
        val metric = ReviewMetric(kind)
        ReviewValue(stringResource(kind.label()), reviewNumber(counts.first), reviewNumber(counts.second),
            reviewChange(counts.first.toDouble(), counts.second.toDouble()), kind.name.lowercase(),
            { onSources(report.period.current, metric) }, { onSources(report.period.previous, metric) })
    }
}
@Composable private fun ReviewMood(report: WeeklyReview, onSources: (ReviewDates, ReviewMetric) -> Unit) {
    Text(stringResource(R.string.review_mood_patterns), style = MaterialTheme.typography.titleLarge)
    listOf(ReviewMetricKind.MOOD to (report.current.mood?.score to report.previous.mood?.score),
        ReviewMetricKind.ENERGY to (report.current.mood?.energy to report.previous.mood?.energy),
        ReviewMetricKind.STRESS to (report.current.mood?.stress to report.previous.mood?.stress)).forEach { (kind, scales) ->
        ReviewValue(stringResource(kind.label()), reviewScale(scales.first), reviewScale(scales.second),
            reviewChange(scales.first?.average, scales.second?.average), kind.name.lowercase(),
            scales.first?.let { { onSources(report.period.current, ReviewMetric(kind)) } },
            scales.second?.let { { onSources(report.period.previous, ReviewMetric(kind)) } })
    }
    Text(stringResource(R.string.review_mood_dates), style = MaterialTheme.typography.titleMedium)
    val format = com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)
    report.current.moodDays.forEach { day ->
        Text(day.date.format(format))
        Text(reviewScale(day.mood?.score))
        if (day.mood != null) TextButton(onClick = { onSources(ReviewDates(day.date, day.date), ReviewMetric(ReviewMetricKind.MOOD)) }) {
            Text(stringResource(R.string.review_view_sources))
        }
    }
    CatalogPatterns(stringResource(R.string.review_top_emotions), report.current.emotions, report.previous.emotions,
        report.period, ReviewMetricKind.EMOTION, onSources, null)
}
@Composable private fun CatalogPatterns(heading: String, current: List<NamedFrequency>, previous: List<NamedFrequency>, period: ReviewPeriod,
    kind: ReviewMetricKind, onSources: (ReviewDates, ReviewMetric) -> Unit, onDetails: ((UUID) -> Unit)?) {
    Text(heading, style = MaterialTheme.typography.titleLarge)
    if (kind != ReviewMetricKind.EMOTION) Text(stringResource(R.string.review_linked_explanation))
    val names = (current + previous).distinctBy { it.name.id }.sortedWith(compareByDescending<NamedFrequency> {
        current.firstOrNull { row -> row.name.id == it.name.id }?.records ?: 0
    }.thenBy { it.name.name }.thenBy { it.name.id }).take(10)
    if (names.isEmpty()) Text(stringResource(R.string.review_no_patterns))
    names.forEach { name ->
        val a = current.firstOrNull { it.name.id == name.name.id }?.records ?: 0
        val b = previous.firstOrNull { it.name.id == name.name.id }?.records ?: 0
        val metric = ReviewMetric(kind, name.name.id)
        val displayName = if (kind == ReviewMetricKind.EMOTION) emotionDisplayName(name.name.id, name.name.name) else name.name.name
        ReviewValue(displayName, stringResource(R.string.review_frequency, a), stringResource(R.string.review_frequency, b),
            reviewChange(a.toDouble(), b.toDouble()), "${kind.name.lowercase()}_${name.name.id}",
            { onSources(period.current, metric) }, { onSources(period.previous, metric) })
        onDetails?.let { TextButton(onClick = { it(name.name.id) }) { Text(stringResource(R.string.review_catalog_detail)) } }
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
            Text(stringResource(R.string.review_error))
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
        }
        is ReviewSourcesState.Data -> {
            if (current.page.records.isEmpty()) Text(stringResource(R.string.memory_review_empty))
            current.page.records.forEach { row ->
                EventCard(row, period.zone, { onEvent(row) }, onMemory?.let { { it(row) } })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
