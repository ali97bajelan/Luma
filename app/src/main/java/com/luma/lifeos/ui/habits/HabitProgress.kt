package com.luma.lifeos.ui.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
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
    val fraction = habitProgressFraction(day.count, target)
    val animatedProgress = remember(day.habit.id, day.date, target) { Animatable(fraction) }
    var lastCount by remember(day.habit.id, day.date, target) { mutableStateOf(day.count) }
    LaunchedEffect(day.count) {
        if (day.count != lastCount) {
            lastCount = day.count
            animatedProgress.animateTo(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 180),
            )
        }
    }
    val unit = habitUnitLabel(day.habit, target)
    val numberFormat = NumberFormat.getIntegerInstance()
    val count = numberFormat.format(day.count)
    val formattedTarget = numberFormat.format(target)
    val description = stringResource(R.string.habit_progress_accessibility, day.habit.name, day.count, target, unit)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .testTag("habit_progress_${day.habit.id}"),
    ) {
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
            progress = { animatedProgress.value },
            modifier = Modifier.fillMaxWidth().height(4.dp)
                .clearAndSetSemantics { }
                .testTag("habit_progress_track_${day.habit.id}"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
        )
    }
}
