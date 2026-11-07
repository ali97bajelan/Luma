package com.luma.lifeos.domain.focus

import java.util.UUID
import java.util.Locale

data class FocusTag(val id: UUID, val name: String)
data class FocusMetadata(val name: String? = null, val tag: FocusTag? = null)

enum class FocusTimerMode { COUNT_UP, TIMED }

data class FocusTimerConfig(val mode: FocusTimerMode, val targetMillis: Long?) {
    init {
        when (mode) {
            FocusTimerMode.COUNT_UP -> require(targetMillis == null) { "Count-up has no target" }
            FocusTimerMode.TIMED -> require(
                targetMillis != null && targetMillis in MIN_FOCUS_TARGET_MILLIS..MAX_FOCUS_TARGET_MILLIS &&
                    targetMillis % MILLIS_PER_MINUTE == 0L,
            ) { "Timed focus target must be 1–180 whole minutes" }
        }
    }

    companion object {
        fun countUp() = FocusTimerConfig(FocusTimerMode.COUNT_UP, null)
        fun timed(minutes: Int) = FocusTimerConfig(FocusTimerMode.TIMED, minutes * MILLIS_PER_MINUTE)
    }
}

const val MILLIS_PER_MINUTE = 60_000L
const val MIN_FOCUS_TARGET_MILLIS = MILLIS_PER_MINUTE
const val MAX_FOCUS_TARGET_MILLIS = 180 * MILLIS_PER_MINUTE

data class FocusStartInput(
    val name: String,
    val tagName: String?,
    val timer: FocusTimerConfig = FocusTimerConfig.countUp(),
    val taskId: UUID? = null,
    val taskOrigin: Boolean = false,
)

/** Validate raw input before trimming so control characters cannot be hidden at the edges. */
fun normalizeFocusStartInput(input: FocusStartInput): FocusStartInput {
    fun clean(value: String): String {
        require(value.none { Character.isISOControl(it) }) { "Control characters are not allowed" }
        return value.trim().also { require(it.isNotBlank()) { "Label is required" } }
    }
    val name = clean(input.name)
    require(name.codePointCount(0, name.length) <= 120) { "Name is too long" }
    val tag = input.tagName?.let(::clean)
    require(tag == null || tag.length <= 50) { "Tag is too long" }
    return input.copy(
        name = name,
        tagName = tag,
        taskOrigin = input.taskOrigin || input.taskId != null,
    )
}

fun normalizeFocusTagName(value: String): String {
    require(value.none { Character.isISOControl(it) }) { "Control characters are not allowed" }
    return value.trim().also {
        require(it.isNotBlank()) { "Tag is required" }
        require(it.length <= 50) { "Tag is too long" }
    }
}

fun normalizeFocusTagIdentity(value: String): String = normalizeFocusTagName(value).lowercase(Locale.ROOT)
