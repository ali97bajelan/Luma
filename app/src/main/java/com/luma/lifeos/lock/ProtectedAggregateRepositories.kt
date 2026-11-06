package com.luma.lifeos.lock

import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusGoalState
import com.luma.lifeos.domain.focus.FocusGoalVersion
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusProductUnavailableReason
import com.luma.lifeos.domain.focus.FocusReflection
import com.luma.lifeos.domain.focus.FocusTagCatalog
import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoDisplayRequest
import com.luma.lifeos.domain.model.PhotoReadScope
import com.luma.lifeos.domain.repository.DailySummaryRepository
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.ReviewDates
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewStatistics
import com.luma.lifeos.domain.review.WeeklyReview
import com.luma.lifeos.domain.search.SearchPage
import com.luma.lifeos.domain.summary.DailySummary
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ProtectedDailySummaryRepository(
    private val delegate: DailySummaryRepository,
    private val gateway: ProtectedContentGateway,
) : DailySummaryRepository by delegate {
    override fun observe(date: LocalDate, zone: ZoneId): Flow<DailySummary> =
        combine(delegate.observe(date, zone), gateway.snapshot) { summary, state ->
            if (canReadAggregate(state)) immutableSummary(summary) else DailySummary(date, zone, 0, 0, 0)
        }

    override suspend fun get(date: LocalDate, zone: ZoneId): DailySummary =
        gateway.readValue(aggregateRead(), { delegate.get(date, zone) }) { value, _ -> immutableSummary(value) }
            ?: DailySummary(date, zone, 0, 0, 0)

    override suspend fun recalculate(date: LocalDate, zone: ZoneId): DailySummary =
        gateway.commit(LockOperation.UPDATE, LockSection.entries.toSet()) { delegate.recalculate(date, zone) }

    private fun canReadAggregate(state: com.luma.lifeos.domain.lock.LockSnapshot) =
        state.decide(aggregateRead()) == com.luma.lifeos.domain.lock.AccessDecision.Allow

    private fun aggregateRead() = LockRequest(
        LockOperation.READ, LockSection.entries.toSet(), LockContentClassification.CLASSIFIED,
    )
}

