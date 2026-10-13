package com.luma.lifeos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.LayoutDirection
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.timeline.DayNavigation
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DayNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previousAndNextArrowsPointInOppositeDirectionsInLtrAndRtl() {
        val layoutDirection = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            LifeOsTheme {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection.value) {
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        DayNavigation(LocalDate.parse("2026-10-11"), {}, {})
                    }
                }
            }
        }

        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { selectedDirection ->
            compose.runOnIdle { layoutDirection.value = selectedDirection }
            compose.waitForIdle()

            val previousCenter = centerLineX("day_previous")
            val nextCenter = centerLineX("day_next")
            val viewCenter = compose.onNodeWithTag("day_previous").captureToImage().width / 2f
            if (selectedDirection == LayoutDirection.Ltr) {
                assertTrue("Previous should point left in LTR", previousCenter < viewCenter)
                assertTrue("Next should point right in LTR", nextCenter > viewCenter)
            } else {
                assertTrue("Previous should point right in RTL", previousCenter > viewCenter)
                assertTrue("Next should point left in RTL", nextCenter < viewCenter)
            }
        }
    }

    private fun centerLineX(tag: String): Float {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val y = pixels.height / 2
        val background = pixels[0, 0]
        val foreground = (0 until pixels.width).filter { x -> pixels[x, y] != background }
        return foreground.average().toFloat()
    }
}
