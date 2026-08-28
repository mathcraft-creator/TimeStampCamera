package com.mathcraft.timestampcamera

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.camera.view.PreviewView

object PreviewFilterApplier {
    fun prepare(previewView: PreviewView) {
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }

    fun update(
        previewView: PreviewView,
        preset: PhotoFilterPreset,
        intensity: Int
    ): Boolean = try {
        if (preset == PhotoFilterPreset.ORIGINAL || intensity <= 0) {
            clear(previewView)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(PhotoFilter.matrix(preset, intensity))
            }
            previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            previewView.setLayerPaint(paint)
        }
        true
    } catch (_: RuntimeException) {
        clear(previewView)
        false
    }

    fun clear(previewView: PreviewView) {
        previewView.setLayerPaint(null)
        previewView.setLayerType(View.LAYER_TYPE_NONE, null)
    }
}
