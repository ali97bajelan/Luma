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

enum class FocusStartBlockReason {
    STARTING,
    UNRESOLVED_START,
    PREPARING_REPEAT,
    REQUIRED_NAME,
    INVALID_NAME,
    TASK_LOADING,
    TASK_CONFLICT,
    INVALID_DURATION,
    INVALID_TAG,
    INVALID_CONFIGURATION,
}

/** Editable setup data plus the immutable Start payload while a write may still be ambiguous. */
data class FocusSetupState(
    val name: String = "",
    val tag: String = "",
    val nameEditedSinceBlur: Boolean = false,
    val nameTouched: Boolean = false,
    val tagEditedSinceBlur: Boolean = false,
    val tagTouched: Boolean = false,
    val timedMinutesEditedSinceBlur: Boolean = false,
    val timedMinutesTouched: Boolean = false,
    val startAttempted: Boolean = false,
    val tagAreaExpanded: Boolean = false,
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
    val selectedTaskId: UUID? = null,
    val selectedTaskTitle: String? = null,
    val taskOrigin: Boolean = false,
    val taskSelectionLoading: Boolean = false,
    val taskSelectionConflict: Boolean = false,
    val taskSelectionRequestId: UUID? = null,
) {
    val isRepeatDraft: Boolean get() = repeatSourceSessionId != null && !repeatLoading && !repeatLoadFailed
    val showNameError: Boolean get() = nameTouched || startAttempted
    val showTagError: Boolean get() = tagTouched || startAttempted
    val showTimedMinutesError: Boolean get() = timedMinutesTouched || startAttempted

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
                normalizeFocusStartInput(
                    FocusStartInput(
                        name = name,
                        tagName = tag.takeIf(String::isNotEmpty),
                        timer = timer,
                        taskId = selectedTaskId,
                        taskOrigin = taskOrigin,
                    ),
                )
            }.getOrNull()
        }

    val startBlockReason: FocusStartBlockReason?
        get() = when {
            capturedStart != null && startFailure != FocusSetupFailure.IO -> FocusStartBlockReason.STARTING
            capturedStart != null || unresolvedStartPending -> FocusStartBlockReason.UNRESOLVED_START
            repeatLoading -> FocusStartBlockReason.PREPARING_REPEAT
            name.isBlank() -> FocusStartBlockReason.REQUIRED_NAME
            runCatching {
                normalizeFocusStartInput(FocusStartInput(name = name, tagName = null))
            }.isFailure -> FocusStartBlockReason.INVALID_NAME
            taskSelectionLoading -> FocusStartBlockReason.TASK_LOADING
            taskSelectionConflict -> FocusStartBlockReason.TASK_CONFLICT
            timerMode == FocusTimerMode.TIMED && timedMinutesText.toIntOrNull()?.let { it in 1..180 } != true ->
                FocusStartBlockReason.INVALID_DURATION
            tag.isNotEmpty() && runCatching {
                normalizeFocusStartInput(FocusStartInput(name = "Focus", tagName = tag))
            }.isFailure -> FocusStartBlockReason.INVALID_TAG
            normalizedInput == null -> FocusStartBlockReason.INVALID_CONFIGURATION
            else -> null
        }

    val canStart: Boolean
        get() = normalizedInput != null && capturedStart == null && !unresolvedStartPending &&
            !taskSelectionLoading && !taskSelectionConflict

    /** Whether replacing setup would discard user-entered values or an explicit task context. */
    val hasUserAuthoredDraft: Boolean
        get() = name.isNotEmpty() || tag.isNotEmpty() ||
            nameEditedSinceBlur || nameTouched || tagEditedSinceBlur || tagTouched ||
            timedMinutesText != "25" || timedMinutesEditedSinceBlur || timedMinutesTouched ||
            timerMode != FocusTimerMode.COUNT_UP || selectedTaskId != null || selectedTaskTitle != null ||
            taskOrigin || taskSelectionLoading || taskSelectionConflict ||
            capturedStart != null || unresolvedStartPending || startFailure != null ||
            repeatLoading || repeatSourceSessionId != null
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
    data object NameBlurred : FocusSetupEvent
    data object TagBlurred : FocusSetupEvent
    data object TimedMinutesBlurred : FocusSetupEvent
    data class TagAreaExpandedChanged(val expanded: Boolean) : FocusSetupEvent
    data object StartAttempted : FocusSetupEvent
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
        val taskTitle: String? = null,
    ) : FocusSetupEvent
    data class TaskSelectionRequested(val taskId: UUID, val requestId: UUID) : FocusSetupEvent
    data class TaskSelectionLoaded(
        val taskId: UUID,
        val requestId: UUID,
        val title: String?,
        val isOpen: Boolean,
    ) : FocusSetupEvent
    data class TaskSelectionLoadFailed(val taskId: UUID, val requestId: UUID) : FocusSetupEvent
    data object TaskSelectionConflict : FocusSetupEvent
    data object TaskSelectionCleared : FocusSetupEvent
    data object TaskContentLocked : FocusSetupEvent
    data class RepeatLoadFailed(val requestId: UUID) : FocusSetupEvent
    data class RepeatSourceMissing(val requestId: UUID) : FocusSetupEvent
    data object StartAnotherRequested : FocusSetupEvent
    data class StartCaptured(val input: FocusStartInput) : FocusSetupEvent
    data object StartIoFailed : FocusSetupEvent
    data object StartValidationRejected : FocusSetupEvent
    data object StartSucceeded : FocusSetupEvent
    data object StartOutcomeReconciled : FocusSetupEvent
    data object PrivateContentLocked : FocusSetupEvent
}

