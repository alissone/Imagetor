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

    // Instance of FilterManager
    private val bitmapFilterManager = BitmapFilterManager()

    init {
        bitmapFilterManager.registerFilter(CustomBrightnessFilter())
        // Add other custom filters here
    }

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
        bitmapFilterManager.resetAllFilters()
        updateFilterValuesMap()
        applyFilters()
    }

    // Get all filter types (both GPU and custom)
    fun getAllFilterTypes(): List<FilterInfo> {
        val gpuFilters = getFilterTypes().map {
            FilterInfo(it.name, "gpu", getFilterDisplayName(it))
        }

        val bitmapFilters = getBitmapFilterTypes().map {
            FilterInfo(it, "bitmap", getBitmapFilterDisplayName(it))
        }

        return gpuFilters + bitmapFilters
    }

    // Get filter value regardless of type
    fun getFilterValueByInfo(info: FilterInfo): Float {
        return when (info.category) {
            "gpu" -> getFilterValue(FilterType.valueOf(info.id))
            "bitmap" -> getBitmapFilterValue(info.id)
            else -> 0f
        }
    }

    // Update filter value regardless of type
    fun updateFilterByInfo(info: FilterInfo, value: Float) {
        when (info.category) {
            "gpu" -> updateFilter(FilterType.valueOf(info.id), value)
            "bitmap" -> updateBitmapFilter(info.id, value)
        }
    }

    // Data class to represent filter info for UI
    data class FilterInfo(
        val id: String,          // Filter type ID (enum name or custom string)
        val category: String,    // "gpu" or "bitmap"
        val displayName: String  // User-friendly name
    )

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

    fun getFilterRangeByInfo(info: FilterInfo): Pair<Float, Float> {
        return when (info.category) {
            "gpu" -> getFilterRange(FilterType.valueOf(info.id))
            "bitmap" -> getBitmapFilterRange(info.id)
            else -> Pair(0f, 1f)
        }
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
        // First apply GPUImage filters
        val filterGroup = generateFilterGroup()
        gpuImage.setFilter(filterGroup)
        var resultBitmap = gpuImage.bitmapWithFilterApplied

        // Then apply custom bitmap filters
        resultBitmap = bitmapFilterManager.applyFilters(resultBitmap)

        // Update the modified bitmap
        _modifiedBitmap.postValue(resultBitmap)
    }

    fun getBitmapFilterTypes(): List<String> {
        return bitmapFilterManager.getAllFilters().map { it.type }
    }

    fun updateBitmapFilter(type: String, value: Float) {
        val filter = bitmapFilterManager.getFilter(type) ?: return
        filter.currentValue = value.coerceIn(filter.minValue, filter.maxValue)
        scheduleFilterProcessing()
    }

    fun getBitmapFilterValue(type: String): Float {
        return bitmapFilterManager.getFilter(type)?.currentValue ?: 0f
    }

    fun getBitmapFilterDisplayName(type: String): String {
        return bitmapFilterManager.getFilter(type)?.displayName ?: "Unknown"
    }

    fun getBitmapFilterRange(type: String): Pair<Float, Float> {
        val filter = bitmapFilterManager.getFilter(type) ?: return Pair(0f, 1f)
        return Pair(filter.minValue, filter.maxValue)
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

    // Add these to your ImageEditorViewModel class

    /**
     * Applies horizontal flip to the current image
     */
    fun flipImageHorizontally() {
        _originalBitmap.value?.let { bitmap ->
            val matrix = android.graphics.Matrix()
            matrix.preScale(-1.0f, 1.0f)

            val flippedBitmap = Bitmap.createBitmap(
                bitmap,
                0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )

            // Update original bitmap with the flipped one
            _originalBitmap.value = flippedBitmap
            gpuImage.setImage(flippedBitmap)
            applyFilters()
        }
    }

    /**
     * Applies vertical flip to the current image
     */
    fun flipImageVertically() {
        _originalBitmap.value?.let { bitmap ->
            val matrix = android.graphics.Matrix()
            matrix.preScale(1.0f, -1.0f)

            val flippedBitmap = Bitmap.createBitmap(
                bitmap,
                0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )

            // Update original bitmap with the flipped one
            _originalBitmap.value = flippedBitmap
            gpuImage.setImage(flippedBitmap)
            applyFilters()
        }
    }

    /**
     * Applies a grain effect to the image
     * @param intensity Float value between 0.0 and 1.0
     */
    fun applyGrainEffect(intensity: Float) {
        // Get current modified bitmap
        val currentBitmap = _modifiedBitmap.value ?: return

        // Create a new bitmap with the same dimensions
        val resultBitmap = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(resultBitmap)

        // Draw the original bitmap
        canvas.drawBitmap(currentBitmap, 0f, 0f, null)

        // Create paint for grain
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }

        // Generate random noise
        val random = java.util.Random()
        for (x in 0 until currentBitmap.width) {
            for (y in 0 until currentBitmap.height) {
                if (random.nextFloat() < 0.1f * intensity) {
                    // Apply grain with random color and intensity
                    val noiseColor = if (random.nextBoolean()) {
                        android.graphics.Color.argb(
                            (50 * intensity).toInt(),
                            255, 255, 255
                        )
                    } else {
                        android.graphics.Color.argb(
                            (50 * intensity).toInt(),
                            0, 0, 0
                        )
                    }

                    paint.color = noiseColor
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }

        // Update the modified bitmap with grain effect
        _modifiedBitmap.value = resultBitmap
    }

    /**
     * Applies a blur effect to the image
     * @param radius The blur radius (1.0 for light blur, higher values for stronger blur)
     */
    fun applyBlurEffect(radius: Float) {
        // Get current bitmap (either modified or original)
        val currentBitmap = _modifiedBitmap.value ?: _originalBitmap.value ?: return

        // Create a RenderScript context
        val rs = android.renderscript.RenderScript.create(getApplication())

        try {
            // Create allocation for input and output
            val input = android.renderscript.Allocation.createFromBitmap(
                rs, currentBitmap,
                android.renderscript.Allocation.MipmapControl.MIPMAP_NONE,
                android.renderscript.Allocation.USAGE_SCRIPT
            )

            val output = android.renderscript.Allocation.createTyped(rs, input.type)

            // Create blur script and set parameters
            val blurScript = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs))
            blurScript.setRadius(radius)
            blurScript.setInput(input)

            // Execute the script and copy the result
            blurScript.forEach(output)

            // Create output bitmap
            val blurredBitmap = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, currentBitmap.config!!)
            output.copyTo(blurredBitmap)

            // Update the modified bitmap
            _modifiedBitmap.value = blurredBitmap

        } finally {
            // Clean up RenderScript resources
            rs.destroy()
        }
    }
}
