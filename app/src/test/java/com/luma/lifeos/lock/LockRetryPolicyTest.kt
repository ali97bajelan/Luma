package com.luma.lifeos.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockRetryPolicyTest {
    @Test
    fun sharedCooldownSequenceMatchesFixtureFailuresFiveThroughEleven() {
        assertEquals(
            listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L),
            (5L..11L).map(LockRetryPolicy::cooldownAfterFailure),
        )
        assertEquals(listOf(0L, 0L, 0L, 0L), (1L..4L).map(LockRetryPolicy::cooldownAfterFailure))
    }

    @Test
    fun cooldownRemainingUsesElapsedTimeAndExpiresAtItsExactBoundary() {
        assertEquals(30_000L, LockRetryPolicy.remainingMs(1_030_000L, 1_000_000L))
        assertEquals(1L, LockRetryPolicy.remainingMs(1_030_000L, 1_029_999L))
        assertEquals(0L, LockRetryPolicy.remainingMs(1_030_000L, 1_030_000L))
        assertEquals(900_000L, LockRetryPolicy.remainingMs(-1L, 1_000_000L))
        assertEquals(900_000L, LockRetryPolicy.remainingMs(2_000_000L, -1L))
    }
}
