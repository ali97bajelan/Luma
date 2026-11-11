package com.luma.lifeos.calendar

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.luma.lifeos.LumaApplication
import java.io.IOException
import java.time.DayOfWeek
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

/** Owns the user's preferred first day for weekly review date ranges. */
@SuppressLint("ApplySharedPref")
class WeekStartPreferences internal constructor(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) {
    private val applicationContext = context.applicationContext
    private val preferences: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val writeMutex = Mutex()
    private val mutableSelection = MutableStateFlow(DEFAULT)
    private val mutableInitialized = MutableStateFlow(value = false)
    private val initialSelection = applicationScope.async(ioDispatcher) {
        try {
            DayOfWeek.valueOf(preferences.getString(SELECTED_DAY_KEY, null) ?: DEFAULT.name)
        } catch (_: Exception) {
            DEFAULT
        }
    }

    val selection: StateFlow<DayOfWeek> = mutableSelection.asStateFlow()

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

    /** Persist first, then publish; a failed write leaves the accepted value unchanged. */
    suspend fun select(value: DayOfWeek): Result<Unit> = withContext(NonCancellable) {
        writeMutex.withLock {
            try {
                val saved = withContext(ioDispatcher) {
                    if (!mutableInitialized.value) {
                        mutableSelection.value = initialSelection.await()
                        mutableInitialized.value = true
                    }
                    preferences.edit().putString(SELECTED_DAY_KEY, value.name).commit()
                }
                if (!saved) Result.failure(IOException("Could not persist the week start preference"))
                else {
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
        val DEFAULT: DayOfWeek = DayOfWeek.SATURDAY
        const val PREFERENCES_NAME = "app_calendar"
        const val SELECTED_DAY_KEY = "week_start_day"
    }
}

@Composable
fun rememberWeekStartPreferences(): WeekStartPreferences {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        val application = applicationContext as? LumaApplication
        application?.weekStartPreferences ?: WeekStartPreferences(applicationContext)
    }
}
