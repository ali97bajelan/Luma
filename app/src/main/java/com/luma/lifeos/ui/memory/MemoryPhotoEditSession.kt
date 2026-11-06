package com.luma.lifeos.ui.memory

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoCrop
import com.luma.lifeos.domain.model.PhotoEdit
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoMetadata
import com.luma.lifeos.domain.repository.PhotoRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class MemoryPhotoRetryStage { IMPORT, TRANSFORM, ATTACH }
internal enum class MemoryPhotoEditError { IMPORT, TRANSFORM, ATTACH, ACCESS, RESTORE }

internal sealed interface MemoryPhotoEditState {
    internal data object Idle : MemoryPhotoEditState
    internal data class Importing(
        val operationId: UUID,
        val stagingLeaseId: UUID,
        val sourceId: UUID,
        val sourceUri: String,
    ) : MemoryPhotoEditState
    internal data class Editing(
        val operationId: UUID,
        val stagingLeaseId: UUID,
        val source: PhotoAttachment,
        val edit: PhotoEdit,
        val outputId: UUID? = null,
        val output: PhotoAttachment? = null,
    ) : MemoryPhotoEditState
    internal data class Applying(
        val operationId: UUID,
        val stagingLeaseId: UUID,
        val source: PhotoAttachment,
        val edit: PhotoEdit,
        val outputId: UUID,
        val keepOriginal: Boolean,
        val output: PhotoAttachment? = null,
        val attaching: Boolean = false,
    ) : MemoryPhotoEditState
    internal data class Failed(
        val operationId: UUID,
        val stagingLeaseId: UUID,
        val sourceId: UUID,
        val sourceUri: String?,
        val source: PhotoAttachment?,
        val edit: PhotoEdit,
        val outputId: UUID?,
        val output: PhotoAttachment?,
        val retryStage: MemoryPhotoRetryStage,
        val error: MemoryPhotoEditError,
        val keepOriginal: Boolean = false,
    ) : MemoryPhotoEditState
}

