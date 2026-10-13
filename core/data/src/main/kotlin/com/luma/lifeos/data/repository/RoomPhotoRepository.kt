package com.luma.lifeos.data.repository

import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.AndroidPhotoCodec
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.repository.PhotoRepository
import java.io.InputStream
import java.util.UUID
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
        mutex.withLock { files.retain(draftId, photos) }
    }
    override suspend fun releaseDraft(draftId: UUID) = withContext(Dispatchers.IO) {
        mutex.withLock { files.release(draftId); clean() }
    }
    override suspend fun preview(photo: PhotoAttachment): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            files.resolve(photo.relativePath)?.let { file -> runCatching { AndroidPhotoCodec.preview(file) }.getOrNull() }
        }
    }
    override suspend fun cleanup() = withContext(Dispatchers.IO) { mutex.withLock { clean() } }
    private suspend fun clean() {
        // Query the current committed references immediately before deletion, outside a transaction.
        files.clean(database.content().referencedMediaPaths().toSet())
    }
}
