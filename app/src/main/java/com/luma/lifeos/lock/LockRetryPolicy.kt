package com.luma.lifeos.lock

/** Consecutive password and recovery-code failures share this bounded delay schedule. */
object LockRetryPolicy {
    const val FIRST_COOLDOWN_FAILURE: Long = 5L
    const val FIRST_COOLDOWN_MS: Long = 30_000L
    const val MAX_COOLDOWN_MS: Long = 900_000L

    fun cooldownAfterFailure(failureCount: Long): Long {
        if (failureCount < FIRST_COOLDOWN_FAILURE) return 0L
        val doublingSteps = (failureCount - FIRST_COOLDOWN_FAILURE).coerceAtMost(5L)
        val doubled = FIRST_COOLDOWN_MS shl doublingSteps.toInt()
        return doubled.coerceAtMost(MAX_COOLDOWN_MS)
    }

    fun remainingMs(cooldownUntilElapsedMs: Long, nowElapsedMs: Long): Long {
        if (cooldownUntilElapsedMs < 0L || nowElapsedMs < 0L) return MAX_COOLDOWN_MS
        if (cooldownUntilElapsedMs <= nowElapsedMs) return 0L
        return cooldownUntilElapsedMs - nowElapsedMs
    }
}
