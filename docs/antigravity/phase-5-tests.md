# Phase 5 Verification Results

## Command Run

```bash
./gradlew :terminal-emulator:testDebugUnitTest :terminal-view:testDebugUnitTest :termux-shared:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --console=plain
```

- **Exit code**: `0` (`BUILD SUCCESSFUL in 1m 31s`, `114 actionable tasks: 9 executed, 105 up-to-date`)
- **`:app:testDebugUnitTest`**: executed after final edit (`44` tests, `0` failures, `0` errors, `0` skipped across 13 suites)
- **`:terminal-emulator:testDebugUnitTest`**: reported `UP-TO-DATE` (`150` tests, `0` failures, `0` errors, `0` skipped across 19 XML test reports in `terminal-emulator/build/test-results/testDebugUnitTest/`)
- **`:terminal-view:testDebugUnitTest`** & **`:termux-shared:testDebugUnitTest`**: `NO-SOURCE`

## `:app:testDebugUnitTest` (Executed)

| Suite | Tests | Failures | Errors | Skipped | Time |
|---|---:|---:|---:|---:|---:|
| `com.newtermux.ai.AiControllerAndContextTest` | 4 | 0 | 0 | 0 | 11.092s |
| `com.newtermux.blocks.BlockSelectionUiTest` | 1 | 0 | 0 | 0 | 0.791s |
| `com.newtermux.blocks.TerminalBlockModelTest` | 6 | 0 | 0 | 0 | 0.400s |
| `com.newtermux.features.LocalhostServerManagerTest` | 3 | 0 | 0 | 0 | 0.008s |
| `com.newtermux.features.SshProfileStoreTest` | 2 | 0 | 0 | 0 | 0.002s |
| `com.newtermux.input.InputDraftAndLayerTest` | 5 | 0 | 0 | 0 | 1.151s |
| `com.newtermux.theme.CuratedThemesTest` | 8 | 0 | 0 | 0 | 0.504s |
| `com.newtermux.update.UpdateManagerVersionTest` | 2 | 0 | 0 | 0 | 0.004s |
| `com.newtermux.workflows.WorkflowParserAndStoreTest` | 5 | 0 | 0 | 0 | 0.964s |
| `com.newtermux.workspace.WorkspaceZoomAndSearchTest` | 4 | 0 | 0 | 0 | 0.822s |
| `com.termux.app.TermuxActivityTest` | 1 | 0 | 0 | 0 | 0.002s |
| `com.termux.app.activities.DevBrowserUrlTest` | 2 | 0 | 0 | 0 | 0.020s |
| `com.termux.app.api.file.FileReceiverActivityTest` | 1 | 0 | 0 | 0 | 0.069s |
| **Total** | **44** | **0** | **0** | **0** | — |

## `:terminal-emulator:testDebugUnitTest` (`UP-TO-DATE` XML Summary)

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `com.termux.terminal.ApcTest` | 1 | 0 | 0 | 0 |
| `com.termux.terminal.ByteQueueTest` | 4 | 0 | 0 | 0 |
| `com.termux.terminal.ControlSequenceIntroducerTest` | 9 | 0 | 0 | 0 |
| `com.termux.terminal.CursorAndScreenTest` | 13 | 0 | 0 | 0 |
| `com.termux.terminal.DecSetTest` | 3 | 0 | 0 | 0 |
| `com.termux.terminal.DeviceControlStringTest` | 3 | 0 | 0 | 0 |
| `com.termux.terminal.HistoryTest` | 2 | 0 | 0 | 0 |
| `com.termux.terminal.KeyHandlerTest` | 2 | 0 | 0 | 0 |
| `com.termux.terminal.OperatingSystemControlTest` | 7 | 0 | 0 | 0 |
| `com.termux.terminal.OscShellIntegrationTest` | 5 | 0 | 0 | 0 |
| `com.termux.terminal.RectangularAreasTest` | 8 | 0 | 0 | 0 |
| `com.termux.terminal.ResizeTest` | 13 | 0 | 0 | 0 |
| `com.termux.terminal.ScreenBufferTest` | 5 | 0 | 0 | 0 |
| `com.termux.terminal.ScrollRegionTest` | 17 | 0 | 0 | 0 |
| `com.termux.terminal.TerminalRowTest` | 16 | 0 | 0 | 0 |
| `com.termux.terminal.TerminalTest` | 17 | 0 | 0 | 0 |
| `com.termux.terminal.TextStyleTest` | 5 | 0 | 0 | 0 |
| `com.termux.terminal.UnicodeInputTest` | 11 | 0 | 0 | 0 |
| `com.termux.terminal.WcWidthTest` | 9 | 0 | 0 | 0 |
| **Total** | **150** | **0** | **0** | **0** |

## `:app:assembleDebug`

Generated all 5 debug APKs under [`app/build/outputs/apk/debug/`](file:///data/data/com.termux/files/home/projects/BeTermux/app/build/outputs/apk/debug):

- `termux-app_apt-android-7-debug_arm64-v8a.apk` (`58M`)
- `termux-app_apt-android-7-debug_armeabi-v7a.apk` (`29M`)
- `termux-app_apt-android-7-debug_universal.apk` (`58M`)
- `termux-app_apt-android-7-debug_x86.apk` (`29M`)
- `termux-app_apt-android-7-debug_x86_64.apk` (`29M`)
