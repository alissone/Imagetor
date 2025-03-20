package com.example.imagetor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.loading_screen)

        Handler().postDelayed({
            val intent = Intent(this, ImageViewActivity::class.java)
            startActivity(intent)
            finish()
        }, 1000) // 1 second delay
    }
}
