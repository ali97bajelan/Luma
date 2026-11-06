package com.luma.lifeos.data.repository

import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.AndroidPhotoCodec
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.PhotoDisplayRequest
import com.luma.lifeos.domain.model.PhotoReadScope
import com.luma.lifeos.domain.repository.PhotoRepository
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomPhotoRepository(private val database: LumaDatabase, private val files: ManagedPhotoFiles,
    private val mutex: Mutex = Mutex(), private val openSource: (String) -> InputStream) : PhotoRepository {
    override suspend fun import(draftId: UUID, id: UUID, source: String) = withContext(Dispatchers.IO) {
        mutex.withLock { files.import(draftId, id, { openSource(source) }, AndroidPhotoCodec::inspect) }
    }
    override suspend fun retainDraft(draftId: UUID, photos: List<PhotoAttachment>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            photos.forEach { photo ->
                if (files.resolve(photo) == null && photo.metadata != null &&
                    database.content().ownerEventId(photo.id.toString()) == null
                ) throw IllegalStateException("Unowned draft photo file is missing")
            }
            files.retain(draftId, photos)
        }
    }
    override suspend fun releaseDraft(draftId: UUID) = withContext(Dispatchers.IO) {
        mutex.withLock { files.release(draftId); clean() }
    }
    override suspend fun preview(photo: PhotoAttachment): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            files.resolve(photo.relativePath)?.let { file -> runCatching { AndroidPhotoCodec.preview(file) }.getOrNull() }
        }
    }
    override suspend fun display(
        photo: PhotoAttachment,
        request: PhotoDisplayRequest,
        scope: PhotoReadScope,
    ): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (scope is PhotoReadScope.Draft && !files.isRetainedBy(scope.leaseId, photo)) return@withLock null
            try {
                val file = files.resolve(photo) ?: return@withLock null
                AndroidPhotoCodec.display(file, request)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                null
            }
        }
    }
    override suspend fun isRetainedBy(leaseId: UUID, photo: PhotoAttachment): Boolean =
        withContext(Dispatchers.IO) { mutex.withLock { files.isRetainedBy(leaseId, photo) } }
    override suspend fun ownerEventId(photoId: UUID): EventId? =
        database.content().ownerEventId(photoId.toString())?.let(EventId::parse)
    override suspend fun cleanup() = withContext(Dispatchers.IO) { mutex.withLock { clean() } }
    private suspend fun clean() {
        // Query the current committed references immediately before deletion, outside a transaction.
        files.clean(database.content().referencedMediaPaths().toSet())
    }
}
