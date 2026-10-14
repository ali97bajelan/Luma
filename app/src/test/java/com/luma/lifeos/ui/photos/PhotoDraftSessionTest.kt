package com.luma.lifeos.ui.photos

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoMetadata
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoDraftSessionTest {
    @Test fun recoveringAnImportRetainsPendingFileBeforeAnyCleanupCanRun() = runTest {
        val owner = UUID.randomUUID()
        val pending = UUID.randomUUID()
        val selected = PhotoAttachment(UUID.randomUUID(), "photos/selected.img")
        val saved = SavedStateHandle(mapOf("photo_pending" to pending.toString(), "photo_source" to "content://old-grant"))
        val files = TestPhotoRepository()
        val session = PhotoDraftSession(files, saved, this, owner, { listOf(selected) }, { true }, {}, {}, {})
        session.retain(listOf(selected))
        assertEquals(setOf(selected.id, pending), files.leases.getValue(owner).map { it.id }.toSet())
    }

    @Test fun savedPhotoReferencesRoundTripLegacyAndKnownMetadataWithoutBytes() {
        val saved = SavedStateHandle()
        val photos = listOf(PhotoAttachment(UUID.randomUUID(), "photos/legacy path.jpg"),
            PhotoAttachment(UUID.randomUUID(), "photos/新しい.img", PhotoMetadata("image/png", 123, 3, 4)))
        saved.savePhotos(photos)
        val restored = SavedStateHandle(mapOf("photos" to saved.get<ArrayList<String>>("photos")))
        assertEquals(photos, restored.readPhotos())
    }

    @Test fun failedRemovalKeepsTheAttachmentAndReportsTheFailure() = runTest {
        val owner = UUID.randomUUID()
        val attachment = PhotoAttachment(UUID.randomUUID(), "photos/kept.img")
        val repository = TestPhotoRepository().apply { failRetainDraft = true }
        repository.leases[owner] = listOf(attachment)
        var current = listOf(attachment)
        var failed = false
        val session = PhotoDraftSession(repository, SavedStateHandle(), this, owner,
            { current }, { true }, {}, { current = it }, { failed = true })

        session.remove(attachment.id)
        advanceUntilIdle()

        assertEquals(listOf(attachment), current)
        assertEquals(listOf(attachment), repository.leases[owner])
        assertTrue(failed)
    }
}
