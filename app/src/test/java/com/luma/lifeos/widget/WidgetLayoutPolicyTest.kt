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
    @Test fun captureUsesWideNarrowAndTallBeforeFallback() {
        val labels = listOf(60 to 48, 72 to 48, 80 to 48)
        assertEquals(WidgetLayoutPolicy.CaptureLayout.WIDE, WidgetLayoutPolicy.captureLayout(300, 100, labels))
        assertEquals(WidgetLayoutPolicy.CaptureLayout.GRID, WidgetLayoutPolicy.captureLayout(200, 160, labels))
        assertEquals(WidgetLayoutPolicy.CaptureLayout.TALL, WidgetLayoutPolicy.captureLayout(120, 200, labels))
        assertEquals(WidgetLayoutPolicy.CaptureLayout.FALLBACK, WidgetLayoutPolicy.captureLayout(120, 160, labels))
    }

    @Test fun largeLabelsRemainDirectWhenBoundsFit() {
        val labels = listOf(120 to 64, 150 to 64, 160 to 64)
        assertEquals(WidgetLayoutPolicy.CaptureLayout.WIDE, WidgetLayoutPolicy.captureLayout(530, 109, labels))
        assertEquals(WidgetLayoutPolicy.CaptureLayout.GRID, WidgetLayoutPolicy.captureLayout(350, 180, labels))
        assertEquals(WidgetLayoutPolicy.CaptureLayout.TALL, WidgetLayoutPolicy.captureLayout(190, 240, labels))
    }

    @Test fun compactShellReservesRecoveryInsteadOfOnlyRemovingRows() {
        assertEquals(true, WidgetLayoutPolicy.needsCompactShell(109, 150))
        assertEquals(false, WidgetLayoutPolicy.needsCompactShell(250, 150))
    }
    @Test fun unequalGridLabelsReserveBothEqualHeightRows() {
        assertEquals(WidgetLayoutPolicy.CaptureLayout.FALLBACK,
            WidgetLayoutPolicy.captureLayout(200, 160, listOf(80 to 72, 80 to 72, 80 to 48)))
    }
}
