package com.luma.lifeos.ui.privacy

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.luma.lifeos.reminders.ReminderPlatform

/** External OS boundary. True means request accepted, never independently confirmed erasure. */
fun interface AppDataReset {
    suspend fun requestReset(): Boolean
}

/** Android stops Luma and clears the entire app sandbox, including open stores and work state. */
class AndroidAppDataReset(context: Context) : AppDataReset {
    private val application = context.applicationContext
    override suspend fun requestReset(): Boolean {
        ReminderPlatform(application).cancelAll()
        return application.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() ?: false
    }
}

fun openLumaAppSettings(context: Context): Boolean = try {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
