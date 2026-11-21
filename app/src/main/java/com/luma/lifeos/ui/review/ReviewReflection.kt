package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import com.luma.lifeos.domain.review.WeeklyReview
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.ui.common.LumaTextButton
import com.luma.lifeos.ui.common.LumaUi
import java.time.LocalDate
import java.time.format.FormatStyle

@Composable
internal fun ReviewReflection(
    report: WeeklyReview,
    today: LocalDate,
    onCaptureMemory: (LocalDate) -> Unit,
    onOpenTasks: (() -> Unit)?,
    onOpenHabits: ((LocalDate) -> Unit)?,
    canReadSummary: Boolean,
) {
    val current = report.current
    val previous = report.previous
    val currentMoodSamples = current.mood?.score?.samples?.let {
        stringResource(R.string.review_sample_count, it)
    } ?: stringResource(R.string.review_reflection_no_mood_samples)
    val previousMoodSamples = previous.mood?.score?.samples?.let {
        stringResource(R.string.review_sample_count, it)
    } ?: stringResource(R.string.review_reflection_no_mood_samples)
    val todayLabel = localizedDate(today, FormatStyle.MEDIUM)
    val reviewedThroughLabel = localizedDate(report.period.current.through, FormatStyle.MEDIUM)

    ReviewSection(
        title = R.string.review_reflection_title,
        icon = R.drawable.ic_bookmark_outline,
        tag = "reflection",
    ) {
        Text(
            stringResource(R.string.review_reflection_intro),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("review_reflection_intro"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
            Text(
                stringResource(R.string.review_reflection_current_range, compactReviewRange(report.period.current)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("review_reflection_current_range"),
            )
            if (report.period.partial) {
                Text(
                    stringResource(R.string.review_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("review_reflection_partial"),
                )
            }
            Text(
                stringResource(R.string.review_reflection_comparison_range, compactReviewRange(report.period.previous)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("review_reflection_comparison_range"),
            )
        }

        if (canReadSummary) {
            Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                Text(
                    stringResource(
                        R.string.review_reflection_entry_coverage,
                        current.records,
                        previous.records,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("review_reflection_entry_coverage"),
                )
                Text(
                    stringResource(
                        R.string.review_reflection_mood_coverage,
                        currentMoodSamples,
                        previousMoodSamples,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("review_reflection_mood_coverage"),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
            Text(
                stringResource(R.string.review_reflection_helped),
                style = MaterialTheme.typography.titleSmall,
            )
            LumaTextButton(
                onClick = { onCaptureMemory(today) },
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = LumaUi.MinTouchTargetHeight)
                    .testTag("review_reflection_memory"),
            ) {
                Text(stringResource(R.string.review_reflection_record_memory, todayLabel))
            }
        }

        if (onOpenTasks != null || onOpenHabits != null) {
            Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                Text(
                    stringResource(R.string.review_reflection_change),
                    style = MaterialTheme.typography.titleSmall,
                )
                onOpenTasks?.let { openTasks ->
                    LumaTextButton(
                        onClick = openTasks,
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("review_reflection_tasks"),
                    ) {
                        Text(stringResource(R.string.review_reflection_open_tasks, todayLabel))
                    }
                }
                onOpenHabits?.let { openHabits ->
                    LumaTextButton(
                        onClick = { openHabits(report.period.current.through) },
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("review_reflection_habits"),
                    ) {
                        Text(stringResource(R.string.review_reflection_open_habits, reviewedThroughLabel))
                    }
                }
            }
        }
    }
}
