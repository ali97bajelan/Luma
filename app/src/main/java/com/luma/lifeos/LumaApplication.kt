package com.luma.lifeos

import android.app.Application
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.luma.lifeos.data.LumaStorage
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.AndroidFocusClock
import com.luma.lifeos.focus.FocusCoordinator
import com.luma.lifeos.focus.FocusPreferences
import com.luma.lifeos.focus.FocusRuntime
import com.luma.lifeos.ui.mood.FileMoodDraftStore
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.domain.usage.UsageSyncer
import com.luma.lifeos.integration.usage.AndroidUsageSource
import com.luma.lifeos.integration.usage.UsageWorkScheduler
import com.luma.lifeos.usage.UsageWorker
import com.luma.lifeos.ui.theme.THEME_KEY
import com.luma.lifeos.widget.WidgetRefreshCoordinator
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.luma.lifeos.lock.AndroidLocalLockStore
import com.luma.lifeos.lock.AndroidLockClock
import com.luma.lifeos.lock.AndroidLockCrypto
import com.luma.lifeos.lock.AppLockCoordinator
import com.luma.lifeos.lock.DefaultLockCredentialService
import com.luma.lifeos.lock.LockActionGuard
import com.luma.lifeos.lock.ProtectedContentGateway
import com.luma.lifeos.lock.ProtectedCalendarRepository
import com.luma.lifeos.lock.ProtectedDailySummaryRepository
import com.luma.lifeos.lock.ProtectedFocusRepository
import com.luma.lifeos.lock.ProtectedHabitRepository
import com.luma.lifeos.lock.ProtectedJournalRepository
import com.luma.lifeos.lock.ProtectedLifeEventRepository
import com.luma.lifeos.lock.ProtectedMemoryRepository
import com.luma.lifeos.lock.ProtectedMoodRepository
import com.luma.lifeos.lock.ProtectedPersonRepository
import com.luma.lifeos.lock.ProtectedPhotoRepository
import com.luma.lifeos.lock.ProtectedSearchRepository
import com.luma.lifeos.lock.ProtectedTimelineRepository
import com.luma.lifeos.lock.ProtectedTodoRepository
import com.luma.lifeos.lock.ProtectedWeeklyReviewRepository
import com.luma.lifeos.lock.ProtectedUsageIntegration

class LumaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleHandler = Handler(Looper.getMainLooper())
    private val startedActivities = mutableSetOf<Activity>()
    private var activityVisibilityUpdate: Runnable? = null
    private val themePreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == THEME_KEY || key == null) {
            WidgetRefreshCoordinator.request(this)
            WidgetRefreshCoordinator.refreshQuickCapture(this)
        }
    }

    // Construction is lazy; the database opens off the main thread on the first suspend/Flow query.
    val appDataReset: com.luma.lifeos.ui.privacy.AppDataReset by lazy {
        com.luma.lifeos.ui.privacy.AndroidAppDataReset(this, focusRuntime)
    }
    val storage: LumaStorage by lazy { LumaStorage(this) }
    private val lockCrypto: AndroidLockCrypto by lazy { AndroidLockCrypto() }
    internal val localLockStore: AndroidLocalLockStore by lazy { AndroidLocalLockStore(this, lockCrypto) }
    val appLockCoordinator: AppLockCoordinator by lazy {
        AppLockCoordinator {
            withContext(Dispatchers.IO) { localLockStore.read()?.policy }
        }
    }
    val lockCredentialService: DefaultLockCredentialService by lazy {
        DefaultLockCredentialService(
            store = localLockStore,
            crypto = lockCrypto,
            clock = AndroidLockClock(),
            onSecurityStateChanged = appLockCoordinator::onPolicyChanged,
        )
    }
    val lockActionGuard: LockActionGuard by lazy {
        LockActionGuard(appLockCoordinator) {
            withContext(Dispatchers.IO) { localLockStore.read()?.policy }
        }
    }
    val protectedContentGateway: ProtectedContentGateway by lazy {
        ProtectedContentGateway(appLockCoordinator, lockActionGuard) { id -> storage.timeline.get(id) }
    }
    val protectedTimeline by lazy { ProtectedTimelineRepository(storage.timeline, protectedContentGateway) }
    val protectedLifeEvents by lazy {
        ProtectedLifeEventRepository(storage.lifeEvents, protectedTimeline, protectedContentGateway)
    }
    val protectedSearch by lazy { ProtectedSearchRepository(storage.search, protectedContentGateway) }
    val protectedMoods by lazy { ProtectedMoodRepository(storage.moods, protectedContentGateway) }
    val protectedJournals by lazy { ProtectedJournalRepository(storage.journals, protectedContentGateway) }
    val protectedMemories by lazy { ProtectedMemoryRepository(storage.memories, protectedContentGateway) }
    val protectedPeople by lazy { ProtectedPersonRepository(storage.people, protectedContentGateway) }
    val protectedPhotos by lazy { ProtectedPhotoRepository(storage.photos, protectedContentGateway) }
    val protectedTodos by lazy { ProtectedTodoRepository(storage.todos, protectedContentGateway) }
    val protectedHabits by lazy { ProtectedHabitRepository(storage.habits, protectedContentGateway) }
    val protectedCalendar by lazy { ProtectedCalendarRepository(storage.calendar, protectedContentGateway) }
    val protectedSummaries by lazy { ProtectedDailySummaryRepository(storage.summaries, protectedContentGateway) }
    val protectedReviews by lazy { ProtectedWeeklyReviewRepository(storage.reviews, protectedContentGateway) }
    val protectedFocusRepository by lazy { ProtectedFocusRepository(storage.focus, protectedContentGateway) }
    val protectedUsage by lazy { ProtectedUsageIntegration(usage, protectedContentGateway) }
    val focusPreferences: FocusPreferences by lazy { FocusPreferences(this) }
    val allowedFocusApps: AllowedFocusApps by lazy { AllowedFocusApps(this) }
    val focusRuntime: FocusRuntime by lazy {
        FocusRuntime(
            coordinator = FocusCoordinator(storage.focus, AndroidFocusClock(this)),
            preferences = focusPreferences,
            allowedApps = allowedFocusApps,
            scope = applicationScope,
        )
    }
    val moodDraftStore: FileMoodDraftStore by lazy { FileMoodDraftStore(this) }
    val usage: UsageIntegration by lazy {
        UsageSyncer(AndroidUsageSource(this), storage.usage, UsageWorkScheduler(this, UsageWorker::class.java))
    }
    val health: com.luma.lifeos.domain.health.HealthIntegration by lazy {
        com.luma.lifeos.domain.health.HealthSyncer(com.luma.lifeos.integration.health.AndroidHealthSource(this), storage.health)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { appLockCoordinator.initialize() }
        focusRuntime.initialize()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) = focusRuntime.onActivityResumed(activity)
            override fun onActivityPaused(activity: android.app.Activity) = focusRuntime.onActivityPaused(activity)
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) {
                activityVisibilityUpdate?.let(lifecycleHandler::removeCallbacks)
                activityVisibilityUpdate = null
                startedActivities += activity
                publishVisibility()
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities -= activity
                scheduleVisibilityReconciliation()
            }
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (startedActivities.remove(activity)) scheduleVisibilityReconciliation()
            }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), receiverFlags())
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        getSharedPreferences("app_theme", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(themePreferenceListener)
        applicationScope.launch {
            storage.widgets.observeInvalidations().collect {
                WidgetRefreshCoordinator.request(this@LumaApplication)
                enqueueReminderReconciliation(this@LumaApplication)
            }
        }
        enqueueReminderReconciliation(this)
    }

    private fun scheduleVisibilityReconciliation() {
        activityVisibilityUpdate?.let(lifecycleHandler::removeCallbacks)
        activityVisibilityUpdate = Runnable {
            activityVisibilityUpdate = null
            publishVisibility()
        }.also { lifecycleHandler.postDelayed(it, ACTIVITY_RECREATION_GRACE_MS) }
    }

    private fun publishVisibility() {
        appLockCoordinator.onVisibleActivityCountChanged(
            count = startedActivities.size,
            elapsedMs = SystemClock.elapsedRealtime(),
            restrictedCapture = appLockCoordinator.snapshot.value.activeBrowseContext?.isRestrictedCapture == true,
        )
    }

    private fun receiverFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val keyguard = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && keyguard?.isDeviceLocked == true) {
                    appLockCoordinator.onDeviceLocked()
                }
            }
        }
    }

    private companion object {
        const val ACTIVITY_RECREATION_GRACE_MS = 120L
    }
}
