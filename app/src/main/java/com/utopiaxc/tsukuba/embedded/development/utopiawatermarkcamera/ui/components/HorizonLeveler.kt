package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Professional horizon leveler overlay with crosshair design.
 * Shows a center reference and a dynamic line that rotates with device roll.
 * Turns green when level (within ±1°).
 */
@Composable
fun HorizonLevelerOverlay(
    pitch: Float,
    roll: Float
) {
    val isLevel = abs(roll) < 1.5f && abs(pitch) < 2f
    val targetColor = if (isLevel) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f)
    
    val animatedRoll by animateFloatAsState(
        targetValue = roll,
        animationSpec = tween(100),
        label = "roll"
    )

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val lineLen = size.width * 0.15f

        // Static center reference mark (horizontal)
        val refColor = Color.White.copy(alpha = 0.5f)
        val refLen = 20.dp.toPx()
        val refGap = 6.dp.toPx()

        // Left reference dash
        drawLine(
            refColor,
            Offset(cx - refLen - refGap, cy),
            Offset(cx - refGap, cy),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Right reference dash
        drawLine(
            refColor,
            Offset(cx + refGap, cy),
            Offset(cx + refLen + refGap, cy),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Center dot
        drawCircle(
            refColor,
            radius = 3.dp.toPx(),
            center = Offset(cx, cy)
        )

        // Dynamic level line (rotates with device roll)
        rotate(degrees = -animatedRoll, pivot = Offset(cx, cy)) {
            drawLine(
                targetColor,
                Offset(cx - lineLen, cy),
                Offset(cx + lineLen, cy),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Small vertical ticks at ends
            val tickLen = 6.dp.toPx()
            drawLine(
                targetColor,
                Offset(cx - lineLen, cy - tickLen),
                Offset(cx - lineLen, cy + tickLen),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                targetColor,
                Offset(cx + lineLen, cy - tickLen),
                Offset(cx + lineLen, cy + tickLen),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Angle text below the leveler
        val angleText = "${String.format("%.1f", animatedRoll)}°"
        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(angleText),
            style = TextStyle(
                color = targetColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
        drawText(
            textLayoutResult,
            topLeft = Offset(
                cx - textLayoutResult.size.width / 2f,
                cy + lineLen + 12.dp.toPx()
            )
        )
    }
}
