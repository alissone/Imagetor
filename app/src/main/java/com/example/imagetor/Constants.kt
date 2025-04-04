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

class Constants {
    companion object {
        // Application wide constants
        const val APPLICATION_ID = "com.example.imagetor"

        // Image editing presets
        const val DEFAULT_BRIGHTNESS = 0.0f
        const val DEFAULT_CONTRAST = 1.0f
        const val DEFAULT_SATURATION = 1.0f
        const val DEFAULT_HUE = 0.0f
        const val DEFAULT_SHADOW = 0.5f
        const val DEFAULT_WHITE_BALANCE = 0.5f

        const val MIN_BRIGHTNESS = -1.0f
        const val MIN_CONTRAST = 0.0f
        const val MIN_SATURATION = 0.0f
        const val MIN_HUE = -180.0f
        const val MIN_SHADOW = 0.0f
        const val MIN_WHITE_BALANCE = 0.0f

        const val MAX_BRIGHTNESS = 1.0f
        const val MAX_CONTRAST = 2.0f
        const val MAX_SATURATION = 2.0f
        const val MAX_HUE = 180.0f
        const val MAX_SHADOW = 1.0f
        const val MAX_WHITE_BALANCE = 1.0f

        // Filter objects
        val BRIGHTNESS_FILTER = ImageFilter(
            name = "Brightness",
            currentLevel = DEFAULT_BRIGHTNESS,
            minLevel = MIN_BRIGHTNESS,
            maxLevel = MAX_BRIGHTNESS
        )

        val CONTRAST_FILTER = ImageFilter(
            name = "Contrast",
            currentLevel = DEFAULT_CONTRAST,
            minLevel = MIN_CONTRAST,
            maxLevel = MAX_CONTRAST
        )

        val SATURATION_FILTER = ImageFilter(
            name = "Saturation",
            currentLevel = DEFAULT_SATURATION,
            minLevel = MIN_SATURATION,
            maxLevel = MAX_SATURATION
        )

        val HUE_FILTER = ImageFilter(
            name = "Hue",
            currentLevel = DEFAULT_HUE,
            minLevel = MIN_HUE,
            maxLevel = MAX_HUE
        )

        val SHADOW_FILTER = ImageFilter(
            name = "Shadow",
            currentLevel = DEFAULT_SHADOW,
            minLevel = MIN_SHADOW,
            maxLevel = MAX_SHADOW
        )

        val WHITE_BALANCE_FILTER = ImageFilter(
            name = "White Balance",
            currentLevel = DEFAULT_WHITE_BALANCE,
            minLevel = MIN_WHITE_BALANCE,
            maxLevel = MAX_WHITE_BALANCE
        )
    }
}

enum class FilterType { BRIGHTNESS, CONTRAST, SATURATION, HUE, SHADOW, WHITE_BALANCE}
