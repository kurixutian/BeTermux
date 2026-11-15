# Phase 3 Diff — Navigation & Workspace

Phase 3 implements the collapsible vertical workspace drawer, split-pane workspace with 18% inactive pane opacity reduction, persisted full UI zoom (`85%`–`130%`) preserving system accessibility `fontScale`, and zero-allocation per-block search/filter & highlighting.

## Added Files

- [`BlockSearch.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockSearch.kt): pure `BlockSearchMatcher` scanning `TerminalBuffer` rows case-insensitively and returning matching row indices and visual column ranges.
- [`SplitPaneWorkspace.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workspace/SplitPaneWorkspace.kt): adaptive portrait/landscape dual-pane workspace applying `INACTIVE_PANE_ALPHA = 0.82f` (18% opacity reduction) to inactive panes and routing click-to-focus back to the active pane.
- [`CollapsibleTabDrawer.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workspace/CollapsibleTabDrawer.kt): left-edge slide-out drawer listing terminal sessions (title, running status, last command summary, split toggle, close action routed through draft/process protection), Dev Browser / File Manager / Localhost tabs, and live UI zoom controls.
- [`WorkspaceHostBridge.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workspace/WorkspaceHostBridge.kt): attaches the drawer overlay to `android.R.id.content` cleanly without altering `TermuxActivityRootView` IME insets, and synchronizes UI zoom onto both Compose and `TerminalView`.
- [`ic_tab_drawer.xml`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/res/drawable/ic_tab_drawer.xml): flat vector drawer icon for the toolbar button.
- [`WorkspaceZoomAndSearchTest.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/test/java/com/newtermux/workspace/WorkspaceZoomAndSearchTest.kt): unit and Compose UI tests for `85%`–`130%` UI zoom clamping + OS `fontScale` preservation, `BlockSearchMatcher`, per-block search bar Filter/All UI toggling, `CollapsibleTabDrawer` session summaries/actions, and `SplitPaneWorkspace` dimming & focus routing.

## Modified Files

- [`NewTermuxSettings.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/features/NewTermuxSettings.java): added `KEY_UI_ZOOM_PERCENT` (`85..130`, default `100`) and `getUiZoomFactor`.
- [`NewTermuxComposeTheme.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/compose/NewTermuxComposeTheme.kt): added `UiZoomMath.computeZoomedDensity` multiplying `density` by the UI zoom factor while preserving `fontScale`.
- [`BlockRowsCanvas.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockRowsCanvas.kt): added `explicitAbsRows` filtered row rendering and pre-allocated `Paint` substring match highlighting.
- [`BlockWorkspaceHost.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockWorkspaceHost.kt): added per-block `BlockSearchBar`, `"Search in block"` menu action, `Ctrl+F` shortcut, `stateFlowOverride`, and `onPaneFocused` hook for split panes.
- [`BlockHostBridge.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockHostBridge.kt): integrated `SplitPaneWorkspace`, `enableSplitWithSession`, `exitSplitMode`, and `Ctrl+F` search dispatch.
- [`newtermux_toolbar.xml`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/res/layout/newtermux_toolbar.xml): added `@+id/btn_tab_drawer` button on the toolbar leading edge.
- [`TermuxTerminalViewClient.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java): scaled `TerminalView` text size using `NewTermuxSettings.getUiZoomFactor`.
- [`TermuxActivity.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/TermuxActivity.java): attached `WorkspaceHostBridge`, wired `@+id/btn_tab_drawer`, synced session lists, and refreshed zoom on resume.
- [`SettingsActivity.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/activities/SettingsActivity.kt): added `UiZoomSelector` (`85%`–`130%` presets and `−`/`+` steppers).
