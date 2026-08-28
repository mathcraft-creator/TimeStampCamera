# Live Photo Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add eight selectable photo-filter presets with a shared intensity control, live CameraX preview rendering, persistent settings, and matching saved-photo processing after the existing beauty pass.

**Architecture:** Keep filter definitions and Bitmap processing in a focused `PhotoFilter` unit that produces one Android `ColorMatrix` for both preview and saved-photo paths. Apply that matrix to a `PreviewView` hardware layer running in `COMPATIBLE` mode, while the capture pipeline applies it to the cropped/beautified Bitmap before stamping. Keep panel state local to `CameraScreen`, but persist the selected preset and intensity in `StampConfig` through `SettingsRepository`.

**Tech Stack:** Kotlin 2.0, Android SDK 34 (minimum API 29), CameraX 1.3.4, Jetpack Compose Material 3, Android `ColorMatrix`/`ColorMatrixColorFilter`, JUnit 4, Robolectric 4.13.

## Global Constraints

- Keep the current minimum supported version at Android 10 (API 29).
- Do not add an image-processing library, ML model, OpenGL shader, CameraX `CameraEffect`, or another CameraX stream.
- Provide exactly these presets: 원본, 화사함, 따뜻함, 차가움, 흑백, 빈티지, 선명함, 페이드.
- Persist one stable preset identifier and one shared intensity in the inclusive range `0..100`; defaults are 원본 and `100`.
- Preserve this capture order: rotation correction → aspect-ratio crop → beauty → photo filter → stamp/logo/border → gallery save.
- The original preset must be a no-op and must not allocate a replacement Bitmap.
- Preview failure must fall back to an unfiltered preview without preventing filtered saved-photo processing.
- Filter failure during saved-photo processing must continue with the pre-filter Bitmap and attempt stamping and saving.
- The filter panel remains usable in portrait and landscape and does not prevent capture while open.

---

## File Map

- Create `app/src/main/java/com/mathcraft/timestampcamera/PhotoFilter.kt`: stable preset model, matrix interpolation, Bitmap application, and allocation-failure fallback.
- Create `app/src/main/java/com/mathcraft/timestampcamera/PreviewFilterApplier.kt`: `PreviewView` hardware-layer setup, filter updates, clear/fallback behavior.
- Create `app/src/main/java/com/mathcraft/timestampcamera/PhotoFilterPanel.kt`: filter button/panel, intensity slider, horizontally scrolling preview thumbnails.
- Create `app/src/main/res/drawable/filter_thumbnail_sample.xml`: small neutral vector sample shared by all thumbnail previews.
- Modify `app/src/main/java/com/mathcraft/timestampcamera/StampConfig.kt`: filter fields and SharedPreferences persistence.
- Modify `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt`: preview configuration, capture snapshot/order, panel state, and UI integration.
- Modify `app/build.gradle.kts`: JUnit and Robolectric test dependencies plus Android resources for unit tests.
- Create `app/src/test/java/com/mathcraft/timestampcamera/PhotoFilterTest.kt`: preset IDs, matrix interpolation, no-op, pixel transform, and fallback tests.
- Create `app/src/test/java/com/mathcraft/timestampcamera/SettingsRepositoryPhotoFilterTest.kt`: persistence, defaults, unknown ID, and intensity clamping.
- Create `app/src/test/java/com/mathcraft/timestampcamera/PreviewFilterApplierTest.kt`: compatible mode, hardware-layer application, clear, and failure fallback.

---

### Task 1: Filter Presets and Bitmap Engine

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/mathcraft/timestampcamera/PhotoFilter.kt`
- Create: `app/src/test/java/com/mathcraft/timestampcamera/PhotoFilterTest.kt`

**Interfaces:**
- Consumes: Android `Bitmap`, `Canvas`, `ColorMatrix`, `ColorMatrixColorFilter`, and `Paint`.
- Produces: `PhotoFilterPreset.fromId(String?): PhotoFilterPreset`, `PhotoFilter.matrix(PhotoFilterPreset, Int): ColorMatrix`, `PhotoFilter.apply(Bitmap, PhotoFilterPreset, Int): Bitmap`, and `PhotoFilter.applyOrOriginal(Bitmap, PhotoFilterPreset, Int): Bitmap`.

- [ ] **Step 1: Add the local unit-test dependencies**

Add this inside `android {}` in `app/build.gradle.kts`:

```kotlin
testOptions {
    unitTests.isIncludeAndroidResources = true
}
```

Add these entries inside `dependencies {}`:

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.robolectric:robolectric:4.13")
```

