package com.mathcraft.timestampcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * 촬영된 사진 전체에 적용하는 가벼운 뷰티 필터.
 *
 * 얼굴 인식(랜드마크) 없이 사진 전체에 균일하게 적용되는 방식이라, 배경도 함께
 * 살짝 부드러워진다는 한계가 있다. 대신 별도 ML 라이브러리 없이도 "잡티가 덜 도드라지고
 * 화사해 보이는" 셀카 보정 효과를 저비용으로 낼 수 있다.
 *
 * - [smooth] 잡티 보정: 원본과 흐림 처리본을 부분 투명도로 합성해 피부 잡티/모공의
 *   대비를 낮춘다. Canvas 알파 합성만 사용하므로 픽셀 단위 루프 없이 빠르다.
 * - [brighten] 화사함: 밝기·채도·따뜻한 색조를 [ColorMatrix] 하나로 합성해 적용한다.
 */
object BeautyFilter {

    /**
     * @param smooth 0~100. 값이 클수록 흐림 합성 비율이 높아져 잡티가 덜 도드라진다.
     * @param brighten 0~100. 값이 클수록 밝기·채도·따뜻한 색조가 강해져 화사해 보인다.
     */
    fun apply(src: Bitmap, smooth: Int, brighten: Int): Bitmap {
        if (smooth <= 0 && brighten <= 0) return src

        val width = src.width
        val height = src.height
        var result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        if (smooth > 0) {
            val fraction = smooth / 100f
            val blurred = cheapBlur(src, blurRadiusFor(fraction))
            canvas.drawBitmap(blurred, 0f, 0f, null)
            blurred.recycle()

            // 스무딩 강도가 높을수록 원본(선명한 잡티) 비중을 낮춘다. 60%까지만 낮춰
            // 사진이 완전히 뭉개지지 않고 이목구비 윤곽은 유지되게 한다.
            val originalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (255 * (1f - fraction * 0.6f)).toInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(src, 0f, 0f, originalPaint)
        } else {
            canvas.drawBitmap(src, 0f, 0f, null)
        }

        if (brighten > 0) {
            val fraction = brighten / 100f
            val brightened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(glowMatrix(fraction))
            }
            Canvas(brightened).drawBitmap(result, 0f, 0f, paint)
            result.recycle()
            result = brightened
        }

        return result
    }

    /** 스무딩 강도(0~1)에 비례해 다운스케일 배율(흐림 반경)을 계산한다. */
    private fun blurRadiusFor(fraction: Float): Int = (3 + fraction * 10).toInt().coerceIn(3, 13)

    /**
     * 원본을 축소했다가 다시 원래 크기로 확대해 저렴하게 흐림 효과를 만든다.
     * (커널 연산 없이 Skia 의 양선형 보간만으로 충분히 부드러운 결과를 얻는다)
     */
    private fun cheapBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val smallWidth = (w / radius).coerceAtLeast(1)
        val smallHeight = (h / radius).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, smallWidth, smallHeight, true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        return blurred
    }

    /** 밝기 상승 + 채도 상승 + 따뜻한 색조(R↑ B↓)를 하나의 행렬로 합성한다. */
    private fun glowMatrix(fraction: Float): ColorMatrix {
        val brightnessOffset = fraction * 26f
        val warm = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightnessOffset * 1.15f, // R: 살짝 더 따뜻하게
                0f, 1f, 0f, 0f, brightnessOffset,          // G
                0f, 0f, 1f, 0f, brightnessOffset * 0.75f,  // B: 덜 올려 따뜻한 톤을 유지
                0f, 0f, 0f, 1f, 0f
            )
        )
        val saturation = ColorMatrix().apply { setSaturation(1f + fraction * 0.18f) }
        saturation.postConcat(warm)
        return saturation
    }
}
