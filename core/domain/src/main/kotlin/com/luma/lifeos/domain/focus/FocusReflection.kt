package com.luma.lifeos.domain.focus

enum class FocusOutcome { MADE_PROGRESS, FINISHED_INTENDED_WORK, GOT_INTERRUPTED }

data class FocusReflection(
    val outcome: FocusOutcome? = null,
    val nextStep: String? = null,
) {
    val isEmpty: Boolean get() = outcome == null && nextStep == null
}

/** Normalize optional user text without hiding control characters at its edges. */
fun normalizeFocusReflection(reflection: FocusReflection): FocusReflection {
    val raw = reflection.nextStep ?: return reflection
    require(raw.none { Character.isISOControl(it) }) { "Control characters are not allowed" }
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return reflection.copy(nextStep = null)
    require(trimmed.codePointCount(0, trimmed.length) <= 280) { "Next step is too long" }
    return reflection.copy(nextStep = trimmed)
}
