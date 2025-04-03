package com.example.imagetor

import android.graphics.Bitmap
import android.widget.ImageView

class ImageProxy {

    companion object {
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

    }
}
