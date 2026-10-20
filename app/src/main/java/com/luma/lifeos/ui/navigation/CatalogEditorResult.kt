package com.luma.lifeos.ui.navigation

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

enum class CatalogEntity { PERSON, PLACE }

sealed interface CatalogEditorResult {
    val id: UUID

    data class Created(override val id: UUID) : CatalogEditorResult
    data class Saved(override val id: UUID) : CatalogEditorResult
    data class Deleted(override val id: UUID) : CatalogEditorResult
}

data class CatalogEditorDelivery(
    val entity: CatalogEntity,
    val requestId: UUID,
    val result: CatalogEditorResult,
)

data class CatalogAttachmentFeedback(
    val messageResource: Int,
    val isError: Boolean,
    val argument: Int? = null,
)

private const val PENDING_ENTITY = "catalog_pending_entity"
private const val PENDING_REQUEST_ID = "catalog_pending_request_id"
private const val RESULT_ENTITY = "catalog_result_entity"
private const val RESULT_REQUEST_ID = "catalog_result_request_id"
private const val RESULT_TYPE = "catalog_result_type"
private const val RESULT_ID = "catalog_result_id"
private const val EDITOR_RESULT_TYPE = "catalog_editor_result_type"
private const val EDITOR_RESULT_ID = "catalog_editor_result_id"

/** Start a request on the calling navigation entry before opening a catalog editor. */
fun SavedStateHandle.beginCatalogRequest(entity: CatalogEntity): UUID {
    clearCatalogResult()
    val requestId = UUID.randomUUID()
    this[PENDING_ENTITY] = entity.name
    this[PENDING_REQUEST_ID] = requestId.toString()
    return requestId
}

fun SavedStateHandle.pendingCatalogRequest(entity: CatalogEntity): UUID? {
    if (get<String>(PENDING_ENTITY) != entity.name) return null
    return get<String>(PENDING_REQUEST_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

/** Publish primitive fields only when the editor belongs to this caller/request. */
fun SavedStateHandle.recordCatalogEditorResult(
    entity: CatalogEntity,
    requestId: UUID?,
    result: CatalogEditorResult,
): Boolean {
    if (requestId == null || pendingCatalogRequest(entity) != requestId) return false
    this[RESULT_ENTITY] = entity.name
    this[RESULT_REQUEST_ID] = requestId.toString()
    this[RESULT_TYPE] = result.resultType()
    this[RESULT_ID] = result.id.toString()
    return true
}

fun SavedStateHandle.catalogEditorResultIdFlow(): StateFlow<String?> = getStateFlow(RESULT_ID, null)

fun SavedStateHandle.readCatalogEditorDelivery(expected: CatalogEntity): CatalogEditorDelivery? {
    val id = get<String>(RESULT_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
    val entity = get<String>(RESULT_ENTITY)?.let { runCatching { CatalogEntity.valueOf(it) }.getOrNull() } ?: return null
    if (entity != expected) return null
    val requestId = get<String>(RESULT_REQUEST_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
    if (pendingCatalogRequest(expected) != requestId) return null
    val result = when (get<String>(RESULT_TYPE)) {
        "CREATED" -> CatalogEditorResult.Created(id)
        "SAVED" -> CatalogEditorResult.Saved(id)
        "DELETED" -> CatalogEditorResult.Deleted(id)
        else -> return null
    }
    return CatalogEditorDelivery(entity, requestId, result)
}

fun SavedStateHandle.consumeCatalogEditorDelivery(delivery: CatalogEditorDelivery) {
    if (readCatalogEditorDelivery(delivery.entity) != delivery) return
    clearCatalogResult()
    remove<String>(PENDING_ENTITY)
    remove<String>(PENDING_REQUEST_ID)
}

fun SavedStateHandle.cancelCatalogRequest(entity: CatalogEntity, requestId: UUID?) {
    if (requestId == null || pendingCatalogRequest(entity) != requestId) return
    clearCatalogResult()
    remove<String>(PENDING_ENTITY)
    remove<String>(PENDING_REQUEST_ID)
}

fun SavedStateHandle.persistCatalogEditorResult(result: CatalogEditorResult) {
    this[EDITOR_RESULT_TYPE] = result.resultType()
    this[EDITOR_RESULT_ID] = result.id.toString()
}

fun SavedStateHandle.readPersistedCatalogEditorResult(): CatalogEditorResult? {
    val id = get<String>(EDITOR_RESULT_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
    return when (get<String>(EDITOR_RESULT_TYPE)) {
        "CREATED" -> CatalogEditorResult.Created(id)
        "SAVED" -> CatalogEditorResult.Saved(id)
        "DELETED" -> CatalogEditorResult.Deleted(id)
        else -> null
    }
}

private fun CatalogEditorResult.resultType(): String = when (this) {
    is CatalogEditorResult.Created -> "CREATED"
    is CatalogEditorResult.Saved -> "SAVED"
    is CatalogEditorResult.Deleted -> "DELETED"
}

private fun SavedStateHandle.clearCatalogResult() {
    remove<String>(RESULT_ENTITY)
    remove<String>(RESULT_REQUEST_ID)
    remove<String>(RESULT_TYPE)
    remove<String>(RESULT_ID)
}