private fun isValidFocusName(value: String): Boolean =
    runCatching { normalizeFocusStartInput(FocusStartInput(name = value, tagName = null)) }.isSuccess

private fun isValidFocusTag(value: String?): Boolean =
    value.isNullOrBlank() || runCatching {
        normalizeFocusStartInput(FocusStartInput(name = "Focus", tagName = value))
    }.isSuccess

private fun isValidFocusTimer(value: FocusTimerConfig): Boolean =
    runCatching { normalizeFocusStartInput(FocusStartInput(name = "Focus", tagName = null, timer = value)) }.isSuccess

fun reduceFocusSetupState(state: FocusSetupState, event: FocusSetupEvent): FocusSetupState = when (event) {
    is FocusSetupEvent.NameChanged -> state.copy(
        name = event.value,
        nameEditedSinceBlur = true,
        repeatNeedsName = state.repeatNeedsName && event.value.isBlank(),
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    is FocusSetupEvent.TagChanged -> state.copy(
        tag = event.value,
        tagEditedSinceBlur = true,
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
        timedMinutesEditedSinceBlur = true,
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    FocusSetupEvent.NameBlurred -> state.copy(
        nameTouched = state.nameTouched || state.nameEditedSinceBlur,
        nameEditedSinceBlur = false,
    )
    FocusSetupEvent.TagBlurred -> state.copy(
        tagTouched = state.tagTouched || state.tagEditedSinceBlur,
        tagEditedSinceBlur = false,
    )
    FocusSetupEvent.TimedMinutesBlurred -> state.copy(
        timedMinutesTouched = state.timedMinutesTouched || state.timedMinutesEditedSinceBlur,
        timedMinutesEditedSinceBlur = false,
    )
    is FocusSetupEvent.TagAreaExpandedChanged -> state.copy(tagAreaExpanded = event.expanded)
    FocusSetupEvent.StartAttempted -> state.copy(startAttempted = true)
    is FocusSetupEvent.PeriodChanged -> state.copy(period = event.value)
    is FocusSetupEvent.ViewingContextChanged -> {
        require(runCatching { ZoneId.of(event.zoneId) }.isSuccess) { "Unknown time zone" }
        state.copy(viewingDate = event.date, viewingZoneId = event.zoneId)
    }
    is FocusSetupEvent.RepeatRequested -> state.copy(
        name = "",
        tag = "",
        nameEditedSinceBlur = false,
        nameTouched = false,
        tagEditedSinceBlur = false,
        tagTouched = false,
        timedMinutesEditedSinceBlur = false,
        timedMinutesTouched = false,
        startAttempted = false,
        tagAreaExpanded = false,
        timerMode = FocusTimerMode.COUNT_UP,
        timedMinutesText = "25",
        repeatSourceSessionId = event.sessionId,
        repeatRequestId = event.requestId,
        repeatLoading = true,
        repeatLoadFailed = false,
        repeatNeedsName = false,
        repeatNextStep = null,
        selectedTaskId = null,
        selectedTaskTitle = null,
        taskOrigin = false,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    is FocusSetupEvent.TaskSelectionRequested -> state.copy(
        name = "",
        nameEditedSinceBlur = false,
        nameTouched = false,
        tagEditedSinceBlur = false,
        tagTouched = false,
        timedMinutesEditedSinceBlur = false,
        timedMinutesTouched = false,
        startAttempted = false,
        tagAreaExpanded = false,
        selectedTaskId = event.taskId,
        selectedTaskTitle = null,
        taskOrigin = true,
        taskSelectionLoading = true,
        taskSelectionConflict = false,
        taskSelectionRequestId = event.requestId,
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
    is FocusSetupEvent.TaskSelectionLoaded -> if (
        state.selectedTaskId != event.taskId || state.taskSelectionRequestId != event.requestId
    ) state else state.copy(
        name = event.title.orEmpty(),
        nameTouched = !isValidFocusName(event.title.orEmpty()),
        selectedTaskTitle = event.title,
        taskSelectionLoading = false,
        taskSelectionConflict = !event.isOpen,
        taskSelectionRequestId = null,
    )
    is FocusSetupEvent.TaskSelectionLoadFailed -> if (
        state.selectedTaskId != event.taskId || state.taskSelectionRequestId != event.requestId
    ) state else state.copy(
        taskSelectionLoading = false,
        taskSelectionConflict = true,
        taskSelectionRequestId = null,
    )
    FocusSetupEvent.TaskSelectionConflict -> state.copy(
        taskSelectionLoading = false,
        taskSelectionConflict = true,
        taskSelectionRequestId = null,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    FocusSetupEvent.TaskSelectionCleared -> state.copy(
        selectedTaskId = null,
        selectedTaskTitle = null,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
        taskOrigin = true,
    )
    FocusSetupEvent.TaskContentLocked -> if (!state.taskOrigin) state else state.copy(
        name = "",
        tag = "",
        repeatNextStep = null,
        selectedTaskTitle = null,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
        nameEditedSinceBlur = false,
        nameTouched = false,
        tagEditedSinceBlur = false,
        tagTouched = false,
        timedMinutesEditedSinceBlur = false,
        timedMinutesTouched = false,
        startAttempted = false,
        tagAreaExpanded = false,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = state.unresolvedStartPending || state.capturedStart != null,
    )
    is FocusSetupEvent.PrefillRequested -> if (
        state.databaseGeneration != event.generation || state.repeatSourceSessionId != event.sessionId ||
            state.repeatRequestId != event.requestId
    ) state else state.copy(
        name = event.input.name,
        tag = event.input.tagName.orEmpty(),
        nameEditedSinceBlur = false,
        nameTouched = !isValidFocusName(event.input.name),
        tagEditedSinceBlur = false,
        tagTouched = !isValidFocusTag(event.input.tagName),
        timedMinutesEditedSinceBlur = false,
        timedMinutesTouched = !isValidFocusTimer(event.input.timer),
        startAttempted = false,
        tagAreaExpanded = false,
        timerMode = event.input.timer.mode,
        timedMinutesText = event.input.timer.targetMillis?.div(MILLIS_PER_MINUTE)?.toString() ?: "25",
        repeatLoading = false,
        repeatRequestId = null,
        repeatLoadFailed = false,
        repeatNeedsName = event.input.name.isBlank(),
        repeatNextStep = event.nextStep,
        selectedTaskId = event.input.taskId,
        selectedTaskTitle = event.taskTitle,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
        taskOrigin = event.input.taskOrigin,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    is FocusSetupEvent.RepeatSourceMissing -> if (state.repeatRequestId == event.requestId) state.copy(
        repeatSourceSessionId = null,
        repeatRequestId = null,
        repeatLoading = false,
        repeatLoadFailed = false,
        repeatNeedsName = false,
        repeatNextStep = null,
        selectedTaskId = null,
        selectedTaskTitle = null,
        taskOrigin = false,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
    ) else state
    is FocusSetupEvent.RepeatLoadFailed -> if (state.repeatRequestId == event.requestId) state.copy(
        repeatLoading = false,
        repeatRequestId = null,
        repeatLoadFailed = true,
    ) else state
    FocusSetupEvent.StartAnotherRequested -> state.copy(
        name = "",
        tag = "",
        nameEditedSinceBlur = false,
        nameTouched = false,
        tagEditedSinceBlur = false,
        tagTouched = false,
        timedMinutesEditedSinceBlur = false,
        timedMinutesTouched = false,
        startAttempted = false,
        tagAreaExpanded = false,
        timerMode = FocusTimerMode.COUNT_UP,
        timedMinutesText = "25",
        repeatSourceSessionId = null,
        repeatRequestId = null,
        repeatLoading = false,
        repeatLoadFailed = false,
        repeatNeedsName = false,
        repeatNextStep = null,
        selectedTaskId = null,
        selectedTaskTitle = null,
        taskOrigin = false,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    is FocusSetupEvent.GenerationChanged -> if (
        state.databaseGeneration != event.value
    ) {
        state.copy(
            name = "",
            tag = "",
            nameEditedSinceBlur = false,
            nameTouched = false,
            tagEditedSinceBlur = false,
            tagTouched = false,
            timedMinutesEditedSinceBlur = false,
            timedMinutesTouched = false,
            startAttempted = false,
            tagAreaExpanded = false,
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
            selectedTaskId = null,
            selectedTaskTitle = null,
            taskOrigin = false,
            taskSelectionLoading = false,
            taskSelectionConflict = false,
            taskSelectionRequestId = null,
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
        startAttempted = true,
    )
    FocusSetupEvent.StartSucceeded -> state.copy(
        name = "",
        tag = "",
        nameEditedSinceBlur = false,
        nameTouched = false,
        tagEditedSinceBlur = false,
        tagTouched = false,
        timedMinutesEditedSinceBlur = false,
        timedMinutesTouched = false,
        startAttempted = false,
        tagAreaExpanded = false,
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
        selectedTaskId = null,
        selectedTaskTitle = null,
        taskOrigin = false,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
    )
    FocusSetupEvent.StartOutcomeReconciled -> state.copy(
        capturedStart = null,
        startFailure = null,
        unresolvedStartPending = false,
    )
    FocusSetupEvent.PrivateContentLocked -> state.copy(
        name = "",
        tag = "",
        nameEditedSinceBlur = false,
        nameTouched = false,
        tagEditedSinceBlur = false,
        tagTouched = false,
        timedMinutesEditedSinceBlur = false,
        timedMinutesTouched = false,
        startAttempted = false,
        tagAreaExpanded = false,
        selectedTaskTitle = null,
        taskSelectionLoading = false,
        taskSelectionConflict = false,
        taskSelectionRequestId = null,
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
    KEY_TASK_ID to state.selectedTaskId?.toString(),
    KEY_TASK_TITLE to state.selectedTaskTitle,
    KEY_TASK_ORIGIN to state.taskOrigin,
    KEY_NAME_EDITED to state.nameEditedSinceBlur,
    KEY_NAME_TOUCHED to state.nameTouched,
    KEY_TAG_EDITED to state.tagEditedSinceBlur,
    KEY_TAG_TOUCHED to state.tagTouched,
    KEY_TIMER_EDITED to state.timedMinutesEditedSinceBlur,
    KEY_TIMER_TOUCHED to state.timedMinutesTouched,
    KEY_START_ATTEMPTED to state.startAttempted,
    KEY_TAG_EXPANDED to state.tagAreaExpanded,
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
        nameEditedSinceBlur = saved[KEY_NAME_EDITED] as? Boolean ?: false,
        nameTouched = saved[KEY_NAME_TOUCHED] as? Boolean ?: false,
        tagEditedSinceBlur = saved[KEY_TAG_EDITED] as? Boolean ?: false,
        tagTouched = saved[KEY_TAG_TOUCHED] as? Boolean ?: false,
        timedMinutesEditedSinceBlur = saved[KEY_TIMER_EDITED] as? Boolean ?: false,
        timedMinutesTouched = saved[KEY_TIMER_TOUCHED] as? Boolean ?: false,
        startAttempted = saved[KEY_START_ATTEMPTED] as? Boolean ?: false,
        tagAreaExpanded = saved[KEY_TAG_EXPANDED] as? Boolean ?: false,
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
        selectedTaskId = (saved[KEY_TASK_ID] as? String)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        },
        selectedTaskTitle = saved[KEY_TASK_TITLE] as? String,
        taskOrigin = saved[KEY_TASK_ORIGIN] as? Boolean == true || saved[KEY_TASK_ID] is String,
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
private const val KEY_TASK_ID = "focus.setup.task_id"
private const val KEY_TASK_TITLE = "focus.setup.task_title"
private const val KEY_TASK_ORIGIN = "focus.setup.task_origin"
private const val KEY_NAME_EDITED = "focus.setup.name_edited"
private const val KEY_NAME_TOUCHED = "focus.setup.name_touched"
private const val KEY_TAG_EDITED = "focus.setup.tag_edited"
private const val KEY_TAG_TOUCHED = "focus.setup.tag_touched"
private const val KEY_TIMER_EDITED = "focus.setup.timer_edited"
private const val KEY_TIMER_TOUCHED = "focus.setup.timer_touched"
private const val KEY_START_ATTEMPTED = "focus.setup.start_attempted"
private const val KEY_TAG_EXPANDED = "focus.setup.tag_expanded"
