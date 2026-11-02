package com.luma.lifeos.ui.navigation

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrimaryNavigationBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun calendarLabelStaysOnOneLineAtNarrowWidthWithLargeText() {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, 1.3f)) {
                LifeOsTheme {
                    PrimaryNavigationBar(
                        selectedRoute = AppRoutes.CALENDAR,
                        onNavigate = {},
                        modifier = Modifier.width(300.dp),
                    )
                }
            }
        }

        val label = compose.onNodeWithTag("nav_label_calendar", useUnmergedTree = true)
        val layoutResults = mutableListOf<TextLayoutResult>()
        val layoutAction = label.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        assertTrue(layoutAction.action?.invoke(layoutResults) == true && layoutResults.isNotEmpty())
        assertEquals("Calendar should remain on one line", 1, layoutResults.single().lineCount)
    }
}
