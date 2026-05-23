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
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.DeviceOrientation
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.SensorData
import kotlin.math.abs
import kotlin.math.atan2

@Composable
fun HorizonLevelerOverlay(
    sensorData: SensorData,
    uiRotation: Float = 0f
) {
    val gx = sensorData.gravityX ?: 0f
    val gy = sensorData.gravityY ?: 0f
    val gz = sensorData.gravityZ ?: 0f
    
    when (sensorData.deviceOrientation) {
        DeviceOrientation.PORTRAIT -> {
            // Up is +Y, Right is +X
            val angle = Math.toDegrees(atan2(-gx.toDouble(), gy.toDouble())).toFloat()
            UprightModeLeveler(angle = angle, uiRotation = uiRotation)
        }
        DeviceOrientation.LANDSCAPE -> {
            // When in landscape, gravity shifts to X axis. 
            // We want the level line to be drawn across the screen relative to the user.
            // By passing uiRotation to UprightModeLeveler, it rotates the canvas,
            // so we calculate the angle relative to the physical axes, and it will be drawn correctly.
            // If phone is CCW 90 (left edge down), gy is 0, gx > 0.
            val angle = Math.toDegrees(atan2(-gx.toDouble(), gy.toDouble())).toFloat()
            UprightModeLeveler(angle = angle, uiRotation = uiRotation)
        }
        DeviceOrientation.FLAT -> {
            // Flat on table. +Z is Up.
            // Tilt in X maps to bubble X. Tilt in Y maps to bubble Y.
            // Android UI: +X is right, +Y is down.
            // If phone top is tilted up, gy > 0. Bubble should go UP (-Y in UI).
            // If phone right is tilted up, gx > 0. Bubble should go RIGHT (+X in UI).
            val tiltX = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
            val tiltY = Math.toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
            FlatModeLeveler(pitch = tiltY, roll = tiltX)
        }
    }
}

@Composable
private fun UprightModeLeveler(
    angle: Float,
    uiRotation: Float = 0f
) {
    val isLevel = abs(angle) < 1.5f
    val targetColor = if (isLevel) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f)
    
    val animatedAngle by animateFloatAsState(
        targetValue = angle,
        animationSpec = tween(100),
        label = "angle"
    )

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        
        rotate(degrees = uiRotation, pivot = Offset(cx, cy)) {
            val lineLen = if (uiRotation != 0f) size.height * 0.15f else size.width * 0.15f

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

            // Dynamic level line (rotates with device angle)
            // The angle we pass in is the absolute angle of gravity. We need to subtract uiRotation 
            // from it because the canvas is already rotated by uiRotation!
            val relativeAngle = animatedAngle + uiRotation
            rotate(degrees = -relativeAngle, pivot = Offset(cx, cy)) {
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
            val relativeAngleDisplay = (relativeAngle % 360f).let { if (it > 180f) it - 360f else if (it < -180f) it + 360f else it }
            val angleText = "${String.format("%.1f", relativeAngleDisplay)}°"
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
}

@Composable
private fun FlatModeLeveler(
    pitch: Float, // correlates to Y tilt
    roll: Float   // correlates to X tilt
) {
    val isLevel = abs(roll) < 1.5f && abs(pitch) < 1.5f
    val targetColor = if (isLevel) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f)
    val bubbleColor = if (isLevel) Color(0xFF4CAF50) else Color(0xFFFFCC00)

    val animatedPitch by animateFloatAsState(
        targetValue = pitch,
        animationSpec = tween(100),
        label = "pitchFlat"
    )
    val animatedRoll by animateFloatAsState(
        targetValue = roll,
        animationSpec = tween(100),
        label = "rollFlat"
    )

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.width * 0.08f

        // Outer reference circle
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = radius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        
        // Inner reference circle (smaller)
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = radius * 0.4f,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )

        // Crosshair
        val crossLen = radius * 1.3f
        drawLine(
            Color.White.copy(alpha = 0.3f),
            Offset(cx - crossLen, cy),
            Offset(cx + crossLen, cy),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            Color.White.copy(alpha = 0.3f),
            Offset(cx, cy - crossLen),
            Offset(cx, cy + crossLen),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Center dot
        drawCircle(
            targetColor,
            radius = 2.5.dp.toPx(),
            center = Offset(cx, cy)
        )

        // Bubble position
        val maxAngle = 15f
        val bubbleX = cx + (animatedRoll / maxAngle).coerceIn(-1f, 1f) * radius
        // -Y is up in Canvas. We want bubble UP if pitch > 0
        val bubbleY = cy - (animatedPitch / maxAngle).coerceIn(-1f, 1f) * radius

        // Bubble
        drawCircle(
            color = bubbleColor.copy(alpha = 0.8f),
            radius = 8.dp.toPx(),
            center = Offset(bubbleX, bubbleY)
        )
        drawCircle(
            color = bubbleColor,
            radius = 8.dp.toPx(),
            center = Offset(bubbleX, bubbleY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )

        // Angle text below
        val angleText = "P:${String.format("%.1f", animatedPitch)}° R:${String.format("%.1f", animatedRoll)}°"
        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(angleText),
            style = TextStyle(
                color = targetColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        )
        drawText(
            textLayoutResult,
            topLeft = Offset(
                cx - textLayoutResult.size.width / 2f,
                cy + crossLen + 12.dp.toPx()
            )
        )
    }
}
