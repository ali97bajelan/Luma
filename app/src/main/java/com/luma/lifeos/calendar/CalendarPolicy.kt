package com.luma.lifeos.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.domain.calendar.CalendarMath
import com.luma.lifeos.domain.calendar.CalendarSystem
import com.luma.lifeos.domain.calendar.GregorianCalendarMath
import com.luma.lifeos.widget.WidgetRefreshCoordinator
import java.util.Locale
import java.time.DayOfWeek
import java.time.temporal.WeekFields

data class CalendarPolicy(
    val preference: CalendarPreference,
    val system: CalendarSystem,
    val displayLocale: Locale,
    val firstDay: DayOfWeek,
    val isReady: Boolean,
)

/** Persian calendars use Saturday-first grids; Gregorian grids follow the app display locale. */
fun resolveCalendarSystem(
    preference: CalendarPreference,
    primarySystemLocale: Locale?,
): CalendarSystem = when (preference) {
    CalendarPreference.PERSIAN -> CalendarSystem.PERSIAN
    CalendarPreference.GREGORIAN -> CalendarSystem.GREGORIAN
    CalendarPreference.SYSTEM -> {
        val primaryLanguage = primarySystemLocale
            ?.let { Locale.forLanguageTag(it.toLanguageTag()).language }
            ?.lowercase(Locale.ROOT)
        if (primaryLanguage == "fa") CalendarSystem.PERSIAN else CalendarSystem.GREGORIAN
    }
}

internal fun calendarMathFor(system: CalendarSystem): CalendarMath = when (system) {
    CalendarSystem.PERSIAN -> persianCalendarMath
    CalendarSystem.GREGORIAN -> GregorianCalendarMath
}

private val persianCalendarMath: CalendarMath by lazy { IcuPersianCalendarMath() }

@Composable
fun rememberCalendarPolicy(): CalendarPolicy =
    rememberCalendarPolicy(rememberCalendarPreferences())

@Composable
internal fun rememberCalendarPolicy(preferences: CalendarPreferences): CalendarPolicy {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preference by preferences.selection.collectAsStateWithLifecycle()
    val initialized by preferences.initialized.collectAsStateWithLifecycle()
    var resumeGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val primarySystemLocale = remember(context, resumeGeneration) {
        SystemCalendarLocale.primary(context)
    }
    val displayLocale = configuration.locales[0] ?: Locale.getDefault()

    return remember(preference, initialized, primarySystemLocale, displayLocale) {
        val system = resolveCalendarSystem(preference, primarySystemLocale)
        CalendarPolicy(
            preference = preference,
            system = system,
            displayLocale = displayLocale,
            firstDay = if (system == CalendarSystem.PERSIAN) {
                DayOfWeek.SATURDAY
            } else {
                WeekFields.of(displayLocale).firstDayOfWeek
            },
            isReady = initialized,
        )
    }
}

/** Observe effective preference changes once at the app root and refresh existing widgets. */
@Composable
internal fun CalendarPolicyWidgetRefreshObserver() {
    val context = LocalContext.current.applicationContext
    val policy = rememberCalendarPolicy()
    var lastSnapshot by remember {
        mutableStateOf<Pair<CalendarPreference, CalendarSystem>?>(null)
    }

    LaunchedEffect(policy.isReady, policy.preference, policy.system) {
        if (!policy.isReady) return@LaunchedEffect
        val current = policy.preference to policy.system
        if (lastSnapshot != null && lastSnapshot != current) {
            WidgetRefreshCoordinator.request(context)
        }
        lastSnapshot = current
    }
}
