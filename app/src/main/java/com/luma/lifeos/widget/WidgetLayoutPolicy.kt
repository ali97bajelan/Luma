package com.luma.lifeos.widget

import com.luma.lifeos.domain.widget.WidgetStore
import kotlin.math.ceil
import kotlin.math.max

/** Pure sizing policy shared by the data-widget renderers. */
internal object WidgetLayoutPolicy {
    const val MAX_MEASUREMENT_ROWS = WidgetStore.COLLECTION_PAGE_SIZE
    const val ROW_GAP_DP = 8
    private const val MIN_WIDGET_WIDTH_DP = 180

    enum class CaptureLayout { WIDE, GRID, TALL, FALLBACK }

    fun captureLayoutOrder(fontScale: Float): List<CaptureLayout> =
        if (fontScale >= 1.5f) {
            listOf(CaptureLayout.TALL, CaptureLayout.GRID, CaptureLayout.WIDE)
        } else {
            listOf(CaptureLayout.WIDE, CaptureLayout.GRID, CaptureLayout.TALL)
        }

    fun captureLabelsFitWithoutSplitting(
        wordWidthsPx: List<Float>,
        contentWidthsPx: List<Float>,
    ): Boolean = wordWidthsPx.size == contentWidthsPx.size &&
        wordWidthsPx.zip(contentWidthsPx).all { (wordWidth, contentWidth) ->
            wordWidth.isFinite() && contentWidth.isFinite() && wordWidth <= contentWidth
        }

    /** Label sizes include text padding and the minimum 48dp target. */
    fun captureLayout(widthDp: Int, heightDp: Int, labels: List<Pair<Int, Int>>): CaptureLayout {
        require(labels.size == 3)
        val width = widthDp - 24
        val height = heightDp - 24
        val cellWidth = labels.maxOf { it.first.coerceAtLeast(48) }
        val cellHeight = labels.maxOf { it.second.coerceAtLeast(48) }
        return when {
            width >= cellWidth * 3 + 16 && height >= cellHeight -> CaptureLayout.WIDE
            width >= labels.take(2).maxOf { it.first.coerceAtLeast(48) } * 2 + 8 &&
                width >= labels[2].first.coerceAtLeast(48) &&
                height >= cellHeight * 2 + 8 -> CaptureLayout.GRID
            width >= cellWidth && height >= cellHeight * 3 + 16 -> CaptureLayout.TALL
            else -> CaptureLayout.FALLBACK
        }
    }

    fun needsCompactShell(heightDp: Int, reservedHeightDp: Int): Boolean = heightDp < reservedHeightDp

    /** Keeps the title, labeled Capture action, and Refresh target separate at every font scale. */
    fun needsStackedHeader(widthDp: Int, titleWidthDp: Int, captureWidthDp: Int): Boolean {
        val availableWidth = widthDp - 24
        val requiredWidth = max(48, titleWidthDp) + max(48, captureWidthDp) + 48 + 16
        return availableWidth < requiredWidth
    }

    /**
     * The legacy min/max option fields combine bounds from different orientations. On a phone,
     * portrait width is paired with landscape height in the min/min pair. Select the two bounds
     * that describe the current orientation when a launcher does not provide exact sizes.
     */
    fun currentBounds(
        isLandscape: Boolean,
        minWidthDp: Int,
        minHeightDp: Int,
        maxWidthDp: Int,
        maxHeightDp: Int,
        fallbackWidthDp: Int,
        fallbackHeightDp: Int,
    ): Pair<Int, Int> {
        fun firstPositive(vararg values: Int): Int = values.firstOrNull { it > 0 } ?: 0
        return if (isLandscape) {
            firstPositive(maxWidthDp, minWidthDp, fallbackWidthDp) to
                firstPositive(minHeightDp, maxHeightDp, fallbackHeightDp)
        } else {
            firstPositive(minWidthDp, maxWidthDp, fallbackWidthDp) to
                firstPositive(maxHeightDp, minHeightDp, fallbackHeightDp)
        }
    }

    fun distinctSizes(sizes: List<Pair<Float, Float>>): List<Pair<Float, Float>> =
        sizes.filter { (width, height) -> width > 0f && height > 0f }.distinct()

    /** Maps a saved habit count to a clamped, 0..1000 presentation value. */
    fun habitProgressPermille(count: Int, target: Int): Int {
        if (target <= 0) return 0
        val clampedCount = count.coerceIn(0, target)
        return (clampedCount.toLong() * 1000L / target.toLong()).toInt()
    }

    /** Counts the complete ordered prefix whose individually measured heights fit the budget. */
    fun rowCapacity(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float,
        reservedHeightDp: Int,
        rowHeightsDp: List<Int>,
    ): Int {
        if (widthDp < MIN_WIDGET_WIDTH_DP || heightDp <= 0) return 0
        val safeScale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
        val scaleAllowance = ceil((safeScale - 1f).coerceAtMost(2f) * 8f).toInt()
        val availableHeight = (heightDp - reservedHeightDp - scaleAllowance).coerceAtLeast(0)
        return rowViewportCapacity(availableHeight, rowHeightsDp, ROW_GAP_DP)
    }

    /** Row heights are already measured for their actual localized content and action width. */
    fun rowPrefixCapacity(availableHeightDp: Int, rowHeightsDp: List<Int>): Int {
        return rowViewportCapacity(availableHeightDp, rowHeightsDp, 0)
    }

    /** Counts whole rows in the measured viewport, charging a gap only between adjacent rows. */
    private fun rowViewportCapacity(
        availableHeightDp: Int,
        rowHeightsDp: List<Int>,
        rowGapDp: Int,
    ): Int {
        if (availableHeightDp <= 0) return 0
        var remaining = availableHeightDp
        var count = 0
        for (height in rowHeightsDp) {
            val requiredHeight = height + if (count > 0) rowGapDp else 0
            if (height <= 0 || requiredHeight > remaining) break
            remaining -= requiredHeight
            count++
        }
        return count
    }
}
