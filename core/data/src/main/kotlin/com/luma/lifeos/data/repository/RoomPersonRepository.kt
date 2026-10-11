package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.PersonEntity
import com.luma.lifeos.data.mapping.toDomainPerson
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PersonRepository
import java.util.UUID
import kotlinx.coroutines.flow.map

class RoomPersonRepository(private val database: LumaDatabase) : PersonRepository {
    private val catalog = database.catalog()
    override fun observePeople() = catalog.observePeople().map { rows -> rows.map { it.toDomainPerson() } }
    override fun observeHistory(id: UUID) = catalog.observePersonHistory(id.toString()).map { graph ->
        graph?.let { PersonHistory(it.person.toDomainPerson(), it.events.map { event -> event.toRecord() }
            .sortedWith { first, second -> LifeEvent.chronologicalOrder.compare(first.event, second.event) }) }
    }
    override suspend fun get(id: UUID): Person? = catalog.person(id.toString())?.toDomainPerson()
    override suspend fun create(id: UUID, input: PersonInput): UUID = database.withTransaction {
        val previous = catalog.person(id.toString())
        val name = input.name.trim()
        val note = input.note.clean()
        if (previous != null) {
            require(previous.name == name && previous.note == note) { "This capture ID belongs to another person" }
            return@withTransaction id
        }
        catalog.upsertPerson(PersonEntity(id.toString(), name, note))
        database.derived().invalidateSummaries()
        id
    }
    override suspend fun update(id: UUID, input: PersonInput) = database.withTransaction {
        val previous = requireNotNull(catalog.person(id.toString())) { "Person was deleted" }
        catalog.upsertPerson(previous.copy(name = input.name.trim(), note = input.note.clean()))
        database.derived().invalidateSummaries()
    }
    override suspend fun delete(id: UUID) = database.withTransaction {
        catalog.deletePerson(id.toString())
        database.derived().invalidateSummaries()
    }
    private fun String?.clean() = this?.trim()?.takeIf(String::isNotEmpty)
}
