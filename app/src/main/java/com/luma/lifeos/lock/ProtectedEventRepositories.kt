package com.luma.lifeos.lock

import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.habit.TimelineFeedPage
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MemoryInput
import com.luma.lifeos.domain.model.MemoryMarkerInput
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.model.SourceIdentity
import com.luma.lifeos.domain.repository.JournalRepository
import com.luma.lifeos.domain.repository.LifeEventRepository
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.repository.MoodRepository
import com.luma.lifeos.domain.repository.SearchRepository
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.search.SearchOptions
import com.luma.lifeos.domain.search.SearchPage
import com.luma.lifeos.domain.search.SearchQuery
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Event/detail/search projections pass through record classification before reaching UI state. */
class ProtectedTimelineRepository(
    private val delegate: TimelineRepository,
    private val gateway: ProtectedContentGateway,
) : TimelineRepository by delegate {
    override suspend fun get(id: EventId): LifeEventRecord? = gateway.readRecord { delegate.get(id) }?.record

    override suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean) =
        scanPage(offset, limit) { nextOffset, nextLimit ->
            delegate.page(range, nextOffset, nextLimit, showAppUsage)
        }

    override suspend fun feed(date: LocalDate, zone: ZoneId, offset: Int, limit: Int, showAppUsage: Boolean): TimelineFeedPage {
        require(offset >= 0 && limit in 1..300)
        val before = gateway.snapshot.value
        if (before.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return emptyFeed(offset)
        val rows = mutableListOf<TimelineFeedItem>()
        var nextOffset = 0
        var rawTotal = Int.MAX_VALUE
        while (nextOffset < rawTotal) {
            val page = delegate.feed(date, zone, nextOffset, SCAN_SIZE, showAppUsage)
            rawTotal = page.totalCount
            if (page.items.isEmpty()) break
            rows += page.items
            nextOffset += page.items.size
        }
        if (!gateway.sameAuthorizationEpoch(before, gateway.snapshot.value)) return emptyFeed(offset)
        val projected = rows.mapNotNull { item ->
            when (item) {
                is TimelineFeedItem.Entry -> gateway.projectRecord(item.record, before)?.let { TimelineFeedItem.Entry(it.record) }
                is TimelineFeedItem.Habit -> item.takeIf {
                    gateway.filterSection(true, LockSection.HABITS, before)
                }
            }
        }
        return TimelineFeedPage(
            items = immutableProjectionList(projected.drop(offset).take(limit)),
            offset = offset,
            totalCount = projected.size,
            eventCount = projected.count { it is TimelineFeedItem.Entry },
            habitLogCount = projected.count { it is TimelineFeedItem.Habit },
        )
    }

    private suspend fun scanPage(offset: Int, limit: Int, load: suspend (Int, Int) -> com.luma.lifeos.domain.model.TimelinePage):
        com.luma.lifeos.domain.model.TimelinePage {
        require(offset >= 0 && limit in 1..300)
        val before = gateway.snapshot.value
        if (before.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) {
            return com.luma.lifeos.domain.model.TimelinePage(emptyList(), offset, 0, 0)
        }
        val records = mutableListOf<LifeEventRecord>()
        var rawTotal = Int.MAX_VALUE
        var nextOffset = 0
        while (nextOffset < rawTotal) {
            val page = load(nextOffset, SCAN_SIZE)
            rawTotal = page.totalCount
            if (page.records.isEmpty()) break
            records += page.records
            nextOffset += page.records.size
        }
        if (!gateway.sameAuthorizationEpoch(before, gateway.snapshot.value)) {
            return com.luma.lifeos.domain.model.TimelinePage(emptyList(), offset, 0, 0)
        }
        val safe = gateway.projectRecords(records, before)
        return com.luma.lifeos.domain.model.TimelinePage(
            records = immutableProjectionList(safe.drop(offset).take(limit)),
            offset = offset,
            totalCount = safe.size,
            eventCount = safe.count { it.event.type != EventType.APP_USAGE },
        )
    }

    private fun emptyFeed(offset: Int) = TimelineFeedPage(emptyList(), offset, 0, 0, 0)

    private companion object { const val SCAN_SIZE = 300 }
}

