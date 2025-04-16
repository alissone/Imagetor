package com.example.imagetor
import android.graphics.PointF
import jp.co.cyberagent.android.gpuimage.filter.*

enum class FilterType {
    BRIGHTNESS, CONTRAST, SATURATION, HUE, SHADOW, WHITE_BALANCE, GAMMA, VIGNETTE, VIBRANCE
}

// Core filter interface that all filters must implement
interface Filter {
    val type: FilterType
    var currentValue: Float
    val defaultValue: Float
    val minValue: Float
    val maxValue: Float
    val displayName: String
    fun createGPUFilter(): GPUImageFilter
    fun shouldApply(): Boolean = currentValue != defaultValue
}

object FilterFactory {
    // Create a filter instance based on type
    fun createFilter(type: FilterType): Filter {
        return when(type) {
            FilterType.BRIGHTNESS -> BrightnessFilter()
            FilterType.CONTRAST -> ContrastFilter()
            FilterType.SATURATION -> SaturationFilter()
            FilterType.HUE -> HueFilter()
            FilterType.SHADOW -> ShadowFilter()
            FilterType.WHITE_BALANCE -> WhiteBalanceFilter()
            FilterType.GAMMA -> GammaFilter()
            FilterType.VIGNETTE -> VignetteFilter()
            FilterType.VIBRANCE -> VibranceFilter()
        }
    }

    // Create all available filters
    fun createAllFilters(): List<Filter> {
        return FilterType.values().map { createFilter(it) }
    }
}

// Individual filter implementations
class BrightnessFilter : Filter {
    override val type = FilterType.BRIGHTNESS
    override var currentValue = 0f
    override val defaultValue = 0f
    override val minValue = -1f
    override val maxValue = 1f
    override val displayName = "Brightness"

    override fun createGPUFilter() = GPUImageBrightnessFilter(currentValue)
}

class ContrastFilter : Filter {
    override val type = FilterType.CONTRAST
    override var currentValue = 1f
    override val defaultValue = 1f
    override val minValue = 0f
    override val maxValue = 2f
    override val displayName = "Contrast"

    override fun createGPUFilter() = GPUImageContrastFilter(currentValue)
}

class SaturationFilter : Filter {
    override val type = FilterType.SATURATION
    override var currentValue = 1f
    override val defaultValue = 1f
    override val minValue = 0f
    override val maxValue = 2f
    override val displayName = "Saturation"

    override fun createGPUFilter() = GPUImageSaturationFilter(currentValue)
}

class HueFilter : Filter {
    override val type = FilterType.HUE
    override var currentValue = 0f
    override val defaultValue = 0f
    override val minValue = -180f
    override val maxValue = 180f
    override val displayName = "Hue"

    override fun createGPUFilter() = GPUImageHueFilter(currentValue)
}

class ShadowFilter : Filter {
    override val type = FilterType.SHADOW
    override var currentValue = 0.5f
    override val defaultValue = 0.5f
    override val minValue = 0f
    override val maxValue = 1f
    override val displayName = "Shadow"

    override fun createGPUFilter(): GPUImageFilter {
        val shadowValue = (currentValue - 0.5f) * 2
        return GPUImageHighlightShadowFilter(0f, shadowValue)
    }
}

class WhiteBalanceFilter : Filter {
    override val type = FilterType.WHITE_BALANCE
    override var currentValue = 0.5f
    override val defaultValue = 0.5f
    override val minValue = 0f
    override val maxValue = 1f
    override val displayName = "White Balance"

    override fun createGPUFilter(): GPUImageFilter {
        val wbValue = (currentValue - 0.5f) * 2
        return GPUImageWhiteBalanceFilter(5000 + wbValue * 3000, 0f)
    }
}

class GammaFilter : Filter {
    override val type = FilterType.GAMMA
    override var currentValue = 1f
    override val defaultValue = 1f
    override val minValue = 0.1f
    override val maxValue = 3f
    override val displayName = "Gamma"

    override fun createGPUFilter() = GPUImageGammaFilter(currentValue)
}

class VignetteFilter : Filter {
    override val type = FilterType.VIGNETTE
    override var currentValue = 0f
    override val defaultValue = 0f
    override val minValue = 0f
    override val maxValue = 1f
    override val displayName = "Vignette"

    override fun createGPUFilter(): GPUImageFilter {
        // Adjust vignette intensity based on current value
        return GPUImageVignetteFilter(
            PointF(0.5f, 0.5f),
            floatArrayOf(0f, 0f, 0f),
            1 - currentValue,
            1.0f
        )
    }
}

class VibranceFilter : Filter {
    override val type = FilterType.VIBRANCE
    override var currentValue = 0f
    override val defaultValue = 0f
    override val minValue = -1f
    override val maxValue = 1f
    override val displayName = "Vibrance"

    override fun createGPUFilter() = GPUImageVibranceFilter(currentValue)
}
