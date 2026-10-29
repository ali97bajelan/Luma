package com.luma.lifeos.focus

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Discovers launcher apps visible to the current user profile. */
class AllowedFocusApps(private val context: Context) {
    data class App(val packageName: String, val label: String, val icon: Drawable)

    enum class LaunchResult { LAUNCHED, NOT_SELECTED, UNAVAILABLE }

    fun discover(): List<App> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName && pm.getLaunchIntentForPackage(it) != null }
            .mapNotNull { packageName ->
                try {
                    val info = pm.getApplicationInfo(packageName, 0)
                    if (!info.enabled) null else App(packageName, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    /** Re-resolves an explicit launcher intent after checking the latest saved selection. */
    fun launch(packageName: String, selectedPackages: Set<String>): LaunchResult {
        if (packageName !in selectedPackages) return LaunchResult.NOT_SELECTED
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: SecurityException) {
            null
        } ?: return LaunchResult.UNAVAILABLE
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LaunchResult.LAUNCHED
        } catch (_: ActivityNotFoundException) {
            LaunchResult.UNAVAILABLE
        } catch (_: SecurityException) {
            LaunchResult.UNAVAILABLE
        }
    }
}
