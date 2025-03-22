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
import com.example.imagetor.databinding.ImageViewActivityBinding
import java.io.File
import java.io.FileOutputStream


class ImageViewActivity : AppCompatActivity() {

    private lateinit var binding: ImageViewActivityBinding
    private lateinit var imageAdjuster: ImageAdjuster

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val selectedImageUri: Uri? = data?.data
            binding.imageView.setImageURI(selectedImageUri)
            imageAdjuster.renderImage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ImageViewActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageAdjuster = ImageAdjuster(binding.imageView)

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

            val builder = VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tempFile))
                type = "image/png"
            }
            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

