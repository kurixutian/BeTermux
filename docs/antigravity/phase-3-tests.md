# Phase 3 Verification Results

## Command Run

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

- **Exit code**: `0` (`BUILD SUCCESSFUL in 1m 12s`, 106 actionable tasks: 6 executed, 100 up-to-date)

## `:app:testDebugUnitTest`

| Suite | Tests | Failures | Errors | Skipped | Time |
|---|---:|---:|---:|---:|---:|
| `com.newtermux.blocks.BlockSelectionUiTest` | 1 | 0 | 0 | 0 | 11.494s |
| `com.newtermux.blocks.TerminalBlockModelTest` | 6 | 0 | 0 | 0 | 0.569s |
| `com.newtermux.features.LocalhostServerManagerTest` | 3 | 0 | 0 | 0 | 0.016s |
| `com.newtermux.features.SshProfileStoreTest` | 2 | 0 | 0 | 0 | 0.002s |
| `com.newtermux.input.InputDraftAndLayerTest` | 5 | 0 | 0 | 0 | 1.741s |
| `com.newtermux.theme.CuratedThemesTest` | 8 | 0 | 0 | 0 | 0.559s |
| `com.newtermux.update.UpdateManagerVersionTest` | 2 | 0 | 0 | 0 | 0.005s |
| `com.newtermux.workspace.WorkspaceZoomAndSearchTest` | 4 | 0 | 0 | 0 | 1.267s |
| `com.termux.app.TermuxActivityTest` | 1 | 0 | 0 | 0 | 0.003s |
| `com.termux.app.activities.DevBrowserUrlTest` | 2 | 0 | 0 | 0 | 0.028s |
| `com.termux.app.api.file.FileReceiverActivityTest` | 1 | 0 | 0 | 0 | 0.071s |
| **Total** | **35** | **0** | **0** | **0** | — |

## `:app:assembleDebug`

Generated all 5 debug APKs under `app/build/outputs/apk/debug/`.
