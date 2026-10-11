package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.PlaceEntity
import com.luma.lifeos.data.mapping.toDomainPlace
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PlaceRepository
import java.util.UUID
import kotlinx.coroutines.flow.map

class RoomPlaceRepository(private val database: LumaDatabase) : PlaceRepository {
    private val catalog = database.catalog()
    override fun observePlaces() = catalog.observePlaces().map { rows -> rows.map { it.toDomainPlace() } }
    override fun observePlace(id: UUID) = catalog.observePlace(id.toString()).map { it?.toDomainPlace() }
    override suspend fun get(id: UUID): Place? = catalog.place(id.toString())?.toDomainPlace()
    override suspend fun create(id: UUID, input: PlaceInput): UUID = database.withTransaction {
        val previous = catalog.place(id.toString())
        val name = input.name.trim()
        val note = input.note.clean()
        if (previous != null) {
            require(previous.name == name && previous.note == note) { "This capture ID belongs to another place" }
            return@withTransaction id
        }
        catalog.upsertPlace(PlaceEntity(id.toString(), name, note))
        database.derived().invalidateSummaries()
        id
    }
    override suspend fun update(id: UUID, input: PlaceInput) = database.withTransaction {
        val previous = requireNotNull(catalog.place(id.toString())) { "Place was deleted" }
        catalog.upsertPlace(previous.copy(name = input.name.trim(), note = input.note.clean()))
        database.derived().invalidateSummaries()
    }
    override suspend fun delete(id: UUID) = database.withTransaction {
        catalog.deletePlace(id.toString())
        database.derived().invalidateSummaries()
    }
    private fun String?.clean() = this?.trim()?.takeIf(String::isNotEmpty)
}
