package com.luma.lifeos.ui.photos

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoMetadata
import com.luma.lifeos.domain.repository.PhotoRepository
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One durable file lease per editor. Pending identity is saved before copying any bytes. */
internal class PhotoDraftSession(
    private val repository: PhotoRepository?, private val saved: SavedStateHandle,
    private val scope: CoroutineScope, private val owner: UUID,
    private val current: () -> List<PhotoAttachment>, private val canEdit: () -> Boolean,
    private val busy: (Boolean) -> Unit, private val update: (List<PhotoAttachment>) -> Unit,
    private val error: () -> Unit,
) {
    suspend fun retain(photos: List<PhotoAttachment>) {
        val pending = saved.get<String>("photo_pending")?.let(UUID::fromString)
        val protected = if (pending == null || photos.any { it.id == pending }) photos
            else photos + PhotoAttachment(pending, "photos/$pending.img")
        repository?.retainDraft(owner, protected)
    }

    fun importPhoto(source: String) {
        if (!canEdit()) return
        if (repository == null || current().size >= PhotoLimits.COUNT) { error(); return }
        val id = if (saved.get<String>("photo_source") == source)
            saved.get<String>("photo_pending")?.let(UUID::fromString) ?: UUID.randomUUID()
        else UUID.randomUUID()
        saved["photo_source"] = source
        saved["photo_pending"] = id.toString()
        busy(true)
        scope.launch {
            try {
                val photo = repository.import(owner, id, source)
                val photos = current().filterNot { it.id == id } + photo
                repository.retainDraft(owner, photos)
                update(photos)
                clearPending()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error()
            } finally { busy(false) }
        }
    }

    fun retry() { saved.get<String>("photo_source")?.let(::importPhoto) }
    fun resumePending() {
        val pending = saved.get<String>("photo_pending")?.let(UUID::fromString)
        if (current().any { it.id == pending }) clearPending() else retry()
    }

    fun remove(id: UUID) {
        if (!canEdit() || current().none { it.id == id }) return
        busy(true)
        scope.launch {
            try {
                val photos = current().filterNot { it.id == id }
                repository?.retainDraft(owner, photos)
                update(photos)
                clearPending()
                bestEffort { repository?.cleanup() }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error()
            } finally { busy(false) }
        }
    }

    fun discard(onDone: () -> Unit) {
        busy(true)
        scope.launch {
            try { releaseAfterWrite(); onDone() } finally { busy(false) }
        }
    }

    suspend fun releaseAfterWrite() {
        // The database write has already succeeded; a cleanup failure must not report a failed save.
        bestEffort { repository?.releaseDraft(owner) }
        clearPending()
    }
    private fun clearPending() { saved["photo_source"] = null; saved["photo_pending"] = null }
    private suspend fun bestEffort(action: suspend () -> Unit) {
        try { action() } catch (failure: Exception) { if (failure is CancellationException) throw failure }
    }
}

/** Saved state contains compact references and metadata, never image bytes or Bitmap objects. */
internal fun SavedStateHandle.savePhotos(photos: List<PhotoAttachment>) {
    fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    this["photos"] = ArrayList(photos.map { photo ->
        val meta = photo.metadata
        listOf(photo.id.toString(), encode(photo.relativePath), meta?.let { encode(it.mimeType) }.orEmpty(),
            meta?.byteCount?.toString().orEmpty(), meta?.width?.toString().orEmpty(), meta?.height?.toString().orEmpty()).joinToString("|")
    })
}
internal fun SavedStateHandle.readPhotos(): List<PhotoAttachment> {
    fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    return get<ArrayList<String>>("photos").orEmpty().map { value ->
        val fields = value.split('|')
        require(fields.size == 6)
        val meta = if (fields[2].isEmpty()) null else PhotoMetadata(decode(fields[2]), fields[3].toLong(), fields[4].toInt(), fields[5].toInt())
        PhotoAttachment(UUID.fromString(fields[0]), decode(fields[1]), meta)
    }
}
