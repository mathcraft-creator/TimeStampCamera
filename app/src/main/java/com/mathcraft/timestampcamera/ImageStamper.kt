package com.mathcraft.timestampcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 사진 비트맵 위에 설정된 각인 문자열을 그려 넣는다. */
object ImageStamper {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

    /** 설정에 따라 각인할 줄 목록을 만든다. */
    fun buildLines(
        config: StampConfig,
        now: Date = Date(),
        location: Location? = null,
        address: String? = null
    ): List<String> {
        val lines = mutableListOf<String>()
        if (config.showDateTime) {
            lines.add(dateTimeFormat.format(now))
        }
        if (config.showGps && location != null) {
            lines.add(String.format(Locale.US, "위도 %.6f, 경도 %.6f", location.latitude, location.longitude))
        }
        if (config.showAddress && !address.isNullOrBlank()) {
            lines.add(address)
        }
        if (config.customText.isNotBlank()) {
            lines.add(config.customText.trim())
        }
        return lines
    }

    /**
     * 원본 비트맵을 복사해 각인한 새 비트맵을 반환한다.
     * lines 가 비어 있으면 원본을 그대로 돌려준다.
     */
    fun stamp(src: Bitmap, config: StampConfig, lines: List<String>): Bitmap {
        if (lines.isEmpty()) return src

        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val width = bmp.width.toFloat()
        val height = bmp.height.toFloat()

        val textSize = width * (config.sizePercent.coerceIn(1, 12) / 100f)
        val padding = width * 0.025f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.color.color
            typeface = config.font.typeface()
            this.textSize = textSize
        }
        // 어떤 배경에서도 잘 보이도록 검은 외곽선을 함께 그린다.
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.color == StampColorOption.BLACK) Color.WHITE else Color.argb(200, 0, 0, 0)
            typeface = config.font.typeface()
            this.textSize = textSize
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.12f
        }

        val fm = fillPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.05f
        val ascent = -fm.ascent
        val blockHeight = lineHeight * lines.size

        val isTop = config.position == StampPosition.TOP_LEFT ||
            config.position == StampPosition.TOP_RIGHT
        val firstBaseline = if (isTop) {
            padding + ascent
        } else {
            height - padding - blockHeight + ascent
        }

        for ((index, line) in lines.withIndex()) {
            val baseline = firstBaseline + index * lineHeight
            val textWidth = fillPaint.measureText(line)
            val x = when (config.position) {
                StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> padding
                StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT -> width - padding - textWidth
                StampPosition.BOTTOM_CENTER -> (width - textWidth) / 2f
            }
            canvas.drawText(line, x, baseline, strokePaint)
            canvas.drawText(line, x, baseline, fillPaint)
        }
        return bmp
    }
}
