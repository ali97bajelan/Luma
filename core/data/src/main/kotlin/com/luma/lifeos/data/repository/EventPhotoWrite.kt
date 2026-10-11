package com.luma.lifeos.data.repository

import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.MediaAttachmentEntity
import com.luma.lifeos.data.mapping.metadataJson
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.PhotoAttachment

/** Targeted reference replacement only. Files and durable draft leases already exist before saving. */
internal suspend fun replacePhotosInTransaction(database: LumaDatabase, id: EventId, photos: List<PhotoAttachment>?) {
    if (photos == null) return
    val content = database.content()
    val previous = content.attachments(id.toString()).associateBy { it.id }
    val rows = photos.map { photo ->
        val old = previous[photo.id.toString()]
        require(old == null || old.relativePath == photo.relativePath) { "Photo identity cannot change its file" }
        MediaAttachmentEntity(photo.id.toString(), id.toString(), photo.relativePath,
            metadataJson = old?.metadataJson ?: photo.metadataJson())
    }
    content.deleteAttachments(id.toString())
    content.insertAttachments(rows)
}
