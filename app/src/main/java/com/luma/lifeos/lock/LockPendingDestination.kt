package com.luma.lifeos.lock

import java.util.UUID

/** Safe navigation intent retained only while this process is alive. It never contains a preview. */
sealed interface LockPendingDestination {
    data class Primary(val route: String) : LockPendingDestination
    data object Search : LockPendingDestination
    data object CaptureMenu : LockPendingDestination
    data object NewHabit : LockPendingDestination
    data object NewTask : LockPendingDestination
    data object People : LockPendingDestination
    data object Habits : LockPendingDestination
    data object Tasks : LockPendingDestination
    data class PersonDetail(val personId: String) : LockPendingDestination
    data class PlaceDetail(val placeId: String) : LockPendingDestination
    data class EventDetail(val eventId: String) : LockPendingDestination
    data class EventEditor(val eventId: String?, val kind: EventEditorKind) : LockPendingDestination
    data class TaskEditor(val taskId: String?) : LockPendingDestination
    data class HabitDetail(val habitId: String, val date: String) : LockPendingDestination
    data class FocusHistory(val route: String) : LockPendingDestination
}

enum class EventEditorKind { MEMORY, JOURNAL, MOOD }

data class PendingLockNavigation(
    val id: UUID,
    val destination: LockPendingDestination,
) {
    companion object {
        fun create(destination: LockPendingDestination) = PendingLockNavigation(UUID.randomUUID(), destination)
    }
}
