package com.example.imagetor
import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.imagetor.Constants.Companion.DEFAULT_BRIGHTNESS
import com.example.imagetor.Constants.Companion.DEFAULT_CONTRAST
import com.example.imagetor.Constants.Companion.DEFAULT_HUE
import com.example.imagetor.Constants.Companion.DEFAULT_SATURATION
import com.example.imagetor.Constants.Companion.DEFAULT_SHADOW
import com.example.imagetor.Constants.Companion.DEFAULT_WHITE_BALANCE
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

    // Current filter values
    private val filterAmounts = mutableMapOf(
        FilterType.BRIGHTNESS to DEFAULT_BRIGHTNESS,
        FilterType.CONTRAST to DEFAULT_CONTRAST,
        FilterType.SATURATION to DEFAULT_SATURATION,
        FilterType.HUE to DEFAULT_HUE,
        FilterType.SHADOW to DEFAULT_SHADOW,
        FilterType.WHITE_BALANCE to DEFAULT_WHITE_BALANCE
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
        filterAmounts[FilterType.BRIGHTNESS] = DEFAULT_BRIGHTNESS
        filterAmounts[FilterType.CONTRAST] = DEFAULT_CONTRAST
        filterAmounts[FilterType.SATURATION] = DEFAULT_SATURATION
        filterAmounts[FilterType.HUE] = DEFAULT_HUE
        filterAmounts[FilterType.SHADOW] = DEFAULT_SHADOW
        filterAmounts[FilterType.WHITE_BALANCE] = DEFAULT_WHITE_BALANCE

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
        return filterAmounts.keys.toList()
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

        if (filterAmounts[FilterType.BRIGHTNESS] != 0.0f) {
            filters.add(GPUImageBrightnessFilter(
                    (filterAmounts[FilterType.BRIGHTNESS]!! / 100) - 1
                ))
        }

        if (filterAmounts[FilterType.CONTRAST] != 0.0f) {
            filters.add(GPUImageContrastFilter(filterAmounts[FilterType.CONTRAST]!!))
        }

        if (filterAmounts[FilterType.SATURATION] != 1.0f) {
            filters.add(GPUImageSaturationFilter(filterAmounts[FilterType.SATURATION]!!))
        }

        if (filterAmounts[FilterType.HUE] != 0.0f) {
            filters.add(GPUImageHueFilter(filterAmounts[FilterType.HUE]!!))
        }

        if (filterAmounts[FilterType.SHADOW] != 0.5f) {
            val shadowValue = (filterAmounts[FilterType.SHADOW]!! - 0.5f) * 2
            filters.add(GPUImageContrastFilter(1 + shadowValue))
        }

        if (filterAmounts[FilterType.WHITE_BALANCE] != 0.5f) {
            val wbValue = (filterAmounts[FilterType.WHITE_BALANCE]!! - 0.5f) * 2
            filters.add(GPUImageWhiteBalanceFilter(5000 + wbValue * 3000, 1.0f))
        }

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
