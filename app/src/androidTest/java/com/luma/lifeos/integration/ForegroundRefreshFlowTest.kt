package com.luma.lifeos.integration

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.ui.health.HealthForegroundRefresh
import com.luma.lifeos.ui.usage.UsageForegroundRefresh
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ForegroundRefreshFlowTest {
    @get:Rule val compose = createComposeRule()
    private val owner = TestOwner()
    private val usage = RecordingUsage()
    private val health = RecordingHealth()
    private val content = mutableStateOf("Today")

    @Test fun importsOnResumeAndReturnButNotOnStartOrRecomposition() {
        mount()
        moveTo(Lifecycle.State.STARTED)
        assertImports(0, 0)
        moveTo(Lifecycle.State.RESUMED)
        assertImports(1, 1)
        compose.runOnIdle { content.value = "Timeline" }
        assertImports(1, 1)
        moveTo(Lifecycle.State.STARTED)
        moveTo(Lifecycle.State.RESUMED)
        assertImports(2, 2)
    }

    @Test fun grantingAccessImportsOnReturnWithoutManualSync() {
        usage.state.value = usage.state.value.copy(status = UsageSyncStatus.ACCESS_REQUIRED)
        health.state.value = health.state.value.copy(status = HealthStatus.ACCESS_REQUIRED)
        mount()
        moveTo(Lifecycle.State.RESUMED)
        assertImports(0, 0)
        moveTo(Lifecycle.State.STARTED)
        compose.runOnIdle {
            usage.state.value = usage.state.value.copy(status = UsageSyncStatus.READY)
            health.state.value = health.state.value.copy(status = HealthStatus.READY)
        }
        moveTo(Lifecycle.State.RESUMED)
        assertImports(1, 1)
    }

    @Test fun pausedImportsAreCancelledAndCanRestartOnReturn() {
        usage.gate = CompletableDeferred()
        health.gate = CompletableDeferred()
        mount()
        moveTo(Lifecycle.State.RESUMED)
        assertImports(1, 1)
        moveTo(Lifecycle.State.STARTED)
        compose.runOnIdle {
            assertEquals(1, usage.cancelled)
            assertEquals(1, health.cancelled)
            assertEquals(0, usage.completed)
            assertEquals(0, health.completed)
            usage.gate = null
            health.gate = null
        }
        moveTo(Lifecycle.State.RESUMED)
        assertImports(2, 2)
        compose.runOnIdle {
            assertEquals(1, usage.completed)
            assertEquals(1, health.completed)
        }
    }

    @Test fun blockedUsageDoesNotDelayHealthAndFailureWaitsForNextResume() {
        usage.gate = CompletableDeferred()
        mount()
        moveTo(Lifecycle.State.RESUMED)
        assertImports(1, 1)
        compose.runOnIdle { assertEquals(1, health.completed) }
        moveTo(Lifecycle.State.STARTED)
        compose.runOnIdle { usage.gate = null; usage.failImport = true }
        moveTo(Lifecycle.State.RESUMED)
        assertImports(2, 2)
        compose.runOnIdle { content.value = "Settings" }
        assertImports(2, 2)
        moveTo(Lifecycle.State.STARTED)
        compose.runOnIdle { usage.failImport = false }
        moveTo(Lifecycle.State.RESUMED)
        assertImports(3, 3)
        compose.runOnIdle { assertEquals(1, usage.completed) }
    }

    @Test fun blockedHealthDoesNotDelayUsage() {
        health.gate = CompletableDeferred()
        mount()
        moveTo(Lifecycle.State.RESUMED)
        assertImports(1, 1)
        compose.runOnIdle { assertEquals(1, usage.completed); assertEquals(0, health.completed) }
    }

    private fun mount() {
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                UsageForegroundRefresh(usage)
                HealthForegroundRefresh(health)
                Text(content.value)
            }
        }
    }

    private fun moveTo(state: Lifecycle.State) {
        compose.runOnIdle { owner.registry.currentState = state }
        compose.waitForIdle()
    }

    private fun assertImports(usageCount: Int, healthCount: Int) {
        compose.runOnIdle {
            assertEquals(usageCount, usage.imports)
            assertEquals(healthCount, health.imports)
        }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class RecordingUsage : UsageIntegration {
        val state = MutableStateFlow(UsageImportState(enabled = true, status = UsageSyncStatus.READY))
        var imports = 0
        var completed = 0
        var cancelled = 0
        var failImport = false
        var gate: CompletableDeferred<Unit>? = null
        override fun observeState() = state
        override fun observeDaily(date: LocalDate, zone: ZoneId): Flow<DailyUsageReport?> = flowOf(null)
        override suspend fun access() = UsageAccess.GRANTED
        override suspend fun refreshAccess() {}
        override suspend fun setEnabled(enabled: Boolean) = error("Unexpected preference change")
        override suspend fun importNow(): UsageSyncOutcome {
            imports++
            try {
                gate?.await()
                check(!failImport) { "Provider failed" }
                completed++
                return UsageSyncOutcome.SUCCESS
            } finally { if (!kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive) cancelled++ }
        }
    }

    private class RecordingHealth : HealthIntegration {
        val state = MutableStateFlow(HealthState(enabled = true, status = HealthStatus.READY, importZone = ZoneId.of("UTC")))
        var imports = 0
        var completed = 0
        var cancelled = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeState() = state
        override suspend fun access() = HealthAccess.GRANTED
        override suspend fun refreshAccess() {}
        override suspend fun setEnabled(enabled: Boolean) = error("Unexpected preference change")
        override suspend fun importNow(): HealthStatus {
            imports++
            try { gate?.await(); completed++; return HealthStatus.READY }
            finally { if (!kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive) cancelled++ }
        }
    }
}
