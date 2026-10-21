package com.luma.lifeos.ui.health

import com.luma.lifeos.domain.health.*
import com.luma.lifeos.ui.integrations.IntegrationSettingsOperation
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HealthSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun close() { Dispatchers.resetMain() }
    @Test fun durableOptInAndDuplicateImportsAreControlledWhileOptOutRemainsAvailable() = runTest(dispatcher) {
        val integration = Integration(); val vm = HealthSettingsViewModel(integration); advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        vm.setEnabled(true); advanceUntilIdle(); assertTrue(vm.state.value.current.enabled)
        integration.gate = CompletableDeferred()
        vm.importNow(); runCurrent(); vm.importNow(); runCurrent()
        assertTrue(vm.state.value.busy); assertEquals(1, integration.imports)
        vm.setEnabled(false); runCurrent(); assertFalse(vm.state.value.current.enabled)
        integration.gate!!.complete(Unit); advanceUntilIdle(); assertFalse(vm.state.value.busy)
    }
    @Test fun errorsCanRetryAndCancellationIsNotDisplayedAsFailure() = runTest(dispatcher) {
        val integration = Integration(); val vm = HealthSettingsViewModel(integration); advanceUntilIdle()
        integration.failure = IllegalStateException(); vm.refresh(); advanceUntilIdle(); assertTrue(vm.state.value.errors.containsKey(IntegrationSettingsOperation.ACCESS_REFRESH))
        integration.failure = null; vm.refresh(); advanceUntilIdle(); assertFalse(vm.state.value.errors.containsKey(IntegrationSettingsOperation.ACCESS_REFRESH))
        integration.failure = CancellationException(); vm.importNow(); advanceUntilIdle()
        assertFalse(vm.state.value.errors.containsKey(IntegrationSettingsOperation.IMPORT)); assertFalse(vm.state.value.busy)
    }
    @Test fun failedStateObservationCanRestart() = runTest(dispatcher) {
        val integration = Integration(); integration.observationFailure = true
        val vm = HealthSettingsViewModel(integration); advanceUntilIdle(); assertTrue(vm.state.value.errors.containsKey(IntegrationSettingsOperation.OBSERVE))
        integration.observationFailure = false; vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.value.loading); assertFalse(vm.state.value.errors.containsKey(IntegrationSettingsOperation.OBSERVE))
    }
    private class Integration : HealthIntegration {
        val flow = MutableStateFlow(HealthState()); var failure: Exception? = null
        var observationFailure = false; var imports = 0; var gate: CompletableDeferred<Unit>? = null
        override fun observeState(): Flow<HealthState> = flow { if (observationFailure) error("storage"); emitAll(this@Integration.flow) }
        override suspend fun access() = HealthAccess.REQUIRED
        override suspend fun setEnabled(enabled: Boolean) {
            failure?.let { throw it }
            flow.value = flow.value.copy(enabled = enabled, importZone = ZoneId.of("UTC"), status = if (enabled) HealthStatus.READY else HealthStatus.DISABLED)
        }
        override suspend fun refreshAccess() { failure?.let { throw it } }
        override suspend fun importNow(): HealthStatus { failure?.let { throw it }; imports++; gate?.await(); return HealthStatus.READY }
    }
}
