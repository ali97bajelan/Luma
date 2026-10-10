package com.luma.lifeos.data.db

import androidx.room.TypeConverter
import com.luma.lifeos.data.entity.IntegrationStatus
import com.luma.lifeos.data.entity.SummaryFreshness
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType

/** Persist enum names, never ordinals; renamed values require an explicit migration. */
class StorageConverters {
    @TypeConverter fun eventType(value: String): EventType = EventType.valueOf(value)
    @TypeConverter fun eventType(value: EventType): String = value.name
    @TypeConverter fun eventSource(value: String): EventSource = EventSource.valueOf(value)
    @TypeConverter fun eventSource(value: EventSource): String = value.name
    @TypeConverter fun freshness(value: String): SummaryFreshness = SummaryFreshness.valueOf(value)
    @TypeConverter fun freshness(value: SummaryFreshness): String = value.name
    @TypeConverter fun integrationStatus(value: String): IntegrationStatus = IntegrationStatus.valueOf(value)
    @TypeConverter fun integrationStatus(value: IntegrationStatus): String = value.name
}
