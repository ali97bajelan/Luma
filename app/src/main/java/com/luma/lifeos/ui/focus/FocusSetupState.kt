package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusTimerConfig
import com.luma.lifeos.domain.focus.FocusTimerMode
import com.luma.lifeos.domain.focus.MILLIS_PER_MINUTE
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class FocusPeriod { TODAY, THIS_WEEK, ALL_TIME }

enum class FocusSetupFailure { VALIDATION, IO }

/** Editable setup data plus the immutable Start payload while a write may still be ambiguous. */
data class FocusSetupState(
    val name: String = "",
    val tag: String = "",
    val timerMode: FocusTimerMode = FocusTimerMode.COUNT_UP,
    val timedMinutesText: String = "25",
    val period: FocusPeriod = FocusPeriod.TODAY,
    val viewingZoneId: String = ZoneId.systemDefault().id,
    val viewingDate: LocalDate = LocalDate.now(ZoneId.of(viewingZoneId)),
    val databaseGeneration: UUID? = null,
    val capturedStart: FocusStartInput? = null,
    val startFailure: FocusSetupFailure? = null,
    val unresolvedStartPending: Boolean = false,
    val repeatSourceSessionId: UUID? = null,
    val repeatRequestId: UUID? = null,
    val repeatLoading: Boolean = false,
    val repeatLoadFailed: Boolean = false,
    val repeatNeedsName: Boolean = false,
    val repeatNextStep: String? = null,
) {
    val isRepeatDraft: Boolean get() = repeatSourceSessionId != null && !repeatLoading && !repeatLoadFailed

    val normalizedInput: FocusStartInput?
        get() {
            val timer = when (timerMode) {
                FocusTimerMode.COUNT_UP -> FocusTimerConfig.countUp()
                FocusTimerMode.TIMED -> timedMinutesText.toIntOrNull()
                    ?.takeIf { it in 1..180 }
                    ?.let { FocusTimerConfig.timed(it) }
                    ?: return null
            }
            return runCatching {
                normalizeFocusStartInput(FocusStartInput(name, tag.takeIf(String::isNotEmpty), timer))
            }.getOrNull()
        }

    val canStart: Boolean
        get() = normalizedInput != null && capturedStart == null && !unresolvedStartPending
}

fun focusTerminalRequestKey(sessionId: UUID, generation: UUID?): String =
    "${generation ?: "unknown"}:$sessionId"

fun shouldShowFocusTerminal(
    sessionId: UUID,
    requestedSetupSessionId: String?,
    generation: UUID? = null,
): Boolean = focusTerminalRequestKey(sessionId, generation) != requestedSetupSessionId

/** Null selection explicitly means reports from the previous database/query must be hidden. */
fun focusInsightsQueryFor(
    setup: FocusSetupState,
    initialized: Boolean,
    generation: UUID?,
): FocusInsightsQuery? {
    if (!initialized || generation == null || setup.databaseGeneration != generation) return null
    val selection = focusInsightsSelection(setup)
    return FocusInsightsQuery(selection.currentRange, generation, selection.previousRange)
}

fun visibleFocusInsightsState(
    selected: FocusInsightsQuery?,
    observed: FocusInsightsState,
): FocusInsightsState = when {
    selected == null -> FocusInsightsState.Unselected
    observed.query == selected -> observed
    else -> FocusInsightsState.Loading(selected)
}

sealed interface FocusSetupEvent {
    data class NameChanged(val value: String) : FocusSetupEvent
    data class TagChanged(val value: String) : FocusSetupEvent
    data class TimerModeChanged(val value: FocusTimerMode) : FocusSetupEvent
    data class TimedMinutesChanged(val value: String) : FocusSetupEvent
    data class PeriodChanged(val value: FocusPeriod) : FocusSetupEvent
    data class ViewingContextChanged(val date: LocalDate, val zoneId: String) : FocusSetupEvent
    data class GenerationChanged(val value: UUID) : FocusSetupEvent
    data class RepeatRequested(val sessionId: UUID, val requestId: UUID) : FocusSetupEvent
    data class PrefillRequested(
        val sessionId: UUID,
        val requestId: UUID,
        val input: FocusStartInput,
        val nextStep: String?,
        val generation: UUID,
    ) : FocusSetupEvent
    data class RepeatLoadFailed(val requestId: UUID) : FocusSetupEvent
    data object StartAnotherRequested : FocusSetupEvent
    data class StartCaptured(val input: FocusStartInput) : FocusSetupEvent
    data object StartIoFailed : FocusSetupEvent
    data object StartValidationRejected : FocusSetupEvent
    data object StartSucceeded : FocusSetupEvent
    data object StartOutcomeReconciled : FocusSetupEvent
    data object PrivateContentLocked : FocusSetupEvent
}

