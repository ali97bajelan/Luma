package com.luma.lifeos.privacy

import com.luma.lifeos.ui.privacy.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    // Only the external OS-erasure boundary is substituted; real confirmation state is tested.
    private class Reset : AppDataReset {
        var calls = 0; var completions = 0; var accepted = true; var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun requestReset(): Boolean {
            calls++; gate?.await(); failure?.let { throw it }; completions++; return accepted
        }
    }
    @Test fun openingOrCancellingConfirmationNeverRequestsDeletion() = runTest(dispatcher) {
        val reset = Reset(); val vm = PrivacyViewModel(reset, dispatcher)
        vm.confirmDelete(); advanceUntilIdle(); assertEquals(0, reset.calls)
        vm.requestDelete(); assertTrue(vm.state.value.confirming)
        vm.cancelDelete(); vm.confirmDelete(); advanceUntilIdle()
        assertFalse(vm.state.value.confirming); assertEquals(0, reset.calls)
    }
    @Test fun confirmedResetRunsOnceAndOnlyReportsRequestAccepted() = runTest(dispatcher) {
        val reset = Reset().apply { gate = CompletableDeferred() }; val vm = PrivacyViewModel(reset, dispatcher)
        vm.requestDelete(); vm.confirmDelete(); runCurrent()
        assertTrue(vm.state.value.busy); assertFalse(vm.state.value.confirming)
        vm.requestDelete(); vm.confirmDelete(); vm.cancelDelete(); runCurrent()
        assertEquals(1, reset.calls)
        reset.gate!!.complete(Unit); advanceUntilIdle()
        assertTrue(vm.state.value.requestAccepted); assertFalse(vm.state.value.actionError)
        vm.requestDelete(); vm.confirmDelete(); advanceUntilIdle(); assertEquals(1, reset.calls)
    }
    @Test fun rejectedResetAllowsNewExplicitConfirmationAndRetry() = runTest(dispatcher) {
        val reset = Reset().apply { accepted = false }; val vm = PrivacyViewModel(reset, dispatcher)
        vm.requestDelete(); vm.confirmDelete(); advanceUntilIdle()
        assertTrue(vm.state.value.actionError); assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.requestAccepted)
        vm.confirmDelete(); advanceUntilIdle(); assertEquals(1, reset.calls)
        reset.accepted = true; vm.requestDelete(); vm.confirmDelete(); advanceUntilIdle()
        assertEquals(2, reset.calls); assertTrue(vm.state.value.requestAccepted); assertFalse(vm.state.value.actionError)
    }
    @Test fun clearingViewModelDoesNotCancelAnExplicitlyConfirmedOsRequest() = runTest(dispatcher) {
        val reset = Reset().apply { gate = CompletableDeferred() }
        val vm = PrivacyViewModel(reset, dispatcher)
        val store = androidx.lifecycle.ViewModelStore().apply { put("privacy", vm) }
        vm.requestDelete(); vm.confirmDelete(); runCurrent()
        assertEquals(1, reset.calls); assertEquals(0, reset.completions)
        store.clear(); reset.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, reset.calls); assertEquals(1, reset.completions)
    }
    @Test fun nativeExceptionShowsRecoverableErrorWithoutAutomaticReplay() = runTest(dispatcher) {
        val reset = Reset().apply { failure = SecurityException("policy") }; val vm = PrivacyViewModel(reset, dispatcher)
        vm.requestDelete(); vm.confirmDelete(); advanceUntilIdle()
        assertTrue(vm.state.value.actionError); assertFalse(vm.state.value.requestAccepted)
        assertEquals(1, reset.calls)
        val recreated = PrivacyViewModel(reset, dispatcher)
        assertFalse(recreated.state.value.confirming)
        recreated.confirmDelete(); advanceUntilIdle(); assertEquals(1, reset.calls)
    }
}