/** Owns one staged selection and transfers its lease only after the editor draft has persisted it. */
internal class MemoryPhotoEditSession(
    private val repository: PhotoRepository?,
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val currentPhotos: () -> List<PhotoAttachment>,
    private val editorLeaseId: UUID,
    private val canEdit: () -> Boolean,
    private val canAccess: () -> Boolean,
    private val accessIsRegistered: (UUID) -> Boolean,
    private val openAccess: (UUID) -> AutoCloseable?,
    private val attach: suspend (PhotoAttachment) -> Unit,
    private val onError: () -> Unit,
) {
    private val mutableState = MutableStateFlow<MemoryPhotoEditState>(MemoryPhotoEditState.Idle)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var operationJob: Job? = null
    private var accessHandle: AutoCloseable? = null

    fun select(sourceUri: String) {
        if (!canEdit()) return
        val previous = state.value
        if (previous !is MemoryPhotoEditState.Idle &&
            !(previous is MemoryPhotoEditState.Failed && previous.retryStage == MemoryPhotoRetryStage.IMPORT)
        ) return
        if (currentPhotos().size >= PhotoLimits.COUNT) { onError(); return }
        if (previous !is MemoryPhotoEditState.Idle) cancel()

        val sourceId = UUID.randomUUID()
        val pending = MemoryPhotoEditState.Importing(UUID.randomUUID(), newStagingLeaseId(sourceId), sourceId, sourceUri)
        generation++
        publish(pending)
        if (!ensureAccess(pending.stagingLeaseId)) {
            fail(pending, MemoryPhotoRetryStage.IMPORT, MemoryPhotoEditError.ACCESS)
            return
        }
        val token = generation
        operationJob = scope.launch {
            try {
                val photos = requireNotNull(repository) { "Photo storage is unavailable" }
                val imported = photos.import(pending.stagingLeaseId, pending.sourceId, pending.sourceUri)
                if (!current(token, pending.operationId)) return@launch
                check(photos.isRetainedBy(pending.stagingLeaseId, imported)) { "Staged photo lease is unavailable" }
                publish(MemoryPhotoEditState.Editing(pending.operationId, pending.stagingLeaseId, imported, PhotoEdit.IDENTITY))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (current(token, pending.operationId)) {
                    fail(pending, MemoryPhotoRetryStage.IMPORT,
                        if (canAccess()) MemoryPhotoEditError.IMPORT else MemoryPhotoEditError.ACCESS)
                }
            }
        }
    }

    fun setCrop(crop: PhotoCrop) {
        val editing = editableState() ?: return
        if (editing.edit.crop == crop) return
        publish(editing.copy(operationId = UUID.randomUUID(), edit = editing.edit.copy(crop = crop), outputId = null, output = null))
    }

    fun rotateClockwise() {
        val editing = editableState() ?: return
        publish(editing.copy(operationId = UUID.randomUUID(), edit = editing.edit.rotateClockwise(), outputId = null, output = null))
    }

    fun reset() {
        val editing = editableState() ?: return
        if (editing.edit == PhotoEdit.IDENTITY) return
        publish(editing.copy(operationId = UUID.randomUUID(), edit = PhotoEdit.IDENTITY, outputId = null, output = null))
    }

    fun confirm(keepOriginal: Boolean) {
        if (!canEdit()) return
        val editing = when (val current = state.value) {
            is MemoryPhotoEditState.Editing -> current
            is MemoryPhotoEditState.Failed -> current.takeIf { it.source != null }?.let {
                val samePayload = keepOriginal == it.keepOriginal
                MemoryPhotoEditState.Editing(
                    it.operationId, it.stagingLeaseId, requireNotNull(it.source), it.edit,
                    it.outputId.takeIf { samePayload }, it.output.takeIf { samePayload },
                )
            }
            else -> null
        } ?: return
        val useSource = keepOriginal || editing.edit == PhotoEdit.IDENTITY
        val outputId = if (useSource) editing.source.id else editing.outputId ?: UUID.randomUUID()
        val payload = MemoryPhotoEditState.Applying(
            operationId = editing.operationId,
            stagingLeaseId = editing.stagingLeaseId,
            source = editing.source,
            edit = editing.edit,
            outputId = outputId,
            keepOriginal = keepOriginal,
            output = if (useSource) editing.source else editing.output,
        )
        generation++
        val token = generation
        publish(payload)
        if (!ensureAccess(payload.stagingLeaseId)) {
            fail(payload, if (useSource) MemoryPhotoRetryStage.ATTACH else MemoryPhotoRetryStage.TRANSFORM,
                MemoryPhotoEditError.ACCESS)
            return
        }
        operationJob = scope.launch {
            var retryStage = if (useSource || payload.output != null) MemoryPhotoRetryStage.ATTACH
                else MemoryPhotoRetryStage.TRANSFORM
            try {
                val photos = requireNotNull(repository) { "Photo storage is unavailable" }
                val result = if (useSource) payload.source else {
                    payload.output?.takeIf { photos.isRetainedBy(payload.stagingLeaseId, it) }
                        ?: photos.transform(payload.stagingLeaseId, payload.outputId, payload.source, payload.edit)
                }
                if (!current(token, payload.operationId)) return@launch
                check(photos.isRetainedBy(payload.stagingLeaseId, result)) { "Edited photo lease is unavailable" }
                val readyToAttach = payload.copy(output = result, attaching = true)
                publish(readyToAttach)
                retryStage = MemoryPhotoRetryStage.ATTACH
                attach(result)
                if (!current(token, payload.operationId)) return@launch
                // The memory draft now owns the selected ID; only then may staging be released.
                releaseStage(payload.stagingLeaseId)
                if (operationMatches(token, payload.operationId) && canAccess()) publish(MemoryPhotoEditState.Idle)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (current(token, payload.operationId)) {
                    val available = when (val active = state.value) {
                        is MemoryPhotoEditState.Applying -> active.output
                        else -> payload.output
                    }
                    fail(payload.copy(output = available), retryStage,
                        if (canAccess()) when (retryStage) {
                            MemoryPhotoRetryStage.IMPORT -> MemoryPhotoEditError.IMPORT
                            MemoryPhotoRetryStage.TRANSFORM -> MemoryPhotoEditError.TRANSFORM
                            MemoryPhotoRetryStage.ATTACH -> MemoryPhotoEditError.ATTACH
                        } else MemoryPhotoEditError.ACCESS)
                }
            }
        }
    }

    fun retry() {
        when (val current = state.value) {
            is MemoryPhotoEditState.Failed -> when (current.retryStage) {
                MemoryPhotoRetryStage.IMPORT -> {
                    val uri = current.sourceUri ?: return
                    generation++
                    val token = generation
                    val pending = MemoryPhotoEditState.Importing(current.operationId, current.stagingLeaseId, current.sourceId, uri)
                    publish(pending)
                    if (!ensureAccess(current.stagingLeaseId)) {
                        fail(current, MemoryPhotoRetryStage.IMPORT, MemoryPhotoEditError.ACCESS)
                        return
                    }
                    operationJob = scope.launch {
                        try {
                            val photos = requireNotNull(repository)
                            val imported = photos.import(current.stagingLeaseId, current.sourceId, uri)
                            if (!this@MemoryPhotoEditSession.current(token, current.operationId)) return@launch
                            check(photos.isRetainedBy(current.stagingLeaseId, imported))
                            publish(MemoryPhotoEditState.Editing(current.operationId, current.stagingLeaseId, imported, PhotoEdit.IDENTITY))
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            if (this@MemoryPhotoEditSession.current(token, current.operationId)) {
                                fail(current, MemoryPhotoRetryStage.IMPORT,
                                    if (canAccess()) MemoryPhotoEditError.IMPORT else MemoryPhotoEditError.ACCESS)
                            }
                        }
                    }
                }
                MemoryPhotoRetryStage.TRANSFORM, MemoryPhotoRetryStage.ATTACH -> confirm(current.keepOriginal)
            }
            else -> Unit
        }
    }

    /** Restores references only after the parent memory route has re-authorized its lease. */
    suspend fun restore() {
        var restored = readSavedState() ?: return
        if (restored is MemoryPhotoEditState.Idle) return
        val restoredSourceId = sourceIdOf(restored)
        if (stagingLeaseOf(restored) == editorLeaseId ||
            stagingLeaseOf(restored) != newStagingLeaseId(restoredSourceId) ||
            sourceOf(restored)?.let { it.id != restoredSourceId } == true
        ) restored = invalidSavedState()
        generation++
        val token = generation
        operationJob?.cancel()
        mutableState.value = restored
        if (!canAccess() || !ensureAccess(restored.stagingLeaseId)) {
            if (token == generation) fail(restored, restoredRetryStage(restored), MemoryPhotoEditError.ACCESS)
            return
        }
        val photos = repository ?: run {
            if (token == generation) fail(restored, restoredRetryStage(restored), MemoryPhotoEditError.IMPORT)
            return
        }
        val output = outputOf(restored)
        val outputId = outputIdOf(restored)
        val source = sourceOf(restored)
        if (source != null && !photos.isRetainedBy(restored.stagingLeaseId, source)) {
            if (token == generation) publish(invalidSavedState())
            return
        }
        if (token != generation) return
        if (output != null && currentPhotos().any { it.id == output.id }) {
            releaseStage(restored.stagingLeaseId)
            if (token == generation) publish(MemoryPhotoEditState.Idle)
            return
        }
        val recoveredOutput = output?.takeIf { photos.isRetainedBy(restored.stagingLeaseId, it) }
        if (token != generation) return
        when (restored) {
            is MemoryPhotoEditState.Importing -> fail(restored, MemoryPhotoRetryStage.IMPORT, MemoryPhotoEditError.IMPORT)
            is MemoryPhotoEditState.Editing -> publish(restored.copy(outputId = outputId, output = recoveredOutput))
            is MemoryPhotoEditState.Applying -> publish(MemoryPhotoEditState.Editing(
                restored.operationId, restored.stagingLeaseId, restored.source, restored.edit,
                if (restored.keepOriginal) null else restored.outputId,
                if (restored.keepOriginal) null else recoveredOutput,
            ))
            is MemoryPhotoEditState.Failed -> publish(restored.copy(outputId = outputId, output = recoveredOutput))
            MemoryPhotoEditState.Idle -> Unit
        }
    }

    /** Route disposal invalidates in-flight completions but keeps the staged files recoverable. */
    fun onRouteClosed() {
        generation++
        operationJob?.cancel()
        operationJob = null
        closeAccess()
    }

    fun cancel() {
        val current = state.value
        if (current is MemoryPhotoEditState.Idle || (current is MemoryPhotoEditState.Applying && current.attaching)) return
        generation++
        operationJob?.cancel()
        operationJob = null
        val leaseId = stagingLeaseOf(current)
        closeAccess()
        publish(MemoryPhotoEditState.Idle)
        if (leaseId == editorLeaseId) {
            onError()
            return
        }
        scope.launch {
            try { repository?.releaseDraft(leaseId) } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
            }
            try { repository?.cleanup() } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
            }
        }
    }

    private fun editableState(): MemoryPhotoEditState.Editing? {
        if (!canEdit()) return null
        return when (val current = state.value) {
            is MemoryPhotoEditState.Editing -> current
            is MemoryPhotoEditState.Failed -> current.source?.let {
                MemoryPhotoEditState.Editing(current.operationId, current.stagingLeaseId, it, current.edit,
                    current.outputId, current.output)
            }
            else -> null
        }
    }

    private fun ensureAccess(leaseId: UUID): Boolean {
        if (leaseId == editorLeaseId || !canAccess()) return false
        if (accessHandle != null && accessIsRegistered(leaseId)) return true
        closeAccess()
        return try {
            val handle = openAccess(leaseId) ?: return false
            accessHandle = handle
            if (accessIsRegistered(leaseId)) true else {
                closeAccess()
                false
            }
        } catch (_: Exception) { false }
    }

    private fun closeAccess() {
        accessHandle?.close()
        accessHandle = null
    }

    private fun operationMatches(token: Long, operationId: UUID): Boolean =
        token == generation && state.value !is MemoryPhotoEditState.Idle && operationIdOf(state.value) == operationId

    private fun current(token: Long, operationId: UUID): Boolean =
        operationMatches(token, operationId) && canAccess() && accessIsRegistered(stagingLeaseOf(state.value))

    private fun fail(
        state: MemoryPhotoEditState,
        retryStage: MemoryPhotoRetryStage,
        error: MemoryPhotoEditError,
    ) {
        val source = sourceOf(state)
        val failed = MemoryPhotoEditState.Failed(
            operationId = operationIdOf(state),
            stagingLeaseId = stagingLeaseOf(state),
            sourceId = source?.id ?: sourceIdOf(state),
            sourceUri = sourceUriOf(state),
            source = source,
            edit = editOf(state),
            outputId = outputIdOf(state),
            output = outputOf(state),
            retryStage = retryStage,
            error = error,
            keepOriginal = (state as? MemoryPhotoEditState.Applying)?.keepOriginal
                ?: (state as? MemoryPhotoEditState.Failed)?.keepOriginal
                ?: false,
        )
        publish(failed)
        onError()
    }

    private suspend fun releaseStage(leaseId: UUID) {
        if (leaseId == editorLeaseId) return
        closeAccess()
        try { repository?.releaseDraft(leaseId) } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
        }
        try { repository?.cleanup() } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
        }
    }

    private fun publish(next: MemoryPhotoEditState) {
        mutableState.value = next
        persist(next)
    }

    private fun persist(value: MemoryPhotoEditState) {
        saved[KEY_STATE] = when (value) {
            MemoryPhotoEditState.Idle -> "idle"
            is MemoryPhotoEditState.Importing -> "importing"
            is MemoryPhotoEditState.Editing -> "editing"
            is MemoryPhotoEditState.Applying -> "applying"
            is MemoryPhotoEditState.Failed -> "failed"
        }
        if (value is MemoryPhotoEditState.Idle) {
            STATE_KEYS.drop(1).forEach { saved[it] = null }
            writePhoto(KEY_SOURCE, null)
            writePhoto(KEY_OUTPUT, null)
            return
        }
        saved[KEY_OPERATION] = operationIdOf(value).toString()
        saved[KEY_STAGE] = stagingLeaseOf(value).toString()
        saved[KEY_SOURCE_ID] = sourceIdOf(value).toString()
        saved[KEY_SOURCE_URI] = sourceUriOf(value)
        saved[KEY_EDIT_TURNS] = editOf(value).clockwiseQuarterTurns
        saved[KEY_CROP_LEFT] = editOf(value).crop.left
        saved[KEY_CROP_TOP] = editOf(value).crop.top
        saved[KEY_CROP_RIGHT] = editOf(value).crop.right
        saved[KEY_CROP_BOTTOM] = editOf(value).crop.bottom
        saved[KEY_OUTPUT_ID] = outputIdOf(value)?.toString()
        saved[KEY_RETRY] = (value as? MemoryPhotoEditState.Failed)?.retryStage?.name
        saved[KEY_ERROR] = (value as? MemoryPhotoEditState.Failed)?.error?.name
        saved[KEY_KEEP_ORIGINAL] = (value as? MemoryPhotoEditState.Applying)?.keepOriginal
            ?: (value as? MemoryPhotoEditState.Failed)?.keepOriginal
        saved[KEY_ATTACHING] = (value as? MemoryPhotoEditState.Applying)?.attaching
        writePhoto(KEY_SOURCE, sourceOf(value))
        writePhoto(KEY_OUTPUT, outputOf(value))
    }

    private fun readSavedState(): MemoryPhotoEditState? = try {
        when (saved.get<String>(KEY_STATE)) {
            null, "idle" -> MemoryPhotoEditState.Idle
            "importing" -> MemoryPhotoEditState.Importing(
                UUID.fromString(requireNotNull(saved.get<String>(KEY_OPERATION))),
                UUID.fromString(requireNotNull(saved.get<String>(KEY_STAGE))),
                UUID.fromString(requireNotNull(saved.get<String>(KEY_SOURCE_ID))),
                requireNotNull(saved.get<String>(KEY_SOURCE_URI)),
            )
            "editing" -> MemoryPhotoEditState.Editing(
                UUID.fromString(requireNotNull(saved.get<String>(KEY_OPERATION))),
                UUID.fromString(requireNotNull(saved.get<String>(KEY_STAGE))),
                requireNotNull(readPhoto(KEY_SOURCE)), readEdit(),
                saved.get<String>(KEY_OUTPUT_ID)?.let(UUID::fromString), readPhoto(KEY_OUTPUT),
            )
            "applying" -> MemoryPhotoEditState.Applying(
                UUID.fromString(requireNotNull(saved.get<String>(KEY_OPERATION))),
                UUID.fromString(requireNotNull(saved.get<String>(KEY_STAGE))),
                requireNotNull(readPhoto(KEY_SOURCE)), readEdit(),
                UUID.fromString(requireNotNull(saved.get<String>(KEY_OUTPUT_ID))),
                saved.get<Boolean>(KEY_KEEP_ORIGINAL) ?: false, readPhoto(KEY_OUTPUT),
                saved.get<Boolean>(KEY_ATTACHING) ?: false,
            )
            "failed" -> MemoryPhotoEditState.Failed(
                UUID.fromString(requireNotNull(saved.get<String>(KEY_OPERATION))),
                UUID.fromString(requireNotNull(saved.get<String>(KEY_STAGE))),
                UUID.fromString(requireNotNull(saved.get<String>(KEY_SOURCE_ID))),
                saved.get<String>(KEY_SOURCE_URI), readPhoto(KEY_SOURCE), readEdit(),
                saved.get<String>(KEY_OUTPUT_ID)?.let(UUID::fromString), readPhoto(KEY_OUTPUT),
                MemoryPhotoRetryStage.valueOf(requireNotNull(saved.get<String>(KEY_RETRY))),
                MemoryPhotoEditError.valueOf(requireNotNull(saved.get<String>(KEY_ERROR))),
                saved.get<Boolean>(KEY_KEEP_ORIGINAL) ?: false,
            )
            else -> invalidSavedState()
        }
    } catch (_: Exception) { invalidSavedState() }

    /** Corrupt saved references cannot authorize cleanup of an arbitrary or parent lease. */
    private fun invalidSavedState(): MemoryPhotoEditState.Failed {
        val sourceId = UUID.randomUUID()
        return MemoryPhotoEditState.Failed(
            operationId = UUID.randomUUID(),
            stagingLeaseId = newStagingLeaseId(sourceId),
            sourceId = sourceId,
            sourceUri = null,
            source = null,
            edit = PhotoEdit.IDENTITY,
            outputId = null,
            output = null,
            retryStage = MemoryPhotoRetryStage.IMPORT,
            error = MemoryPhotoEditError.RESTORE,
        )
    }

    private fun newStagingLeaseId(sourceId: UUID): UUID {
        var discriminator = 0
        var leaseId: UUID
        do {
            leaseId = UUID.nameUUIDFromBytes(
                "$editorLeaseId:$sourceId:$discriminator".toByteArray(Charsets.UTF_8),
            )
            discriminator++
        } while (leaseId == editorLeaseId)
        return leaseId
    }

    private fun writePhoto(prefix: String, photo: PhotoAttachment?) {
        saved["$prefix.id"] = photo?.id?.toString()
        saved["$prefix.path"] = photo?.relativePath
        saved["$prefix.mime"] = photo?.metadata?.mimeType
        saved["$prefix.bytes"] = photo?.metadata?.byteCount
        saved["$prefix.width"] = photo?.metadata?.width
        saved["$prefix.height"] = photo?.metadata?.height
    }

    private fun readPhoto(prefix: String): PhotoAttachment? {
        val id = saved.get<String>("$prefix.id")?.let(UUID::fromString) ?: return null
        val path = saved.get<String>("$prefix.path") ?: return null
        val mime = saved.get<String>("$prefix.mime")
        val metadata = if (mime == null) null else PhotoMetadata(
            mime, requireNotNull(saved.get<Long>("$prefix.bytes")),
            requireNotNull(saved.get<Int>("$prefix.width")), requireNotNull(saved.get<Int>("$prefix.height")),
        )
        return PhotoAttachment(id, path, metadata)
    }

    private fun readEdit() = PhotoEdit(
        saved.get<Int>(KEY_EDIT_TURNS) ?: 0,
        PhotoCrop(
            saved.get<Double>(KEY_CROP_LEFT) ?: 0.0,
            saved.get<Double>(KEY_CROP_TOP) ?: 0.0,
            saved.get<Double>(KEY_CROP_RIGHT) ?: 1.0,
            saved.get<Double>(KEY_CROP_BOTTOM) ?: 1.0,
        ),
    )

    private fun operationIdOf(value: MemoryPhotoEditState) = when (value) {
        MemoryPhotoEditState.Idle -> error("Idle has no operation")
        is MemoryPhotoEditState.Importing -> value.operationId
        is MemoryPhotoEditState.Editing -> value.operationId
        is MemoryPhotoEditState.Applying -> value.operationId
        is MemoryPhotoEditState.Failed -> value.operationId
    }
    private fun stagingLeaseOf(value: MemoryPhotoEditState) = when (value) {
        MemoryPhotoEditState.Idle -> error("Idle has no lease")
        is MemoryPhotoEditState.Importing -> value.stagingLeaseId
        is MemoryPhotoEditState.Editing -> value.stagingLeaseId
        is MemoryPhotoEditState.Applying -> value.stagingLeaseId
        is MemoryPhotoEditState.Failed -> value.stagingLeaseId
    }
    private fun sourceIdOf(value: MemoryPhotoEditState) = when (value) {
        MemoryPhotoEditState.Idle -> error("Idle has no source")
        is MemoryPhotoEditState.Importing -> value.sourceId
        is MemoryPhotoEditState.Editing -> value.source.id
        is MemoryPhotoEditState.Applying -> value.source.id
        is MemoryPhotoEditState.Failed -> value.sourceId
    }
    private fun sourceUriOf(value: MemoryPhotoEditState): String? = when (value) {
        MemoryPhotoEditState.Idle -> null
        is MemoryPhotoEditState.Importing -> value.sourceUri
        is MemoryPhotoEditState.Editing -> null
        is MemoryPhotoEditState.Applying -> null
        is MemoryPhotoEditState.Failed -> value.sourceUri
    }
    private fun sourceOf(value: MemoryPhotoEditState): PhotoAttachment? = when (value) {
        MemoryPhotoEditState.Idle -> null
        is MemoryPhotoEditState.Importing -> null
        is MemoryPhotoEditState.Editing -> value.source
        is MemoryPhotoEditState.Applying -> value.source
        is MemoryPhotoEditState.Failed -> value.source
    }
    private fun outputOf(value: MemoryPhotoEditState): PhotoAttachment? = when (value) {
        MemoryPhotoEditState.Idle -> null
        is MemoryPhotoEditState.Importing -> null
        is MemoryPhotoEditState.Editing -> value.output
        is MemoryPhotoEditState.Applying -> value.output
        is MemoryPhotoEditState.Failed -> value.output
    }
    private fun outputIdOf(value: MemoryPhotoEditState): UUID? = when (value) {
        MemoryPhotoEditState.Idle -> null
        is MemoryPhotoEditState.Importing -> null
        is MemoryPhotoEditState.Editing -> value.outputId
        is MemoryPhotoEditState.Applying -> value.outputId
        is MemoryPhotoEditState.Failed -> value.outputId
    }
    private fun editOf(value: MemoryPhotoEditState): PhotoEdit = when (value) {
        MemoryPhotoEditState.Idle -> PhotoEdit.IDENTITY
        is MemoryPhotoEditState.Importing -> PhotoEdit.IDENTITY
        is MemoryPhotoEditState.Editing -> value.edit
        is MemoryPhotoEditState.Applying -> value.edit
        is MemoryPhotoEditState.Failed -> value.edit
    }
    private fun restoredRetryStage(value: MemoryPhotoEditState) = when (value) {
        is MemoryPhotoEditState.Importing -> MemoryPhotoRetryStage.IMPORT
        is MemoryPhotoEditState.Editing, is MemoryPhotoEditState.Applying -> MemoryPhotoRetryStage.TRANSFORM
        is MemoryPhotoEditState.Failed -> value.retryStage
        MemoryPhotoEditState.Idle -> MemoryPhotoRetryStage.IMPORT
    }

    private companion object {
        const val KEY_STATE = "memory_photo_edit_state"
        const val KEY_OPERATION = "memory_photo_edit_operation"
        const val KEY_STAGE = "memory_photo_edit_stage_lease"
        const val KEY_SOURCE_ID = "memory_photo_edit_source_id"
        const val KEY_SOURCE_URI = "memory_photo_edit_source_uri"
        const val KEY_SOURCE = "memory_photo_edit_source"
        const val KEY_OUTPUT = "memory_photo_edit_output"
        const val KEY_OUTPUT_ID = "memory_photo_edit_output_id"
        const val KEY_EDIT_TURNS = "memory_photo_edit_turns"
        const val KEY_CROP_LEFT = "memory_photo_edit_crop_left"
        const val KEY_CROP_TOP = "memory_photo_edit_crop_top"
        const val KEY_CROP_RIGHT = "memory_photo_edit_crop_right"
        const val KEY_CROP_BOTTOM = "memory_photo_edit_crop_bottom"
        const val KEY_KEEP_ORIGINAL = "memory_photo_edit_keep_original"
        const val KEY_ATTACHING = "memory_photo_edit_attaching"
        const val KEY_RETRY = "memory_photo_edit_retry"
        const val KEY_ERROR = "memory_photo_edit_error"
        val STATE_KEYS = listOf(
            KEY_STATE, KEY_OPERATION, KEY_STAGE, KEY_SOURCE_ID, KEY_SOURCE_URI, KEY_SOURCE, KEY_OUTPUT,
            KEY_OUTPUT_ID, KEY_EDIT_TURNS, KEY_CROP_LEFT, KEY_CROP_TOP, KEY_CROP_RIGHT, KEY_CROP_BOTTOM,
            KEY_KEEP_ORIGINAL, KEY_ATTACHING, KEY_RETRY, KEY_ERROR,
        )
    }
}
