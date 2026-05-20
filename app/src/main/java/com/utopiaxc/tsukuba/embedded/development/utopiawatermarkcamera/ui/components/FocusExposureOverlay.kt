package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * iPhone-style focus square with exposure compensation slider.
 * Shows a yellow square at the focus point with a sun icon slider on the right.
 */
@Composable
fun FocusExposureOverlay(
    focusPoint: Offset?,
    exposureCompensation: Float,
    exposureRange: ClosedFloatingPointRange<Float>,
    onExposureChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (focusPoint == null) return

    val density = LocalDensity.current

    // Animation for the focus square
    val scale = remember { Animatable(1.6f) }
    val alpha = remember { Animatable(1f) }

    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(focusPoint) {
        scale.snapTo(1.6f)
        alpha.snapTo(1f)
        scale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
    }

    // Auto-hide after 3 seconds of no interaction
    LaunchedEffect(focusPoint, isInteracting) {
        if (!isInteracting) {
            delay(3000)
            alpha.animateTo(0f, animationSpec = tween(300))
            onDismiss()
        }
    }

    val xDp = with(density) { focusPoint.x.toDp() }
    val yDp = with(density) { focusPoint.y.toDp() }
    val squareSize = 70.dp
    val halfSquare = squareSize / 2

    Box(modifier = Modifier.fillMaxSize()) {
        // Focus square
        Canvas(
            modifier = Modifier
                .offset(x = xDp - halfSquare, y = yDp - halfSquare)
                .size(squareSize * scale.value)
        ) {
            val stroke = Stroke(width = 2.dp.toPx())
            val color = Color(0xFFFFCC00).copy(alpha = alpha.value)
            val s = size.minDimension
            val padding = (size.width - s) / 2

            // Draw focus square with corner lines (iPhone style)
            val cornerLen = s * 0.25f

            // Top-left
            drawLine(color, Offset(padding, padding), Offset(padding + cornerLen, padding), strokeWidth = stroke.width)
            drawLine(color, Offset(padding, padding), Offset(padding, padding + cornerLen), strokeWidth = stroke.width)
            // Top-right
            drawLine(color, Offset(padding + s, padding), Offset(padding + s - cornerLen, padding), strokeWidth = stroke.width)
            drawLine(color, Offset(padding + s, padding), Offset(padding + s, padding + cornerLen), strokeWidth = stroke.width)
            // Bottom-left
            drawLine(color, Offset(padding, padding + s), Offset(padding + cornerLen, padding + s), strokeWidth = stroke.width)
            drawLine(color, Offset(padding, padding + s), Offset(padding, padding + s - cornerLen), strokeWidth = stroke.width)
            // Bottom-right
            drawLine(color, Offset(padding + s, padding + s), Offset(padding + s - cornerLen, padding + s), strokeWidth = stroke.width)
            drawLine(color, Offset(padding + s, padding + s), Offset(padding + s, padding + s - cornerLen), strokeWidth = stroke.width)

            // Center crosshair (small)
            val crossSize = s * 0.08f
            val cx = size.width / 2
            val cy = size.height / 2
            drawLine(color, Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), strokeWidth = 1.dp.toPx())
            drawLine(color, Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), strokeWidth = 1.dp.toPx())
        }

        // Exposure compensation slider (right side of focus square)
        if (alpha.value > 0.3f) {
            val sliderHeight = 160.dp
            val sliderX = xDp + halfSquare + 16.dp
            val sliderY = yDp - sliderHeight / 2

            Column(
                modifier = Modifier
                    .offset(x = sliderX, y = sliderY)
                    .width(36.dp)
                    .height(sliderHeight),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sun icon at top
                Text(
                    text = "☀",
                    fontSize = 16.sp,
                    color = Color(0xFFFFCC00).copy(alpha = alpha.value)
                )

                // Vertical slider track
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isInteracting = true },
                                onDragEnd = { isInteracting = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val range = exposureRange.endInclusive - exposureRange.start
                                    val delta = -(dragAmount.y / size.height.toFloat()) * range
                                    val newVal = (exposureCompensation + delta).coerceIn(exposureRange)
                                    onExposureChange(newVal)
                                }
                            )
                        }
                ) {
                    // Track background
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            Color(0xFFFFCC00).copy(alpha = alpha.value * 0.4f),
                            Offset(size.width / 2, 0f),
                            Offset(size.width / 2, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                        // Current position indicator
                        val range = exposureRange.endInclusive - exposureRange.start
                        val normalized = if (range > 0) {
                            1f - (exposureCompensation - exposureRange.start) / range
                        } else 0.5f
                        val y = size.height * normalized
                        drawCircle(
                            Color(0xFFFFCC00).copy(alpha = alpha.value),
                            radius = 6.dp.toPx(),
                            center = Offset(size.width / 2, y)
                        )
                    }
                }

                // Moon icon at bottom
                Text(
                    text = "☾",
                    fontSize = 14.sp,
                    color = Color(0xFFFFCC00).copy(alpha = alpha.value * 0.6f)
                )
            }
        }
    }
}
