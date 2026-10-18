package com.luma.lifeos.domain.todo

object TodoTextLimits {
    const val TITLE = 200
    const val NOTE = 4_000
    const val MIN_YEAR = 1
    const val MAX_YEAR = 9_999

    fun codePointCount(value: String): Int = value.codePointCount(0, value.length)

    fun containsLineBreak(value: String): Boolean = value.any { character ->
        character == '\n' || character == '\r' || character == '\u000B' || character == '\u000C' ||
            character == '\u0085' || character == '\u2028' || character == '\u2029'
    }
}
