# Phase 0 — Architecture & Lifecycle Audit Report

**Date:** 2026-11-19  
**Spec:** `project_workspaces_and_sidebars.md`  
**Environment tested:** Non-rooted Termux on Android 16 (`Linux localhost 6.6.118-android15-8-... aarch64`, `uid=10912(u0_a912)`, SELinux `u:r:untrusted_app_27:s0:c144,c259,c512,c768`)

---

## Deliverable 1: Shell Spawning, `setsid()`, `TerminalSession` PID/SID Exposure, and Write Boundary

### Shell Spawning & `setsid()`
- `terminal-emulator/src/main/jni/termux.c`:
  - `Java_com_termux_terminal_JNI_createSubprocess` (`L117-183`) calls `create_subprocess(...)` (`L25-115`).
  - Opens `/dev/ptmx` (`L36`), sets up `grantpt`/`unlockpt`/`ptsname_r` (`L44-50`), `IUTF8` and `TIOCSWINSZ` (`L55-63`), then `fork()` (`L65`).
  - Child process (`L71-114`):
    1. Unblocks all signals (`L73-75`) and closes master PTY (`L77`).
    2. Calls `setsid()` (`L78`) — creating a new session (`sid == pgid == pid`).
    3. Opens slave PTY without `O_NOCTTY` (`L80`), acquiring the controlling terminal.
    4. Redirects stdin/stdout/stderr (`L83-85`), closes fds `> 2` (`L87-96`), sets env + `chdir(cwd)` (`L98-101`), and `execvp(cmd, argv)` (`L108`).
  - Neither `tcsetpgrp` nor `setpgid` is called in `termux.c`; the interactive shell manages foreground/background job PGIDs within the created session (`sid`).

