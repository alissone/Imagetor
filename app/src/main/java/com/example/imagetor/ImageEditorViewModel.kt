package com.example.imagetor

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*

private const val touchEventInterval = 100L

class ImageEditorViewModel(application: Application) : AndroidViewModel(application) {

    // LiveData for UI updates
    private val _originalBitmap = MutableLiveData<Bitmap?>()
    val originalBitmap: LiveData<Bitmap?> = _originalBitmap

    private val _modifiedBitmap = MutableLiveData<Bitmap?>()
    val modifiedBitmap: LiveData<Bitmap?> = _modifiedBitmap

    private val _filterValues = MutableLiveData<Map<FilterType, Float>>()
    val filterValues: LiveData<Map<FilterType, Float>> = _filterValues

    // GPUImage instance for image processing
    private val gpuImage = GPUImage(application)

    // Active filters
    private val filters = ImageFilters.createAllFilters()


    // Handler for debouncing filter processing
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFilterRunnable: Runnable? = null

    // Set original bitmap and apply initial processing
    fun setOriginalBitmap(bitmap: Bitmap) {
        _originalBitmap.value = bitmap

        // Set the bitmap in GPUImage
        gpuImage.setImage(bitmap)

        // Apply any current filters
        processBitmap()
    }

    // Get the modified bitmap
    fun getModifiedBitmap(): Bitmap? {
        return _modifiedBitmap.value
    }

    // Reset all filters to default values
    fun resetFilters() {
        filters.forEach { filter ->
            filter.currentValue = filter.defaultValue
        }
        updateFilterValuesMap()
        processBitmap()
    }

    fun updateFilter(filterType: FilterType, value: Float) {
        filters.find { it.type == filterType }?.let { filter ->
            // Ensure value is within valid range
            filter.currentValue = value.coerceIn(filter.minValue, filter.maxValue)
            updateFilterValuesMap()
            processBitmap()
        }
    }

    // Get all available filter types
    fun getFilterTypes(): List<FilterType> {
        return filters.map { it.type }
    }

    fun getFilterName(optionIndex: Int): String {
        var names = arrayOf("Brightness", "Contrast", "Saturation", "Hue", "Shadow", "White_balance")
        return names[optionIndex]
    }

    // Get current value for a specific filter
    fun getFilterValue(filterType: FilterType): Float? {
        return filters.find { it.type == filterType }?.currentValue
    }

    // Get display name for a filter type
    fun getFilterDisplayName(filterType: FilterType): String {
        return filters.find { it.type == filterType }?.displayName ?: filterType.toString()
    }

    // Get min and max values for a filter type
    fun getFilterRange(filterType: FilterType): Pair<Float, Float>? {
        filters.find { it.type == filterType }?.let {
            return Pair(it.minValue, it.maxValue)
        }
        return null
    }


    // Update the filter values LiveData map
    private fun updateFilterValuesMap() {
        val valuesMap = filters.associate { it.type to it.currentValue }
        _filterValues.value = valuesMap
    }

    // Process the bitmap with current filters (debounced)
    private fun processBitmap() {
        pendingFilterRunnable?.let { handler.removeCallbacks(it) }

        pendingFilterRunnable = Runnable {
            applyFilters()
        }

        handler.postDelayed(pendingFilterRunnable!!, touchEventInterval)
    }

    // Apply all active filters to the bitmap
    private fun applyFilters() {
        // Get only filters that should be applied
        val activeFilters = filters.filter { it.shouldApply() }
            .map { it.createGPUFilter() }

        if (activeFilters.isNotEmpty()) {
            val filterGroup = GPUImageFilterGroup()
            activeFilters.forEach { filterGroup.addFilter(it) }
            gpuImage.setFilter(filterGroup)

            // Update modified bitmap
            _modifiedBitmap.postValue(gpuImage.bitmapWithFilterApplied)
        } else {
            // No filters - just use original bitmap
            _modifiedBitmap.postValue(_originalBitmap.value)
        }
    }

    // Clean up resources when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        pendingFilterRunnable?.let { handler.removeCallbacks(it) }
    }


    /**
     * Converts a filter value to seekbar progress (0-100)
     */
    fun filterValueToProgress(filterType: FilterType, value: Float): Int {
        val (min, max) = getFilterRange(filterType) ?: return 0
        return ((value - min) / (max - min) * 100L).toInt()
    }

    /**
     * Converts seekbar progress (0-100) to a filter value
     */
    fun progressToFilterValue(filterType: FilterType, progress: Int): Float {
        val (min, max) = getFilterRange(filterType) ?: return 0f
        return min + (progress / 100f) * (max - min)
    }

    /**
     * Gets the appropriate suffix for the filter value display
     */
    fun getFilterValueSuffix(filterType: FilterType): String {
        return when (filterType) {
            FilterType.HUE -> "°"
            else -> "%"
        }
    }

    /**
     * Gets a formatted display string for a filter value
     */
    fun getFilterValueDisplay(filterType: FilterType, value: Float): String {
        val progress = filterValueToProgress(filterType, value)
        return "$progress${getFilterValueSuffix(filterType)}"
    }

}
