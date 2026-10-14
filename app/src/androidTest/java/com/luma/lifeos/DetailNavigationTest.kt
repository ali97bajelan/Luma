package com.luma.lifeos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.lifeos.ui.common.DetailTopBar
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backAndEditRemainVisibleAfterLongScrollInNarrowRtlLargeTextLayout() {
        var backClicks = 0
        var editClicks = 0
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(baseDensity.density, fontScale = 2f),
            ) {
                LifeOsTheme {
                    Box(Modifier.width(320.dp).height(500.dp)) {
                        Scaffold(topBar = {
                            DetailTopBar(
                                title = "A very long localized record title that should not push actions away",
                                backTag = "journal_detail_back",
                                onBack = { backClicks++ },
                                editTag = "journal_edit",
                                editLabel = "Edit journal",
                                onEdit = { editClicks++ },
                            )
                        }) { padding ->
                            val rows = remember { (0 until 40).map { "Long detail row $it" } }
                            LazyColumn(Modifier.padding(padding).testTag("detail_test_content")) {
                                items(rows) { row -> Text(row, Modifier.testTag(if (row == "Long detail row 0") "detail_first_row" else "detail_row")) }
                            }
                        }
                    }
                }
            }
        }

        val barBounds = compose.onNodeWithTag("detail_top_bar").fetchSemanticsNode().boundsInRoot
        val firstRowBounds = compose.onNodeWithTag("detail_first_row").fetchSemanticsNode().boundsInRoot
        assertTrue("Body content starts below the fixed app bar", firstRowBounds.top >= barBounds.bottom)
        compose.onNodeWithTag("detail_test_content").performScrollToIndex(39)
        compose.onNodeWithTag("journal_detail_back").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("journal_edit").assertIsDisplayed()
            .assertContentDescriptionEquals("Edit journal")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, editClicks)
        }
    }
}
