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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.imagetor.ImageEditorViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class ImageViewActivity : AppCompatActivity() {

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

    // State variables for touch handling
    private var isPopupShown = false
    private var startX = 0f
    private var startY = 0f
    private var currentOption = 0
    private var currentCategory = "adjust" // Default category

    private val POPUP_HIDE_DELAY = 1000L  // 1 second
    private var hidePopupRunnable: Runnable? = null

    private val handler = Handler(Looper.getMainLooper())

    // ViewModel
    private lateinit var imageEditorViewModel: ImageEditorViewModel

    // Constants
    private val LONG_PRESS_THRESHOLD = 100L // Time needed to make popup appear
    private val MIN_SWIPE_DISTANCE_H = 50f // Min. Distance needed to make a change in one of the filters
    private val MIN_SWIPE_DISTANCE_V = 140f // Min. Distance to swipe to change filter, vertically
    private val PROGRESS_STEPS_SIZE = 1 // Percentage that increases/decreases per pixel when swiping left/right

    // Filter category subcategories
    private val categorySubcategories = mapOf<String,List<SubcategoryItem>>(
        "file" to listOf(
            SubcategoryItem("Load", R.drawable.baseline_arrow_circle_up_24) { openImagePicker() },
            SubcategoryItem("Save", R.drawable.baseline_arrow_circle_down_24) { saveCurrentImage() },
            SubcategoryItem("Share", R.drawable.baseline_ios_share_24) { shareCurrentImage() }
        ),
        "adjust" to listOf(
            SubcategoryItem("Light", R.drawable.baseline_colorize_24) { setFilterGroup("light") },
            SubcategoryItem("Color", R.drawable.baseline_color_lens_24) { setFilterGroup("color") },
            SubcategoryItem("Effects", R.drawable.baseline_blur_on_24) { setFilterGroup("effects") },
            SubcategoryItem("Detail", R.drawable.baseline_details_24) { setFilterGroup("detail") },
            SubcategoryItem("Reset", R.drawable.baseline_auto_fix_off_24) { imageEditorViewModel.resetFilters() }
        ),
        "crop" to listOf(
            SubcategoryItem("Crop", R.drawable.baseline_crop_24) { showCropOptions() },
            SubcategoryItem("Rotate", R.drawable.baseline_crop_rotate_24) { rotateImage() },
            SubcategoryItem("Flip", R.drawable.baseline_flip_24) { flipImage() }
        ),
        "effects" to listOf(
            SubcategoryItem("Grain", R.drawable.baseline_grain_24) { applyGrain() },
            SubcategoryItem("Grain", R.drawable.baseline_grain_24) { applyGrain() },
            SubcategoryItem("Blur", R.drawable.baseline_blur_circular_24) { applyBlur() }
        ),
//        "presets" to listOf(
//            SubcategoryItem("Natural", R.drawable.baseline_nature_24) { applyPreset("natural") },
//            SubcategoryItem("Vivid", R.drawable.baseline_format_color_fill_24) { applyPreset("vivid") },
//            SubcategoryItem("B&W", R.drawable.baseline_monochrome_photos_24) { applyPreset("bw") },
//            SubcategoryItem("Vintage", R.drawable.baseline_history_24) { applyPreset("vintage") }
//        )
    )

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hue_modification)

        // Initialize ViewModel
        imageEditorViewModel = ViewModelProvider(this).get(ImageEditorViewModel::class.java)

        // Initialize UI components
        initializeViews()
        setupListeners()
        observeViewModel()

        // Initially hide original image
        originalImageView.visibility = View.GONE

        // Initialize filter controls with the first filter
        updateFilterControls(0)

        // Set up bottom navigation and initial subcategories
        setupBottomNavigation()
        updateSubcategories("adjust") // Default category
    }

    private fun initializeViews() {
        toggleButton = findViewById(R.id.toggleButton)
        originalImageView = findViewById(R.id.originalImageView)
        modifiedImageView = findViewById(R.id.modifiedImageView)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        subcategoriesContainer = findViewById(R.id.filter_subcategories_container)

        // Initialize single seekbar components
        filterSeekBar = findViewById(R.id.filterSeekBar)
        filterNameLabel = findViewById(R.id.filterNameLabel)
        filterValueLabel = findViewById(R.id.filterValueLabel)
        filterControlsContainer = findViewById(R.id.filterControlsContainer)
        prevFilterButton = findViewById(R.id.prevFilterButton)
        nextFilterButton = findViewById(R.id.nextFilterButton)

        // Initialize popup UI components
        optionsPopup = findViewById(R.id.optionsPopup)
        optionTitle = findViewById(R.id.optionTitle)
        optionValue = findViewById(R.id.optionValue)

        // Initially hide the popup
        optionsPopup.visibility = View.GONE
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_file -> updateSubcategories("file")
                R.id.nav_adjust -> updateSubcategories("adjust")
                R.id.nav_crop -> updateSubcategories("crop")
                R.id.nav_effects -> updateSubcategories("effects")
                R.id.nav_presets -> updateSubcategories("presets")
            }
            true
        }
    }

    private fun updateSubcategories(category: String) {
        currentCategory = category
        subcategoriesContainer.removeAllViews()

        categorySubcategories[category]?.forEach { subcategory ->
            val subcategoryView = LayoutInflater.from(this)
                .inflate(R.layout.layout_filter_subcategory, subcategoriesContainer, false)

            val icon = subcategoryView.findViewById<ImageView>(R.id.subcategory_icon)
            val text = subcategoryView.findViewById<TextView>(R.id.subcategory_text)

            icon.setImageResource(subcategory.iconResId)
            text.text = subcategory.name

            subcategoryView.setOnClickListener {
                subcategory.action.invoke()
            }

            subcategoriesContainer.addView(subcategoryView)
        }

        // Update visibility of filter controls based on category
        filterControlsContainer.visibility = if (category == "adjust") View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        // Observe original image changes
        imageEditorViewModel.originalBitmap.observe(this) { bitmap ->
            bitmap?.let {
                originalImageView.setImageBitmap(it)
            }
        }

        // Observe modified image changes
        imageEditorViewModel.modifiedBitmap.observe(this) { bitmap ->
            bitmap?.let {
                modifiedImageView.setImageBitmap(it)
            }
        }

        // Observe filter values for UI updates
        imageEditorViewModel.filterValues.observe(this) { filterValues ->
            // Update seekbar for the current filter type
            updateSeekBarFromViewModel(filterValues)

            // Also update popup if it's showing
            if (isPopupShown) {
                updateOptionValueDisplay(currentOption)
            }
        }
    }

    private fun updateSeekBarFromViewModel(filterValues: Map<FilterType, Float>) {
        // Get current filter type
        val currentFilterType = imageEditorViewModel.getFilterTypes()[currentOption]
        filterNameLabel.text = imageEditorViewModel.getFilterName(currentOption)

        // Update the seekbar for the current filter
        filterValues[currentFilterType]?.let { value ->
            // Convert model value to progress (0-100)
            val progress = imageEditorViewModel.filterValueToProgress(currentFilterType, value)

            // Update seekbar if needed
            if (filterSeekBar.progress != progress) {
                filterSeekBar.progress = progress
            }

            // Get formatted display value
            filterValueLabel.text = imageEditorViewModel.getFilterValueDisplay(currentFilterType, value)
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

            // Hide popup when toggling between views
            if (isPopupShown) {
                hideOptionsPopup()
            }
        }

        // Set up navigation buttons for filter types
        prevFilterButton.setOnClickListener {
            val filterTypes = imageEditorViewModel.getFilterTypes()
            currentOption = (currentOption - 1 + filterTypes.size) % filterTypes.size
            updateFilterControls(currentOption)
        }

        nextFilterButton.setOnClickListener {
            val filterTypes = imageEditorViewModel.getFilterTypes()
            currentOption = (currentOption + 1) % filterTypes.size
            updateFilterControls(currentOption)
        }

        // Update the SeekBar listener
        filterSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val currentFilterType = imageEditorViewModel.getFilterTypes()[currentOption]
                    // Convert progress to filter value
                    val filterValue = imageEditorViewModel.progressToFilterValue(currentFilterType, progress)

                    // Update the filter
                    imageEditorViewModel.updateFilter(currentFilterType, filterValue)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Touch listener for popup controls
        modifiedImageView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial touch position
                    startX = event.x
                    startY = event.y

                    // Cancel any pending hide operations when user touches the screen
                    hidePopupRunnable?.let { handler.removeCallbacks(it) }

                    // Start a delayed action for long press
                    view.postDelayed({
                        if (!isPopupShown) {
                            showOptionsPopup()
                        }
                    }, LONG_PRESS_THRESHOLD)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isPopupShown) {
                        // Calculate how far the finger has moved
                        val deltaX = event.x - startX
                        val deltaY = event.y - startY

                        val currentFilterType = imageEditorViewModel.getFilterTypes()[currentOption]

                        // Handle horizontal swipe - adjust value
                        if (abs(deltaX) > MIN_SWIPE_DISTANCE_H) {
                            val currentVal = imageEditorViewModel.getFilterValue(currentFilterType)

                            // Get the current progress value (0-100)
                            val currentProgress = imageEditorViewModel.filterValueToProgress(currentFilterType, currentVal)

                            // Calculate new progress based on swipe distance
                            val progressChange = (deltaX / MIN_SWIPE_DISTANCE_H * PROGRESS_STEPS_SIZE).toInt()
                            val newProgress = (currentProgress + progressChange).coerceIn(0, 100)

                            // Convert back to filter value and update
                            val newValue = imageEditorViewModel.progressToFilterValue(currentFilterType, newProgress)
                            imageEditorViewModel.updateFilter(currentFilterType, newValue)

                            // Reset start position for continuous adjustment
                            startX = event.x
                        }

                        // Handle vertical swipe - change option
                        if (abs(deltaY) > MIN_SWIPE_DISTANCE_V) {
                            val filterTypes = imageEditorViewModel.getFilterTypes()
                            if (deltaY > 0) {
                                // Swipe down - next option
                                currentOption = (currentOption + 1) % filterTypes.size
                            } else {
                                // Swipe up - previous option
                                currentOption = (currentOption - 1 + filterTypes.size) % filterTypes.size
                            }
                            updateOptionDisplay()

                            // Reset start position for continuous switching
                            startY = event.y
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isPopupShown) {
                        // If popup not shown, remove pending callbacks
                        view.removeCallbacks(null)
                    } else {
                        // Schedule popup to hide after delay
                        scheduleHidePopup()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun scheduleHidePopup() {
        // Cancel any existing hide operation
        hidePopupRunnable?.let { handler.removeCallbacks(it) }

        // Create new runnable for hiding
        hidePopupRunnable = Runnable {
            hideOptionsPopup()
        }

        // Schedule the hide operation
        handler.postDelayed(hidePopupRunnable!!, POPUP_HIDE_DELAY)
    }

    private fun updateFilterControls(currentOptionIndex: Int) {
        val filterTypes = imageEditorViewModel.getFilterTypes()
        if (currentOptionIndex in filterTypes.indices) {
            val currentFilterType = filterTypes[currentOptionIndex]
            val value = imageEditorViewModel.getFilterValue(currentFilterType)

            // Convert to progress
            val progress = imageEditorViewModel.filterValueToProgress(currentFilterType, value)

            // Update filter bar
            filterSeekBar.progress = progress
            filterValueLabel.text = imageEditorViewModel.getFilterValueDisplay(currentFilterType, value)
            filterNameLabel.text = imageEditorViewModel.getFilterName(currentOptionIndex)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            try {
                // Get the selected image URI
                val imageUri: Uri = data.data ?: return

                // Load bitmap and update ViewModel
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                imageEditorViewModel.setOriginalBitmap(bitmap)

                // Reset filters
                imageEditorViewModel.resetFilters()

                // Update UI
                updateFilterControls(currentOption)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCurrentImage() {
        imageEditorViewModel.getModifiedBitmap()?.let { bitmap ->
            saveImage(bitmap)
        } ?: Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrentImage() {
        imageEditorViewModel.getModifiedBitmap()?.let { bitmap ->
            shareImage(bitmap)
        } ?: Toast.makeText(this, "No image to share", Toast.LENGTH_SHORT).show()
    }

    // For FlipImage() function in ImageViewActivity.kt
    private fun flipImage() {
        imageEditorViewModel.getModifiedBitmap()?.let { bitmap ->
            // Ask user if they want horizontal or vertical flip
            val options = arrayOf("Horizontal Flip", "Vertical Flip")

            androidx.appcompat.app.AlertDialog.Builder(this)
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

    // For applySharpen() function in ImageViewActivity.kt
    private fun applySharpen() {
        // Switch to "detail" filter group
        setFilterGroup("detail")

        // Find the index of SHARPEN filter
        val filterTypes = imageEditorViewModel.getFilterTypes()
        val sharpenIndex = filterTypes.indexOf(FilterType.SHARPEN)

        if (sharpenIndex != -1) {
            // Update the current option to sharpen
            currentOption = sharpenIndex
            updateFilterControls(currentOption)

            // Apply a moderate sharpen effect
            val sharpenFilter = imageEditorViewModel.getFilterValue(FilterType.SHARPEN)
            imageEditorViewModel.updateFilter(FilterType.SHARPEN, 0.5f)

            Toast.makeText(this, "Sharpen filter applied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Sharpen filter not available", Toast.LENGTH_SHORT).show()
        }
    }

    // For applyGrain() function in ImageViewActivity.kt
    private fun applyGrain() {
        imageEditorViewModel.getModifiedBitmap()?.let { bitmap ->
            // Apply a grain effect with medium intensity
            imageEditorViewModel.applyGrainEffect(0.3f)
            Toast.makeText(this, "Grain effect applied", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(this, "No image to apply grain effect", Toast.LENGTH_SHORT).show()
    }

    // For applyBlur() function in ImageViewActivity.kt
    private fun applyBlur() {
        // Ask user for blur intensity
        val options = arrayOf("Light Blur", "Medium Blur", "Strong Blur")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose Blur Intensity")
            .setItems(options) { _, which ->
                val blurIntensity = when (which) {
                    0 -> 1.0f
                    1 -> 3.0f
                    2 -> 7.0f
                    else -> 1.0f
                }

                imageEditorViewModel.applyBlurEffect(blurIntensity)
                Toast.makeText(this, "Blur effect applied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveImage(bitmap: Bitmap) {
        try {
            // Create a temporary file in cache
            val tempFile = File.createTempFile("modified_image", ".jpg", this.externalCacheDir)

            // Save the bitmap to the temporary file
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            // Notify the user
            Toast.makeText(this, "Image saved: ${tempFile.name}", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage(bitmap: Bitmap) {
        try {
            // Create a temporary file in cache
            val tempFile = File.createTempFile("modified_image", ".jpg", this.externalCacheDir)

            // Save the bitmap to the temporary file
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            // Create a content URI for sharing
            val tempFileUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )

            // Open share intent
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


    private fun showOriginalImageOnly() {
        originalImageView.visibility = View.VISIBLE
        modifiedImageView.visibility = View.GONE
    }

    private fun showModifiedImageOnly() {
        originalImageView.visibility = View.GONE
        modifiedImageView.visibility = View.VISIBLE
    }

    private fun showOptionsPopup() {
        isPopupShown = true

        // Cancel any pending hide operations
        hidePopupRunnable?.let { handler.removeCallbacks(it) }

        // Set the initial option to display
        updateOptionDisplay()

        // Animate the popup appearing
        optionsPopup.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        optionsPopup.startAnimation(animation)
    }

    private fun hideOptionsPopup() {
        if (!isPopupShown) return

        isPopupShown = false

        // Animate the popup disappearing
        val animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        optionsPopup.startAnimation(animation)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                optionsPopup.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }

    private fun updateOptionDisplay() {
        val currentFilterType = imageEditorViewModel.getFilterTypes()[currentOption]
        optionTitle.text = imageEditorViewModel.getFilterDisplayName(currentFilterType)
        updateOptionValueDisplay(currentOption)
    }

    private fun updateOptionValueDisplay(currentOptionIndex: Int) {
        val currentFilterType = imageEditorViewModel.getFilterTypes()[currentOptionIndex]
        val value = imageEditorViewModel.getFilterValue(currentFilterType)
        optionValue.text = imageEditorViewModel.getFilterValueDisplay(currentFilterType, value)
    }

    // Function stub for setting filter group
    private fun setFilterGroup(groupName: String) {
        Toast.makeText(this, "Changing to $groupName filters", Toast.LENGTH_SHORT).show()
        // Implementation would go here - update available filters based on the group
    }

    // Function stubs for crop operations
    private fun showCropOptions() {
        Toast.makeText(this, "Crop options would appear here", Toast.LENGTH_SHORT).show()
    }

    private fun rotateImage() {
        Toast.makeText(this, "Image rotation would happen here", Toast.LENGTH_SHORT).show()
    }



    override fun onBackPressed() {
        if (isPopupShown) {
            hideOptionsPopup()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPopupShown) {
            hideOptionsPopup()
        }
    }
}
