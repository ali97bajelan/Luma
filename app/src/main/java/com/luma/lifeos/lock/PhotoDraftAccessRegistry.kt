package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockLoadStatus
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.PhotoAttachment
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** In-memory photo capabilities owned by an authorized memory route. */
class PhotoDraftAccessRegistry(
    private val gateway: ProtectedContentGateway,
    scope: CoroutineScope,
) {
    private data class Epoch(val policyGeneration: Long, val sessionGeneration: Long)
    private data class Registration(
        val leaseId: UUID,
        val editorLeaseId: UUID,
        val eventId: EventId?,
        val epoch: Epoch,
        val parentToken: UUID?,
        val token: UUID,
    )
    private data class AuthorizedScope(val registration: Registration, val sections: Set<LockSection>)

    private val lock = Any()
    private val registrations = mutableMapOf<UUID, Registration>()

    init {
        scope.launch {
            gateway.snapshot.collect {
                synchronized(lock) {
                    val currentSnapshot = gateway.snapshot.value
                    val currentEpoch = epoch(currentSnapshot)
                    registrations.entries.removeAll { (_, registration) ->
                        currentEpoch == null || registration.epoch != currentEpoch || !allowsMemories(currentSnapshot)
                    }
                }
            }
        }
    }

    /** Registers an editor or viewer lease only while the memory section is currently readable. */
    fun open(leaseId: UUID, eventId: EventId?): AutoCloseable = open(leaseId, eventId, leaseId)

    /** Child staging/viewer leases share authorization with their owning memory editor lease. */
    fun open(leaseId: UUID, eventId: EventId?, editorLeaseId: UUID): AutoCloseable {
        val editor = if (leaseId == editorLeaseId) null else currentRegistration(editorLeaseId)
        if (leaseId != editorLeaseId) check(
            editor != null && editor.editorLeaseId == editorLeaseId && editor.eventId == eventId,
        ) { "Memory editor photo scope is unavailable" }
        val before = gateway.snapshot.value
        val beforeEpoch = epoch(before)
        check(beforeEpoch != null && allowsMemories(before)) { "Memory photo access is unavailable" }
        val token = UUID.randomUUID()
        synchronized(lock) {
            val after = gateway.snapshot.value
            check(epoch(after) == beforeEpoch && allowsMemories(after)) { "Memory photo access changed" }
            if (editor != null) {
                val currentEditor = registrations[editorLeaseId]
                check(currentEditor?.token == editor.token && currentEditor.eventId == eventId) {
                    "Memory editor photo scope changed"
                }
            }
            val previous = registrations[leaseId]
            if (editor == null && previous != null) {
                registrations.entries.removeAll { (_, child) ->
                    child.editorLeaseId == leaseId && child.parentToken == previous.token
                }
            }
            registrations[leaseId] = Registration(leaseId, editorLeaseId, eventId, beforeEpoch, editor?.token, token)
        }
        return AutoCloseable {
            synchronized(lock) {
                if (registrations[leaseId]?.token == token) {
                    registrations.remove(leaseId)
                    registrations.entries.removeAll { (_, child) ->
                        child.editorLeaseId == leaseId && child.parentToken == token
                    }
                }
            }
        }
    }

    /** Whether a live route capability still exists for this lease. */
    fun isRegistered(leaseId: UUID): Boolean = currentRegistration(leaseId) != null

    /** Captured by facades to reject results if a route reopens with the same lease UUID. */
    fun tokenFor(leaseId: UUID): UUID? = currentRegistration(leaseId)?.token

    /** Returns the classified memory and parent-event sections for a live route lease. */
    suspend fun requiredSections(leaseId: UUID): Set<LockSection>? = authorizedScope(leaseId)?.sections

    /** A UUID alone is not authority; the live route and its current lock generation are required. */
    suspend fun permits(leaseId: UUID, @Suppress("UNUSED_PARAMETER") photo: PhotoAttachment): Boolean =
        authorizedScope(leaseId) != null

    /** Active leases tied to this lease's editor, used to verify staging-to-draft transfers. */
    suspend fun activeLeaseIds(leaseId: UUID): Set<UUID> {
        val requestingScope = authorizedScope(leaseId) ?: return emptySet()
        val editorLeaseId = requestingScope.registration.editorLeaseId
        val parent = authorizedScope(editorLeaseId) ?: return emptySet()
        val candidates = synchronized(lock) {
            registrations.values.filter {
                it.editorLeaseId == editorLeaseId && it.eventId == parent.registration.eventId &&
                    it.epoch == parent.registration.epoch
            }.map { it.leaseId }
        }
        return candidates.filterTo(linkedSetOf()) { authorizedScope(it) != null }
    }

    private suspend fun authorizedScope(leaseId: UUID): AuthorizedScope? {
        val registration = currentRegistration(leaseId) ?: return null
        val before = gateway.snapshot.value
        if (epoch(before) != registration.epoch || !allowsMemories(before)) return null
        val parentSections = registration.eventId?.let { gateway.requiredSections(it) } ?:
            if (registration.eventId == null) emptySet() else return null
        val sections = parentSections + LockSection.MEMORIES
        val after = gateway.snapshot.value
        if (epoch(after) != registration.epoch || !gateway.sameAuthorizationEpoch(before, after) ||
            !allowsSections(after, sections)
        ) return null
        val latest = currentRegistration(leaseId, sections) ?: return null
        if (latest.token != registration.token || latest.parentToken != registration.parentToken) return null
        return AuthorizedScope(registration, sections)
    }

    private fun currentRegistration(
        leaseId: UUID,
        requiredSections: Set<LockSection>? = null,
    ): Registration? {
        val snapshot = gateway.snapshot.value
        val currentEpoch = epoch(snapshot)
        val registration = synchronized(lock) { registrations[leaseId] }
        val parentIsCurrent = registration?.let { current ->
            if (current.editorLeaseId == current.leaseId) current.parentToken == null
            else synchronized(lock) {
                registrations[current.editorLeaseId]?.let { parent ->
                    parent.leaseId == current.editorLeaseId && parent.editorLeaseId == parent.leaseId &&
                        parent.token == current.parentToken && parent.eventId == current.eventId &&
                        parent.epoch == current.epoch
                } == true
            }
        } == true
        if (registration == null || currentEpoch == null || registration.epoch != currentEpoch ||
            !allowsMemories(snapshot) || !parentIsCurrent ||
            (requiredSections != null && !allowsSections(snapshot, requiredSections))
        ) {
            synchronized(lock) {
                if (registration != null && registrations[leaseId]?.token == registration.token) {
                    registrations.remove(leaseId)
                    if (registration.editorLeaseId == leaseId) {
                        registrations.entries.removeAll { (_, child) ->
                            child.editorLeaseId == leaseId && child.parentToken == registration.token
                        }
                    }
                }
            }
            return null
        }
        return registration
    }

    private fun allowsMemories(snapshot: com.luma.lifeos.domain.lock.LockSnapshot): Boolean =
        snapshot.decide(readRequest(setOf(LockSection.MEMORIES))) == AccessDecision.Allow

    private fun allowsSections(
        snapshot: com.luma.lifeos.domain.lock.LockSnapshot,
        sections: Set<LockSection>,
    ): Boolean = snapshot.decide(readRequest(sections)) == AccessDecision.Allow

    private fun readRequest(sections: Set<LockSection>) = LockRequest(
        operation = LockOperation.READ,
        requiredSections = sections,
        contentClassification = LockContentClassification.CLASSIFIED,
    )

    private fun epoch(snapshot: com.luma.lifeos.domain.lock.LockSnapshot): Epoch? {
        if (snapshot.loadStatus != LockLoadStatus.READY) return null
        val generation = snapshot.policy?.generation ?: return null
        return Epoch(generation, snapshot.sessionGeneration)
    }
}
