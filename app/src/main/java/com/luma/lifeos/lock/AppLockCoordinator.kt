package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockAccess
import com.luma.lifeos.domain.lock.LockBrowseContext
import com.luma.lifeos.domain.lock.LockGrant
import com.luma.lifeos.domain.lock.LockLoadStatus
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.LockSnapshot
import com.luma.lifeos.domain.lock.navigationGrantRevocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

/** One process-local owner for lock policy, navigation grants, and pending destinations. */
class AppLockCoordinator(
    private val readPersistedPolicy: suspend () -> LockPolicy?,
) : LockAccess {
    private val monitor = Any()
    private val mutableSnapshot = MutableStateFlow(LockSnapshot())
    override val snapshot: StateFlow<LockSnapshot> = mutableSnapshot.asStateFlow()
    private var visibleActivityCount = 0
    private var pendingBrowse: BrowseRequest? = null
    private var pendingNavigation: PendingLockNavigation? = null

    /** Must complete before the app shell is allowed to draw a private destination. */
    suspend fun initialize() {
        val loaded = try {
            readPersistedPolicy() ?: LockPolicy()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            synchronized(monitor) {
                mutableSnapshot.value = LockSnapshot(
                    loadStatus = LockLoadStatus.ERROR,
                    sessionGeneration = nextSession(mutableSnapshot.value.sessionGeneration),
                )
                pendingBrowse = null
                pendingNavigation = null
            }
            return
        }
        onPolicyChanged(loaded)
        synchronized(monitor) {
            mutableSnapshot.value = mutableSnapshot.value.copy(loadStatus = LockLoadStatus.READY)
        }
    }

    fun onVisibleActivityCountChanged(count: Int, elapsedMs: Long, restrictedCapture: Boolean) {
        require(count >= 0)
        synchronized(monitor) {
            val current = mutableSnapshot.value
            if (elapsedMs < 0L) {
                mutableSnapshot.value = current.copy(
                    grant = LockGrant(),
                    awayDeadlineElapsedMs = null,
                    sessionGeneration = nextSession(current.sessionGeneration),
                )
                visibleActivityCount = count
                invalidateBrowse()
                return
            }
            val wasVisible = visibleActivityCount > 0
            val nowVisible = count > 0
            visibleActivityCount = count
            val policy = current.policy
            when {
                wasVisible && !nowVisible && policy?.enabled == true -> {
                    val deadline = policy.relockMode.awayDurationMs?.let { saturatedAdd(elapsedMs, it) }
                    val grant = if (deadline == null) LockGrant() else current.grant
                    mutableSnapshot.value = current.copy(
                        grant = grant,
                        awayDeadlineElapsedMs = deadline,
                        sessionGeneration = if (deadline == null) nextSession(current.sessionGeneration)
                        else current.sessionGeneration,
                    )
                }
                !wasVisible && nowVisible -> {
                    val deadline = current.awayDeadlineElapsedMs
                    val expired = deadline != null && (elapsedMs < 0L || elapsedMs >= deadline)
                    mutableSnapshot.value = current.copy(
                        grant = if (expired) LockGrant() else current.grant,
                        awayDeadlineElapsedMs = null,
                        sessionGeneration = if (expired) nextSession(current.sessionGeneration)
                        else current.sessionGeneration,
                    )
                }
            }
            if (count == 0 && policy?.enabled == true && policy.relockMode.awayDurationMs == null) {
                invalidateBrowse()
            }
            // Capture is deliberately not an authorization or timer refresh. Its presence is
            // retained only as lifecycle context for diagnostics and future route reconciliation.
            if (restrictedCapture) Unit
        }
    }

    fun onDeviceLocked() = invalidateAllGrants()

    fun onNavigationChanged(context: LockBrowseContext) {
        synchronized(monitor) {
            val current = mutableSnapshot.value
            if (current.activeBrowseContext != context) invalidateBrowse()
            val policy = current.policy ?: run {
                mutableSnapshot.value = current.copy(activeBrowseContext = context)
                return
            }
            val previous = current.activeBrowseContext
            val returningToParent = previous != null &&
                previous.topLevelDestination == context.topLevelDestination &&
                previous.authorizationScopeId != context.authorizationScopeId &&
                context.authorizationScopeId == context.topLevelDestination
            val revocation = if (returningToParent) {
                // A parent list remains a filtered projection of the sections already granted
                // while viewing its related detail. Entering the detail still narrows grants.
                com.luma.lifeos.domain.lock.NavigationGrantRevocation()
            } else {
                navigationGrantRevocation(
                    mode = policy.relockMode,
                    current = previous,
                    next = context,
                    currentlyGrantedSections = current.grant.sections,
                )
            }
            val wholeApp = current.grant.wholeApp && !revocation.wholeApp
            val sections = current.grant.sections - revocation.sections
            val grant = current.grant.copy(sections = sections, wholeApp = wholeApp)
            val sessionGeneration = if (grant != current.grant) nextSession(current.sessionGeneration)
            else current.sessionGeneration
            mutableSnapshot.value = current.copy(
                grant = if (sessionGeneration != current.sessionGeneration) {
                    grant.copy(sessionGeneration = sessionGeneration)
                } else grant,
                activeBrowseContext = context,
                sessionGeneration = sessionGeneration,
            )
            if (context.isSettings || context.isRestrictedCapture) invalidateBrowse()
        }
    }

    fun lockNow() = invalidateAllGrants()

    /** A policy generation change always discards every in-memory capability. */
    fun onPolicyChanged(policy: LockPolicy) {
        synchronized(monitor) {
            val current = mutableSnapshot.value
            val changed = current.policy != policy
            mutableSnapshot.value = current.copy(
                loadStatus = if (current.loadStatus == LockLoadStatus.ERROR) LockLoadStatus.ERROR else LockLoadStatus.READY,
                policy = policy,
                grant = if (changed) LockGrant() else current.grant,
                awayDeadlineElapsedMs = if (changed) null else current.awayDeadlineElapsedMs,
                sessionGeneration = if (changed) nextSession(current.sessionGeneration) else current.sessionGeneration,
            )
            if (changed) {
                pendingBrowse = null
                pendingNavigation = null
            }
        }
    }

    /** Stores only the requested sections and a typed, preview-free destination. */
    fun requestBrowse(
        request: LockRequest,
        destination: LockPendingDestination?,
    ): AccessDecision = synchronized(monitor) {
        if (mutableSnapshot.value.activeBrowseContext?.isRestrictedCapture == true ||
            destination?.requiresCreateSession() == true
        ) {
            pendingBrowse = null
            pendingNavigation = null
            return@synchronized AccessDecision.Deny(
                com.luma.lifeos.domain.lock.AccessDenial.INVALID_CREATE_SESSION,
            )
        }
        val decision = mutableSnapshot.value.decide(request)
        if (decision is AccessDecision.NeedUnlock) {
            val sections = if (decision.wholeApp) emptySet() else decision.sections.toSet()
            pendingBrowse = BrowseRequest(sections, decision.wholeApp)
            pendingNavigation = destination?.let(PendingLockNavigation::create)
        } else {
            pendingBrowse = null
            pendingNavigation = null
        }
        decision
    }

    fun pendingBrowseRequest(): LockRequest? = synchronized(monitor) {
        pendingBrowse?.let { pending ->
            LockRequest(
                operation = LockOperation.READ,
                requiredSections = pending.sections,
                contentClassification = if (pending.sections.isEmpty()) {
                    com.luma.lifeos.domain.lock.LockContentClassification.KNOWN_UNPROTECTED
                } else com.luma.lifeos.domain.lock.LockContentClassification.CLASSIFIED,
            )
        }
    }

    /** Consume the proof and navigate at most once, after rechecking the current snapshot. */
    fun unlock(proof: OperationProof, sections: Set<LockSection>, wholeApp: Boolean): PendingLockNavigation? =
        synchronized(monitor) {
            val pending = pendingBrowse ?: return null
            val current = mutableSnapshot.value
            val policy = current.policy ?: return null
            if (current.loadStatus != LockLoadStatus.READY || !policy.enabled ||
                current.activeBrowseContext?.isRestrictedCapture == true ||
                pending.sections != sections || pending.wholeApp != wholeApp
            ) return null
            if (!proof.bindBrowseRequest(sections, wholeApp, policy.generation) ||
                !proof.consumeBrowseRequest(sections, wholeApp, policy.generation)
            ) return null

            val grant = if (wholeApp) {
                LockGrant(wholeApp = true, policyGeneration = policy.generation,
                    sessionGeneration = current.sessionGeneration)
            } else {
                current.grant.copy(
                    sections = current.grant.sections + sections,
                    wholeApp = false,
                    policyGeneration = policy.generation,
                    sessionGeneration = current.sessionGeneration,
                )
            }
            mutableSnapshot.value = current.copy(grant = grant, awayDeadlineElapsedMs = null)
            val navigation = pendingNavigation
            pendingBrowse = null
            pendingNavigation = null
            navigation?.takeIf { nav -> destinationStillAllowed(nav.destination, mutableSnapshot.value) }
        }

    /** Cancellation never restores the old private route. The caller returns to a safe shell. */
    fun cancelPendingNavigation() = synchronized(monitor) {
        pendingBrowse = null
        pendingNavigation = null
    }

    internal suspend fun <T> serializeAction(block: suspend () -> T): T = actionMutex.withLock { block() }

    /** Atomic admission point shared with policy-generation invalidation callbacks. */
    internal fun admissionDecision(request: LockRequest, persisted: LockPolicy?): AccessDecision =
        synchronized(monitor) {
            val current = mutableSnapshot.value
            if (current.loadStatus != LockLoadStatus.READY || current.policy == null ||
                persisted == null || current.policy != persisted
            ) {
                AccessDecision.Deny(com.luma.lifeos.domain.lock.AccessDenial.STORAGE_ERROR)
            } else {
                current.decide(request)
            }
        }

    private fun destinationStillAllowed(destination: LockPendingDestination, current: LockSnapshot): Boolean {
        val required = destination.requiredSections()
        return current.decide(
            LockRequest(
                operation = LockOperation.READ,
                requiredSections = required,
                contentClassification = if (required.isEmpty()) {
                    com.luma.lifeos.domain.lock.LockContentClassification.KNOWN_UNPROTECTED
                } else com.luma.lifeos.domain.lock.LockContentClassification.CLASSIFIED,
            ),
        ) == AccessDecision.Allow
    }

    private fun LockPendingDestination.requiredSections(): Set<LockSection> = when (this) {
        is LockPendingDestination.Primary -> when (route) {
            "review" -> LockSection.entries.toSet()
            else -> emptySet()
        }
        LockPendingDestination.Search -> LockSection.entries.toSet()
        LockPendingDestination.CaptureMenu -> LockSection.entries.toSet()
        LockPendingDestination.NewHabit -> setOf(LockSection.HABITS)
        LockPendingDestination.NewTask -> setOf(LockSection.TASKS)
        LockPendingDestination.People -> setOf(LockSection.PEOPLE)
        LockPendingDestination.Habits -> setOf(LockSection.HABITS)
        LockPendingDestination.Tasks -> setOf(LockSection.TASKS)
        is LockPendingDestination.PersonDetail -> setOf(LockSection.PEOPLE)
        is LockPendingDestination.PlaceDetail -> LockSection.entries.toSet()
        is LockPendingDestination.EventDetail -> LockSection.entries.toSet()
        is LockPendingDestination.EventEditor -> LockSection.entries.toSet()
        is LockPendingDestination.TaskEditor -> setOf(LockSection.TASKS)
        is LockPendingDestination.HabitDetail -> setOf(LockSection.HABITS)
        is LockPendingDestination.FocusHistory -> setOf(LockSection.FOCUS)
    }

    private fun LockPendingDestination.requiresCreateSession(): Boolean = when (this) {
        LockPendingDestination.CaptureMenu,
        LockPendingDestination.NewHabit,
        LockPendingDestination.NewTask -> true
        is LockPendingDestination.EventEditor -> eventId == null
        else -> false
    }

    private fun invalidateAllGrants() = synchronized(monitor) {
        val current = mutableSnapshot.value
        mutableSnapshot.value = current.copy(
            grant = LockGrant(),
            awayDeadlineElapsedMs = null,
            sessionGeneration = nextSession(current.sessionGeneration),
        )
        invalidateBrowse()
    }

    private fun invalidateBrowse() {
        pendingBrowse = null
        pendingNavigation = null
    }

    private fun nextSession(current: Long): Long = if (current == Long.MAX_VALUE) 0L else current + 1L

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left < 0L || Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private data class BrowseRequest(val sections: Set<LockSection>, val wholeApp: Boolean)

    private companion object {
        val actionMutex = Mutex()
    }
}
