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

enum class FilterType { BRIGHTNESS, CONTRAST, SATURATION, HUE, SHADOW, WHITE_BALANCE, STRUCTURE }

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

    // Current filter values
    private val filterAmounts = mutableMapOf(
        FilterType.BRIGHTNESS to 0.0f,
        FilterType.CONTRAST to 0.0f,
        FilterType.SATURATION to 1.0f,
        FilterType.HUE to 0.0f,
        FilterType.SHADOW to 0.5f,
        FilterType.WHITE_BALANCE to 0.5f
    )

    // Filter types available for editing
    private val availableFilterTypes = listOf(
        FilterType.BRIGHTNESS,
        FilterType.CONTRAST,
        FilterType.SATURATION,
        FilterType.HUE,
        FilterType.SHADOW,
        FilterType.WHITE_BALANCE
    )

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
        filterAmounts[FilterType.BRIGHTNESS] = 0.0f
        filterAmounts[FilterType.CONTRAST] = 0.0f
        filterAmounts[FilterType.SATURATION] = 1.0f
        filterAmounts[FilterType.HUE] = 0.0f
        filterAmounts[FilterType.SHADOW] = 0.5f
        filterAmounts[FilterType.WHITE_BALANCE] = 0.5f

        _filterValues.value = filterAmounts.toMap()
        processBitmap()
    }

    // Update a specific filter value
    fun updateFilter(filterType: FilterType, value: Float) {
        filterAmounts[filterType] = value
        _filterValues.value = filterAmounts.toMap()
        processBitmap()
    }

    // Get all available filter types
    fun getFilterTypes(): List<FilterType> {
        return availableFilterTypes
    }

    // Get current value for a specific filter
    fun getFilterValue(filterType: FilterType): Float? {
        return filterAmounts[filterType]
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
    private fun applyFilters() {
        val filters = mutableListOf<GPUImageFilter>()

        // Add Brightness filter if needed
        if (filterAmounts[FilterType.BRIGHTNESS] != 0.0f) {
            filters.add(GPUImageBrightnessFilter(filterAmounts[FilterType.BRIGHTNESS]!!))
        }

        // Add Contrast filter if needed
        if (filterAmounts[FilterType.CONTRAST] != 0.0f) {
            filters.add(GPUImageContrastFilter(filterAmounts[FilterType.CONTRAST]!!))
        }

        // Add Saturation filter if needed
        if (filterAmounts[FilterType.SATURATION] != 1.0f) {
            filters.add(GPUImageSaturationFilter(filterAmounts[FilterType.SATURATION]!!))
        }

        // Add Hue filter if needed
        if (filterAmounts[FilterType.HUE] != 0.0f) {
            filters.add(GPUImageHueFilter(filterAmounts[FilterType.HUE]!!))
        }

        // Add Shadow filter if needed
        if (filterAmounts[FilterType.SHADOW] != 0.5f) {
            val shadowValue = (filterAmounts[FilterType.SHADOW]!! - 0.5f) * 2
            filters.add(GPUImageContrastFilter(1 + shadowValue))
        }

        // Add White Balance filter if needed
        if (filterAmounts[FilterType.WHITE_BALANCE] != 0.5f) {
            val wbValue = (filterAmounts[FilterType.WHITE_BALANCE]!! - 0.5f) * 2
            filters.add(GPUImageWhiteBalanceFilter(5000 + wbValue * 3000, 1.0f))
        }

        // Apply all filters
        if (filters.isNotEmpty()) {
            val filterGroup = GPUImageFilterGroup()
            filters.forEach { filterGroup.addFilter(it) }
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
