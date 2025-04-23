package com.example.imagetor

import android.graphics.Bitmap

/**
 * Interface for custom bitmap filters that can't be implemented with GPUImage
 */
interface BitmapFilter {
    val type: String
    var currentValue: Float
    val defaultValue: Float
    val minValue: Float
    val maxValue: Float
    val displayName: String

    /**
     * Applies the filter to the bitmap with the current value
     * @param bitmap The source bitmap to process
     * @return A new bitmap with the filter applied
     */
    fun applyFilter(bitmap: Bitmap): Bitmap

    /**
     * Whether this filter should be applied (usually true if value != default)
     */
    fun shouldApply(): Boolean = currentValue != defaultValue
}
