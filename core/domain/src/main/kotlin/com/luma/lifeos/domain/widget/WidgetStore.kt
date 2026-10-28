package com.luma.lifeos.domain.widget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface WidgetStore {
    /** Maximum number of ordered row candidates a widget snapshot may fetch, including hidden rows. */
    companion object {
        const val MAX_CANDIDATE_ROWS = 8
        const val COLLECTION_PAGE_SIZE = 32
    }

    suspend fun ensureConfigured(instance: WidgetInstance)
    suspend fun configuration(widgetId: Int): WidgetInstance? = null
    suspend fun configure(instance: WidgetInstance)
    /** Fetches an ordered prefix; rowLimit must be in 0..MAX_CANDIDATE_ROWS. */
    suspend fun snapshot(widgetId: Int, now: Instant, deviceZone: ZoneId, rowLimit: Int): WidgetSnapshot?
    /** Fetches one bounded page from the full eligible collection without changing row identity. */
    suspend fun collectionPage(
        widgetId: Int,
        now: Instant,
        deviceZone: ZoneId,
        offset: Int,
        limit: Int,
    ): WidgetSnapshot?
    /** Checks the exact eligible record before a collection navigation fill-in is opened. */
    suspend fun isEligibleCollectionItem(
        widgetId: Int,
        itemId: UUID,
        now: Instant,
        deviceZone: ZoneId,
    ): Boolean
    /** Binds a mutable collection fill-in token to the exact row identity and displayed date. */
    suspend fun isValidCollectionMutation(
        widgetId: Int,
        itemId: UUID,
        token: UUID,
        date: LocalDate,
    ): Boolean
    suspend fun execute(widgetId: Int, token: UUID, now: Instant, deviceZone: ZoneId): WidgetActionResult
    suspend fun dismissFeedback(widgetId: Int, expectedUndoToken: UUID? = null)
    suspend fun remove(widgetId: Int)
    suspend fun remap(oldIds: List<Int>, newInstances: List<WidgetInstance>)
    fun observeInvalidations(): Flow<Unit>
}
