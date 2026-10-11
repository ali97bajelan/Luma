package com.luma.lifeos.data.mapping

import com.luma.lifeos.data.entity.PlaceEntity
import com.luma.lifeos.domain.model.Place
import java.util.UUID

fun PlaceEntity.toDomainPlace() = Place(UUID.fromString(id), name, note, latitude, longitude)
