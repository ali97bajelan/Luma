package com.luma.lifeos.data.repository

import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EventPlaceEntity
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.PlaceSelection

/** Caller owns the event transaction; failed references roll back the entire capture. */
internal suspend fun replacePlaceInTransaction(database: LumaDatabase, eventId: EventId, selection: PlaceSelection?) {
    if (selection == null) return
    database.content().deletePlace(eventId.toString())
    selection.id?.let { database.content().insertPlace(EventPlaceEntity(eventId.toString(), it.toString())) }
}
