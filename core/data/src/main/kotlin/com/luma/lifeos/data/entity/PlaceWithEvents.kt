package com.luma.lifeos.data.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class PlaceWithEvents(
    @Embedded val place: PlaceEntity,
    @Relation(parentColumn = "id", entityColumn = "id", entity = LifeEventEntity::class,
        associateBy = Junction(EventPlaceEntity::class, parentColumn = "place_id", entityColumn = "event_id"))
    val events: List<EventWithDetails>,
)
