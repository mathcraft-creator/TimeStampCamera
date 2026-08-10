package com.mathcraft.timestampcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 사진 비트맵 위에 설정된 각인 문자열을 그려 넣는다. */
object ImageStamper {

    private fun getButterflyDrawable(context: android.content.Context, resId: Int): Drawable? {
        return ContextCompat.getDrawable(context, resId)
    }

    /** 설정에 따라 각인할 줄 목록을 만든다. */
    fun buildLines(
        config: StampConfig,
        now: Date = Date(),
        location: Location? = null,
        address: String? = null
    ): List<String> {
        val lines = mutableListOf<String>()
        if (config.showDateTime) {
            val df = SimpleDateFormat(config.template.format, Locale.KOREA)
            // 줄바꿈(\n)이 포함된 템플릿 대응
            lines.addAll(df.format(now).split("\n"))
        }
        if (config.showGps && location != null) {
            lines.add(String.format(Locale.US, "위도 %.6f, 경도 %.6f", location.latitude, location.longitude))
        }
        if (config.showAddress && !address.isNullOrBlank()) {
            lines.add(address)
        }
        if (config.customText.isNotBlank()) {
            // 커스텀 텍스트도 혹시 모를 줄바꿈 대응
            lines.addAll(config.customText.trim().split("\n"))
        }
        return lines
    }

