package com.luma.lifeos.domain.lock

import com.luma.lifeos.domain.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

class LockPolicyTest {
    @Test
    fun coverageOffersOnlyWholeAppAndMemorySectionWithDerivedClassifications() {
        assertEquals(
            setOf(LockCoverage.WHOLE_APP, LockCoverage.MEMORY_SECTION),
            LockCoverage.entries.toSet(),
        )
        assertEquals(LockCoverage.WHOLE_APP, LockPolicy().coverage)
        assertEquals(emptySet<LockSection>(), LockPolicy(enabled = true).sections)
        assertEquals(
            setOf(LockSection.MEMORIES),
            LockPolicy(enabled = true, coverage = LockCoverage.MEMORY_SECTION).sections,
        )
    }

    @Test
    fun memoryCoverageAllowsPublicEditorCreatesAndRequiresMemoryGrantForPrivateCreate() {
        val lockedMemory = LockSnapshot(
            loadStatus = LockLoadStatus.READY,
            policy = LockPolicy(enabled = true, coverage = LockCoverage.MEMORY_SECTION, generation = 4),
            sessionGeneration = 7,
        )
        fun editorCreate(sections: Set<LockSection>, id: String? = "new-record", sessionId: String? = null) =
            LockRequest(
                operation = LockOperation.CREATE_IN_EDITOR,
                requiredSections = sections,
                contentClassification = if (sections.isEmpty()) {
                    LockContentClassification.KNOWN_UNPROTECTED
                } else {
                    LockContentClassification.CLASSIFIED
                },
                objectId = id,
                createSessionId = sessionId,
            )

        assertEquals(AccessDecision.Allow, lockedMemory.decide(editorCreate(setOf(LockSection.TASKS))))
        assertEquals(AccessDecision.Allow, lockedMemory.decide(editorCreate(setOf(LockSection.HABITS))))
        assertEquals(AccessDecision.Allow, lockedMemory.decide(editorCreate(setOf(LockSection.MOOD))))
        assertEquals(
            AccessDecision.NeedUnlock(setOf(LockSection.MEMORIES), wholeApp = false),
            lockedMemory.decide(editorCreate(setOf(LockSection.MEMORIES))),
        )
        assertEquals(
            AccessDecision.NeedUnlock(setOf(LockSection.MEMORIES), wholeApp = false),
            lockedMemory.decide(editorCreate(setOf(LockSection.MOOD, LockSection.MEMORIES))),
        )
        assertEquals(
            AccessDecision.Deny(AccessDenial.INVALID_CREATE_SESSION),
            lockedMemory.decide(editorCreate(setOf(LockSection.MOOD), id = null)),
        )
        assertEquals(
            AccessDecision.Deny(AccessDenial.INVALID_CREATE_SESSION),
            lockedMemory.decide(editorCreate(setOf(LockSection.MOOD), sessionId = "restricted-session")),
        )

        val grantedMemory = lockedMemory.copy(
            grant = LockGrant(
                sections = setOf(LockSection.MEMORIES),
                policyGeneration = 4,
                sessionGeneration = 7,
            ),
        )
        assertEquals(
            AccessDecision.Allow,
            grantedMemory.decide(editorCreate(setOf(LockSection.MEMORIES))),
        )
        assertEquals(
            AccessDecision.Allow,
            lockedMemory.decide(
                LockRequest(
                    operation = LockOperation.CREATE,
                    requiredSections = emptySet(),
                    contentClassification = LockContentClassification.KNOWN_UNPROTECTED,
                    createSessionId = "validated-session",
                ),
            ),
        )
    }

    @Test
    fun fixtureProfilesHideMemoryAliasesAndWholeAppContent() {
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
    fun metadataClassificationMatchesLoadedRecordClassification() {
        AppLockFixture.records.mapNotNull { it.event }.forEach { record ->
            val metadata = LockRecordClassification(
                type = record.event.type,
                hasMemoryMarker = record.memory != null,
                hasPeople = record.people.isNotEmpty(),
            )
            assertEquals(
                ProtectedRecordClassifier.requiredSections(record),
                ProtectedRecordClassifier.requiredSections(metadata),
            )
            assertEquals(
                ProtectedRecordClassifier.peopleFieldSections(record),
                ProtectedRecordClassifier.peopleFieldSections(metadata),
            )
        }
        assertEquals(
            setOf(LockSection.MEMORIES, LockSection.MOOD),
            ProtectedRecordClassifier.requiredSections(
                LockRecordClassification(EventType.MOOD, hasMemoryMarker = true, hasPeople = false),
            ),
        )
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
    fun staleGrantGenerationDoesNotAuthorizeMemorySection() {
        val snapshot = LockSnapshot(
            loadStatus = LockLoadStatus.READY,
            policy = LockPolicy(true, LockCoverage.MEMORY_SECTION, generation = 3),
            grant = LockGrant(sections = setOf(LockSection.MEMORIES), policyGeneration = 2, sessionGeneration = 8),
            sessionGeneration = 8,
        )

        assertEquals(
            AccessDecision.NeedUnlock(setOf(LockSection.MEMORIES), wholeApp = false),
            snapshot.decide(LockRequest(LockOperation.READ, setOf(LockSection.MEMORIES))),
        )
    }
}
