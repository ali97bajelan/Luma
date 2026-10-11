package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.Person
import com.luma.lifeos.domain.model.PersonInput
import com.luma.lifeos.domain.model.PersonHistory
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    fun observePeople(): Flow<List<Person>>
    /** Person and linked event graphs are read as one committed snapshot; missing person emits null. */
    fun observeHistory(id: UUID): Flow<PersonHistory?>
    suspend fun get(id: UUID): Person?
    /** Retained IDs make identical create retries idempotent; equal names may have distinct identities. */
    suspend fun create(id: UUID, input: PersonInput): UUID
    suspend fun update(id: UUID, input: PersonInput)
    /** Removes only catalog row/links. Missing IDs are successful retries. */
    suspend fun delete(id: UUID)
}
