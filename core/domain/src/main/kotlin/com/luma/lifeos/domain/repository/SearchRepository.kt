package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.search.SearchOptions
import com.luma.lifeos.domain.search.SearchPage
import com.luma.lifeos.domain.search.SearchQuery
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    /** Initial emission confirms observation registration, later emissions invalidate pages. */
    fun observeChanges(): Flow<Unit>
    fun observeOptions(): Flow<SearchOptions>
    suspend fun page(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int): SearchPage
}
