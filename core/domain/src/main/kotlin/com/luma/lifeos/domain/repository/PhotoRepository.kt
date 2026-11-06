package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.PhotoDisplayRequest
import com.luma.lifeos.domain.model.PhotoEdit
import com.luma.lifeos.domain.model.PhotoReadScope
import java.util.UUID

interface PhotoRepository {
    /** Copies a selected source into private storage; retained IDs recover completed copies. */
    suspend fun import(draftId: UUID, id: UUID, source: String): PhotoAttachment
    /** Durable protection for unsaved and restored selections, including existing event photos. */
    suspend fun retainDraft(draftId: UUID, photos: List<PhotoAttachment>)
    suspend fun releaseDraft(draftId: UUID)
    /** Encoded bounded preview, or null for missing, corrupt or unmanaged files. */
    suspend fun preview(photo: PhotoAttachment): ByteArray?
    /** Encoded bounded PNG backed by the managed image, or null when it cannot be read safely. */
    suspend fun display(
        photo: PhotoAttachment,
        request: PhotoDisplayRequest,
        scope: PhotoReadScope = PhotoReadScope.Saved,
    ): ByteArray? = null
    /** Whether a canonical attachment identity is protected by this draft lease. */
    suspend fun isRetainedBy(leaseId: UUID, photo: PhotoAttachment): Boolean = false
    /** Safe ownership metadata for authorizing a saved attachment before decoding its bytes. */
    suspend fun ownerEventId(photoId: UUID): EventId? = null
    /** Writes a bounded, immutable derived PNG to a fresh output ID owned by the draft. */
    suspend fun transform(
        draftId: UUID,
        outputId: UUID,
        source: PhotoAttachment,
        edit: PhotoEdit,
    ): PhotoAttachment = throw UnsupportedOperationException("Photo transformation is not supported")
    /** Removes only currently unreferenced, unleased, managed files; never inside a DB transaction. */
    suspend fun cleanup()
}
