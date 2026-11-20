package com.mathcraft.timestampcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 사진 시각을 어디서 가져왔는지 */
enum class PhotoTimeSource { EXIF, MEDIA_STORE, NONE }

/**
 * 앱으로 직접 찍지 않은 갤러리 이미지에, 그 이미지의 촬영 시각(EXIF)을 각인한다.
 * 시각 우선순위: EXIF DateTimeOriginal → DateTimeDigitized → DateTime → MediaStore 촬영/수정일.
 */
object GalleryStamper {

    /** 너무 큰 이미지는 OOM 방지를 위해 긴 변 기준으로 줄여서 디코딩한다. */
    private const val MAX_EDGE = 4096

    data class Result(val uri: Uri?, val timeSource: PhotoTimeSource)

    fun stampAndSave(context: Context, source: Uri, config: StampConfig): Result {
        val (takenAt, timeSource) = readPhotoTime(context, source)
        val bitmap = decodeUpright(context, source) ?: return Result(null, timeSource)
        // 시각 정보를 못 찾으면 날짜 각인은 건너뛴다(현재 시각을 넣으면 사진과 맞지 않음).
        val lineConfig = if (takenAt == null) config.copy(showDateTime = false) else config
        // 위치는 '지금' 위치가 사진 위치와 다르므로 각인하지 않는다.
        val lines = ImageStamper.buildLines(lineConfig, takenAt ?: Date(), null, null)
        val stamped = ImageStamper.stamp(context, bitmap, config, lines)
        val saved = saveBitmapToGallery(context, stamped, takenAt)
        if (stamped !== bitmap) stamped.recycle()
        bitmap.recycle()
        return Result(saved, timeSource)
    }

    fun readPhotoTime(context: Context, uri: Uri): Pair<Date?, PhotoTimeSource> {
        val exifDate = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                parseExifDateTime(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ) ?: parseExifDateTime(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),
                    exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED)
                ) ?: parseExifDateTime(
                    exif.getAttribute(ExifInterface.TAG_DATETIME),
                    exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
                )
            }
        } catch (e: Exception) {
            null
        }
        if (exifDate != null) return exifDate to PhotoTimeSource.EXIF

        val storeDate = queryMediaStoreTime(context, uri)
        if (storeDate != null) return storeDate to PhotoTimeSource.MEDIA_STORE
        return null to PhotoTimeSource.NONE
    }

    /**
     * EXIF 날짜 문자열("yyyy:MM:dd HH:mm:ss")을 Date 로 변환한다.
     * offset("+09:00")이 있으면 그 시간대로, 없으면 기기 시간대로 해석한다.
     */
    fun parseExifDateTime(
        value: String?,
        offset: String?,
        defaultZone: TimeZone = TimeZone.getDefault()
    ): Date? {
        val text = value?.trim()?.trimEnd('\u0000') ?: return null
        // 값이 비었거나 "0000:00:00 00:00:00" 처럼 미기록인 경우
        if (text.isEmpty() || text.startsWith("0000")) return null
        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
            isLenient = false
            timeZone = offset?.trim()
                ?.takeIf { OFFSET_REGEX.matches(it) }
                ?.let { TimeZone.getTimeZone("GMT$it") }
                ?: defaultZone
        }
        return try {
            fmt.parse(text.take(19))
        } catch (e: ParseException) {
            null
        }
    }

    private val OFFSET_REGEX = Regex("[+-]\\d{2}:\\d{2}")

    private fun queryMediaStoreTime(context: Context, uri: Uri): Date? = try {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED),
            null, null, null
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val takenIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val takenMs = if (takenIdx >= 0 && !c.isNull(takenIdx)) c.getLong(takenIdx) else 0L
            val modSec = if (modIdx >= 0 && !c.isNull(modIdx)) c.getLong(modIdx) else 0L
            when {
                takenMs > 0 -> Date(takenMs)
                modSec > 0 -> Date(modSec * 1000)
                else -> null
            }
        }
    } catch (e: Exception) {
        null
    }

    /** EXIF 회전 정보를 반영해 똑바로 선 비트맵으로 디코딩한다. */
    private fun decodeUpright(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return decoded
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        return upright
    }
}
