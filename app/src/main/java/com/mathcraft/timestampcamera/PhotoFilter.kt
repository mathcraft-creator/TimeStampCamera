package com.mathcraft.timestampcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

private fun photoFilterMatrix(vararg values: Float): FloatArray = values

private fun photoFilterIdentity() = photoFilterMatrix(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

enum class PhotoFilterPreset(
    val id: String,
    val label: String,
    internal val fullMatrix: FloatArray
) {
    ORIGINAL("original", "원본", photoFilterIdentity()),
    BRIGHT("bright", "화사함", photoFilterMatrix(
        1.08f, 0f, 0f, 0f, 14f,
        0f, 1.08f, 0f, 0f, 12f,
        0f, 0f, 1.04f, 0f, 9f,
        0f, 0f, 0f, 1f, 0f
    )),
    WARM("warm", "따뜻함", photoFilterMatrix(
        1.10f, 0f, 0f, 0f, 10f,
        0f, 1.03f, 0f, 0f, 4f,
        0f, 0f, 0.90f, 0f, -2f,
        0f, 0f, 0f, 1f, 0f
    )),
    COOL("cool", "차가움", photoFilterMatrix(
        0.94f, 0f, 0f, 0f, -2f,
        0f, 1.02f, 0f, 0f, 2f,
        0f, 0f, 1.10f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f
    )),
    MONOCHROME("monochrome", "흑백", photoFilterMatrix(
        .213f, .715f, .072f, 0f, 0f,
        .213f, .715f, .072f, 0f, 0f,
        .213f, .715f, .072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    VINTAGE("vintage", "빈티지", photoFilterMatrix(
        .393f, .769f, .189f, 0f, -12f,
        .349f, .686f, .168f, 0f, -6f,
        .272f, .534f, .131f, 0f, 3f,
        0f, 0f, 0f, 1f, 0f
    )),
    VIVID("vivid", "선명함", photoFilterMatrix(
        1.18f, -.08f, -.08f, 0f, -4f,
        -.08f, 1.18f, -.08f, 0f, -4f,
        -.08f, -.08f, 1.18f, 0f, -4f,
        0f, 0f, 0f, 1f, 0f
    )),
    FADE("fade", "페이드", photoFilterMatrix(
        .88f, .04f, .04f, 0f, 18f,
        .04f, .88f, .04f, 0f, 18f,
        .04f, .04f, .88f, 0f, 18f,
        0f, 0f, 0f, 1f, 0f
    ));

    companion object {
        fun fromId(id: String?): PhotoFilterPreset =
            entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

object PhotoFilter {
    private val identity = PhotoFilterPreset.ORIGINAL.fullMatrix

    fun matrix(preset: PhotoFilterPreset, intensity: Int): ColorMatrix {
        val fraction = if (preset == PhotoFilterPreset.ORIGINAL) 0f
            else intensity.coerceIn(0, 100) / 100f
        return ColorMatrix(FloatArray(20) { index ->
            identity[index] + (preset.fullMatrix[index] - identity[index]) * fraction
        })
    }

    fun apply(src: Bitmap, preset: PhotoFilterPreset, intensity: Int): Bitmap {
        if (preset == PhotoFilterPreset.ORIGINAL || intensity <= 0) return src
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix(preset, intensity))
        }
        Canvas(result).drawBitmap(src, 0f, 0f, paint)
        return result
    }

    fun applyOrOriginal(src: Bitmap, preset: PhotoFilterPreset, intensity: Int): Bitmap =
        try {
            apply(src, preset, intensity)
        } catch (_: RuntimeException) {
            src
        } catch (_: OutOfMemoryError) {
            src
        }
}
