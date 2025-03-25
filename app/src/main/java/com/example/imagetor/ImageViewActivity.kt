// ImageViewActivity.kt
package com.example.imagetor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.widget.ImageView
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.imagetor.databinding.ImageViewActivityBinding
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView
import java.io.File
import java.io.FileOutputStream


class ImageViewActivity : AppCompatActivity() {

    private lateinit var binding: ImageViewActivityBinding
    private lateinit var imageAdjuster: ImageAdjuster

    private lateinit var gpuImage: GPUImage

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val selectedImageUri: Uri? = data?.data
            binding.imageView.setImageURI(selectedImageUri)
            imageAdjuster.setMainImageURI(selectedImageUri)

            gpuImage.setImage(selectedImageUri) // this loads image on the current thread, should be run in a thread
//            gpuImage.setFilter(GPUImageSepiaFilter())

            imageAdjuster.resetAdjustments()
            imageAdjuster.renderImage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ImageViewActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageAdjuster = ImageAdjuster(binding.imageView)

        gpuImage = GPUImage(this)
//        gpuImage.setGLSurfaceView(findViewById<GLSurfaceView>(R.id.surfaceView))

        binding.pickImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.saveImageButton.setOnClickListener {
            saveAndShareImage(binding.imageView)
        }

        binding.brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                imageAdjuster.setBrightness(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.saturationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                imageAdjuster.setSaturation(progress / 100f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.contrastSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                imageAdjuster.setContrast(progress / 100f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.hueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                imageAdjuster.setHue(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun saveAndShareImage(imageView: ImageView) {
        val bitmap = (imageView.drawable as BitmapDrawable).bitmap
        val adjustedBitmap = imageAdjuster.applyColorFilterToBitmap(bitmap, imageView.colorFilter)

        // Save the adjusted bitmap to a temporary file
        val tempFile = File.createTempFile("adjusted_image", ".png", cacheDir)
        try {
            FileOutputStream(tempFile).use { out ->
                adjustedBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            // Use FileProvider to get a content URI for the file
            val photoURI: Uri = FileProvider.getUriForFile(this, "${Constants.APPLICATION_ID}.fileprovider", tempFile)

            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, photoURI)
                type = "image/png"
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION // Grant temporary read permission to the content URI
            }
            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

