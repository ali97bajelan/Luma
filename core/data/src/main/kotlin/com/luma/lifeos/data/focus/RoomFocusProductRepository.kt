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
import com.luma.lifeos.domain.focus.FocusReflection
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusTagCatalog
import com.luma.lifeos.domain.focus.normalizeFocusReflection
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.domain.focus.normalizeFocusTagIdentity
import com.luma.lifeos.domain.focus.normalizeFocusTagName
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
import java.util.Base64
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

    override fun observeTagCatalog(): Flow<FocusTagCatalog> = flow {
        database.withTransaction {
            ensureGeneration()
            ensureTagCatalogState()
        }
        emitAll(combine(
            dao.observeTagCatalogState(),
            focusDao.observeTags(),
            dao.observeTagAliases(),
            focusDao.observeStorageState(),
        ) { state, tags, aliases, generation ->
            FocusTagCatalog(
                revision = state?.revision ?: 0L,
                tags = tags.map { FocusTag(UUID.fromString(it.id), it.name) },
                mergedIds = aliases.associate { UUID.fromString(it.formerTagId) to UUID.fromString(it.targetTagId) },
                databaseGeneration = generation?.generationId?.let { UUID.fromString(it) },
            )
        })
    }.flowOn(Dispatchers.IO)

    override fun observeTaskFocusMillis(taskId: UUID): Flow<Long> =
        focusDao.observeTaskFocusMillis(taskId.toString())

    override suspend fun unlinkTask(
        sessionId: UUID,
        expectedRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult {
        if (expectedRevision < 0) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
        }
        val payload = "unlink-task|$sessionId|$expectedRevision"
        return safely {
            database.withTransaction {
                val generation = ensureGeneration().generationId
                if (generation != context.generation.toString()) {
                    return@withTransaction FocusProductResult.SessionConflict(actualRevision = null)
                }
                val replay = replay(context, generation, payload)
                if (replay != null) return@withTransaction replay

                val session = focusDao.session(sessionId.toString())
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
                if (session.revision != expectedRevision) {
                    return@withTransaction FocusProductResult.SessionConflict(session.revision)
                }
                if (session.state !in TERMINAL_STATES || session.openSlot != null ||
                    session.taskId == null || !session.taskOrigin
                ) {
                    return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
                }
                val nextSessionRevision = nextRevision(session.revision)
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
                val now = maxOf(
                    clock.instant(),
                    java.time.Instant.ofEpochSecond(session.updatedAtSeconds, session.updatedAtNanos.toLong()),
                )
                check(focusDao.updateSession(
                    session.copy(
                        taskId = null,
                        revision = nextSessionRevision,
                        updatedAtSeconds = now.epochSecond,
                        updatedAtNanos = now.nano,
                    ),
                ) == 1)
                insertReceipt(generation, context, payload)
                FocusProductResult.Applied
            }
        }
    }

    /** Metadata edits preserve taskId and taskOrigin; those fields are not user-editable here. */
    override suspend fun updateSessionMetadata(
        sessionId: UUID,
        expectedRevision: Long,
        name: String?,
        tagName: String?,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult {
        if (expectedRevision < 0 || expectedCatalogRevision < 0) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
        }
        val normalizedTag = try { tagName?.let(::normalizeFocusTagName) } catch (_: IllegalArgumentException) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
        }
        val payload = listOf(
            "session-metadata", sessionId.toString(), expectedRevision.toString(), expectedCatalogRevision.toString(),
            encodePayloadPart(name), encodePayloadPart(normalizedTag),
        ).joinToString("|")
        return safely {
            database.withTransaction {
                val generation = ensureGeneration().generationId
                if (generation != context.generation.toString()) {
                    return@withTransaction FocusProductResult.SessionConflict(actualRevision = null)
                }
                ensureTagCatalogState()
                val replay = replay(context, generation, payload)
                if (replay != null) return@withTransaction replay
                val currentCatalog = tagCatalogSnapshot()
                if (currentCatalog.revision != expectedCatalogRevision) {
                    return@withTransaction FocusProductResult.TagCatalogConflict(currentCatalog)
                }
                val session = focusDao.session(sessionId.toString())
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
                if (session.state !in TERMINAL_STATES || session.openSlot != null) {
                    return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
                }
                if (session.revision != expectedRevision) return@withTransaction FocusProductResult.SessionConflict(session.revision)
                val normalizedName = if (session.name == null && name == null) null else runCatching {
                    normalizeFocusStartInput(com.luma.lifeos.domain.focus.FocusStartInput(name ?: "", normalizedTag)).name
                }.getOrElse {
                    return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
                }
                val nextSessionRevision = nextRevision(session.revision)
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_SESSION_EDIT)
                val tag = if (normalizedTag == null) null else {
                    resolveOrCreateTag(normalizedTag)
                        ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
                }
                val updatedAt = clock.instant()
                check(focusDao.updateSession(
                    session.copy(
                        name = normalizedName,
                        tagId = tag?.id?.toString(),
                        revision = nextSessionRevision,
                        updatedAtSeconds = updatedAt.epochSecond,
                        updatedAtNanos = updatedAt.nano,
                    ),
                ) == 1)
                insertReceipt(generation, context, payload)
                FocusProductResult.Applied
            }
        }
    }

    override suspend fun renameTag(
        tagId: UUID,
        name: String,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult {
        val normalizedName = try { normalizeFocusTagName(name) } catch (_: IllegalArgumentException) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
        }
        if (expectedCatalogRevision < 0) return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
        val normalizedIdentity = normalizeFocusTagIdentity(normalizedName)
        val payload = "rename-tag|$tagId|$expectedCatalogRevision|${encodePayloadPart(normalizedName)}"
        return safely {
            database.withTransaction {
                val generation = ensureGeneration().generationId
                if (generation != context.generation.toString()) return@withTransaction tagCatalogConflict()
                ensureTagCatalogState()
                val replay = replay(context, generation, payload)
                if (replay != null) return@withTransaction replay
                val state = requireNotNull(dao.tagCatalogState())
                if (state.revision != expectedCatalogRevision) return@withTransaction tagCatalogConflict()
                val source = focusDao.tag(tagId.toString())
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
                val target = focusDao.tagByNormalizedName(normalizedIdentity)
                    ?: dao.tagAlias(normalizedIdentity)?.let { focusDao.tag(it.targetTagId) }
                if (target != null && target.id != source.id) {
                    return@withTransaction FocusProductResult.MergeRequired(source.toDomain(), target.toDomain())
                }
                if (source.name == normalizedName && source.normalizedName == normalizedIdentity) {
                    insertReceipt(generation, context, payload)
                    return@withTransaction FocusProductResult.Applied
                }
                val nextCatalogRevision = nextRevision(state.revision)
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
                val updatedAt = clock.instant()
                if (focusDao.updateTag(source.copy(name = normalizedName, normalizedName = normalizedIdentity)) != 1) {
                    throw IllegalStateException("Focus tag could not be renamed")
                }
                if (source.normalizedName != normalizedIdentity) {
                    dao.replaceTagAlias(FocusTagAliasEntity(source.normalizedName, source.id, source.id))
                }
                dao.deleteTagAlias(normalizedIdentity)
                bumpSessionsForTag(source.id, updatedAt)
                check(dao.updateTagCatalogState(state.copy(revision = nextCatalogRevision)) == 1)
                insertReceipt(generation, context, payload)
                FocusProductResult.Applied
            }
        }
    }

    override suspend fun mergeTags(
        sourceTagId: UUID,
        targetTagId: UUID,
        expectedCatalogRevision: Long,
        context: FocusProductContext,
    ): FocusProductResult {
        if (sourceTagId == targetTagId || expectedCatalogRevision < 0) {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
        }
        val payload = "merge-tags|$sourceTagId|$targetTagId|$expectedCatalogRevision"
        return safely {
            database.withTransaction {
                val generation = ensureGeneration().generationId
                if (generation != context.generation.toString()) return@withTransaction tagCatalogConflict()
                ensureTagCatalogState()
                val replay = replay(context, generation, payload)
                if (replay != null) return@withTransaction replay
                val state = requireNotNull(dao.tagCatalogState())
                if (state.revision != expectedCatalogRevision) return@withTransaction tagCatalogConflict()
                val source = focusDao.tag(sourceTagId.toString())
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
                val target = focusDao.tag(targetTagId.toString())
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
                val nextCatalogRevision = nextRevision(state.revision)
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_TAG)
                val affected = focusDao.sessionsForTag(source.id).map { session ->
                    val nextSessionRevision = nextRevision(session.revision)
                        ?: throw IllegalStateException("Focus session revision is exhausted")
                    session to nextSessionRevision
                }
                val updatedAt = clock.instant()
                dao.retargetAliases(source.id, target.id)
                dao.replaceTagAlias(FocusTagAliasEntity(source.normalizedName, source.id, target.id))
                for ((session, nextSessionRevision) in affected) {
                    check(focusDao.updateSession(
                        session.copy(
                            tagId = target.id,
                            revision = nextSessionRevision,
                            updatedAtSeconds = updatedAt.epochSecond,
                            updatedAtNanos = updatedAt.nano,
                        ),
                    ) == 1)
                    updateOpenRuntimeRevision(session, nextSessionRevision)
                }
                check(focusDao.deleteTag(source.id) == 1)
                check(dao.updateTagCatalogState(state.copy(revision = nextCatalogRevision)) == 1)
                insertReceipt(generation, context, payload)
                FocusProductResult.Applied
            }
        }
    }

    override suspend fun saveReflection(
        sessionId: UUID,
        expectedRevision: Long,
        reflection: FocusReflection,
        context: FocusProductContext,
    ): FocusProductResult {
        val normalized = runCatching { normalizeFocusReflection(reflection) }.getOrElse {
            return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_REFLECTION)
        }
        if (expectedRevision < 0) return FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_REFLECTION)
        val encodedNextStep = normalized.nextStep?.let {
            Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
        } ?: "-"
        val payload = "reflection|$sessionId|$expectedRevision|${normalized.outcome?.name ?: "-"}|$encodedNextStep"
        return safely {
            database.withTransaction {
                val generation = ensureGeneration().generationId
                if (generation != context.generation.toString()) {
                    return@withTransaction FocusProductResult.SessionConflict(actualRevision = null)
                }
                val replay = replay(context, generation, payload)
                if (replay != null) return@withTransaction replay

                val session = focusDao.session(sessionId.toString())
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_REFLECTION)
                if (session.state !in setOf("FINISHED", "INTERRUPTED") || session.openSlot != null) {
                    return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_REFLECTION)
                }
                if (session.revision != expectedRevision) {
                    return@withTransaction FocusProductResult.SessionConflict(session.revision)
                }
                val nextRevision = runCatching { Math.addExact(session.revision, 1L) }.getOrNull()
                    ?: return@withTransaction FocusProductResult.Invalid(FocusProductInvalidReason.INVALID_REFLECTION)
                val updatedAt = clock.instant()
                check(focusDao.updateSession(
                    session.copy(
                        revision = nextRevision,
                        updatedAtSeconds = updatedAt.epochSecond,
                        updatedAtNanos = updatedAt.nano,
                        outcome = normalized.outcome?.name,
                        nextStep = normalized.nextStep,
                    ),
                ) == 1)
                dao.insertReceipt(
                    FocusProductCommandReceiptEntity(generation, context.commandId.toString(), payload, RESULT_APPLIED),
                )
                FocusProductResult.Applied
            }
        }
    }

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

    private suspend fun resolveOrCreateTag(name: String): FocusTag? {
        val normalized = normalizeFocusTagIdentity(name)
        focusDao.tagByNormalizedName(normalized)?.let { return it.toDomain() }
        dao.tagAlias(normalized)?.let { alias ->
            return focusDao.tag(alias.targetTagId)?.toDomain()
        }
        val state = requireNotNull(dao.tagCatalogState())
        val nextCatalogRevision = nextRevision(state.revision) ?: return null
        val tag = FocusTagEntity(UUID.randomUUID().toString(), name, normalized)
        focusDao.insertTag(tag)
        check(dao.updateTagCatalogState(state.copy(revision = nextCatalogRevision)) == 1)
        return tag.toDomain()
    }

    private suspend fun bumpSessionsForTag(tagId: String, updatedAt: java.time.Instant) {
        for (session in focusDao.sessionsForTag(tagId)) {
            val nextSessionRevision = nextRevision(session.revision)
                ?: throw IllegalStateException("Focus session revision is exhausted")
            check(focusDao.updateSession(
                session.copy(
                    revision = nextSessionRevision,
                    updatedAtSeconds = updatedAt.epochSecond,
                    updatedAtNanos = updatedAt.nano,
                ),
            ) == 1)
            updateOpenRuntimeRevision(session, nextSessionRevision)
        }
    }

    private suspend fun updateOpenRuntimeRevision(session: FocusSessionEntity, nextSessionRevision: Long) {
        if (session.openSlot == null || session.state != "RUNNING") return
        val runtime = focusDao.runtime() ?: throw IllegalStateException("Running focus session has no runtime")
        check(runtime.sessionId == session.id && runtime.revision == session.revision) {
            "Running focus session and runtime revisions diverged"
        }
        check(focusDao.updateRuntime(runtime.copy(revision = nextSessionRevision)) == 1)
    }

    private suspend fun tagCatalogSnapshot(): FocusTagCatalog {
        val state = ensureTagCatalogState()
        val generation = ensureGeneration().generationId
        return FocusTagCatalog(
            revision = state.revision,
            tags = focusDao.tags().map(FocusTagEntity::toDomain),
            mergedIds = dao.tagAliases().associate {
                UUID.fromString(it.formerTagId) to UUID.fromString(it.targetTagId)
            },
            databaseGeneration = UUID.fromString(generation),
        )
    }

    private suspend fun tagCatalogConflict(): FocusProductResult.TagCatalogConflict =
        FocusProductResult.TagCatalogConflict(tagCatalogSnapshot())

    private suspend fun ensureTagCatalogState(): FocusTagCatalogStateEntity {
        dao.insertTagCatalogStateIfAbsent(FocusTagCatalogStateEntity())
        return requireNotNull(dao.tagCatalogState())
    }

    private suspend fun insertReceipt(generation: String, context: FocusProductContext, payload: String) {
        dao.insertReceipt(
            FocusProductCommandReceiptEntity(generation, context.commandId.toString(), payload, RESULT_APPLIED),
        )
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
        val TERMINAL_STATES = setOf("FINISHED", "INTERRUPTED")
    }
}

private fun FocusTagEntity.toDomain() = FocusTag(UUID.fromString(id), name)

private fun nextRevision(value: Long): Long? = runCatching { Math.addExact(value, 1L) }.getOrNull()

private fun encodePayloadPart(value: String?): String = value?.let {
    Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
} ?: "-"

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
