package com.luma.lifeos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.calendar.CalendarDateFormatter
import com.luma.lifeos.calendar.calendarMathFor
import com.luma.lifeos.calendar.CalendarPolicy
import com.luma.lifeos.domain.calendar.CalendarDateParts
import com.luma.lifeos.domain.calendar.CalendarMath
import com.luma.lifeos.domain.calendar.CalendarSystem
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

private const val PICKER_MODE_CALENDAR = "calendar"
private const val PICKER_MODE_MONTH = "month"
private const val PICKER_MODE_YEAR = "year"
private const val MILLIS_PER_DAY = 86_400_000L

@Composable
internal fun LumaCalendarDatePicker(
    state: LumaCalendarDatePickerState,
    policy: CalendarPolicy,
    isSelectable: (LocalDate) -> Boolean,
    modifier: Modifier = Modifier,
) {
    key(policy.system) {
        if (policy.system == CalendarSystem.PERSIAN) {
            PersianCalendarDatePicker(
                state = state,
                policy = policy,
                isSelectable = isSelectable,
                modifier = modifier,
            )
        } else {
            GregorianCalendarDatePicker(
                state = state,
                isSelectable = isSelectable,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GregorianCalendarDatePicker(
    state: LumaCalendarDatePickerState,
    isSelectable: (LocalDate) -> Boolean,
    modifier: Modifier,
) {
    val selectableDates = remember(state, isSelectable) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                state.isSelectable(utcTimeMillis.toPickerDate(), isSelectable)

            override fun isSelectableYear(year: Int): Boolean =
                (year in state.minDate.year..state.maxDate.year) &&
                    calendarYearHasSelectableDate(year, state, isSelectable)
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.selectedDate
            ?.takeIf { state.isSelectable(it, isSelectable) }
            ?.toPickerMillis(),
        initialDisplayedMonthMillis = state.displayedDate.withDayOfMonth(1).toPickerMillis(),
        yearRange = state.minDate.year..state.maxDate.year,
        selectableDates = selectableDates,
    )
    LaunchedEffect(pickerState.selectedDateMillis) {
        val selectedDate = pickerState.selectedDateMillis
            ?.toPickerDate()
            ?.takeIf { state.isSelectable(it, isSelectable) }
        if (selectedDate == null) {
            state.clearSelection()
        } else {
            state.selectDate(selectedDate)
        }
    }
    LaunchedEffect(pickerState.displayedMonthMillis) {
        pickerState.displayedMonthMillis.toPickerDate().let(state::updateDisplayedDate)
    }

    DatePicker(
        state = pickerState,
        colors = DatePickerDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier,
    )
}

@Composable
private fun PersianCalendarDatePicker(
    state: LumaCalendarDatePickerState,
    policy: CalendarPolicy,
    isSelectable: (LocalDate) -> Boolean,
    modifier: Modifier,
) {
    val math = remember { calendarMathFor(CalendarSystem.PERSIAN) }
    val parts = math.fromIso(state.displayedDate)
    val minParts = math.fromIso(state.minDate)
    val maxParts = math.fromIso(state.maxDate)
    val monthIndex = parts.monthIndex()
    val minMonthIndex = minParts.monthIndex()
    val maxMonthIndex = maxParts.monthIndex()
    var mode by rememberSaveable { mutableStateOf(PICKER_MODE_CALENDAR) }
    val locale = policy.displayLocale
    val title = CalendarDateFormatter.formatMonth(state.displayedDate, CalendarSystem.PERSIAN, locale)
    val chooseMonthYearDescription = stringResource(R.string.calendar_choose_month_year)
    val previousMonthAvailable = monthIndex > minMonthIndex
    val nextMonthAvailable = monthIndex < maxMonthIndex

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
    ) {
        if (mode == PICKER_MODE_CALENDAR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = previousMonthAvailable,
                    onClick = { moveCalendarMonth(state, math, parts, -1) },
                    modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
                        .testTag("calendar_picker_previous_month"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron),
                        contentDescription = stringResource(R.string.calendar_previous_month),
                        modifier = Modifier.size(LumaUi.IconSize),
                    )
                }
                LumaTextButton(
                    onClick = { mode = PICKER_MODE_MONTH },
                    modifier = Modifier.weight(1f).testTag("calendar_picker_month_year"),
                ) {
                    androidx.compose.material3.Text(
                        text = title,
                        modifier = Modifier.semantics {
                            contentDescription = "$title, $chooseMonthYearDescription"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    enabled = nextMonthAvailable,
                    onClick = { moveCalendarMonth(state, math, parts, 1) },
                    modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
                        .testTag("calendar_picker_next_month"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_forward),
                        contentDescription = stringResource(R.string.calendar_next_month),
                        modifier = Modifier.size(LumaUi.IconSize),
                    )
                }
            }
            PersianCalendarGrid(
                state = state,
                policy = policy,
                math = math,
                parts = parts,
                isSelectable = isSelectable,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
            ) {
                LumaFilterChip(
                    selected = mode == PICKER_MODE_MONTH,
                    onClick = { mode = PICKER_MODE_MONTH },
                    label = { androidx.compose.material3.Text(stringResource(R.string.calendar_month)) },
                    modifier = Modifier.weight(1f),
                )
                LumaFilterChip(
                    selected = mode == PICKER_MODE_YEAR,
                    onClick = { mode = PICKER_MODE_YEAR },
                    label = { androidx.compose.material3.Text(stringResource(R.string.calendar_year)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (mode == PICKER_MODE_MONTH) {
                PersianMonthChooser(
                    state = state,
                    math = math,
                    selectedYear = parts.year,
                    selectedMonth = parts.month,
                    isSelectable = isSelectable,
                    locale = locale,
                    onSelected = { month ->
                        calendarMonthStart(math, parts.year, month)?.let { state.updateDisplayedDate(it) }
                        mode = PICKER_MODE_CALENDAR
                    },
                )
            } else {
                PersianYearChooser(
                    state = state,
                    math = math,
                    currentParts = parts,
                    minYear = minParts.year,
                    maxYear = maxParts.year,
                    isSelectable = isSelectable,
                    locale = locale,
                    onSelected = { year ->
                        val availableMonths = (1..12).filter {
                            calendarMonthHasSelectableDate(math, year, it, state, isSelectable)
                        }
                        val targetMonth = availableMonths.minByOrNull { abs(it - parts.month) }
                        targetMonth?.let { month ->
                            calendarMonthStart(math, year, month)?.let(state::updateDisplayedDate)
                        }
                        mode = PICKER_MODE_CALENDAR
                    },
                )
            }
        }
    }
}

@Composable
private fun PersianCalendarGrid(
    state: LumaCalendarDatePickerState,
    policy: CalendarPolicy,
    math: CalendarMath,
    parts: CalendarDateParts,
    isSelectable: (LocalDate) -> Boolean,
) {
    val locale = policy.displayLocale
    val monthStart = requireNotNull(calendarMonthStart(math, parts.year, parts.month))
    val monthLength = math.monthLength(parts.year, parts.month)
    val leadingDays = (monthStart.dayOfWeek.value + 1) % 7
    val slotCount = ((leadingDays + monthLength + 6) / 7) * 7
    val weekdaysStart = LocalDate.of(2024, 1, 6)
    val weekdays = (0..6).map { day ->
        CalendarDateFormatter.formatWeekday(weekdaysStart.plusDays(day.toLong()), CalendarSystem.PERSIAN, locale)
    }

    Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
            weekdays.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = LumaUi.MinTouchTargetHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
        repeat(slotCount / 7) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                repeat(7) { weekday ->
                    val slot = week * 7 + weekday
                    val dayNumber = slot - leadingDays + 1
                    if (dayNumber in 1..monthLength) {
                        val date = monthStart.plusDays((dayNumber - 1).toLong())
                        PersianCalendarDay(
                            date = date,
                            selected = state.selectedDate == date,
                            enabled = state.isSelectable(date, isSelectable),
                            locale = locale,
                            onClick = { state.selectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(
                            modifier = Modifier.weight(1f)
                                .heightIn(min = LumaUi.MinTouchTargetHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersianCalendarDay(
    date: LocalDate,
    selected: Boolean,
    enabled: Boolean,
    locale: java.util.Locale,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val day = CalendarDateFormatter.formatDayNumber(
        calendarMathFor(CalendarSystem.PERSIAN).fromIso(date),
        locale,
    )
    val label = CalendarDateFormatter.formatDate(
        date,
        java.time.format.FormatStyle.FULL,
        CalendarSystem.PERSIAN,
        locale,
    )
    val backgroundColor = if (selected) colors.primary else Color.Transparent
    val textColor = when {
        selected -> colors.onPrimary
        enabled -> colors.onSurface
        else -> colors.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                this.selected = selected
                if (!enabled) disabled()
            }
            .testTag("calendar_picker_day_$date"),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = day,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun PersianMonthChooser(
    state: LumaCalendarDatePickerState,
    math: CalendarMath,
    selectedYear: Int,
    selectedMonth: Int,
    isSelectable: (LocalDate) -> Boolean,
    locale: java.util.Locale,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
        for (firstMonth in 1..12 step 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                for (month in firstMonth until firstMonth + 3) {
                    val allowed = calendarMonthHasSelectableDate(math, selectedYear, month, state, isSelectable)
                    val monthDate = calendarMonthStart(math, selectedYear, month)
                    LumaFilterChip(
                        selected = selectedMonth == month,
                        onClick = { onSelected(month) },
                        enabled = allowed && monthDate != null,
                        label = {
                            androidx.compose.material3.Text(
                                text = monthDate?.let {
                                    CalendarDateFormatter.formatMonthName(it, CalendarSystem.PERSIAN, locale)
                                }.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("calendar_picker_month_$month"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersianYearChooser(
    state: LumaCalendarDatePickerState,
    math: CalendarMath,
    currentParts: CalendarDateParts,
    minYear: Int,
    maxYear: Int,
    isSelectable: (LocalDate) -> Boolean,
    locale: java.util.Locale,
    onSelected: (Int) -> Unit,
) {
    val yearCount = maxYear - minYear + 1
    val initialIndex = (currentParts.year - minYear).coerceIn(0, yearCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            .testTag("calendar_picker_years"),
        state = listState,
    ) {
        items(
            count = yearCount,
            key = { index -> (minYear + index).toString() },
        ) { index ->
            val year = minYear + index
            val hasSelectableMonth = (1..12).any {
                calendarMonthHasSelectableDate(math, year, it, state, isSelectable)
            }
            LumaTextButton(
                onClick = { onSelected(year) },
                enabled = hasSelectableMonth,
                modifier = Modifier.fillMaxWidth()
                    .semantics { selected = year == currentParts.year }
                    .testTag("calendar_picker_year_$year"),
            ) {
                androidx.compose.material3.Text(numberFormat.format(year))
            }
        }
    }
}

private fun moveCalendarMonth(
    state: LumaCalendarDatePickerState,
    math: CalendarMath,
    parts: CalendarDateParts,
    monthDelta: Int,
) {
    val targetIndex = parts.monthIndex() + monthDelta
    val targetYear = Math.floorDiv(targetIndex, 12L).toInt()
    val targetMonth = Math.floorMod(targetIndex, 12L).toInt() + 1
    calendarMonthStart(math, targetYear, targetMonth)?.let(state::updateDisplayedDate)
}

private fun CalendarDateParts.monthIndex(): Long =
    year.toLong() * 12L + month - 1L

private fun calendarMonthStart(math: CalendarMath, year: Int, month: Int): LocalDate? =
    runCatching { math.toIso(CalendarDateParts(year, month, 1)) }.getOrNull()

private fun calendarMonthHasSelectableDate(
    math: CalendarMath,
    year: Int,
    month: Int,
    state: LumaCalendarDatePickerState,
    isSelectable: (LocalDate) -> Boolean,
): Boolean {
    val firstDay = calendarMonthStart(math, year, month) ?: return false
    val length = runCatching { math.monthLength(year, month) }.getOrNull() ?: return false
    return (0 until length).any { offset ->
        val date = runCatching { firstDay.plusDays(offset.toLong()) }.getOrNull()
        date != null && state.isSelectable(date, isSelectable)
    }
}

private fun calendarYearHasSelectableDate(
    year: Int,
    state: LumaCalendarDatePickerState,
    isSelectable: (LocalDate) -> Boolean,
): Boolean {
    val firstDay = runCatching { LocalDate.of(year, 1, 1) }.getOrNull() ?: return false
    val lastDay = runCatching { LocalDate.of(year, 12, 31) }.getOrNull() ?: return false
    var date = maxOf(firstDay, state.minDate)
    val end = minOf(lastDay, state.maxDate)
    while (!date.isAfter(end)) {
        if (state.isSelectable(date, isSelectable)) return true
        date = date.plusDays(1)
    }
    return false
}

private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    LocalDate.ofEpochDay(Math.floorDiv(this, MILLIS_PER_DAY))
