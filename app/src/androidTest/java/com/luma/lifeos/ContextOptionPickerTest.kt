package com.luma.lifeos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.ui.common.ContextOption
import com.luma.lifeos.ui.common.ContextOptionPicker
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ContextOptionPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeCatalogSearchAndDoneUseStableIds() {
        val options = (0 until 2_000).map { index ->
            val id = UUID.nameUUIDFromBytes("person-$index".toByteArray())
            ContextOption(id, "Person $index", if (index == 1_999) "Near the library" else null)
        }
        val expected = options.last().id
        var applied = emptySet<UUID>()
        compose.setContent {
            LifeOsTheme {
                ContextOptionPicker("People", options, emptySet(), maxSelections = 100,
                    onApply = { applied = it }, onCreate = {}, onDismiss = {})
            }
        }

        compose.onNodeWithTag("context_search").performTextInput("Person 1999")
        compose.onNodeWithTag("context_option_$expected").performClick()
        compose.onNodeWithTag("context_done").performClick()
        compose.runOnIdle { assertEquals(setOf(expected), applied) }
    }

    @Test fun cancelDiscardsChangesAndMissingSelectionCanBeRemoved() {
        val unavailable = UUID.randomUUID()
        val optionId = UUID.randomUUID()
        var applied: Set<UUID>? = null
        compose.setContent {
            LifeOsTheme {
                ContextOptionPicker("People", listOf(ContextOption(optionId, "Sam", null)),
                    setOf(unavailable), maxSelections = 1,
                    onApply = { applied = it }, onCreate = {}, onDismiss = {})
            }
        }

        compose.onNodeWithText("Unavailable selection").assertIsDisplayed()
        compose.onNodeWithTag("context_option_$optionId").assertIsNotEnabled()
        compose.onNodeWithTag("context_remove_$unavailable").assertIsDisplayed().performClick()
        compose.onNodeWithTag("context_option_$optionId").assertIsEnabled().performClick()
        compose.onNodeWithTag("context_cancel").performClick()
        compose.runOnIdle { assertEquals(null, applied) }
    }
}
