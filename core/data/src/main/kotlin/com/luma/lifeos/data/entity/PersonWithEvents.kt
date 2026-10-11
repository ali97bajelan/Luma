package com.luma.lifeos.data.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class PersonWithEvents(
    @Embedded val person: PersonEntity,
    @Relation(parentColumn = "id", entityColumn = "id", entity = LifeEventEntity::class,
        associateBy = Junction(EventPersonEntity::class, parentColumn = "person_id", entityColumn = "event_id"))
    val events: List<EventWithDetails>,
)
