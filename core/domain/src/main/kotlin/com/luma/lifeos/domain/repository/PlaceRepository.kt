package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.Place
import com.luma.lifeos.domain.model.PlaceInput
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun observePlaces(): Flow<List<Place>>
    /** Missing or deleted place emits null. */
    fun observePlace(id: UUID): Flow<Place?>
    suspend fun get(id: UUID): Place?
    /** Equal names can have distinct IDs; identical retained-ID creates are idempotent. */
    suspend fun create(id: UUID, input: PlaceInput): UUID
    /** Changes name/note while retaining existing coordinates. Missing IDs reject updates. */
    suspend fun update(id: UUID, input: PlaceInput)
    /** Removes the catalog row and links, retaining all events. Missing IDs are successful retries. */
    suspend fun delete(id: UUID)
}
