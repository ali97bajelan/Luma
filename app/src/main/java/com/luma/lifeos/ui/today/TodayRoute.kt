package com.luma.lifeos.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.domain.repository.LifeEventRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.R
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TodayRoute(
    repository: LifeEventRepository,
    date: LocalDate,
    zone: ZoneId,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onMoodClick: (EventId) -> Unit = {},
    onJournalClick: (EventId) -> Unit = {},
    @StringRes heading: Int = R.string.today,
    screenTag: String = "screen_today",
) {
    var retry by remember { mutableIntStateOf(0) }
    // Recreate collection state so old-day rows cannot appear under the new date header.
    key(repository, date, zone, retry) {
        val observation = remember { observeToday(repository, date, zone) }
        val state by observation.collectAsStateWithLifecycle(initialValue = TodayUiState.Loading)
        TodayScreen(date, zone, state, onSelectDate, onToday, onRetry = { retry++ },
            onMoodClick = onMoodClick, onJournalClick = onJournalClick, heading = heading, screenTag = screenTag)
    }
}
