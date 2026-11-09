package com.luma.lifeos.ui.privacy

import com.luma.lifeos.ui.common.LumaUi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.lock.LockPatternRules
import com.luma.lifeos.ui.common.AccessibleFeedback
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.sqrt

@Composable
internal fun LockPatternInput(
    enabled: Boolean,
    onCompleted: (List<Int>) -> Unit,
    onStarted: () -> Unit = {},
    onCleared: () -> Unit = {},
    modifier: Modifier = Modifier,
    resetKey: Any? = Unit,
) {
    var nodes by remember { mutableStateOf(emptyList<Int>()) }
    var tooShort by remember { mutableStateOf(false) }
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentOnStarted by rememberUpdatedState(onStarted)
    val currentOnCleared by rememberUpdatedState(onCleared)
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val radiusPx = with(density) { 24.dp.toPx() }
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outline

    LaunchedEffect(resetKey) {
        nodes = emptyList()
        tooShort = false
    }
    DisposableEffect(Unit) {
        onDispose {
            nodes = emptyList()
            tooShort = false
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)) {
            val dimension = maxWidth.coerceAtMost(288.dp).coerceAtLeast(176.dp)
            val edge = (dimension - 48.dp) / 2f
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    Modifier.size(dimension).pointerInput(enabled, resetKey) {
                        if (!enabled) return@pointerInput
                        val radius = radiusPx
                        val centers = List(9) { node ->
                            centerForNode(node, size.width.toFloat(), size.height.toFloat(), radius)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val pointerId = down.id
                            var previous = down.position
                            var completedGesture = false
                            var drawingStarted = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                                    val samples = change.historical.map { it.position } + change.position
                                    for (sample in samples) {
                                        if (!drawingStarted) {
                                            val movedX = sample.x - down.position.x
                                            val movedY = sample.y - down.position.y
                                            if (movedX * movedX + movedY * movedY > touchSlop * touchSlop) {
                                                drawingStarted = true
                                                currentOnStarted()
                                                nodes = appendAtPoint(emptyList(), down.position, centers, radius)
                                                tooShort = false
                                                nodes = appendCrossedNodes(nodes, down.position, sample, centers, radius)
                                            }
                                        } else {
                                            nodes = appendCrossedNodes(nodes, previous, sample, centers, radius)
                                        }
                                        previous = sample
                                    }
                                    if (drawingStarted) change.consume()
                                    if (!change.pressed) {
                                        completedGesture = true
                                        break
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                nodes = emptyList()
                                tooShort = false
                                throw cancelled
                            }
                            if (completedGesture && drawingStarted) {
                                val encoded = LockPatternRules.encode(nodes)
                                if (encoded == null) {
                                    tooShort = true
                                    nodes = emptyList()
                                } else {
                                    encoded.fill('\u0000')
                                    val completed = nodes.toList()
                                    nodes = emptyList()
                                    currentOnCompleted(completed)
                                }
                            } else if (drawingStarted) {
                                nodes = emptyList()
                            }
                        }
                    },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val centers = List(9) { node ->
                            centerForNode(node, size.width, size.height, radiusPx)
                        }
                        if (nodes.size > 1) {
                            val path = Path().apply {
                                moveTo(centers[nodes[0]].x, centers[nodes[0]].y)
                                for (index in 1 until nodes.size) {
                                    val center = centers[nodes[index]]
                                    lineTo(center.x, center.y)
                                }
                            }
                            drawPath(path, activeColor, style = Stroke(width = with(density) { 4.dp.toPx() }))
                        }
                        centers.forEachIndexed { index, center ->
                            drawCircle(
                                color = if (index in nodes) activeColor else inactiveColor,
                                radius = with(density) { if (index in nodes) 10.dp.toPx() else 8.dp.toPx() },
                                center = center,
                            )
                        }
                    }
                    for (node in 0..8) {
                        val label = stringResource(nodeLabelResource(node))
                        Box(
                            Modifier.offset(x = edge * (node % 3), y = edge * (node / 3))
                                .size(48.dp)
                                .semantics { contentDescription = label }
                                .clickable(enabled = enabled) {
                                    if (nodes.isEmpty()) currentOnStarted()
                                    nodes = LockPatternRules.appendNode(nodes, node)
                                    tooShort = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(if (node in nodes) 28.dp else 20.dp)
                                    .background(if (node in nodes) activeColor else inactiveColor, CircleShape),
                            )
                        }
                    }
                }
            }
        }
        if (tooShort) AccessibleFeedback(stringResource(R.string.app_lock_pattern_too_short))
        Row(horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap), verticalAlignment = Alignment.CenterVertically) {
            LumaTextButton(enabled = enabled, onClick = {
                nodes = emptyList()
                tooShort = false
                currentOnCleared()
            }) { Text(stringResource(R.string.app_lock_pattern_clear)) }
            LumaTextButton(enabled = enabled && nodes.size >= 4, onClick = {
                val encoded = LockPatternRules.encode(nodes)
                if (encoded == null) {
                    tooShort = true
                } else {
                    encoded.fill('\u0000')
                    val completed = nodes.toList()
                    nodes = emptyList()
                    tooShort = false
                    currentOnCompleted(completed)
                }
            }) { Text(stringResource(R.string.app_lock_pattern_done)) }
        }
    }
}

private fun appendAtPoint(
    current: List<Int>,
    point: Offset,
    centers: List<Offset>,
    radius: Float,
): List<Int> {
    var result = current
    centers.forEachIndexed { node, center ->
        val dx = point.x - center.x
        val dy = point.y - center.y
        if (dx * dx + dy * dy <= radius * radius) {
            result = LockPatternRules.appendNode(result, node)
        }
    }
    return result
}

private fun appendCrossedNodes(
    current: List<Int>,
    start: Offset,
    end: Offset,
    centers: List<Offset>,
    radius: Float,
): List<Int> {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return current
    val crossings = centers.mapIndexedNotNull { node, center ->
        if (node in current) return@mapIndexedNotNull null
        val projection = (((center.x - start.x) * dx + (center.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        val nearestX = start.x + projection * dx
        val nearestY = start.y + projection * dy
        val offsetX = center.x - nearestX
        val offsetY = center.y - nearestY
        val distanceSquared = offsetX * offsetX + offsetY * offsetY
        if (distanceSquared > radius * radius) return@mapIndexedNotNull null
        val entryDistance = sqrt((radius * radius - distanceSquared) / lengthSquared)
        max(0f, projection - entryDistance) to node
    }.sortedWith(compareBy<Pair<Float, Int>> { it.first }.thenBy { it.second })
    return crossings.fold(current) { acc, (_, node) -> LockPatternRules.appendNode(acc, node) }
}

private fun centerForNode(node: Int, width: Float, height: Float, radius: Float) = Offset(
    x = radius + (width - 2 * radius) * (node % 3) / 2f,
    y = radius + (height - 2 * radius) * (node / 3) / 2f,
)

private fun nodeLabelResource(node: Int): Int = when (node) {
    0 -> R.string.app_lock_pattern_top_left
    1 -> R.string.app_lock_pattern_top_middle
    2 -> R.string.app_lock_pattern_top_right
    3 -> R.string.app_lock_pattern_middle_left
    4 -> R.string.app_lock_pattern_middle_center
    5 -> R.string.app_lock_pattern_middle_right
    6 -> R.string.app_lock_pattern_bottom_left
    7 -> R.string.app_lock_pattern_bottom_middle
    else -> R.string.app_lock_pattern_bottom_right
}
