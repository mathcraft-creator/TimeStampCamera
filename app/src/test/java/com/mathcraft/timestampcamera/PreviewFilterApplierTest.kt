package com.mathcraft.timestampcamera

import android.view.View
import androidx.camera.view.PreviewView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewFilterApplierTest {
    @Test fun prepareForcesTextureViewCompatibleMode() {
        val view = PreviewView(ApplicationProvider.getApplicationContext())
        PreviewFilterApplier.prepare(view)
        assertEquals(PreviewView.ImplementationMode.COMPATIBLE, view.implementationMode)
    }

    @Test fun activeFilterUsesHardwareLayerAndClearRestoresNoLayer() {
        val view = PreviewView(ApplicationProvider.getApplicationContext())
        PreviewFilterApplier.prepare(view)
        assertTrue(PreviewFilterApplier.update(view, PhotoFilterPreset.WARM, 60))
        assertEquals(View.LAYER_TYPE_HARDWARE, view.layerType)
        PreviewFilterApplier.clear(view)
        assertEquals(View.LAYER_TYPE_NONE, view.layerType)
    }

    @Test fun originalClearsLayerAndReportsSuccess() {
        val view = PreviewView(ApplicationProvider.getApplicationContext())
        PreviewFilterApplier.prepare(view)
        assertTrue(PreviewFilterApplier.update(view, PhotoFilterPreset.ORIGINAL, 100))
        assertEquals(View.LAYER_TYPE_NONE, view.layerType)
    }
}
