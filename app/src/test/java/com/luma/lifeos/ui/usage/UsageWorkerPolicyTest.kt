package com.luma.lifeos.ui.usage

import com.luma.lifeos.domain.usage.UsageSyncOutcome
import com.luma.lifeos.usage.shouldRetryUsage
import org.junit.Assert.*
import org.junit.Test

class UsageWorkerPolicyTest {
    @Test fun transientFailuresRetryWithABoundAndDeniedDisabledEmptyResultsDoNot() {
        assertTrue(shouldRetryUsage(UsageSyncOutcome.ERROR, 0))
        assertTrue(shouldRetryUsage(UsageSyncOutcome.LOCKED, 1))
        assertFalse(shouldRetryUsage(UsageSyncOutcome.ERROR, 3))
        listOf(UsageSyncOutcome.DISABLED, UsageSyncOutcome.ACCESS_REQUIRED, UsageSyncOutcome.NO_DATA,
            UsageSyncOutcome.UNAVAILABLE, UsageSyncOutcome.SUCCESS).forEach { assertFalse(shouldRetryUsage(it, 0)) }
    }
}
