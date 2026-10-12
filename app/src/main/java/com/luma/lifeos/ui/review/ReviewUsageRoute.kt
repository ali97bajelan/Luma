package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.ui.usage.UsageDayState
import com.luma.lifeos.ui.usage.observeUsageDay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Read-only saved evidence. Opening this route does not request access or import native data. */
@Composable
fun ReviewUsageRoute(integration: UsageIntegration, date: LocalDate, zone: ZoneId, onBack: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    key(date, zone, retry) {
        val observation = remember(integration) { observeUsageDay(integration, date, zone) }
        val state by observation.collectAsStateWithLifecycle(UsageDayState.Loading)
        val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        LazyColumn(Modifier.fillMaxSize().testTag("screen_review_usage"), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.review_saved_usage), style = MaterialTheme.typography.headlineMedium)
                Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                Text(zone.id)
                Text(stringResource(R.string.review_usage_explanation))
            }
            when (val current = state) {
                UsageDayState.Loading -> item { CircularProgressIndicator() }
                UsageDayState.Error -> item {
                    Text(stringResource(R.string.review_error))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
                UsageDayState.Empty -> item { Text(stringResource(R.string.usage_daily_empty)) }
                is UsageDayState.Data -> {
                    item {
                        Text(stringResource(when (current.report.history) {
                            UsageHistory.COMPLETE -> R.string.usage_daily_explanation
                            UsageHistory.PARTIAL -> R.string.usage_history_partial
                            UsageHistory.UNAVAILABLE -> R.string.usage_legacy_totals
                        }))
                        Text(reviewDuration(current.report.totalMillis.takeUnless {
                            current.report.apps.isEmpty() && current.report.history == UsageHistory.PARTIAL
                        }))
                    }
                    items(current.report.apps, key = { "app:${it.packageName}" }) { app ->
                        Column {
                            Text(app.appLabel ?: app.packageName)
                            if (app.appLabel != null) Text(app.packageName)
                            Text(reviewDuration(app.durationMillis))
                        }
                    }
                    items(current.report.sessions, key = { it.identity }) { session ->
                        Card(Modifier.fillMaxWidth().testTag("review_usage_session_${session.identity}")) {
                            Column(Modifier.padding(16.dp)) {
                                Text(session.appLabel ?: session.packageName)
                                Text(stringResource(R.string.usage_session_times, session.start.atZone(zone).format(format), session.end.atZone(zone).format(format)))
                                Text(reviewDuration(session.durationMillis))
                                if (session.openedAt < session.start) Text(stringResource(R.string.usage_session_continued, session.openedAt.atZone(zone).format(format)))
                                if (session.ongoing) Text(stringResource(R.string.usage_session_ongoing))
                            }
                        }
                    }
                }
            }
        }
    }
}
