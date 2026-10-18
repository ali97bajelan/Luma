package com.luma.lifeos.ui.integrations

import com.luma.lifeos.domain.health.*
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.ui.health.refreshHealthOnOpen
import com.luma.lifeos.ui.usage.refreshUsageOnOpen
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ForegroundRefreshTest {
    @Test fun usageImportsOnlyEnabledEligibleStates() = runTest {
        val eligible = setOf(UsageSyncStatus.READY, UsageSyncStatus.NO_DATA,
            UsageSyncStatus.ERROR, UsageSyncStatus.INTERVAL_CHANGED)
        for (enabled in listOf(false, true)) for (status in UsageSyncStatus.entries) {
            val usage = RecordingUsage(UsageImportState(enabled = enabled, status = status))
            refreshUsageOnOpen(usage)
            assertEquals("enabled=$enabled status=$status", enabled && status in eligible, usage.imports == 1)
            assertEquals("refresh", usage.calls.first())
            assertEquals(1, usage.calls.count { it == "observe" })
        }
    }

    @Test fun healthImportsOnlyEnabledEligibleStates() = runTest {
        val eligible = setOf(HealthStatus.READY, HealthStatus.NO_DATA, HealthStatus.ERROR)
        for (enabled in listOf(false, true)) for (status in HealthStatus.entries) {
            val health = RecordingHealth(HealthState(enabled = enabled, status = status, importZone = ZoneId.of("UTC")))
            refreshHealthOnOpen(health)
            assertEquals("enabled=$enabled status=$status", enabled && status in eligible, health.imports == 1)
            assertEquals("refresh", health.calls.first())
            assertEquals(1, health.calls.count { it == "observe" })
        }
    }

    @Test fun permissionGrantAndRevocationUseStateAfterAccessRefresh() = runTest {
        val usage = RecordingUsage(UsageImportState(enabled = true, status = UsageSyncStatus.ACCESS_REQUIRED))
        val health = RecordingHealth(HealthState(enabled = true, status = HealthStatus.ACCESS_REQUIRED, importZone = ZoneId.of("UTC")))
        usage.afterRefresh = usage.state.value.copy(status = UsageSyncStatus.READY)
        health.afterRefresh = health.state.value.copy(status = HealthStatus.READY)
        refreshUsageOnOpen(usage)
        refreshHealthOnOpen(health)
        assertEquals(listOf("refresh", "observe", "import"), usage.calls)
        assertEquals(listOf("refresh", "observe", "import"), health.calls)

        usage.afterRefresh = usage.state.value.copy(status = UsageSyncStatus.ACCESS_REQUIRED)
        health.afterRefresh = health.state.value.copy(status = HealthStatus.ACCESS_REQUIRED)
        refreshUsageOnOpen(usage)
        refreshHealthOnOpen(health)
        assertEquals(1, usage.imports)
        assertEquals(1, health.imports)
    }

    @Test fun importStateEmissionsDoNotTriggerAnotherImport() = runTest {
        val usage = RecordingUsage(UsageImportState(enabled = true, status = UsageSyncStatus.ERROR))
        val health = RecordingHealth(HealthState(enabled = true, status = HealthStatus.ERROR, importZone = ZoneId.of("UTC")))
        refreshUsageOnOpen(usage)
        refreshHealthOnOpen(health)
        assertEquals(1, usage.imports)
        assertEquals(1, health.imports)
        // A subsequent activation may retry, even when the last import failed.
        refreshUsageOnOpen(usage)
        refreshHealthOnOpen(health)
        assertEquals(2, usage.imports)
        assertEquals(2, health.imports)
    }

    @Test fun refreshFailuresAndCancellationPropagateWithoutImporting() = runTest {
        for (failure in listOf(IllegalStateException("storage"), CancellationException("paused"))) {
            val usage = RecordingUsage(UsageImportState(enabled = true, status = UsageSyncStatus.READY))
            val health = RecordingHealth(HealthState(enabled = true, status = HealthStatus.READY, importZone = ZoneId.of("UTC")))
            usage.failure = failure
            health.failure = failure
            try { refreshUsageOnOpen(usage); fail("Expected usage failure") }
            catch (actual: Exception) { assertSame(failure, actual) }
            try { refreshHealthOnOpen(health); fail("Expected health failure") }
            catch (actual: Exception) { assertSame(failure, actual) }
            assertEquals(0, usage.imports)
            assertEquals(0, health.imports)
        }
    }

    private class RecordingUsage(initial: UsageImportState) : UsageIntegration {
        val state = MutableStateFlow(initial)
        val calls = mutableListOf<String>()
        var imports = 0
        var afterRefresh: UsageImportState? = null
        var failure: Exception? = null
        override fun observeState(): Flow<UsageImportState> = flow { calls += "observe"; emitAll(state) }
        override fun observeDaily(date: LocalDate, zone: ZoneId): Flow<DailyUsageReport?> = flowOf(null)
        override suspend fun access() = error("Use refreshAccess")
        override suspend fun setEnabled(enabled: Boolean) = error("Must not change opt-in")
        override suspend fun refreshAccess() {
            calls += "refresh"
            failure?.let { throw it }
            afterRefresh?.let { state.value = it }
        }
        override suspend fun importNow(): UsageSyncOutcome {
            calls += "import"; imports++
            state.value = state.value.copy(status = UsageSyncStatus.ERROR)
            return UsageSyncOutcome.ERROR
        }
    }

    private class RecordingHealth(initial: HealthState) : HealthIntegration {
        val state = MutableStateFlow(initial)
        val calls = mutableListOf<String>()
        var imports = 0
        var afterRefresh: HealthState? = null
        var failure: Exception? = null
        override fun observeState(): Flow<HealthState> = flow { calls += "observe"; emitAll(state) }
        override suspend fun access() = error("Use refreshAccess")
        override suspend fun setEnabled(enabled: Boolean) = error("Must not change opt-in")
        override suspend fun refreshAccess() {
            calls += "refresh"
            failure?.let { throw it }
            afterRefresh?.let { state.value = it }
        }
        override suspend fun importNow(): HealthStatus {
            calls += "import"; imports++
            state.value = state.value.copy(status = HealthStatus.ERROR)
            return HealthStatus.ERROR
        }
    }
}
