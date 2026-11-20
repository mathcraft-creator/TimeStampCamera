package com.mathcraft.timestampcamera

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * 사진 선택기 Uri 처럼 일부 컬럼만 지원하고, 지원하지 않는 컬럼을 요청하면
 * 쿼리 전체를 예외로 거부하는 제공자. EXIF 를 읽을 파일 스트림도 열 수 없다.
 */
class PickerLikeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val supported = mapOf<String, Any?>(MediaStore.MediaColumns.DATE_TAKEN to TAKEN_MS)
        val cols = projection ?: supported.keys.toTypedArray()
        cols.forEach { require(it in supported) { "Unsupported column: $it" } }
        return MatrixCursor(cols).apply { addRow(cols.map { supported[it] }) }
    }

    override fun getType(uri: Uri): String = "image/png"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ) = 0

    companion object {
        const val AUTHORITY = "test.pickerlike"
        const val TAKEN_MS = 1723271420000L
    }
}

@RunWith(RobolectricTestRunner::class)
class GalleryStamperTimeFallbackTest {

    @Test
    fun usesDateTakenWhenProviderRejectsOtherColumns() {
        Robolectric.setupContentProvider(PickerLikeProvider::class.java, PickerLikeProvider.AUTHORITY)
        val uri = Uri.parse("content://${PickerLikeProvider.AUTHORITY}/media/1")

        val (date, source) = GalleryStamper.readPhotoTime(ApplicationProvider.getApplicationContext(), uri)

        assertEquals(PhotoTimeSource.MEDIA_STORE, source)
        assertEquals(PickerLikeProvider.TAKEN_MS, date?.time)
    }
}