- [ ] **Step 2: Write failing tests for preset identity, interpolation, and Bitmap ownership**

Create `app/src/test/java/com/mathcraft/timestampcamera/PhotoFilterTest.kt`:

```kotlin
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

@RunWith(RobolectricTestRunner::class)
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
        assertEquals(Color.red(pixel), Color.green(pixel), 1)
        assertEquals(Color.green(pixel), Color.blue(pixel), 1)
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
```

- [ ] **Step 3: Run the tests and verify the new API is missing**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.PhotoFilterTest"
```

Expected: `FAILED` with unresolved references to `PhotoFilterPreset` and `PhotoFilter`.

- [ ] **Step 4: Implement preset matrices and Bitmap application**

Create `app/src/main/java/com/mathcraft/timestampcamera/PhotoFilter.kt`:

```kotlin
package com.mathcraft.timestampcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class PhotoFilterPreset(
    val id: String,
    val label: String,
    internal val fullMatrix: FloatArray
) {
    ORIGINAL("original", "원본", identity()),
    BRIGHT("bright", "화사함", matrix(
        1.08f, 0f, 0f, 0f, 14f,
        0f, 1.08f, 0f, 0f, 12f,
        0f, 0f, 1.04f, 0f, 9f,
        0f, 0f, 0f, 1f, 0f
    )),
    WARM("warm", "따뜻함", matrix(
        1.10f, 0f, 0f, 0f, 10f,
        0f, 1.03f, 0f, 0f, 4f,
        0f, 0f, 0.90f, 0f, -2f,
        0f, 0f, 0f, 1f, 0f
    )),
    COOL("cool", "차가움", matrix(
        0.94f, 0f, 0f, 0f, -2f,
        0f, 1.02f, 0f, 0f, 2f,
        0f, 0f, 1.10f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f
    )),
    MONOCHROME("monochrome", "흑백", matrix(
        .213f, .715f, .072f, 0f, 0f,
        .213f, .715f, .072f, 0f, 0f,
        .213f, .715f, .072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    VINTAGE("vintage", "빈티지", matrix(
        .393f, .769f, .189f, 0f, -12f,
        .349f, .686f, .168f, 0f, -6f,
        .272f, .534f, .131f, 0f, 3f,
        0f, 0f, 0f, 1f, 0f
    )),
    VIVID("vivid", "선명함", matrix(
        1.18f, -.08f, -.08f, 0f, -4f,
        -.08f, 1.18f, -.08f, 0f, -4f,
        -.08f, -.08f, 1.18f, 0f, -4f,
        0f, 0f, 0f, 1f, 0f
    )),
    FADE("fade", "페이드", matrix(
        .88f, .04f, .04f, 0f, 18f,
        .04f, .88f, .04f, 0f, 18f,
        .04f, .04f, .88f, 0f, 18f,
        0f, 0f, 0f, 1f, 0f
    ));

    companion object {
        fun fromId(id: String?): PhotoFilterPreset =
            entries.firstOrNull { it.id == id } ?: ORIGINAL

        private fun identity() = matrix(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        private fun matrix(vararg values: Float): FloatArray = values
    }
}

object PhotoFilter {
    private val identity = PhotoFilterPreset.ORIGINAL.fullMatrix

    fun matrix(preset: PhotoFilterPreset, intensity: Int): ColorMatrix {
        val fraction = if (preset == PhotoFilterPreset.ORIGINAL) 0f
            else intensity.coerceIn(0, 100) / 100f
        return ColorMatrix(FloatArray(20) { index ->
            identity[index] + (preset.fullMatrix[index] - identity[index]) * fraction
        })
    }

    fun apply(src: Bitmap, preset: PhotoFilterPreset, intensity: Int): Bitmap {
        if (preset == PhotoFilterPreset.ORIGINAL || intensity <= 0) return src
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix(preset, intensity))
        }
        Canvas(result).drawBitmap(src, 0f, 0f, paint)
        return result
    }

    fun applyOrOriginal(src: Bitmap, preset: PhotoFilterPreset, intensity: Int): Bitmap =
        try {
            apply(src, preset, intensity)
        } catch (_: RuntimeException) {
            src
        } catch (_: OutOfMemoryError) {
            src
        }
}
```

- [ ] **Step 5: Run the focused tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.PhotoFilterTest"
```

Expected: `BUILD SUCCESSFUL` and all `PhotoFilterTest` methods pass.

- [ ] **Step 6: Commit the filter engine**

```powershell
git add app/build.gradle.kts app/src/main/java/com/mathcraft/timestampcamera/PhotoFilter.kt app/src/test/java/com/mathcraft/timestampcamera/PhotoFilterTest.kt
git commit -m "feat: add photo filter presets and bitmap engine"
```

---

### Task 2: Persistent Filter Settings

**Files:**
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/StampConfig.kt:150-258`
- Create: `app/src/test/java/com/mathcraft/timestampcamera/SettingsRepositoryPhotoFilterTest.kt`

**Interfaces:**
- Consumes: `PhotoFilterPreset.fromId(String?)` from Task 1.
- Produces: `StampConfig.photoFilter: PhotoFilterPreset`, `StampConfig.photoFilterIntensity: Int`, and SharedPreferences keys `photoFilterId`/`photoFilterIntensity`.

- [ ] **Step 1: Write failing persistence and migration tests**

Create `app/src/test/java/com/mathcraft/timestampcamera/SettingsRepositoryPhotoFilterTest.kt`:

```kotlin
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
```

Also add this Task 2 dependency to `app/build.gradle.kts` because the test imports `ApplicationProvider`:

```kotlin
testImplementation("androidx.test:core:1.6.1")
```

- [ ] **Step 2: Run the test and verify the config fields are missing**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.SettingsRepositoryPhotoFilterTest"
```

Expected: `FAILED` with unresolved `photoFilter` and `photoFilterIntensity` references.

- [ ] **Step 3: Add config fields and persistence**

Append these parameters after `beautyBrighten` in `StampConfig`:

```kotlin
val beautyBrighten: Int = 40,
val photoFilter: PhotoFilterPreset = PhotoFilterPreset.ORIGINAL,
val photoFilterIntensity: Int = 100
```

Append these arguments in `SettingsRepository.load()`:

```kotlin
beautyBrighten = prefs.getInt(KEY_BEAUTY_BRIGHTEN, 40),
photoFilter = PhotoFilterPreset.fromId(prefs.getString(KEY_PHOTO_FILTER_ID, null)),
photoFilterIntensity = prefs.getInt(KEY_PHOTO_FILTER_INTENSITY, 100).coerceIn(0, 100)
```

Append these writes after the beauty values in `save()`:

```kotlin
.putString(KEY_PHOTO_FILTER_ID, c.photoFilter.id)
.putInt(KEY_PHOTO_FILTER_INTENSITY, c.photoFilterIntensity.coerceIn(0, 100))
```

Append these constants in the companion object:

```kotlin
private const val KEY_PHOTO_FILTER_ID = "photoFilterId"
private const val KEY_PHOTO_FILTER_INTENSITY = "photoFilterIntensity"
```

- [ ] **Step 4: Run filter and repository tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.PhotoFilterTest" --tests "com.mathcraft.timestampcamera.SettingsRepositoryPhotoFilterTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit settings persistence**

```powershell
git add app/build.gradle.kts app/src/main/java/com/mathcraft/timestampcamera/StampConfig.kt app/src/test/java/com/mathcraft/timestampcamera/SettingsRepositoryPhotoFilterTest.kt
git commit -m "feat: persist photo filter settings"
```

---

### Task 3: Live Preview Filter Adapter

**Files:**
- Create: `app/src/main/java/com/mathcraft/timestampcamera/PreviewFilterApplier.kt`
- Create: `app/src/test/java/com/mathcraft/timestampcamera/PreviewFilterApplierTest.kt`
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt:74-75,89-133`

**Interfaces:**
- Consumes: `PhotoFilter.matrix(PhotoFilterPreset, Int)` from Task 1.
- Produces: `PreviewFilterApplier.prepare(PreviewView)`, `PreviewFilterApplier.update(PreviewView, PhotoFilterPreset, Int): Boolean`, and `PreviewFilterApplier.clear(PreviewView)`.

- [ ] **Step 1: Write failing preview adapter tests**

Create `app/src/test/java/com/mathcraft/timestampcamera/PreviewFilterApplierTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the tests and verify the adapter is missing**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.PreviewFilterApplierTest"
```

Expected: `FAILED` with unresolved `PreviewFilterApplier`.

- [ ] **Step 3: Implement defensive hardware-layer application**

Create `app/src/main/java/com/mathcraft/timestampcamera/PreviewFilterApplier.kt`:

```kotlin
package com.mathcraft.timestampcamera

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.camera.view.PreviewView

object PreviewFilterApplier {
    fun prepare(previewView: PreviewView) {
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }

    fun update(
        previewView: PreviewView,
        preset: PhotoFilterPreset,
        intensity: Int
    ): Boolean = try {
        if (preset == PhotoFilterPreset.ORIGINAL || intensity <= 0) {
            clear(previewView)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(PhotoFilter.matrix(preset, intensity))
            }
            previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            previewView.setLayerPaint(paint)
        }
        true
    } catch (_: RuntimeException) {
        clear(previewView)
        false
    }

    fun clear(previewView: PreviewView) {
        previewView.setLayerPaint(null)
        previewView.setLayerType(View.LAYER_TYPE_NONE, null)
    }
}
```

- [ ] **Step 4: Prepare the remembered PreviewView and update its filter without rebinding CameraX**

Replace the `previewView` initialization in `CameraScreen` with:

```kotlin
val previewView = remember {
    PreviewView(context).also(PreviewFilterApplier::prepare)
}
```

Add this effect after camera binding and do not add filter fields to the camera-binding effect keys:

```kotlin
LaunchedEffect(previewView, config.photoFilter, config.photoFilterIntensity) {
    PreviewFilterApplier.update(
        previewView = previewView,
        preset = config.photoFilter,
        intensity = config.photoFilterIntensity
    )
}

