package com.luma.lifeos.ui.review

import com.luma.lifeos.ui.theme.primaryFixed
import com.luma.lifeos.ui.theme.onPrimaryFixed

import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.review.ReviewDates
import com.luma.lifeos.domain.review.ReviewPeriod
import com.luma.lifeos.language.localizedDate
import java.time.ZoneOffset
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ReviewHeader(
    period: ReviewPeriod,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onPick: () -> Unit,
) {
    var about by rememberSaveable { mutableStateOf(false) }
    val range = compactReviewRange(period.current)
    val comparison = compactReviewRange(period.previous)
    val format = localizedDate(FormatStyle.MEDIUM)
    val fullRange = stringResource(R.string.memory_review_range,
        period.current.start.format(format), period.current.through.format(format))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.review_weekly_title), style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto),
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                    .semantics { heading() }.testTag("review_weekly_heading"))
            IconButton(onClick = { about = true }, modifier = Modifier.testTag("review_about")) {
                Icon(painterResource(R.drawable.ic_info), stringResource(R.string.review_about),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = period.current.start.year > -999999998,
                    onClick = onPrevious, modifier = Modifier.testTag("review_previous")) {
                    Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.review_previous_week))
                }
                TextButton(onClick = onPick, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .testTag("review_picker").semantics { contentDescription = fullRange }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(range, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        if (period.partial) Surface(shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryFixed,
                            contentColor = MaterialTheme.colorScheme.onPrimaryFixed) {
                            Text(stringResource(R.string.review_in_progress), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).testTag("review_in_progress"))
                        }
                    }
                }
                IconButton(enabled = !period.partial, onClick = onNext, modifier = Modifier.testTag("review_next")) {
                    Icon(painterResource(R.drawable.ic_chevron_forward), stringResource(R.string.review_next_week))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.review_compare_short, comparison), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("review_comparison"))
            if (!period.partial) TextButton(onClick = onThisWeek, modifier = Modifier.heightIn(min = 48.dp).testTag("review_this_week")) {
                Text(stringResource(R.string.review_this_week))
            }
        }
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
        title = { Text(stringResource(R.string.review_about)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.review_saved_explanation))
                Text(stringResource(R.string.review_comparison_range, period.previous.start.format(format),
                    period.previous.through.format(format), stringResource(R.string.review_local_time)))
                if (period.partial) Text(stringResource(R.string.review_partial))
                Text(stringResource(R.string.review_missing_context), modifier = Modifier.testTag("review_missing_context"))
            }
        },
        confirmButton = {
            TextButton(onClick = { about = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("review_about_close")) {
                Text(stringResource(R.string.health_close))
            }
        },
    )
}

@Composable
internal fun compactReviewRange(dates: ReviewDates): String {
    val locale = Locale.getDefault()
    return remember(dates, locale) {
        // ICU collapses repeated months/years while keeping each language's date order.
        // Use Gregorian civil dates, as elsewhere in the review, regardless of locale defaults.
        val formatter = DateIntervalFormat.getInstance("yMMMd",
            ULocale.forLocale(locale).setKeywordValue("calendar", "gregorian"))
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        formatter.format(DateInterval(
            dates.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            dates.through.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        ))
    }
}