### `TerminalSession` PID, Handle, and SID Reachability
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java`:
  - `mHandle` (`L36`): `public final String mHandle = UUID.randomUUID().toString();`
  - `mShellPid` (`L57`): assigned in `initializeEmulator` (`L128`) and exposed via `public int getPid()` (`L318-320`); reset to `-1` in `cleanupResources` (`L251-255`).
  - `JNI.java` (`terminal-emulator/src/main/java/com/termux/terminal/JNI.java`) does **not** expose `getsid`.
  - `gradle.properties` sets `minSdkVersion=21`, while `android.system.Os.getsid(int)` requires API 29+.
  - **Reachable SID on all API levels (21+):** `/proc/<pid>/stat` field 6 (token index `3` after stripping `)` of `comm`) contains the Session ID (`sid`). `TermuxActivity.readProcStatTokens(int pid)` (`TermuxActivity.java:1246-1265`) already reads `/proc/<pid>/stat` in this exact format, and `/proc/<pid>/status` (`Uid:` line) verifies process UID ownership.

### PTY Input Write Boundary (D3) & Termination Path (D2)
- **Write boundary:** `TerminalOutput.write(String)` (`TerminalOutput.java:9-13`) and `TerminalSession.writeCodePoint(boolean, int)` (`TerminalSession.java:183-217`) both funnel into `TerminalSession.write(byte[] data, int offset, int count)` (`TerminalSession.java:178-180`), which writes into `mTerminalToProcessIOQueue` (drained by `TermSessionOutputWriter` to the PTY master fd).
  - `TerminalView` (`IME`, hardware keys, `autofill`, `TerminalEmulator.paste`, mouse events) all route through `TerminalSession.write(...)` / `writeCodePoint(...)`.
  - Note: `TerminalEmulator` also invokes `mSession.write(...)` for terminal-protocol replies (CPR/DSR/DA/OSC). An input gate at `TerminalSession.write(byte[], int, int)` covers every user/programmatic input path (and can optionally preserve an internal/protocol write path if ever needed, though a stopped process cannot read protocol replies while `T` anyway).
- **Termination path:** `TerminalSession.finishIfRunning()` (`TerminalSession.java:240-248`) currently sends `Os.kill(mShellPid, OsConstants.SIGKILL)` to `mShellPid` only — leaving child PGIDs/processes untouched if they ignore SIGHUP or were backgrounded.

---

## Deliverable 2: Empirical SID Suspension Proof (Non-Rooted Android 16)

Executed throwaway Python PTY prototypes in a non-rooted Android 16 Termux environment (`uid=10912`, `untrusted_app_27`) spawning interactive `bash -i` via `setsid()` + `TIOCSCTTY`, plus background `sleep 1000 &` and foreground jobs:

1. **Test A (`bash -i` + `sleep 1000 &` + CPU-bound `python3` loop):**
   - `BEFORE`: 3 processes in `sid=23297` across **3 distinct PGIDs**:
     - `pid=23297` (`bash`, `pgid=23297`, `sid=23297`, `state=S`)
     - `pid=23301` (`sleep`, `pgid=23301`, `sid=23297`, `state=S`)
     - `pid=23304` (`python3`, `pgid=23304`, `sid=23297`, `state=R`, `utime=68`)
   - `AFTER SIGSTOP` (per retained PID where `sid == 23297` and `Uid == 10912`):
     - All 3 transitioned to `state=T` (`python3` `utime` stayed frozen at `68`).
   - `AFTER SIGCONT`:
     - All 3 resumed (`bash`/`sleep` -> `S`, `python3` -> `R`, `utime` advanced `68 -> 88`).
   - `D2 TERMINATION FROM STOPPED STATE` (`SIGCONT` -> `SIGTERM` -> 2s poll -> `SIGKILL`):
     - `sleep` and `python3` exited on `SIGTERM`; interactive `bash` (`pid=23297`) ignored `SIGTERM` (standard interactive shell behavior) and was terminated cleanly by `SIGKILL` with 0 survivors.

2. **Test B (`bash -i` + `sleep 1000 &` + interactive TTY editor `nano`):**
   - `BEFORE`: `bash` (`pid=24583, pgid=24583, sid=24583, S`), `sleep` (`pid=24585, pgid=24585, sid=24583, S`), `nano` (`pid=24586, pgid=24586, sid=24583, S`).
   - `AFTER SIGSTOP`: All 3 verified `state=T`.
   - `AFTER SIGCONT`: All 3 verified `state=S`.

### Exit Criteria Status
- **Verified on Android 16 (non-rooted `untrusted_app_27`):** `/proc/[0-9]*/stat` & `/proc/[0-9]*/status` enumeration for same-UID processes works without root, SID-based per-PID `SIGSTOP` puts all distinct PGIDs in `T`, and `CONT -> TERM -> wait -> KILL` handles interactive `bash` ignoring `SIGTERM`.
- **Gap vs 2-device criterion:** Only **one** Android device/version (Android 16) is connected in this environment (`adb devices` returned none). Verification on a second Android version/device (e.g., Android 11–14) remains required before removing the Phase 4 hibernation feature flag's experimental/rollout gate.

---

## Deliverable 3: `FileTabViewer` Audit (Viewer vs Editor)

`app/src/main/java/com/termux/app/activities/FileTabViewer.kt` is **both an editor and a viewer**:
- **Editable in-app** (via `BasicTextField`, saving with `file.writeText(text)` + `onSaved()`):
  - `CodeTextTabViewer` (`L1352-1575`): syntax-highlighted text/code editor with line numbers, undo/redo (50 steps), coding symbols bar, line wrap, Save button. *(Note: `initialText` at `L1359` is `val` instead of `mutableStateOf`, so `isModified` does not clear after save — worth noting/fixing if touched.)*
  - `MarkdownTabViewer` (`L827-957`): preview/edit toggle + Save.
  - `HtmlTabViewer` (`L964-1078`): render/edit toggle + Save.
- **View-only in-app** (with `"Open in App"` via `FileProvider` `Intent.ACTION_VIEW` and `"Share"` via `ACTION_SEND`):
  - `ImageTabViewer`, `VideoTabViewer`, `AudioTabViewer`, `PdfTabViewer`, `DocxTabViewer`, `PptxTabViewer`, `SpreadsheetTabViewer`, `BinaryTabViewer`.
- **Directory tab:** `FolderTabViewer` (`L226-372`) browses subdirectories and supports Copy Path / Delete.
- **External editor:** `Intent.ACTION_EDIT` is not yet wired anywhere in `app/src/`. `file_manager_paths.xml` exposes `files-path` (`.` and `home`), `external-path`, `external-files-path`, and `cache-path` under `${applicationId}.fileprovider`.
- **Conclusion for D7 / Phase 3:** For `FileCategory.CODE_TEXT`, `MARKDOWN`, `HTML`, label the action **"Edit / View"** (or **"Open in Tab"**, since `FileTabViewer` opens both editable text and view-only media/docs), and additionally offer **"Open Externally"** (`ACTION_EDIT` / `ACTION_VIEW` chooser via `FileProvider` with graceful fallback for paths outside `file_manager_paths.xml`).

---

## Deliverable 4: Session Ownership & Creation Paths

- **Application scope (`TermuxApplication.java`):** Initializes `TermuxShellManager` (`termux-shared/.../TermuxShellManager.java`), which owns `mTermuxSessions` (`List<TermuxSession>`), `mTermuxTasks`, and `SHELL_ID`.
- **Service ownership (`TermuxService.java`):**
  - `createTermuxSession(ExecutionCommand)` (`L677-733`) instantiates `TermuxSession` (generating `TerminalSession.mHandle`), appends to `mShellManager.mTermuxSessions`, persists `SessionStatePersister`, notifies `mTermuxTerminalSessionActivityClient`.
  - `removeTermuxSession(TerminalSession)` (`L736-743`) / `onTermuxSessionExited(TermuxSession)` (`L747-771`) removes from `mShellManager.mTermuxSessions`, saves `SessionStatePersister`, updates notifications/activity.
  - `restoreSessionsIfNeeded()` (`L421-457`) restores from `SessionStatePersister` on cold service start.
  - `actionServiceExecute()` (`L461-527`) creates sessions from plugin/AM `ACTION_SERVICE_EXECUTE` intents.
- **Activity & UI creation/switch/close paths (`TermuxActivity.java` & `TermuxTerminalSessionActivityClient.java`):**
  1. `TermuxActivity.onServiceConnected` (`L854-906`) — initial bootstrap session or `Intent.ACTION_RUN` shortcut session.
  2. `TermuxTerminalSessionActivityClient.addNewSession(boolean, String)` (`L371-412`) — used by `showNewTabMenu` (`L1187`), `performToolbarAction(R.id.btn_new_session)` (`L2314`, called from `WorkspaceHostBridge` & `ControlDeck`), `clickDrawerCommand` (`L2364`), keyboard shortcuts (`Ctrl+Alt+C`, `ACTION_SHORTCUT_CREATE_SESSION`).
  3. `TermuxActivity.startLocalhostServer` (`L1685-1705`) — calls `mTermuxService.createTermuxSession` directly.
  4. `WorkspaceHostBridge` (`app/src/main/java/com/betermux/workspace/WorkspaceHostBridge.kt`) — attaches `CollapsibleTabDrawer` (`DrawerSessionItem`s) to `android.R.id.content` (`TermuxActivity.java:975-976`), toggled by `R.id.btn_tab_drawer`.
  5. `DeckBridge` / `ControlDeck` (`ControlDeck.kt`) — reads `act.termuxService.termuxSessions` directly.
- **Persistence gap:** `SessionStatePersister.java` persists `name`, `cwd`, `failsafe`, `resume_command` in `session_state.json`, **not** `TerminalSession.mHandle` (ephemeral UUID per process). Therefore workspace↔session reconciliation must handle both live `mHandle` association (in-memory + SharedPreferences during service lifetime) and graceful fallback / migration to Default Workspace when sessions are cold-restored with new UUID `mHandle`s!
