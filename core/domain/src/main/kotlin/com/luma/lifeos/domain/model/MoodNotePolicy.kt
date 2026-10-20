package com.luma.lifeos.domain.model

object MoodNotePolicy {
    const val LIMIT = 5_000

    fun canAcceptMoodNote(previous: String, next: String): Boolean =
        next.length <= LIMIT || next == previous || next.length < previous.length
}
