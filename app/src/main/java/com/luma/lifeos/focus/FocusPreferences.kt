package com.luma.lifeos.focus

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/** Device-local shortcut choices. Package names are never included in backup. */
@SuppressLint("ApplySharedPref") // Callers need a durable success result before a selection is used.
class FocusPreferences private constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(DEFAULT_PREFERENCES, Context.MODE_PRIVATE),
    )

    internal constructor(context: Context, preferencesName: String) : this(
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE),
    )

    fun selectedPackages(): Set<String> = preferences.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    val allowedPackages: Flow<Set<String>> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(selectedPackages())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(selectedPackages())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setAllowedPackages(packages: Set<String>): Boolean = synchronized(preferences) {
        preferences.edit().putStringSet(KEY, packages.toSet()).commit()
    }

    /** Updates one choice against the latest persisted set so another window's changes are retained. */
    fun setPackageAllowed(packageName: String, allowed: Boolean): Boolean {
        if (packageName.isBlank()) return false
        return synchronized(preferences) {
            val current = selectedPackages()
            val updated = current.toMutableSet().apply {
                if (allowed) add(packageName) else remove(packageName)
            }
            if (updated == current) true else preferences.edit().putStringSet(KEY, updated).commit()
        }
    }

    fun markEmergencyExit(sessionId: UUID): Boolean =
        preferences.edit().putString(EMERGENCY_SESSION_KEY, sessionId.toString()).commit()

    fun emergencyExitSessionId(): UUID? =
        preferences.getString(EMERGENCY_SESSION_KEY, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun clearEmergencyExit(sessionId: UUID): Boolean {
        if (emergencyExitSessionId() != sessionId) return true
        return preferences.edit().remove(EMERGENCY_SESSION_KEY).commit()
    }

    private companion object {
        const val DEFAULT_PREFERENCES = "focus_preferences"
        const val KEY = "allowed_packages"
        const val EMERGENCY_SESSION_KEY = "emergency_exit_session"
    }
}
