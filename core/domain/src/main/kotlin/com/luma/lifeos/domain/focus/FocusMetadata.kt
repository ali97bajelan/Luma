package com.luma.lifeos.domain.focus

import java.util.UUID

data class FocusTag(val id: UUID, val name: String)
data class FocusMetadata(val name: String? = null, val tag: FocusTag? = null)
data class FocusStartInput(val name: String, val tagName: String?)

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
    return FocusStartInput(name, tag)
}
