package com.luma.lifeos.ui.usage

import com.luma.lifeos.domain.usage.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class UsageSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun optInImportAndDisableReflectDurableStateWithoutDuplicateUiActions() = runTest(dispatcher) {
        val integration = Integration(); val vm = UsageSettingsViewModel(integration); advanceUntilIdle()
        assertFalse(vm.state.value.current.enabled)
        vm.setEnabled(true); advanceUntilIdle(); assertTrue(vm.state.value.current.enabled)
        integration.gate = CompletableDeferred()
        vm.importNow(); runCurrent(); vm.importNow(); runCurrent()
        assertTrue(vm.state.value.busy); assertEquals(1, integration.imports)
        integration.gate!!.complete(Unit); advanceUntilIdle()
        assertFalse(vm.state.value.busy); assertEquals(Instant.parse("2026-10-11T12:00:00Z"), vm.state.value.current.lastSync)
        vm.setEnabled(false); advanceUntilIdle(); assertFalse(vm.state.value.current.enabled)
    }
    @Test fun failedActionCanRetryAndCancellationDoesNotBecomeAnError() = runTest(dispatcher) {
        val integration = Integration(); val vm = UsageSettingsViewModel(integration); advanceUntilIdle()
        integration.failure = IllegalStateException("Database unavailable")
        vm.setEnabled(true); advanceUntilIdle(); assertTrue(vm.state.value.actionError)
        integration.failure = null; vm.setEnabled(true); advanceUntilIdle(); assertFalse(vm.state.value.actionError)
        integration.failure = CancellationException("Stopped")
        vm.importNow(); advanceUntilIdle(); assertFalse(vm.state.value.actionError); assertFalse(vm.state.value.busy)
    }
    @Test fun dailyLoadingMissingDataAndFailuresAreDistinct() = runTest(dispatcher) {
        val day = LocalDate.parse("2026-10-11"); val zone = ZoneOffset.UTC
        val integration = Integration()
        assertEquals(listOf(UsageDayState.Loading, UsageDayState.Empty), observeUsageDay(integration, day, zone).take(2).toList())
        integration.dailyFailure = true
        assertEquals(listOf(UsageDayState.Loading, UsageDayState.Error), observeUsageDay(integration, day, zone).toList())
    }
    @Test fun optOutIsAvailableWhileAnImportIsWaiting() = runTest(dispatcher) {
        val integration = Integration(); val vm = UsageSettingsViewModel(integration); advanceUntilIdle()
        vm.setEnabled(true); advanceUntilIdle()
        integration.gate = CompletableDeferred()
        vm.importNow(); runCurrent()
        vm.setEnabled(false); runCurrent()
        val disabled = !vm.state.value.current.enabled
        integration.gate!!.complete(Unit); advanceUntilIdle()
        assertTrue("Opt-out was suppressed by the busy import", disabled)
        assertFalse(vm.state.value.busy)
    }
    private class Integration : UsageIntegration {
        val changes = MutableStateFlow(UsageImportState()); var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null; var imports = 0; var dailyFailure = false
        override fun observeState() = changes
        override fun observeDaily(date: LocalDate, zone: ZoneId): Flow<DailyUsageReport?> = flow { if (dailyFailure) error("Unavailable"); emit(null) }
        override suspend fun access() = UsageAccess.REQUIRED
        override suspend fun refreshAccess() { failure?.let { throw it } }
        override suspend fun setEnabled(enabled: Boolean) { failure?.let { throw it }; changes.value = changes.value.copy(enabled = enabled, status = if (enabled) UsageSyncStatus.READY else UsageSyncStatus.DISABLED) }
        override suspend fun importNow(): UsageSyncOutcome { failure?.let { throw it }; imports++; gate?.await(); changes.value = changes.value.copy(lastSync = Instant.parse("2026-10-11T12:00:00Z")); return UsageSyncOutcome.SUCCESS }
    }
}
