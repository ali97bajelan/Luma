package com.luma.lifeos

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.platform.testTag
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.journal.JournalDraft
import com.luma.lifeos.ui.journal.JournalEditorError
import com.luma.lifeos.ui.journal.JournalEditorScreen
import com.luma.lifeos.ui.journal.JournalEditorState
import com.luma.lifeos.ui.journal.JournalTextField
import com.luma.lifeos.ui.mood.MoodDraft
import com.luma.lifeos.ui.mood.MoodEditorScreen
import com.luma.lifeos.ui.mood.MoodEditorState
import com.luma.lifeos.ui.mood.MoodTimeControls
import com.luma.lifeos.ui.QuickCaptureSheet
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.ui.photos.PhotoEditor
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickCaptureOmitsHabitChoiceWhenCallbackIsAbsent() {
        compose.setContent {
            LifeOsTheme {
                QuickCaptureSheet(
                    onDismiss = {}, onMood = {}, onJournal = {}, onMemory = {},
                )
            }
        }

        compose.onNodeWithTag("capture_mood").assertExists()
        compose.onNodeWithTag("capture_journal").assertExists()
        compose.onNodeWithTag("capture_memory").assertExists()
        compose.onNodeWithTag("capture_habit").assertDoesNotExist()
    }

    @Test fun quickCaptureHabitCallbackUsesNowOrSelectedDateAndKeepsOtherActions() {
        val browsedDate = LocalDate.parse("2026-10-12")
        val habitDates = mutableListOf<LocalDate?>()
        val moodDates = mutableListOf<LocalDate?>()
        val journalDates = mutableListOf<LocalDate?>()
        val memoryDates = mutableListOf<LocalDate?>()
        var taskCalls = 0
        compose.setContent {
            LifeOsTheme {
                QuickCaptureSheet(
                    browsedDate = browsedDate,
                    onDismiss = {},
                    onMood = { moodDates += it },
                    onJournal = { journalDates += it },
                    onMemory = { memoryDates += it },
                    onTask = { taskCalls += 1 },
                    onHabit = { habitDates += it },
                )
            }
        }

        compose.onNodeWithTag("capture_mood").performScrollTo().performClick()
        compose.onNodeWithTag("capture_journal").performScrollTo().performClick()
        compose.onNodeWithTag("capture_memory").performScrollTo().performClick()
        compose.onNodeWithTag("capture_task").performScrollTo().performClick()
        compose.onNodeWithTag("capture_habit").performScrollTo().performClick()
        compose.onNodeWithTag("capture_on_browsed_date").performScrollTo().performClick()
        compose.onNodeWithTag("capture_habit").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(listOf(null, browsedDate), habitDates)
            assertEquals(listOf(null), moodDates)
            assertEquals(listOf(null), journalDates)
            assertEquals(listOf(null), memoryDates)
            assertEquals(1, taskCalls)
        }
    }

    @Test fun editorSectionsAreHeadings() {
        compose.setContent {
            LifeOsTheme {
                JournalEditorScreen(
                    state = JournalEditorState(JournalDraft(Instant.parse("2026-10-14T08:00:00Z").toEpochMilli(), body = "Entry"), ready = true),
                    onCancel = {}, onSave = {}, onDelete = {}, onRetry = {}, onTitle = {}, onBody = {},
                    onAddTag = { true }, onRemoveTag = {}, onTime = { _, _ -> true },
                )
            }
        }

        compose.onNodeWithTag("editor_title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag("journal_tags_toggle").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test fun disclosureNamesContentsAndKeepsTimestampVisible() {
        compose.setContent {
            LifeOsTheme {
                MoodEditorScreen(
                    state = MoodEditorState(MoodDraft(Instant.parse("2026-10-14T08:00:00Z").toEpochMilli(), score = 3), ready = true),
                    editing = false, onCancel = {}, onSave = {}, onDelete = {}, onRetry = {}, onScore = {},
                    onEmotion = {}, onEnergy = {}, onStress = {}, onNote = {}, onTime = { _, _ -> true },
                )
            }
        }

        compose.onNodeWithTag("capture_timestamp_value").assertIsDisplayed()
        compose.onNodeWithTag("mood_question").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag("mood_question").assertIsDisplayed()
        compose.onNodeWithTag("mood_more").assertTextContains("Note and context").performClick()
        compose.onNodeWithTag("mood_more").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("mood_note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capture_timestamp_value").assertExists()
    }

    @Test fun fieldErrorsBelongToFieldNodes() {
        compose.setContent {
            LifeOsTheme {
                JournalEditorScreen(
                    state = JournalEditorState(
                        JournalDraft(Instant.parse("2026-10-14T08:00:00Z").toEpochMilli(), body = "Kept body"),
                        ready = true, textErrors = setOf(JournalTextField.BODY),
                    ),
                    onCancel = {}, onSave = {}, onDelete = {}, onRetry = {}, onTitle = {}, onBody = {},
                    onAddTag = { false }, onRemoveTag = {}, onTime = { _, _ -> true },
                )
            }
        }

        compose.onNodeWithTag("journal_body").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test fun saveFailureStaysVisibleWithScrolledBody() {
        compose.setContent {
            LifeOsTheme {
                JournalEditorScreen(
                    state = JournalEditorState(
                        JournalDraft(Instant.parse("2026-10-14T08:00:00Z").toEpochMilli(), body = "Long entry\n".repeat(200)),
                        ready = true, error = JournalEditorError.SAVE,
                    ),
                    onCancel = {}, onSave = {}, onDelete = {}, onRetry = {}, onTitle = {}, onBody = {},
                    onAddTag = { true }, onRemoveTag = {}, onTime = { _, _ -> true },
                )
            }
        }

        compose.onNodeWithTag("journal_body").performScrollTo()
        compose.onNodeWithTag("journal_error").assertIsDisplayed()
        compose.onNodeWithTag("journal_save").assertIsDisplayed()
    }

    @Test fun photoFailureRevealsContextAndRetry() {
        val retries = AtomicInteger()
        val previewRepository = object : PhotoRepository {
            override suspend fun import(draftId: java.util.UUID, id: java.util.UUID, source: String): PhotoAttachment = error("unused")
            override suspend fun retainDraft(draftId: java.util.UUID, photos: List<PhotoAttachment>) = Unit
            override suspend fun releaseDraft(draftId: java.util.UUID) = Unit
            override suspend fun preview(photo: PhotoAttachment): ByteArray? = null
            override suspend fun cleanup() = Unit
        }
        compose.setContent {
            LifeOsTheme {
                JournalEditorScreen(
                    state = JournalEditorState(
                        JournalDraft(Instant.parse("2026-10-14T08:00:00Z").toEpochMilli(), body = "Entry"),
                        ready = true, error = JournalEditorError.PHOTO,
                    ),
                    onCancel = {}, onSave = {}, onDelete = {}, onRetry = {}, onTitle = {}, onBody = {},
                    onAddTag = { true }, onRemoveTag = {}, onTime = { _, _ -> true },
                    photosContent = { PhotoEditor(previewRepository, emptyList(), true, {}, {},
                        { retries.incrementAndGet() }, importFailed = true) },
                )
            }
        }

        compose.onNodeWithTag("photo_import_error").assertIsDisplayed()
        compose.onNodeWithTag("photo_retry_import").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries.get()) }
    }

    @Test fun failedPhotoRemovalShowsLocalFeedbackAndKeepsTheAttachmentVisible() {
        val attachment = PhotoAttachment(java.util.UUID.randomUUID(), "photos/kept.img")
        val repository = object : PhotoRepository {
            override suspend fun import(draftId: java.util.UUID, id: java.util.UUID, source: String): PhotoAttachment = error("unused")
            override suspend fun retainDraft(draftId: java.util.UUID, photos: List<PhotoAttachment>) = Unit
            override suspend fun releaseDraft(draftId: java.util.UUID) = Unit
            override suspend fun preview(photo: PhotoAttachment): ByteArray? = null
            override suspend fun cleanup() = Unit
        }
        compose.setContent {
            LifeOsTheme {
                JournalEditorScreen(
                    state = JournalEditorState(
                        JournalDraft(Instant.parse("2026-10-14T08:00:00Z").toEpochMilli(), body = "Entry", photos = listOf(attachment)),
                        ready = true, error = JournalEditorError.PHOTO,
                    ),
                    onCancel = {}, onSave = {}, onDelete = {}, onRetry = {}, onTitle = {}, onBody = {},
                    onAddTag = { true }, onRemoveTag = {}, onTime = { _, _ -> true },
                    photosContent = { PhotoEditor(repository, listOf(attachment), true, {}, {}, {},
                        importFailed = false, operationFailed = true) },
                )
            }
        }

        compose.onNodeWithTag("photo_operation_error").assertIsDisplayed()
        compose.onNodeWithTag("photo_remove_${attachment.id}").assertExists()
        compose.onNodeWithTag("photo_retry_import").assertDoesNotExist()
    }

    @Test fun timestampDialogHasOneAnnouncementOwner() {
        var timeError by mutableStateOf(false)
        compose.setContent {
            LifeOsTheme {
                MoodTimeControls(
                    Instant.parse("2026-10-14T08:00:00Z").toEpochMilli(), enabled = true,
                    timeError = timeError, onTime = { _, _ -> timeError = true; false },
                )
            }
        }

        compose.onNodeWithTag("capture_timestamp_change").performClick()
        compose.onNodeWithTag("capture_timestamp_done").performClick()
        compose.onNodeWithTag("capture_timestamp_dialog").assertIsDisplayed()
        compose.onAllNodesWithTag("mood_time_error", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("mood_time_error", useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("mood_time_error", useUnmergedTree = true).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithTag("capture_timestamp_cancel").performClick()
        compose.onAllNodesWithTag("mood_time_error", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("mood_time_error", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun timestampChangeStaysLocalUntilDoneAndSameMinuteRetainsMilliseconds() {
        val originalMillis = Instant.parse("2026-10-14T08:00:00.123Z").toEpochMilli()
        val calls = mutableListOf<Pair<java.time.LocalDateTime, java.time.ZoneId>>()
        compose.setContent {
            LifeOsTheme {
                MoodTimeControls(originalMillis, enabled = true, timeError = false,
                    onTime = { dateTime, zone -> calls += dateTime to zone; true })
            }
        }

        compose.onNodeWithTag("capture_timestamp_change").performClick()
        compose.onNodeWithTag("capture_timestamp_cancel").performClick()
        compose.runOnIdle { assertEquals(0, calls.size) }

        compose.onNodeWithTag("capture_timestamp_change").performClick()
        compose.onNodeWithTag("capture_timestamp_done").performClick()
        compose.runOnIdle {
            assertEquals(1, calls.size)
            assertEquals(ZoneId.systemDefault(), calls.single().second)
            assertEquals(Instant.ofEpochMilli(originalMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(), calls.single().first)
        }
    }
}
