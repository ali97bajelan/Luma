package com.luma.lifeos.domain.focus

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Device-local invitation configuration; disabled with no selected days is a valid initial state. */
data class FocusInvitationConfig(
    val enabled: Boolean = false,
    val revision: Long = 0,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val time: LocalTime = LocalTime.of(9, 0),
) {
    init {
        require(revision >= 0) { "Invitation revision cannot be negative" }
        require(!enabled || weekdays.isNotEmpty()) { "Enabled invitations require at least one weekday" }
    }
}

/** Identity of one local civil-date occurrence in a database generation and schedule revision. */
data class FocusInvitationSlot(
    val generation: UUID,
    val scheduleRevision: Long,
    val date: LocalDate,
    val dueAt: Instant,
) {
    init {
        require(scheduleRevision >= 0) { "Invitation revision cannot be negative" }
    }
}

/** Local-only, generation-aware reminder persistence. Implementations claim actions atomically. */
interface FocusInvitationRepository {
    fun observeConfig(): Flow<FocusInvitationConfig>

    suspend fun saveConfig(
        config: FocusInvitationConfig,
        expectedRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult

    suspend fun consume(slot: FocusInvitationSlot, now: Instant): Boolean

    suspend fun snooze(slot: FocusInvitationSlot, now: Instant, token: String): Boolean

    suspend fun disable(slot: FocusInvitationSlot, token: String): Boolean
}
