package com.example.imagetor

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
    }
}

enum class FilterType { BRIGHTNESS, CONTRAST, SATURATION, HUE, SHADOW, WHITE_BALANCE, STRUCTURE }