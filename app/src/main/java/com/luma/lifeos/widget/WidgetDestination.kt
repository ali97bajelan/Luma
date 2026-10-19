package com.luma.lifeos.widget

import java.io.Serializable
import java.time.LocalDate
import java.util.UUID

enum class CaptureKind {
    MOOD,
    MEMORY,
    JOURNAL,
}

sealed interface WidgetDestination : Serializable {
    data object Today : WidgetDestination
    data object QuickCapture : WidgetDestination
    data class Capture(val kind: CaptureKind) : WidgetDestination
    data object Habits : WidgetDestination
    data class HabitDetail(val id: UUID, val date: LocalDate) : WidgetDestination
    data object Tasks : WidgetDestination
    data class Task(val id: UUID) : WidgetDestination
    data object NewTask : WidgetDestination
}

data class WidgetLaunchRequest(
    val id: UUID,
    val destination: WidgetDestination,
) : Serializable
