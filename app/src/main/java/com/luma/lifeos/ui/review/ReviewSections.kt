package com.luma.lifeos.ui.review

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.review.ReviewPeriod
import com.luma.lifeos.language.localizedDate
import java.time.format.FormatStyle

internal data class ReviewSectionDestination(val key: String, @StringRes val title: Int)

internal fun reviewSections(includeHabits: Boolean): List<ReviewSectionDestination> = buildList {
    add(ReviewSectionDestination("overview", R.string.review_section_overview))
    add(ReviewSectionDestination("counts", R.string.review_entry_counts))
    add(ReviewSectionDestination("mood", R.string.review_mood))
    add(ReviewSectionDestination("memories", R.string.review_memories))
    add(ReviewSectionDestination("catalog", R.string.review_people_places))
    add(ReviewSectionDestination("usage", R.string.review_usage))
    add(ReviewSectionDestination("activity", R.string.review_activity))
    if (includeHabits) add(ReviewSectionDestination("habits", R.string.review_habits))
}

@Composable
internal fun ReviewSectionNavigation(
    selected: String?,
    period: ReviewPeriod,
    onSection: (String) -> Unit,
    includeHabits: Boolean = false,
) {
    var showSections by rememberSaveable { mutableStateOf(false) }
    val sections = reviewSections(includeHabits)
    val shortcuts = sections.drop(2)
    val requesters = remember(shortcuts) {
        shortcuts.associate { it.key to BringIntoViewRequester() }
    }
    val range = compactReviewRange(period.current)
    val fullRange = stringResource(
        R.string.memory_review_range,
        period.current.start.format(localizedDate(FormatStyle.MEDIUM)),
        period.current.through.format(localizedDate(FormatStyle.MEDIUM)),
    )
    val inProgress = stringResource(R.string.review_in_progress)
    val stickyDescription = if (period.partial) {
        stringResource(R.string.review_sticky_week_accessibility, fullRange, inProgress)
    } else fullRange

    LaunchedEffect(selected, requesters) {
        selected?.let { requesters[it]?.bringIntoView() }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = stickyDescription
            }.testTag("review_sticky_week"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(range, style = MaterialTheme.typography.titleSmall)
                if (period.partial) {
                    Text(inProgress, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(
                onClick = { onSection("overview") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("review_overview_action"),
            ) {
                Text(stringResource(R.string.review_overview_action))
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { showSections = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("review_sections_open"),
            ) {
                Text(stringResource(R.string.review_sections))
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shortcuts.forEach { destination ->
                    FilterChip(
                        selected = selected == destination.key,
                        onClick = { onSection(destination.key) },
                        label = { Text(stringResource(destination.title)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .bringIntoViewRequester(requesters.getValue(destination.key))
                            .testTag("review_section_${destination.key}"),
                    )
                }
            }
        }
    }

    if (showSections) {
        AlertDialog(
            onDismissRequest = { showSections = false },
            title = { Text(stringResource(R.string.review_sections)) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sections.forEach { destination ->
                        TextButton(
                            onClick = {
                                showSections = false
                                onSection(destination.key)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("review_section_destination_${destination.key}"),
                        ) {
                            Text(stringResource(destination.title), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSections = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.health_close))
                }
            },
        )
    }
}
