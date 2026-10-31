package com.luma.lifeos.domain.lock

import kotlinx.coroutines.flow.StateFlow

enum class LockLoadStatus { UNKNOWN, READY, ERROR }

/** Distinguishes intentionally unprotected content from an object T04 has not classified yet. */
enum class LockContentClassification { CLASSIFIED, KNOWN_UNPROTECTED, UNCLASSIFIED }

enum class LockOperation {
    READ,
    UPDATE,
    DELETE,
    CREATE,
    SHARE,
    EXPORT,
    BACKUP,
    RESTORE,
    RESET,
    SECURITY_SETTINGS,
}

enum class AccessDenial {
    INITIALIZING,
    STORAGE_ERROR,
    UNCLASSIFIED_CONTENT,
    INVALID_CREATE_SESSION,
    FRESH_AUTHENTICATION_REQUIRED,
}

/** In-memory authorization only. A grant is valid for one policy and process session generation. */
data class LockGrant(
    val sections: Set<LockSection> = emptySet(),
    val wholeApp: Boolean = false,
    val policyGeneration: Long = 0L,
    val sessionGeneration: Long = 0L,
    val issuedAtElapsedMs: Long = 0L,
) {
    init {
        require(policyGeneration >= 0L)
        require(sessionGeneration >= 0L)
        require(issuedAtElapsedMs >= 0L)
        require(!wholeApp || sections.isEmpty()) {
            "A whole-app grant is represented separately from section grants"
        }
    }
}

/** List/detail routes that share a scope use the same authorizationScopeId. */
data class LockBrowseContext(
    val topLevelDestination: String,
    val authorizationScopeId: String,
    val requiredSections: Set<LockSection> = emptySet(),
    val isSettings: Boolean = false,
    val isRestrictedCapture: Boolean = false,
) {
    init {
        require(topLevelDestination.isNotBlank())
        require(authorizationScopeId.isNotBlank())
    }
}

/** The snapshot is safe to publish: it has no password, recovery code, proof, or record data. */
data class LockSnapshot(
    val loadStatus: LockLoadStatus = LockLoadStatus.UNKNOWN,
    val policy: LockPolicy? = null,
    val grant: LockGrant = LockGrant(),
    val activeBrowseContext: LockBrowseContext? = null,
    val awayDeadlineElapsedMs: Long? = null,
    val sessionGeneration: Long = 0L,
) {
    init {
        require(sessionGeneration >= 0L)
        require(awayDeadlineElapsedMs == null || awayDeadlineElapsedMs >= 0L)
        if (loadStatus == LockLoadStatus.READY) require(policy != null) {
            "A ready lock snapshot must include its policy"
        }
    }

    /** Pure decision against this immutable snapshot; callers must still guard persistence. */
    fun decide(request: LockRequest): AccessDecision {
        when (loadStatus) {
            LockLoadStatus.UNKNOWN -> return AccessDecision.Deny(AccessDenial.INITIALIZING)
            LockLoadStatus.ERROR -> return AccessDecision.Deny(AccessDenial.STORAGE_ERROR)
            LockLoadStatus.READY -> Unit
        }

        val currentPolicy = policy ?: return AccessDecision.Deny(AccessDenial.STORAGE_ERROR)
        if (!currentPolicy.enabled) return AccessDecision.Allow

        if (request.operation in FRESH_AUTH_OPERATIONS) {
            return AccessDecision.Deny(AccessDenial.FRESH_AUTHENTICATION_REQUIRED)
        }
        if (request.operation in CONTENT_READ_WRITE_OPERATIONS) {
            val validClassification = when (request.contentClassification) {
                LockContentClassification.CLASSIFIED -> request.requiredSections.isNotEmpty()
                LockContentClassification.KNOWN_UNPROTECTED -> request.requiredSections.isEmpty()
                LockContentClassification.UNCLASSIFIED -> false
            }
            if (!validClassification) return AccessDecision.Deny(AccessDenial.UNCLASSIFIED_CONTENT)
        }
        if (request.operation == LockOperation.CREATE &&
            (request.objectId != null || request.createSessionId.isNullOrBlank())
        ) {
            return AccessDecision.Deny(AccessDenial.INVALID_CREATE_SESSION)
        }

        if (request.operation == LockOperation.CREATE) return AccessDecision.Allow

        val grantIsCurrent = grant.policyGeneration == currentPolicy.generation &&
            grant.sessionGeneration == sessionGeneration
        if (currentPolicy.coverage == LockCoverage.WHOLE_APP) {
            return if (grantIsCurrent && grant.wholeApp) {
                AccessDecision.Allow
            } else {
                AccessDecision.NeedUnlock(request.requiredSections, wholeApp = true)
            }
        }

        val protectedSections = request.requiredSections intersect currentPolicy.sections
        if (protectedSections.isEmpty()) return AccessDecision.Allow
        val grantedSections = if (grantIsCurrent) grant.sections else emptySet()
        val missingSections = protectedSections - grantedSections
        return if (missingSections.isEmpty()) {
            AccessDecision.Allow
        } else {
            AccessDecision.NeedUnlock(missingSections, wholeApp = false)
        }
    }

    private companion object {
        val CONTENT_READ_WRITE_OPERATIONS = setOf(
            LockOperation.READ,
            LockOperation.UPDATE,
            LockOperation.DELETE,
        )

        val FRESH_AUTH_OPERATIONS = setOf(
            LockOperation.SHARE,
            LockOperation.EXPORT,
            LockOperation.BACKUP,
            LockOperation.RESTORE,
            LockOperation.RESET,
            LockOperation.SECURITY_SETTINGS,
        )
    }
}

/**
 * A request names every content section it reads or mutates. Include PEOPLE when consuming linked
 * identity fields; unrelated prose stays classified independently. A CREATE session ID is only a
 * routing hint here: the restricted-capture service must validate its opaque session capability.
 */
data class LockRequest(
    val operation: LockOperation,
    val requiredSections: Set<LockSection>,
    val contentClassification: LockContentClassification = LockContentClassification.CLASSIFIED,
    val objectId: String? = null,
    val createSessionId: String? = null,
)

sealed interface AccessDecision {
    data object Allow : AccessDecision
    data class NeedUnlock(val sections: Set<LockSection>, val wholeApp: Boolean) : AccessDecision
    data class Deny(val reason: AccessDenial) : AccessDecision
}

/**
 * Implementations publish Unknown until the policy is loaded. `decide` reads only the current
 * snapshot; it is not a commit authorization. Writes must recheck under the serialized action
 * guard immediately before persistence. An action already admitted into its commit section may
 * finish while revocation waits for that section; queued actions are checked after revocation.
 * Async read results carry their policy/session generation and are discarded if either changes
 * before publication.
 */
interface LockAccess {
    val snapshot: StateFlow<LockSnapshot>

    fun decide(request: LockRequest): AccessDecision = snapshot.value.decide(request)
}
