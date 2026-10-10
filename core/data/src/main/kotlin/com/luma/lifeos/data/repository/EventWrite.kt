package com.luma.lifeos.data.repository

import com.luma.lifeos.data.entity.*
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEvent

/**
 * Complete storage snapshot: omitted content and links are removed by save().
 * Catalog entries must exist first. All child event IDs refer to event.id; imported
 * retries remap them to the existing canonical ID within the write transaction.
 * These storage DTOs remain in core:data; feature-domain models arrive in later tasks.
 */
data class EventWrite(
    val event: LifeEvent,
    val mood: MoodEntryEntity? = null,
    val emotionIds: Set<String> = emptySet(),
    val journal: JournalEntryEntity? = null,
    val memory: MemoryEntity? = null,
    val personIds: Set<String> = emptySet(),
    val placeId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val attachments: List<MediaAttachmentEntity> = emptyList(),
    val activities: List<ActivityRecordEntity> = emptyList(),
    val appUsage: AppUsageRecordEntity? = null,
    val metadataJson: String = "{}",
)

/** Paths become eligible for physical cleanup only after this transaction has committed. */
data class EventWriteResult(val eventId: EventId, val unreferencedMediaPaths: List<String>)
