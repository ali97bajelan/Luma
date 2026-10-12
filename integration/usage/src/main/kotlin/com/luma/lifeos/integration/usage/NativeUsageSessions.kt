package com.luma.lifeos.integration.usage

import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.domain.usage.UsageSession
import java.time.Instant

enum class NativeUsageEventKind { RESUME, PAUSE, SCREEN_OFF, LOCK, SHUTDOWN, STARTUP }
data class NativeUsageEvent(val timeMillis: Long, val kind: NativeUsageEventKind,
    val packageName: String? = null, val activity: String? = null)

/** Missing openings are omitted; reboot without a close cannot establish an end. */
fun reconstructUsageSessions(events: List<NativeUsageEvent>, range: TimeRange): List<UsageSession> {
    data class Active(val start: Instant, val activities: MutableMap<String, Int>, var pausedClass: String? = null)
    val active = mutableMapOf<String, Active>()
    val result = mutableListOf<UsageSession>()
    fun close(pkg: String, end: Instant, ongoing: Boolean = false) {
        val visit = active.remove(pkg) ?: return
        if (end > visit.start) result += UsageSession(pkg, visit.start, end, ongoing = ongoing)
    }
    // Preserve native equal-time order. Activity handoffs can be continuous, while a
    // pause/reopen of the same class is a separate visit even at the same millisecond.
    // The public API has no activity instance ID: count same-class overlapping resumes.
    events.filter { Instant.ofEpochMilli(it.timeMillis) in range }.distinct().sortedBy { it.timeMillis }
        .groupBy { it.timeMillis }.forEach { (timeMillis, group) ->
            val time = Instant.ofEpochMilli(timeMillis)
            group.forEach { event ->
                val pkg = event.packageName?.takeIf { it.isNotBlank() }
                val activity = event.activity ?: ""
                when (event.kind) {
                    NativeUsageEventKind.RESUME -> if (pkg != null) {
                        val prior = active[pkg]
                        if (prior != null && prior.activities.isEmpty() && prior.pausedClass == activity) close(pkg, time)
                        val visit = active.getOrPut(pkg) { Active(time, mutableMapOf()) }
                        visit.activities[activity] = (visit.activities[activity] ?: 0) + 1
                        visit.pausedClass = null
                    }
                    NativeUsageEventKind.PAUSE -> if (pkg != null) {
                        val visit = active[pkg]
                        val count = visit?.activities?.get(activity)
                        if (visit != null && count != null) {
                            if (count == 1) visit.activities.remove(activity) else visit.activities[activity] = count - 1
                            if (visit.activities.isEmpty()) visit.pausedClass = activity
                        }
                    }
                    NativeUsageEventKind.SCREEN_OFF, NativeUsageEventKind.LOCK, NativeUsageEventKind.SHUTDOWN ->
                        active.keys.toList().forEach { close(it, time) }
                    NativeUsageEventKind.STARTUP -> active.clear()
                }
            }
            active.filterValues { it.activities.isEmpty() }.keys.toList().forEach { close(it, time) }
        }
    active.keys.toList().forEach { close(it, range.endExclusive, ongoing = true) }
    return result.sortedWith(compareBy<UsageSession> { it.start }.thenBy { it.packageName })
}