class ProtectedSearchRepository(
    private val delegate: SearchRepository,
    private val gateway: ProtectedContentGateway,
) : SearchRepository by delegate {
    override fun observeOptions(): Flow<SearchOptions> = combine(delegate.observeOptions(), gateway.snapshot) { options, state ->
        options.copy(
            tags = immutableProjectionList(
                options.tags.takeIf { gateway.filterSection(true, LockSection.JOURNAL, state) }.orEmpty(),
            ),
            people = immutableProjectionList(
                options.people.takeIf { gateway.filterSection(true, LockSection.PEOPLE, state) }.orEmpty(),
            ),
            places = immutableProjectionList(options.places),
        )
    }

    override suspend fun page(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int): SearchPage {
        require(offset >= 0 && limit > 0)
        val before = gateway.snapshot.value
        if (before.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return SearchPage(emptyList(), offset, 0)
        val raw = mutableListOf<LifeEventRecord>()
        var rawTotal = Int.MAX_VALUE
        var nextOffset = 0
        while (nextOffset < rawTotal) {
            val page = delegate.page(query, zone, nextOffset, SCAN_SIZE)
            rawTotal = page.totalCount
            if (page.records.isEmpty()) break
            raw += page.records
            nextOffset += page.records.size
        }
        if (!gateway.sameAuthorizationEpoch(before, gateway.snapshot.value)) return SearchPage(emptyList(), offset, 0)
        val safe = gateway.projectRecords(raw, before)
        return SearchPage(immutableProjectionList(safe.drop(offset).take(limit)), offset, safe.size)
    }

    private companion object { const val SCAN_SIZE = 100 }
}

class ProtectedLifeEventRepository(
    private val delegate: LifeEventRepository,
    private val timeline: TimelineRepository,
    private val gateway: ProtectedContentGateway,
) : LifeEventRepository by delegate {
    override fun observeRecords(range: TimeRange): Flow<List<LifeEventRecord>> = gateway.records(delegate.observeRecords(range))

    override fun observe(range: TimeRange): Flow<List<LifeEvent>> =
        observeRecords(range).map { records -> immutableProjectionList(records.map(LifeEventRecord::event)) }

    override suspend fun get(id: EventId): LifeEvent? =
        gateway.readRecord { timeline.get(id) }?.record?.event

    override suspend fun findBySource(identity: SourceIdentity): LifeEvent? {
        val event = delegate.findBySource(identity) ?: return null
        return gateway.readRecord { timeline.get(event.id) }?.record?.event
    }

    override suspend fun upsert(event: LifeEvent): EventId {
        val existing = gateway.requiredSections(event.id)
        val operation = if (existing == null) LockOperation.CREATE else LockOperation.UPDATE
        val sections = existing.orEmpty() + event.requiredSection()
        return gateway.commit(operation, sections, event.id.toString()) { delegate.upsert(event) }
    }

    override suspend fun delete(id: EventId) {
        val sections = gateway.requiredSections(id, includePeopleFields = true).orEmpty()
        gateway.commit(LockOperation.DELETE, sections, id.toString()) { delegate.delete(id) }
    }

    private fun LifeEvent.requiredSection(): Set<LockSection> = setOf(when (type) {
        EventType.MEMORY -> LockSection.MEMORIES
        EventType.JOURNAL -> LockSection.JOURNAL
        EventType.MOOD -> LockSection.MOOD
        EventType.ACTIVITY, EventType.APP_USAGE -> LockSection.HEALTH_USAGE
    })
}

class ProtectedMemoryRepository(
    private val delegate: MemoryRepository,
    private val gateway: ProtectedContentGateway,
) : MemoryRepository by delegate {
    override suspend fun get(id: EventId): LifeEventRecord? = gateway.readRecord { delegate.get(id) }?.record
    override fun observe(range: TimeRange): Flow<List<LifeEventRecord>> = gateway.records(delegate.observe(range))
    override suspend fun create(id: EventId, input: MemoryInput): EventId =
        gateway.commit(LockOperation.CREATE, setOf(LockSection.MEMORIES), id.toString()) { delegate.create(id, input) }
    override suspend fun update(id: EventId, input: MemoryInput) =
        gateway.commit(
            LockOperation.UPDATE,
            requiredExisting(id) + if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
            id.toString(),
        ) { delegate.update(id, input) }
    override suspend fun delete(id: EventId) {
        gateway.commit(LockOperation.DELETE, gateway.requiredSections(id, includePeopleFields = true).orEmpty(), id.toString()) {
            delegate.delete(id)
        }
    }
    override suspend fun saveMarker(id: EventId, input: MemoryMarkerInput) =
        gateway.commit(
            LockOperation.UPDATE,
            requiredExisting(id) + setOf(LockSection.MEMORIES) +
                if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
            id.toString(),
        ) { delegate.saveMarker(id, input) }
    override suspend fun removeMarker(id: EventId) =
        gateway.commit(
            LockOperation.UPDATE,
            requiredExisting(id) + setOf(LockSection.MEMORIES),
            id.toString(),
        ) { delegate.removeMarker(id) }

    private suspend fun requiredExisting(id: EventId): Set<LockSection> =
        gateway.requiredSections(id) ?: throw unclassifiedExistingRecord()
}

class ProtectedMoodRepository(
    private val delegate: MoodRepository,
    private val gateway: ProtectedContentGateway,
) : MoodRepository by delegate {
    override fun observeEmotions() = combine(delegate.observeEmotions(), gateway.snapshot) { emotions, state ->
        immutableProjectionList(emotions.takeIf { gateway.filterSection(true, LockSection.MOOD, state) }.orEmpty())
    }
    override suspend fun get(id: EventId): LifeEventRecord? = gateway.readRecord { delegate.get(id) }?.record
    override suspend fun create(id: EventId, input: MoodInput): EventId =
        gateway.commit(LockOperation.CREATE, setOf(LockSection.MOOD), id.toString()) { delegate.create(id, input) }
    override suspend fun update(id: EventId, input: MoodInput) =
        gateway.commit(
            LockOperation.UPDATE,
            requiredExisting(id) + if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
            id.toString(),
        ) { delegate.update(id, input) }
    override suspend fun delete(id: EventId) {
        gateway.commit(LockOperation.DELETE, gateway.requiredSections(id, includePeopleFields = true).orEmpty(), id.toString()) {
            delegate.delete(id)
        }
    }

    private suspend fun requiredExisting(id: EventId): Set<LockSection> =
        gateway.requiredSections(id) ?: throw unclassifiedExistingRecord()
}

class ProtectedJournalRepository(
    private val delegate: JournalRepository,
    private val gateway: ProtectedContentGateway,
) : JournalRepository by delegate {
    override fun observeTags() = combine(delegate.observeTags(), gateway.snapshot) { tags, state ->
        immutableProjectionList(tags.takeIf { gateway.filterSection(true, LockSection.JOURNAL, state) }.orEmpty())
    }
    override suspend fun get(id: EventId): LifeEventRecord? = gateway.readRecord { delegate.get(id) }?.record
    override suspend fun create(id: EventId, input: JournalInput): EventId =
        gateway.commit(LockOperation.CREATE, setOf(LockSection.JOURNAL), id.toString()) { delegate.create(id, input) }
    override suspend fun update(id: EventId, input: JournalInput) =
        gateway.commit(
            LockOperation.UPDATE,
            requiredExisting(id) + if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
            id.toString(),
        ) { delegate.update(id, input) }
    override suspend fun delete(id: EventId) {
        gateway.commit(LockOperation.DELETE, gateway.requiredSections(id, includePeopleFields = true).orEmpty(), id.toString()) {
            delegate.delete(id)
        }
    }

    private suspend fun requiredExisting(id: EventId): Set<LockSection> =
        gateway.requiredSections(id) ?: throw unclassifiedExistingRecord()
}

internal fun unclassifiedExistingRecord() = LockActionDeniedException(
    com.luma.lifeos.domain.lock.AccessDecision.Deny(
        com.luma.lifeos.domain.lock.AccessDenial.UNCLASSIFIED_CONTENT,
    ),
)
