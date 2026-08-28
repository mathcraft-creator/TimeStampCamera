# Bottom Camera Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the shutter horizontally centered while placing Beauty and Filter to its left, Camera Switch to its right, and the expandable filter panel above the entire bottom control row.

**Architecture:** Split the current combined filter panel/toggle composable into an expandable panel and a standalone filter button so `CameraScreen` can position each control independently. Keep transient expansion state and all existing callbacks unchanged; only move control composition and z-order.

**Tech Stack:** Kotlin 2.0, Android SDK 34 (minimum API 29), CameraX 1.3.4, Jetpack Compose Material 3.

## Global Constraints

- Keep the shutter at the exact horizontal center and `28.dp` above the bottom edge.
- Place Beauty and Filter side by side to the shutter's left without overlapping the `78.dp` shutter.
- Place Camera Switch to the shutter's right.
- Remove the old top-left Camera Switch and Beauty controls.
- Keep Settings at the top right.
- Open the filter panel above the bottom button row; it must not overlap the shutter in portrait or landscape.
- Preserve filter-panel state restoration, outside-tap dismissal, accessibility semantics, filter persistence, camera switching, beauty toggling, and capture behavior.
- Keep minimum API 29 and add no dependencies.

---

### Task 1: Recompose the Bottom Camera Controls

**Files:**
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/PhotoFilterPanel.kt`
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt`

**Interfaces:**
- Consumes: current `PhotoFilterPreset`, filter intensity, expansion state, `onChange`, `lensFacing`, and `takePhoto()`.
- Produces: `PhotoFilterPanel(...)` for expanded content and `PhotoFilterButton(...)` for the standalone toggle.

- [ ] **Step 1: Record the pre-change verification baseline**

Temporarily bypass the tracked Java 25 daemon selector, use Java 17, then restore the selector byte-for-byte.

Run:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest assembleDebug
```

Expected: `BUILD SUCCESSFUL`, 15 tests with zero failures, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 2: Split the filter panel and toggle composables**

In `PhotoFilterPanel.kt`, replace `PhotoFilterControls` with these two public composables while preserving the existing private `FilterThumbnail`:

```kotlin
@Composable
fun PhotoFilterPanel(
    selected: PhotoFilterPreset,
    intensity: Int,
    onSelect: (PhotoFilterPreset) -> Unit,
    onIntensityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
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
    }
}

@Composable
fun PhotoFilterButton(
    selected: PhotoFilterPreset,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                if (selected == PhotoFilterPreset.ORIGINAL) Color.White.copy(.85f)
                else AccentPink
            )
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "필터 닫기" else "필터 열기",
                onClick = onClick
            )
            .semantics { stateDescription = if (expanded) "열림" else "닫힘" },
        contentAlignment = Alignment.Center
    ) {
        Text("🎨필터", color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
```

Remove imports that only supported the old wrapper (`Spacer`, `height`) and keep/add imports required by the split (`MutableInteractionSource`, `matchParentSize`, `remember`).

- [ ] **Step 3: Remove the old top-left controls from `CameraScreen`**

Delete the complete old blocks beginning with these comments:

```kotlin
// 카메라 전환 버튼
// 뷰티 필터 빠른 전환 버튼 (전환 버튼 바로 아래)
```

Do not change the top-right `FloatingActionButton` for Settings.

- [ ] **Step 4: Render the expanded filter panel above the bottom row**

Replace the old `PhotoFilterControls(...)` call with:

```kotlin
if (filterPanelExpanded) {
    PhotoFilterPanel(
        selected = config.photoFilter,
        intensity = config.photoFilterIntensity,
        onSelect = { preset -> onChange(config.copy(photoFilter = preset)) },
        onIntensityChange = { value ->
            onChange(config.copy(photoFilterIntensity = value.coerceIn(0, 100)))
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 12.dp, end = 12.dp, bottom = 118.dp)
    )
}
```

Keep this after the full-screen outside-dismiss layer so the panel remains interactive.

- [ ] **Step 5: Add left-side Beauty and Filter controls around the centered shutter**

Add this row after the filter panel and before the shutter:

```kotlin
Row(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .offset(x = (-91).dp)
        .padding(bottom = 40.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    CameraCircleButton(
        text = "✨뷰티",
        active = config.beautyEnabled,
        onClick = { onChange(config.copy(beautyEnabled = !config.beautyEnabled)) }
    )
    PhotoFilterButton(
        selected = config.photoFilter,
        expanded = filterPanelExpanded,
        onClick = { filterPanelExpanded = !filterPanelExpanded }
    )
}
```

The row width is `116.dp`; offset `-91.dp` places its right edge about `33.dp` left of center, leaving a gap beside the shutter's `39.dp` radius.

- [ ] **Step 6: Add the right-side Camera Switch and shared circle helper**

Add this button after the left row and before the shutter:

```kotlin
CameraCircleButton(
    text = "전환",
    active = false,
    onClick = {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
    },
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .offset(x = 70.dp)
        .padding(bottom = 40.dp)
)
```

Add this focused helper below `CameraScreen` and above the border drawing helpers:

```kotlin
@Composable
private fun CameraCircleButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFFFF80AB) else Color.White.copy(alpha = 0.85f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = if (text == "전환") 12.sp else 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
```

Import `androidx.compose.ui.semantics.Role` if the project version exposes it there; otherwise use the existing Compose `androidx.compose.ui.semantics.Role` import already used by `PhotoFilterPanel.kt`.

- [ ] **Step 7: Compile and run all automated checks**

Temporarily bypass the Java 25 daemon selector, run with Java 17, and restore it afterward.

Run:

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest assembleDebug lintDebug
```

Expected: `BUILD SUCCESSFUL`; 15 tests, zero failures/errors; debug APK exists; lint has no fatal errors.

- [ ] **Step 8: Verify the visual acceptance matrix on an API 29+ device**

```text
[ ] Shutter remains at exact screen center in portrait and landscape
[ ] Beauty and Filter are side by side left of shutter without overlap
[ ] Camera Switch is right of shutter without overlap
[ ] Top-left no longer contains Camera Switch or Beauty
[ ] Settings remains top-right
[ ] Filter panel opens above the row and does not cover shutter
[ ] Filter panel outside tap and Filter button both close it
[ ] Beauty toggle, filter selection/intensity, switch, and shutter still work
[ ] Rotation preserves expanded filter-panel state
```

If no authorized device is connected, record this matrix as a pre-release manual gate.

- [ ] **Step 9: Commit the control layout**

```powershell
git add app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt app/src/main/java/com/mathcraft/timestampcamera/PhotoFilterPanel.kt
git commit -m "feat: move camera controls beside shutter"
```