fun reduceFocusSetupState(state: FocusSetupState, event: FocusSetupEvent): FocusSetupState = when (event) {
    is FocusSetupEvent.NameChanged -> state.copy(
        name = event.value,
        repeatNeedsName = state.repeatNeedsName && event.value.isBlank(),
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    is FocusSetupEvent.TagChanged -> state.copy(
        tag = event.value,
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    is FocusSetupEvent.TimerModeChanged -> state.copy(
        timerMode = event.value,
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    is FocusSetupEvent.TimedMinutesChanged -> state.copy(
        timedMinutesText = event.value.mapNotNull { character ->
            Character.digit(character, 10).takeIf { it >= 0 }?.digitToChar()
        }.take(3).joinToString(""),
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    is FocusSetupEvent.PeriodChanged -> state.copy(period = event.value)
    is FocusSetupEvent.ViewingContextChanged -> {
        require(runCatching { ZoneId.of(event.zoneId) }.isSuccess) { "Unknown time zone" }
        state.copy(viewingDate = event.date, viewingZoneId = event.zoneId)
    }
    is FocusSetupEvent.RepeatRequested -> state.copy(
        name = "",
        tag = "",
        timerMode = FocusTimerMode.COUNT_UP,
        timedMinutesText = "25",
        repeatSourceSessionId = event.sessionId,
        repeatRequestId = event.requestId,
        repeatLoading = true,
        repeatLoadFailed = false,
        repeatNeedsName = false,
        repeatNextStep = null,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    is FocusSetupEvent.PrefillRequested -> if (
        state.databaseGeneration != event.generation || state.repeatSourceSessionId != event.sessionId ||
            state.repeatRequestId != event.requestId
    ) state else state.copy(
        name = event.input.name,
        tag = event.input.tagName.orEmpty(),
        timerMode = event.input.timer.mode,
        timedMinutesText = event.input.timer.targetMillis?.div(MILLIS_PER_MINUTE)?.toString() ?: "25",
        repeatLoading = false,
        repeatRequestId = null,
        repeatLoadFailed = false,
        repeatNeedsName = event.input.name.isBlank(),
        repeatNextStep = event.nextStep,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    is FocusSetupEvent.RepeatLoadFailed -> if (state.repeatRequestId == event.requestId) state.copy(
        repeatLoading = false,
        repeatRequestId = null,
        repeatLoadFailed = true,
    ) else state
    FocusSetupEvent.StartAnotherRequested -> state.copy(
        name = "",
        tag = "",
        timerMode = FocusTimerMode.COUNT_UP,
        timedMinutesText = "25",
        repeatSourceSessionId = null,
        repeatRequestId = null,
        repeatLoading = false,
        repeatLoadFailed = false,
        repeatNeedsName = false,
        repeatNextStep = null,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    is FocusSetupEvent.GenerationChanged -> if (
        state.databaseGeneration != null && state.databaseGeneration != event.value
    ) {
        state.copy(
            name = "",
            tag = "",
            timerMode = FocusTimerMode.COUNT_UP,
            timedMinutesText = "25",
            databaseGeneration = event.value,
            capturedStart = null,
            startFailure = null,
            unresolvedStartPending = false,
            repeatSourceSessionId = null,
            repeatRequestId = null,
            repeatLoading = false,
            repeatLoadFailed = false,
            repeatNeedsName = false,
            repeatNextStep = null,
        )
    } else {
        state.copy(databaseGeneration = event.value)
    }
    is FocusSetupEvent.StartCaptured -> {
        val normalized = normalizeFocusStartInput(event.input)
        if (!state.canStart || state.normalizedInput != normalized) state else state.copy(
            capturedStart = normalized,
            startFailure = null,
            unresolvedStartPending = true,
        )
    }
    FocusSetupEvent.StartIoFailed -> if (state.capturedStart == null) state else state.copy(
        startFailure = FocusSetupFailure.IO,
    )
    FocusSetupEvent.StartValidationRejected -> state.copy(
        capturedStart = null,
        startFailure = FocusSetupFailure.VALIDATION,
        unresolvedStartPending = false,
    )
    FocusSetupEvent.StartSucceeded -> state.copy(
        name = "",
        tag = "",
        timerMode = FocusTimerMode.COUNT_UP,
        timedMinutesText = "25",
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
        repeatSourceSessionId = null,
        repeatRequestId = null,
        repeatLoading = false,
        repeatLoadFailed = false,
        repeatNeedsName = false,
        repeatNextStep = null,
    )
    FocusSetupEvent.StartOutcomeReconciled -> state.copy(
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    FocusSetupEvent.PrivateContentLocked -> state.copy(
        name = "",
        tag = "",
        timerMode = FocusTimerMode.COUNT_UP,
        timedMinutesText = "25",
        unresolvedStartPending = state.unresolvedStartPending || state.capturedStart != null,
        capturedStart = null,
        startFailure = null,
        repeatSourceSessionId = null,
        repeatRequestId = null,
        repeatLoading = false,
        repeatLoadFailed = false,
        repeatNeedsName = false,
        repeatNextStep = null,
    )
}

/** Primitive-only representation suitable for SavedStateHandle and Android Bundle restoration. */
fun encodeFocusSetupState(state: FocusSetupState): Map<String, Any?> = mapOf(
    KEY_NAME to state.name,
    KEY_TAG to state.tag,
    KEY_TIMER_MODE to state.timerMode.name,
    KEY_TIMER_MINUTES to state.timedMinutesText,
    KEY_PERIOD to state.period.name,
    KEY_ZONE to state.viewingZoneId,
    KEY_GENERATION to state.databaseGeneration?.toString(),
    KEY_REPEAT_SOURCE to state.repeatSourceSessionId?.takeUnless { state.repeatLoading }?.toString(),
    KEY_REPEAT_FAILED to state.repeatLoadFailed,
    KEY_REPEAT_NEEDS_NAME to state.repeatNeedsName,
    KEY_REPEAT_NEXT_STEP to state.repeatNextStep,
)

/** Restore user drafts and selection while using the current local date after process recreation. */
fun restoreFocusSetupState(
    saved: Map<String, Any?>,
    defaultZoneId: String = ZoneId.systemDefault().id,
    today: LocalDate = LocalDate.now(ZoneId.of(defaultZoneId)),
): FocusSetupState {
    val zone = (saved[KEY_ZONE] as? String)
        ?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
        ?: defaultZoneId.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
        ?: ZoneId.systemDefault().id
    val date = (saved[KEY_DATE] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    return FocusSetupState(
        name = saved[KEY_NAME] as? String ?: "",
        tag = saved[KEY_TAG] as? String ?: "",
        timerMode = (saved[KEY_TIMER_MODE] as? String)?.let { value ->
            FocusTimerMode.entries.firstOrNull { it.name == value }
        } ?: FocusTimerMode.COUNT_UP,
        timedMinutesText = (saved[KEY_TIMER_MINUTES] as? String)
            ?.takeIf { it.length <= 3 && it.all(Char::isDigit) } ?: "25",
        period = (saved[KEY_PERIOD] as? String)?.let { value ->
            FocusPeriod.entries.firstOrNull { it.name == value }
        } ?: FocusPeriod.TODAY,
        viewingZoneId = zone,
        viewingDate = date,
        databaseGeneration = (saved[KEY_GENERATION] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        repeatSourceSessionId = (saved[KEY_REPEAT_SOURCE] as? String)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        },
        repeatLoadFailed = saved[KEY_REPEAT_FAILED] as? Boolean ?: false,
        repeatNeedsName = saved[KEY_REPEAT_NEEDS_NAME] as? Boolean ?: false,
        repeatNextStep = saved[KEY_REPEAT_NEXT_STEP] as? String,
    )
}

data class FocusInsightsSelection(
    val period: FocusPeriod,
    val date: LocalDate,
    val zone: ZoneId,
    val currentRange: TimeRange?,
    val previousRange: TimeRange?,
)

/** Build local-day and ISO-week windows at midnight in the selected zone, including DST changes. */
fun focusInsightsSelection(state: FocusSetupState): FocusInsightsSelection {
    val zone = ZoneId.of(state.viewingZoneId)
    val today = state.viewingDate
    val selection = when (state.period) {
        FocusPeriod.TODAY -> FocusInsightsSelection(
            period = state.period,
            date = today,
            zone = zone,
            currentRange = TimeRange.forDay(today, zone),
            previousRange = TimeRange.forDay(today.minusDays(1), zone),
        )
        FocusPeriod.THIS_WEEK -> {
            val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val currentEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
            val previousStart = startOfWeek.minusWeeks(1).atStartOfDay(zone).toInstant()
            val previousEnd = startOfWeek.atStartOfDay(zone).toInstant()
            FocusInsightsSelection(
                period = state.period,
                date = today,
                zone = zone,
                currentRange = TimeRange(startOfWeek.atStartOfDay(zone).toInstant(), currentEnd),
                previousRange = TimeRange(previousStart, previousEnd),
            )
        }
        FocusPeriod.ALL_TIME -> FocusInsightsSelection(
            period = state.period,
            date = today,
            zone = zone,
            currentRange = null,
            previousRange = null,
        )
    }
    return selection
}

private const val KEY_NAME = "focus.setup.name"
private const val KEY_TAG = "focus.setup.tag"
private const val KEY_TIMER_MODE = "focus.setup.timer_mode"
private const val KEY_TIMER_MINUTES = "focus.setup.timer_minutes"
private const val KEY_PERIOD = "focus.setup.period"
private const val KEY_ZONE = "focus.setup.zone"
private const val KEY_DATE = "focus.setup.date"
private const val KEY_GENERATION = "focus.setup.generation"
private const val KEY_REPEAT_SOURCE = "focus.setup.repeat_source"
private const val KEY_REPEAT_FAILED = "focus.setup.repeat_failed"
private const val KEY_REPEAT_NEEDS_NAME = "focus.setup.repeat_needs_name"
private const val KEY_REPEAT_NEXT_STEP = "focus.setup.repeat_next_step"
