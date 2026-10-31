package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.EventId
import java.util.UUID

interface PhotoRepository {
    /** Copies a selected source into private storage; retained IDs recover completed copies. */
    suspend fun import(draftId: UUID, id: UUID, source: String): PhotoAttachment
    /** Durable protection for unsaved and restored selections, including existing event photos. */
    suspend fun retainDraft(draftId: UUID, photos: List<PhotoAttachment>)
    suspend fun releaseDraft(draftId: UUID)
    /** Encoded bounded preview, or null for missing, corrupt or unmanaged files. */
    suspend fun preview(photo: PhotoAttachment): ByteArray?
    /** Safe ownership metadata for authorizing a saved attachment before decoding its bytes. */
    suspend fun ownerEventId(photoId: UUID): EventId? = null
    /** Removes only currently unreferenced, unleased, managed files; never inside a DB transaction. */
    suspend fun cleanup()
}
