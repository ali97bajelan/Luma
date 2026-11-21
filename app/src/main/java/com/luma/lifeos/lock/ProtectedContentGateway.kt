package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockAccess
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockRecordClassification
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.lock.LockSnapshot
import com.luma.lifeos.domain.lock.ProtectedRecordClassifier
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.EventId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The only UI-facing record projection and commit authorization boundary for existing content. */
class ProtectedContentGateway(
    private val access: LockAccess,
    private val actions: LockActionGuard,
    private val loadRecordForClassification: suspend (EventId) -> LifeEventRecord? = { null },
    private val loadMetadataForClassification: suspend (EventId) -> LockRecordClassification? = { null },
) {
    val snapshot get() = access.snapshot

    /** Returns only the section labels needed to gate a route; record content stays inside the facade. */
    suspend fun classifyRecord(id: EventId, includePeopleFields: Boolean = false): Set<LockSection>? =
        recordClassification(id)?.let { classification ->
            ProtectedRecordClassifier.requiredSections(classification) +
                if (includePeopleFields) ProtectedRecordClassifier.peopleFieldSections(classification) else emptySet()
        }

    /** Classification metadata only; raw record values never leave this gateway. */
    suspend fun requiredSections(id: EventId, includePeopleFields: Boolean = false): Set<LockSection>? =
        classifyRecord(id, includePeopleFields)

    private suspend fun recordClassification(id: EventId): LockRecordClassification? =
        loadMetadataForClassification(id) ?: loadRecordForClassification(id)?.let { record ->
            LockRecordClassification(
                type = record.event.type,
                hasMemoryMarker = record.memory != null,
                hasPeople = record.people.isNotEmpty(),
            )
        }

    /** Create retries are checked against an existing record's actual scope and event kind. */
    suspend fun <T> commitEditorCreate(
        id: EventId,
        expectedType: EventType,
        newSections: Set<LockSection>,
        commit: suspend () -> T,
    ): T = actions.executeResolved(
        request = {
            val classification = recordClassification(id)
            if (classification != null && classification.type != expectedType) {
                throw LockActionDeniedException(
                    AccessDecision.Deny(com.luma.lifeos.domain.lock.AccessDenial.UNCLASSIFIED_CONTENT),
                )
            }
            val existingSections = classification?.let(ProtectedRecordClassifier::requiredSections).orEmpty()
            LockRequest(
                operation = LockOperation.CREATE_IN_EDITOR,
                requiredSections = newSections + existingSections,
                contentClassification = LockContentClassification.CLASSIFIED,
                objectId = id.toString(),
            )
        },
        commit = commit,
    )

    fun projectRecord(record: LifeEventRecord, state: LockSnapshot = snapshot.value): ProtectedRecordProjection? {
        if (state.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return null
        val required = ProtectedRecordClassifier.requiredSections(record)
        val classification = if (required.isEmpty()) LockContentClassification.KNOWN_UNPROTECTED
        else LockContentClassification.CLASSIFIED
        val request = LockRequest(
            operation = LockOperation.READ,
            requiredSections = required,
            contentClassification = classification,
            objectId = record.event.id.toString(),
        )
        if (state.decide(request) != AccessDecision.Allow) return null

        val peopleRequest = LockRequest(
            operation = LockOperation.READ,
            requiredSections = setOf(LockSection.PEOPLE),
            contentClassification = LockContentClassification.CLASSIFIED,
            objectId = record.event.id.toString(),
        )
        val people = if (state.decide(peopleRequest) == AccessDecision.Allow) record.people else emptyList()
        val mood = record.mood
        val journal = record.journal
        val projected = record.copy(
            mood = mood?.copy(emotions = immutableProjectionList(mood.emotions)),
            journal = journal?.copy(tags = immutableProjectionList(journal.tags)),
            tags = immutableProjectionList(record.tags),
            people = immutableProjectionList(people),
            photos = immutableProjectionList(record.photos),
            activities = immutableProjectionList(record.activities),
        )
        return ProtectedRecordProjection(
            record = projected,
            requiredSections = immutableProjectionSections(required),
            policyGeneration = state.policy?.generation ?: 0L,
            sessionGeneration = state.sessionGeneration,
        )
    }

    fun projectRecords(records: List<LifeEventRecord>, state: LockSnapshot = snapshot.value): List<LifeEventRecord> =
        immutableProjectionList(records.mapNotNull { projectRecord(it, state)?.record })

    fun <T> mapAfterRevocation(source: Flow<T>, projection: (T, LockSnapshot) -> T): Flow<T> =
        combine(source, snapshot) { value, state -> projection(value, state) }

    fun records(source: Flow<List<LifeEventRecord>>): Flow<List<LifeEventRecord>> =
        combine(source, snapshot) { records, state -> projectRecords(records, state) }

    /** A suspend query cannot publish a value if its authorization generation changed mid-flight. */
    suspend fun readRecord(read: suspend () -> LifeEventRecord?): ProtectedRecordProjection? {
        val before = snapshot.value
        if (before.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return null
        val record = read() ?: return null
        val after = snapshot.value
        if (!sameAuthorizationEpoch(before, after)) return null
        return projectRecord(record, after)
    }

    suspend fun <T> readValue(
        request: LockRequest,
        read: suspend () -> T,
        redact: (T, LockSnapshot) -> T?,
    ): T? {
        val before = snapshot.value
        if (before.decide(request) != AccessDecision.Allow) return null
        val result = read()
        val after = snapshot.value
        if (!sameAuthorizationEpoch(before, after) || after.decide(request) != AccessDecision.Allow) return null
        return redact(result, after)
    }

    suspend fun <T> commit(
        operation: LockOperation,
        sections: Set<LockSection>,
        objectId: String? = null,
        commit: suspend () -> T,
    ): T = actions.execute(
        LockRequest(
            operation = operation,
            requiredSections = sections,
            contentClassification = if (sections.isEmpty()) LockContentClassification.KNOWN_UNPROTECTED
            else LockContentClassification.CLASSIFIED,
            objectId = objectId,
        ),
        commit,
    )

    fun filterSection(value: Boolean, section: LockSection, state: LockSnapshot = snapshot.value): Boolean =
        value && state.decide(
            LockRequest(
                operation = LockOperation.READ,
                requiredSections = setOf(section),
                contentClassification = LockContentClassification.CLASSIFIED,
            ),
        ) == AccessDecision.Allow

    fun sameAuthorizationEpoch(before: LockSnapshot, after: LockSnapshot): Boolean =
        before.loadStatus == com.luma.lifeos.domain.lock.LockLoadStatus.READY &&
            after.loadStatus == com.luma.lifeos.domain.lock.LockLoadStatus.READY &&
            before.policy?.generation == after.policy?.generation &&
            before.sessionGeneration == after.sessionGeneration
}
