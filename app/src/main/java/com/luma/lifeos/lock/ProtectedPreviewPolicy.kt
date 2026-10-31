package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.widget.WidgetKind

/** External surfaces never inherit an in-app unlock grant. */
object ProtectedPreviewPolicy {
    /** An unreadable policy fails closed; a readable, absent policy means App Lock is disabled. */
    fun redact(
        policy: LockPolicy?,
        section: LockSection,
        policyReadable: Boolean = true,
    ): Boolean {
        if (!policyReadable) return true
        if (policy?.enabled != true) return false
        return policy.coverage == LockCoverage.WHOLE_APP || section in policy.sections
    }

    fun widgetSection(kind: WidgetKind): LockSection = when (kind) {
        WidgetKind.HABITS -> LockSection.HABITS
        WidgetKind.TASKS -> LockSection.TASKS
    }

    fun reminderSection(kind: String): LockSection? = when (kind) {
        "habit" -> LockSection.HABITS
        "task" -> LockSection.TASKS
        else -> null
    }
}
