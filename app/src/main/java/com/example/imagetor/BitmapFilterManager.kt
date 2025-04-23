package com.example.imagetor

import android.graphics.Bitmap

/**
 * Manager for handling custom bitmap filters
 */
class BitmapFilterManager {
    private val filters = mutableMapOf<String, BitmapFilter>()

    /**
     * Register a custom filter
     */
    fun registerFilter(filter: BitmapFilter) {
        filters[filter.type] = filter
    }

    /**
     * Get a filter by type
     */
    fun getFilter(type: String): BitmapFilter? = filters[type]

    /**
     * Get all registered filters
     */
    fun getAllFilters(): List<BitmapFilter> = filters.values.toList()

    /**
     * Get active filters that should be applied
     */
    fun getActiveFilters(): List<BitmapFilter> =
        filters.values.filter { it.shouldApply() }

    /**
     * Apply all active filters to the bitmap in sequence
     */
    fun applyFilters(sourceBitmap: Bitmap): Bitmap {
        var resultBitmap = sourceBitmap

        getActiveFilters().forEach { filter ->
            resultBitmap = filter.applyFilter(resultBitmap)
        }

        return resultBitmap
    }

    /**
     * Reset all filters to their default values
     */
    fun resetAllFilters() {
        filters.values.forEach { filter ->
            filter.currentValue = filter.defaultValue
        }
    }
}
