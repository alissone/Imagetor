package com.example.imagetor

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog // Keep AlertDialog for Flip
import androidx.appcompat.app.AppCompatActivity
// import androidx.core.content.ContextCompat // No longer used directly
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
// import com.example.imagetor.ImageEditorViewModel // ViewModel is used implicitly
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class ImageViewActivity : AppCompatActivity() {

    // UI Components
    private lateinit var toggleButton: ToggleButton
    private lateinit var originalImageView: ImageView
    private lateinit var modifiedImageView: ImageView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var subcategoriesContainer: LinearLayout

    // Single dynamic seekbar components
    private lateinit var filterSeekBar: SeekBar
    private lateinit var filterNameLabel: TextView
    private lateinit var filterValueLabel: TextView
    private lateinit var filterControlsContainer: LinearLayout
    private lateinit var prevFilterButton: ImageView
    private lateinit var nextFilterButton: ImageView

    // UI Components for popup
    private lateinit var optionsPopup: LinearLayout
    private lateinit var optionTitle: TextView
    private lateinit var optionValue: TextView

    // State variables
    private var isPopupShown = false
    private var startX = 0f
    private var startY = 0f
    // Removed currentOption, FilterCategories now manages the current filter index internally
    private var currentCategory = "adjust" // Default category for subcategories display

    // Handler and Runnable for popup auto-hide
    private val handler = Handler(Looper.getMainLooper())
    private val POPUP_HIDE_DELAY = 1000L // 1 second
    private var hidePopupRunnable: Runnable? = null

    // ViewModel and Filter Category Manager
    private lateinit var imageEditorViewModel: ImageEditorViewModel
    private lateinit var filterCategories: FilterCategories // Manages filter groups and selection

    // --- Constants ---
    private val LONG_PRESS_THRESHOLD = 100L // Time needed to make popup appear
    private val MIN_SWIPE_DISTANCE_H = 50f  // Min. horizontal distance for value change swipe
    private val MIN_SWIPE_DISTANCE_V = 140f // Min. vertical distance to change filter swipe
    // Removed PROGRESS_STEPS_SIZE, calculation now uses swipe distance directly


    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                imageEditorViewModel.setOriginalBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Filter category subcategories definition (Actions now call simplified methods)
    // Removed duplicate Grain entry
    private val categorySubcategories = mapOf<String, List<SubcategoryItem>>(
        "file" to listOf(
            SubcategoryItem("Load", R.drawable.baseline_arrow_circle_up_24) { openImagePicker() },
            SubcategoryItem("Save", R.drawable.baseline_arrow_circle_down_24) { saveCurrentImage() },
            SubcategoryItem("Share", R.drawable.baseline_ios_share_24) { shareCurrentImage() }
        ),
        "adjust" to listOf(
            SubcategoryItem("Light", R.drawable.baseline_colorize_24) { setFilterGroupAndUpdateUI("light") },
            SubcategoryItem("Color", R.drawable.baseline_color_lens_24) { setFilterGroupAndUpdateUI("color") },
            SubcategoryItem("Effects", R.drawable.baseline_blur_on_24) { setFilterGroupAndUpdateUI("effects") },
            SubcategoryItem("Detail", R.drawable.baseline_details_24) { setFilterGroupAndUpdateUI("detail") },
            SubcategoryItem("Reset", R.drawable.baseline_auto_fix_off_24) {
                imageEditorViewModel.resetFilters()
                // After resetting, ensure the UI reflects the default state for the current filter
                updateFilterControls()
                Toast.makeText(this, "Filters Reset", Toast.LENGTH_SHORT).show()
            }
        ),
        "crop" to listOf(
            SubcategoryItem("Crop", R.drawable.baseline_crop_24) { showCropOptions() }, // Stub
            SubcategoryItem("Rotate", R.drawable.baseline_crop_rotate_24) { rotateImage() }, // Stub
            SubcategoryItem("Flip", R.drawable.baseline_flip_24) { showFlipOptions() } // Calls VM flip
        ),
        "effects" to listOf(
            // Keep Grain and Blur as separate actions for now (Option 1)
            SubcategoryItem("Grain", R.drawable.baseline_grain_24) { applyGrain() },
            SubcategoryItem("Blur", R.drawable.baseline_blur_circular_24) { showBlurOptions() }
            // If Grain/Blur were integrated as filters (Option 2), they would be selected via "Effects" in "adjust"
        )
        // Presets category still commented out - needs implementation
    )

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hue_modification) // Ensure this layout name is correct

        // Initialize ViewModel first
        imageEditorViewModel = ViewModelProvider(this).get(ImageEditorViewModel::class.java)

        // Initialize FilterCategories, needs ViewModel
        filterCategories = FilterCategories(imageEditorViewModel)

        // Initialize UI components
        initializeViews()

        // Setup listeners
        setupListeners()

        // Observe ViewModel changes
        observeViewModel()

        // Initially hide original image
        originalImageView.visibility = View.GONE

        // Set up bottom navigation and initial subcategories
        setupBottomNavigation()
        updateSubcategoriesUI(filterCategories.currentFilter?.group ?: "adjust") // Use initial group from FilterCategories

        // Initialize filter controls with the default filter
        updateFilterControls()

    }

    private fun initializeViews() {
        toggleButton = findViewById(R.id.toggleButton)
        originalImageView = findViewById(R.id.originalImageView)
        modifiedImageView = findViewById(R.id.modifiedImageView)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        subcategoriesContainer = findViewById(R.id.filter_subcategories_container)

        filterSeekBar = findViewById(R.id.filterSeekBar)
        filterNameLabel = findViewById(R.id.filterNameLabel)
        filterValueLabel = findViewById(R.id.filterValueLabel)
        filterControlsContainer = findViewById(R.id.filterControlsContainer)
        prevFilterButton = findViewById(R.id.prevFilterButton)
        nextFilterButton = findViewById(R.id.nextFilterButton)

        optionsPopup = findViewById(R.id.optionsPopup)
        optionTitle = findViewById(R.id.optionTitle)
        optionValue = findViewById(R.id.optionValue)
        optionsPopup.visibility = View.GONE // Initially hide popup
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            val category = when (item.itemId) {
                R.id.nav_file -> "file"
                R.id.nav_adjust -> "adjust" // This category controls sub-groups for filters
                R.id.nav_crop -> "crop"
                R.id.nav_effects -> "effects" // This category has direct actions (Grain, Blur)
                R.id.nav_presets -> "presets" // Currently unused
                else -> null
            }
            category?.let {
                updateSubcategoriesUI(it)
                // If switching to Adjust, ensure filter controls reflect the current filter group
                if (it == "adjust") {
                    // No need to change filter group here, just ensure controls are visible
                    filterControlsContainer.visibility = View.VISIBLE
                    updateFilterControls() // Refresh controls based on FilterCategories state
                } else {
                    filterControlsContainer.visibility = View.GONE
                }
            }
            true
        }
        // Set default selection
        bottomNavigation.selectedItemId = R.id.nav_adjust
    }

    // Updates the horizontal scroll view with subcategory items
    private fun updateSubcategoriesUI(categoryKey: String) {
        currentCategory = categoryKey // Store the current top-level category
        subcategoriesContainer.removeAllViews()

        categorySubcategories[categoryKey]?.forEach { subcategory ->
            val subcategoryView = LayoutInflater.from(this)
                .inflate(R.layout.layout_filter_subcategory, subcategoriesContainer, false)

            val icon = subcategoryView.findViewById<ImageView>(R.id.subcategory_icon)
            val text = subcategoryView.findViewById<TextView>(R.id.subcategory_text)

            icon.setImageResource(subcategory.iconResId)
            text.text = subcategory.name

            subcategoryView.setOnClickListener { subcategory.action.invoke() }
            subcategoriesContainer.addView(subcategoryView)
        }

        // Show/Hide filter controls container based on the *main* category selected
        filterControlsContainer.visibility = if (categoryKey == "adjust") View.VISIBLE else View.GONE
    }

    // Sets the filter group in FilterCategories and updates the UI accordingly
    private fun setFilterGroupAndUpdateUI(groupName: String) {
        filterCategories.setCategory(groupName) // Update the active filter group
        updateFilterControls() // Update SeekBar/Labels for the new current filter
        // Optionally, provide feedback
        Toast.makeText(this, "Selected $groupName Filters", Toast.LENGTH_SHORT).show()
    }


    private fun observeViewModel() {
        imageEditorViewModel.originalBitmap.observe(this) { bitmap ->
            originalImageView.setImageBitmap(bitmap)
            // When a new image is loaded, reset might happen in ViewModel.
            // Ensure UI reflects the state of the current filter from FilterCategories.
            if (bitmap != null) updateFilterControls()
        }

        imageEditorViewModel.modifiedBitmap.observe(this) { bitmap ->
            modifiedImageView.setImageBitmap(bitmap)
        }

        // Change this to observe filterValuesById instead of filterValues
        imageEditorViewModel.filterValuesById.observe(this) { filterValueMap ->
            filterCategories.currentFilter?.let { currentFilterInfo ->
                filterValueMap[currentFilterInfo.id]?.let { newValue ->
                    // Update the UI elements for the current filter if its value changed
                    updateFilterValueDisplays(currentFilterInfo, newValue)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showOriginalImageOnly()
            } else {
                showModifiedImageOnly()
            }
            if (isPopupShown) hideOptionsPopup() // Hide popup when toggling
        }

        // Previous/Next buttons navigate within the current filter group
        prevFilterButton.setOnClickListener {
            filterCategories.previousFilter()
            updateFilterControls() // Update UI for the new current filter
        }

        nextFilterButton.setOnClickListener {
            filterCategories.nextFilter()
            updateFilterControls() // Update UI for the new current filter
        }

        // SeekBar listener updates the ViewModel for the *current* filter
        filterSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    filterCategories.currentFilter?.let { filterInfo ->
                        // Convert progress back to filter value
                        val newValue = imageEditorViewModel.progressToFilterValue(filterInfo, progress)
                        // Update ViewModel (this will trigger LiveData update)
                        imageEditorViewModel.updateFilterByInfo(filterInfo, newValue)
                        // Update the value label immediately for responsiveness
                        filterValueLabel.text = imageEditorViewModel.getFilterValueDisplay(filterInfo, newValue)
                        // Also update popup value if shown
                        if(isPopupShown && filterCategories.currentFilter == filterInfo) {
                            optionValue.text = imageEditorViewModel.getFilterValueDisplay(filterInfo, newValue)
                        }
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Touch listener on the modified image for popup controls
        modifiedImageView.setOnTouchListener { view, event ->
            // Only allow touch controls if the "adjust" category is selected
            if (currentCategory != "adjust") {
                // If not in adjust mode, maybe allow long press to show original?
                // Or just consume the touch event.
                return@setOnTouchListener true // Consume event, do nothing
            }


            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    hidePopupRunnable?.let { handler.removeCallbacks(it) } // Cancel pending hide

                    // Schedule long press to show popup
                    view.postDelayed({
                        // Check if finger is still down (or hasn't moved much)
//                        if (!isPopupShown && view.isPressed) { // Check isPressed state
                            if (!isPopupShown) { // Check isPressed state
                            filterCategories.currentFilter?.let { // Only show if there's a current filter
                                showOptionsPopup(it)
                            }
                        }
                    }, LONG_PRESS_THRESHOLD)
                    true // Consume event
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isPopupShown) {
                        val deltaX = event.x - startX
                        val deltaY = event.y - startY
                        print("MOVING! $deltaX $deltaY")

                        // Vertical swipe: Change filter
                        if (abs(deltaY) > MIN_SWIPE_DISTANCE_V) {
                            if (deltaY > 0) {
                                filterCategories.nextFilter()
                                updateFilterControls() // Update UI for the new current filter
                            } else {
                                filterCategories.previousFilter()
                                updateFilterControls() // Update UI for the new current filter
                            }

                            filterCategories.currentFilter?.let { updateOptionDisplay(it) } // Update popup content
                            startY = event.y // Reset Y threshold
                            startX = event.x // Reset X threshold to prevent value change on filter switch
                            scheduleHidePopup() // Reschedule hide after interaction
                        }
                        // Horizontal swipe: Adjust value
                        else if (abs(deltaX) > MIN_SWIPE_DISTANCE_H) {
                            filterCategories.currentFilter?.let { filterInfo ->
                                val currentValue = imageEditorViewModel.getFilterValueByInfo(filterInfo)
                                val range = imageEditorViewModel.getFilterRangeByInfo(filterInfo)

                                // Calculate change based on swipe distance relative to image width (more intuitive)
                                val changePercentage = deltaX / view.width // Fraction of width swiped
                                val valueChange = changePercentage * (range.second - range.first)
                                var newValue = currentValue + valueChange

                                // Coerce value within limits
                                newValue = newValue.coerceIn(range.first, range.second)

                                // Update ViewModel
                                imageEditorViewModel.updateFilterByInfo(filterInfo, newValue)
                                // Update popup value immediately
                                updateOptionDisplay(filterInfo) // Update the popup value display

                                // Update main seekbar if visible
                                if (filterControlsContainer.visibility == View.VISIBLE) {
                                    updateFilterValueDisplays(filterInfo, newValue)
                                }
                            }
                            startX = event.x // Reset X threshold
                            startY = event.y // Reset Y threshold
                            scheduleHidePopup() // Reschedule hide after interaction
                        }
                        true // Consume event
                    } else {
                        // If popup is not shown, check if movement exceeds threshold to cancel long press
                        val dist = Math.sqrt(((event.x - startX) * (event.x - startX) + (event.y - startY) * (event.y - startY)).toDouble())
                        if (dist > MIN_SWIPE_DISTANCE_H) { // Use horizontal threshold as general movement tolerance
                            view.removeCallbacks(null) // Cancel pending long press runnable
                        }
                        false // Don't consume if popup not shown and not cancelling long press yet
                    }
                }

                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(null) // Remove any pending runnables (long press)
                    if (isPopupShown) {
                        scheduleHidePopup() // Schedule hide if popup was visible
                    }
                    isPopupShown = false // Reset flag potentially (hide might do this)
                    true // Consume event
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(null) // Remove any pending runnables
                    if (isPopupShown) {
                        hideOptionsPopup() // Hide immediately on cancel
                    }
                    isPopupShown = false
                    true
                }

                else -> false
            }
        }
    }

    // --- UI Update Methods ---

    /**
     * Updates the main filter controls (SeekBar, Name, Value) based on the
     * currently selected filter in FilterCategories.
     */
    private fun updateFilterControls() {
        filterCategories.currentFilter?.let { filterInfo ->
            filterNameLabel.text = filterInfo.displayName
            val currentValue = imageEditorViewModel.getFilterValueByInfo(filterInfo)
            updateFilterValueDisplays(filterInfo, currentValue) // Update seekbar and value label
        } ?: run {
            // Handle case where there's no current filter (e.g., empty group)
            filterNameLabel.text = "N/A"
            filterValueLabel.text = ""
            filterSeekBar.progress = 0
            // Maybe disable seekbar/buttons
            filterSeekBar.isEnabled = false
            prevFilterButton.isEnabled = false
            nextFilterButton.isEnabled = false
        }
        // Ensure controls are enabled if a filter exists
        val hasFilters = filterCategories.currentFilter != null
        filterSeekBar.isEnabled = hasFilters
        prevFilterButton.isEnabled = hasFilters
        nextFilterButton.isEnabled = hasFilters
    }

    /**
     * Updates the visual representation (SeekBar progress, Value Label) of a filter's value.
     * Can be called by observers or direct interactions.
     */
    private fun updateFilterValueDisplays(filterInfo: ImageEditorViewModel.FilterInfo, value: Float) {
        // Check if the filter being updated is the one currently shown in the controls
        if (filterCategories.currentFilter?.id == filterInfo.id &&
            filterCategories.currentFilter?.category == filterInfo.category) {

            val progress = imageEditorViewModel.filterValueToProgress(filterInfo, value)
            val valueText = imageEditorViewModel.getFilterValueDisplay(filterInfo, value)

            // Update SeekBar only if progress changed to avoid loops
            if (filterSeekBar.progress != progress) {
                filterSeekBar.progress = progress
            }
            filterValueLabel.text = valueText

            // Update popup if it's shown and displaying the same filter
            if (isPopupShown && optionTitle.text == filterInfo.displayName) {
                optionValue.text = valueText
            }
        }
    }


    // --- Popup Methods ---

    private fun scheduleHidePopup() {
        hidePopupRunnable?.let { handler.removeCallbacks(it) }
        hidePopupRunnable = Runnable { hideOptionsPopup() }
        handler.postDelayed(hidePopupRunnable!!, POPUP_HIDE_DELAY)
    }

    private fun showOptionsPopup(filterInfo: ImageEditorViewModel.FilterInfo) {
        if (isPopupShown) return // Avoid showing multiple times
        isPopupShown = true
        hidePopupRunnable?.let { handler.removeCallbacks(it) } // Cancel any pending hide

        updateOptionDisplay(filterInfo) // Set initial content

        optionsPopup.visibility = View.VISIBLE
        optionsPopup.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
    }

    private fun hideOptionsPopup() {
        if (!isPopupShown) return
        isPopupShown = false
        hidePopupRunnable?.let { handler.removeCallbacks(it) } // Ensure runnable is removed

        val animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) { optionsPopup.visibility = View.GONE }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        optionsPopup.startAnimation(animation)
    }

    // Updates the text content of the popup based on the provided FilterInfo
    private fun updateOptionDisplay(filterInfo: ImageEditorViewModel.FilterInfo) {
        optionTitle.text = filterInfo.displayName
        val currentValue = imageEditorViewModel.getFilterValueByInfo(filterInfo)
        optionValue.text = imageEditorViewModel.getFilterValueDisplay(filterInfo, currentValue)
    }

    // --- Image Loading and Actions ---

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }


    // This method is deprecated, consider using Activity Result API
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data?.data != null) {
            try {
                val imageUri: Uri = data.data!!
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                // Check bitmap size/validity if needed
                imageEditorViewModel.setOriginalBitmap(bitmap) // ViewModel now handles reset and initial filter apply
                // UI update for controls will be triggered by ViewModel observers
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveCurrentImage() {
        imageEditorViewModel.getModifiedBitmap()?.let { bitmap ->
            saveImage(bitmap) // Use helper function
        } ?: Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrentImage() {
        imageEditorViewModel.getModifiedBitmap()?.let { bitmap ->
            shareImage(bitmap) // Use helper function
        } ?: Toast.makeText(this, "No image to share", Toast.LENGTH_SHORT).show()
    }

    // Refactored flip to show dialog first
    private fun showFlipOptions() {
        imageEditorViewModel.originalBitmap.value?.let { // Check if there's an image to flip
            val options = arrayOf("Horizontal Flip", "Vertical Flip")
            AlertDialog.Builder(this)
                .setTitle("Choose Flip Direction")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> imageEditorViewModel.flipImageHorizontally()
                        1 -> imageEditorViewModel.flipImageVertically()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } ?: Toast.makeText(this, "No image to flip", Toast.LENGTH_SHORT).show()
    }


    // Keep applyGrain and applyBlur separate for now (Option 1)
    private fun applyGrain() {
        // Maybe add intensity selection later
        imageEditorViewModel.applyGrainEffect(0.3f) // Using fixed intensity
        Toast.makeText(this, "Grain effect applied", Toast.LENGTH_SHORT).show()
    }

    // Show dialog for blur intensity selection
    private fun showBlurOptions() {
        imageEditorViewModel.originalBitmap.value?.let { // Check if there's an image
            val options = arrayOf("Light Blur", "Medium Blur", "Strong Blur")
            AlertDialog.Builder(this)
                .setTitle("Choose Blur Intensity")
                .setItems(options) { _, which ->
                    val blurIntensity = when (which) {
                        0 -> 1.0f
                        1 -> 4.0f // Increased medium blur slightly
                        2 -> 8.0f // Increased strong blur slightly
                        else -> 1.0f
                    }
                    imageEditorViewModel.applyBlurEffect(blurIntensity)
                    Toast.makeText(this, "Blur effect applied", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } ?: Toast.makeText(this, "No image to apply blur", Toast.LENGTH_SHORT).show()
    }


    // --- File Helper Methods (Save/Share) ---

    private fun saveImage(bitmap: Bitmap) {
        // Consider saving to MediaStore for better gallery integration
        val file = createTempImageFile("saved") ?: return // Use helper to create file
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            // Notify user of save location (cache dir isn't user-visible easily)
            Toast.makeText(this, "Image saved to app cache: ${file.name}", Toast.LENGTH_LONG).show()
            // To save to gallery, you'd use MediaStore API here
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage(bitmap: Bitmap) {
        val file = createTempImageFile("share") ?: return // Use helper to create file
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            val tempFileUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, tempFileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to create a temporary file in cache directory
    private fun createTempImageFile(prefix: String): File? {
        return try {
            File.createTempFile("${prefix}_${System.currentTimeMillis()}", ".jpg", this.externalCacheDir)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to create temporary file", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // --- View Visibility ---

    private fun showOriginalImageOnly() {
        originalImageView.visibility = View.VISIBLE
        modifiedImageView.visibility = View.GONE
    }

    private fun showModifiedImageOnly() {
        originalImageView.visibility = View.GONE
        modifiedImageView.visibility = View.VISIBLE
    }

    // --- Activity Lifecycle & Back Press ---

    override fun onBackPressed() {
        if (isPopupShown) {
            hideOptionsPopup()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPopupShown) { // Ensure popup is dismissed if activity is paused
            hideOptionsPopup()
        }
        // Cancel any pending handler tasks to avoid leaks or issues
        hidePopupRunnable?.let { handler.removeCallbacks(it) }
    }

    // Stubs for unimplemented features
    private fun showCropOptions() {
        Toast.makeText(this, "Crop feature not implemented", Toast.LENGTH_SHORT).show()
    }

    private fun rotateImage() {
        // Needs implementation, similar to flip but using rotation matrix
        // e.g., imageEditorViewModel.rotateImage(90f)
        Toast.makeText(this, "Rotate feature not implemented", Toast.LENGTH_SHORT).show()
    }

    // Removed applySharpen as it's now handled by selecting "Detail" -> "Sharpen"
    // Removed formatValueForDisplay - ViewModel now provides formatted string
    // Removed calculateProgress - ViewModel handles progress calculation
}
