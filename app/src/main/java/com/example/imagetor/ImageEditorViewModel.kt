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

        handler.postDelayed(pendingFilterRunnable!!, 100)
    }

    // Apply all active filters to the bitmap
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
}
