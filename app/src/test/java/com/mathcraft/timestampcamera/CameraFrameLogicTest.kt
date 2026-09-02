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
        assertEquals(2f, nextZoomRatio(2f, 1f, 1f, 4f), 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zoomRejectsAnInvertedCameraRange() {
        nextZoomRatio(1f, 2f, 4f, 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zoomRejectsANonPositiveScaleFactor() {
        nextZoomRatio(2f, 0f, 1f, 4f)
    }

    @Test
    fun zoomButtonsUseSymmetricMultiplicativeSteps() {
        val increased = nextZoomRatio(2f, ZOOM_BUTTON_FACTOR, 1f, 8f)
        val decreased = nextZoomRatio(increased, 1f / ZOOM_BUTTON_FACTOR, 1f, 8f)
        assertEquals(2.5f, increased, 0.0001f)
        assertEquals(2f, decreased, 0.0001f)
    }

    @Test
    fun mainActivityIsNotLockedToPortrait() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android:screenOrientation=\"portrait\""))
    }

    @Test
    fun cameraUseCasesReceiveTheCurrentDisplayRotation() {
        val cameraScreen = File("src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt").readText()
        val call = Regex.escape(".setTargetRotation(displayRotation)").toRegex()
        assertEquals(2, call.findAll(cameraScreen).count())
    }

    @Test
    fun onlyTheLatestCameraBindRequestRemainsCurrent() {
        val requests = LatestRequestGuard()
        val first = requests.start()
        val second = requests.start()

        assertFalse(requests.isCurrent(first))
        org.junit.Assert.assertTrue(requests.isCurrent(second))

        requests.invalidate()
        assertFalse(requests.isCurrent(second))
    }

    @Test
    fun onlyTheLatestFailedZoomRequestRollsBackToActualZoom() {
        assertEquals(1.4f, zoomRatioAfterFailure(2f, 2f, 1.4f), 0.0001f)
        assertEquals(3f, zoomRatioAfterFailure(2f, 3f, 1.4f), 0.0001f)
    }
}
