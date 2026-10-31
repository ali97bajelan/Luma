package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.usage.DailyUsageReport
import com.luma.lifeos.domain.usage.UsageIntegration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Keeps trusted import controls while filtering the only UI-facing raw usage report flow. */
class ProtectedUsageIntegration(
    private val delegate: UsageIntegration,
    private val gateway: ProtectedContentGateway,
) : UsageIntegration by delegate {
    override fun observeDaily(date: java.time.LocalDate, zone: java.time.ZoneId): Flow<DailyUsageReport?> =
        combine(delegate.observeDaily(date, zone), gateway.snapshot) { report, state ->
            if (report == null || !gateway.filterSection(true, LockSection.HEALTH_USAGE, state)) null
            else report.copy(apps = immutableProjectionList(report.apps))
        }
}
