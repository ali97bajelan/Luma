package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.reviewTarget
import java.text.NumberFormat
import java.time.DayOfWeek

enum class HabitSchedulePreset { DAILY, WEEKDAYS, CUSTOM }

fun schedulePreset(days: Set<DayOfWeek>): HabitSchedulePreset = when (days) {
    DayOfWeek.entries.toSet() -> HabitSchedulePreset.DAILY
    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) ->
        HabitSchedulePreset.WEEKDAYS
    else -> HabitSchedulePreset.CUSTOM
}

fun schedulePresetDays(preset: HabitSchedulePreset, currentDays: Set<DayOfWeek>): Set<DayOfWeek> = when (preset) {
    HabitSchedulePreset.DAILY -> DayOfWeek.entries.toSet()
    HabitSchedulePreset.WEEKDAYS -> setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    HabitSchedulePreset.CUSTOM -> currentDays
}

fun habitProgressFraction(count: Int, target: Int): Float =
    if (target <= 0) 0f else (count.toFloat() / target).coerceIn(0f, 1f)

@Composable
internal fun HabitProgressContent(day: HabitDay) {
    val target = day.reviewTarget() ?: return
    val unit = habitUnitLabel(day.habit, target)
    val numberFormat = NumberFormat.getIntegerInstance()
    val count = numberFormat.format(day.count)
    val formattedTarget = numberFormat.format(target)
    val description = stringResource(R.string.habit_progress_accessibility, day.habit.name, day.count, target, unit)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()
        .semantics { contentDescription = description }.testTag("habit_progress_${day.habit.id}")) {
        Text(
            stringResource(R.string.habit_progress, count, formattedTarget),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            unit,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { habitProgressFraction(day.count, target) },
            modifier = Modifier.fillMaxWidth().height(4.dp)
                .testTag("habit_progress_track_${day.habit.id}"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
        )
    }
}
