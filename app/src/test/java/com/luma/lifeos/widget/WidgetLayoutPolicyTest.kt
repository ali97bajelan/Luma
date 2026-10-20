package com.luma.lifeos.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutPolicyTest {
    @Test fun portraitBoundsUsePortraitWidthAndHeightRatherThanMinMin() {
        assertEquals(
            276 to 210,
            WidgetLayoutPolicy.currentBounds(
                isLandscape = false,
                minWidthDp = 276,
                minHeightDp = 108,
                maxWidthDp = 577,
                maxHeightDp = 210,
                fallbackWidthDp = 250,
                fallbackHeightDp = 250,
            ),
        )
    }

    @Test fun landscapeBoundsUseLandscapeWidthAndHeightRatherThanMinMin() {
        assertEquals(
            577 to 108,
            WidgetLayoutPolicy.currentBounds(
                isLandscape = true,
                minWidthDp = 276,
                minHeightDp = 108,
                maxWidthDp = 577,
                maxHeightDp = 210,
                fallbackWidthDp = 250,
                fallbackHeightDp = 250,
            ),
        )
    }

    @Test fun responsiveBoundsRemoveDuplicateOrientationSizes() {
        assertEquals(
            listOf(276f to 210f, 577f to 108f),
            WidgetLayoutPolicy.distinctSizes(
                listOf(276f to 210f, 276f to 210f, 577f to 108f, 577f to 108f),
            ),
        )
    }
}
