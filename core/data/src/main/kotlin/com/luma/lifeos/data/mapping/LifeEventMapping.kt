package com.luma.lifeos.data.mapping

import com.luma.lifeos.data.entity.LifeEventEntity
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEvent
import java.time.Instant

fun LifeEventEntity.toDomain(): LifeEvent = LifeEvent(
    id = EventId.parse(id), type = type,
    occurredAt = Instant.ofEpochMilli(occurredAt), createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt), endedAt = endedAt?.let(Instant::ofEpochMilli),
    source = source, sourceReference = sourceReference, title = title, note = note,
)

fun LifeEvent.toEntity(metadataJson: String = "{}"): LifeEventEntity = LifeEventEntity(
    id = id.toString(), type = type,
    occurredAt = occurredAt.toEpochMilli(), createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(), endedAt = endedAt?.toEpochMilli(),
    source = source, sourceReference = sourceReference, title = title, note = note,
    metadataJson = metadataJson,
)

/** Millisecond rows satisfy >= start / < end when both query bounds round upward. */
internal fun Instant.storageBoundary(): Long {
    val millis = toEpochMilli()
    return if (nano % 1_000_000 == 0) millis else Math.addExact(millis, 1)
}
