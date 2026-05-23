package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.watermark

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.R
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
        val colorMode: Int, // 0: Light, 1: Dark
        val authorName: String = ""
    )

    /**
     * Holds EXIF camera info extracted from the captured JPEG.
     */
    data class ExifCameraInfo(
        val aperture: String? = null,      // e.g. "f/1.8"
        val focalLength: String? = null,   // e.g. "26mm"
        val shutterSpeed: String? = null,  // e.g. "1/120s"
        val iso: String? = null            // e.g. "ISO 100"
    )

    suspend fun processAndSaveImage(
        imageUri: Uri,
        config: WatermarkConfig,
        sensorData: SensorData,
        locationData: LocationData,
        filterMode: Int = 0
    ): Uri? = withContext(Dispatchers.IO) {
        
        // 1. Read original Bitmap
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext null
        inputStream?.close()

        // 2. Read EXIF orientation and rotate accordingly
        val exifRotation = readExifRotation(imageUri)
        val exifCameraInfo = readExifCameraInfo(imageUri)
        
        var bitmap = if (exifRotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(exifRotation.toFloat())
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
            val watermarked = drawWatermark(bitmap, config, sensorData, locationData, exifCameraInfo)
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
        
        // Delete the cache file
        try {
            val path = imageUri.path
            if (path != null) {
                java.io.File(path).delete()
            }
        } catch (_: Exception) { }

        bitmap.recycle()
        return@withContext resultUri
    }

    /**
     * Read EXIF orientation from the image file and return rotation degrees.
     */
    private fun readExifRotation(uri: Uri): Int {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return 0
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 // Could handle flip but rare
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Read EXIF camera info from the captured JPEG.
     */
    private fun readExifCameraInfo(uri: Uri): ExifCameraInfo {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ExifCameraInfo()
            val exif = ExifInterface(inputStream)
            inputStream.close()

            // Aperture: TAG_F_NUMBER gives f-number directly
            val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
            val apertureStr = if (fNumber != null) "f/$fNumber" else null

            // Focal length: TAG_FOCAL_LENGTH gives as rational "26/1"
            val focalRational = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            val focalStr = if (focalRational != null) {
                try {
                    val parts = focalRational.split("/")
                    val mm = if (parts.size == 2) {
                        (parts[0].toDouble() / parts[1].toDouble()).let {
                            if (it == it.toLong().toDouble()) "${it.toLong()}mm" else "%.1fmm".format(it)
                        }
                    } else "${focalRational}mm"
                    mm
                } catch (e: Exception) { "${focalRational}mm" }
            } else null

            // Shutter speed: TAG_EXPOSURE_TIME gives seconds as float string
            val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            val shutterStr = if (exposureTime != null) {
                try {
                    val time = exposureTime.toDouble()
                    if (time >= 1.0) {
                        "${time.toLong()}s"
                    } else {
                        val denominator = (1.0 / time).toLong()
                        "1/${denominator}s"
                    }
                } catch (e: Exception) { "${exposureTime}s" }
            } else null

            // ISO
            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                ?: exif.getAttribute("PhotographicSensitivity")
            val isoStr = if (iso != null) "ISO $iso" else null

            ExifCameraInfo(
                aperture = apertureStr,
                focalLength = focalStr,
                shutterSpeed = shutterStr,
                iso = isoStr
            )
        } catch (e: Exception) {
            ExifCameraInfo()
        }
    }

    /**
     * Draw the new 4-row watermark layout.
     *
     * Row 1: [Date Time]                              [Device Name]
     * Row 2: [Coordinates · Place]                    [f/1.8 26mm 1/120s]
     * Row 3: [Pressure Alt Direction]
     * Row 4: [Shot by ©Author]        [BRAND]         [Powered by Utopia Watermark Camera]
     */
    private fun drawWatermark(
        source: Bitmap,
        config: WatermarkConfig,
        sensorData: SensorData,
        locationData: LocationData,
        exifInfo: ExifCameraInfo
    ): Bitmap {
        val imgW = source.width
        val imgH = source.height

        // Font sizing based on image dimensions
        val baseFontSize = Math.max(imgW, imgH) * 0.016f
        val titleFontSize = baseFontSize * 1.3f
        val smallFontSize = baseFontSize * 0.85f
        val lineSpacing = baseFontSize * 1.0f
        val padding = baseFontSize * 2f
        val rowGap = baseFontSize * 0.4f

        val isDark = config.colorMode == 1
        val textColor = if (isDark) Color.WHITE else Color.DKGRAY
        val subtextColor = if (isDark) Color.rgb(200, 200, 200) else Color.rgb(100, 100, 100)
        val bgColor = if (isDark) Color.rgb(40, 40, 40) else Color.WHITE

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = titleFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titlePaintRight = Paint(titlePaint).apply {
            textAlign = Paint.Align.RIGHT
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val bodyPaintRight = Paint(bodyPaint).apply {
            textAlign = Paint.Align.RIGHT
        }

        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subtextColor
            textSize = smallFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val smallPaintRight = Paint(smallPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        val smallPaintCenter = Paint(smallPaint).apply {
            textAlign = Paint.Align.CENTER
        }

        // Build row contents
        // Row 1: Date+Time (left), Device Name (right)
        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date())
        val deviceStr = if (config.showDeviceInfo) "${Build.BRAND.uppercase()} ${Build.MODEL}" else null

        // Row 2: Location (left), Camera EXIF info (right)
        val locationStr = if (config.showLocation && locationData.location != null) {
            val lat = locationData.location.latitude.format(4)
            val lng = locationData.location.longitude.format(4)
            val coordStr = "${lat}°N, ${lng}°E"
            val addr = locationData.addressName
            if (!addr.isNullOrBlank()) "$coordStr · $addr" else coordStr
        } else null

        val cameraInfoStr = if (config.showCameraInfo) {
            val parts = listOfNotNull(exifInfo.aperture, exifInfo.focalLength, exifInfo.shutterSpeed, exifInfo.iso)
            if (parts.isNotEmpty()) parts.joinToString("  ") else null
        } else null

        // Row 3: Pressure + Altitude + Direction (left)
        val row3Parts = mutableListOf<String>()
        if (config.showPressure && sensorData.pressure != null) {
            row3Parts.add("${sensorData.pressure.format(1)} hPa")
        }
        if (config.showAltitude) {
            val pressureAlt = sensorData.altitude
            val gpsAlt = locationData.location?.altitude
            if (pressureAlt != null && gpsAlt != null) {
                row3Parts.add(context.getString(R.string.watermark_alt_dual, pressureAlt.format(1), gpsAlt.format(1)))
            } else if (pressureAlt != null) {
                row3Parts.add(context.getString(R.string.watermark_alt, pressureAlt.format(1)))
            } else if (gpsAlt != null) {
                row3Parts.add(context.getString(R.string.watermark_alt, gpsAlt.format(1)))
            }
        }
        if (config.showDirection && sensorData.azimuth != null) {
            row3Parts.add(getDirectionString(sensorData.azimuth))
        }
        val row3Str = if (row3Parts.isNotEmpty()) row3Parts.joinToString("  ·  ") else null

        // Row 4: Shot by (left), Brand (center), Powered by (right)
        val authorStr = if (config.authorName.isNotBlank()) {
            context.getString(R.string.watermark_shot_by, config.authorName)
        } else null
        val poweredByStr = context.getString(R.string.watermark_powered_by)

        // Calculate strip height
        var rowCount = 1 // Row 1 always present (date)
        if (locationStr != null || cameraInfoStr != null) rowCount++
        if (row3Str != null) rowCount++
        rowCount++ // Row 4 always present (powered by)

        val stripHeight = (
            padding + titleFontSize +                              // Top padding + Row 1 baseline
            (rowCount - 1) * (lineSpacing + rowGap) +              // Distance to last row baseline
            baseFontSize * 2                                       // Bottom padding (2 lines of text)
        ).toInt()

        // Create result bitmap
        val newWidth = imgW
        val newHeight = imgH + stripHeight

        val result = Bitmap.createBitmap(newWidth, newHeight, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Fill background
        canvas.drawColor(bgColor)
        
        // Draw original image at top
        canvas.drawBitmap(source, 0f, 0f, null)

        // Draw text rows in the strip area
        val leftX = padding
        val rightX = imgW - padding
        var y = imgH + padding + titleFontSize

        // Row 1: Date (left), Device (right)
        canvas.drawText(dateStr, leftX, y, titlePaint)
        if (deviceStr != null) {
            canvas.drawText(deviceStr, rightX, y, titlePaintRight)
        }

        // Row 2: Location (left), Camera info (right)
        if (locationStr != null || cameraInfoStr != null) {
            y += lineSpacing + rowGap
            if (locationStr != null) {
                canvas.drawText(locationStr, leftX, y, bodyPaint)
            }
            if (cameraInfoStr != null) {
                canvas.drawText(cameraInfoStr, rightX, y, bodyPaintRight)
            }
        }

        // Row 3: Pressure/Alt/Direction (left)
        if (row3Str != null) {
            y += lineSpacing + rowGap
            canvas.drawText(row3Str, leftX, y, bodyPaint)
        }

        // Row 4: Author (left), Brand (center), Powered by (right)
        y += lineSpacing + rowGap
        if (authorStr != null) {
            canvas.drawText(authorStr, leftX, y, smallPaint)
        }

        // Powered by on right
        canvas.drawText(poweredByStr, rightX, y, smallPaintRight)
        
        return result
    }

    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun getDirectionString(azimuth: Float): String {
        val directions = arrayOf(
            context.getString(R.string.watermark_direction_n),
            context.getString(R.string.watermark_direction_ne),
            context.getString(R.string.watermark_direction_e),
            context.getString(R.string.watermark_direction_se),
            context.getString(R.string.watermark_direction_s),
            context.getString(R.string.watermark_direction_sw),
            context.getString(R.string.watermark_direction_w),
            context.getString(R.string.watermark_direction_nw),
            context.getString(R.string.watermark_direction_n)
        )
        val index = Math.round(((azimuth % 360) / 45)).toInt().coerceIn(0, 8)
        return "${azimuth.format(0)}° ${directions[index]}"
    }
}