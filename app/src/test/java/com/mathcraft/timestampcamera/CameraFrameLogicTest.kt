package com.mathcraft.timestampcamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class CameraFrameLogicTest {
    @Test
    fun portraitRatiosKeepConfiguredValues() {
        assertEquals(3f / 4f, frameAspectRatio(StampAspectRatio.RATIO_3_4, false), 0.0001f)
        assertEquals(9f / 16f, frameAspectRatio(StampAspectRatio.RATIO_9_16, false), 0.0001f)
    }

    @Test
    fun landscapeRatiosInvertConfiguredValues() {
        assertEquals(4f / 3f, frameAspectRatio(StampAspectRatio.RATIO_3_4, true), 0.0001f)
        assertEquals(16f / 9f, frameAspectRatio(StampAspectRatio.RATIO_9_16, true), 0.0001f)
    }

    @Test
    fun squareStaysSquareInBothOrientations() {
        assertEquals(1f, frameAspectRatio(StampAspectRatio.RATIO_1_1, false), 0.0001f)
        assertEquals(1f, frameAspectRatio(StampAspectRatio.RATIO_1_1, true), 0.0001f)
    }

    @Test
    fun zoomAppliesScaleAndClampsToCameraRange() {
        assertEquals(1.5f, nextZoomRatio(1f, 1.5f, 1f, 4f), 0.0001f)
        assertEquals(1f, nextZoomRatio(1.2f, 0.5f, 1f, 4f), 0.0001f)
        assertEquals(4f, nextZoomRatio(3f, 2f, 1f, 4f), 0.0001f)
    }

    @Test
    fun mainActivityIsNotLockedToPortrait() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android:screenOrientation=\"portrait\""))
    }
}