DisposableEffect(previewView) {
    onDispose { PreviewFilterApplier.clear(previewView) }
}
```

The existing binding key remains exactly:

```kotlin
LaunchedEffect(previewView, lensFacing, config.aspectRatio) {
```

- [ ] **Step 5: Run preview and filter tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.PhotoFilterTest" --tests "com.mathcraft.timestampcamera.PreviewFilterApplierTest"
```

Expected: `BUILD SUCCESSFUL` and no CameraX binding is required by the tests.

- [ ] **Step 6: Build the debug APK to catch Android API or import errors**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit live preview support**

```powershell
git add app/src/main/java/com/mathcraft/timestampcamera/PreviewFilterApplier.kt app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt app/src/test/java/com/mathcraft/timestampcamera/PreviewFilterApplierTest.kt
git commit -m "feat: apply photo filters to camera preview"
```

---

### Task 4: Filter Panel and Thumbnail UI

**Files:**
- Create: `app/src/main/java/com/mathcraft/timestampcamera/PhotoFilterPanel.kt`
- Create: `app/src/main/res/drawable/filter_thumbnail_sample.xml`
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt:219-405`

**Interfaces:**
- Consumes: `PhotoFilterPreset.entries`, `PhotoFilter.matrix(...)`, `StampConfig.photoFilter`, and `StampConfig.photoFilterIntensity`.
- Produces: `PhotoFilterControls(selected, intensity, expanded, onToggleExpanded, onSelect, onIntensityChange)` composable.

- [ ] **Step 1: Add the neutral vector thumbnail sample**

Create `app/src/main/res/drawable/filter_thumbnail_sample.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="72dp"
    android:height="72dp"
    android:viewportWidth="72"
    android:viewportHeight="72">
    <path android:fillColor="#78909C" android:pathData="M0,0h72v72h-72z" />
    <path android:fillColor="#A5D6A7" android:pathData="M0,43L19,28 34,41 49,20 72,43v29h-72z" />
    <path android:fillColor="#FFCC80" android:pathData="M22,16a14,14 0,1 0,28 0a14,14 0,1 0,-28 0" />
    <path android:fillColor="#6D4C41" android:pathData="M24,15c2,-11 22,-11 25,1c-7,-3 -17,-3 -25,-1z" />
    <path android:fillColor="#EF9A9A" android:pathData="M28,20h4v2h-4zM40,20h4v2h-4zM33,28c3,2 6,2 9,0v2c-3,3 -6,3 -9,0z" />
</vector>
```

- [ ] **Step 2: Implement the panel and thumbnails in a focused composable**

Create `app/src/main/java/com/mathcraft/timestampcamera/PhotoFilterPanel.kt`:

```kotlin
package com.mathcraft.timestampcamera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val AccentPink = Color(0xFFFF80AB)

@Composable
fun PhotoFilterControls(
    selected: PhotoFilterPreset,
    intensity: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (PhotoFilterPreset) -> Unit,
    onIntensityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("강도", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = intensity.toFloat(),
                        onValueChange = { onIntensityChange(it.roundToInt()) },
                        enabled = selected != PhotoFilterPreset.ORIGINAL,
                        valueRange = 0f..100f,
                        steps = 99,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("$intensity", color = Color.White, fontSize = 12.sp)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PhotoFilterPreset.entries.forEach { preset ->
                        FilterThumbnail(
                            preset = preset,
                            selected = preset == selected,
                            intensity = if (preset == PhotoFilterPreset.ORIGINAL) 0 else intensity,
                            onClick = { onSelect(preset) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (selected == PhotoFilterPreset.ORIGINAL) Color.White.copy(.85f) else AccentPink)
                .clickable(onClick = onToggleExpanded),
            contentAlignment = Alignment.Center
        ) {
            Text("🎨필터", color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FilterThumbnail(
    preset: PhotoFilterPreset,
    selected: Boolean,
    intensity: Int,
    onClick: () -> Unit
) {
    val matrix = PhotoFilter.matrix(preset, intensity).array
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.filter_thumbnail_sample),
            contentDescription = "${preset.label} 필터",
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)),
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) AccentPink else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
        )
        Text(
            text = preset.label,
            color = if (selected) AccentPink else Color.White,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

- [ ] **Step 3: Integrate panel state and callbacks into `CameraScreen`**

Add local panel state beside `saving`:

```kotlin
var filterPanelExpanded by remember { mutableStateOf(false) }
```

Inside the root `BoxWithConstraints`, add a transparent dismiss target immediately after the centered camera-frame `Box` and before the logo/buttons:

```kotlin
if (filterPanelExpanded) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable { filterPanelExpanded = false }
    )
}
```

Place the controls above and to the right of the shutter so the button remains available while the panel is open:

```kotlin
PhotoFilterControls(
    selected = config.photoFilter,
    intensity = config.photoFilterIntensity,
    expanded = filterPanelExpanded,
    onToggleExpanded = { filterPanelExpanded = !filterPanelExpanded },
    onSelect = { preset -> onChange(config.copy(photoFilter = preset)) },
    onIntensityChange = { value ->
        onChange(config.copy(photoFilterIntensity = value.coerceIn(0, 100)))
    },
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(start = 12.dp, end = 12.dp, bottom = 112.dp)
)
```

Keep the shutter `Box` after this composable so it is drawn above the dismiss target and remains clickable. The filter controls consume clicks within their own bounds; clicking elsewhere closes the panel.

- [ ] **Step 4: Compile the complete UI**

Run:

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL`; no unresolved Compose imports; APK produced at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Run static unit tests after UI integration**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with the filter, repository, and preview adapter suites passing.

- [ ] **Step 6: Commit the filter panel**

```powershell
git add app/src/main/java/com/mathcraft/timestampcamera/PhotoFilterPanel.kt app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt app/src/main/res/drawable/filter_thumbnail_sample.xml
git commit -m "feat: add live photo filter controls"
```

---

### Task 5: Saved-Photo Pipeline and Final Verification

**Files:**
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt:160-216`
- Modify: `README.md:5-16`

**Interfaces:**
- Consumes: `PhotoFilter.applyOrOriginal(Bitmap, PhotoFilterPreset, Int)` and the Task 2 `StampConfig` fields.
- Produces: a capture pipeline that snapshots filter settings and applies color filtering after beauty and before `ImageStamper.stamp(...)`.

- [ ] **Step 1: Snapshot configuration at shutter time**

At the start of `takePhoto()`, immediately after the existing `saving` guard, capture the immutable configuration and use it throughout the callback:

```kotlin
fun takePhoto() {
    val capture = imageCapture.value ?: return
    if (saving) return
    val captureConfig = config
    saving = true
```

Within `onCaptureSuccess`, replace every capture-pipeline reference to `config` with `captureConfig`, including aspect ratio, beauty values, location/address flags, `ImageStamper.buildLines`, and `ImageStamper.stamp`.

- [ ] **Step 2: Apply the saved-photo filter after beauty and before stamps**

Insert this block immediately after the existing beauty block:

```kotlin
val colorFiltered = PhotoFilter.applyOrOriginal(
    src = bmp,
    preset = captureConfig.photoFilter,
    intensity = captureConfig.photoFilterIntensity
)
if (colorFiltered !== bmp) bmp.recycle()
bmp = colorFiltered
```

The complete ordered portion must read:

```kotlin
if (captureConfig.aspectRatio == StampAspectRatio.RATIO_1_1) {
    bmp = cropToSquare(bmp)
}

if (captureConfig.beautyEnabled) {
    val filtered = BeautyFilter.apply(
        bmp,
        captureConfig.beautySmooth,
        captureConfig.beautyBrighten
    )
    if (filtered !== bmp) bmp.recycle()
    bmp = filtered
}

val colorFiltered = PhotoFilter.applyOrOriginal(
    bmp,
    captureConfig.photoFilter,
    captureConfig.photoFilterIntensity
)
if (colorFiltered !== bmp) bmp.recycle()
bmp = colorFiltered

val loc = if (captureConfig.showGps || captureConfig.showAddress) {
    locationHelper.current()
} else null
val address = if (captureConfig.showAddress && loc != null) {
    locationHelper.addressOf(loc)
} else null
val lines = ImageStamper.buildLines(captureConfig, Date(), loc, address)
val stamped = ImageStamper.stamp(context, bmp, captureConfig, lines)
saveBitmapToGallery(context, stamped)
```

- [ ] **Step 3: Update the README feature list and behavior note**

Add these bullets under `## 주요 기능` in `README.md`:

```markdown
- **실시간 사진 필터 8종**: 원본·화사함·따뜻함·차가움·흑백·빈티지·선명함·페이드
- 필터 **강도 조절(0~100)** 및 촬영 미리보기·저장 사진 동시 적용
- 기존 **뷰티 보정과 사진 필터를 함께 사용** 가능
```

Add this bullet under `## 참고 사항`:

```markdown
- 일부 기기에서 실시간 필터 미리보기와 저장 사진은 색 공간 차이로 색감이 미세하게 다를 수 있습니다.
```

- [ ] **Step 4: Run the full automated verification suite**

Run:

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug lintDebug
```

Expected: `BUILD SUCCESSFUL`; unit tests pass; debug APK exists; lint reports no fatal errors.

- [ ] **Step 5: Perform the device acceptance matrix**

Install `app/build/outputs/apk/debug/app-debug.apk` on the Galaxy Note20 Ultra or another API 29+ device and record pass/fail for every row:

```text
[ ] Front camera: all 8 presets update preview without camera restart
[ ] Rear camera: all 8 presets update preview without camera restart
[ ] Intensity 0/50/100: visible strength progression; original disables slider
[ ] Panel open: shutter remains clickable and saves a photo
[ ] Outside tap / filter button: panel closes
[ ] Portrait → landscape → portrait: panel and controls stay inside the screen
[ ] 3:4, 9:16, 1:1: saved composition and filter remain correct
[ ] Beauty off/filter off: original behavior retained
[ ] Beauty on/filter off: existing beauty result retained
[ ] Beauty off/filter on: selected color filter saved
[ ] Beauty on/filter on: beauty precedes color filter; stamps retain configured colors
[ ] App restart: selected preset and intensity restore
[ ] Rapid preset/intensity changes: no preview freeze or camera rebind flash
[ ] Preview and saved image: tone direction and relative intensity match by eye
```

Expected: every row passes. If a row fails, use the `systematic-debugging` skill before editing code.

- [ ] **Step 6: Inspect only intended changes and commit integration**

Run:

```powershell
git status --short
git diff --check
git diff -- app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt README.md
```

Expected: no whitespace errors; only the planned source/docs changes plus known pre-existing untracked `.artifacts/` and `.superpowers/` entries.

Commit:

```powershell
git add app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt README.md
git commit -m "feat: save photos with selected color filter"
```

- [ ] **Step 7: Verify the committed tree once more**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
git log -5 --oneline
git status --short
```

Expected: Gradle reports `BUILD SUCCESSFUL`; the five feature commits are visible; only pre-existing untracked build artifacts and the brainstorming directory remain.
