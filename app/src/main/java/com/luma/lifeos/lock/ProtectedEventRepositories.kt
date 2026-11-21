package com.luma.lifeos.lock

import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.habit.TimelineFeedPage
import com.luma.lifeos.domain.model.EditorWriteResult
import com.luma.lifeos.domain.model.EventEditorBaseline
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
import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.LockLoadStatus
import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.data.repository.EventVisibility
import com.luma.lifeos.data.repository.EventVisibilityQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

private fun wholeAppLocked(state: com.luma.lifeos.domain.lock.LockSnapshot): Boolean {
    val policy = state.policy ?: return false
    return policy.enabled && policy.coverage == LockCoverage.WHOLE_APP &&
        state.decide(LockRequest(LockOperation.READ, setOf(LockSection.MEMORIES))) != AccessDecision.Allow
}

private fun memoryVisibility(state: com.luma.lifeos.domain.lock.LockSnapshot): EventVisibility {
    val policy = state.policy ?: return EventVisibility.ALL
    return if (policy.enabled && policy.coverage == LockCoverage.MEMORY_SECTION &&
        state.decide(LockRequest(LockOperation.READ, setOf(LockSection.MEMORIES))) != AccessDecision.Allow
    ) EventVisibility.WITHOUT_MEMORY else EventVisibility.ALL
}

/** Event/detail/search projections pass through record classification before reaching UI state. */
class ProtectedTimelineRepository(
    private val delegate: TimelineRepository,
    private val gateway: ProtectedContentGateway,
    private val visibilityQueries: EventVisibilityQueries? = null,
) : TimelineRepository by delegate {
    override suspend fun get(id: EventId): LifeEventRecord? = gateway.readRecord { delegate.get(id) }?.record

    override suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean): com.luma.lifeos.domain.model.TimelinePage {
        val before = gateway.snapshot.value
        if (before.loadStatus != LockLoadStatus.READY || wholeAppLocked(before)) {
            return com.luma.lifeos.domain.model.TimelinePage(emptyList(), offset, 0, 0)
        }
        val visibility = memoryVisibility(before)
        if (visibilityQueries != null) {
            val result = visibilityQueries.timelinePage(range, offset, limit, showAppUsage, visibility)
            return result.takeIf { gateway.sameAuthorizationEpoch(before, gateway.snapshot.value) }
                ?: com.luma.lifeos.domain.model.TimelinePage(emptyList(), offset, 0, 0)
        }
        return scanPage(offset, limit) { nextOffset, nextLimit ->
            delegate.page(range, nextOffset, nextLimit, showAppUsage)
        }
    }

    override suspend fun feed(date: LocalDate, zone: ZoneId, offset: Int, limit: Int, showAppUsage: Boolean): TimelineFeedPage {
        require(offset >= 0 && limit in 1..300)
        val before = gateway.snapshot.value
        if (before.loadStatus != LockLoadStatus.READY || wholeAppLocked(before)) return emptyFeed(offset)
        if (visibilityQueries != null) {
            val result = visibilityQueries.timelineFeed(
                date, zone, offset, limit, showAppUsage, memoryVisibility(before),
            )
            return result.takeIf { gateway.sameAuthorizationEpoch(before, gateway.snapshot.value) } ?: emptyFeed(offset)
        }
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
    private val visibilityQueries: EventVisibilityQueries? = null,
) : SearchRepository by delegate {
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun observeOptions(): Flow<SearchOptions> = gateway.snapshot
        .distinctUntilChangedBy { state ->
            listOf(state.loadStatus, state.policy, state.sessionGeneration, state.grant)
        }
        .flatMapLatest { before ->
            if (before.loadStatus != LockLoadStatus.READY || wholeAppLocked(before)) {
                return@flatMapLatest flowOf(SearchOptions())
            }
            val visibility = memoryVisibility(before)
            val source = visibilityQueries?.observeSearchOptions(visibility) ?: delegate.observeOptions()
            source.map { options ->
                val after = gateway.snapshot.value
                if (!gateway.sameAuthorizationEpoch(before, after)) SearchOptions()
                else options.copy(
                    tags = immutableProjectionList(
                        options.tags.takeIf { visibility == EventVisibility.ALL }.orEmpty(),
                    ),
                    people = immutableProjectionList(
                        options.people.takeIf { gateway.filterSection(true, LockSection.PEOPLE, after) }.orEmpty(),
                    ),
                    places = immutableProjectionList(options.places),
                )
            }
        }

    override suspend fun page(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int): SearchPage {
        require(offset >= 0 && limit > 0)
        val before = gateway.snapshot.value
        if (before.loadStatus != LockLoadStatus.READY || wholeAppLocked(before)) return SearchPage(emptyList(), offset, 0)
        if (visibilityQueries != null) {
            val result = visibilityQueries.searchPage(query, zone, offset, limit, memoryVisibility(before))
            return result.takeIf { gateway.sameAuthorizationEpoch(before, gateway.snapshot.value) }
                ?: SearchPage(emptyList(), offset, 0)
        }
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

    // RoomSearchRepository accepts pages of at most 50 rows.
    private companion object { const val SCAN_SIZE = 50 }
}

class ProtectedLifeEventRepository(
    private val delegate: LifeEventRepository,
    private val timeline: TimelineRepository,
    private val gateway: ProtectedContentGateway,
    private val visibilityQueries: EventVisibilityQueries? = null,
) : LifeEventRepository by delegate {
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun observeRecords(range: TimeRange): Flow<List<LifeEventRecord>> = gateway.snapshot
        .distinctUntilChangedBy { state ->
            listOf(state.loadStatus, state.policy, state.sessionGeneration, state.grant)
        }
        .flatMapLatest { before ->
            if (before.loadStatus != LockLoadStatus.READY || wholeAppLocked(before)) {
                return@flatMapLatest flowOf(emptyList())
            }
            val source = visibilityQueries?.observeRecords(range, memoryVisibility(before))
                ?: delegate.observeRecords(range)
            source.map { records ->
                val after = gateway.snapshot.value
                if (!gateway.sameAuthorizationEpoch(before, after)) emptyList()
                else gateway.projectRecords(records, after)
            }
        }

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
        if (existing == null) {
            return gateway.commitEditorCreate(
                id = event.id,
                expectedType = event.type,
                newSections = event.requiredSection(),
            ) { delegate.upsert(event) }
        }
        return gateway.commit(LockOperation.UPDATE, existing + event.requiredSection(), event.id.toString()) {
            delegate.upsert(event)
        }
    }

    override suspend fun delete(id: EventId) {
        val sections = gateway.requiredSections(id, includePeopleFields = true).orEmpty()
        gateway.commit(LockOperation.DELETE, sections, id.toString()) { delegate.delete(id) }
    }

    private fun LifeEvent.requiredSection(): Set<LockSection> = setOf(when (type) {
        EventType.MEMORY -> LockSection.MEMORIES
        EventType.JOURNAL -> LockSection.MEMORIES
        EventType.MOOD -> LockSection.MOOD
        EventType.ACTIVITY, EventType.APP_USAGE -> LockSection.HEALTH_USAGE
    })
}

