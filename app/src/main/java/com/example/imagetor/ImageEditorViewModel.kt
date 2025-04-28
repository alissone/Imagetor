package com.example.imagetor

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.os.Handler
import android.os.Looper
import com.example.imagetor.filters.ImageOperations
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*

private const val FILTER_PROCESSING_DELAY = 100L

class ImageEditorViewModel(application: Application) : AndroidViewModel(application) {

    // LiveData for UI updates
    private val _originalBitmap = MutableLiveData<Bitmap?>()
    val originalBitmap: LiveData<Bitmap?> = _originalBitmap

    private val _modifiedBitmap = MutableLiveData<Bitmap?>()
    val modifiedBitmap: LiveData<Bitmap?> = _modifiedBitmap

    // Keep both LiveData objects for compatibility
    private val _filterValuesOld = MutableLiveData<Map<FilterType, Float>>()
    val filterValues: LiveData<Map<FilterType, Float>> = _filterValuesOld

    // New map for string keys
    private val _filterValuesNew = MutableLiveData<Map<String, Float>>()
    val filterValuesById: LiveData<Map<String, Float>> = _filterValuesNew

    // GPUImage instance for image processing
    private val gpuImage = GPUImage(application)

    // Filter store - maintains all filter states (GPU filters)
    private val filterStore = FilterStore()

    // Image operations handler
    private val imageOperations = ImageOperations(application)

    // Handler for debouncing filter processing
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFilterRunnable: Runnable? = null

    // Instance of FilterManager for custom bitmap filters
    private val bitmapFilterManager = BitmapFilterManager()

    // Define mapping from Filter ID (FilterType name or BitmapFilter type) to Group Name
    private val filterGroupMap: Map<String, String> = mapOf(
        // GPU Filters (using FilterType names)
        FilterType.BRIGHTNESS.name to "light",
        FilterType.CONTRAST.name to "light",
        FilterType.SHADOW.name to "light", // Assuming shadow is light adjustment
        FilterType.SATURATION.name to "color",
        FilterType.HUE.name to "color",
        FilterType.WHITE_BALANCE.name to "color",
        FilterType.VIBRANCE.name to "color",
        FilterType.GAMMA.name to "detail", // Assuming gamma is detail
        FilterType.SHARPEN.name to "detail",
        FilterType.VIGNETTE.name to "effects",
        // Bitmap Filters (using their 'type' strings - ensure these match your BitmapFilter types)
        "CUSTOM_BRIGHTNESS" to "light" // Example ID
        // Add other custom filters and their groups here
    )

    init {
        // Register custom bitmap filters here
        bitmapFilterManager.registerFilter(CustomBrightnessFilter()) // Example registration
        // Call this after initialization to populate LiveData
        updateFilterValuesMap()
    }

    /**
     * Sets the original bitmap and applies initial filters
     */
    fun setOriginalBitmap(bitmap: Bitmap) {
        _originalBitmap.value = bitmap
        // Reset filters to default when loading a new image
        resetFilters() // This also calls applyFilters and updateFilterValuesMap
        // Set the image *after* resetting filters if GPUImage holds state
        gpuImage.setImage(bitmap)
    }

    /**
     * Gets the current modified bitmap
     */
    fun getModifiedBitmap(): Bitmap? {
        return _modifiedBitmap.value
    }

    /**
     * Resets all filters (GPU and Bitmap) to their default values
     */
    fun resetFilters() {
        filterStore.resetAllFilters()
        bitmapFilterManager.resetAllFilters()
        updateFilterValuesMap() // Update LiveData with default values
        applyFilters() // Apply the default filter state
    }

    // Data class to represent filter info for UI - now includes group
    data class FilterInfo(
        val id: String,          // Filter type ID (enum name or custom string)
        val category: String,    // "gpu" or "bitmap"
        val displayName: String, // User-friendly name
        val group: String        // e.g., "light", "color", "detail", "effects"
    )

    // Get all filter types (both GPU and custom) as FilterInfo objects
    fun getAllFilterTypes(): List<FilterInfo> {
        val gpuFilters = filterStore.getAllFilters().mapNotNull { filter ->
            val id = filter.type.name
            filterGroupMap[id]?.let { group ->
                FilterInfo(id, "gpu", filter.displayName, group)
            }
        }

        val bitmapFilters = bitmapFilterManager.getAllFilters().mapNotNull { filter ->
            val id = filter.type
            filterGroupMap[id]?.let { group ->
                FilterInfo(id, "bitmap", filter.displayName, group)
            }
        }
        // Combine and potentially sort if needed
        return (gpuFilters + bitmapFilters).sortedBy { it.displayName }
    }

