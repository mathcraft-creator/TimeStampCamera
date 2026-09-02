# Final Feature Integration and APK Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Combine remote shutter, live filters, orientation-aware camera framing, and zoom into one tested `main` build, then publish its installable APK through the existing rolling GitHub release.

**Architecture:** Start an isolated integration worktree from `feature/remote-shutter`, merge `feature/orientation-zoom`, and resolve overlapping Android files by preserving both branches' behaviors. Keep zoom math in `CameraFrameLogic.kt`, remote input in `RemoteShutter.kt`, and compose both through `CameraScreen.kt`. After local unit tests and debug/release builds pass, fast-forward `main`, push it, and verify the GitHub Actions release asset points at the pushed commit.

**Tech Stack:** Kotlin, Jetpack Compose, CameraX 1.3.4, Gradle 8.7, JUnit 4, Robolectric, GitHub Actions, GitHub Releases

## Global Constraints

- Minimum supported Android version remains Android 10 (API 29).
- Preserve all remote shutter, live filter, beauty, stamp, logo, border, front/rear camera, orientation, and zoom behaviors.
- Zoom stays within the active camera's CameraX-reported minimum and maximum ratios.
- Existing user settings remain backward compatible.
- The published APK must be produced from the final pushed `main` commit.

---

### Task 1: Create the isolated integration branch

**Files:**
- Create worktree: `.worktrees/final-integration`
- Modify: none

**Interfaces:**
- Consumes: `feature/remote-shutter`, `feature/orientation-zoom`
- Produces: local branch `feature/final-integration`

- [ ] **Step 1: Verify `.worktrees` is ignored**

Run: `git check-ignore .worktrees`
Expected: `.worktrees` is printed.

- [ ] **Step 2: Create the integration worktree**

Run: `git worktree add .worktrees/final-integration -b feature/final-integration feature/remote-shutter`
Expected: a new worktree on `feature/final-integration`.

- [ ] **Step 3: Merge the zoom branch without finalizing conflicts**

Run: `git merge --no-ff feature/orientation-zoom`
Expected: either a clean merge or conflicts limited to the known overlapping Android files.

### Task 2: Preserve zoom, orientation, filters, and remote shutter together

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/mathcraft/timestampcamera/CameraScreen.kt`
- Preserve: `app/src/main/java/com/mathcraft/timestampcamera/CameraFrameLogic.kt`
- Preserve: `app/src/main/java/com/mathcraft/timestampcamera/RemoteShutter.kt`
- Test: `app/src/test/java/com/mathcraft/timestampcamera/CameraFrameLogicTest.kt`

**Interfaces:**
- Consumes: `nextZoomRatio(current: Float, scaleFactor: Float, min: Float, max: Float): Float`, `RemoteShutterBus.trigger()`, `PhotoFilterPanel(...)`
- Produces: one `CameraScreen` with orientation-aware preview, front/rear switching, pinch/button zoom, live filters, beauty toggle, and remote shutter capture

- [ ] **Step 1: Resolve build and manifest conflicts additively**

Preserve the current CameraX dependencies, S Pen SDK file-tree dependency, portrait-orientation removal from the zoom branch, and S Pen package query from the remote branch.

- [ ] **Step 2: Resolve `CameraScreen.kt` additively**

Use the remote/filter branch layout as the base, then add the zoom branch's display rotation, frame aspect calculation, `Camera` binding state, zoom state observer, pinch gesture, `−/＋` controls, and lens-switch zoom reset. Keep filter preview/update/disposal, photo filter save processing, beauty controls, and `RemoteShutterBus`/`SpenRemoteController` effects.

- [ ] **Step 3: Verify no conflict markers remain**

Run: `rg -n '^(<<<<<<<|=======|>>>>>>>)' app`
Expected: no output.

- [ ] **Step 4: Run zoom unit tests**

Run: `gradle :app:testDebugUnitTest --tests com.mathcraft.timestampcamera.CameraFrameLogicTest --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the integration**

Run: `git add app README.md docs && git commit`
Expected: one merge commit containing both feature sets.

### Task 3: Verify the complete application

**Files:**
- Test: all files under `app/src/test/java/com/mathcraft/timestampcamera/`
- Build output: `app/build/outputs/apk/debug/app-debug.apk`
- Build output: `app/build/outputs/apk/release/app-release-unsigned.apk`

**Interfaces:**
- Consumes: the integrated Android app
- Produces: tested debug and release APKs

- [ ] **Step 1: Run all unit tests**

Run: `gradle :app:testDebugUnitTest --no-daemon --stacktrace`
Expected: `BUILD SUCCESSFUL` and no failed tests.

- [ ] **Step 2: Build the debug APK**

Run: `gradle :app:assembleDebug --no-daemon --stacktrace`
Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Build the release APK**

Run: `gradle :app:assembleRelease --no-daemon --stacktrace`
Expected: `BUILD SUCCESSFUL`; signed output when signing secrets exist, otherwise an unsigned release artifact.

- [ ] **Step 4: Inspect APK metadata**

Run: use Android SDK `aapt dump badging app/build/outputs/apk/debug/app-debug.apk` when available.
Expected: package `com.mathcraft.timestampcamera`, version code `1`, version name `1.0`, minimum SDK `29`.

### Task 4: Publish and verify the rolling GitHub release

**Files:**
- Update branch ref: `main`
- Publish asset: GitHub release tag `debug`, asset `app-debug.apk`

**Interfaces:**
- Consumes: verified `feature/final-integration` commit and `.github/workflows/build.yml`
- Produces: public install URL `https://github.com/mathcraft-creator/TimeStampCamera/releases/download/debug/app-debug.apk`

- [ ] **Step 1: Fast-forward local `main` to the verified integration commit**

Run: `git branch -f main feature/final-integration` from a worktree where `main` is not checked out.
Expected: `main` and `feature/final-integration` resolve to the same commit.

- [ ] **Step 2: Push the integration and main branches**

Run: `git push origin feature/final-integration main`
Expected: both remote refs point at the verified integration commit; the `main` push starts `Build APK`.

- [ ] **Step 3: Poll the GitHub Actions run**

Use the GitHub API to find the `Build APK` run for the pushed commit and wait until it completes.
Expected: conclusion `success`.

- [ ] **Step 4: Verify the rolling release target and asset**

Use the GitHub Releases API and a HEAD request for the asset URL.
Expected: tag `debug` targets the pushed `main` commit and `app-debug.apk` downloads successfully.

- [ ] **Step 5: Add the verified download link to the feature introduction**

Modify `docs/superpowers/specs/2026-09-02-short-app-promotion-copy-design.md` so its sharing copy links to the verified rolling release asset, commit the documentation, push `main`, and verify the follow-up workflow succeeds.
