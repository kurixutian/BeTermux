# Phase 1b Diff — Block Visual System

Phase 1b implements the structured block model, OSC 133/7 shell integration, absolute transcript coordinate indexing (`mTotalTranscriptRows`), `TerminalRowPainter` row rendering reuse, sticky headers, multi-select actions, Comfort/Compact density toggle, hardware keyboard block navigation (`Ctrl+Up`/`Ctrl+Down`), alternate-screen pass-through, and Raw PTY toggle.

## Modified Files

- `terminal-emulator`
  - [`TerminalBuffer.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java): added monotonic `mTotalTranscriptRows` counter incremented in `scrollDownOneLine`, `ReflowMapping` / `resizeAndMap` for resize-safe coordinate remapping, null-safe `allocateFullLineIfNecessary` inside `getSelectedText`, and absolute row accessors (`getTotalTranscriptRows`, `getOldestRetainedAbsoluteRow`, `getScreenEndAbsoluteRowExclusive`, `getRowAtAbsolute`, `getSelectedTextAbsolute`).
  - [`TerminalRow.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java): changed `isBlank()` to `public boolean isBlank()` for cross-package blank-row checks.
  - [`TerminalOutput.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/main/java/com/termux/terminal/TerminalOutput.java): added `onOsc133Event(Osc133Event)` and `onOsc7CwdChanged(String)` callbacks.
  - [`TerminalEmulator.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java): added OSC 7 CWD UTF-8 `%XX` decoding (preserving `+`), OSC 133 (`A`/`B`/`C`/`D[;exit]` + `cmdline=`/`cmdline_url=`) dispatch suppressed on alternate buffer, and reflow notification dispatch.
  - [`TerminalSessionClient.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/main/java/com/termux/terminal/TerminalSessionClient.java) & [`TerminalSession.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java): plumbed OSC 133, OSC 7, and main-buffer reflow callbacks plus `attachEmulatorForTesting`.
  - [`TerminalTestCase.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/test/java/com/termux/terminal/TerminalTestCase.java): added `sendInputBytes` test helper and captured OSC callbacks.
- `terminal-view`
  - [`TerminalRenderer.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-view/src/main/java/com/termux/view/TerminalRenderer.java) & [`TerminalView.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-view/src/main/java/com/termux/view/TerminalView.java): extracted shared per-row drawing into `TerminalRowPainter` and exposed `getRowPainter()` so completed blocks reuse live terminal typography, colors, URL underlines, and selection styling.
- `app`
  - [`app/build.gradle`](file:///data/data/com.termux/files/home/projects/BeTermux/app/build.gradle): added `androidx.compose.ui:ui-test-manifest` (`debugImplementation`) and `androidx.compose.ui:ui-test-junit4` (`testImplementation`).
  - [`NewTermuxSettings.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/features/NewTermuxSettings.java): added `KEY_BLOCK_VIEW_ENABLED`, `KEY_BLOCK_DENSITY`, `DENSITY_COMFORT`, and `DENSITY_COMPACT`.
  - [`activity_termux.xml`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/res/layout/activity_termux.xml): added `@+id/block_workspace_container` `ComposeView` inside `terminal_container`.
  - [`TermuxInstaller.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/TermuxInstaller.java): hooked idempotent `ShellIntegrationInstaller` provisioning for bash and zsh.
  - [`TermuxTerminalSessionActivityClient.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java): forwarded text changes, OSC 133/7, reflow, active session switches, and session removal to `BlockHostBridge`.
  - [`TermuxTerminalViewClient.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java): added `Ctrl+Up`/`Ctrl+Down` hardware block navigation and delegated text tap link resolution to `TerminalTapResolver`.
  - [`TermuxActivity.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/TermuxActivity.java): wired `BlockHostBridge`, added `"Toggle Block / Raw PTY"` menu action, and synchronized terminal vs. block visibility.
  - [`SettingsActivity.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/activities/SettingsActivity.kt): added Block View switch and Comfort/Compact segmented density selector.

## Added Files

- [`Osc133Event.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/main/java/com/termux/terminal/Osc133Event.java)
- [`OscShellIntegrationTest.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-emulator/src/test/java/com/termux/terminal/OscShellIntegrationTest.java)
- [`TerminalRowPainter.java`](file:///data/data/com.termux/files/home/projects/BeTermux/terminal-view/src/main/java/com/termux/view/TerminalRowPainter.java)
- [`TerminalBlock.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/TerminalBlock.kt)
- [`TerminalTapResolver.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/TerminalTapResolver.kt)
- [`BlockSessionController.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockSessionController.kt)
- [`ShellIntegrationInstaller.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/ShellIntegrationInstaller.kt)
- [`BlockActions.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockActions.kt)
- [`BlockRowsCanvas.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockRowsCanvas.kt)
- [`BlockWorkspaceHost.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockWorkspaceHost.kt)
- [`BlockHostBridge.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockHostBridge.kt)
- [`TerminalBlockModelTest.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/test/java/com/newtermux/blocks/TerminalBlockModelTest.kt)
- [`BlockSelectionUiTest.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/test/java/com/newtermux/blocks/BlockSelectionUiTest.kt)
