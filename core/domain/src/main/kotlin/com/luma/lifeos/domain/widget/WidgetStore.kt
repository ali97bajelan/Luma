package com.luma.lifeos.domain.widget

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface WidgetStore {
    /** Maximum number of ordered row candidates a widget snapshot may fetch, including hidden rows. */
    companion object {
        const val MAX_CANDIDATE_ROWS = 8
    }

    suspend fun ensureConfigured(instance: WidgetInstance)
    suspend fun configuration(widgetId: Int): WidgetInstance? = null
    suspend fun configure(instance: WidgetInstance)
    /** Fetches an ordered prefix; rowLimit must be in 0..MAX_CANDIDATE_ROWS. */
    suspend fun snapshot(widgetId: Int, now: Instant, deviceZone: ZoneId, rowLimit: Int): WidgetSnapshot?
    suspend fun execute(widgetId: Int, token: UUID, now: Instant, deviceZone: ZoneId): WidgetActionResult
    suspend fun dismissFeedback(widgetId: Int)
    suspend fun remove(widgetId: Int)
    suspend fun remap(oldIds: List<Int>, newInstances: List<WidgetInstance>)
    fun observeInvalidations(): Flow<Unit>
}
