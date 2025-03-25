package com.example.imagetor
import android.content.Context
import jp.co.cyberagent.android.gpuimage.GPUImage

object GPUImageSingleton {
    private var gpuImageInstance: GPUImage? = null

    fun getInstance(context: Context): GPUImage {
        if (gpuImageInstance == null) {
            gpuImageInstance = GPUImage(context)
        }
        return gpuImageInstance!!
    }

    fun reset() {
        gpuImageInstance = null
    }
}
