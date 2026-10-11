package com.luma.lifeos.ui.photos

import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoMetadata
import com.luma.lifeos.domain.repository.PhotoRepository
import java.util.UUID

class TestPhotoRepository : PhotoRepository {
    val files = mutableMapOf<UUID, PhotoAttachment>()
    val leases = mutableMapOf<UUID, List<PhotoAttachment>>()
    var failImport = false
    var failAfterCopy = false
    var failRelease = false
    override suspend fun import(draftId: UUID, id: UUID, source: String): PhotoAttachment {
        if (failImport) error("Source unavailable")
        val photo = files.getOrPut(id) { PhotoAttachment(id, "photos/$id.img", PhotoMetadata("image/png", 4, 2, 2)) }
        if (failAfterCopy) error("Completed copy result unavailable")
        return photo
    }
    override suspend fun retainDraft(draftId: UUID, photos: List<PhotoAttachment>) { leases[draftId] = photos }
    override suspend fun releaseDraft(draftId: UUID) { if (failRelease) error("Cleanup unavailable"); leases.remove(draftId) }
    override suspend fun preview(photo: PhotoAttachment): ByteArray? = null
    override suspend fun cleanup() = Unit
}
