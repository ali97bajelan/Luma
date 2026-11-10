package com.luma.lifeos.ui.calendar

import com.luma.lifeos.ui.common.LumaUi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
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
import com.luma.lifeos.calendar.CalendarDateFormatter
import com.luma.lifeos.calendar.calendarMathFor
import com.luma.lifeos.domain.calendar.CalendarMath
import com.luma.lifeos.domain.calendar.CalendarMode
import com.luma.lifeos.domain.calendar.calendarGridDates
import com.luma.lifeos.domain.calendar.canMoveCalendarSelection
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.language.DisplayTimePolicy
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun CalendarGrid(
    state: CalendarUiState,
    displayTime: DisplayTimePolicy,
    onSelectDate: (LocalDate) -> Unit,
    onSetMode: (CalendarMode) -> Unit,
    onSetCalendarSystem: (com.luma.lifeos.domain.calendar.CalendarSystem) -> Unit,
    calendarSwitchEnabled: Boolean,
    onMovePeriod: (Int) -> Unit,
    onRetry: (CalendarSource) -> Unit,
) {
    val locale = displayTime.locale
    val math = remember(state.calendarSystem) { calendarMathFor(state.calendarSystem) }
    val monthLabel = remember(state.selectedDate, state.calendarSystem, locale) {
        CalendarDateFormatter.formatMonth(state.selectedDate, state.calendarSystem, locale)
    }
    val dates = remember(state.selectedDate, state.mode, state.firstDay, math) {
        calendarGridDates(state.selectedDate, state.mode, state.firstDay, math)
    }
    val markerScroll = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale
    val canPrevious = runCatching {
        canMoveCalendarSelection(state.selectedDate, state.mode, -1, math)
    }.getOrDefault(false)
    val canNext = runCatching {
        canMoveCalendarSelection(state.selectedDate, state.mode, 1, math)
    }.getOrDefault(false)

    LumaSectionSurface(modifier = Modifier.testTag("calendar_grid_card")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 560.dp || fontScale >= 1.3f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                    Text(
                        monthLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { stateDescription = monthLabel }
                            .testTag("calendar_month_label"),
                    )
                    CalendarSystemControl(
                        system = state.calendarSystem,
                        onSelect = onSetCalendarSystem,
                        enabled = calendarSwitchEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CalendarModeControl(state.mode, onSetMode, Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CalendarPeriodButtons(state.mode, canPrevious, canNext, onMovePeriod)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
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
                        CalendarSystemControl(
                            system = state.calendarSystem,
                            onSelect = onSetCalendarSystem,
                            enabled = calendarSwitchEnabled,
                            modifier = Modifier.widthIn(max = 420.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CalendarModeControl(state.mode, onSetMode)
                        CalendarPeriodButtons(state.mode, canPrevious, canNext, onMovePeriod)
                    }
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
                                        calendarMath = math,
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
        CalendarMarkerSourceStatus(state.memoryMarkers, R.string.calendar_memories_loading, R.string.calendar_memories_error,
            "calendar_memory_markers", onRetry = { onRetry(CalendarSource.MEMORIES) })
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
            com.luma.lifeos.ui.common.LumaTextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
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
    calendarMath: CalendarMath,
    markers: DayMarkers,
    markerState: CalendarUiState,
    displayTime: DisplayTimePolicy,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isSelected = date == selectedDate
    val isToday = date == today
    val dateParts = remember(date, calendarMath) { calendarMath.fromIso(date) }
    val selectedParts = remember(selectedDate, calendarMath) { calendarMath.fromIso(selectedDate) }
    val isOverflow = mode == CalendarMode.MONTH &&
        (dateParts.year != selectedParts.year || dateParts.month != selectedParts.month)
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
    val dateLabel = remember(date, calendarMath.system, displayTime.locale) {
        CalendarDateFormatter.formatDate(date, FormatStyle.FULL, calendarMath.system, displayTime.locale)
    }
    val taskCount = when (val source = markerState.taskMarkers) {
        is CalendarDataState.Ready -> pluralStringResource(
            R.plurals.calendar_tasks_count, source.value[date]?.count ?: 0, source.value[date]?.count ?: 0,
        )
        else -> stringResource(R.string.calendar_count_unavailable)
    }
    val memoryCount = when (val source = markerState.memoryMarkers) {
        is CalendarDataState.Ready -> pluralStringResource(
            R.plurals.calendar_memories_count, source.value[date] ?: 0, source.value[date] ?: 0,
        )
        else -> stringResource(R.string.calendar_count_unavailable)
    }
    val overdueStatus = when (markerState.taskMarkers) {
        is CalendarDataState.Ready -> stringResource(
            if (markers.overdue) R.string.calendar_day_has_overdue_tasks else R.string.calendar_day_no_overdue_tasks,
        )
        else -> stringResource(R.string.calendar_count_unavailable)
    }
    val description = stringResource(
        R.string.calendar_day_accessibility,
        dateLabel,
        taskCount,
        memoryCount,
        overdueStatus,
    )
    val todayStatus = stringResource(R.string.calendar_day_today)
    val locale = displayTime.locale
    val number = remember(dateParts, locale) {
        CalendarDateFormatter.formatDayNumber(dateParts, locale)
    }
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
                if (isToday) stateDescription = todayStatus
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
                if (markers.tasks > 0) MarkerDot(
                    kind = if (markers.overdue) CalendarMarkerKind.OVERDUE else CalendarMarkerKind.TASK,
                    color = if (markers.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                if (markers.memories > 0) MarkerDot(CalendarMarkerKind.MEMORY, MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

private enum class CalendarMarkerKind { TASK, MEMORY, OVERDUE }

private fun calendarMarkerShape(kind: CalendarMarkerKind): Shape = when (kind) {
    CalendarMarkerKind.TASK -> RoundedCornerShape(1.dp)
    CalendarMarkerKind.MEMORY -> CircleShape
    CalendarMarkerKind.OVERDUE -> CutCornerShape(percent = 50)
}

@Composable
private fun MarkerDot(kind: CalendarMarkerKind, color: androidx.compose.ui.graphics.Color) {
    val size = if (kind == CalendarMarkerKind.OVERDUE) 7.dp else 5.dp
    Surface(Modifier.size(size), shape = calendarMarkerShape(kind), color = color, content = {})
}

@Composable
private fun CalendarMarkerLegend() {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LumaUi.SectionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(CalendarMarkerKind.TASK, MaterialTheme.colorScheme.primary, R.string.calendar_legend_tasks)
        LegendItem(CalendarMarkerKind.MEMORY, MaterialTheme.colorScheme.secondary, R.string.calendar_legend_memories)
        LegendItem(CalendarMarkerKind.OVERDUE, MaterialTheme.colorScheme.error, R.string.calendar_legend_overdue)
    }
}

@Composable
private fun LegendItem(kind: CalendarMarkerKind, color: androidx.compose.ui.graphics.Color, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(Modifier.size(9.dp), shape = calendarMarkerShape(kind), color = color, content = {})
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private data class DayMarkers(val tasks: Int, val overdue: Boolean, val memories: Int)

private fun markerCounts(state: CalendarUiState, date: LocalDate): DayMarkers = DayMarkers(
    tasks = (state.taskMarkers as? CalendarDataState.Ready)?.value?.get(date)?.count ?: 0,
    overdue = (state.taskMarkers as? CalendarDataState.Ready)?.value?.get(date)?.overdue == true,
    memories = (state.memoryMarkers as? CalendarDataState.Ready)?.value?.get(date) ?: 0,
)

private val MIN_GRID_WIDTH = 336.dp
