# Phase 4 Verification Results

## Command Run

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

- **Exit code**: `0` (`BUILD SUCCESSFUL in 47s`, 106 actionable tasks: 5 executed, 101 up-to-date)

## `:app:testDebugUnitTest`

| Suite | Tests | Failures | Errors | Skipped | Time |
|---|---:|---:|---:|---:|---:|
| `com.newtermux.blocks.BlockSelectionUiTest` | 1 | 0 | 0 | 0 | 7.941s |
| `com.newtermux.blocks.TerminalBlockModelTest` | 6 | 0 | 0 | 0 | 0.405s |
| `com.newtermux.features.LocalhostServerManagerTest` | 3 | 0 | 0 | 0 | 0.007s |
| `com.newtermux.features.SshProfileStoreTest` | 2 | 0 | 0 | 0 | 0.002s |
| `com.newtermux.input.InputDraftAndLayerTest` | 5 | 0 | 0 | 0 | 1.207s |
| `com.newtermux.theme.CuratedThemesTest` | 8 | 0 | 0 | 0 | 0.487s |
| `com.newtermux.update.UpdateManagerVersionTest` | 2 | 0 | 0 | 0 | 0.006s |
| `com.newtermux.workflows.WorkflowParserAndStoreTest` | 5 | 0 | 0 | 0 | 0.930s |
| `com.newtermux.workspace.WorkspaceZoomAndSearchTest` | 4 | 0 | 0 | 0 | 0.922s |
| `com.termux.app.TermuxActivityTest` | 1 | 0 | 0 | 0 | 0.002s |
| `com.termux.app.activities.DevBrowserUrlTest` | 2 | 0 | 0 | 0 | 0.015s |
| `com.termux.app.api.file.FileReceiverActivityTest` | 1 | 0 | 0 | 0 | 0.075s |
| **Total** | **40** | **0** | **0** | **0** | — |

## `:app:assembleDebug`

Generated all 5 debug APKs under `app/build/outputs/apk/debug/`.
