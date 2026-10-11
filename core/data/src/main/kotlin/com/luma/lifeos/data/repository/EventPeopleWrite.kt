package com.luma.lifeos.data.repository

import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EventPersonEntity
import com.luma.lifeos.domain.model.EventId
import java.util.UUID

/** Caller owns the encompassing transaction; foreign-key failures roll back the whole capture. */
internal suspend fun replacePeopleInTransaction(database: LumaDatabase, id: EventId, ids: Set<UUID>?) {
    if (ids == null) return
    database.content().deletePeople(id.toString())
    database.content().insertPeople(ids.map { EventPersonEntity(id.toString(), it.toString()) })
}
