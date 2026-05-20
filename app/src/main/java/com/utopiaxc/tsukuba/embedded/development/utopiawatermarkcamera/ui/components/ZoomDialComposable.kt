package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * iPhone-style zoom control with preset buttons and a horizontal scroll dial.
 * 
 * Preset buttons: .5x, 1x, 2x (or available presets based on min/max zoom)
 * Dial: horizontal scrollable ruler for fine-grained zoom (0.1x step)
 */
@Composable
fun ZoomDialControl(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit
) {
    val presets = buildPresetList(minZoom, maxZoom)
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current zoom ratio text
        Text(
            text = "${String.format("%.1f", currentZoom)}×",
            color = Color(0xFFFFCC00),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Preset lens buttons row
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            presets.forEach { preset ->
                val isActive = isPresetActive(currentZoom, preset, presets)
                ZoomPresetButton(
                    label = formatPresetLabel(preset),
                    isActive = isActive,
                    onClick = { onZoomChange(preset) }
                )
            }
        }

        // Horizontal dial ruler
        ZoomDial(
            currentZoom = currentZoom,
            minZoom = minZoom,
            maxZoom = maxZoom,
            onZoomChange = onZoomChange
        )
    }
}

@Composable
private fun ZoomPresetButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) Color(0xFFFFCC00) else Color.White.copy(alpha = 0.15f)
    val textColor = if (isActive) Color.Black else Color.White

    val animatedSize by animateFloatAsState(
        targetValue = if (isActive) 38f else 32f,
        animationSpec = tween(150),
        label = "presetSize"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(animatedSize.dp)
            .clip(CircleShape)
            .background(bgColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            event.changes.forEach { it.consume() }
                            onClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (isActive) 12.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ZoomDial(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    val dialWidthDp = 240.dp
    val dialHeightDp = 32.dp

    Box(
        modifier = Modifier
            .width(dialWidthDp)
            .height(dialHeightDp)
            .pointerInput(minZoom, maxZoom) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val range = maxZoom - minZoom
                    val pxPerUnit = size.width.toFloat() / range
                    val delta = -dragAmount / pxPerUnit
                    val newZoom = (currentZoom + delta).coerceIn(minZoom, maxZoom)
                    // Round to 0.1 step
                    val rounded = (newZoom * 10).roundToInt() / 10f
                    onZoomChange(rounded.coerceIn(minZoom, maxZoom))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val range = maxZoom - minZoom
            val cx = w / 2

            // Draw tick marks based on current zoom position
            val pxPerUnit = w / (range.coerceAtLeast(0.1f))
            val visibleRange = w / pxPerUnit / 2

            // Draw scale ticks
            val startVal = (currentZoom - visibleRange).coerceAtLeast(minZoom)
            val endVal = (currentZoom + visibleRange).coerceAtMost(maxZoom)

            var v = (startVal * 10).toInt() / 10f
            while (v <= endVal) {
                val xPos = cx + (v - currentZoom) * pxPerUnit
                val isMajor = (v * 10).roundToInt() % 10 == 0
                val tickHeight = if (isMajor) h * 0.5f else h * 0.25f
                val tickAlpha = (1f - kotlin.math.abs(xPos - cx) / (w / 2)).coerceIn(0.1f, 0.8f)

                drawLine(
                    Color.White.copy(alpha = tickAlpha),
                    Offset(xPos, h / 2 - tickHeight / 2),
                    Offset(xPos, h / 2 + tickHeight / 2),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                v += 0.1f
            }

            // Center indicator (triangle pointer)
            val pointerSize = 4.dp.toPx()
            drawLine(
                Color(0xFFFFCC00),
                Offset(cx, 0f),
                Offset(cx, pointerSize * 2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                Color(0xFFFFCC00),
                Offset(cx, h),
                Offset(cx, h - pointerSize * 2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun buildPresetList(minZoom: Float, maxZoom: Float): List<Float> {
    val candidates = listOf(0.5f, 1f, 2f, 5f, 10f)
    return candidates.filter { it in minZoom..maxZoom }.let { filtered ->
        if (filtered.isEmpty()) listOf(minZoom) else filtered
    }
}

private fun isPresetActive(current: Float, preset: Float, presets: List<Float>): Boolean {
    val idx = presets.indexOf(preset)
    val next = if (idx < presets.size - 1) presets[idx + 1] else Float.MAX_VALUE
    val prev = if (idx > 0) presets[idx - 1] else Float.MIN_VALUE
    val lowerBound = (preset + prev) / 2
    val upperBound = (preset + next) / 2
    return current >= lowerBound && current < upperBound
}

private fun formatPresetLabel(zoom: Float): String {
    return when {
        zoom < 1f -> ".${(zoom * 10).roundToInt()}"
        zoom == zoom.toInt().toFloat() -> "${zoom.toInt()}×"
        else -> "${String.format("%.1f", zoom)}×"
    }
}
