package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Filter engine providing color matrix filters for camera preview and capture.
 * Modes: 0=None, 1=B&W, 2=Vintage, 3=Cool, 4=Vivid
 */
object FilterEngine {

    fun getColorMatrix(mode: Int): ColorMatrix? {
        return when (mode) {
            1 -> grayscaleMatrix()
            2 -> vintageMatrix()
            3 -> coolMatrix()
            4 -> vividMatrix()
            else -> null // No filter
        }
    }

    /**
     * Apply filter to a bitmap (used during capture)
     */
    fun applyFilter(source: Bitmap, mode: Int): Bitmap {
        val matrix = getColorMatrix(mode) ?: return source
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /**
     * Grayscale / Black & White
     */
    private fun grayscaleMatrix(): ColorMatrix {
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        return cm
    }

    /**
     * Vintage / Warm sepia tone
     */
    private fun vintageMatrix(): ColorMatrix {
        // First desaturate partially
        val saturation = ColorMatrix()
        saturation.setSaturation(0.5f)

        // Then apply warm tint
        val tint = ColorMatrix(floatArrayOf(
            1.2f, 0.1f, 0.0f, 0f, 10f,
            0.0f, 1.0f, 0.0f, 0f, 5f,
            0.0f, 0.0f, 0.8f, 0f, -10f,
            0.0f, 0.0f, 0.0f, 1f, 0f
        ))

        val result = ColorMatrix()
        result.setConcat(tint, saturation)
        return result
    }

    /**
     * Cool / Blue-tinted
     */
    private fun coolMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            0.9f, 0.0f, 0.0f, 0f, -5f,
            0.0f, 0.95f, 0.05f, 0f, 0f,
            0.0f, 0.05f, 1.15f, 0f, 15f,
            0.0f, 0.0f, 0.0f, 1f, 0f
        ))
    }

    /**
     * Vivid / High saturation + contrast
     */
    private fun vividMatrix(): ColorMatrix {
        val saturation = ColorMatrix()
        saturation.setSaturation(1.6f)

        // Slight contrast boost
        val contrast = ColorMatrix(floatArrayOf(
            1.1f, 0.0f, 0.0f, 0f, -15f,
            0.0f, 1.1f, 0.0f, 0f, -15f,
            0.0f, 0.0f, 1.1f, 0f, -15f,
            0.0f, 0.0f, 0.0f, 1f, 0f
        ))

        val result = ColorMatrix()
        result.setConcat(contrast, saturation)
        return result
    }
}