    // Get filter value regardless of type using FilterInfo
    fun getFilterValueByInfo(info: FilterInfo): Float {
        return when (info.category) {
            "gpu" -> getGpuFilterValue(FilterType.valueOf(info.id))
            "bitmap" -> getBitmapFilterValue(info.id)
            else -> 0f // Or a sensible default/error value
        }
    }

    // Update filter value regardless of type using FilterInfo
    fun updateFilterByInfo(info: FilterInfo, value: Float) {
        when (info.category) {
            "gpu" -> updateGpuFilter(FilterType.valueOf(info.id), value)
            "bitmap" -> updateBitmapFilter(info.id, value)
        }
    }

    // Get filter range regardless of type using FilterInfo
    fun getFilterRangeByInfo(info: FilterInfo): Pair<Float, Float> {
        return when (info.category) {
            "gpu" -> getGpuFilterRange(FilterType.valueOf(info.id))
            "bitmap" -> getBitmapFilterRange(info.id)
            else -> Pair(0f, 1f) // Default range
        }
    }

    // --- GPU Filter Specific Methods ---

    /**
     * Updates a specific GPU filter with a new value
     */
    fun updateGpuFilter(filterType: FilterType, value: Float) {
        val filter = filterStore.getFilter(filterType)
        // Ensure value is within valid range
        filter.currentValue = value.coerceIn(filter.minValue, filter.maxValue)
        updateFilterValuesMap() // Update LiveData
        scheduleFilterProcessing()
    }

    /** Gets the current value for a specific GPU filter */
    fun getGpuFilterValue(filterType: FilterType): Float {
        return filterStore.getFilter(filterType).currentValue
    }

    /** Gets the min and max values for a GPU filter */
    fun getGpuFilterRange(filterType: FilterType): Pair<Float, Float> {
        val filter = filterStore.getFilter(filterType)
        return Pair(filter.minValue, filter.maxValue)
    }

    // --- Bitmap Filter Specific Methods ---

    /** Updates a specific Bitmap filter with a new value */
    fun updateBitmapFilter(type: String, value: Float) {
        val filter = bitmapFilterManager.getFilter(type) ?: return
        filter.currentValue = value.coerceIn(filter.minValue, filter.maxValue)
        updateFilterValuesMap() // Update LiveData
        scheduleFilterProcessing()
    }

    /** Gets the current value for a specific Bitmap filter */
    fun getBitmapFilterValue(type: String): Float {
        return bitmapFilterManager.getFilter(type)?.currentValue ?: 0f // Return default if not found
    }

    /** Gets the min and max values for a Bitmap filter */
    fun getBitmapFilterRange(type: String): Pair<Float, Float> {
        val filter = bitmapFilterManager.getFilter(type) ?: return Pair(0f, 1f) // Default range
        return Pair(filter.minValue, filter.maxValue)
    }

    // --- Filter Application ---

    /**
     * Schedules filter processing with debouncing
     */
    private fun scheduleFilterProcessing() {
        pendingFilterRunnable?.let { handler.removeCallbacks(it) }
        pendingFilterRunnable = Runnable { applyFilters() }
        handler.postDelayed(pendingFilterRunnable!!, FILTER_PROCESSING_DELAY)
    }

    /**
     * Core function that applies GPU and then Bitmap filters
     */
    private fun applyFilters() {
        _originalBitmap.value?.let { original ->
            // Make sure GPUImage has the latest original bitmap
            gpuImage.setImage(original)

            // First apply GPUImage filters
            val filterGroup = generateFilterGroup()
            gpuImage.setFilter(filterGroup)
            var resultBitmap = try {
                gpuImage.bitmapWithFilterApplied // This can throw errors sometimes
            } catch (e: Exception) {
                // Log error, maybe return original bitmap
                System.err.println("Error applying GPU filters: ${e.message}")
                original
            }

            // Then apply custom bitmap filters if GPU step succeeded
            if (resultBitmap != null) {
                resultBitmap = bitmapFilterManager.applyFilters(resultBitmap)
            } else {
                // Handle case where GPU filtering failed
                resultBitmap = original // Fallback
            }

            // Update the modified bitmap LiveData
            _modifiedBitmap.postValue(resultBitmap)
        } ?: run {
            // If original bitmap is null, clear modified bitmap
            _modifiedBitmap.postValue(null)
        }
    }

