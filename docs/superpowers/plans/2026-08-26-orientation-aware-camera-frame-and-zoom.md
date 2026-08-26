# Orientation-Aware Camera Frame and Zoom Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rotate the camera frame between 3:4/4:3 and 9:16/16:9 while preserving 1:1, and add CameraX pinch plus button zoom.

**Architecture:** Put orientation and zoom arithmetic in a small platform-independent `CameraFrameLogic.kt` unit and cover it with JVM tests. `CameraScreen` reads Compose configuration, binds CameraX for the active orientation, observes the bound camera's zoom state, and routes gestures/buttons through the tested calculations.

**Tech Stack:** Kotlin 2.0, Android SDK 34 (minimum 29), Jetpack Compose BOM 2024.09.00, CameraX 1.3.4, JUnit 4.13.2, Gradle.

## Global Constraints

- Keep persisted `StampAspectRatio` enum names and the settings choices `3:4`, `9:16`, and `1:1` unchanged.
- Portrait maps to `3:4`, `9:16`, `1:1`; landscape maps to `4:3`, `16:9`, `1:1`.
- Pinch and `− / +` buttons must both use CameraX zoom so preview and capture match.
- Rotation preserves zoom; switching front/back cameras resets zoom to `1.0×`; zoom is not persisted.
- Do not redesign unrelated controls, EXIF behavior, or gallery storage.

---

### Task 1: Tested frame and zoom calculations

