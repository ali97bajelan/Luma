package com.luma.lifeos.data.mapping

import com.luma.lifeos.data.entity.PersonEntity
import com.luma.lifeos.domain.model.Person
import java.util.UUID

fun PersonEntity.toDomainPerson() = Person(UUID.fromString(id), name, note, photoUri)
