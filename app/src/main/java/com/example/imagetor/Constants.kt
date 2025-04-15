package com.example.imagetor

import android.graphics.PointF
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHighlightShadowFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHueFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVibranceFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter

// Core filter interface that all filters must implement (only works for GPUImage filters for now)
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

enum class FilterType {
    BRIGHTNESS, CONTRAST, SATURATION, HUE, SHADOW, WHITE_BALANCE, GAMMA, VIGNETTE, VIBRANCE
}

sealed class ImageFilters {

    class BrightnessFilter : Filter {
        override val type = FilterType.BRIGHTNESS
        override var currentValue = 0f
        override val defaultValue = 0f
        override val minValue = -1f
        override val maxValue = 1f
        override val displayName = "Brightness"

        override fun createGPUFilter() = GPUImageBrightnessFilter(currentValue)
        override fun shouldApply() = currentValue != defaultValue
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

//    class HighlightFilter : Filter {
//        override val type = FilterType.HIGHLIGHTS
//        override var currentValue = 0.5f
//        override val defaultValue = 0.5f
//        override val minValue = 0f
//        override val maxValue = 1f
//        override val displayName = "Highlights"
//
//        override fun createGPUFilter(): GPUImageFilter {
//            val shadowValue = (currentValue - 0.5f) * 2
//            return GPUImageHighlightShadowFilter(highLight)
//        }
//    }

    class VibranceFiter : Filter {
        override val type = FilterType.SHADOW
        override var currentValue = 0.5f
        override val defaultValue = 0.5f
        override val minValue = 0f
        override val maxValue = 1.0f
        override val displayName = "Vibrance"

        override fun createGPUFilter(): GPUImageFilter {
            val value = (currentValue - 0.5f) * 2
            return GPUImageVibranceFilter(1 + value)
        }
    }

    class VignetteFilter : Filter {
        override val type = FilterType.SHADOW
        override var currentValue = 0.5f
        override val defaultValue = 0.5f
        override val minValue = 0f
        override val maxValue = 2.0f
        override val displayName = "Vignette"

        override fun createGPUFilter(): GPUImageFilter {
            val value = (currentValue - 0.5f) * 2
            return GPUImageVignetteFilter(PointF(100f,100f), floatArrayOf(0.5f), 0.5f, 0.5f)
        }
    }
    class HazeFilter : Filter {
        override val type = FilterType.SHADOW
        override var currentValue = 0.5f
        override val defaultValue = 0.5f
        override val minValue = 0f
        override val maxValue = 2.0f
        override val displayName = "Haze"

        override fun createGPUFilter(): GPUImageFilter {
            val value = (currentValue - 0.5f) * 2
            return GPUImageVibranceFilter(1 + value)
        }
    }
    class GammaFilter : Filter {
        override val type = FilterType.SHADOW
        override var currentValue = 0.5f
        override val defaultValue = 0.5f
        override val minValue = 0f
        override val maxValue = 2.2f
        override val displayName = "Gamma"

        override fun createGPUFilter(): GPUImageFilter {
            val shadowValue = (currentValue - 0.5f) * 2
            return GPUImageContrastFilter(1 + shadowValue)
        }
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
            return GPUImageContrastFilter(1 + shadowValue)
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
            return GPUImageWhiteBalanceFilter(5000 + wbValue * 3000, 1.0f)
        }
    }

    companion object {
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
                FilterType.VIBRANCE -> VibranceFiter()
            }
        }

        fun createAllFilters(): List<Filter> {
            return FilterType.values().map { createFilter(it) }
        }
    }
}