package com.luma.lifeos.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.repository.LifeEventRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Retains the last successful Today feed while a navigation destination is recreated. */
internal class TodayFeedViewModel : ViewModel() {
    private data class FeedKey(
        val repository: LifeEventRepository,
        val date: LocalDate,
        val zone: ZoneId,
        val authorizationEpoch: Pair<Long, Long>,
    )

    private val retainedFeeds = LinkedHashMap<FeedKey, TodayUiState>(8, 0.75f, true)
    private var activeAuthorizationEpoch: Pair<Long, Long>? = null

    @Synchronized
    fun observe(
        repository: LifeEventRepository,
        date: LocalDate,
        zone: ZoneId,
        authorizationEpoch: Pair<Long, Long>,
    ): StateFlow<TodayUiState> {
        if (activeAuthorizationEpoch != authorizationEpoch) {
            retainedFeeds.clear()
            activeAuthorizationEpoch = authorizationEpoch
        }
        val key = FeedKey(repository, date, zone, authorizationEpoch)
        val initial = retainedFeeds[key] ?: TodayUiState.Loading
        return observeToday(repository, date, zone)
            .onEach { state ->
                when (state) {
                    is TodayUiState.Data, TodayUiState.Empty -> retain(key, state)
                    else -> Unit
                }
            }
            .map { state ->
                if (state == TodayUiState.Loading) retained(key) ?: state else state
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
    }

    @Synchronized
    private fun retained(key: FeedKey): TodayUiState? =
        retainedFeeds[key].takeIf { key.authorizationEpoch == activeAuthorizationEpoch }

    @Synchronized
    private fun retain(key: FeedKey, state: TodayUiState) {
        if (key.authorizationEpoch != activeAuthorizationEpoch) return
        retainedFeeds[key] = state
        while (retainedFeeds.size > MAX_RETAINED_FEEDS) {
            val oldest = retainedFeeds.keys.firstOrNull() ?: break
            retainedFeeds.remove(oldest)
        }
    }

    private companion object {
        const val MAX_RETAINED_FEEDS = 8
    }
}
