package com.luma.lifeos.domain.lock

/** A navigation-triggered grant revocation. Foreground navigation does not revoke grants. */
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
 * Navigation does not end the foreground session. Relock is handled when no Luma activity is
 * visible: immediately for [RelockMode.EVERY_TIME], or at the away deadline for timed modes.
 */
@Suppress("UNUSED_PARAMETER")
fun navigationGrantRevocation(
    mode: RelockMode,
    current: LockBrowseContext?,
    next: LockBrowseContext,
    currentlyGrantedSections: Set<LockSection>,
): NavigationGrantRevocation = NavigationGrantRevocation()
