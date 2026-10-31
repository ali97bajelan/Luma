package com.luma.lifeos.domain.lock

/** A conservative transition to apply to the process-local grants after a browse-context change. */
data class NavigationGrantRevocation(
    val sections: Set<LockSection> = emptySet(),
    val wholeApp: Boolean = false,
) {
    val isEmpty: Boolean get() = sections.isEmpty() && !wholeApp
}

/**
 * True at the exact relock boundary. Samples must come from one monotonic clock. Invalid or
 * reversed samples fail closed, including negative values and a clock that moved backwards.
 */
fun deadlineExpired(departedAtMs: Long, nowMs: Long, mode: RelockMode): Boolean {
    if (departedAtMs < 0L || nowMs < 0L || nowMs < departedAtMs) return true
    val durationMs = mode.awayDurationMs ?: return true
    return nowMs - departedAtMs >= durationMs
}

/**
 * Every-time mode preserves grants within a related list/detail scope and drops selected grants
 * when leaving that scope, switching top-level destinations, opening settings, or entering
 * restricted capture. Timed mode never changes grants due to navigation. Capture does not update
 * an away deadline; only the lifecycle coordinator may record a real departure sample.
 */
fun navigationGrantRevocation(
    mode: RelockMode,
    current: LockBrowseContext?,
    next: LockBrowseContext,
    currentlyGrantedSections: Set<LockSection>,
): NavigationGrantRevocation {
    if (mode != RelockMode.EVERY_TIME || current == null) return NavigationGrantRevocation()

    val mustDropSelectedGrants = next.isSettings || next.isRestrictedCapture ||
        current.topLevelDestination != next.topLevelDestination
    val sectionsToRevoke = when {
        mustDropSelectedGrants -> currentlyGrantedSections
        current.authorizationScopeId != next.authorizationScopeId ->
            currentlyGrantedSections - next.requiredSections
        else -> emptySet()
    }

    // Whole-app access is revoked by lifecycle departure, device lock, explicit Lock now, and
    // generation changes; route changes only narrow per-section grants.
    return NavigationGrantRevocation(sections = sectionsToRevoke, wholeApp = false)
}
