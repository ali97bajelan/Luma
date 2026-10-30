package com.luma.lifeos.backup

import com.luma.lifeos.domain.backup.*
import com.luma.lifeos.ui.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.*
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private class Fake : BackupService {
        var exports = 0; var restores = 0; var closes = 0; var fail = false
        val prepared = object : PreparedBackup {
            override val preview = BackupPreview(5, 2, Instant.EPOCH)
            override suspend fun restore() { restores++; if (fail) throw IOException("Failure") }
            override fun close() { closes++ }
        }
        override suspend fun export(output: OutputStream) { exports++; if (fail) throw IOException("Failure"); output.write(123) }
        override suspend fun prepare(input: InputStream): PreparedBackup { if (fail) throw IOException("Failure"); return prepared }
    }
    @Test fun previewRequiresConfirmationAndCancelReleasesWithoutReplacement() = runTest(dispatcher) {
        val service = Fake(); val vm = BackupViewModel(service, dispatcher)
        vm.prepare { ByteArrayInputStream(byteArrayOf(1)) }; advanceUntilIdle()
        assertEquals(5L, vm.state.value.preview!!.eventCount); assertEquals(0, service.restores)
        vm.cancelRestore(); advanceUntilIdle()
        assertNull(vm.state.value.preview); assertEquals(1, service.closes); assertEquals(0, service.restores)
    }
    @Test fun confirmationRunsOnceAndFailureExposesRetryWithoutRetainingPreview() = runTest(dispatcher) {
        val service = Fake(); val vm = BackupViewModel(service, dispatcher)
        vm.prepare { ByteArrayInputStream(byteArrayOf(1)) }; advanceUntilIdle()
        service.fail = true
        vm.confirmRestore(); vm.confirmRestore(); advanceUntilIdle()
        assertEquals(1, service.restores); assertEquals(BackupMessage.RESTORE_FAILED, vm.state.value.message)
        assertFalse(vm.state.value.busy); assertNull(vm.state.value.preview)
    }
    @Test fun restoreCompletionCallbackRunsAfterSuccessAndFailure() = runTest(dispatcher) {
        val successfulService = Fake()
        val successfulVm = BackupViewModel(successfulService, dispatcher)
        var successes = 0
        var completions = 0
        successfulVm.prepare { ByteArrayInputStream(byteArrayOf(1)) }; advanceUntilIdle()
        successfulVm.confirmRestore(onRestored = { successes++ }, onFinished = { completions++ })
        advanceUntilIdle()
        assertEquals(1, successes)
        assertEquals(1, completions)

        val failedService = Fake()
        val failedVm = BackupViewModel(failedService, dispatcher)
        var failedSuccesses = 0
        failedVm.prepare { ByteArrayInputStream(byteArrayOf(1)) }; advanceUntilIdle()
        failedService.fail = true
        failedVm.confirmRestore(onRestored = { failedSuccesses++ }, onFinished = { completions++ })
        advanceUntilIdle()
        assertEquals(0, failedSuccesses)
        assertEquals(2, completions)
        assertEquals(BackupMessage.RESTORE_FAILED, failedVm.state.value.message)
    }

    @Test fun restoreSuccessCallbackRunsOnceAfterCommitAndNeverOnFailure() = runTest(dispatcher) {
        val successfulService = Fake()
        val successfulVm = BackupViewModel(successfulService, dispatcher)
        var successes = 0
        successfulVm.prepare { ByteArrayInputStream(byteArrayOf(1)) }
        advanceUntilIdle()
        assertEquals(0L, successfulVm.state.value.preview?.todoCount)
        successfulVm.confirmRestore { successes++ }
        successfulVm.confirmRestore { successes++ }
        advanceUntilIdle()
        assertEquals(1, successes)
        assertEquals(BackupMessage.RESTORED, successfulVm.state.value.message)

        val failedService = Fake()
        val failedVm = BackupViewModel(failedService, dispatcher)
        var failures = 0
        failedVm.prepare { ByteArrayInputStream(byteArrayOf(1)) }
        advanceUntilIdle()
        failedService.fail = true
        failedVm.confirmRestore { failures++ }
        advanceUntilIdle()
        assertEquals(0, failures)
        assertEquals(BackupMessage.RESTORE_FAILED, failedVm.state.value.message)
    }
    @Test fun duplicateExportIsIgnoredAndStreamsCloseOnFailure() = runTest(dispatcher) {
        val service = Fake().apply { fail = true }; val vm = BackupViewModel(service, dispatcher)
        var closed = false
        val stream = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        vm.export { stream }; vm.export { error("Duplicate") }; advanceUntilIdle()
        assertEquals(1, service.exports); assertTrue(closed)
        assertEquals(BackupMessage.EXPORT_FAILED, vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }
    @Test fun exportSuccessIsReportedOnlyAfterOutputCompletes() = runTest(dispatcher) {
        val vm = BackupViewModel(Fake(), dispatcher)
        vm.export { ByteArrayOutputStream() }; assertTrue(vm.state.value.busy); advanceUntilIdle()
        assertEquals(BackupMessage.EXPORTED, vm.state.value.message)
    }
    @Test fun invalidArchiveReportsErrorAndDoesNotRestore() = runTest(dispatcher) {
        val service = Fake().apply { fail = true }; val vm = BackupViewModel(service, dispatcher)
        vm.prepare { ByteArrayInputStream(byteArrayOf(1)) }; advanceUntilIdle()
        assertEquals(BackupMessage.INVALID_BACKUP, vm.state.value.message)
        assertNull(vm.state.value.preview); assertEquals(0, service.restores)
    }
}
