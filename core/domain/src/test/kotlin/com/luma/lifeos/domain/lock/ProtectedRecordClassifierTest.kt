package com.luma.lifeos.domain.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectedRecordClassifierTest {
    @Test
    fun eventRecordsMatchTheIndependentFixtureProtectionSets() {
        val expected = mapOf(
            "memory_private" to setOf(LockSection.MEMORIES),
            "journal_public" to setOf(LockSection.JOURNAL),
            "journal_marked" to setOf(LockSection.JOURNAL, LockSection.MEMORIES),
            "mood_public" to setOf(LockSection.MOOD),
            "mood_marked" to setOf(LockSection.MOOD, LockSection.MEMORIES),
            "journal_person_linked" to setOf(LockSection.JOURNAL),
            "health_activity" to setOf(LockSection.HEALTH_USAGE),
            "usage_event" to setOf(LockSection.HEALTH_USAGE),
        )

        expected.forEach { (alias, sections) ->
            val record = AppLockFixture.records.single { it.alias == alias }.event!!
            assertEquals(alias, sections, ProtectedRecordClassifier.requiredSections(record))
        }
    }

    @Test
    fun linkedIdentityFieldsRequirePeopleIndependentlyOfJournalOrMemoryProse() {
        val memory = AppLockFixture.records.single { it.alias == "memory_private" }.event!!
        val linkedJournal = AppLockFixture.records.single { it.alias == "journal_person_linked" }.event!!
        val ordinaryJournal = AppLockFixture.records.single { it.alias == "journal_public" }.event!!

        assertEquals(setOf(LockSection.MEMORIES), ProtectedRecordClassifier.requiredSections(memory))
        assertEquals(setOf(LockSection.PEOPLE), ProtectedRecordClassifier.peopleFieldSections(memory))
        assertEquals(setOf(LockSection.JOURNAL), ProtectedRecordClassifier.requiredSections(linkedJournal))
        assertEquals(setOf(LockSection.PEOPLE), ProtectedRecordClassifier.peopleFieldSections(linkedJournal))
        assertEquals(emptySet<LockSection>(), ProtectedRecordClassifier.peopleFieldSections(ordinaryJournal))
    }

    @Test
    fun nonEventFixtureKindsMapToTheirSectionAndPlacesStayUnprotected() {
        assertEquals(LockSection.TASKS, ProtectedRecordClassifier.taskSection())
        assertEquals(LockSection.HABITS, ProtectedRecordClassifier.habitSection())
        assertEquals(LockSection.PEOPLE, ProtectedRecordClassifier.personSection())
        assertEquals(LockSection.FOCUS, ProtectedRecordClassifier.focusSection())
        assertEquals(LockSection.HEALTH_USAGE, ProtectedRecordClassifier.healthAndUsageSection())
        assertEquals(null, ProtectedRecordClassifier.placeSection())
        assertEquals(LockContentClassification.KNOWN_UNPROTECTED, ProtectedRecordClassifier.placeClassification())
    }
}
