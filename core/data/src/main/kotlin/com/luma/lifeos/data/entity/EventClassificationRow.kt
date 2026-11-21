package com.luma.lifeos.data.entity

import androidx.room.ColumnInfo
import com.luma.lifeos.domain.model.EventType

/** Minimal database projection used for route and mutation authorization. */
data class EventClassificationRow(
    val type: EventType,
    @ColumnInfo(name = "has_memory_marker") val hasMemoryMarker: Boolean,
    @ColumnInfo(name = "has_people") val hasPeople: Boolean,
)
