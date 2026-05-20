package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.watermark

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.LocationData
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.SensorData
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.FilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class WatermarkEngine(private val context: Context) {

    data class WatermarkConfig(
        val enabled: Boolean,
        val showLocation: Boolean,
        val showAltitude: Boolean,
        val showPressure: Boolean,
        val showDirection: Boolean,
        val showDeviceInfo: Boolean,
        val showCameraInfo: Boolean,
        val position: Int, // 0: Bottom, 1: Top, 2: Left, 3: Right
        val colorMode: Int // 0: Light, 1: Dark
    )

    suspend fun processAndSaveImage(
        imageUri: Uri,
        rotationDegrees: Int,
        config: WatermarkConfig,
        sensorData: SensorData,
        locationData: LocationData,
        filterMode: Int = 0
    ): Uri? = withContext(Dispatchers.IO) {
        
        // 1. Read original Bitmap
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext null
        inputStream?.close()

        // 2. Rotate if needed
        var bitmap = if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotated = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            if (rotated != originalBitmap) originalBitmap.recycle()
            rotated
        } else {
            originalBitmap
        }

        // 3. Apply filter
        if (filterMode > 0) {
            val filtered = FilterEngine.applyFilter(bitmap, filterMode)
            if (filtered != bitmap) {
                bitmap.recycle()
                bitmap = filtered
            }
        }

        // 4. Draw Watermark if enabled
        if (config.enabled) {
            val watermarked = drawWatermark(bitmap, config, sensorData, locationData)
            if (watermarked != bitmap) {
                bitmap.recycle()
                bitmap = watermarked
            }
        }

        // 5. Save to MediaStore
        val filename = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/UtopiaCamera")
            }
        }

        val resultUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (resultUri != null) {
            context.contentResolver.openOutputStream(resultUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
        
        // Delete the cache file (not a content URI, so use File.delete)
        try {
            val path = imageUri.path
            if (path != null) {
                java.io.File(path).delete()
            }
        } catch (_: Exception) { }

        bitmap.recycle()
        return@withContext resultUri
    }

    private fun drawWatermark(
        source: Bitmap,
        config: WatermarkConfig,
        sensorData: SensorData,
        locationData: LocationData
    ): Bitmap {
        val imgW = source.width
        val imgH = source.height
        val isHorizontalStrip = config.position == 0 || config.position == 1
        
        // Build text lines
        val lines = buildWatermarkLines(config, sensorData, locationData)
        if (lines.isEmpty()) return source

        // Calculate strip dimensions
        val baseFontSize = Math.max(imgW, imgH) * 0.018f
        val boldFontSize = baseFontSize * 1.3f
        val lineSpacing = baseFontSize * 0.6f
        val padding = baseFontSize * 1.5f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.colorMode == 1) Color.WHITE else Color.DKGRAY
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.colorMode == 1) Color.WHITE else Color.DKGRAY
            textSize = boldFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // First line is date (bold), rest are regular
        val totalTextHeight = boldFontSize + (lines.size * (baseFontSize + lineSpacing)) + lineSpacing
        val stripSize = (totalTextHeight + padding * 2).toInt()
        
        // Create result bitmap with strip
        val newWidth: Int
        val newHeight: Int
        val imgDx: Float
        val imgDy: Float

        when (config.position) {
            0 -> { // Bottom
                newWidth = imgW
                newHeight = imgH + stripSize
                imgDx = 0f
                imgDy = 0f
            }
            1 -> { // Top
                newWidth = imgW
                newHeight = imgH + stripSize
                imgDx = 0f
                imgDy = stripSize.toFloat()
            }
            2 -> { // Left
                newWidth = imgW + stripSize
                newHeight = imgH
                imgDx = stripSize.toFloat()
                imgDy = 0f
            }
            3 -> { // Right
                newWidth = imgW + stripSize
                newHeight = imgH
                imgDx = 0f
                imgDy = 0f
            }
            else -> {
                newWidth = imgW
                newHeight = imgH + stripSize
                imgDx = 0f
                imgDy = 0f
            }
        }

        val result = Bitmap.createBitmap(newWidth, newHeight, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // Fill background with strip color
        val bgColor = if (config.colorMode == 1) Color.rgb(50, 50, 50) else Color.WHITE
        canvas.drawColor(bgColor)

        // Draw original image
        canvas.drawBitmap(source, imgDx, imgDy, null)

        // Draw text in the strip area
        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
        
        when (config.position) {
            0 -> { // Bottom strip
                var y = imgH + padding + boldFontSize
                canvas.drawText(dateStr, padding, y, boldPaint)
                y += lineSpacing + baseFontSize
                for (line in lines) {
                    canvas.drawText(line, padding, y, textPaint)
                    y += baseFontSize + lineSpacing
                }
            }
            1 -> { // Top strip
                var y = padding + boldFontSize
                canvas.drawText(dateStr, padding, y, boldPaint)
                y += lineSpacing + baseFontSize
                for (line in lines) {
                    canvas.drawText(line, padding, y, textPaint)
                    y += baseFontSize + lineSpacing
                }
            }
            2 -> { // Left strip - draw text rotated 90° counter-clockwise
                canvas.save()
                canvas.rotate(-90f, stripSize / 2f, imgH / 2f)
                val textAreaWidth = imgH.toFloat()  // After rotation, height becomes width
                val startX = (stripSize / 2f - imgH / 2f) + padding
                var y = (imgH / 2f - stripSize / 2f) + padding + boldFontSize
                canvas.drawText(dateStr, startX, y, boldPaint)
                y += lineSpacing + baseFontSize
                for (line in lines) {
                    canvas.drawText(line, startX, y, textPaint)
                    y += baseFontSize + lineSpacing
                }
                canvas.restore()
            }
            3 -> { // Right strip - draw text rotated 90° clockwise
                canvas.save()
                val cx = imgW + stripSize / 2f
                val cy = imgH / 2f
                canvas.rotate(90f, cx, cy)
                val startX = cx - imgH / 2f + padding
                var y = cy - stripSize / 2f + padding + boldFontSize
                canvas.drawText(dateStr, startX, y, boldPaint)
                y += lineSpacing + baseFontSize
                for (line in lines) {
                    canvas.drawText(line, startX, y, textPaint)
                    y += baseFontSize + lineSpacing
                }
                canvas.restore()
            }
        }
        
        return result
    }

    private fun buildWatermarkLines(
        config: WatermarkConfig,
        sensorData: SensorData,
        locationData: LocationData
    ): List<String> {
        val lines = mutableListOf<String>()
        
        // Device info line
        if (config.showDeviceInfo) {
            lines.add("${Build.BRAND.uppercase()} ${Build.MODEL}")
        }

        // Camera info (EXIF) - basic info from what CameraX provides
        if (config.showCameraInfo) {
            // CameraX doesn't easily expose EXIF before saving, so we show device camera label
            lines.add("Shot on ${Build.BRAND} ${Build.MODEL}")
        }

        // Location line
        if (config.showLocation && locationData.location != null) {
            val lat = locationData.location.latitude.format(4)
            val lng = locationData.location.longitude.format(4)
            val coordStr = "${lat}°N, ${lng}°E"
            val addr = locationData.addressName
            lines.add(if (!addr.isNullOrBlank()) "$coordStr · $addr" else coordStr)
        }

        // Altitude + Pressure combined or separate
        val altParts = mutableListOf<String>()
        if (config.showAltitude && sensorData.altitude != null) {
            altParts.add("Alt: ${sensorData.altitude.format(1)}m")
        }
        if (config.showPressure && sensorData.pressure != null) {
            altParts.add("${sensorData.pressure.format(1)} hPa")
        }
        if (altParts.isNotEmpty()) {
            lines.add(altParts.joinToString(" · "))
        }

        // Direction
        if (config.showDirection && sensorData.azimuth != null) {
            lines.add(getDirectionString(sensorData.azimuth))
        }

        return lines
    }

    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun getDirectionString(azimuth: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = Math.round(((azimuth % 360) / 45)).toInt().coerceIn(0, 8)
        return "${azimuth.format(0)}° ${directions[index]}"
    }
}