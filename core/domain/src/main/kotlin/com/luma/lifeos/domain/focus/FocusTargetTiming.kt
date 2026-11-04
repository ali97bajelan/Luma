package com.luma.lifeos.domain.focus

/** Returns null for count-up sessions and never derives time from wall-clock spans. */
fun remainingFocusMillis(session: FocusSession): Long? = session.timer.targetMillis?.let { target ->
    (target - session.creditedMillis).coerceAtLeast(0L)
}

/** Caps projected active credit at the saved target; callers pass the whole-session candidate. */
fun capFocusAdvance(session: FocusSession, candidateCreditMillis: Long): Long {
    require(candidateCreditMillis >= 0L) { "Candidate focus credit cannot be negative" }
    return session.timer.targetMillis?.let(candidateCreditMillis::coerceAtMost) ?: candidateCreditMillis
}
