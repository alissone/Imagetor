package com.example.imagetor

/**
 * Manages filter categories and the currently selected filter within a category.
 * Uses FilterInfo from the ViewModel as the source of truth.
 */
class FilterCategories(private val viewModel: ImageEditorViewModel) {

    private var allFilters: List<ImageEditorViewModel.FilterInfo> = emptyList()
    private var currentGroupFilters: List<ImageEditorViewModel.FilterInfo> = emptyList()
    private var currentCategoryName: String = "light" // Default category/group
    private var currentIndex: Int = -1

    // The currently selected filter based on category and index
    var currentFilter: ImageEditorViewModel.FilterInfo? = null
        private set

    init {
        updateFiltersFromViewModel()
        setCategory(currentCategoryName) // Initialize with the default category
    }

    // Fetch the latest filter list from the ViewModel
    private fun updateFiltersFromViewModel() {
        allFilters = viewModel.getAllFilterTypes()
    }

    /**
     * Sets the current active filter category (group).
     * Updates the list of filters available within this group and resets the index.
     */
    fun setCategory(groupName: String) {
        currentCategoryName = groupName
        // Filter the master list by the selected group name
        currentGroupFilters = allFilters.filter { it.group.equals(groupName, ignoreCase = true) }
        // Reset index to the beginning of the new group
        currentIndex = if (currentGroupFilters.isNotEmpty()) 0 else -1
        currentFilter = currentGroupFilters.getOrNull(currentIndex)
    }

    /**
     * Gets the list of filters belonging to the currently selected category/group.
     */
    fun getCurrentCategoryFilters(): List<ImageEditorViewModel.FilterInfo> {
        return currentGroupFilters
    }

    /**
     * Moves to the next filter within the current category, wrapping around.
     */
    fun nextFilter() {
        if (currentGroupFilters.isEmpty()) return
        currentIndex = (currentIndex + 1) % currentGroupFilters.size
        currentFilter = currentGroupFilters[currentIndex]
    }

    /**
     * Moves to the previous filter within the current category, wrapping around.
     */
    fun previousFilter() {
        if (currentGroupFilters.isEmpty()) return
        currentIndex = (currentIndex - 1 + currentGroupFilters.size) % currentGroupFilters.size
        currentFilter = currentGroupFilters[currentIndex]
    }

    /**
     * Selects a specific filter within the currently active category by its ID and type.
     * Useful for directly jumping to a filter (e.g., clicking "Sharpen").
     * Returns true if the filter was found and selected, false otherwise.
     */
    fun selectFilterById(filterId: String, filterCategory: String): Boolean {
        val index = currentGroupFilters.indexOfFirst {
            it.id.equals(filterId, ignoreCase = true) &&
                    it.category.equals(filterCategory, ignoreCase = true)
        }
        if (index != -1) {
            currentIndex = index
            currentFilter = currentGroupFilters[index]
            return true
        }
        return false // Filter not found in the current group
    }
}
