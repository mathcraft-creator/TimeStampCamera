package com.mathcraft.timestampcamera

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoFilterTest {
    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    @Test fun presetIdsAreUniqueAndRoundTrip() {
        val ids = PhotoFilterPreset.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        PhotoFilterPreset.entries.forEach { preset ->
            assertEquals(preset, PhotoFilterPreset.fromId(preset.id))
        }
    }

    @Test fun unknownPresetFallsBackToOriginal() {
        assertEquals(PhotoFilterPreset.ORIGINAL, PhotoFilterPreset.fromId("missing"))
        assertEquals(PhotoFilterPreset.ORIGINAL, PhotoFilterPreset.fromId(null))
    }

    @Test fun zeroIntensityIsIdentityForEveryPreset() {
        PhotoFilterPreset.entries.forEach { preset ->
            assertArrayEquals(identity, PhotoFilter.matrix(preset, 0).array, 0.0001f)
        }
    }

    @Test fun originalIsIdentityAtEveryIntensity() {
        listOf(-10, 0, 50, 100, 130).forEach { intensity ->
            assertArrayEquals(
                identity,
                PhotoFilter.matrix(PhotoFilterPreset.ORIGINAL, intensity).array,
                0.0001f
            )
        }
    }

    @Test fun halfIntensityIsMidpointBetweenIdentityAndFullPreset() {
        val full = PhotoFilter.matrix(PhotoFilterPreset.WARM, 100).array
        val half = PhotoFilter.matrix(PhotoFilterPreset.WARM, 50).array
        val expected = FloatArray(20) { index -> (identity[index] + full[index]) / 2f }
        assertArrayEquals(expected, half, 0.0001f)
    }

    @Test fun intensityIsClamped() {
        assertArrayEquals(
            PhotoFilter.matrix(PhotoFilterPreset.COOL, 0).array,
            PhotoFilter.matrix(PhotoFilterPreset.COOL, -1).array,
            0.0001f
        )
        assertArrayEquals(
            PhotoFilter.matrix(PhotoFilterPreset.COOL, 100).array,
            PhotoFilter.matrix(PhotoFilterPreset.COOL, 101).array,
            0.0001f
        )
    }

    @Test fun originalAndZeroIntensityReuseInputBitmap() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        assertSame(source, PhotoFilter.apply(source, PhotoFilterPreset.ORIGINAL, 100))
        assertSame(source, PhotoFilter.apply(source, PhotoFilterPreset.VINTAGE, 0))
    }

    @Test fun activeFilterCreatesResultAndChangesPixel() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(80, 100, 140))
        }
        val result = PhotoFilter.apply(source, PhotoFilterPreset.MONOCHROME, 100)
        assertNotSame(source, result)
        val pixel = result.getPixel(0, 0)
        assertEquals(Color.red(pixel).toFloat(), Color.green(pixel).toFloat(), 1f)
        assertEquals(Color.green(pixel).toFloat(), Color.blue(pixel).toFloat(), 1f)
        result.recycle()
        source.recycle()
    }

    @Test fun runtimeFailureFallsBackToInputBitmap() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.recycle()
        assertSame(
            source,
            PhotoFilter.applyOrOriginal(source, PhotoFilterPreset.WARM, 100)
        )
    }
}