class ProtectedMemoryRepository(
    private val delegate: MemoryRepository,
    private val gateway: ProtectedContentGateway,
) : MemoryRepository by delegate {
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun observeTags(): Flow<List<com.luma.lifeos.domain.model.Tag>> = gateway.snapshot
        .distinctUntilChangedBy { state -> listOf(state.loadStatus, state.policy, state.sessionGeneration, state.grant) }
        .flatMapLatest { state ->
            if (!gateway.filterSection(true, LockSection.MEMORIES, state)) flowOf(emptyList())
            else delegate.observeTags().map { tags ->
                if (gateway.filterSection(true, LockSection.MEMORIES, gateway.snapshot.value)) {
                    immutableProjectionList(tags)
                } else emptyList()
            }
        }
    override suspend fun get(id: EventId): LifeEventRecord? = gateway.readRecord { delegate.get(id) }?.record
    override suspend fun canReadEditorBaseline(baseline: EventEditorBaseline): Boolean {
        val state = gateway.snapshot.value
        val requiredSections = baseline.requiredSections + LockSection.MEMORIES
        return baseline.requiredSections.isNotEmpty() && state.loadStatus == LockLoadStatus.READY &&
            state.decide(LockRequest(LockOperation.READ, requiredSections, LockContentClassification.CLASSIFIED)) ==
            AccessDecision.Allow
    }
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun observe(range: TimeRange): Flow<List<LifeEventRecord>> = gateway.snapshot
        .distinctUntilChangedBy { state -> listOf(state.loadStatus, state.policy, state.sessionGeneration, state.grant) }
        .flatMapLatest { state ->
            if (!gateway.filterSection(true, LockSection.MEMORIES, state)) flowOf(emptyList())
            else delegate.observe(range).map { records ->
                val current = gateway.snapshot.value
                if (gateway.filterSection(true, LockSection.MEMORIES, current)) {
                    gateway.projectRecords(records, current)
                } else emptyList()
            }
        }
    override fun observeLibrary(limit: Int, eventTypes: Set<EventType>): Flow<List<LifeEventRecord>> {
        require(limit > 0) { "Library limit must be positive" }
        val supported = setOf(EventType.MEMORY, EventType.MOOD)
        require(eventTypes.all { it in supported }) { "Only memory and mood events can be listed as memories" }
        if (eventTypes.isEmpty()) return flowOf(emptyList())

        return gateway.snapshot
            .distinctUntilChangedBy { state ->
                listOf(state.loadStatus, state.policy, state.sessionGeneration, state.grant)
            }
            .flatMapLatest { before ->
                if (before.loadStatus != LockLoadStatus.READY) return@flatMapLatest flowOf(emptyList())
                val authorizedTypes = eventTypes.filterTo(linkedSetOf()) { type ->
                    val sections = when (type) {
                        EventType.MEMORY -> setOf(LockSection.MEMORIES)
                        EventType.JOURNAL -> setOf(LockSection.MEMORIES)
                        EventType.MOOD -> setOf(LockSection.MEMORIES, LockSection.MOOD)
                        else -> emptySet()
                    }
                    before.decide(LockRequest(LockOperation.READ, sections)) ==
                        com.luma.lifeos.domain.lock.AccessDecision.Allow
                }
                if (authorizedTypes.isEmpty()) return@flatMapLatest flowOf(emptyList())

                delegate.observeLibrary(limit, authorizedTypes).map { records ->
                    val after = gateway.snapshot.value
                    if (!gateway.sameAuthorizationEpoch(before, after)) emptyList()
                    else gateway.projectRecords(records, before)
                }
            }
    }
    override suspend fun create(id: EventId, input: MemoryInput): EventId =
        gateway.commitEditorCreate(id, EventType.MEMORY, setOf(LockSection.MEMORIES)) {
            delegate.create(id, input)
        }
    override suspend fun update(id: EventId, input: MemoryInput) =
        gateway.commit(
            LockOperation.UPDATE,
            requiredExisting(id) + if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
            id.toString(),
        ) { delegate.update(id, input) }
    override suspend fun updateIfUnchanged(
        id: EventId,
        baseline: EventEditorBaseline,
        input: MemoryInput,
    ): EditorWriteResult = gateway.commit(
        LockOperation.UPDATE,
        baseline.requiredSections + if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
        id.toString(),
    ) { delegate.updateIfUnchanged(id, baseline, input) }
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
    override suspend fun saveMarkerIfUnchanged(
        id: EventId,
        baseline: EventEditorBaseline,
        input: MemoryMarkerInput,
    ): EditorWriteResult = gateway.commit(
        LockOperation.UPDATE,
        baseline.requiredSections + setOf(LockSection.MEMORIES) +
            if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
        id.toString(),
    ) { delegate.saveMarkerIfUnchanged(id, baseline, input) }
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
    override suspend fun canReadEditorBaseline(baseline: EventEditorBaseline): Boolean {
        val state = gateway.snapshot.value
        return baseline.requiredSections.isNotEmpty() && state.loadStatus == LockLoadStatus.READY &&
            state.decide(LockRequest(LockOperation.READ, baseline.requiredSections, LockContentClassification.CLASSIFIED)) ==
            AccessDecision.Allow
    }
    override suspend fun create(id: EventId, input: MoodInput): EventId =
        gateway.commitEditorCreate(id, EventType.MOOD, setOf(LockSection.MOOD)) {
            delegate.create(id, input)
        }
    override suspend fun update(id: EventId, input: MoodInput) =
        gateway.commit(
            LockOperation.UPDATE,
            requiredExisting(id) + if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
            id.toString(),
        ) { delegate.update(id, input) }
    override suspend fun updateIfUnchanged(
        id: EventId,
        baseline: EventEditorBaseline,
        input: MoodInput,
    ): EditorWriteResult = gateway.commit(
        LockOperation.UPDATE,
        baseline.requiredSections + if (input.personIds != null) setOf(LockSection.PEOPLE) else emptySet(),
        id.toString(),
    ) { delegate.updateIfUnchanged(id, baseline, input) }
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
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun observeTags() = gateway.snapshot
        .distinctUntilChangedBy { state -> listOf(state.loadStatus, state.policy, state.sessionGeneration, state.grant) }
        .flatMapLatest { state ->
            if (!gateway.filterSection(true, LockSection.MEMORIES, state)) flowOf(emptyList())
            else delegate.observeTags().map { tags ->
                if (gateway.filterSection(true, LockSection.MEMORIES, gateway.snapshot.value)) {
                    immutableProjectionList(tags)
                } else emptyList()
            }
        }
    override suspend fun get(id: EventId): LifeEventRecord? = gateway.readRecord { delegate.get(id) }?.record
    override suspend fun create(id: EventId, input: JournalInput): EventId =
        gateway.commitEditorCreate(id, EventType.JOURNAL, setOf(LockSection.MEMORIES)) {
            delegate.create(id, input)
        }
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
