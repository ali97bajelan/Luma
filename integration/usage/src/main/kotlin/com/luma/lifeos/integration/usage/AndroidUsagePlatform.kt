package com.luma.lifeos.integration.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.os.SystemClock
import android.provider.Settings
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.domain.usage.UsageClockAnchor
import java.time.Instant

internal class AndroidUsagePlatform(private val context: Context) : UsagePlatform {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val user = context.getSystemService(UserManager::class.java)
    override fun supported() = usage != null && appOps != null && user != null
    override fun unlocked() = user?.isUserUnlocked == true
    override fun clockAnchor(): UsageClockAnchor {
        // A missing/unreadable boot identity fails the import; do not guess across restarts.
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        return UsageClockAnchor(Instant.ofEpochMilli(System.currentTimeMillis()), SystemClock.elapsedRealtime(), boot)
    }
    @Suppress("DEPRECATION")
    override fun granted(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= 29) appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            else appOps?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_DEFAULT &&
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
    }
    override fun daily(range: TimeRange) = usage?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
        range.start.toEpochMilli(), range.endExclusive.toEpochMilli())?.map {
        NativeUsageRow(it.packageName, it.firstTimeStamp, it.lastTimeStamp, it.totalTimeInForeground)
    }
    @Suppress("DEPRECATION")
    override fun events(range: TimeRange): List<NativeUsageEvent>? {
        val events = usage?.queryEvents(range.start.toEpochMilli(), range.endExclusive.toEpochMilli()) ?: return null
        val result = mutableListOf<NativeUsageEvent>()
        val event = UsageEvents.Event()
        var count = 0
        while (events.hasNextEvent()) {
            check(++count <= 200_000) { "Usage event response is too large" }
            if (!events.getNextEvent(event)) break
            val kind = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> NativeUsageEventKind.RESUME
                UsageEvents.Event.MOVE_TO_BACKGROUND -> NativeUsageEventKind.PAUSE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> NativeUsageEventKind.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_SHOWN -> NativeUsageEventKind.LOCK
                UsageEvents.Event.DEVICE_SHUTDOWN -> NativeUsageEventKind.SHUTDOWN
                UsageEvents.Event.DEVICE_STARTUP -> NativeUsageEventKind.STARTUP
                else -> continue
            }
            result += NativeUsageEvent(event.timeStamp, kind, event.packageName, event.className)
        }
        return result
    }
    @Suppress("DEPRECATION")
    override fun label(packageName: String): String? {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        return context.packageManager.getApplicationLabel(info).toString()
    }
}

/** OEMs may omit the detail or general settings activity. Every launch has a safe fallback. */
fun openUsageAccessSettings(context: Context): Boolean {
    val intents = listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    for (intent in intents) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true }
        catch (_: android.content.ActivityNotFoundException) { }
        catch (_: SecurityException) { }
    }
    return false
}
