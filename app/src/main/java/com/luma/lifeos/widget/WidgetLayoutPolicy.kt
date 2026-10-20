package com.luma.lifeos.widget

import kotlin.math.ceil

/** Pure sizing policy shared by the data-widget renderers. */
internal object WidgetLayoutPolicy {
    const val MAX_ROWS = 3
    private const val MIN_WIDGET_WIDTH_DP = 180

    enum class CaptureLayout { WIDE, GRID, TALL, FALLBACK }

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

    /**
     * Returns the number of complete rows that fit after all measured header, feedback and footer
     * content has been reserved. The caller measures localized content at the active font scale.
     */
    fun rowCapacity(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float,
        reservedHeightDp: Int,
        rowHeightDp: Int,
    ): Int {
        if (widthDp < MIN_WIDGET_WIDTH_DP || heightDp <= 0 || rowHeightDp <= 0) return 0
        val safeScale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
        // Text is measured using scaled pixels by the renderer; leave a little extra for host
        // rounding and line-height differences at larger system font settings.
        val scaleAllowance = ceil((safeScale - 1f).coerceAtMost(2f) * 8f).toInt()
        return ((heightDp - reservedHeightDp - scaleAllowance).coerceAtLeast(0) / rowHeightDp)
            .coerceIn(0, MAX_ROWS)
    }
}
