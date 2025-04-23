package com.example.imagetor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * A custom brightness filter that operates directly on bitmaps
 */
class CustomBrightnessFilter : BitmapFilter {
    override val type = "CUSTOM_BRIGHTNESS"
    override var currentValue = 0f
    override val defaultValue = 0f
    override val minValue = -1f
    override val maxValue = 1f
    override val displayName = "Custom Brightness"

    override fun applyFilter(bitmap: Bitmap): Bitmap {
        // Create output bitmap of the same size with safe config handling
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
        val canvas = Canvas(output)

        // Create color matrix for brightness adjustment
        val colorMatrix = ColorMatrix().apply {
            setScale(1f + currentValue, 1f + currentValue, 1f + currentValue, 1f)
        }

        // Create paint with color filter
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        // Draw the original bitmap with the filter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return output
    }
}
