# Phase 2 Verification Results

## Command Run

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

- **Exit code**: `0` (`BUILD SUCCESSFUL in 2m 37s`, 106 actionable tasks: 23 executed, 83 up-to-date)

## `:app:testDebugUnitTest`

| Suite | Tests | Failures | Errors | Skipped | Time |
|---|---:|---:|---:|---:|---:|
| `com.newtermux.blocks.BlockSelectionUiTest` | 1 | 0 | 0 | 0 | 8.937s |
| `com.newtermux.blocks.TerminalBlockModelTest` | 6 | 0 | 0 | 0 | 0.5s |
| `com.newtermux.features.LocalhostServerManagerTest` | 3 | 0 | 0 | 0 | 0.012s |
| `com.newtermux.features.SshProfileStoreTest` | 2 | 0 | 0 | 0 | 0.001s |
| `com.newtermux.input.InputDraftAndLayerTest` | 5 | 0 | 0 | 0 | 1.622s |
| `com.newtermux.theme.CuratedThemesTest` | 8 | 0 | 0 | 0 | 0.549s |
| `com.newtermux.update.UpdateManagerVersionTest` | 2 | 0 | 0 | 0 | 0.002s |
| `com.termux.app.TermuxActivityTest` | 1 | 0 | 0 | 0 | 0.003s |
| `com.termux.app.activities.DevBrowserUrlTest` | 2 | 0 | 0 | 0 | 0.017s |
| `com.termux.app.api.file.FileReceiverActivityTest` | 1 | 0 | 0 | 0 | 0.079s |
| **Total** | **31** | **0** | **0** | **0** | — |

## `:app:assembleDebug`

Generated all 5 debug APKs under `app/build/outputs/apk/debug/`:
- `termux-app_apt-android-7-debug_arm64-v8a.apk` (54M)
- `termux-app_apt-android-7-debug_armeabi-v7a.apk` (25M)
- `termux-app_apt-android-7-debug_universal.apk` (54M)
- `termux-app_apt-android-7-debug_x86.apk` (25M)
- `termux-app_apt-android-7-debug_x86_64.apk` (25M)
