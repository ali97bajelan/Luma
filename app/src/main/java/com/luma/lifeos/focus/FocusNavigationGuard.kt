package com.luma.lifeos.focus

import com.luma.lifeos.widget.WidgetDestination
import java.io.Serializable

internal sealed interface FocusPendingDestination : Serializable {
    data class Widget(val destination: WidgetDestination) : FocusPendingDestination
    data class Reminder(val kind: String, val id: String, val date: String) : FocusPendingDestination
    data class Route(val route: String) : FocusPendingDestination
}

/** Pure route policy: one external destination may be offered after a running session releases the guard. */
internal data class FocusNavigationGuard(
    val pendingDestination: FocusPendingDestination? = null,
) : Serializable {
    fun isGuarded(state: FocusRuntimeState): Boolean = state.navigationGuarded

    fun intercept(state: FocusRuntimeState, destination: FocusPendingDestination): FocusNavigationGuard =
        if (isGuarded(state)) copy(pendingDestination = destination) else this

    /** Returns non-null whenever navigation must be queued, even if the queued value is unchanged. */
    fun interceptIfGuarded(
        state: FocusRuntimeState,
        destination: FocusPendingDestination,
    ): FocusNavigationGuard? = if (isGuarded(state)) intercept(state, destination) else null

    fun consumePending(): Pair<FocusPendingDestination?, FocusNavigationGuard> =
        pendingDestination to copy(pendingDestination = null)
}
