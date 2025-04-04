package com.example.imagetor

interface Filter {
    val name: String
    var currentLevel: Float
    val minLevel: Float
    val maxLevel: Float
}

data class ImageFilter(
    override val name: String,
    override var currentLevel: Float,
    override val minLevel: Float,
    override val maxLevel: Float
) : Filter

sealed class FilterConstants {
    object Defaults {
        const val BRIGHTNESS = 0.0f
        const val CONTRAST = 1.0f
        const val SATURATION = 1.0f
        const val HUE = 0.0f
        const val SHADOW = 0.5f
        const val WHITE_BALANCE = 0.5f
    }

    object Mins {
        const val BRIGHTNESS = -1.0f
        const val CONTRAST = -2.0f
        const val SATURATION = 0.0f
        const val HUE = -180.0f
        const val SHADOW = 0.0f
        const val WHITE_BALANCE = 0.0f
    }

    object Maxs {
        const val BRIGHTNESS = 200.0f
        const val CONTRAST = 2.0f
        const val SATURATION = 2.0f
        const val HUE = 180.0f
        const val SHADOW = 1.0f
        const val WHITE_BALANCE = 1.0f
    }


    object Filters {
        val BRIGHTNESS_FILTER = ImageFilter(
            name = "Brightness",
            currentLevel = Defaults.BRIGHTNESS,
            minLevel = Mins.BRIGHTNESS,
            maxLevel = Maxs.BRIGHTNESS
        )

        val CONTRAST_FILTER = ImageFilter(
            name = "Contrast",
            currentLevel = Defaults.CONTRAST,
            minLevel = Mins.CONTRAST,
            maxLevel = Maxs.CONTRAST
        )

        val SATURATION_FILTER = ImageFilter(
            name = "Saturation",
            currentLevel = Defaults.SATURATION,
            minLevel = Mins.SATURATION,
            maxLevel = Maxs.SATURATION
        )

        val HUE_FILTER = ImageFilter(
            name = "Hue",
            currentLevel = Defaults.HUE,
            minLevel = Mins.HUE,
            maxLevel = Maxs.HUE
        )

        val SHADOW_FILTER = ImageFilter(
            name = "Shadow",
            currentLevel = Defaults.SHADOW,
            minLevel = Mins.SHADOW,
            maxLevel = Maxs.SHADOW
        )

        val WHITE_BALANCE_FILTER = ImageFilter(
            name = "White Balance",
            currentLevel = Defaults.WHITE_BALANCE,
            minLevel = Mins.WHITE_BALANCE,
            maxLevel = Maxs.WHITE_BALANCE
        )
    }

    companion object {
        val FILTER_MAX_LEVELS = mapOf<FilterType,Float>(
            FilterType.BRIGHTNESS to Maxs.BRIGHTNESS,
            FilterType.CONTRAST to Maxs.CONTRAST,
            FilterType.SATURATION to Maxs.SATURATION,
            FilterType.HUE to Maxs.HUE,
            FilterType.SHADOW to Maxs.SHADOW,
            FilterType.WHITE_BALANCE to Maxs.WHITE_BALANCE,
        )
    }
}

enum class FilterType { BRIGHTNESS, CONTRAST, SATURATION, HUE, SHADOW, WHITE_BALANCE }
