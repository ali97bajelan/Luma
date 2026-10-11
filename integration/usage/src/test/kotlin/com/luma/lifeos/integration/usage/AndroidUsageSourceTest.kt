package com.luma.lifeos.integration.usage

import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AndroidUsageSourceTest {
    private val range = TimeRange(Instant.ofEpochMilli(10_000), Instant.ofEpochMilli(20_000))
    @Test fun accessChecksBlockLockedDeniedAndUnsupportedReads() = runBlocking {
        val native = Platform(); val source = AndroidUsageSource(native)
        native.unlocked = false; assertEquals(UsageAccess.LOCKED, source.access())
        assertTrue(runCatching { source.query(range) }.exceptionOrNull() is UsageAccessException)
        native.unlocked = true; native.granted = false; assertEquals(UsageAccess.REQUIRED, source.access())
        assertTrue(runCatching { source.query(range) }.isFailure)
        native.available = false; assertEquals(UsageAccess.UNAVAILABLE, source.access()); assertEquals(0, native.reads)
    }
    @Test fun nativeBoundsAndMillisecondsArePreservedIncludingExpandedQueryPeriods() = runBlocking {
        val native = Platform().apply { rows = listOf(NativeUsageRow("org.example.app", 1_000, 30_000, 5_125)) }
        val result = AndroidUsageSource(native).query(range).single()
        assertEquals(1_000L, result.start.toEpochMilli()); assertEquals(30_000L, result.end.toEpochMilli())
        assertEquals(5_125L, result.durationMillis); assertEquals("Example", result.appLabel)
        assertEquals(range, native.requested)
    }
    @Test fun missingLabelsFallBackWithoutLosingUsageAndEmptyDataIsNotZero() = runBlocking {
        val native = Platform().apply { rows = listOf(NativeUsageRow("org.example.app", 1_000, 30_000, 0)); labelFailure = true }
        val source = AndroidUsageSource(native)
        assertNull(source.query(range).single().appLabel)
        native.rows = emptyList(); assertTrue(source.query(range).isEmpty())
        native.rows = null
        assertTrue(runCatching { source.query(range) }.exceptionOrNull() is IllegalStateException)
    }
    @Test fun transientPlatformAccessFailurePropagatesAndAFollowingCheckCanRecover() = runBlocking {
        val native = Platform(); val source = AndroidUsageSource(native)
        native.accessFailure = true
        assertTrue(runCatching { source.access() }.exceptionOrNull() is IllegalStateException)
        native.accessFailure = false; assertEquals(UsageAccess.GRANTED, source.access())
    }
    @Test fun impossibleNativeCountersFailRatherThanClampingOrFabricatingValues() = runBlocking {
        val native = Platform(); val source = AndroidUsageSource(native)
        native.rows = listOf(NativeUsageRow("org.example.app", 1_000, 30_000, 30_000))
        assertTrue(runCatching { source.query(range) }.isFailure)
        native.rows = listOf(NativeUsageRow("org.example.app", 1_000, 1_000, 0))
        assertTrue(source.query(range).isEmpty())
        native.securityFailure = true
        assertEquals(UsageAccess.REQUIRED, (runCatching { source.query(range) }.exceptionOrNull() as UsageAccessException).access)
    }
    private class Platform : UsagePlatform {
        var available = true; var unlocked = true; var granted = true; var reads = 0
        var rows: List<NativeUsageRow>? = emptyList(); var requested: TimeRange? = null
        var labelFailure = false; var securityFailure = false; var accessFailure = false
        override fun supported() = available
        override fun unlocked() = unlocked
        override fun granted(): Boolean { if (accessFailure) error("Temporary AppOps failure"); return granted }
        override fun daily(range: TimeRange): List<NativeUsageRow>? { reads++; requested = range; if (securityFailure) throw SecurityException(); return rows }
        override fun label(packageName: String): String { if (labelFailure) throw IllegalStateException("Uninstalled"); return "Example" }
    }
}
