package com.luma.lifeos.data.mapping

import com.luma.lifeos.data.entity.MediaAttachmentEntity
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoMetadata
import java.util.UUID
import org.json.JSONObject

fun MediaAttachmentEntity.toDomainPhoto() = PhotoAttachment(UUID.fromString(id), relativePath, runCatching {
    val json = JSONObject(metadataJson)
    PhotoMetadata(json.getString("mime_type"), json.getLong("byte_count"), json.getInt("width"), json.getInt("height"))
}.getOrNull())

internal fun PhotoAttachment.metadataJson(): String = metadata?.let {
    JSONObject().put("mime_type", it.mimeType).put("byte_count", it.byteCount)
        .put("width", it.width).put("height", it.height).toString()
} ?: "{}"
