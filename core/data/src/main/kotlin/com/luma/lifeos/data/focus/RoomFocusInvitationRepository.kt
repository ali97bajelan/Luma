package com.luma.lifeos.data.focus

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.domain.focus.FocusInvitationConfig
import com.luma.lifeos.domain.focus.FocusInvitationRepository
import com.luma.lifeos.domain.focus.FocusInvitationSlot
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductInvalidReason
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusProductUnavailableReason
import com.luma.lifeos.domain.focus.focusInvitationDeadline
import com.luma.lifeos.domain.focus.focusInvitationDueAt
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull

/** Device-local invitation configuration and at-most-once occurrence claims. */
class RoomFocusInvitationRepository(
    private val database: LumaDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val deviceZone: () -> ZoneId = { ZoneId.systemDefault() },
    private val isBreakRunning: (FocusBreakEntity) -> Boolean = { true },
) : FocusInvitationRepository {
    private val invitationDao get() = database.focusInvitations()
    private val focusDao get() = database.focus()

    override fun observeConfig(): Flow<FocusInvitationConfig> = flow {
        database.withTransaction {
            ensureGeneration()
            ensureSettings()
        }
        emitAll(invitationDao.observeSettings().map { it?.toDomain() ?: FocusInvitationConfig() })
    }.flowOn(Dispatchers.IO)

    override fun observeGeneration(): Flow<UUID> = flow {
        database.withTransaction { ensureGeneration() }
        emitAll(
            focusDao.observeStorageState()
                .map { state -> state?.generationId?.let(UUID::fromString) }
                .filterNotNull(),
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun currentGeneration(): UUID = database.withTransaction {
        UUID.fromString(ensureGeneration().generationId)
    }

    override suspend fun saveConfig(
        config: FocusInvitationConfig,
        expectedRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult {
        if (expectedRevision < 0 || config.revision != expectedRevision ||
            config.time.second != 0 || config.time.nano != 0 ||
            (config.enabled && config.weekdays.isEmpty())
        ) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_INVITATION)
        }

        val payload = config.commandPayload(expectedRevision)
        return safely {
            database.withTransaction {
                val generation = ensureGeneration().generationId
                ensureSettings()
                val settings = requireNotNull(invitationDao.settings())
                val current = settings.toDomain()
                if (generation != context.generation.toString()) {
                    return@withTransaction FocusProductResult.InvitationConflict(current)
                }
                replay(context, generation, payload)?.let { return@withTransaction it }
                if (settings.revision != expectedRevision) {
                    return@withTransaction FocusProductResult.InvitationConflict(current)
                }

                if (settings.matches(config)) {
                    insertReceipt(generation, context, payload)
                    return@withTransaction FocusProductResult.Applied
                }

                val nextRevision = nextRevision(settings.revision)
                    ?: return@withTransaction FocusProductResult.Invalid(
                        FocusProductInvalidReason.INVALID_INVITATION,
                    )
                check(
                    invitationDao.updateSettings(
                        settings.copy(
                            enabled = config.enabled,
                            revision = nextRevision,
                            weekdayMask = config.weekdayMask,
                            minuteOfDay = config.time.toMinuteOfDay(),
                        ),
                    ) == 1,
                )
                invitationDao.deleteDeliveries(generation)
                insertReceipt(generation, context, payload)
                FocusProductResult.Applied
            }
        }
    }

    /** Persists a validated occurrence before its one-shot alarm is scheduled. */
    override suspend fun ensureScheduled(slot: FocusInvitationSlot): Boolean = safelyBoolean {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            invitationDao.deleteOtherGenerations(generation)
            ensureSettings()
            val settings = requireNotNull(invitationDao.settings())
            if (slot.generation.toString() != generation ||
                !settings.enabled ||
                slot.scheduleRevision != settings.revision ||
                slot.date.dayOfWeek !in settings.toDomain().weekdays
            ) {
                return@withTransaction false
            }

            val expectedDue = focusInvitationDueAt(slot.date, deviceZone(), settings.toDomain().time)
            val expectedMillis = expectedDue.toEpochMilli()
            if (slot.dueAt.toEpochMilli() != expectedMillis) return@withTransaction false

            val existing = invitationDao.delivery(generation, settings.revision, slot.date.toString())
            if (existing != null) {
                if (existing.state == PENDING && existing.dueAt != expectedMillis) {
                    check(invitationDao.updateDelivery(existing.copy(dueAt = expectedMillis)) == 1)
                    return@withTransaction true
                }
                return@withTransaction existing.dueAt == expectedMillis && existing.state == PENDING
            }
            invitationDao.insertDelivery(
                FocusInvitationDeliveryEntity(
                    databaseGeneration = generation,
                    scheduleRevision = settings.revision,
                    localDate = slot.date.toString(),
                    dueAt = expectedMillis,
                ),
            ) == 1L
        }
    }

    /** Marks a quiet-hour or otherwise ineligible scheduled occurrence as skipped. */
    override suspend fun skip(slot: FocusInvitationSlot, now: Instant): Boolean = safelyBoolean {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            val settings = currentSettings()
            val row = invitationDao.delivery(generation, slot.scheduleRevision, slot.date.toString())
                ?: return@withTransaction false
            val dueAt = row.activeDueAt() ?: return@withTransaction false
            if ((row.state != PENDING && row.state != SNOOZED) ||
                !isCurrentSlot(slot, generation, settings, row, dueAt) ||
                now.isBefore(Instant.ofEpochMilli(dueAt))
            ) {
                return@withTransaction false
            }
            check(invitationDao.updateDelivery(row.copy(state = SKIPPED, actionToken = null)) == 1)
            true
        }
    }

    /** Atomically claims one eligible attempt and creates its local action token. */
    override suspend fun consume(slot: FocusInvitationSlot, now: Instant): Boolean = safelyBoolean {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            invitationDao.deleteOtherGenerations(generation)
            val settings = currentSettings()
            val row = invitationDao.delivery(generation, slot.scheduleRevision, slot.date.toString())
                ?: return@withTransaction false
            val activeDueAt = row.activeDueAt() ?: return@withTransaction false
            if (!isCurrentSlot(slot, generation, settings, row, activeDueAt)) {
                return@withTransaction false
            }

            val due = Instant.ofEpochMilli(activeDueAt)
            if (now.isBefore(due)) return@withTransaction false
            if (now.isAfter(focusInvitationDeadline(due))) {
                check(invitationDao.updateDelivery(row.copy(state = SKIPPED, actionToken = null)) == 1)
                return@withTransaction false
            }
            if (row.state != PENDING && row.state != SNOOZED) return@withTransaction false

            // Recheck both protected Focus runtimes inside the same claim transaction.
            val activeBreak = focusDao.breakRuntime()?.takeIf(isBreakRunning)
            if (focusDao.openSession() != null || activeBreak != null) {
                check(invitationDao.updateDelivery(row.copy(state = SKIPPED, actionToken = null)) == 1)
                return@withTransaction false
            }

            val token = UUID.randomUUID().toString()
            check(
                invitationDao.updateDelivery(
                    row.copy(state = CLAIMED, actionToken = token),
                ) == 1,
            )
            true
        }
    }

    /** Returns a claimed occurrence token only to the trusted local notification path. */
    override suspend fun actionToken(slot: FocusInvitationSlot): String? = safelyValue {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            val settings = currentSettings()
            val row = invitationDao.delivery(generation, slot.scheduleRevision, slot.date.toString())
                ?: return@withTransaction null
            val dueAt = row.activeDueAt() ?: return@withTransaction null
            if (!isCurrentSlot(slot, generation, settings, row, dueAt) || row.state != CLAIMED) {
                return@withTransaction null
            }
            if (clock.instant().isAfter(focusInvitationDeadline(Instant.ofEpochMilli(dueAt)))) {
                check(invitationDao.updateDelivery(row.copy(state = SKIPPED, actionToken = null)) == 1)
                return@withTransaction null
            }
            row.actionToken
        }
    }

    override suspend fun snoozedSlot(slot: FocusInvitationSlot): FocusInvitationSlot? = safelyValue {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            val row = invitationDao.delivery(generation, slot.scheduleRevision, slot.date.toString())
                ?: return@withTransaction null
            val snoozedDueAt = row.snoozedDueAt ?: return@withTransaction null
            if (row.state != SNOOZED || generation != slot.generation.toString() ||
                row.scheduleRevision != slot.scheduleRevision || row.localDate != slot.date.toString()
            ) {
                return@withTransaction null
            }
            val due = Instant.ofEpochMilli(snoozedDueAt)
            if (clock.instant().isAfter(focusInvitationDeadline(due))) {
                check(invitationDao.updateDelivery(row.copy(state = SKIPPED, actionToken = null)) == 1)
                return@withTransaction null
            }
            slot.copy(dueAt = due)
        }
    }

    /** A token can snooze one claimed occurrence once; the next claim receives a fresh token. */
    override suspend fun snooze(
        slot: FocusInvitationSlot,
        now: Instant,
        token: String,
    ): Boolean = safelyBoolean {
        if (token.isBlank()) return@safelyBoolean false
        database.withTransaction {
            val generation = ensureGeneration().generationId
            val settings = currentSettings()
            val row = invitationDao.delivery(generation, slot.scheduleRevision, slot.date.toString())
                ?: return@withTransaction false
            if (generation != slot.generation.toString() ||
                !settings.enabled || settings.revision != slot.scheduleRevision ||
                row.state != CLAIMED || row.actionToken != token || row.snoozedDueAt != null ||
                slot.dueAt.toEpochMilli() != row.dueAt
            ) {
                return@withTransaction false
            }

            val due = Instant.ofEpochMilli(row.dueAt)
            if (now.isBefore(due) || now.isAfter(focusInvitationDeadline(due))) {
                return@withTransaction false
            }
            val snoozedDueAt = now.plusSeconds(SNOOZE_SECONDS).toEpochMilli()
            check(
                invitationDao.updateDelivery(
                    row.copy(state = SNOOZED, snoozedDueAt = snoozedDueAt, actionToken = null),
                ) == 1,
            )
            true
        }
    }

    /** Disabling an invitation advances its revision and invalidates every outstanding action. */
    override suspend fun disable(slot: FocusInvitationSlot, token: String): Boolean = safelyBoolean {
        if (token.isBlank()) return@safelyBoolean false
        database.withTransaction {
            val generation = ensureGeneration().generationId
            val settings = currentSettings()
            val row = invitationDao.delivery(generation, slot.scheduleRevision, slot.date.toString())
                ?: return@withTransaction false
            val activeDueAt = row.activeDueAt() ?: return@withTransaction false
            if (generation != slot.generation.toString() ||
                !settings.enabled || settings.revision != slot.scheduleRevision ||
                row.state != CLAIMED || row.actionToken != token ||
                slot.dueAt.toEpochMilli() != activeDueAt
            ) {
                return@withTransaction false
            }
            if (clock.instant().isAfter(focusInvitationDeadline(Instant.ofEpochMilli(activeDueAt)))) {
                check(invitationDao.updateDelivery(row.copy(state = SKIPPED, actionToken = null)) == 1)
                return@withTransaction false
            }
            val nextRevision = nextRevision(settings.revision) ?: return@withTransaction false
            check(
                invitationDao.updateSettings(
                    settings.copy(enabled = false, revision = nextRevision),
                ) == 1,
            )
            invitationDao.deleteDeliveries(generation)
            true
        }
    }

    private suspend fun currentSettings(): FocusInvitationSettingsEntity {
        ensureSettings()
        return requireNotNull(invitationDao.settings())
    }

    private suspend fun ensureSettings() {
        invitationDao.insertSettingsIfAbsent(FocusInvitationSettingsEntity())
    }

    private suspend fun ensureGeneration(): FocusStorageStateEntity {
        focusDao.insertStorageStateIfAbsent(
            FocusStorageStateEntity(singletonId = 1, generationId = UUID.randomUUID().toString()),
        )
        return requireNotNull(focusDao.storageState())
    }

    private suspend fun replay(
        context: FocusProductContext,
        generation: String,
        payload: String,
    ): FocusProductResult? {
        val prior = invitationDao.receipt(generation, context.commandId.toString()) ?: return null
        if (prior.payload != payload) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_COMMAND)
        }
        return if (prior.resultType == RESULT_APPLIED) FocusProductResult.AlreadyApplied else null
    }

    private suspend fun insertReceipt(
        generation: String,
        context: FocusProductContext,
        payload: String,
    ) {
        invitationDao.insertReceipt(
            FocusProductCommandReceiptEntity(
                databaseGeneration = generation,
                commandId = context.commandId.toString(),
                payload = payload,
                resultType = RESULT_APPLIED,
            ),
        )
    }

    private fun isCurrentSlot(
        slot: FocusInvitationSlot,
        generation: String,
        settings: FocusInvitationSettingsEntity,
        row: FocusInvitationDeliveryEntity,
        activeDueAt: Long,
    ): Boolean {
        if (slot.generation.toString() != generation ||
            !settings.enabled || settings.revision != slot.scheduleRevision ||
            row.databaseGeneration != generation || row.scheduleRevision != slot.scheduleRevision ||
            row.localDate != slot.date.toString() || row.activeDueAt() != activeDueAt ||
            slot.dueAt.toEpochMilli() != activeDueAt
        ) {
            return false
        }
        if (row.state == PENDING) {
            val config = settings.toDomain()
            if (slot.date.dayOfWeek !in config.weekdays ||
                focusInvitationDueAt(slot.date, deviceZone(), config.time).toEpochMilli() != row.dueAt
            ) {
                return false
            }
        }
        return true
    }

    private suspend fun safely(block: suspend () -> FocusProductResult): FocusProductResult = try {
        block()
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        FocusProductResult.Unavailable(FocusProductUnavailableReason(failure.javaClass.simpleName))
    }

    private suspend fun safelyBoolean(block: suspend () -> Boolean): Boolean = try {
        block()
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        false
    }

    private suspend fun <T> safelyValue(block: suspend () -> T?): T? = try {
        block()
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        null
    }

    private companion object {
        const val RESULT_APPLIED = "APPLIED"
        const val PENDING = "PENDING"
        const val CLAIMED = "CLAIMED"
        const val SNOOZED = "SNOOZED"
        const val SKIPPED = "SKIPPED"
        const val SNOOZE_SECONDS = 10L * 60L
    }
}

