package com.example.imagetor

import android.app.Activity
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.imagetor.databinding.ImageViewActivityBinding

class ImageViewActivity : AppCompatActivity() {

    private lateinit var binding: ImageViewActivityBinding

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val imageUri: Uri? = data?.data
            binding.imageView.setImageURI(imageUri)
            adjustBrightness(binding.brightnessSeekBar.progress)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ImageViewActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        binding.pickImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                adjustBrightness(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun adjustBrightness(brightnessValue: Int) {
        val brightness = (brightnessValue - 100).toFloat() / 100
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        colorMatrix.setScale(1 + brightness, 1 + brightness, 1 + brightness, 1f)

        val filter = ColorMatrixColorFilter(colorMatrix)
        binding.imageView.colorFilter = filter
    }
}
