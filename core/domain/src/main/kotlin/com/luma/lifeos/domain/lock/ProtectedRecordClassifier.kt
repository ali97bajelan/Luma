package com.luma.lifeos.domain.lock

import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEventRecord

/** Classification shared by projections and write guards. Linked People fields are separate. */
object ProtectedRecordClassifier {
    /** Returns every section that protects the complete event graph, including its photos. */
    fun requiredSections(record: LifeEventRecord): Set<LockSection> = when (record.event.type) {
        EventType.MEMORY -> setOf(LockSection.MEMORIES)
        EventType.JOURNAL -> if (record.memory != null) {
            setOf(LockSection.MEMORIES, LockSection.JOURNAL)
        } else {
            setOf(LockSection.JOURNAL)
        }
        EventType.MOOD -> if (record.memory != null) {
            setOf(LockSection.MEMORIES, LockSection.MOOD)
        } else {
            setOf(LockSection.MOOD)
        }
        EventType.ACTIVITY, EventType.APP_USAGE -> setOf(LockSection.HEALTH_USAGE)
    }

    /** People names, notes, relationship labels, and links are hidden independently of prose. */
    fun peopleFieldSections(record: LifeEventRecord): Set<LockSection> =
        if (record.people.isEmpty()) emptySet() else setOf(LockSection.PEOPLE)

    fun taskSection(): LockSection = LockSection.TASKS
    fun habitSection(): LockSection = LockSection.HABITS
    fun personSection(): LockSection = LockSection.PEOPLE
    fun focusSection(): LockSection = LockSection.FOCUS
    fun healthAndUsageSection(): LockSection = LockSection.HEALTH_USAGE

    /** Places are intentionally not covered by app-lock section policy. */
    fun placeSection(): LockSection? = null

    fun placeClassification(): LockContentClassification = LockContentClassification.KNOWN_UNPROTECTED
}