    /**
     * Generates a GPUImageFilterGroup based on current filter values
     */
    private fun generateFilterGroup(): GPUImageFilter {
        val activeFilters = filterStore.getActiveFilters()
            .map { it.createGPUFilter() }

        return if (activeFilters.isNotEmpty()) {
            GPUImageFilterGroup(activeFilters) // Pass list directly to constructor
        } else {
            // Return an identity filter if no active filters
            GPUImageFilter()
        }
    }

    // --- LiveData Update ---

    /**
     * Updates both filter values maps (old and new) for LiveData observers
     */
    private fun updateFilterValuesMap() {
        // Update old map (FilterType -> Float)
        val oldMap = filterStore.getAllFilters()
            .associate { it.type to it.currentValue }
        _filterValuesOld.value = oldMap

        // Update new map (String -> Float)
        val gpuValues = filterStore.getAllFilters()
            .associate { it.type.name to it.currentValue }
        val bitmapValues = bitmapFilterManager.getAllFilters()
            .associate { it.type to it.currentValue }

        // Merge the two maps
        _filterValuesNew.value = gpuValues + bitmapValues
    }

    // --- UI Helper Methods (Value Conversion, Display Formatting) ---

    /**
     * Converts a filter value to seekbar progress (0-100) using FilterInfo
     */
    fun filterValueToProgress(info: FilterInfo, value: Float): Int {
        val range = getFilterRangeByInfo(info)
        // Avoid division by zero if min == max
        return if (range.second == range.first) {
            0
        } else {
            ((value - range.first) / (range.second - range.first) * 100).toInt().coerceIn(0, 100)
        }
    }

    /**
     * Converts seekbar progress (0-100) to a filter value using FilterInfo
     */
    fun progressToFilterValue(info: FilterInfo, progress: Int): Float {
        val range = getFilterRangeByInfo(info)
        return (range.first + (progress / 100f) * (range.second - range.first)).coerceIn(range.first, range.second)
    }

    /**
     * Gets the appropriate suffix for filter value display based on FilterInfo
     */
    fun getFilterValueSuffix(info: FilterInfo): String {
        // Use the ID, assuming HUE is the only one with '°'
        return when (info.id.uppercase()) { // Use uppercase for safety
            FilterType.HUE.name -> "°"
            else -> "%"
        }
    }

    /**
     * Gets a formatted display string for a filter value based on FilterInfo
     */
    fun getFilterValueDisplay(info: FilterInfo, value: Float): String {
        val progress = filterValueToProgress(info, value)
        return "$progress${getFilterValueSuffix(info)}"
    }

    /**
     * Clean up resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        pendingFilterRunnable?.let { handler.removeCallbacks(it) }
    }

    /**
     * Inner class that manages GPU filter state
     * (Assuming Filter, FilterType, FilterFactory are defined elsewhere)
     */
    private inner class FilterStore {
        private val filters: Map<FilterType, Filter> = FilterType.values()
            .associateWith { FilterFactory.createFilter(it) }

        fun getFilter(type: FilterType): Filter = filters[type]!!
        fun getAllFilters(): List<Filter> = filters.values.toList()
        fun getActiveFilters(): List<Filter> = filters.values.filter { it.shouldApply() }

        fun resetAllFilters() {
            filters.values.forEach { filter ->
                filter.currentValue = filter.defaultValue
            }
        }
    }

    // --- Image Operation Methods (Now using ImageOperations class) ---

    fun flipImageHorizontally() {
        _originalBitmap.value?.let { bitmap ->
            val flippedBitmap = imageOperations.flipHorizontally(bitmap)
            // Update original bitmap and re-apply filters
            _originalBitmap.value = flippedBitmap
            gpuImage.setImage(flippedBitmap)
            applyFilters()
        }
    }

    fun flipImageVertically() {
        _originalBitmap.value?.let { bitmap ->
            val flippedBitmap = imageOperations.flipVertically(bitmap)
            // Update original bitmap and re-apply filters
            _originalBitmap.value = flippedBitmap
            gpuImage.setImage(flippedBitmap)
            applyFilters()
        }
    }

    fun applyGrainEffect(intensity: Float) {
        val currentBitmap = _modifiedBitmap.value ?: _originalBitmap.value ?: return
        val resultBitmap = imageOperations.applyGrain(currentBitmap, intensity)
        _modifiedBitmap.postValue(resultBitmap)
    }

    fun applyBlurEffect(radius: Float) {
        val currentBitmap = _modifiedBitmap.value ?: _originalBitmap.value ?: return
        val blurredBitmap = imageOperations.applyBlur(currentBitmap, radius)
        _modifiedBitmap.postValue(blurredBitmap)
    }
}
