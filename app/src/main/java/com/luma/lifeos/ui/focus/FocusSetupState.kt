package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusStartInput
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
    val period: FocusPeriod = FocusPeriod.TODAY,
    val viewingZoneId: String = ZoneId.systemDefault().id,
    val viewingDate: LocalDate = LocalDate.now(ZoneId.of(viewingZoneId)),
    val databaseGeneration: UUID? = null,
    val capturedStart: FocusStartInput? = null,
    val startFailure: FocusSetupFailure? = null,
) {
    val normalizedInput: FocusStartInput?
        get() = runCatching {
            normalizeFocusStartInput(FocusStartInput(name, tag.takeIf(String::isNotEmpty)))
        }.getOrNull()

    val canStart: Boolean
        get() = normalizedInput != null && capturedStart == null
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
    data class PeriodChanged(val value: FocusPeriod) : FocusSetupEvent
    data class ViewingContextChanged(val date: LocalDate, val zoneId: String) : FocusSetupEvent
    data class GenerationChanged(val value: UUID) : FocusSetupEvent
    data class StartCaptured(val input: FocusStartInput) : FocusSetupEvent
    data object StartIoFailed : FocusSetupEvent
    data object StartValidationRejected : FocusSetupEvent
    data object StartSucceeded : FocusSetupEvent
    data object StartOutcomeReconciled : FocusSetupEvent
}

fun reduceFocusSetupState(state: FocusSetupState, event: FocusSetupEvent): FocusSetupState = when (event) {
    is FocusSetupEvent.NameChanged -> state.copy(
        name = event.value,
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    is FocusSetupEvent.TagChanged -> state.copy(
        tag = event.value,
        startFailure = state.startFailure.takeUnless { it == FocusSetupFailure.VALIDATION },
    )
    is FocusSetupEvent.PeriodChanged -> state.copy(period = event.value)
    is FocusSetupEvent.ViewingContextChanged -> {
        require(runCatching { ZoneId.of(event.zoneId) }.isSuccess) { "Unknown time zone" }
        state.copy(viewingDate = event.date, viewingZoneId = event.zoneId)
    }
    is FocusSetupEvent.GenerationChanged -> if (
        state.databaseGeneration != null && state.databaseGeneration != event.value
    ) {
        state.copy(
            name = "",
            tag = "",
            databaseGeneration = event.value,
            capturedStart = null,
            startFailure = null,
        )
    } else {
        state.copy(databaseGeneration = event.value)
    }
    is FocusSetupEvent.StartCaptured -> {
        val normalized = normalizeFocusStartInput(event.input)
        if (!state.canStart || state.normalizedInput != normalized) state else state.copy(
            capturedStart = normalized,
            startFailure = null,
        )
    }
    FocusSetupEvent.StartIoFailed -> if (state.capturedStart == null) state else state.copy(
        startFailure = FocusSetupFailure.IO,
    )
    FocusSetupEvent.StartValidationRejected -> state.copy(
        capturedStart = null,
        startFailure = FocusSetupFailure.VALIDATION,
    )
    FocusSetupEvent.StartSucceeded -> state.copy(
        name = "",
        tag = "",
        capturedStart = null,
        startFailure = null,
    )
    FocusSetupEvent.StartOutcomeReconciled -> state.copy(capturedStart = null, startFailure = null)
}

/** Primitive-only representation suitable for SavedStateHandle and Android Bundle restoration. */
fun encodeFocusSetupState(state: FocusSetupState): Map<String, Any?> = mapOf(
    KEY_NAME to state.name,
    KEY_TAG to state.tag,
    KEY_PERIOD to state.period.name,
    KEY_ZONE to state.viewingZoneId,
    KEY_GENERATION to state.databaseGeneration?.toString(),
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
        period = (saved[KEY_PERIOD] as? String)?.let { value ->
            FocusPeriod.entries.firstOrNull { it.name == value }
        } ?: FocusPeriod.TODAY,
        viewingZoneId = zone,
        viewingDate = date,
        databaseGeneration = (saved[KEY_GENERATION] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() },
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
private const val KEY_PERIOD = "focus.setup.period"
private const val KEY_ZONE = "focus.setup.zone"
private const val KEY_DATE = "focus.setup.date"
private const val KEY_GENERATION = "focus.setup.generation"
