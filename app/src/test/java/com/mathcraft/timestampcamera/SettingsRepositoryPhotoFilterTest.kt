package com.mathcraft.timestampcamera

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryPhotoFilterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clearBefore() = clear()
    @After fun clearAfter() = clear()

    @Test fun missingSettingsUseOriginalAtFullIntensity() {
        val loaded = SettingsRepository(context).load()
        assertEquals(PhotoFilterPreset.ORIGINAL, loaded.photoFilter)
        assertEquals(100, loaded.photoFilterIntensity)
    }

    @Test fun filterAndIntensityRoundTrip() {
        val repository = SettingsRepository(context)
        repository.save(StampConfig(
            photoFilter = PhotoFilterPreset.VINTAGE,
            photoFilterIntensity = 65
        ))
        val loaded = repository.load()
        assertEquals(PhotoFilterPreset.VINTAGE, loaded.photoFilter)
        assertEquals(65, loaded.photoFilterIntensity)
    }

    @Test fun unknownFilterAndOutOfRangeIntensityAreSanitized() {
        context.getSharedPreferences("stamp_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("photoFilterId", "removed-filter")
            .putInt("photoFilterIntensity", 140)
            .commit()
        val loaded = SettingsRepository(context).load()
        assertEquals(PhotoFilterPreset.ORIGINAL, loaded.photoFilter)
        assertEquals(100, loaded.photoFilterIntensity)
    }

    private fun clear() {
        context.getSharedPreferences("stamp_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
