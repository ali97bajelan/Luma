package com.luma.lifeos.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.calendar.CalendarMode
import com.luma.lifeos.domain.calendar.canMoveCalendarSelection
import com.luma.lifeos.domain.todo.TodoTextLimits
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.language.DisplayTimePolicy
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.chrono.IsoChronology
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
internal fun CalendarGrid(
    state: CalendarUiState,
    displayTime: DisplayTimePolicy,
    onSelectDate: (LocalDate) -> Unit,
    onSetMode: (CalendarMode) -> Unit,
    onMovePeriod: (Int) -> Unit,
    onRetry: (CalendarSource) -> Unit,
) {
    val locale = displayTime.locale
    val monthLabel = remember(state.selectedDate, locale) {
        DateTimeFormatterBuilder().appendPattern("LLLL uuuu").toFormatter(locale)
            .withChronology(IsoChronology.INSTANCE)
            .withDecimalStyle(DecimalStyle.of(locale))
            .format(state.selectedDate)
    }
    val dates = remember(state.selectedDate, state.mode, state.firstDay) {
        calendarGridDates(state.selectedDate, state.mode, state.firstDay)
    }
    val markerScroll = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale
    val canPrevious = runCatching {
        canMoveCalendarSelection(state.selectedDate, state.mode, -1)
    }.getOrDefault(false)
    val canNext = runCatching {
        canMoveCalendarSelection(state.selectedDate, state.mode, 1)
    }.getOrDefault(false)

    LumaSectionSurface(modifier = Modifier.testTag("calendar_grid_card")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 500.dp || fontScale >= 1.5f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        monthLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { stateDescription = monthLabel }
                            .testTag("calendar_month_label"),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CalendarModeControl(state.mode, onSetMode, Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            CalendarPeriodButtons(state.mode, canPrevious, canNext, onMovePeriod)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        monthLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { stateDescription = monthLabel }
                            .testTag("calendar_month_label"),
                    )
                    CalendarModeControl(state.mode, onSetMode)
                    CalendarPeriodButtons(state.mode, canPrevious, canNext, onMovePeriod)
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gridWidth = maxOf(maxWidth, MIN_GRID_WIDTH)
            Column(
                Modifier.fillMaxWidth().horizontalScroll(markerScroll)
                    .testTag("calendar_grid_viewport"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Column(Modifier.width(gridWidth)) {
                    WeekdayHeader(state.firstDay, locale)
                    dates.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(Modifier.weight(1f).heightIn(min = 56.dp))
                                } else {
                                    CalendarDateCell(
                                        date = date,
                                        selectedDate = state.selectedDate,
                                        today = state.today,
                                        mode = state.mode,
                                        markers = markerCounts(state, date),
                                        markerState = state,
                                        displayTime = displayTime,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelectDate(date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        CalendarMarkerLegend()
        CalendarMarkerSourceStatus(state.taskMarkers, R.string.calendar_tasks_loading, R.string.calendar_tasks_error,
            "calendar_task_markers", onRetry = { onRetry(CalendarSource.TASKS) })
        CalendarMarkerSourceStatus(state.habitMarkers, R.string.calendar_habits_loading, R.string.calendar_habits_error,
            "calendar_habit_markers", onRetry = { onRetry(CalendarSource.HABITS) })
        CalendarMarkerSourceStatus(state.journalMarkers, R.string.calendar_reflection_loading, R.string.calendar_reflection_error,
            "calendar_journal_markers", onRetry = { onRetry(CalendarSource.JOURNALS) })
    }
}

@Composable
private fun CalendarMarkerSourceStatus(
    source: CalendarDataState<*>,
    loadingMessage: Int,
    errorMessage: Int,
    tag: String,
    onRetry: () -> Unit,
) {
    when (source) {
        CalendarDataState.Loading -> Text(
            stringResource(loadingMessage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("${tag}_loading"),
        )
        CalendarDataState.Error -> Row(
            Modifier.fillMaxWidth().testTag("${tag}_error"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(errorMessage), Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
        is CalendarDataState.Ready -> Unit
    }
}

@Composable
private fun CalendarModeControl(
    mode: CalendarMode,
    onSetMode: (CalendarMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalendarModeOption(
                selected = mode == CalendarMode.MONTH,
                label = R.string.calendar_month,
                tag = "calendar_mode_month",
                onClick = { onSetMode(CalendarMode.MONTH) },
            )
            CalendarModeOption(
                selected = mode == CalendarMode.WEEK,
                label = R.string.calendar_week,
                tag = "calendar_mode_week",
                onClick = { onSetMode(CalendarMode.WEEK) },
            )
        }
    }
}

@Composable
private fun CalendarModeOption(
    selected: Boolean,
    label: Int,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = selected }.testTag(tag),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(label), maxLines = 1)
        }
    }
}

@Composable
private fun CalendarPeriodButtons(
    mode: CalendarMode,
    canPrevious: Boolean,
    canNext: Boolean,
    onMovePeriod: (Int) -> Unit,
) {
    IconButton(
        onClick = { onMovePeriod(-1) },
        enabled = canPrevious,
        modifier = Modifier.size(48.dp).testTag("calendar_previous"),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = stringResource(
                if (mode == CalendarMode.MONTH) R.string.calendar_previous_month else R.string.calendar_previous_week,
            ),
        )
    }
    IconButton(
        onClick = { onMovePeriod(1) },
        enabled = canNext,
        modifier = Modifier.size(48.dp).testTag("calendar_next"),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_forward),
            contentDescription = stringResource(
                if (mode == CalendarMode.MONTH) R.string.calendar_next_month else R.string.calendar_next_week,
            ),
        )
    }
}