**Files:**
- Create: `app/src/main/java/com/mathcraft/timestampcamera/CameraFrameLogic.kt`
- Create: `app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: existing `StampAspectRatio`.
- Produces: `frameAspectRatio(ratio: StampAspectRatio, isLandscape: Boolean): Float` and `nextZoomRatio(current: Float, scaleFactor: Float, min: Float, max: Float): Float`.

- [ ] **Step 1: Add the JVM test dependency and write failing tests**

Add to `dependencies` in `app/build.gradle.kts`:

```kotlin
testImplementation("junit:junit:4.13.2")
```

Create `CameraFrameLogicTest.kt`:

```kotlin
package com.mathcraft.timestampcamera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFrameLogicTest {
    @Test fun portraitRatiosKeepConfiguredValues() {
        assertEquals(3f / 4f, frameAspectRatio(StampAspectRatio.RATIO_3_4, false), 0.0001f)
        assertEquals(9f / 16f, frameAspectRatio(StampAspectRatio.RATIO_9_16, false), 0.0001f)
    }

    @Test fun landscapeRatiosInvertConfiguredValues() {
        assertEquals(4f / 3f, frameAspectRatio(StampAspectRatio.RATIO_3_4, true), 0.0001f)
        assertEquals(16f / 9f, frameAspectRatio(StampAspectRatio.RATIO_9_16, true), 0.0001f)
    }

    @Test fun squareStaysSquareInBothOrientations() {
        assertEquals(1f, frameAspectRatio(StampAspectRatio.RATIO_1_1, false), 0.0001f)
        assertEquals(1f, frameAspectRatio(StampAspectRatio.RATIO_1_1, true), 0.0001f)
    }

    @Test fun zoomAppliesScaleAndClampsToCameraRange() {
        assertEquals(1.5f, nextZoomRatio(1f, 1.5f, 1f, 4f), 0.0001f)
        assertEquals(1f, nextZoomRatio(1.2f, 0.5f, 1f, 4f), 0.0001f)
        assertEquals(4f, nextZoomRatio(3f, 2f, 1f, 4f), 0.0001f)
    }
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.CameraFrameLogicTest"`

Expected: compilation fails because `frameAspectRatio` and `nextZoomRatio` do not exist.

- [ ] **Step 3: Add the minimal pure implementation**

Create `CameraFrameLogic.kt`:

```kotlin
package com.mathcraft.timestampcamera

fun frameAspectRatio(ratio: StampAspectRatio, isLandscape: Boolean): Float =
    if (isLandscape && ratio != StampAspectRatio.RATIO_1_1) 1f / ratio.value else ratio.value

fun nextZoomRatio(
    current: Float,
    scaleFactor: Float,
    min: Float,
    max: Float
): Float = (current * scaleFactor).coerceIn(min, max)
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.CameraFrameLogicTest"`

Expected: `BUILD SUCCESSFUL` and all four tests pass.

- [ ] **Step 5: Commit the calculation unit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/mathcraft/timestampcamera/CameraFrameLogic.kt app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt
git commit -m "test: define camera frame and zoom calculations"
```

---

### Task 2: Enable rotation and fit the frame inside both screen orientations

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt`
- Test: `app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt`

**Interfaces:**
- Consumes: `frameAspectRatio(StampAspectRatio, Boolean): Float` from Task 1.
- Produces: an orientation-aware, screen-bounded camera frame and an orientation-keyed CameraX bind.

- [ ] **Step 1: Add a failing manifest regression test**

Add to `CameraFrameLogicTest.kt`:

```kotlin
@Test fun mainActivityIsNotLockedToPortrait() {
    val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
    org.junit.Assert.assertFalse(manifest.contains("android:screenOrientation=\"portrait\""))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.CameraFrameLogicTest.mainActivityIsNotLockedToPortrait"`

Expected: assertion failure because the manifest still contains the portrait lock.

- [ ] **Step 3: Remove the portrait lock and use the calculated frame ratio**

Delete `android:screenOrientation="portrait"` from `.MainActivity` in `AndroidManifest.xml`.

In `CameraScreen.kt`, import `android.content.res.Configuration`, `androidx.compose.foundation.layout.heightIn`, `androidx.compose.ui.platform.LocalConfiguration`, and `androidx.compose.ui.unit.Dp`. Read orientation and calculate:

```kotlin
val configuration = LocalConfiguration.current
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
val displayAspectRatio = frameAspectRatio(config.aspectRatio, isLandscape)
```

Include `isLandscape` in the camera binding `LaunchedEffect` key. Replace the frame modifier with a bounded box whose width and height are selected from `maxWidth`, `maxHeight`, and `displayAspectRatio`:

```kotlin
val frameWidth: Dp
val frameHeight: Dp
if (maxWidth / maxHeight > displayAspectRatio) {
    frameHeight = maxHeight
    frameWidth = maxHeight * displayAspectRatio
} else {
    frameWidth = maxWidth
    frameHeight = maxWidth / displayAspectRatio
}

Modifier.size(frameWidth, frameHeight).align(Alignment.Center)
```

Move the logo preview into this same frame `Box` so its alignment and border inset use the captured area rather than the full screen.

- [ ] **Step 4: Run tests and compile the app**

Run: `./gradlew.bat testDebugUnitTest assembleDebug`

Expected: manifest regression and calculation tests pass; `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit orientation support**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt
git commit -m "feat: adapt camera frame to screen orientation"
```

---

### Task 3: Bind CameraX zoom state and pinch gestures

**Files:**
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt`

**Interfaces:**
- Consumes: `nextZoomRatio(current, scaleFactor, min, max): Float` from Task 1 and CameraX `Camera.cameraControl`/`cameraInfo.zoomState`.
- Produces: observed `zoomRatio`, `minZoomRatio`, `maxZoomRatio`, plus a shared `applyZoomScale(scaleFactor: Float)` path for gestures and buttons.

- [ ] **Step 1: Extend the zoom test with a neutral-scale case and verify RED**

Add to `zoomAppliesScaleAndClampsToCameraRange`:

```kotlin
assertEquals(2f, nextZoomRatio(2f, 1f, 1f, 4f), 0.0001f)
```

Temporarily change the production function to omit clamping for this RED cycle only by first adding a separate invalid-range test:

```kotlin
@Test(expected = IllegalArgumentException::class)
fun zoomRejectsAnInvertedCameraRange() {
    nextZoomRatio(1f, 2f, 4f, 1f)
}
```

Run: `./gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.CameraFrameLogicTest.zoomRejectsAnInvertedCameraRange"`

Expected: FAIL because the function does not validate `min <= max`.

- [ ] **Step 2: Add minimal range validation and verify GREEN**

Change `nextZoomRatio` to:

```kotlin
fun nextZoomRatio(current: Float, scaleFactor: Float, min: Float, max: Float): Float {
    require(min <= max) { "min zoom must not exceed max zoom" }
    return (current * scaleFactor).coerceIn(min, max)
}
```

Run: `./gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.CameraFrameLogicTest"`

Expected: all tests pass.

- [ ] **Step 3: Connect CameraX state and preserve zoom across rotation**

In `CameraScreen.kt`, keep mutable state for the bound `Camera`, actual zoom, and requested zoom:

```kotlin
var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
var zoomRatio by remember { mutableStateOf(1f) }
var minZoomRatio by remember { mutableStateOf(1f) }
var maxZoomRatio by remember { mutableStateOf(1f) }
var requestedZoomRatio by remember { mutableStateOf(1f) }
```

Store the return value from `bindToLifecycle`, observe `camera.cameraInfo.zoomState` with the lifecycle owner, update all three displayed limits/values, and immediately call `camera.cameraControl.setZoomRatio(requestedZoomRatio.coerceIn(min, max))` after binding. Remove the observer in `DisposableEffect` cleanup.

Add the shared handler:

```kotlin
fun applyZoomScale(scaleFactor: Float) {
    val camera = boundCamera ?: return
    val target = nextZoomRatio(zoomRatio, scaleFactor, minZoomRatio, maxZoomRatio)
    requestedZoomRatio = target
    camera.cameraControl.setZoomRatio(target)
}
```

Attach `Modifier.pointerInput(boundCamera, minZoomRatio, maxZoomRatio) { detectTransformGestures { _, _, gestureZoom, _ -> applyZoomScale(gestureZoom) } }` to the camera frame, importing `pointerInput` and `detectTransformGestures`.

- [ ] **Step 4: Compile and run all unit tests**

Run: `./gradlew.bat testDebugUnitTest assembleDebug`

Expected: all tests pass and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit pinch zoom**

```powershell
git add app/src/main/java/com/mathcraft/timestampcamera/CameraFrameLogic.kt app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt
git commit -m "feat: add CameraX pinch zoom"
```

---

### Task 4: Add zoom buttons, indicator, and camera-switch reset

**Files:**
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt`

**Interfaces:**
- Consumes: `applyZoomScale(Float)` and observed CameraX zoom limits from Task 3.
- Produces: `− / current × / +` controls and explicit `1.0×` reset on lens switch.

- [ ] **Step 1: Add a failing button-step calculation test**

Add to `CameraFrameLogicTest.kt`:

```kotlin
@Test fun zoomButtonsUseSymmetricMultiplicativeSteps() {
    val increased = nextZoomRatio(2f, 1.25f, 1f, 8f)
    val decreased = nextZoomRatio(increased, 1f / 1.25f, 1f, 8f)
    assertEquals(2.5f, increased, 0.0001f)
    assertEquals(2f, decreased, 0.0001f)
}
```

Before running, set a wished-for public constant in the test (`ZOOM_BUTTON_FACTOR`) so compilation fails:

```kotlin
val increased = nextZoomRatio(2f, ZOOM_BUTTON_FACTOR, 1f, 8f)
val decreased = nextZoomRatio(increased, 1f / ZOOM_BUTTON_FACTOR, 1f, 8f)
```

Run: `./gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.CameraFrameLogicTest.zoomButtonsUseSymmetricMultiplicativeSteps"`

Expected: compilation fails because `ZOOM_BUTTON_FACTOR` is undefined.

- [ ] **Step 2: Add the minimal button factor and verify GREEN**

Add to `CameraFrameLogic.kt`:

```kotlin
const val ZOOM_BUTTON_FACTOR = 1.25f
```

Run: `./gradlew.bat testDebugUnitTest --tests "com.mathcraft.timestampcamera.CameraFrameLogicTest"`

Expected: all tests pass.

- [ ] **Step 3: Add controls and reset on lens switch**

Add a bottom-centered `Row` above the capture button with two circular clickable controls and a formatted label:

```kotlin
Row(
    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    ZoomButton("−", enabled = zoomRatio > minZoomRatio) {
        applyZoomScale(1f / ZOOM_BUTTON_FACTOR)
    }
    Text(String.format(java.util.Locale.US, "%.1f×", zoomRatio), modifier = Modifier.padding(horizontal = 16.dp))
    ZoomButton("+", enabled = zoomRatio < maxZoomRatio) {
        applyZoomScale(ZOOM_BUTTON_FACTOR)
    }
}
```

Implement a private `@Composable ZoomButton(label: String, enabled: Boolean, onClick: () -> Unit)` using the existing circular white control style. In the camera-switch click handler, set both `requestedZoomRatio` and `zoomRatio` to `1f` before changing `lensFacing`. Do not reset zoom in the orientation path.

- [ ] **Step 4: Run all tests and assemble**

Run: `./gradlew.bat testDebugUnitTest assembleDebug`

Expected: all tests pass and the debug APK builds successfully.

- [ ] **Step 5: Commit zoom controls**

```powershell
git add app/src/main/java/com/mathcraft/timestampcamera/CameraFrameLogic.kt app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt
git commit -m "feat: add camera zoom controls"
```

---

### Task 5: Final regression and artifact verification

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`
- Modify only if a preceding verification exposes a scoped defect.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: verified debug build and clean tracked working tree.

- [ ] **Step 1: Run the complete verification suite from a clean command**

Run: `./gradlew.bat clean testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`, all JVM tests pass, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 2: Inspect manifest merge and repository state**

Run:

```powershell
Select-String -Path app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml -Pattern 'screenOrientation'
git status --short
```

Expected: no portrait `screenOrientation` match; no uncommitted tracked source changes.

- [ ] **Step 3: Perform device/emulator acceptance checks when an Android target is available**

Verify: `3:4 → 4:3`, `9:16 → 16:9`, and `1:1 → 1:1` on rotation; the frame stays on-screen; pinch and both buttons change the displayed and captured zoom; rotation preserves zoom; lens switching resets to `1.0×`.

- [ ] **Step 4: Commit only any scoped verification fixes**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mathcraft/timestampcamera/CameraFrameLogic.kt app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt
git commit -m "fix: resolve camera rotation and zoom regression"
```

Skip this commit when Step 1–3 require no fixes.
