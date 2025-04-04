package com.example.imagetor

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max


class ImageViewActivity : AppCompatActivity() {

    private lateinit var toggleButton: ToggleButton
    private lateinit var imageContainer: FrameLayout
    private lateinit var originalImageView: ImageView
    private lateinit var modifiedImageView: ImageView
    private lateinit var hueSeekBar: SeekBar
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var contrastSeekBar: SeekBar
    private lateinit var saturationSeekBar: SeekBar
    private lateinit var selectImageButton: LinearLayout
    private lateinit var saveImageButton: LinearLayout
    private lateinit var resetControlsButton: LinearLayout


    // UI Components for popup
    private lateinit var optionsPopup: LinearLayout
    private lateinit var optionTitle: TextView
    private lateinit var optionValue: TextView

    // State variables for touch handling
    private var isPopupShown = false
    private var startX = 0f
    private var startY = 0f
    private var currentOption = 0

    // ViewModel
    private lateinit var imageEditorViewModel: ImageEditorViewModel

    // Constants
    private val LONG_PRESS_THRESHOLD = 100L
    private val MIN_SWIPE_DISTANCE = 140f

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
    }

    private fun initializeViews() {
        toggleButton = findViewById(R.id.toggleButton)
        originalImageView = findViewById(R.id.originalImageView)
        modifiedImageView = findViewById(R.id.modifiedImageView)
        hueSeekBar = findViewById(R.id.hueSeekBar)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        contrastSeekBar = findViewById(R.id.contrastSeekBar)
        saturationSeekBar = findViewById(R.id.saturationSeekBar)
        selectImageButton = findViewById(R.id.selectImageButton)
        saveImageButton = findViewById(R.id.saveImageButton)
        resetControlsButton = findViewById(R.id.reset_button)

        // Initialize popup UI components
        optionsPopup = findViewById(R.id.optionsPopup)
        optionTitle = findViewById(R.id.optionTitle)
        optionValue = findViewById(R.id.optionValue)

        // Initially hide the popup
        optionsPopup.visibility = View.GONE
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
            // Update seekbars only if they're not being adjusted by user
            updateSeekBarsFromViewModel(filterValues)
        }
    }

    private fun updateSeekBarsFromViewModel(filterValues: Map<FilterType, Float>) {
        // Only update if the seekbar is not being touched by the user
        filterValues[FilterType.BRIGHTNESS]?.let {
            val progress = ((it + 1)).toInt()
            if (brightnessSeekBar.progress != progress) {
                brightnessSeekBar.progress = progress
            }
        }

        filterValues[FilterType.CONTRAST]?.let {
            val progress = ((it + 0.6) * 100).toInt()
            if (contrastSeekBar.progress != progress) {
                contrastSeekBar.progress = progress
            }
        }

        filterValues[FilterType.SATURATION]?.let {
            val progress = (it * 200).toInt()
            if (saturationSeekBar.progress != progress) {
                saturationSeekBar.progress = progress
            }
        }

        filterValues[FilterType.HUE]?.let {
            if (hueSeekBar.progress != it.toInt()) {
                hueSeekBar.progress = it.toInt()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // Image selection button
        selectImageButton.setOnClickListener {
            openImagePicker()
        }

        // Toggle button for original/modified view
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showOriginalImageOnly()
            } else {
                showModifiedImageOnly()
            }
        }

        // Save button
        saveImageButton.setOnClickListener {
            imageEditorViewModel.getModifiedBitmap()?.let { bitmap ->
                saveImage(bitmap)
            } ?: Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
        }

        resetControlsButton.setOnClickListener { imageEditorViewModel.resetFilters() }

        // Brightness SeekBar
        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    imageEditorViewModel.updateFilter(FilterType.BRIGHTNESS, progress.toFloat())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Contrast SeekBar
        contrastSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    imageEditorViewModel.updateFilter(FilterType.CONTRAST, ((progress.toFloat() / 100) - 0.6).toFloat())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Saturation SeekBar
        saturationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    imageEditorViewModel.updateFilter(FilterType.SATURATION, (progress.toFloat() / 200).toFloat())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Hue SeekBar
        hueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    imageEditorViewModel.updateFilter(FilterType.HUE, progress.toFloat())
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

                        // Get the min and max values for the current filter
                        val (minValue, maxValue) = imageEditorViewModel.getFilterRange(currentFilterType) ?: Pair(0f, 100f)

                        // Handle horizontal swipe - adjust value
                        if (abs(deltaX) > MIN_SWIPE_DISTANCE) {
                            val currentVal = imageEditorViewModel.getFilterValue(currentFilterType) ?: 0f

                            // Scale the change based on the filter's range
                            val filterRange = maxValue - minValue
                            val change = (deltaX / 10) * (filterRange / 100f) // Scale factor to make movement appropriate

                            // Apply the change and ensure it stays within bounds
                            val newValue = (currentVal + change).coerceIn(minValue, maxValue)
                            imageEditorViewModel.updateFilter(currentFilterType, newValue)
                            updateOptionValueDisplay()

                            // Reset start position for continuous adjustment
                            startX = event.x
                        }

                        // Handle vertical swipe - change option
                        if (abs(deltaY) > MIN_SWIPE_DISTANCE) {
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
                    // If popup not shown, this was a quick tap
                    if (!isPopupShown) {
                        view.removeCallbacks(null)
                    }
                    true
                }

                else -> false
            }
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
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImage(bitmap: Bitmap) {
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

            // Notify the user and share
            Toast.makeText(this, "Image saved: ${tempFile.name}", Toast.LENGTH_SHORT).show()

            // Open share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, tempFileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Image"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
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

        // Set the initial option to display
        updateOptionDisplay()

        // Animate the popup appearing
        optionsPopup.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        optionsPopup.startAnimation(animation)
    }

    private fun hideOptionsPopup() {
        isPopupShown = false

        // Animate the popup disappearing
        val animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        optionsPopup.startAnimation(animation)
        optionsPopup.visibility = View.GONE
    }

    private fun updateOptionDisplay() {
        val currentFilterType = imageEditorViewModel.getFilterTypes()[currentOption]
        optionTitle.text = imageEditorViewModel.getFilterDisplayName(currentFilterType)
        updateOptionValueDisplay()
    }

    private fun updateOptionValueDisplay() {
        val currentFilterType = imageEditorViewModel.getFilterTypes()[currentOption]
        val value = imageEditorViewModel.getFilterValue(currentFilterType) ?: 0f
        val range = imageEditorViewModel.getFilterRange(currentFilterType)

        // Calculate percentage for display
        val percentage = if (range != null) {
            val (min, max) = range
            ((value - min) / (max - min) * 100).toInt()
        } else {
            value.toInt()
        }

        optionValue.text = "$percentage%"
    }

    override fun onBackPressed() {
        if (isPopupShown) {
            hideOptionsPopup()
        } else {
            super.onBackPressed()
        }
    }
}
