package com.luma.lifeos.lock

import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockAccess
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.LockSnapshot
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.MemoryInput
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.repository.JournalRepository
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.repository.MoodRepository
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.normalizeTodoInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CaptureKind { MEMORY, JOURNAL, MOOD, TASK, HABIT_DEFINITION }

data class CaptureDraft(
    val id: UUID,
    val kind: CaptureKind,
    val revision: Long,
    val idempotencyRevision: UUID,
    val fields: Map<String, String>,
    val photos: List<com.luma.lifeos.domain.model.PhotoAttachment> = emptyList(),
) {
    init {
        require(revision >= 0)
        require(fields.keys.all { it.length <= 64 })
        require(photos.size <= com.luma.lifeos.domain.model.PhotoLimits.COUNT)
    }
}

sealed interface CaptureInput {
    val kind: CaptureKind

    data class Memory(val value: MemoryInput) : CaptureInput { override val kind = CaptureKind.MEMORY }
    data class Journal(val value: JournalInput) : CaptureInput { override val kind = CaptureKind.JOURNAL }
    data class Mood(val value: MoodInput) : CaptureInput { override val kind = CaptureKind.MOOD }
    data class Task(val value: TodoInput) : CaptureInput { override val kind = CaptureKind.TASK }
    data class HabitDefinition(val value: HabitSaveInput) : CaptureInput { override val kind = CaptureKind.HABIT_DEFINITION }
}

/** Process-local, unforgeable create authority. Draft IDs alone never authorize a write. */
class CreateSession internal constructor(
    internal val draftId: UUID,
    val kind: CaptureKind,
    internal val token: UUID = UUID.randomUUID(),
) {
    override fun toString(): String = "CreateSession(<redacted>)"
}

sealed interface CaptureResult {
    data object Saved : CaptureResult
    data object ValidationError : CaptureResult
    data object RetryableFailure : CaptureResult
    data object Conflict : CaptureResult
}

sealed interface CaptureResumeResult {
    data class Ready(val session: CreateSession, val draft: CaptureDraft) : CaptureResumeResult
    data class RequiresUnlock(val request: LockRequest) : CaptureResumeResult
    data object Missing : CaptureResumeResult
    data object Unavailable : CaptureResumeResult
}

/**
 * Owns every raw repository used by restricted capture. The route receives only this narrow API;
 * its session token is checked again inside the shared serialized write guard.
 */
