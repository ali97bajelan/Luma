package com.luma.lifeos.sharing

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.TimelineRepository
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class EntryShareKind { MEMORY, MOOD }
data class EntryShareRequest(val eventId: EventId, val kind: EntryShareKind)
data class EntryShareOptions(
    val includeDate: Boolean = true, val includeNote: Boolean = true,
    val includePeople: Boolean = false, val includePlace: Boolean = false,
    val includeEnergy: Boolean = false, val includeStress: Boolean = false,
    val includeSourceContent: Boolean = false,
)
data class EntryShareSnapshot(val request: EntryShareRequest, val record: LifeEventRecord, val zone: ZoneId)
data class EntryShareTextContext(
    val locale: Locale, val memoryHeading: String, val feelingHeading: (String) -> String,
    val emotionHeading: String, val noteHeading: String, val sourceHeading: String,
    val peopleHeading: String, val placeHeading: String, val energyHeading: String,
    val stressHeading: String, val scoreLabels: Map<Int, String>,
    val formatDate: (Instant, ZoneId) -> String,
    val emotionName: (com.luma.lifeos.domain.model.Emotion) -> String = { it.name },
    val formatMoodDateTime: (Instant, ZoneId) -> String = formatDate,
)
enum class EntryShareTextError { EMPTY, TOO_LARGE }

suspend fun loadEntryShareSnapshot(repository: TimelineRepository, request: EntryShareRequest, zone: ZoneId): EntryShareSnapshot? {
    val record = repository.get(request.eventId) ?: return null
    val eligible = when (request.kind) {
        EntryShareKind.MOOD -> record.event.type == EventType.MOOD && record.mood != null
        EntryShareKind.MEMORY -> record.memory != null && when (record.event.type) {
            EventType.MEMORY -> true
            EventType.MOOD -> record.mood != null
            EventType.JOURNAL -> record.journal != null
            else -> false
        }
    }
    if (!eligible) return null
    return EntryShareSnapshot(request, record.copy(
        mood = record.mood?.let { it.copy(emotions = it.emotions.toList()) },
        journal = record.journal?.let { it.copy(tags = it.tags.toList()) },
        people = record.people.toList(), photos = record.photos.toList(), activities = record.activities.toList(),
    ), zone)
}
