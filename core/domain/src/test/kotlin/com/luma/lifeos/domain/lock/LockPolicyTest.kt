package com.luma.lifeos.domain.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    @Test
    fun selectedCoverageRequiresASectionAndWholeAppCoverageStoresNone() {
        assertTrue(runCatching {
            LockPolicy(enabled = true, coverage = LockCoverage.SELECTED, sections = emptySet())
        }.exceptionOrNull() is IllegalArgumentException)

        assertTrue(runCatching {
            LockPolicy(enabled = true, coverage = LockCoverage.WHOLE_APP, sections = setOf(LockSection.MEMORIES))
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(emptySet<LockSection>(), LockPolicy(enabled = true, coverage = LockCoverage.WHOLE_APP).sections)
    }

    @Test
    fun fixtureProfilesHideExactlyTheirDeclaredRecordAliases() {
        AppLockFixture.profiles.forEach { profile ->
            val snapshot = LockSnapshot(
                loadStatus = LockLoadStatus.READY,
                policy = profile.policy,
                sessionGeneration = 1,
            )
            val actualHidden = AppLockFixture.records.mapNotNull { record ->
                val classification = if (record.knownUnprotected) {
                    LockContentClassification.KNOWN_UNPROTECTED
                } else {
                    LockContentClassification.CLASSIFIED
                }
                val decision = snapshot.decide(
                    LockRequest(
                        operation = LockOperation.READ,
                        requiredSections = record.requiredSections,
                        contentClassification = classification,
                    ),
                )
                record.alias.takeIf { decision !is AccessDecision.Allow }
            }.toSet()

            assertEquals("profile ${profile.name}", profile.hiddenAliases, actualHidden)
        }
    }

    @Test
    fun eachSelectedSectionMatchesTheFixtureAloneProfile() {
        LockSection.entries.forEach { section ->
            val snapshot = LockSnapshot(
                loadStatus = LockLoadStatus.READY,
                policy = LockPolicy(
                    enabled = true,
                    coverage = LockCoverage.SELECTED,
                    sections = setOf(section),
                    generation = 1,
                ),
                sessionGeneration = 1,
            )
            val actualHidden = AppLockFixture.records.mapNotNull { record ->
                val classification = if (record.knownUnprotected) {
                    LockContentClassification.KNOWN_UNPROTECTED
                } else LockContentClassification.CLASSIFIED
                val decision = snapshot.decide(
                    LockRequest(LockOperation.READ, record.requiredSections, classification),
                )
                record.alias.takeIf { decision !is AccessDecision.Allow }
            }.toSet()

            assertEquals("section $section", AppLockFixture.sectionOnlyHiddenAliases.getValue(section), actualHidden)
        }
    }

    @Test
    fun unknownAndErrorSnapshotsNeverAuthorizeFixtureContent() {
        val request = LockRequest(LockOperation.READ, setOf(LockSection.MEMORIES))
        assertEquals(AccessDecision.Deny(AccessDenial.INITIALIZING), LockSnapshot().decide(request))
        assertEquals(
            AccessDecision.Deny(AccessDenial.STORAGE_ERROR),
            LockSnapshot(loadStatus = LockLoadStatus.ERROR).decide(request),
        )
    }

    @Test
    fun staleGrantGenerationDoesNotAuthorizeASelectedSection() {
        val snapshot = LockSnapshot(
            loadStatus = LockLoadStatus.READY,
            policy = LockPolicy(true, LockCoverage.SELECTED, setOf(LockSection.MEMORIES), generation = 3),
            grant = LockGrant(sections = setOf(LockSection.MEMORIES), policyGeneration = 2, sessionGeneration = 8),
            sessionGeneration = 8,
        )

        assertEquals(
            AccessDecision.NeedUnlock(setOf(LockSection.MEMORIES), wholeApp = false),
            snapshot.decide(LockRequest(LockOperation.READ, setOf(LockSection.MEMORIES))),
        )
    }
}
