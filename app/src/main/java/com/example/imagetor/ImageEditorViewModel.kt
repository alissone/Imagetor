package com.example.imagetor

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup

private const val FILTER_PROCESSING_DELAY = 100L

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

    // Filter store - maintains all filter states
    private val filterStore = FilterStore()

    // Handler for debouncing filter processing
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFilterRunnable: Runnable? = null

    /**
     * Sets the original bitmap and applies initial filters
     */
    fun setOriginalBitmap(bitmap: Bitmap) {
        _originalBitmap.value = bitmap
        gpuImage.setImage(bitmap)
        applyFilters()
    }

    /**
     * Gets the current modified bitmap
     */
    fun getModifiedBitmap(): Bitmap? {
        return _modifiedBitmap.value
    }

    /**
     * Resets all filters to their default values
     */
    fun resetFilters() {
        filterStore.resetAllFilters()
        updateFilterValuesMap()
        applyFilters()
    }

    /**
     * Updates a specific filter with a new value
     */
    fun updateFilter(filterType: FilterType, value: Float) {
        val filter = filterStore.getFilter(filterType)
        // Ensure value is within valid range
        filter.currentValue = value.coerceIn(filter.minValue, filter.maxValue)
        updateFilterValuesMap()
        scheduleFilterProcessing()
    }

    /**
     * Gets all available filter types
     */
    fun getFilterTypes(): List<FilterType> {
        return FilterType.values().toList()
    }

    /**
     * Gets the display name for a filter based on index
     */
    fun getFilterName(optionIndex: Int): String {
        val filterType = getFilterTypes()[optionIndex]
        return filterStore.getFilter(filterType).displayName
    }

    /**
     * Gets the current value for a specific filter
     */
    fun getFilterValue(filterType: FilterType): Float {
        return filterStore.getFilter(filterType).currentValue
    }

    /**
     * Gets the display name for a filter type
     */
    fun getFilterDisplayName(filterType: FilterType): String {
        return filterStore.getFilter(filterType).displayName
    }

    /**
     * Gets the min and max values for a filter
     */
    fun getFilterRange(filterType: FilterType): Pair<Float, Float> {
        val filter = filterStore.getFilter(filterType)
        return Pair(filter.minValue, filter.maxValue)
    }

    /**
     * Schedules filter processing with debouncing
     */
    private fun scheduleFilterProcessing() {
        pendingFilterRunnable?.let { handler.removeCallbacks(it) }
        pendingFilterRunnable = Runnable { applyFilters() }
        handler.postDelayed(pendingFilterRunnable!!, FILTER_PROCESSING_DELAY)
    }

    /**
     * Core function that generates filter group and applies it to the image
     */
    private fun applyFilters() {
        val filterGroup = generateFilterGroup()
        gpuImage.setFilter(filterGroup)
        _modifiedBitmap.postValue(gpuImage.bitmapWithFilterApplied)
    }

    /**
     * Generates a GPUImageFilterGroup based on current filter values
     */
    private fun generateFilterGroup(): GPUImageFilter {
        val activeFilters = filterStore.getActiveFilters()
            .map { it.createGPUFilter() }

        return if (activeFilters.isNotEmpty()) {
            GPUImageFilterGroup().apply {
                activeFilters.forEach { addFilter(it) }
            }
        } else {
            // Return an identity filter if no active filters
            GPUImageFilter()
        }
    }

    /**
     * Updates the filter values map for LiveData observers
     */
    private fun updateFilterValuesMap() {
        val valuesMap = filterStore.getAllFilters()
            .associate { it.type to it.currentValue }
        _filterValues.value = valuesMap
    }

    /**
     * Converts a filter value to seekbar progress (0-100)
     */
    fun filterValueToProgress(filterType: FilterType, value: Float): Int {
        val filter = filterStore.getFilter(filterType)
        return ((value - filter.minValue) / (filter.maxValue - filter.minValue) * 100).toInt()
    }

    /**
     * Converts seekbar progress (0-100) to a filter value
     */
    fun progressToFilterValue(filterType: FilterType, progress: Int): Float {
        val filter = filterStore.getFilter(filterType)
        return filter.minValue + (progress / 100f) * (filter.maxValue - filter.minValue)
    }

    /**
     * Gets the appropriate suffix for filter value display
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

    /**
     * Clean up resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        pendingFilterRunnable?.let { handler.removeCallbacks(it) }
    }

    /**
     * Inner class that manages filter state
     */
    private inner class FilterStore {
        private val filters: Map<FilterType, Filter> = FilterType.values()
            .associateWith { FilterFactory.createFilter(it) }

        fun getFilter(type: FilterType): Filter = filters[type]!!

        fun getAllFilters(): List<Filter> = filters.values.toList()

        fun getActiveFilters(): List<Filter> =
            filters.values.filter { it.shouldApply() }

        fun resetAllFilters() {
            filters.values.forEach { filter ->
                filter.currentValue = filter.defaultValue
            }
        }
    }
}