private fun FocusInvitationConfig.commandPayload(expectedRevision: Long): String = buildString {
    append("invitation|")
    append(enabled).append('|')
    append(weekdays.sortedBy { it.value }.joinToString(",") { it.value.toString() }).append('|')
    append(time.toMinuteOfDay()).append('|')
    append(expectedRevision)
}

private fun FocusInvitationSettingsEntity.toDomain(): FocusInvitationConfig = FocusInvitationConfig(
    enabled = enabled,
    revision = revision,
    weekdays = DayOfWeek.values()
        .filter { weekdayMask and (1 shl (it.value - 1)) != 0 }
        .toSet(),
    time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
)

private fun FocusInvitationSettingsEntity.matches(config: FocusInvitationConfig): Boolean =
    enabled == config.enabled &&
        weekdayMask == config.weekdayMask &&
        minuteOfDay == config.time.toMinuteOfDay()

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

private val FocusInvitationConfig.weekdayMask: Int
    get() = weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun FocusInvitationDeliveryEntity.activeDueAt(): Long? = when (state) {
    "PENDING" -> dueAt
    "SNOOZED" -> snoozedDueAt
    "CLAIMED" -> snoozedDueAt ?: dueAt
    else -> null
}

private fun nextRevision(value: Long): Long? = runCatching { Math.addExact(value, 1L) }.getOrNull()
