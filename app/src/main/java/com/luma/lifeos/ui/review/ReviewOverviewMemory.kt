package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewMetricKind
import com.luma.lifeos.domain.review.WeeklyReview
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaSectionHeader
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaSurfaceTone
import com.luma.lifeos.ui.timeline.label
import java.time.format.FormatStyle

@Composable
internal fun ReviewOverviewMemory(
    report: WeeklyReview,
    repository: WeeklyReviewRepository,
    onEvent: (LifeEventRecord) -> Unit,
) {
    val hasSavedMemory = report.current.memories > 0
    LumaSectionSurface(
        modifier = Modifier.testTag("review_overview_memory"),
        tone = if (hasSavedMemory) LumaSurfaceTone.Reflective else LumaSurfaceTone.Neutral,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painterResource(R.drawable.ic_bookmark_outline),
                contentDescription = null,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LumaSectionHeader(title = stringResource(R.string.review_latest_memory))
                LatestMemoryContent(report, repository, onEvent)
            }
        }
    }
}

@Composable
private fun LatestMemoryContent(
    report: WeeklyReview,
    repository: WeeklyReviewRepository,
    onEvent: (LifeEventRecord) -> Unit,
) {
    val displayTime = rememberDisplayTimePolicy()
    if (report.current.memories == 0) {
        Text(
            stringResource(R.string.review_memory_empty_short),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("review_overview_memory_empty"),
        )
        return
    }

    var retry by remember { mutableIntStateOf(0) }
    val flow = remember(repository, report.period, report.current.memories, retry) {
        observeReviewSources(
            repository,
            report.period.current,
            report.period.zone,
            ReviewMetric(ReviewMetricKind.MEMORIES),
            maxOf(0, report.current.memories - 1),
            1,
        )
    }
    val state by flow.collectAsStateWithLifecycle(ReviewSourcesState.Loading)
    when (val current = state) {
        ReviewSourcesState.Loading -> CircularProgressIndicator(Modifier.testTag("review_overview_memory_loading"))
        ReviewSourcesState.Error -> {
            AccessibleFeedback(stringResource(R.string.review_error))
            TextButton(
                onClick = { retry++ },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("review_overview_memory_retry"),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
        is ReviewSourcesState.Data -> {
            val row = current.page.records.lastOrNull()
            if (row == null) {
                Text(
                    stringResource(R.string.review_memory_empty_short),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("review_overview_memory_empty"),
                )
            } else {
                val date = row.event.occurredAt.atZone(report.period.zone)
                    .format(displayTime.dateTime(FormatStyle.MEDIUM))
                TextButton(
                    onClick = { onEvent(row) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                        .testTag("review_overview_memory_open"),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.event.title?.takeIf { it.isNotBlank() } ?: stringResource(row.event.type.label()))
                        Text(
                            date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(painterResource(R.drawable.ic_chevron_forward), contentDescription = null)
                }
            }
        }
    }
}
