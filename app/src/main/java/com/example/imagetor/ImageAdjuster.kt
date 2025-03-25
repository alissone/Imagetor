package com.example.imagetor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHighlightShadowFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHueFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter


class ImageAdjuster(private val imageView: ImageView) {
    private var brightnessLevel: Int = 100
    private var saturationLevel: Float = 1f
    private var contrastLevel: Float = 1f


    private lateinit var mainImageView: GPUImageView

    // The default values
    private var saturationOpacity: Float = 50.0f
    private var warmOpacity: Float = 50.0f
    private var shadowOpacity: Int = 0
    private var hueOpacity: Int = 0


    fun setAdjustment(
        HueOpacity: Int,
        SaturationOpacity: Float,
        ShadowOpacity: Int,
        WarmOpacity: Float
    ): GPUImageFilterGroup {
        val filterGroup = GPUImageFilterGroup()
        filterGroup.addFilter(GPUImageHueFilter(range(HueOpacity, 0.0f, 360.0f)))
        filterGroup.addFilter(
            GPUImageHighlightShadowFilter(
                range(ShadowOpacity, 0.0f, 1.0f),
                range(0, 1.0f, 0.0f)
            )
        )
        filterGroup.addFilter(
            GPUImageWhiteBalanceFilter(
                range(
                    WarmOpacity.toInt(),
                    4000.0f,
                    8000.0f
                ), range(SaturationOpacity.toInt(), 0.0f, -2.0f)
            )
        )
        return filterGroup
    }


    protected fun range(percentage: Int, start: Float, end: Float): Float {
        return (((end - start) * (percentage.toFloat())) / 100.0f) + start
    }


    fun setHue(hueValue: Int) {
        hueOpacity = hueValue
        renderImage()
    }

    fun setBrightness(brightnessValue: Int) {
        this.brightnessLevel = brightnessValue
        renderImage()
    }

    fun setSaturation(saturationValue: Float) {
        this.saturationLevel = saturationValue
        renderImage()
    }

    fun setContrast(contrastValue: Float) {
        this.contrastLevel = contrastValue
        renderImage()
    }

    fun resetAdjustments() {
        brightnessLevel = 100
        saturationLevel = 1f
        contrastLevel = 1f
        renderImage()

    }

    fun applyColorFilterToBitmap(original: Bitmap, filter: ColorFilter): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = filter
        }
        canvas.drawBitmap(original, 0f, 0f, paint)
        return result
    }

    private fun generateColorFilter(
        brightness: Int,
        saturation: Float,
        contrast: Float
    ): ColorFilter {
        // Implement image adjustment logic here
        val brightness = (brightnessLevel - 100).toFloat() / 100

        val colorMatrix = ColorMatrix()

        // Apply saturation
        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(saturationLevel)
        colorMatrix.postConcat(saturationMatrix)

        // Apply brightness
        val brightnessMatrix = ColorMatrix()
        brightnessMatrix.setScale(1 + brightness, 1 + brightness, 1 + brightness, 1f)
        colorMatrix.postConcat(brightnessMatrix)

        // Apply contrast
        if (contrastLevel != 1f) {
            val scale = contrastLevel
            val translate = (-.5f * scale + .5f) * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(contrastMatrix)
        }

        return ColorMatrixColorFilter(colorMatrix)
    }

    fun renderImage() {
        imageView.colorFilter = generateColorFilter(brightnessLevel,contrastLevel,saturationLevel)
//        mainImageView.setFilter(setAdjustment(hueOpacity, saturationOpacity, shadowOpacity, warmOpacity));
    }

    fun setMainImageURI(selectedImageUri: Uri?) {
//        mainImageView.setImage(imageView.drawable.toBitmap())
//        mainImageView.setImage(selectedImageUri)
        print("Setting image URI to $selectedImageUri")
    }
}
