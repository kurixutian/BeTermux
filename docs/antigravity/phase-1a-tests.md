# Phase 1a — Test & Verification Report

## 1. Gradle Unit Tests & Debug APK Build
Executed after the final edit:
```sh
./gradlew :app:testDebugUnitTest :terminal-emulator:testDebugUnitTest :app:assembleDebug --console=plain
```
- **Result**: `BUILD SUCCESSFUL in 1m 5s` (exit code `0`)

### `:app:testDebugUnitTest` (19 tests, 0 failures, 0 errors, 0 skipped)
All test suites passed under Robolectric `4.14.1` (`enabledSdks="28"`):
- `com.newtermux.theme.CuratedThemesTest`: **8 passed** (0 skipped, 0 failed)
  - `curatedThemesListHasExactlyThreeCuratedEntries`
  - `normalizeKeyMapsLegacyThemesAndNullToWarmDark`
  - `highContrastMeetsWcagAaaAcrossAllSurfaces` (>= 7.0:1 primary, secondary, muted, defaultAccent on background/surface/surfaceElevated)
  - `warmDarkAndSolarizedDarkMeetWcagAaForPrimaryAndSecondaryText` (>= 7.0 primary, >= 4.5 secondary)
  - `colorThemePersistsAndWritesValidProperties`
  - `previewColorsMatchJavaSurfaceAccessors`
  - `backgroundScrimClampsAndPersistsAcrossReads`
  - `motionPreferencesRespectsSystemAnimatorScale`
- `com.newtermux.features.LocalhostServerManagerTest`: **3 passed**
- `com.newtermux.features.SshProfileStoreTest`: **2 passed**
- `com.newtermux.update.UpdateManagerVersionTest`: **2 passed**
- `com.termux.app.activities.DevBrowserUrlTest`: **2 passed**
- `com.termux.app.TermuxActivityTest`: **1 passed**
- `com.termux.app.api.file.FileReceiverActivityTest`: **1 passed** (`testIsSharedTextAnUrl` now green on Robolectric 4.14.1)

### `:terminal-emulator:testDebugUnitTest` (145 baseline tests, `UP-TO-DATE`)
Unmodified in Phase 1a (`ApcTest` 1, `ByteQueueTest` 4, `ControlSequenceIntroducerTest` 9, `CursorAndScreenTest` 13, `DecSetTest` 3, `DeviceControlStringTest` 3, `HistoryTest` 2, `KeyHandlerTest` 2, `OperatingSystemControlTest` 7, `RectangularAreasTest` 8, `ResizeTest` 13, `ScreenBufferTest` 5, `ScrollRegionTest` 17, `TerminalRowTest` 16, `TerminalTest` 17, `TextStyleTest` 5, `UnicodeInputTest` 11, `WcWidthTest` 9 — 0 failures, 0 errors, 0 skipped).

### `:app:assembleDebug`
Generated all 5 debug split/universal APKs cleanly under `app/build/outputs/apk/debug/`:
- `termux-app_apt-android-7-debug_arm64-v8a.apk` (54M)
- `termux-app_apt-android-7-debug_armeabi-v7a.apk` (25M)
- `termux-app_apt-android-7-debug_universal.apk` (54M)
- `termux-app_apt-android-7-debug_x86.apk` (25M)
- `termux-app_apt-android-7-debug_x86_64.apk` (25M)

## 2. Static Removal Verification
Command:
```sh
rg -c "com\.newtermux\.glass|GlassBackground|GlassCard|GlassTopBar|GlassTabBar|GlassChip|GlassBottomSheet|glassSurface|glassPageSource|LocalGlass|RuntimeShader|RenderEffect|Liquid Glass" app/src/
```
- **Result**: Zero matches (`ZERO_GLASS_MATCHES`, exit code `1` from `rg` matching nothing).

## 3. Scope & Environment Limitations (Unverified on Device)
- **No attached Android device / ADB in this environment**: `adb` is not installed on PATH and `/data/data/com.termux/files/home/android-sdk/platform-tools/adb` cannot execute on this `linux-aarch64` host (`Exec format error`).
- **Pending manual / device QA before final release**: visual inspection/screenshots of Warm Dark, Solarized Dark, High Contrast, Settings, Theme Picker, interactive `ActivityResultContracts.GetContent` photo picker, and background image + scrim rendering inside `TermuxActivity`.
