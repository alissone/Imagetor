package com.example.imagetor

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
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
        FilterType.SATURATION to 0.0f,
        FilterType.HUE to 0.0f,
        FilterType.SHADOW to 0.5f,
        FilterType.WHITE_BALANCE to 0.5f
    )


    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    fun createProxyBitmap(imageView: ImageView, sourceBitmap: Bitmap): Bitmap {
        // Get the dimensions of the ImageView
        var targetWidth = imageView.width
        var targetHeight = imageView.height


        // If the ImageView hasn't been laid out yet, use its layout params
        if (targetWidth <= 0 || targetHeight <= 0) {
            targetWidth = imageView.layoutParams.width
            targetHeight = imageView.layoutParams.height


            // If layout params aren't set either, we need another approach
            if (targetWidth <= 0 || targetHeight <= 0) {
                // Wait for layout to complete or use default dimensions
                imageView.post {
                    // Call this method again once layout is complete
                }
                return sourceBitmap // Return original for now
            }
        }


        // Create a scaled bitmap that matches the ImageView dimensions
        return Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true)
    }

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

            if (filterAmounts[FilterType.SATURATION] != 0.0f) {
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
                filterAmounts[FilterType.SATURATION] = progress.toFloat()
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


    private fun saveImage(bitmap: Bitmap) {
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