class RestrictedCaptureService(
    private val memories: MemoryRepository,
    private val journals: JournalRepository,
    private val moods: MoodRepository,
    private val todos: TodoRepository,
    private val habits: HabitRepository,
    private val photos: PhotoRepository,
    private val access: LockAccess,
    private val actions: LockActionGuard,
    private val drafts: PrivateCaptureDraftStore,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    val lockSnapshot: StateFlow<LockSnapshot> get() = access.snapshot

    private val active = ConcurrentHashMap<UUID, CreateSession>()
    private val sessionMutex = Mutex()

    suspend fun begin(kind: CaptureKind): CreateSession = sessionMutex.withLock {
        val draft = newDraft(kind)
        drafts.write(draft)
        CreateSession(draft.id, kind).also { active[it.token] = it }
    }

    /** Returns opaque IDs only. It never decrypts a draft or returns private content. */
    suspend fun recoverableDraftIds(): List<UUID> = drafts.listIds()

    suspend fun activeDraft(session: CreateSession): CaptureDraft? =
        if (isActive(session)) drafts.read(session.draftId) else null

    suspend fun resume(draftId: UUID, proof: OperationProof? = null): CaptureResumeResult = sessionMutex.withLock {
        val draft = try {
            drafts.read(draftId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withLock CaptureResumeResult.Unavailable
        } ?: return@withLock CaptureResumeResult.Missing
        val request = readRequest(draft.kind)
        when (access.snapshot.value.decide(request)) {
            AccessDecision.Allow -> {
                val session = CreateSession(draft.id, draft.kind)
                active[session.token] = session
                CaptureResumeResult.Ready(session, draft)
            }
            is AccessDecision.NeedUnlock -> {
                val generation = access.snapshot.value.policy?.generation
                    ?: return@withLock CaptureResumeResult.Unavailable
                val sections = request.requiredSections
                if (proof == null || !proof.bindBrowseRequest(
                        sections = sections, wholeApp = false, currentGeneration = generation,
                    ) || !proof.consumeBrowseRequest(
                        sections = sections, wholeApp = false, currentGeneration = generation,
                    )
                ) return@withLock CaptureResumeResult.RequiresUnlock(request)
                val session = CreateSession(draft.id, draft.kind)
                active[session.token] = session
                CaptureResumeResult.Ready(session, draft)
            }
            is AccessDecision.Deny -> CaptureResumeResult.Unavailable
        }
    }

    suspend fun updateDraft(session: CreateSession, fields: Map<String, String>): CaptureDraft? =
        sessionMutex.withLock {
            if (!isActive(session)) return@withLock null
            val current = drafts.read(session.draftId) ?: return@withLock null
            val updated = current.copy(revision = nextRevision(current.revision), fields = fields.toMap())
            drafts.write(updated)
            updated
        }

    suspend fun addPhoto(session: CreateSession, source: String): CaptureDraft? = sessionMutex.withLock {
        if (!isActive(session) || session.kind !in PHOTO_KINDS) return@withLock null
        val current = drafts.read(session.draftId) ?: return@withLock null
        if (current.photos.size >= com.luma.lifeos.domain.model.PhotoLimits.COUNT) return@withLock null
        val photoId = UUID.randomUUID()
        try {
            val imported = photos.import(session.draftId, photoId, source)
            val updated = current.copy(
                revision = nextRevision(current.revision),
                photos = current.photos + imported,
            )
            photos.retainDraft(session.draftId, updated.photos)
            drafts.write(updated)
            updated
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { photos.retainDraft(session.draftId, current.photos) }
            null
        }
    }

    suspend fun removePhoto(session: CreateSession, photoId: UUID): CaptureDraft? = sessionMutex.withLock {
        if (!isActive(session)) return@withLock null
        val current = drafts.read(session.draftId) ?: return@withLock null
        if (current.photos.none { it.id == photoId }) return@withLock current
        val updated = current.copy(
            revision = nextRevision(current.revision),
            photos = current.photos.filterNot { it.id == photoId },
        )
        drafts.write(updated)
        photos.retainDraft(session.draftId, updated.photos)
        photos.cleanup()
        updated
    }

    /** Decodes only a photo already leased to this live create session. */
    suspend fun previewPhoto(session: CreateSession, photoId: UUID): ByteArray? {
        if (!isActive(session)) return null
        val draft = drafts.read(session.draftId) ?: return null
        val photo = draft.photos.firstOrNull { it.id == photoId } ?: return null
        return photos.preview(photo)
    }

    suspend fun save(session: CreateSession, input: CaptureInput): CaptureResult = sessionMutex.withLock {
        if (!isActive(session) || input.kind != session.kind) return@withLock CaptureResult.Conflict
        val draft = try {
            drafts.read(session.draftId) ?: return@withLock CaptureResult.RetryableFailure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withLock CaptureResult.RetryableFailure
        }
        try {
            validateRestrictedInput(draft, input)
        } catch (_: IllegalArgumentException) {
            return@withLock CaptureResult.ValidationError
        }
        try {
            actions.execute(
                LockRequest(
                    operation = LockOperation.CREATE,
                    requiredSections = setOf(sectionFor(session.kind)),
                    contentClassification = LockContentClassification.CLASSIFIED,
                    createSessionId = session.draftId.toString(),
                ),
            ) {
                // Recheck the capability adjacent to persistence. Cancel and departure share
                // this mutex, so neither can revoke the session during a started commit.
                if (!isActive(session)) throw LockActionDeniedException(
                    AccessDecision.Deny(com.luma.lifeos.domain.lock.AccessDenial.INVALID_CREATE_SESSION),
                )
                when (input) {
                    is CaptureInput.Memory -> memories.create(EventId(draft.id), input.value.copy(photos = draft.photos))
                    is CaptureInput.Journal -> journals.create(EventId(draft.id), input.value.copy(photos = draft.photos))
                    is CaptureInput.Mood -> moods.create(EventId(draft.id), input.value)
                    is CaptureInput.Task -> todos.create(draft.id, draft.idempotencyRevision, input.value)
                    is CaptureInput.HabitDefinition -> habits.create(draft.id, input.value)
                }
            }
            // Stable record IDs/revisions make a retry after commit idempotent. Delete the
            // encrypted draft before releasing its media lease; a cleanup failure can then
            // leave an orphan lease, but can never leave a recoverable draft without media.
            drafts.delete(draft.id)
            active.remove(session.token)
            runCatching { photos.releaseDraft(draft.id) }
            CaptureResult.Saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LockActionDeniedException) {
            CaptureResult.RetryableFailure
        } catch (_: IllegalStateException) {
            CaptureResult.Conflict
        } catch (_: IllegalArgumentException) {
            CaptureResult.Conflict
        } catch (_: Exception) {
            CaptureResult.RetryableFailure
        }
    }

    suspend fun cancel(session: CreateSession) {
        sessionMutex.withLock {
            if (!isActive(session)) return
            drafts.delete(session.draftId)
            active.remove(session.token)
            try {
                photos.releaseDraft(session.draftId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The encrypted draft is already gone; a stale media lease is safe to clean later.
            }
        }
    }

    /** Hides the current session after a real departure while preserving its encrypted draft. */
    suspend fun suspendSession(session: CreateSession) = sessionMutex.withLock {
        if (isActive(session)) active.remove(session.token)
    }

    private fun isActive(session: CreateSession): Boolean = active[session.token] === session

    private fun readRequest(kind: CaptureKind): LockRequest = LockRequest(
        operation = LockOperation.READ,
        requiredSections = setOf(sectionFor(kind)),
        contentClassification = LockContentClassification.CLASSIFIED,
        objectId = null,
    )

    private fun sectionFor(kind: CaptureKind): LockSection = when (kind) {
        CaptureKind.MEMORY -> LockSection.MEMORIES
        CaptureKind.JOURNAL -> LockSection.JOURNAL
        CaptureKind.MOOD -> LockSection.MOOD
        CaptureKind.TASK -> LockSection.TASKS
        CaptureKind.HABIT_DEFINITION -> LockSection.HABITS
    }

    private fun validateRestrictedInput(draft: CaptureDraft, input: CaptureInput) {
        require(input.kind == draft.kind)
        when (input) {
            is CaptureInput.Memory -> require(input.value.personIds == null && input.value.placeSelection == null &&
                input.value.photos.orEmpty().map { it.id }.toSet() == draft.photos.map { it.id }.toSet())
            is CaptureInput.Journal -> require(input.value.personIds == null && input.value.placeSelection == null &&
                input.value.photos.orEmpty().map { it.id }.toSet() == draft.photos.map { it.id }.toSet())
            is CaptureInput.Mood -> require(input.value.personIds == null && input.value.placeSelection == null &&
                input.value.emotionIds.isEmpty())
            is CaptureInput.Task -> normalizeTodoInput(input.value)
            is CaptureInput.HabitDefinition -> Unit
        }
    }

    private suspend fun newDraft(kind: CaptureKind): CaptureDraft {
        val now = Instant.now()
        // Reuse only the habit tracking-zone preference (not any saved habit records) so the
        // existing create contract can accept a new definition on installations with a custom zone.
        val captureZone = if (kind == CaptureKind.HABIT_DEFINITION) habits.trackingZone() ?: zone() else zone()
        val today = LocalDate.now(captureZone)
        val fields = when (kind) {
            CaptureKind.MEMORY, CaptureKind.JOURNAL, CaptureKind.MOOD -> mapOf("occurredAt" to now.toString())
            CaptureKind.TASK -> emptyMap()
            CaptureKind.HABIT_DEFINITION -> mapOf(
                "start" to today.toString(), "target" to "1", "weekdays" to "1,2,3,4,5,6,7",
                "zone" to captureZone.id,
            )
        }
        return CaptureDraft(UUID.randomUUID(), kind, 0L, UUID.randomUUID(), fields)
    }

    private fun nextRevision(revision: Long): Long =
        if (revision == Long.MAX_VALUE) throw IllegalStateException("Capture draft revision exhausted") else revision + 1L

    private companion object {
        val PHOTO_KINDS = setOf(CaptureKind.MEMORY, CaptureKind.JOURNAL)
    }
}
