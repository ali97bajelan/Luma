package com.luma.lifeos.domain.lock

/** The independently selectable private areas in Luma. Places remain outside this list. */
enum class LockSection {
    MEMORIES,
    JOURNAL,
    MOOD,
    TASKS,
    HABITS,
    PEOPLE,
    FOCUS,
    HEALTH_USAGE,
}

enum class LockCoverage { WHOLE_APP, SELECTED }

enum class RelockMode {
    EVERY_TIME,
    AFTER_1_MINUTE,
    AFTER_5_MINUTES,
    AFTER_15_MINUTES,
    ;

    /** Null means that leaving the app invalidates access instead of starting a timer. */
    val awayDurationMs: Long?
        get() = when (this) {
            EVERY_TIME -> null
            AFTER_1_MINUTE -> 60_000L
            AFTER_5_MINUTES -> 300_000L
            AFTER_15_MINUTES -> 900_000L
        }
}

/** Persistent lock preferences. It deliberately contains no credential or grant material. */
data class LockPolicy(
    val enabled: Boolean = false,
    val coverage: LockCoverage = LockCoverage.WHOLE_APP,
    val sections: Set<LockSection> = emptySet(),
    val relockMode: RelockMode = RelockMode.EVERY_TIME,
    val screenshotsProtected: Boolean = true,
    val generation: Long = 0L,
) {
    init {
        require(generation >= 0L) { "Policy generation cannot be negative" }
        when (coverage) {
            LockCoverage.WHOLE_APP -> require(sections.isEmpty()) {
                "Whole-app coverage does not store a section selection"
            }
            LockCoverage.SELECTED -> require(sections.isNotEmpty()) {
                "Selected coverage requires at least one section"
            }
        }
    }
}