    /**
     * 원본 비트맵을 복사해 각인한 새 비트맵을 반환한다.
     * lines 가 비어 있으면 원본을 그대로 돌려준다.
     */
    fun stamp(context: android.content.Context, src: Bitmap, config: StampConfig, lines: List<String>): Bitmap {
        if (lines.isEmpty() && !config.showLogo && config.border == StampBorder.NONE) return src

        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val width = bmp.width.toFloat()
        val height = bmp.height.toFloat()

        // 테두리가 있으면 로고/텍스트가 테두리와 겹치지 않도록 안전 여백을 추가로 확보한다.
        val borderInset = width * BorderMetrics.contentInsetFraction(config.border, config.borderThickness)

        // --- 1. 로고 각인 (Butterfly + Time Stamp) ---
        if (config.showLogo && config.logoPosition != LogoPosition.NONE) {
            val logoScale = width / 1000f
            val logoSize = config.logoSize.toFloat() * logoScale
            val logoPadding = width * 0.02f + borderInset
            val iconSize = (logoSize * 1.15f).toInt()

            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = config.logoFont.typeface()
                textSize = logoSize * 0.8f
            }

            val logoStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 0, 0, 0)
                typeface = config.logoFont.typeface()
                textSize = logoSize * 0.8f
                style = Paint.Style.STROKE
                strokeWidth = logoSize * 0.1f
            }

            val butterfly = getButterflyDrawable(context, R.drawable.ic_butterfly_royal)?.mutate()
            val tint = config.logoColor.tint
            butterfly?.colorFilter = if (tint != null) {
                android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                null
            }
            val logoText = "Time Stamp"
            val textWidth = logoPaint.measureText(logoText)
            val totalLogoWidth = iconSize * 2 + (logoSize * 0.5f) + textWidth

            val lx = when (config.logoPosition) {
                LogoPosition.TOP_LEFT, LogoPosition.BOTTOM_LEFT -> logoPadding
                LogoPosition.TOP_RIGHT, LogoPosition.BOTTOM_RIGHT -> width - logoPadding - totalLogoWidth
                else -> (width - totalLogoWidth) / 2f
            }
            val ly = when (config.logoPosition) {
                LogoPosition.TOP_LEFT, LogoPosition.TOP_RIGHT -> logoPadding
                LogoPosition.BOTTOM_LEFT, LogoPosition.BOTTOM_RIGHT -> height - logoPadding - logoSize
                else -> logoPadding
            }

            butterfly?.let {
                // 아이콘을 위로 살짝 올려서 텍스트의 베이스라인(Underline)과 시각적으로 맞춤
                val iconYOffset = logoSize * 0.12f
                val leftBox = Rect(
                    lx.toInt(),
                    (ly - iconYOffset).toInt(),
                    (lx + iconSize).toInt(),
                    (ly + iconSize - iconYOffset).toInt()
                )
                it.setBounds(leftBox)
                it.draw(canvas)
            }

            val textX = lx + iconSize + (logoSize * 0.22f)
            val textY = ly + logoSize * 0.74f
            canvas.drawText(logoText, textX, textY, logoStroke)
            canvas.drawText(logoText, textX, textY, logoPaint)

            butterfly?.let {
                val iconYOffset = logoSize * 0.12f
                val rx = lx + iconSize + (logoSize * 0.44f) + textWidth
                val rightBox = Rect(
                    rx.toInt(),
                    (ly - iconYOffset).toInt(),
                    (rx + iconSize).toInt(),
                    (ly + iconSize - iconYOffset).toInt()
                )
                canvas.save()
                canvas.scale(-1f, 1f, rx + iconSize / 2f.toFloat(), ly + iconSize / 2f.toFloat())
                it.setBounds(rightBox)
                it.draw(canvas)
                canvas.restore()
            }
        }

        // --- 2. 텍스트 각인 ---
        if (lines.isNotEmpty()) {
            val scale = width / 1000f
            val textSize = config.fontSize.toFloat() * scale
            
            // 1:1 비율일 때 여백을 대폭 줄임 (사용자 피드백 반영)
            val padding = (if (config.aspectRatio == StampAspectRatio.RATIO_1_1) {
                width * 0.005f
            } else {
                width * 0.025f
            }) + borderInset

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
        }

        // --- 3. 테두리 ---
        drawBorder(canvas, width, height, config.border, config.borderColor, config.borderThickness)

        return bmp
    }

    /** 사진 가장자리에 선택된 테두리 템플릿을 그린다. */
    private fun drawBorder(canvas: Canvas, width: Float, height: Float, border: StampBorder, color: Int, thickness: Int) {
        when (border) {
            StampBorder.NONE -> {}
            StampBorder.SIMPLE -> drawSimpleFrame(canvas, width, height, color, thickness)
            StampBorder.DOUBLE_LINE -> drawDoubleLineFrame(canvas, width, height, color, thickness)
            StampBorder.CORNER_MARKS -> drawCornerMarks(canvas, width, height, color, thickness)
        }
    }

    private fun drawSimpleFrame(canvas: Canvas, width: Float, height: Float, color: Int, thickness: Int) {
        val strokeWidth = width * (thickness / 1000f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        val inset = strokeWidth / 2f
        canvas.drawRect(inset, inset, width - inset, height - inset, paint)
    }

    private fun drawDoubleLineFrame(canvas: Canvas, width: Float, height: Float, color: Int, thickness: Int) {
        val outerStroke = width * (thickness / 1000f)
        val gap = outerStroke * (8f / 12f)
        val innerStroke = outerStroke * (4f / 12f)

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = outerStroke
        }
        val outerInset = outerStroke / 2f
        canvas.drawRect(outerInset, outerInset, width - outerInset, height - outerInset, outerPaint)

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = innerStroke
        }
        val innerInset = outerStroke + gap + innerStroke / 2f
        canvas.drawRect(innerInset, innerInset, width - innerInset, height - innerInset, innerPaint)
    }

    private fun drawCornerMarks(canvas: Canvas, width: Float, height: Float, color: Int, thickness: Int) {
        val strokeWidth = width * (thickness / 1000f)
        val armLength = width * 0.07f
        val margin = width * 0.03f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        // 좌상단
        canvas.drawLine(margin, margin + armLength, margin, margin, paint)
        canvas.drawLine(margin, margin, margin + armLength, margin, paint)
        // 우상단
        canvas.drawLine(width - margin - armLength, margin, width - margin, margin, paint)
        canvas.drawLine(width - margin, margin, width - margin, margin + armLength, paint)
        // 좌하단
        canvas.drawLine(margin, height - margin - armLength, margin, height - margin, paint)
        canvas.drawLine(margin, height - margin, margin + armLength, height - margin, paint)
        // 우하단
        canvas.drawLine(width - margin - armLength, height - margin, width - margin, height - margin, paint)
        canvas.drawLine(width - margin, height - margin - armLength, width - margin, height - margin, paint)
    }
}
