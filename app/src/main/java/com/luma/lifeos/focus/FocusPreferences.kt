package com.luma.lifeos.focus

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/** Device-local shortcut choices. Package names are never included in backup. */
class FocusPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("focus_preferences", Context.MODE_PRIVATE)

    fun selectedPackages(): Set<String> = preferences.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    val allowedPackages: Flow<Set<String>> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(selectedPackages())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(selectedPackages())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setAllowedPackages(packages: Set<String>): Boolean =
        preferences.edit().putStringSet(KEY, packages.toSet()).commit()

    fun markEmergencyExit(sessionId: UUID): Boolean =
        preferences.edit().putString(EMERGENCY_SESSION_KEY, sessionId.toString()).commit()

    fun emergencyExitSessionId(): UUID? =
        preferences.getString(EMERGENCY_SESSION_KEY, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun clearEmergencyExit(sessionId: UUID): Boolean {
        if (emergencyExitSessionId() != sessionId) return true
        return preferences.edit().remove(EMERGENCY_SESSION_KEY).commit()
    }

    private companion object {
        const val KEY = "allowed_packages"
        const val EMERGENCY_SESSION_KEY = "emergency_exit_session"
    }
}
