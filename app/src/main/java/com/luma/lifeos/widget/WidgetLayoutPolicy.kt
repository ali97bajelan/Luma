package com.luma.lifeos.widget

import kotlin.math.ceil

/** Pure sizing policy shared by the data-widget renderers. */
internal object WidgetLayoutPolicy {
    const val MAX_ROWS = 3
    private const val MIN_WIDGET_WIDTH_DP = 180

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
