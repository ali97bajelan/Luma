package com.luma.lifeos.focus

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable

/** Discovers launcher apps visible to the current user profile. */
class AllowedFocusApps internal constructor(private val platform: FocusAppPlatform) {
    constructor(context: Context) : this(AndroidFocusAppPlatform(context.applicationContext))

    data class App(val packageName: String, val label: String, val icon: Drawable? = null)

    enum class LaunchResult { LAUNCHED, NOT_SELECTED, UNAVAILABLE }

    fun discover(): List<App> = platform.launcherPackages()
        .asSequence()
        .distinct()
        .filterNot { it == platform.ownPackageName }
        .mapNotNull { packageName ->
            try {
                platform.resolveApp(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: Resources.NotFoundException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        .toList()

    /** Re-resolves a selected package immediately before starting its explicit launcher intent. */
    fun launch(packageName: String, selectedPackages: Set<String>): LaunchResult {
        if (packageName !in selectedPackages) return LaunchResult.NOT_SELECTED
        return try {
            if (platform.resolveApp(packageName) == null) return LaunchResult.UNAVAILABLE
            platform.launch(packageName)
            LaunchResult.LAUNCHED
        } catch (_: ActivityNotFoundException) {
            LaunchResult.UNAVAILABLE
        } catch (_: PackageManager.NameNotFoundException) {
            LaunchResult.UNAVAILABLE
        } catch (_: SecurityException) {
            LaunchResult.UNAVAILABLE
        } catch (_: Resources.NotFoundException) {
            LaunchResult.UNAVAILABLE
        }
    }
}

/** Narrow package access surface, kept injectable so package discovery and launch failures can be tested. */
internal interface FocusAppPlatform {
    val ownPackageName: String
    fun launcherPackages(): List<String>
    @Throws(PackageManager.NameNotFoundException::class)
    fun resolveApp(packageName: String): AllowedFocusApps.App?
    @Throws(ActivityNotFoundException::class, PackageManager.NameNotFoundException::class, SecurityException::class)
    fun launch(packageName: String)
}

private class AndroidFocusAppPlatform(private val context: Context) : FocusAppPlatform {
    private val packageManager: PackageManager get() = context.packageManager
    override val ownPackageName: String get() = context.packageName

    override fun launcherPackages(): List<String> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
    }

    override fun resolveApp(packageName: String): AllowedFocusApps.App? {
        if (packageManager.getLaunchIntentForPackage(packageName) == null) return null
        val info = packageManager.getApplicationInfo(packageName, 0)
        if (!info.enabled) return null
        val label = packageManager.getApplicationLabel(info).toString().trim().ifEmpty { packageName }
        return AllowedFocusApps.App(packageName, label, packageManager.getApplicationIcon(info))
    }

    override fun launch(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: throw ActivityNotFoundException("No launcher activity for selected package")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Adds package identity only when two visible apps share the same display label. */
internal fun AllowedFocusApps.App.displayName(allApps: List<AllowedFocusApps.App>): String {
    val hasCollision = allApps.count { it.label.equals(label, ignoreCase = true) } > 1
    return if (hasCollision) "$label · $packageName" else label
}
