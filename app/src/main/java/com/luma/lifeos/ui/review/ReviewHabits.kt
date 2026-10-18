package com.luma.lifeos.ui.review

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDayState
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitWeekComparison
import com.luma.lifeos.domain.habit.HabitWeekComparisonRow
import com.luma.lifeos.domain.habit.compareHabitWeeks
import com.luma.lifeos.domain.habit.reviewTarget
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.habits.habitUnitLabel
import java.time.LocalDate
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface ReviewHabitsState {
    data object Loading : ReviewHabitsState
    data class Data(val comparison: HabitWeekComparison) : ReviewHabitsState
    data object Error : ReviewHabitsState
}

internal fun observeHabitWeek(repository: HabitRepository, start: LocalDate, through: LocalDate): Flow<ReviewHabitsState> =
    repository.observeChanges().map {
        try {
            val current = repository.week(start, through)
            val previous = repository.week(start.minusWeeks(1), current.through.minusWeeks(1))
            ReviewHabitsState.Data(compareHabitWeeks(current, previous))
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            ReviewHabitsState.Error
        }
    }.onStart { emit(ReviewHabitsState.Loading) }.distinctUntilChanged()

@Composable
internal fun ReviewHabits(
    state: ReviewHabitsState,
    onRetry: () -> Unit,
    onHabit: (UUID, LocalDate) -> Unit,
) {
    ReviewSection(R.string.review_habits, R.drawable.ic_nav_today, "habits") {
        when (val current = state) {
            ReviewHabitsState.Loading -> CircularProgressIndicator(Modifier.testTag("review_habits_loading"))
            ReviewHabitsState.Error -> {
                AccessibleFeedback(stringResource(R.string.review_error))
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("review_habits_retry")) {
                    Text(stringResource(R.string.retry))
                }
            }
            is ReviewHabitsState.Data -> {
                if (current.comparison.rows.isEmpty()) ReviewEmpty(R.string.review_habits_empty, "review_habits_empty")
                current.comparison.rows.forEach { row ->
                    HabitReviewRow(
                        row = row,
                        currentStart = current.comparison.current.start,
                        currentThrough = current.comparison.current.through,
                        previousThrough = current.comparison.previous.through,
                        onHabit = onHabit,
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitReviewRow(
    row: HabitWeekComparisonRow,
    currentStart: LocalDate,
    currentThrough: LocalDate,
    previousThrough: LocalDate,
    onHabit: (UUID, LocalDate) -> Unit,
) {
    var detailsExpanded by rememberSaveable(row.habit.id.toString(), currentStart.toString()) {
        mutableStateOf(false)
    }
    val repetitionCount = row.currentRepetitions
    val scheduledDays = row.currentScheduledDays
    val currentRepetitions = pluralStringResource(
        R.plurals.review_habit_repetitions,
        repetitionCount,
        reviewNumber(repetitionCount),
    )
    val currentScheduledDays = pluralStringResource(
        R.plurals.review_habit_scheduled_days,
        scheduledDays,
        reviewNumber(scheduledDays),
    )
    val previousRepetitions = pluralStringResource(
        R.plurals.review_habit_repetitions,
        row.previousRepetitions,
        reviewNumber(row.previousRepetitions),
    )
    val previousScheduledDays = pluralStringResource(
        R.plurals.review_habit_scheduled_days,
        row.previousScheduledDays,
        reviewNumber(row.previousScheduledDays),
    )
    val slots = habitWeekSlots(currentStart, currentThrough, row)
    val stateDescriptionText = stringResource(
        if (detailsExpanded) R.string.review_details_expanded else R.string.review_details_collapsed,
    )
    val detailsLabel = if (detailsExpanded) {
        stringResource(R.string.review_hide_details, stringResource(R.string.review_habits_day_details))
    } else stringResource(R.string.review_habits_day_details)
    val historyDate = if (row.current != null) currentThrough else previousThrough

    Card(
        Modifier.fillMaxWidth().testTag("review_habit_${row.habit.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(row.habit.name, style = MaterialTheme.typography.titleMedium)
            if (row.current == null) {
                Text(
                    stringResource(R.string.review_habits_no_records_period),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("review_habits_no_records_${row.habit.id}"),
                )
            } else {
                Text(
                    stringResource(R.string.review_habits_summary, currentRepetitions, currentScheduledDays),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("review_habits_current_${row.habit.id}"),
                )
            }

            HabitWeekStrip(slots, row.habit.id)

            if (row.previous != null) {
                Text(
                    stringResource(R.string.review_habits_previous_summary, previousRepetitions, previousScheduledDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("review_habits_previous_${row.habit.id}"),
                )
            }
            if (row.currentScheduledDays == 0 || row.previousScheduledDays == 0) {
                Text(
                    stringResource(R.string.review_habits_no_comparable_days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("review_habits_no_comparison_${row.habit.id}"),
                )
            }

            if (!row.current?.days.isNullOrEmpty()) {
                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        stateDescription = stateDescriptionText
                    }.testTag("review_habit_details_${row.habit.id}"),
                ) {
                    Text(detailsLabel)
                }
                if (detailsExpanded) {
                    row.current?.days.orEmpty().forEach { day -> HabitReviewDay(day, row, onHabit) }
                }
            }

            TextButton(
                onClick = { onHabit(row.habit.id, historyDate) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("review_habits_open_${row.habit.id}"),
            ) {
                Text(stringResource(R.string.review_habits_open))
            }
        }
    }
}

@Composable
private fun HabitWeekStrip(slots: List<HabitWeekSlot>, habitId: UUID) {
    val rows = if (LocalDensity.current.fontScale >= 1.5f) listOf(slots.take(4), slots.drop(4)) else listOf(slots)
    Column(Modifier.fillMaxWidth().testTag("review_habit_week_${habitId}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { slot -> HabitWeekSlotCell(slot, habitId, Modifier.weight(1f)) }
                if (rows.size > 1 && index == 1) Spacer(Modifier.weight((4 - row.size).toFloat()))
            }
        }
    }
}

@Composable
private fun HabitWeekSlotCell(slot: HabitWeekSlot, habitId: UUID, modifier: Modifier = Modifier) {
    val date = slot.date.format(com.luma.lifeos.language.localizedDate(FormatStyle.FULL))
    val state = stringResource(reviewHabitSlotStateLabel(slot))
    val target = slot.day?.reviewTarget()
    val detail = when {
        slot.day == null -> null
        target != null -> stringResource(R.string.review_habits_progress, reviewNumber(slot.day.count), reviewNumber(target))
        slot.day.count > 0 -> stringResource(
            R.string.review_habits_repetitions,
            reviewNumber(slot.day.count),
            habitUnitLabel(slot.day.habit, slot.day.count),
        )
        else -> null
    }
    val description = if (detail == null) {
        stringResource(R.string.review_habits_slot_accessibility, date, state)
    } else {
        stringResource(R.string.review_habits_slot_accessibility_detail, date, state, detail)
    }
    val weekday = slot.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("review_habit_slot_${habitId}_${slot.date}"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(weekday, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text(reviewNumber(slot.date.dayOfMonth), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            Text(habitWeekSlotSymbol(slot), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

private fun habitWeekSlotSymbol(slot: HabitWeekSlot): String {
    if (slot.upcoming) return "↗"
    val day = slot.day ?: return "?"
    if (day.skipRemaining) return "↷"
    return when (day.state) {
        HabitDayState.NOT_SCHEDULED -> "—"
        HabitDayState.PAUSED -> "Ⅱ"
        HabitDayState.ARCHIVED -> "▣"
        HabitDayState.NO_RECORDS -> "○"
        HabitDayState.PARTIAL -> "◐"
        HabitDayState.TARGET_REACHED -> "✓"
    }
}

@StringRes
private fun reviewHabitSlotStateLabel(slot: HabitWeekSlot): Int = when {
    slot.upcoming -> R.string.review_habits_upcoming
    slot.day == null -> R.string.review_habits_no_records_period
    slot.day.skipRemaining && slot.day.count == 0 -> R.string.habit_state_skipped
    slot.day.skipRemaining -> R.string.habit_remaining_skipped
    else -> reviewHabitStateLabel(slot.day)
}

@Composable
private fun HabitReviewDay(day: HabitDay, row: HabitWeekComparisonRow, onHabit: (UUID, LocalDate) -> Unit) {
    val date = day.date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))
    val state = stringResource(reviewHabitStateLabel(day))
    Column(
        Modifier.fillMaxWidth().testTag("review_habit_day_${row.habit.id}_${day.date}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.review_habits_day, date, state), style = MaterialTheme.typography.labelLarge)
        val target = day.reviewTarget()
        if (target != null) {
            Text(stringResource(R.string.review_habits_progress, reviewNumber(day.count), reviewNumber(target)))
        } else if (day.count > 0) {
            Text(stringResource(R.string.review_habits_repetitions, reviewNumber(day.count), habitUnitLabel(row.habit, day.count)))
        }
        if (day.skipRemaining && day.count > 0) Text(
            stringResource(R.string.habit_remaining_skipped),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = { onHabit(row.habit.id, day.date) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("review_habit_open_${row.habit.id}_${day.date}"),
        ) {
            Text(stringResource(R.string.review_habits_open))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@StringRes
private fun reviewHabitStateLabel(day: HabitDay): Int = when {
    day.skipRemaining && day.count == 0 -> R.string.habit_state_skipped
    else -> when (day.state) {
        HabitDayState.NOT_SCHEDULED -> R.string.habit_state_not_scheduled
        HabitDayState.PAUSED -> R.string.habit_state_paused
        HabitDayState.ARCHIVED -> R.string.habit_state_archived
        HabitDayState.NO_RECORDS -> R.string.habit_state_no_records
        HabitDayState.PARTIAL -> R.string.habit_state_partial
        HabitDayState.TARGET_REACHED -> R.string.habit_state_reached
    }
}
