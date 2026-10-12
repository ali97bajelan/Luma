package com.luma.lifeos.integration.health

import com.luma.lifeos.domain.health.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assert.*

class AndroidHealthSourceTest {
    private val windows = stepWindows(Instant.parse("2026-10-12T12:00:00Z"), ZoneId.of("UTC"))
    private class Platform : HealthPlatform {
        var available = HealthAccess.GRANTED
        var total: Long? = 123
        var calls = 0
        var effect: suspend () -> Unit = {}
        override suspend fun access() = available
        override suspend fun steps(window: StepWindow): Long? { calls++; effect(); return total }
    }
    @Test fun exactNativeTotalsAndNullAreNotInvented() = runBlocking {
        val platform = Platform(); val source = AndroidHealthSource(platform)
        assertEquals(windows, source.query(windows).map { it.window })
        platform.total = null; assertTrue(source.query(windows).all { it.count == null })
        platform.total = 0; assertTrue(source.query(windows).all { it.count == 0L })
    }
    @Test fun unavailableAccessNeverQueries() = runBlocking {
        val platform = Platform(); val source = AndroidHealthSource(platform)
        for (access in HealthAccess.entries.filter { it != HealthAccess.GRANTED }) {
            platform.available = access; assertEquals(access, source.access())
            try { source.query(windows); fail() } catch (error: HealthAccessException) { assertEquals(access, error.access) }
        }
        assertEquals(0, platform.calls)
    }
    @Test fun midBatchRevocationReturnsNoPartialResult() = runBlocking {
        val platform = Platform(); platform.effect = { platform.available = HealthAccess.REQUIRED }
        try { AndroidHealthSource(platform).query(windows); fail() } catch (error: HealthAccessException) { assertEquals(HealthAccess.REQUIRED, error.access) }
        assertEquals(1, platform.calls)
    }
    @Test fun securityExceptionsMapToAccessRequired() = runBlocking {
        val platform = Platform(); platform.effect = { throw SecurityException() }
        try { AndroidHealthSource(platform).query(windows); fail() } catch (error: HealthAccessException) { assertEquals(HealthAccess.REQUIRED, error.access) }
    }
    @Test fun providerErrorsAndCancellationPropagate() = runBlocking {
        val platform = Platform(); platform.effect = { error("service failure") }
        try { AndroidHealthSource(platform).query(windows); fail() } catch (_: IllegalStateException) { }
        platform.effect = { throw CancellationException() }
        try { AndroidHealthSource(platform).query(windows); fail() } catch (_: CancellationException) { }
    }
    @Test fun optOutStopsRemainingNativeAggregateCalls() = runBlocking {
        val platform = Platform()
        val states = MutableStateFlow(HealthState())
        var commits = 0
        val store = object : HealthRepository {
            override fun observeState() = states
            override suspend fun state() = states.value
            override suspend fun setEnabled(enabled: Boolean, zone: ZoneId) {
                states.value = states.value.copy(enabled = enabled, importZone = zone, revision = states.value.revision + 1,
                    status = if (enabled) HealthStatus.READY else HealthStatus.DISABLED)
            }
            override suspend fun recordStatus(status: HealthStatus, revision: Long) {
                if (states.value.enabled && states.value.revision == revision) states.value = states.value.copy(status = status)
            }
            override suspend fun commit(rows: List<StepObservation>, syncedAt: Instant, expected: HealthState) { commits++ }
        }
        val sync = HealthSyncer(AndroidHealthSource(platform), store, Clock.fixed(windows.last().end, ZoneOffset.UTC)) { ZoneId.of("UTC") }
        sync.setEnabled(true)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        platform.effect = { entered.complete(Unit); release.await() }
        val importing = async { sync.importNow() }; withTimeout(2000) { entered.await() }
        sync.setEnabled(false); release.complete(Unit)
        assertEquals(HealthStatus.DISABLED, withTimeout(2000) { importing.await() })
        assertEquals(1, platform.calls); assertEquals(0, commits)
    }
}
