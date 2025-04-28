package com.example.imagetor.filters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import java.util.Random

/**
 * Class responsible for handling image transformation operations
 * such as flip, grain effect, and blur.
 */
class ImageOperations(private val context: Context) {

    /**
     * Flips the provided bitmap horizontally
     * @param bitmap The bitmap to flip
     * @return The horizontally flipped bitmap
     */
    fun flipHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.preScale(-1.0f, 1.0f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Flips the provided bitmap vertically
     * @param bitmap The bitmap to flip
     * @return The vertically flipped bitmap
     */
    fun flipVertically(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.preScale(1.0f, -1.0f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Applies a grain effect to the provided bitmap
     * @param bitmap The bitmap to apply grain to
     * @param intensity The intensity of the grain effect (0.0f to 1.0f)
     * @return The bitmap with grain effect applied
     */
    fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val random = Random()
        // Simplified grain logic
        val grainPoints = (bitmap.width * bitmap.height * intensity * 0.1).toInt()
        for (i in 0 until grainPoints) {
            val x = random.nextInt(bitmap.width).toFloat()
            val y = random.nextInt(bitmap.height).toFloat()
            paint.color = if (random.nextBoolean())
                Color.argb(50, 255, 255, 255)
            else
                Color.argb(50, 0, 0, 0)
            canvas.drawPoint(x, y, paint)
        }

        return resultBitmap
    }

    /**
     * Applies a blur effect to the provided bitmap
     * @param bitmap The bitmap to apply blur to
     * @param radius The radius of the blur (0.1f to 25f)
     * @return The blurred bitmap, or the original bitmap if blurring fails
     */
    fun applyBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val clampedRadius = radius.coerceIn(0.1f, 25f) // Clamp radius for RenderScript
        var rs: RenderScript? = null

        try {
            rs = RenderScript.create(context)

            val input = Allocation.createFromBitmap(rs, bitmap)
            val output = Allocation.createTyped(rs, input.type)

            val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blurScript.setRadius(clampedRadius)
            blurScript.setInput(input)
            blurScript.forEach(output)

            val blurredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config!!)
            output.copyTo(blurredBitmap)

            return blurredBitmap
        } catch (e: Exception) {
            System.err.println("Error applying blur: ${e.message}")
            return bitmap // Return original on error
        } finally {
            rs?.destroy()
        }
    }
}
