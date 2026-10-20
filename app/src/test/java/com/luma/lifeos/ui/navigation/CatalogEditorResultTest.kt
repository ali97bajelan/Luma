package com.luma.lifeos.ui.navigation

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEditorResultTest {
    @Test fun createdResultIsScopedToCallerAndConsumedOnce() {
        val caller = SavedStateHandle()
        val requestId = caller.beginCatalogRequest(CatalogEntity.PERSON)
        val created = CatalogEditorResult.Created(UUID.randomUUID())

        assertTrue(caller.recordCatalogEditorResult(CatalogEntity.PERSON, requestId, created))
        val delivery = caller.readCatalogEditorDelivery(CatalogEntity.PERSON)
        assertEquals(CatalogEditorDelivery(CatalogEntity.PERSON, requestId, created), delivery)
        assertNull(caller.readCatalogEditorDelivery(CatalogEntity.PLACE))

        caller.consumeCatalogEditorDelivery(requireNotNull(delivery))
        assertNull(caller.readCatalogEditorDelivery(CatalogEntity.PERSON))
        assertNull(caller.pendingCatalogRequest(CatalogEntity.PERSON))
    }

    @Test fun catalogCreationWithoutCaptureRequestIsNotPublished() {
        val catalogEntry = SavedStateHandle()
        assertFalse(catalogEntry.recordCatalogEditorResult(
            CatalogEntity.PLACE,
            requestId = null,
            result = CatalogEditorResult.Created(UUID.randomUUID()),
        ))
        assertNull(catalogEntry.readCatalogEditorDelivery(CatalogEntity.PLACE))
    }

    @Test fun resultFromStaleRequestCannotSatisfyNewRequest() {
        val caller = SavedStateHandle()
        val staleRequest = caller.beginCatalogRequest(CatalogEntity.PERSON)
        val currentRequest = caller.beginCatalogRequest(CatalogEntity.PERSON)

        assertFalse(caller.recordCatalogEditorResult(
            CatalogEntity.PERSON,
            staleRequest,
            CatalogEditorResult.Created(UUID.randomUUID()),
        ))
        assertTrue(caller.recordCatalogEditorResult(
            CatalogEntity.PERSON,
            currentRequest,
            CatalogEditorResult.Created(UUID.randomUUID()),
        ))
        assertEquals(currentRequest, caller.readCatalogEditorDelivery(CatalogEntity.PERSON)?.requestId)
    }

    @Test fun staleCancellationCannotClearNewRequest() {
        val caller = SavedStateHandle()
        val staleRequest = caller.beginCatalogRequest(CatalogEntity.PLACE)
        val currentRequest = caller.beginCatalogRequest(CatalogEntity.PLACE)

        caller.cancelCatalogRequest(CatalogEntity.PLACE, staleRequest)
        assertEquals(currentRequest, caller.pendingCatalogRequest(CatalogEntity.PLACE))
        caller.cancelCatalogRequest(CatalogEntity.PLACE, currentRequest)
        assertNull(caller.pendingCatalogRequest(CatalogEntity.PLACE))
    }
}
