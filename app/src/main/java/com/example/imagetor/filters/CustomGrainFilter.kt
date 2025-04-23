package com.example.imagetor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Random

/**
 * A custom grain filter implementation
 */
class CustomGrainFilter : BitmapFilter {
    override val type = "CUSTOM_GRAIN"
    override var currentValue = 0f
    override val defaultValue = 0f
    override val minValue = 0f
    override val maxValue = 1f
    override val displayName = "Film Grain"

    override fun applyFilter(bitmap: Bitmap): Bitmap {
        if (currentValue <= 0.01f) return bitmap

        // Create a new bitmap with safe config handling
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
        val canvas = Canvas(resultBitmap)


        // Draw the original bitmap
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // Create paint for grain
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Generate random noise
        val random = Random()
        val density = 0.1f * currentValue

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (random.nextFloat() < density) {
                    // Apply grain with random intensity
                    val alpha = (40 * currentValue).toInt()
                    val noiseColor = if (random.nextBoolean()) {
                        Color.argb(alpha, 255, 255, 255)
                    } else {
                        Color.argb(alpha, 0, 0, 0)
                    }

                    paint.color = noiseColor
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }

        return resultBitmap
    }
}
