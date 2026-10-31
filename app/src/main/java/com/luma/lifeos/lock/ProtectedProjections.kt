package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.model.LifeEventRecord
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet

/** Immutable, generation-tagged content values. Consumers must discard a stale generation. */
data class ProtectedRecordProjection(
    val record: LifeEventRecord,
    val requiredSections: Set<LockSection>,
    val policyGeneration: Long,
    val sessionGeneration: Long,
)

class ProtectedPageProjection<T>(
    items: List<T>,
    val offset: Int,
    val total: Int,
    val policyGeneration: Long,
    val sessionGeneration: Long,
) {
    val items: List<T> = Collections.unmodifiableList(ArrayList(items))

    init {
        require(offset >= 0 && total >= 0)
        require(items.size <= (total - offset).coerceAtLeast(0))
    }
}

internal fun <T> immutableProjectionList(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

internal fun immutableProjectionSections(values: Set<LockSection>): Set<LockSection> =
    Collections.unmodifiableSet(LinkedHashSet(values))
