package com.example.imagetor

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHueFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

enum class FilterType { BRIGHTNESS, CONTRAST, SATURATION, HUE, SHADOW, WHITE_BALANCE }

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: ToggleButton
    private lateinit var imageContainer: FrameLayout
    private lateinit var originalImageView: ImageView
    private lateinit var modifiedImageView: ImageView
    private lateinit var hueSeekBar: SeekBar
    private lateinit var selectImageButton: Button
    private lateinit var saveImageButton: Button

    private var originalBitmap: Bitmap? = null
    private var modifiedBitmap: Bitmap? = null
    lateinit var gpuImage: GPUImage

    private var filterAmounts = mutableMapOf(
        FilterType.BRIGHTNESS to 0.0f,
        FilterType.CONTRAST to 0.0f,
        FilterType.SATURATION to 1.0f,
        FilterType.HUE to 0.0f,
        FilterType.SHADOW to 0.5f,
        FilterType.WHITE_BALANCE to 0.5f
    )

    // UI Components
    private lateinit var optionsPopup: LinearLayout
    private lateinit var optionTitle: TextView
    private lateinit var optionValue: TextView

    // State variables
    private var isPopupShown = false
    private var startX = 0f
    private var startY = 0f
    private var currentOption = 0
    private var currentValue = 50

    // Options available in the editor
    private val options = listOf("Brightness", "Contrast", "Saturation", "Structure")
    private val optionValues = mutableMapOf(
        "Brightness" to 50,
        "Contrast" to 50,
        "Saturation" to 50,
        "Structure" to 50
    )

    // Minimum time to hold for popup to appear (ms)
    private val LONG_PRESS_THRESHOLD = 300L

    // Minimum distance to consider as a swipe
    private val MIN_SWIPE_DISTANCE = 140f

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    fun createProxyBitmap(imageView: ImageView, sourceBitmap: Bitmap): Bitmap {
        // Get the dimensions of the ImageView
        var targetHeight = imageView.height


        // If the ImageView hasn't been laid out yet, use its layout params
        if (targetHeight <= 0) {
            targetHeight = imageView.layoutParams.height


            // If layout params aren't set either, we need another approach
            if (targetHeight <= 0) {
                // Wait for layout to complete or use default dimensions
                imageView.post {
                    // Call this method again once layout is complete
                }
                return sourceBitmap // Return original for now
            }
        }

        // Calculate the width proportionally to the aspect ratio of the original image
        val targetWidth =
            (sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat() * targetHeight).toInt()

        // Create a scaled bitmap that matches the ImageView dimensions
        return Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hue_modification)

        toggleButton = findViewById(R.id.toggleButton)
        originalImageView = findViewById(R.id.originalImageView)
        modifiedImageView = findViewById(R.id.modifiedImageView)
        hueSeekBar = findViewById(R.id.hueSeekBar)
        selectImageButton = findViewById(R.id.selectImageButton)
        saveImageButton = findViewById(R.id.saveImageButton)

        gpuImage = GPUImage(this)

        val brightnessSeekBar = findViewById<SeekBar>(R.id.brightnessSeekBar)
        val contrastSeekBar = findViewById<SeekBar>(R.id.contrastSeekBar)
        val saturationSeekBar = findViewById<SeekBar>(R.id.saturationSeekBar)


        // Initialize UI components
        optionsPopup = findViewById(R.id.optionsPopup)
        optionTitle = findViewById(R.id.optionTitle)
        optionValue = findViewById(R.id.optionValue)

        // Initially hide the popup
        optionsPopup.visibility = View.GONE

        // Set touch listener for the image view
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

                        // If significant horizontal movement, adjust the current option's value
                        if (abs(deltaX) > MIN_SWIPE_DISTANCE) {
                            val currentOptionName = options[currentOption]
                            val currentVal = optionValues[currentOptionName] ?: 50
                            val change = (deltaX / 10).toInt()
                            optionValues[currentOptionName] = (currentVal + change).coerceIn(0, 100)
                            updateOptionValueDisplay()

                            // Reset start position to allow for continuous adjustment
                            startX = event.x
                        }

                        // If significant vertical movement, switch between options
                        if (abs(deltaY) > MIN_SWIPE_DISTANCE) {
                            if (deltaY > 0) {
                                // Swipe down - next option
                                currentOption = (currentOption + 1) % options.size
                            } else {
                                // Swipe up - previous option
                                currentOption = (currentOption - 1 + options.size) % options.size
                            }
                            updateOptionDisplay()

                            // Reset start position to allow for continuous switching
                            startY = event.y
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // If the popup is not shown, this was a quick tap, handle accordingly
                    if (!isPopupShown) {
                        view.removeCallbacks(null)
                    }
                    true
                }

                else -> false
            }
        }

        originalImageView.visibility = View.GONE

        selectImageButton.setOnClickListener {
            openImagePicker()
        }
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showOriginalImageOnly()
            } else {
                showModifiedImageOnly()
            }
        }



        // TODO: We should really edit a proxy of the image (fit by % of screen resolution) instead of the full image

        fun processBitmap() {
            val filters = mutableListOf<GPUImageFilter>()

            if (filterAmounts[FilterType.BRIGHTNESS] != 0.0f) {
                filters.add(GPUImageBrightnessFilter(filterAmounts[FilterType.BRIGHTNESS]!!))
            }

            if (filterAmounts[FilterType.CONTRAST] != 0.0f) {
                filters.add(GPUImageContrastFilter(filterAmounts[FilterType.CONTRAST]!!))
            }

            if (filterAmounts[FilterType.SATURATION] != 0.1f) {
                filters.add(GPUImageSaturationFilter(filterAmounts[FilterType.SATURATION]!!))
            }

            if (filterAmounts[FilterType.HUE] != 0.0f) {
                filters.add(GPUImageHueFilter(filterAmounts[FilterType.HUE]!!))
            }


            if (filterAmounts[FilterType.SHADOW] != 0.5f) { // Assuming 50 is the neutral value
                val shadowValue = (filterAmounts[FilterType.SHADOW]!! - 50).toFloat() / 100 * 2
                filters.add(GPUImageContrastFilter(1 + shadowValue))
            }

            if (filterAmounts[FilterType.WHITE_BALANCE] != 0.5f) { // Assuming 50 is the neutral value
                val wbValue = (filterAmounts[FilterType.WHITE_BALANCE]!! - 50).toFloat() / 100 * 2
                filters.add(GPUImageWhiteBalanceFilter(5000 + wbValue * 3000, 1.0f))
            }

            applyFilters(filters.toTypedArray())
        }

        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//              processBitmap(SeekBarType.BRIGHTNESS, originalBitmap, progress.toFloat())
                filterAmounts[FilterType.BRIGHTNESS] = (progress.toFloat() / 100) - 1
                processBitmap()
            }


            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        contrastSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                filterAmounts[FilterType.CONTRAST] = ((progress.toFloat() / 100) - 0.6).toFloat()
                processBitmap()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        saturationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                filterAmounts[FilterType.SATURATION] = (progress.toFloat() / 200).toFloat()
                processBitmap()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        hueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                filterAmounts[FilterType.HUE] = progress.toFloat()
                processBitmap()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // TODO Example for saving, it should save it into the app storage itself, and we will be able to share it afterwards
        saveImageButton.setOnClickListener {
            modifiedBitmap?.let { bitmap ->
                saveImage(bitmap)
            } ?: Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
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

                // Load bitmap from URI
                originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                var proxyBitmap = createProxyBitmap(modifiedImageView, originalBitmap!!)

                // Display original image
                originalImageView.setImageBitmap(proxyBitmap)
                gpuImage.setImage(proxyBitmap)

                // Reset hue seekbar
                hueSeekBar.progress = 0
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilters(filters: Array<GPUImageFilter>) {
        val filterGroup = GPUImageFilterGroup()
        filters.forEach { filterGroup.addFilter(it) }
        gpuImage.setFilter(filterGroup)

        if (gpuImage.bitmapWithFilterApplied != null) {
            modifiedImageView.setImageBitmap(gpuImage.bitmapWithFilterApplied)
        } else {
            print("Oopsie bitmap is null!!!")
        }
    }


    fun saveImage(bitmap: Bitmap) {
        try {
            // Create a file in the external storage
            val fileName = "modified_image_${System.currentTimeMillis()}.jpg"
            val file = File(getExternalFilesDir(null), fileName)

            // Save the bitmap to the file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            // Create a content URI for sharing
            val fileUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            // Notify the user and optionally share
            Toast.makeText(this, "Image saved: $fileName", Toast.LENGTH_SHORT).show()

            // Optional: Open share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, fileUri)
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
        val currentOptionName = options[currentOption]
        optionTitle.text = currentOptionName
        updateOptionValueDisplay()
    }

    private fun updateOptionValueDisplay() {
        val currentOptionName = options[currentOption]
        val value = optionValues[currentOptionName] ?: 50
        optionValue.text = "$value%"

        // Here you would also apply the actual image adjustments
        applyImageEffect(currentOptionName, value)
    }

    private fun applyImageEffect(option: String, value: Int) {
        // This is where you would implement the actual image processing
        // For an MVP, we're just logging the changes
        println("Applying $option with value $value")

        // Example implementation could use ColorMatrix, ColorMatrixColorFilter, etc.
        // or a library like GPUImage
    }

    override fun onBackPressed() {
        if (isPopupShown) {
            hideOptionsPopup()
        } else {
            super.onBackPressed()
        }
    }
}

//package com.example.imagetor
//
//import android.content.Intent
//import android.os.Bundle
//import android.os.Handler
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//
//class MainActivity : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.loading_screen)
//
//        Handler().postDelayed({
//            val intent = Intent(this, ImageViewActivity::class.java)
//            startActivity(intent)
//            finish()
//        }, 1000) // 1 second delay
//    }
//}