@Composable
private fun WeekdayHeader(firstDay: DayOfWeek, locale: Locale) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        repeat(7) { offset ->
            val day = firstDay.plus(offset.toLong())
            Text(
                day.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarDateCell(
    date: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    mode: CalendarMode,
    markers: DayMarkers,
    markerState: CalendarUiState,
    displayTime: DisplayTimePolicy,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isSelected = date == selectedDate
    val isToday = date == today
    val isOverflow = mode == CalendarMode.MONTH && date.month != selectedDate.month
    val fill = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val foreground = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isOverflow -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (isToday && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    val status = stringResource(
        when {
            isToday && isSelected -> R.string.calendar_day_today_selected
            isToday -> R.string.calendar_day_today
            isSelected -> R.string.calendar_day_selected
            else -> R.string.calendar_day_regular
        },
    )
    val dateLabel = date.format(displayTime.date(FormatStyle.FULL))
    val taskCount = when (val source = markerState.taskMarkers) {
        is CalendarDataState.Ready -> pluralStringResource(
            R.plurals.calendar_tasks_count, source.value[date]?.count ?: 0, source.value[date]?.count ?: 0,
        )
        else -> stringResource(R.string.calendar_count_unavailable)
    }
    val habitCount = when (val source = markerState.habitMarkers) {
        is CalendarDataState.Ready -> pluralStringResource(
            R.plurals.calendar_habits_count, source.value[date] ?: 0, source.value[date] ?: 0,
        )
        else -> stringResource(R.string.calendar_count_unavailable)
    }
    val journalCount = when (val source = markerState.journalMarkers) {
        is CalendarDataState.Ready -> pluralStringResource(
            R.plurals.calendar_journals_count, source.value[date] ?: 0, source.value[date] ?: 0,
        )
        else -> stringResource(R.string.calendar_count_unavailable)
    }
    val description = stringResource(
        R.string.calendar_day_accessibility,
        dateLabel,
        status,
        taskCount,
        habitCount,
        journalCount,
    )
    val locale = displayTime.locale
    val number = remember(date.dayOfMonth, locale) { java.text.NumberFormat.getIntegerInstance(locale).format(date.dayOfMonth) }
    val bringIntoViewRequester = remember(date) { BringIntoViewRequester() }
    LaunchedEffect(isSelected) {
        if (isSelected) bringIntoViewRequester.bringIntoView()
    }

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).bringIntoViewRequester(bringIntoViewRequester)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                stateDescription = status
                selected = isSelected
            }
            .testTag("calendar_day_${date}"),
        shape = CircleShape,
        color = fill,
        contentColor = foreground,
        border = border,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(number, style = MaterialTheme.typography.bodyMedium, color = foreground)
            Row(
                Modifier.heightIn(min = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (markers.tasks > 0) MarkerDot(if (markers.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (markers.habits > 0) MarkerDot(MaterialTheme.colorScheme.tertiary)
                if (markers.journals > 0) MarkerDot(MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun MarkerDot(color: androidx.compose.ui.graphics.Color) {
    Surface(Modifier.size(5.dp), shape = CircleShape, color = color, content = {})
}

@Composable
private fun CalendarMarkerLegend() {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(MaterialTheme.colorScheme.primary, R.string.calendar_legend_tasks)
        LegendItem(MaterialTheme.colorScheme.tertiary, R.string.calendar_legend_habits)
        LegendItem(MaterialTheme.colorScheme.secondary, R.string.calendar_legend_journals)
        LegendItem(MaterialTheme.colorScheme.error, R.string.calendar_legend_overdue)
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(Modifier.size(8.dp), shape = CircleShape, color = color, content = {})
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private data class DayMarkers(val tasks: Int, val overdue: Boolean, val habits: Int, val journals: Int)

private fun markerCounts(state: CalendarUiState, date: LocalDate): DayMarkers = DayMarkers(
    tasks = (state.taskMarkers as? CalendarDataState.Ready)?.value?.get(date)?.count ?: 0,
    overdue = (state.taskMarkers as? CalendarDataState.Ready)?.value?.get(date)?.overdue == true,
    habits = (state.habitMarkers as? CalendarDataState.Ready)?.value?.get(date) ?: 0,
    journals = (state.journalMarkers as? CalendarDataState.Ready)?.value?.get(date) ?: 0,
)

private fun calendarGridDates(selected: LocalDate, mode: CalendarMode, firstDay: DayOfWeek): List<LocalDate?> {
    val first = when (mode) {
        CalendarMode.WEEK -> selected.with(TemporalAdjusters.previousOrSame(firstDay))
        CalendarMode.MONTH -> {
            val monthStart = selected.withDayOfMonth(1)
            val offset = (monthStart.dayOfWeek.value - firstDay.value + 7) % 7
            monthStart.minusDays(offset.toLong())
        }
    }
    val slots = when (mode) {
        CalendarMode.WEEK -> 7
        CalendarMode.MONTH -> {
            val offset = (selected.withDayOfMonth(1).dayOfWeek.value - firstDay.value + 7) % 7
            ((offset + selected.lengthOfMonth() + 6) / 7) * 7
        }
    }
    return List(slots) { index ->
        first.plusDays(index.toLong()).takeIf { it.year in TodoTextLimits.MIN_YEAR..TodoTextLimits.MAX_YEAR }
    }
}

private val MIN_GRID_WIDTH = 336.dp
