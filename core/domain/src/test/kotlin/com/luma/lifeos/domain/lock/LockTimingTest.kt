package com.luma.lifeos.domain.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTimingTest {
    @Test
    fun eachFixtureDeadlineRetainsAtBeforeAndExpiresAtAndAfterBoundary() {
        val departure = 1_000_000L
        val vectors = listOf(
            RelockMode.AFTER_1_MINUTE to 60_000L,
            RelockMode.AFTER_5_MINUTES to 300_000L,
            RelockMode.AFTER_15_MINUTES to 900_000L,
        )
        vectors.forEach { (mode, duration) ->
            assertFalse("$mode before", deadlineExpired(departure, departure + duration - 1L, mode))
            assertTrue("$mode exact", deadlineExpired(departure, departure + duration, mode))
            assertTrue("$mode after", deadlineExpired(departure, departure + duration + 1L, mode))
        }
    }

    @Test
    fun everyTimeAndInvalidMonotonicSamplesExpireConservatively() {
        assertTrue(deadlineExpired(1_000_000, 1_000_000, RelockMode.EVERY_TIME))
        assertTrue(deadlineExpired(-1, 2, RelockMode.AFTER_5_MINUTES))
        assertTrue(deadlineExpired(2, -1, RelockMode.AFTER_5_MINUTES))
        assertTrue(deadlineExpired(2, 1, RelockMode.AFTER_5_MINUTES))
    }

    @Test
    fun relatedScopesRetainOnlyStillRequiredGrants() {
        val current = LockBrowseContext("timeline", "timeline-list", setOf(LockSection.MEMORIES))
        val related = current.copy(authorizationScopeId = "memory-detail", requiredSections = setOf(LockSection.MEMORIES))

        assertTrue(
            navigationGrantRevocation(
                RelockMode.EVERY_TIME,
                current,
                related,
                setOf(LockSection.MEMORIES),
            ).isEmpty,
        )
        assertEquals(
            NavigationGrantRevocation(sections = setOf(LockSection.MEMORIES)),
            navigationGrantRevocation(
                RelockMode.EVERY_TIME,
                current,
                related.copy(requiredSections = setOf(LockSection.TASKS)),
                setOf(LockSection.MEMORIES),
            ),
        )
    }

    @Test
    fun topLevelSettingsAndRestrictedCaptureDropSelectedGrantsButTimedNavigationDoesNot() {
        val current = LockBrowseContext("timeline", "timeline-list", setOf(LockSection.MEMORIES))
        val grants = setOf(LockSection.MEMORIES, LockSection.JOURNAL)
        val topLevel = LockBrowseContext("today", "today-list")

        assertEquals(
            NavigationGrantRevocation(sections = grants),
            navigationGrantRevocation(RelockMode.EVERY_TIME, current, topLevel, grants),
        )
        assertEquals(
            NavigationGrantRevocation(sections = grants),
            navigationGrantRevocation(RelockMode.EVERY_TIME, current, current.copy(isSettings = true), grants),
        )
        assertEquals(
            NavigationGrantRevocation(sections = grants),
            navigationGrantRevocation(RelockMode.EVERY_TIME, current, current.copy(isRestrictedCapture = true), grants),
        )
        assertEquals(
            NavigationGrantRevocation(),
            navigationGrantRevocation(RelockMode.AFTER_5_MINUTES, current, topLevel, grants),
        )
    }
}
