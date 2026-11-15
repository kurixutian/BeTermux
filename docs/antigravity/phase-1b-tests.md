# Phase 1b Verification Results

## Command Run

```bash
./gradlew :terminal-emulator:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
```

- **Exit code**: `0` (`BUILD SUCCESSFUL in 3m 19s`, 110 actionable tasks: 25 executed, 85 up-to-date)

## `:terminal-emulator:testDebugUnitTest`

| Suite | Tests | Failures | Errors | Skipped | Time |
|---|---:|---:|---:|---:|---:|
| `com.termux.terminal.ApcTest` | 1 | 0 | 0 | 0 | 0.016s |
| `com.termux.terminal.ByteQueueTest` | 4 | 0 | 0 | 0 | 0.003s |
| `com.termux.terminal.ControlSequenceIntroducerTest` | 9 | 0 | 0 | 0 | 0.087s |
| `com.termux.terminal.CursorAndScreenTest` | 13 | 0 | 0 | 0 | 0.039s |
| `com.termux.terminal.DecSetTest` | 3 | 0 | 0 | 0 | 0.007s |
| `com.termux.terminal.DeviceControlStringTest` | 3 | 0 | 0 | 0 | 6.095s |
| `com.termux.terminal.HistoryTest` | 2 | 0 | 0 | 0 | 0.001s |
| `com.termux.terminal.KeyHandlerTest` | 2 | 0 | 0 | 0 | 0.0s |
| `com.termux.terminal.OperatingSystemControlTest` | 7 | 0 | 0 | 0 | 0.038s |
| `com.termux.terminal.OscShellIntegrationTest` | 5 | 0 | 0 | 0 | 0.011s |
| `com.termux.terminal.RectangularAreasTest` | 8 | 0 | 0 | 0 | 0.004s |
| `com.termux.terminal.ResizeTest` | 13 | 0 | 0 | 0 | 0.479s |
| `com.termux.terminal.ScreenBufferTest` | 5 | 0 | 0 | 0 | 0.002s |
| `com.termux.terminal.ScrollRegionTest` | 17 | 0 | 0 | 0 | 0.005s |
| `com.termux.terminal.TerminalRowTest` | 16 | 0 | 0 | 0 | 0.007s |
| `com.termux.terminal.TerminalTest` | 17 | 0 | 0 | 0 | 0.111s |
| `com.termux.terminal.TextStyleTest` | 5 | 0 | 0 | 0 | 0.07s |
| `com.termux.terminal.UnicodeInputTest` | 11 | 0 | 0 | 0 | 0.003s |
| `com.termux.terminal.WcWidthTest` | 9 | 0 | 0 | 0 | 0.001s |
| **Total** | **150** | **0** | **0** | **0** | — |

## `:app:testDebugUnitTest`

| Suite | Tests | Failures | Errors | Skipped | Time |
|---|---:|---:|---:|---:|---:|
| `com.newtermux.blocks.BlockSelectionUiTest` | 1 | 0 | 0 | 0 | 10.558s |
| `com.newtermux.blocks.TerminalBlockModelTest` | 6 | 0 | 0 | 0 | 0.443s |
| `com.newtermux.features.LocalhostServerManagerTest` | 3 | 0 | 0 | 0 | 0.014s |
| `com.newtermux.features.SshProfileStoreTest` | 2 | 0 | 0 | 0 | 0.002s |
| `com.newtermux.theme.CuratedThemesTest` | 8 | 0 | 0 | 0 | 0.555s |
| `com.newtermux.update.UpdateManagerVersionTest` | 2 | 0 | 0 | 0 | 0.006s |
| `com.termux.app.TermuxActivityTest` | 1 | 0 | 0 | 0 | 0.002s |
| `com.termux.app.activities.DevBrowserUrlTest` | 2 | 0 | 0 | 0 | 0.024s |
| `com.termux.app.api.file.FileReceiverActivityTest` | 1 | 0 | 0 | 0 | 0.09s |
| **Total** | **26** | **0** | **0** | **0** | — |

## `:app:assembleDebug`

Generated all 5 debug APKs under `app/build/outputs/apk/debug/`:
- `termux-app_apt-android-7-debug_arm64-v8a.apk` (54M)
- `termux-app_apt-android-7-debug_armeabi-v7a.apk` (25M)
- `termux-app_apt-android-7-debug_universal.apk` (54M)
- `termux-app_apt-android-7-debug_x86.apk` (25M)
- `termux-app_apt-android-7-debug_x86_64.apk` (25M)

Note: Physical device/emulator visual screenshots were not captured in this environment because `adb` cannot execute directly on this container (`Exec format error`).