class ProtectedWeeklyReviewRepository(
    private val delegate: WeeklyReviewRepository,
    private val gateway: ProtectedContentGateway,
) : WeeklyReviewRepository by delegate {
    override fun observe(period: com.luma.lifeos.domain.review.ReviewPeriod): Flow<WeeklyReview> =
        combine(delegate.observe(period), gateway.snapshot) { review, state ->
            if (canReadReview(state)) immutableReview(review) else emptyReview(period)
        }

    override suspend fun sources(
        dates: ReviewDates,
        zone: ZoneId,
        metric: ReviewMetric,
        offset: Int,
        limit: Int,
    ): SearchPage {
        require(offset >= 0 && limit > 0)
        val before = gateway.snapshot.value
        val request = aggregateRead()
        if (before.decide(request) != com.luma.lifeos.domain.lock.AccessDecision.Allow) {
            return SearchPage(emptyList(), offset, 0)
        }
        val raw = mutableListOf<com.luma.lifeos.domain.model.LifeEventRecord>()
        var rawTotal = Int.MAX_VALUE
        var nextOffset = 0
        while (nextOffset < rawTotal) {
            val page = delegate.sources(dates, zone, metric, nextOffset, SCAN_SIZE)
            rawTotal = page.totalCount
            if (page.records.isEmpty()) break
            raw += page.records
            nextOffset += page.records.size
        }
        if (!gateway.sameAuthorizationEpoch(before, gateway.snapshot.value)) return SearchPage(emptyList(), offset, 0)
        val safe = gateway.projectRecords(raw, before)
        return SearchPage(immutableProjectionList(safe.drop(offset).take(limit)), offset, safe.size)
    }

    private fun canReadReview(state: com.luma.lifeos.domain.lock.LockSnapshot) =
        state.decide(aggregateRead()) == com.luma.lifeos.domain.lock.AccessDecision.Allow

    private fun aggregateRead() = LockRequest(
        LockOperation.READ, LockSection.entries.toSet(), LockContentClassification.CLASSIFIED,
    )

    private fun emptyReview(period: com.luma.lifeos.domain.review.ReviewPeriod): WeeklyReview {
        val blank = ReviewStatistics(0, 0, 0, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
        return WeeklyReview(period, blank, blank)
    }

    private companion object { const val SCAN_SIZE = 100 }
}

class ProtectedPhotoRepository(
    private val delegate: PhotoRepository,
    private val gateway: ProtectedContentGateway,
    private val draftAccess: PhotoDraftAccessRegistry? = null,
) : PhotoRepository by delegate {
    override suspend fun import(draftId: UUID, id: UUID, source: String): PhotoAttachment {
        val access = draftAccess
        val scopeToken = access?.tokenFor(draftId)
        if (access == null || scopeToken == null) {
            return gateway.commit(LockOperation.CREATE, LockSection.entries.toSet()) {
                delegate.import(draftId, id, source)
            }
        }
        val routeSections = access.requiredSections(draftId) ?: throw unclassifiedExistingRecord()
        val ownerRequest = photoOwnerReadRequest(id)
        val sections = routeSections + ownerRequest?.requiredSections.orEmpty()
        return gateway.commit(LockOperation.UPDATE, sections, draftId.toString()) {
            if (access.tokenFor(draftId) != scopeToken || access.requiredSections(draftId) != routeSections) {
                throw unclassifiedExistingRecord()
            }
            val before = gateway.snapshot.value
            if (ownerRequest != null && before.decide(ownerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) {
                throw unclassifiedExistingRecord()
            }
            val photo = delegate.import(draftId, id, source)
            val latestOwnerRequest = photoOwnerReadRequest(id)
            val latestRouteSections = access.requiredSections(draftId)
            val retained = delegate.isRetainedBy(draftId, photo)
            val permitted = access.permits(draftId, photo)
            val finalSnapshot = gateway.snapshot.value
            if (!gateway.sameAuthorizationEpoch(before, finalSnapshot) || latestRouteSections != routeSections ||
                ownerRequest != latestOwnerRequest ||
                (latestOwnerRequest != null && finalSnapshot.decide(latestOwnerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) ||
                !allowsPhotoSections(finalSnapshot, routeSections) ||
                !retained || !permitted || access.tokenFor(draftId) != scopeToken
            ) throw unclassifiedExistingRecord()
            photo
        }
    }

    override suspend fun retainDraft(draftId: UUID, photos: List<PhotoAttachment>) {
        val access = draftAccess
        val scopeToken = access?.tokenFor(draftId)
        if (access == null || scopeToken == null) {
            gateway.commit(LockOperation.UPDATE, requiredPhotoSections(photos), draftId.toString()) {
                delegate.retainDraft(draftId, photos)
            }
            return
        }
        val routeSections = access.requiredSections(draftId) ?: throw unclassifiedExistingRecord()
        val ownerRequests = photos.map { photo -> photoOwnerReadRequest(photo.id) }
        val required = routeSections + ownerRequests.filterNotNull().flatMap { it.requiredSections }
        gateway.commit(LockOperation.UPDATE, required, draftId.toString()) {
            val before = gateway.snapshot.value
            if (access.tokenFor(draftId) != scopeToken || access.requiredSections(draftId) != routeSections) {
                throw unclassifiedExistingRecord()
            }
            photos.forEachIndexed { index, photo ->
                if (!access.permits(draftId, photo)) throw unclassifiedExistingRecord()
                val ownerRequest = ownerRequests[index]
                if (ownerRequest != null && before.decide(ownerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) {
                    throw unclassifiedExistingRecord()
                }
                if (ownerRequest == null && !isRetainedByEditorLease(access, draftId, photo)) {
                    throw unclassifiedExistingRecord()
                }
            }
            if (access.tokenFor(draftId) != scopeToken) throw unclassifiedExistingRecord()
            delegate.retainDraft(draftId, photos)
            val latestOwnerRequests = photos.map { photo -> photoOwnerReadRequest(photo.id) }
            val latestRouteSections = access.requiredSections(draftId)
            val retained = photos.all { delegate.isRetainedBy(draftId, it) }
            val permitted = photos.all { access.permits(draftId, it) }
            val after = gateway.snapshot.value
            if (!gateway.sameAuthorizationEpoch(before, after) || latestRouteSections != routeSections ||
                latestOwnerRequests != ownerRequests || !retained || !permitted || access.tokenFor(draftId) != scopeToken ||
                !allowsPhotoSections(after, routeSections) ||
                latestOwnerRequests.filterNotNull().any {
                    after.decide(it) != com.luma.lifeos.domain.lock.AccessDecision.Allow
                }
            ) throw unclassifiedExistingRecord()
        }
    }

    override suspend fun releaseDraft(draftId: UUID) =
        delegate.releaseDraft(draftId)

    override suspend fun preview(photo: PhotoAttachment): ByteArray? {
        val before = gateway.snapshot.value
        if (before.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return null
        val request = photoReadRequest(photo.id)
        if (before.decide(request) != com.luma.lifeos.domain.lock.AccessDecision.Allow) return null
        val bytes = delegate.preview(photo) ?: return null
        val latestRequest = photoReadRequest(photo.id)
        val after = gateway.snapshot.value
        if (!gateway.sameAuthorizationEpoch(before, after) ||
            request.requiredSections != latestRequest.requiredSections ||
            after.decide(request) != com.luma.lifeos.domain.lock.AccessDecision.Allow ||
            after.decide(latestRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow
        ) return null
        return bytes
    }

    override suspend fun display(
        photo: PhotoAttachment,
        request: PhotoDisplayRequest,
        scope: PhotoReadScope,
    ): ByteArray? {
        if (scope == PhotoReadScope.Saved) {
            val before = gateway.snapshot.value
            val ownerRequest = photoReadRequest(photo.id)
            if (before.decide(ownerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) return null
            val bytes = delegate.display(photo, request, PhotoReadScope.Saved) ?: return null
            val latestRequest = photoReadRequest(photo.id)
            val after = gateway.snapshot.value
            if (!gateway.sameAuthorizationEpoch(before, after) || ownerRequest != latestRequest ||
                after.decide(ownerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow ||
                after.decide(latestRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow
            ) return null
            return bytes
        }

        val access = draftAccess ?: return null
        val leaseId = (scope as PhotoReadScope.Draft).leaseId
        val scopeToken = access.tokenFor(leaseId) ?: return null
        val before = gateway.snapshot.value
        if (!access.permits(leaseId, photo)) return null
        val ownerRequest = photoOwnerReadRequest(photo.id)
        if (ownerRequest != null && before.decide(ownerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) return null
        if (!delegate.isRetainedBy(leaseId, photo)) return null
        val beforeSections = access.requiredSections(leaseId)
        val beforeDecode = gateway.snapshot.value
        if (!gateway.sameAuthorizationEpoch(before, beforeDecode) ||
            (ownerRequest != null && beforeDecode.decide(ownerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) ||
            !allowsPhotoSections(beforeDecode, beforeSections) ||
            access.tokenFor(leaseId) != scopeToken
        ) return null
        val bytes = delegate.display(photo, request, scope) ?: return null
        val latestOwnerRequest = photoOwnerReadRequest(photo.id)
        val retained = delegate.isRetainedBy(leaseId, photo)
        val permitted = access.permits(leaseId, photo)
        val latestSections = access.requiredSections(leaseId)
        val after = gateway.snapshot.value
        if (!gateway.sameAuthorizationEpoch(before, after) || ownerRequest != latestOwnerRequest ||
            (latestOwnerRequest != null && after.decide(latestOwnerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) ||
            !allowsPhotoSections(after, latestSections) ||
            !permitted || !retained || access.tokenFor(leaseId) != scopeToken
        ) return null
        return bytes
    }

    override suspend fun isRetainedBy(leaseId: UUID, photo: PhotoAttachment): Boolean {
        val access = draftAccess ?: return false
        val scopeToken = access.tokenFor(leaseId) ?: return false
        val before = gateway.snapshot.value
        if (!access.permits(leaseId, photo)) return false
        val ownerRequest = photoOwnerReadRequest(photo.id)
        if (ownerRequest != null && before.decide(ownerRequest) != com.luma.lifeos.domain.lock.AccessDecision.Allow) return false
        if (!delegate.isRetainedBy(leaseId, photo)) return false
        val latestOwnerRequest = photoOwnerReadRequest(photo.id)
        val retained = delegate.isRetainedBy(leaseId, photo)
        val permitted = access.permits(leaseId, photo)
        val sections = access.requiredSections(leaseId)
        val after = gateway.snapshot.value
        return gateway.sameAuthorizationEpoch(before, after) && ownerRequest == latestOwnerRequest &&
            (latestOwnerRequest == null || after.decide(latestOwnerRequest) == com.luma.lifeos.domain.lock.AccessDecision.Allow) &&
            allowsPhotoSections(after, sections) && permitted && retained && access.tokenFor(leaseId) == scopeToken
    }

    private suspend fun photoReadRequest(photoId: UUID): LockRequest {
        val owner = delegate.ownerEventId(photoId) ?: return LockRequest(
                LockOperation.READ,
                emptySet(),
                LockContentClassification.UNCLASSIFIED,
                objectId = photoId.toString(),
            )
        return photoOwnerReadRequest(photoId, owner)
    }

    private suspend fun photoOwnerReadRequest(photoId: UUID): LockRequest? =
        delegate.ownerEventId(photoId)?.let { owner -> photoOwnerReadRequest(photoId, owner) }

    private suspend fun photoOwnerReadRequest(photoId: UUID, owner: com.luma.lifeos.domain.model.EventId): LockRequest {
        val sections = gateway.requiredSections(owner) ?: return LockRequest(
            LockOperation.READ,
            emptySet(),
            LockContentClassification.UNCLASSIFIED,
            objectId = photoId.toString(),
        )
        return LockRequest(
            LockOperation.READ,
            sections,
            if (sections.isEmpty()) LockContentClassification.KNOWN_UNPROTECTED
            else LockContentClassification.CLASSIFIED,
            objectId = photoId.toString(),
        )
    }

    private fun allowsPhotoSections(
        snapshot: com.luma.lifeos.domain.lock.LockSnapshot,
        sections: Set<LockSection>?,
    ): Boolean = sections != null && snapshot.decide(
        LockRequest(LockOperation.READ, sections, LockContentClassification.CLASSIFIED),
    ) == com.luma.lifeos.domain.lock.AccessDecision.Allow

    private suspend fun isRetainedByEditorLease(
        access: PhotoDraftAccessRegistry,
        editorLeaseId: UUID,
        photo: PhotoAttachment,
    ): Boolean {
        for (leaseId in access.activeLeaseIds(editorLeaseId)) {
            if (delegate.isRetainedBy(leaseId, photo)) return true
        }
        return false
    }

    private suspend fun requiredPhotoSections(photos: List<PhotoAttachment>): Set<LockSection> {
        val result = mutableSetOf<LockSection>()
        for (photo in photos) {
            val owner = delegate.ownerEventId(photo.id)
            if (owner == null) {
                val state = gateway.snapshot.value
                if (state.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
                    state.policy?.enabled != false
                ) throw unclassifiedExistingRecord()
            } else {
                result += gateway.requiredSections(owner) ?: throw unclassifiedExistingRecord()
            }
        }
        return result
    }
}


class ProtectedFocusProductRepository(
    private val delegate: FocusProductRepository,
    private val gateway: ProtectedContentGateway,
) : FocusProductRepository {
    override suspend fun currentGeneration(): UUID = delegate.currentGeneration()

    override fun observeGoals(): Flow<FocusGoalState> =
        combine(delegate.observeGoals(), gateway.snapshot) { goals, state ->
            if (state.decide(focusProductRead()) == com.luma.lifeos.domain.lock.AccessDecision.Allow) goals
            else FocusGoalState.DISABLED
        }

    override fun observeTagCatalog(): Flow<FocusTagCatalog> =
        combine(delegate.observeTagCatalog(), gateway.snapshot) { catalog, state ->
            if (state.decide(focusProductRead()) == com.luma.lifeos.domain.lock.AccessDecision.Allow) catalog
            else FocusTagCatalog(revision = 0, tags = emptyList())
        }

    override suspend fun saveReflection(
        sessionId: UUID,
        expectedRevision: Long,
        reflection: FocusReflection,
        context: FocusProductContext,
    ): FocusProductResult = try {
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS), sessionId.toString()) {
            delegate.saveReflection(sessionId, expectedRevision, reflection, context)
        }
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        FocusProductResult.Unavailable(FocusProductUnavailableReason("access"))
    }

    override suspend fun updateSessionMetadata(
        sessionId: UUID,
        expectedRevision: Long,
        name: String?,
        tagName: String?,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult = try {
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS), sessionId.toString()) {
            delegate.updateSessionMetadata(sessionId, expectedRevision, name, tagName, expectedCatalogRevision, context)
        }
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        FocusProductResult.Unavailable(FocusProductUnavailableReason("access"))
    }

    override suspend fun renameTag(
        tagId: UUID,
        name: String,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult = commit(tagId.toString()) {
        delegate.renameTag(tagId, name, expectedCatalogRevision, context)
    }

    override suspend fun mergeTags(
        sourceTagId: UUID,
        targetTagId: UUID,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult = commit(sourceTagId.toString()) {
        delegate.mergeTags(sourceTagId, targetTagId, expectedCatalogRevision, context)
    }

    override suspend fun scheduleGoal(
        goal: FocusGoalVersion,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult = commit { delegate.scheduleGoal(goal, expectedSettingsRevision, context) }

    override suspend fun setGoalsEnabled(
        enabled: Boolean,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult = commit { delegate.setGoalsEnabled(enabled, expectedSettingsRevision, context) }

    private suspend fun commit(recordId: String? = null, write: suspend () -> FocusProductResult): FocusProductResult = try {
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS), recordId, commit = write)
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        FocusProductResult.Unavailable(FocusProductUnavailableReason("access"))
    }

    private fun focusProductRead() = LockRequest(
        LockOperation.READ, setOf(LockSection.FOCUS), LockContentClassification.CLASSIFIED,
    )
}

class ProtectedFocusRepository(
    private val delegate: FocusRepository,
    private val gateway: ProtectedContentGateway,
) : FocusRepository by delegate {
    override fun observeOpen(): Flow<FocusSession?> = combine(delegate.observeOpen(), gateway.snapshot) { session, state ->
        session.takeIf { canRead(state) }
    }

    override fun observeStatistics(range: TimeRange): Flow<FocusStatistics> =
        combine(delegate.observeStatistics(range), gateway.snapshot) { stats, state ->
            stats.takeIf { canRead(state) } ?: FocusStatistics(0, 0, 0)
        }

    override fun observeTagSuggestions(): Flow<List<FocusTag>> =
        combine(delegate.observeTagSuggestions(), gateway.snapshot) { tags, state ->
            if (canRead(state)) immutableProjectionList(tags) else emptyList()
        }

    override fun observeTags(): Flow<List<FocusTag>> =
        combine(delegate.observeTags(), gateway.snapshot) { tags, state ->
            if (canRead(state)) immutableProjectionList(tags) else emptyList()
        }

    override suspend fun history(range: TimeRange?, offset: Int, limit: Int): FocusPage =
        gateway.readValue(focusRead(), { delegate.history(range, offset, limit) }) { page, _ ->
            page.copy(rows = immutableProjectionList(page.rows))
        }
            ?: FocusPage(emptyList(), offset, 0)

    override suspend fun history(query: FocusHistoryQuery, offset: Int, limit: Int): FocusPage =
        gateway.readValue(focusRead(), { delegate.history(query, offset, limit) }) { page, _ ->
            page.copy(rows = immutableProjectionList(page.rows))
        }
            ?: FocusPage(emptyList(), offset, 0)

    override suspend fun start(
        commandId: UUID,
        sessionId: UUID,
        input: FocusStartInput,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult = gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS), sessionId.toString()) {
        delegate.start(commandId, sessionId, input, sample, context)
    }

    override suspend fun apply(command: FocusCommand): FocusWriteResult =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS), command.sessionId.toString()) {
            delegate.apply(command)
        }

    override suspend fun reconcile(sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult =
        gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS)) { delegate.reconcile(sample, context) }

    override suspend fun resolveRecovery(
        sessionId: UUID,
        expectedRevision: Long,
        choice: FocusRecoveryChoice,
        sample: FocusTimeSample,
        context: FocusWriteContext,
        terminalReason: FocusTerminalReason,
    ): FocusWriteResult = gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS), sessionId.toString()) {
        delegate.resolveRecovery(sessionId, expectedRevision, choice, sample, context, terminalReason)
    }

    override suspend fun checkpoint(
        sessionId: UUID,
        expectedRevision: Long,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult = gateway.commit(LockOperation.UPDATE, setOf(LockSection.FOCUS), sessionId.toString()) {
        delegate.checkpoint(sessionId, expectedRevision, sample, context)
    }

    override suspend fun delete(
        sessionId: UUID,
        expectedRevision: Long,
        context: FocusWriteContext,
    ): FocusWriteResult = gateway.commit(LockOperation.DELETE, setOf(LockSection.FOCUS), sessionId.toString()) {
        delegate.delete(sessionId, expectedRevision, context)
    }

    override suspend fun discardOpen(
        sessionId: UUID,
        expectedRevision: Long,
        context: FocusWriteContext,
    ): FocusWriteResult = gateway.commit(LockOperation.DELETE, setOf(LockSection.FOCUS), sessionId.toString()) {
        delegate.discardOpen(sessionId, expectedRevision, context)
    }

    private fun canRead(state: com.luma.lifeos.domain.lock.LockSnapshot) =
        state.decide(focusRead()) == com.luma.lifeos.domain.lock.AccessDecision.Allow

    private fun focusRead() = LockRequest(
        LockOperation.READ, setOf(LockSection.FOCUS), LockContentClassification.CLASSIFIED,
    )
}

private fun immutableSummary(summary: DailySummary): DailySummary {
    val usage = summary.usage
    return summary.copy(
        usage = usage?.copy(apps = immutableProjectionList(usage.apps)),
        activities = summary.activities?.let { immutableProjectionList(it) },
    )
}

private fun immutableReview(review: WeeklyReview): WeeklyReview = review.copy(
    current = immutableReviewStatistics(review.current),
    previous = immutableReviewStatistics(review.previous),
)

private fun immutableReviewStatistics(statistics: ReviewStatistics): ReviewStatistics {
    val usage = statistics.usage
    return statistics.copy(
        moodDays = immutableProjectionList(statistics.moodDays),
        emotions = immutableProjectionList(statistics.emotions),
        people = immutableProjectionList(statistics.people),
        places = immutableProjectionList(statistics.places),
        activities = immutableProjectionList(statistics.activities),
        usage = usage?.copy(
            apps = immutableProjectionList(usage.apps),
            days = immutableProjectionList(usage.days.map { day ->
                day.copy(apps = immutableProjectionList(day.apps))
            }),
        ),
    )
}
