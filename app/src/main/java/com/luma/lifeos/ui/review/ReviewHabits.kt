package com.luma.lifeos.ui.review

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.FormatStyle
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

private fun observeHabitWeek(repository: HabitRepository, start: LocalDate, through: LocalDate): Flow<ReviewHabitsState> =
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
    repository: HabitRepository,
    start: LocalDate,
    through: LocalDate,
    onHabit: (UUID, LocalDate) -> Unit,
) {
    var retry by remember(repository, start, through) { mutableIntStateOf(0) }
    val observation = remember(repository, start, through, retry) { observeHabitWeek(repository, start, through) }
    val state by observation.collectAsStateWithLifecycle(ReviewHabitsState.Loading)
    ReviewSection(R.string.review_habits, R.drawable.ic_nav_today, "habits") {
        when (val current = state) {
            ReviewHabitsState.Loading -> CircularProgressIndicator(Modifier.testTag("review_habits_loading"))
            ReviewHabitsState.Error -> {
                AccessibleFeedback(stringResource(R.string.review_error))
                TextButton(onClick = { retry++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("review_habits_retry")) {
                    Text(stringResource(R.string.retry))
                }
            }
            is ReviewHabitsState.Data -> {
                if (current.comparison.rows.isEmpty()) ReviewEmpty(R.string.review_habits_empty, "review_habits_empty")
                current.comparison.rows.forEach { row ->
                    HabitReviewRow(row, current.comparison.previous.through, onHabit)
                }
            }
        }
    }
}

@Composable
private fun HabitReviewRow(
    row: HabitWeekComparisonRow,
    previousThrough: LocalDate,
    onHabit: (UUID, LocalDate) -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("review_habit_${row.habit.id}"),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.habit.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.review_habits_current_total, row.currentRepetitions, row.currentScheduledDays),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("review_habits_current_${row.habit.id}"))
            Text(stringResource(R.string.review_habits_previous_total, row.previousRepetitions, row.previousScheduledDays),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("review_habits_previous_${row.habit.id}"))
            row.current?.days.orEmpty().forEach { day -> HabitReviewDay(day, row, onHabit) }
            TextButton(onClick = { onHabit(row.habit.id, previousThrough) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("review_habits_previous_open_${row.habit.id}")) {
                Text(stringResource(R.string.review_habits_open))
            }
        }
    }
}

@Composable
private fun HabitReviewDay(day: HabitDay, row: HabitWeekComparisonRow, onHabit: (UUID, LocalDate) -> Unit) {
    val date = day.date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))
    val state = stringResource(reviewHabitStateLabel(day))
    Column(Modifier.fillMaxWidth().testTag("review_habit_day_${row.habit.id}_${day.date}"),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.review_habits_day, date, state), style = MaterialTheme.typography.labelLarge)
        val target = day.reviewTarget()
        if (target != null) {
            val number = NumberFormat.getIntegerInstance().format(day.count)
            val formattedTarget = NumberFormat.getIntegerInstance().format(target)
            Text(stringResource(R.string.habit_progress, number, formattedTarget, habitUnitLabel(row.habit, target)))
        } else if (day.count > 0) {
            Text(stringResource(R.string.review_habits_repetitions, day.count, habitUnitLabel(row.habit, day.count)))
        }
        if (day.skipRemaining && day.count > 0) Text(stringResource(R.string.habit_remaining_skipped),
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onHabit(row.habit.id, day.date) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("review_habit_open_${row.habit.id}_${day.date}")) {
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
