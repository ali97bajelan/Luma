package com.luma.lifeos.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.domain.repository.LifeEventRepository
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TodayRoute(
    repository: LifeEventRepository,
    date: LocalDate,
    zone: ZoneId,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    // Recreate collection state so old-day rows cannot appear under the new date header.
    key(repository, date, zone, retry) {
        val observation = remember { observeToday(repository, date, zone) }
        val state by observation.collectAsStateWithLifecycle(initialValue = TodayUiState.Loading)
        TodayScreen(date, zone, state, onSelectDate, onToday, onRetry = { retry++ })
    }
}
