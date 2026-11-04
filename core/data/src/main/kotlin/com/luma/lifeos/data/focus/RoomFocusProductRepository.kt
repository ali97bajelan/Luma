package com.luma.lifeos.data.focus

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.domain.focus.FocusGoalState
import com.luma.lifeos.domain.focus.FocusGoalVersion
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductInvalidReason
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusProductUnavailableReason
import com.luma.lifeos.domain.focus.nextGoalMonday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Room-backed goal history and generation/revision-safe goal commands. */
class RoomFocusProductRepository(
    private val database: LumaDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val deviceZone: () -> ZoneId = { ZoneId.systemDefault() },
) : FocusProductRepository {
    private val dao get() = database.focusProducts()
    private val focusDao get() = database.focus()

    override suspend fun currentGeneration(): UUID = database.withTransaction {
        UUID.fromString(ensureGeneration().generationId)
    }

    override fun observeGoals(): Flow<FocusGoalState> = flow {
        database.withTransaction {
            ensureGeneration()
            ensureSettings()
        }
        emitAll(combine(dao.observeSettings(), dao.observeVersions()) { settings, versions ->
            settings?.toDomain(versions.map(FocusGoalVersionEntity::toDomain)) ?: FocusGoalState.DISABLED
        })
    }.flowOn(Dispatchers.IO)

    override suspend fun scheduleGoal(
        goal: FocusGoalVersion,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult = safely {
        val payload = buildString {
            append("schedule|").append(goal.id).append('|').append(goal.revision).append('|')
            append(goal.effectiveMonday).append('|').append(goal.zoneId).append('|')
            append(goal.targetMinutes).append('|').append(goal.weekdayMask).append('|')
            append(expectedSettingsRevision)
        }
        database.withTransaction {
            val generation = ensureGeneration().generationId
            if (generation != context.generation.toString()) return@withTransaction conflict()
            val replay = replay(context, generation, payload) ?: return@withTransaction processSchedule(
                goal, expectedSettingsRevision, context, generation, payload,
            )
            replay
        }
    }

    override suspend fun setGoalsEnabled(
        enabled: Boolean,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult = safely {
        val payload = "enabled|$enabled|$expectedSettingsRevision"
        database.withTransaction {
            val generation = ensureGeneration().generationId
            if (generation != context.generation.toString()) return@withTransaction conflict()
            val replay = replay(context, generation, payload) ?: return@withTransaction processEnabled(
                enabled, expectedSettingsRevision, context, generation, payload,
            )
            replay
        }
    }

    private suspend fun processSchedule(
        goal: FocusGoalVersion,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
        generation: String,
        payload: String,
    ): FocusProductResult {
        ensureSettings()
        val settings = requireNotNull(dao.settings())
        if (settings.settingsRevision != expectedSettingsRevision) return conflict()
        val now = clock.instant()
        val zone = ZoneId.of(goal.zoneId)
        if (goal.effectiveMonday != nextGoalMonday(now, zone)) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_GOAL)
        }
        val nextRevision = runCatching { Math.addExact(settings.settingsRevision, 1L) }.getOrNull()
            ?: return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_GOAL)
        if (goal.revision != nextRevision) return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_GOAL)
        val existingVersions = dao.versions()
        val pending = existingVersions.filter { version ->
            version.effectiveMonday.toGoalDate().atStartOfDay(ZoneId.of(version.zoneId)).toInstant().isAfter(now)
        }
        pending.forEach { dao.deleteVersion(it.id) }
        dao.insertVersion(goal.toEntity())
        val activeGoal = settings.toDomain(existingVersions.map(FocusGoalVersionEntity::toDomain)).activeGoal(now)
        val updated = if (settings.enabled) {
            if (activeGoal == null) {
                settings.copy(
                    settingsRevision = nextRevision,
                    visibleFromMonday = goal.effectiveMonday.toString(),
                    visibleFromZoneId = goal.zoneId,
                )
            } else {
                settings.copy(settingsRevision = nextRevision)
            }
        } else {
            settings.copy(
                enabled = true,
                settingsRevision = nextRevision,
                visibleFromMonday = goal.effectiveMonday.toString(),
                visibleFromZoneId = goal.zoneId,
            )
        }
        check(dao.updateSettings(updated) == 1)
        dao.insertReceipt(
            FocusProductCommandReceiptEntity(generation, context.commandId.toString(), payload, RESULT_APPLIED),
        )
        return FocusProductResult.Applied
    }

    private suspend fun processEnabled(
        enabled: Boolean,
        expectedSettingsRevision: Long,
        context: FocusProductContext,
        generation: String,
        payload: String,
    ): FocusProductResult {
        ensureSettings()
        val settings = requireNotNull(dao.settings())
        if (settings.settingsRevision != expectedSettingsRevision) return conflict()
        val now = clock.instant()
        if (settings.enabled == enabled && enabled) {
            dao.insertReceipt(
                FocusProductCommandReceiptEntity(generation, context.commandId.toString(), payload, RESULT_APPLIED),
            )
            return FocusProductResult.Applied
        }
        val pendingVersions = dao.versions().filter { version ->
            version.effectiveMonday.toGoalDate().atStartOfDay(ZoneId.of(version.zoneId)).toInstant().isAfter(now)
        }
        if (settings.enabled == enabled && !enabled && pendingVersions.isEmpty()) {
            dao.insertReceipt(
                FocusProductCommandReceiptEntity(generation, context.commandId.toString(), payload, RESULT_APPLIED),
            )
            return FocusProductResult.Applied
        }
        val nextRevision = runCatching { Math.addExact(settings.settingsRevision, 1L) }.getOrNull()
            ?: return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_GOAL)
        if (!enabled) {
            deletePendingVersions(now)
            check(dao.updateSettings(
                settings.copy(
                    enabled = false,
                    settingsRevision = nextRevision,
                    visibleFromMonday = null,
                    visibleFromZoneId = null,
                ),
            ) == 1)
        } else {
            val template = dao.versions().maxWithOrNull(
                compareBy<FocusGoalVersionEntity>(
                    { it.effectiveMonday.toGoalDate().atStartOfDay(ZoneId.of(it.zoneId)).toInstant() },
                    { it.id },
                ),
            ) ?: return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_GOAL)
            val zone = deviceZone()
            val monday = nextGoalMonday(now, zone)
            deletePendingVersions(now)
            val version = template.copy(
                id = UUID.randomUUID().toString(),
                revision = nextRevision,
                effectiveMonday = monday.toString(),
                zoneId = zone.id,
            )
            dao.insertVersion(version)
            check(dao.updateSettings(
                settings.copy(
                    enabled = true,
                    settingsRevision = nextRevision,
                    visibleFromMonday = monday.toString(),
                    visibleFromZoneId = zone.id,
                ),
            ) == 1)
        }
        dao.insertReceipt(
            FocusProductCommandReceiptEntity(generation, context.commandId.toString(), payload, RESULT_APPLIED),
        )
        return FocusProductResult.Applied
    }

    private suspend fun deletePendingVersions(now: java.time.Instant) {
        dao.versions().filter { version ->
            version.effectiveMonday.toGoalDate().atStartOfDay(ZoneId.of(version.zoneId)).toInstant().isAfter(now)
        }.forEach { dao.deleteVersion(it.id) }
    }

    private suspend fun replay(
        context: FocusProductContext,
        generation: String,
        payload: String,
    ): FocusProductResult? {
        val receipt = dao.receipt(generation, context.commandId.toString()) ?: return null
        if (receipt.payload != payload) return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_COMMAND)
        return if (receipt.resultType == RESULT_APPLIED) FocusProductResult.AlreadyApplied else null
    }

    private suspend fun conflict(): FocusProductResult.Conflict {
        ensureSettings()
        return FocusProductResult.Conflict(
            requireNotNull(dao.settings()).toDomain(dao.versions().map(FocusGoalVersionEntity::toDomain)),
        )
    }

    private suspend fun ensureSettings() {
        dao.insertSettingsIfAbsent(FocusGoalSettingsEntity())
    }

    private suspend fun ensureGeneration(): FocusStorageStateEntity {
        focusDao.insertStorageStateIfAbsent(
            FocusStorageStateEntity(singletonId = 1, generationId = UUID.randomUUID().toString()),
        )
        return requireNotNull(focusDao.storageState())
    }

    private suspend fun safely(block: suspend () -> FocusProductResult): FocusProductResult = try {
        block()
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        FocusProductResult.Unavailable(FocusProductUnavailableReason(failure.javaClass.simpleName))
    }

    private companion object {
        const val RESULT_APPLIED = "APPLIED"
    }
}

private fun FocusGoalSettingsEntity.toDomain(versions: List<FocusGoalVersion>) = FocusGoalState(
    enabled = enabled,
    settingsRevision = settingsRevision,
    visibleFromMonday = visibleFromMonday?.toGoalDate(),
    visibleFromZoneId = visibleFromZoneId,
    versions = versions,
)

private fun FocusGoalVersionEntity.toDomain() = FocusGoalVersion(
    id = UUID.fromString(id),
    revision = revision,
    effectiveMonday = effectiveMonday.toGoalDate(),
    zoneId = zoneId,
    targetMinutes = targetMinutes,
    preferredDays = DayOfWeek.values().filter { preferredWeekdayMask and (1 shl (it.value - 1)) != 0 }.toSet(),
)

private fun FocusGoalVersion.toEntity() = FocusGoalVersionEntity(
    id = id.toString(),
    revision = revision,
    effectiveMonday = effectiveMonday.toString(),
    zoneId = zoneId,
    targetMinutes = targetMinutes,
    preferredWeekdayMask = weekdayMask,
)

private fun String.toGoalDate(): LocalDate = LocalDate.parse(this).also {
    require(it.toString() == this && it.dayOfWeek == DayOfWeek.MONDAY)
}
