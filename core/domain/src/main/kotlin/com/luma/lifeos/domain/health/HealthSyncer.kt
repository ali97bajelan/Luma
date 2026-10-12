package com.luma.lifeos.domain.health

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HealthSyncer(private val source: HealthSource, private val repository: HealthRepository,
    private val clock: Clock = Clock.systemUTC(), private val zone: () -> ZoneId = ZoneId::systemDefault) : HealthIntegration {
    private val imports = Mutex()
    private val controls = Mutex()
    private var activeRead: Deferred<List<StepObservation>>? = null
    override fun observeState() = repository.observeState()
    override suspend fun setEnabled(enabled: Boolean) {
        controls.withLock {
            repository.setEnabled(enabled, zone())
            if (!enabled) activeRead?.cancel(HealthReadStopped())
        }
        if (enabled) refreshAccess()
    }
    override suspend fun refreshAccess() = imports.withLock {
        val state = repository.state()
        if (!state.enabled) return@withLock
        try {
            val access = source.access()
            val status = if (access == HealthAccess.GRANTED && state.status in setOf(HealthStatus.READY, HealthStatus.NO_DATA, HealthStatus.ERROR))
                state.status else access.status()
            repository.recordStatus(status, state.revision)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            repository.recordStatus(if (failure is HealthAccessException) failure.access.status() else HealthStatus.ERROR, state.revision)
        }
    }
    override suspend fun importNow(): HealthStatus = imports.withLock {
        val state = repository.state()
        if (!state.enabled) return@withLock HealthStatus.DISABLED
        try {
            val access = source.access()
            if (access != HealthAccess.GRANTED) return@withLock record(access.status(), state)
            val now = Instant.ofEpochMilli(clock.millis())
            val windows = stepWindows(now, requireNotNull(state.importZone))
            val rows = read(windows, state)
            // Reject partial or conflicting responses: never clear data on a partial query failure.
            require(rows.size == windows.size && rows.map { it.window }.toSet() == windows.toSet())
            if (!unchanged(state)) return@withLock HealthStatus.DISABLED
            val after = source.access()
            if (after != HealthAccess.GRANTED) return@withLock record(after.status(), state)
            repository.commit(rows, now, state)
            if (rows.any { it.count != null }) HealthStatus.READY else HealthStatus.NO_DATA
        } catch (failure: Exception) {
            if (failure is HealthReadStopped) return@withLock HealthStatus.DISABLED
            if (failure is CancellationException) throw failure
            if (!unchanged(state) || failure is HealthImportChangedException) return@withLock HealthStatus.DISABLED
            record(if (failure is HealthAccessException) failure.access.status() else HealthStatus.ERROR, state)
        }
    }
    private suspend fun read(windows: List<StepWindow>, state: HealthState): List<StepObservation> = coroutineScope {
        val pending = async(start = CoroutineStart.LAZY) { source.query(windows) }
        try {
            controls.withLock {
                if (!unchanged(state)) { pending.cancel(); throw HealthImportChangedException() }
                activeRead = pending
                pending.start()
            }
            pending.await()
        } finally {
            withContext(NonCancellable) { controls.withLock { if (activeRead === pending) activeRead = null } }
        }
    }
    private suspend fun unchanged(expected: HealthState): Boolean = repository.state().let {
        it.enabled && it.revision == expected.revision && it.importZone == expected.importZone
    }
    private suspend fun record(status: HealthStatus, state: HealthState): HealthStatus {
        repository.recordStatus(status, state.revision)
        return if (unchanged(state)) status else HealthStatus.DISABLED
    }
    private class HealthReadStopped : CancellationException("Health reads disabled")
}
