package com.luma.lifeos.integration.usage

import android.content.Context
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.domain.usage.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A narrow gateway allows pure tests of access gating and native-value conversion. */
interface UsagePlatform {
    fun supported(): Boolean
    fun unlocked(): Boolean
    fun granted(): Boolean
    fun daily(range: TimeRange): List<NativeUsageRow>?
    fun events(range: TimeRange): List<NativeUsageEvent>? = null
    fun label(packageName: String): String?
    fun clockAnchor(): UsageClockAnchor? = null
}
data class NativeUsageRow(val packageName: String, val startMillis: Long, val endMillis: Long, val durationMillis: Long)

class AndroidUsageSource(private val platform: UsagePlatform) : UsageSource {
    constructor(context: Context) : this(AndroidUsagePlatform(context.applicationContext))
    override suspend fun clockAnchor() = withContext(Dispatchers.IO) { platform.clockAnchor() }
    override suspend fun access(): UsageAccess = withContext(Dispatchers.IO) {
        when {
            !platform.supported() -> UsageAccess.UNAVAILABLE
            !platform.unlocked() -> UsageAccess.LOCKED
            !platform.granted() -> UsageAccess.REQUIRED
            else -> UsageAccess.GRANTED
        }
    }
    override suspend fun query(range: TimeRange): List<UsageBucket> = withContext(Dispatchers.IO) {
        val permission = access()
        if (permission != UsageAccess.GRANTED) throw UsageAccessException(permission)
        val rows = try { platform.daily(range) } catch (_: SecurityException) { throw UsageAccessException(UsageAccess.REQUIRED) }
            ?: if (!platform.unlocked()) throw UsageAccessException(UsageAccess.LOCKED)
                else throw IllegalStateException("No usable native usage response")
        check(rows.size <= 100_000) { "Usage response is too large" }
        rows.filterNot { it.startMillis == it.endMillis && it.durationMillis == 0L }.map { row ->
            val label = try { platform.label(row.packageName)?.trim()?.take(200)?.takeIf { it.isNotEmpty() } }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; null }
            UsageBucket(row.packageName, Instant.ofEpochMilli(row.startMillis), Instant.ofEpochMilli(row.endMillis), row.durationMillis, label)
        }
    }
    override suspend fun querySessions(range: TimeRange): UsageSessionBatch? = withContext(Dispatchers.IO) {
        val permission = access()
        if (permission != UsageAccess.GRANTED) throw UsageAccessException(permission)
        val events = try { platform.events(range) }
            catch (_: SecurityException) { throw UsageAccessException(UsageAccess.REQUIRED) }
            ?: if (!platform.unlocked()) throw UsageAccessException(UsageAccess.LOCKED) else return@withContext null
        check(events.size <= 200_000) { "Usage event response is too large" }
        val labels = mutableMapOf<String, String?>()
        val sessions = reconstructUsageSessions(events, range).map { session ->
            val label = labels.getOrPut(session.packageName) {
                try { platform.label(session.packageName)?.trim()?.take(200)?.takeIf { it.isNotEmpty() } }
                catch (failure: Exception) { if (failure is CancellationException) throw failure; null }
            }
            session.copy(appLabel = label)
        }
        // Android does not expose a retention boundary or guarantee complete event history.
        UsageSessionBatch(range, sessions, UsageHistory.PARTIAL)
    }
}
