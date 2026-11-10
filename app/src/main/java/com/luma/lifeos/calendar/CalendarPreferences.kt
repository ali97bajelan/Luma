package com.luma.lifeos.calendar

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.luma.lifeos.LumaApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** User's app-wide calendar choice. SYSTEM is a preference mode, not a calendar chronology. */
enum class CalendarPreference(val storageValue: String) {
    SYSTEM("system"),
    PERSIAN("persian"),
    GREGORIAN("gregorian");

    companion object {
        fun fromStorage(value: String?): CalendarPreference =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/**
 * Owns the app-wide presentation preference. Preference reads and writes run on I/O, and the app
 * keeps one instance for its lifetime so every screen observes the same accepted value.
 */
@SuppressLint("ApplySharedPref")
class CalendarPreferences internal constructor(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) {
    private val applicationContext = context.applicationContext
    private val preferences: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val writeMutex = Mutex()
    private val mutableSelection = MutableStateFlow(CalendarPreference.SYSTEM)
    private val mutableInitialized = MutableStateFlow(value = false)
    private val initialSelection = applicationScope.async(ioDispatcher) {
        try {
            CalendarPreference.fromStorage(preferences.getString(SELECTED_CALENDAR_KEY, null))
        } catch (_: Exception) {
            CalendarPreference.SYSTEM
        }
    }

    val selection: StateFlow<CalendarPreference> = mutableSelection.asStateFlow()
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    init {
        applicationScope.launch(ioDispatcher) {
            writeMutex.withLock {
                if (!mutableInitialized.value) {
                    mutableSelection.value = initialSelection.await()
                    mutableInitialized.value = true
                }
            }
        }
    }

    /** Persist first, then publish; a failed write leaves the previously accepted value in place. */
    suspend fun select(value: CalendarPreference): Result<Unit> = withContext(NonCancellable) {
        writeMutex.withLock {
            try {
                val saved = withContext(ioDispatcher) {
                    if (!mutableInitialized.value) {
                        mutableSelection.value = initialSelection.await()
                        mutableInitialized.value = true
                    }
                    preferences.edit()
                        .putString(SELECTED_CALENDAR_KEY, value.storageValue)
                        .commit()
                }
                if (!saved) {
                    Result.failure(IOException("Could not persist the calendar preference"))
                } else {
                    mutableSelection.value = value
                    Result.success(Unit)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }

    companion object {
        const val PREFERENCES_NAME = "app_calendar"
        const val SELECTED_CALENDAR_KEY = "selected_calendar"
    }
}

@Composable
fun rememberCalendarPreferences(): CalendarPreferences {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        val application = applicationContext as? LumaApplication
        application?.calendarPreferences ?: CalendarPreferences(applicationContext)
    }
}
