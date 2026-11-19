# BeTermux Dual Sidebars, Project Workspaces & Sound Hibernation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Phases 1–4 of `project_workspaces_and_sidebars.md`: Application-scoped `WorkspaceManager` with identity-only JSON persistence, Dual Sidebar UX (workspace-scoped left drawer + right file explorer toggle), lazy IDE-style `RightFileDrawer`, and SID-based workspace hibernation behind a single rollback flag.

**Architecture:** `WorkspaceManager` lives in Application scope (`TermuxApplication`) backed by `SharedPreferences` JSON persistence (`WorkspaceStore`) for identity/order/session associations, while `WorkspaceSleepState` remains runtime-only. `WorkspaceHostBridge` connects `WorkspaceManager` and `WorkspaceHibernationController` to `TermuxActivity`, rendering `CollapsibleTabDrawer` (left) and `RightDrawerOverlay`/`RightFileDrawer` (right) in Compose using `BeTermuxComposeTheme` and `LocalWarpPalette`. Hibernation (`ProcSessionController` + `TerminalSession` input gating) is gated behind `BeTermuxSettings.isWorkspaceHibernationEnabled` and uses `/proc/<pid>/stat` SID enumeration + UID verification + `SIGSTOP`/`SIGCONT` + `CONT -> TERM -> wait -> KILL`.

**Tech Stack:** Kotlin 2.2.20, Java 11, Jetpack Compose (BOM 2025.06.01, Material 3), Android SDK 36 (`minSdkVersion=21`), Gradle 9.2.1, JUnit 4 + Robolectric 4.14.1 + Compose UI Test.

**Spec:** `/data/data/com.termux/files/home/projects/BeTermux/project_workspaces_and_sidebars.md` (and Phase 0 findings in `docs/phase-0-audit.md`)

## Global Constraints

- **D1 (SID-wide suspension):** Identify session processes via SID (`/proc/<pid>/stat` field 6 / token index 3 after `)`), verify UID from `/proc/<pid>/status` (`Uid:`) matches app UID, never signal `pid <= 1` or `pgid <= 0`, hold workspace lock during suspend/resume, send `SIGSTOP` per PID, verify every PID transitions to state `'T'`; fail closed to `WorkspaceSleepState.ERROR` if `/proc` enumeration fails or verification is partial.
- **D2 (Termination):** `SIGCONT` all verified SID PIDs -> `SIGTERM` -> bounded poll up to `5000ms` -> `SIGKILL` survivors -> verify all gone (else `ERROR`). Applied to tab close, explicit kill, Remove Workspace (when termination selected), and cold-start leaked stopped process cleanup.
- **D3 (Input gating):** Gate `TerminalSession.write(byte[], int, int)` at the session boundary when `isInputGated()` is true (`SLEEPING`, `SUSPENDING`, `RESUMING`), covering IME, hardware keyboard, paste, autofill/accessibility, and programmatic writes.
- **D4 (Persistence identity-only):** Persist `id`, `name`, `rootPath`, `sortOrder`, `sessionHandles` in `SharedPreferences` JSON. Never deserialize `WorkspaceSleepState` as truth. Drop records with empty `id` or `rootPath`. Reconcile live `TerminalSession` handles on startup.
- **D5 (Cross-workspace tap):** Selecting a terminal in another workspace switches active workspace, selects the terminal, and wakes that workspace if sleeping before accepting input.
- **D6 (Remove workspace):** Default migrates sessions to `"default"` (`Default Workspace`) without deleting `rootPath` on disk; termination is an explicit separate choice following D2.
- **D7 (File explorer):** Lazy per-directory expansion on `Dispatchers.IO`, per-node loading/error state, symlink-loop protection (canonical path ancestor tracking + max depth `32`), hidden-file toggle, create/delete, "Open Terminal Here", honest `"Edit / View"` vs `"View"` labeling (per Phase 0 audit of `FileTabViewer`), and explicit `"Open Externally"` (`ACTION_EDIT` / `ACTION_VIEW` via `FileProvider`).
- **D8 (Data model):** `enum class WorkspaceSleepState { AWAKE, SUSPENDING, SLEEPING, RESUMING, ERROR }` and `data class ProjectWorkspace(val id: String, val name: String, val rootPath: String, val sortOrder: Int, val sessionHandles: List<String>)`.
- **Rollback flags:** Independent per-phase toggles in `BeTermuxSettings`: `KEY_WORKSPACES_ENABLED`, `KEY_DUAL_SIDEBARS_ENABLED`, `KEY_RIGHT_FILE_EXPLORER_ENABLED`, `KEY_WORKSPACE_HIBERNATION_ENABLED` (single kill-switch for all signal sending & zzz path).
- **UI craft floor:** Use `BeTermuxComposeTheme` / `LocalWarpPalette` / `rememberReduceMotion()`. Zero drop shadows or blurs; 1dp borders; `48dp` icon touch targets; explicit `contentDescription` and `testTag` on interactive controls; never delete or modify unrelated git working-tree changes.

## Review Focus

1. **Interactive shell ignoring `SIGTERM`:** Interactive `bash` ignores `SIGTERM`, so D2 termination from `SLEEPING` must verify `SIGCONT -> SIGTERM -> bounded wait -> SIGKILL` actually terminates the shell and children rather than leaving an orphaned shell.
2. **Ephemeral `TerminalSession.mHandle` on cold start:** `SessionStatePersister` creates new UUID `mHandle`s on cold service restore; startup reconciliation must prune stale persisted handles and adopt unassociated live sessions into Default Workspace instead of leaving ghost handles or unmapped tabs.
3. **Rapid double-tap on Sleep/Wake or Cross-Workspace Switch:** Concurrent calls must serialize on the per-workspace lock (`Mutex`) without transitioning `SUSPENDING -> SLEEPING` if cancelled or leaving `mInputGated` stuck `true` on `ERROR`.
4. **Symlink cycles & inaccessible roots in `RightFileDrawer`:** Expanding `a -> b -> a` or a deleted/unreadable `rootPath` (`ENOENT`, `EACCES`, unmounted external storage) must stop at visited canonical path / friendly error state without stack overflow or main-thread I/O.
5. **Paths outside `file_manager_paths.xml` on "Open Externally":** Invoking `FileProvider.getUriForFile` on `/data/local/tmp/...` or `/` throws `IllegalArgumentException`; handle gracefully with an actionable message instead of crashing.

---

### Task 1: Workspace Models, Persistence Store, Feature Flags, and Application-Scope `WorkspaceManager` (Phase 1A)

**Files:**
- Create: `app/src/main/java/com/betermux/workspace/WorkspaceModels.kt`
- Create: `app/src/main/java/com/betermux/workspace/WorkspaceStore.kt`
- Create: `app/src/main/java/com/betermux/workspace/WorkspaceManager.kt`
- Modify: `app/src/main/java/com/betermux/features/BeTermuxSettings.java:30-37,250-258`
- Modify: `app/src/main/java/com/termux/app/TermuxApplication.java:40-50`
- Test: `app/src/test/java/com/betermux/workspace/WorkspaceManagerTest.kt`

**Interfaces:**
- Consumes: `TermuxConstants.TERMUX_HOME_DIR_PATH`, `Context.getSharedPreferences`
- Produces:
  - `enum class WorkspaceSleepState { AWAKE, SUSPENDING, SLEEPING, RESUMING, ERROR }`
  - `data class ProjectWorkspace(val id: String, val name: String, val rootPath: String, val sortOrder: Int, val sessionHandles: List<String>)`
  - `data class WorkspaceStateSnapshot(val workspaces: List<ProjectWorkspace>, val activeWorkspaceId: String, val sleepStates: Map<String, WorkspaceSleepState>)`
  - `object WorkspaceManager`: `init(context: Context, defaultRootPath: String = ...)`, `resetForTesting(context: Context, defaultRootPath: String)`, `state: StateFlow<WorkspaceStateSnapshot>`, `createWorkspace(name: String, rootPath: String): ProjectWorkspace`, `renameWorkspace(workspaceId: String, newName: String)`, `removeWorkspace(workspaceId: String): List<String>` (returns migrated session handles), `removeWorkspaceAndClearSessions(workspaceId: String): List<String>`, `setActiveWorkspace(workspaceId: String): Boolean`, `associateSession(sessionHandle: String, workspaceId: String? = null)`, `dissociateSession(sessionHandle: String)`, `workspaceForSession(sessionHandle: String): ProjectWorkspace?`, `activeWorkspace(): ProjectWorkspace`, `reconcileStartupSessions(liveHandles: Collection<String>)`, `getSleepState(workspaceId: String): WorkspaceSleepState`, `setSleepState(workspaceId: String, sleepState: WorkspaceSleepState)`
  - `BeTermuxSettings`: `isWorkspacesEnabled(Context)`, `setWorkspacesEnabled(Context, boolean)`, `isDualSidebarsEnabled(Context)`, `setDualSidebarsEnabled(Context, boolean)`, `isRightFileExplorerEnabled(Context)`, `setRightFileExplorerEnabled(Context, boolean)`, `isWorkspaceHibernationEnabled(Context)`, `setWorkspaceHibernationEnabled(Context, boolean)` (all default `true`)

- [ ] **Step 1: Write the failing test in `app/src/test/java/com/betermux/workspace/WorkspaceManagerTest.kt`**

```kotlin
package com.betermux.workspace

import android.content.Context
import com.betermux.features.BeTermuxSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE).edit().clear().commit()
        WorkspaceManager.resetForTesting(context, "/data/data/com.termux/files/home")
    }

    @Test
    fun coldStartCreatesDefaultWorkspaceAndMigratesOrphanedLiveSessions() {
        WorkspaceManager.reconcileStartupSessions(listOf("h1", "h2"))
        val snap = WorkspaceManager.state.value
        assertEquals(1, snap.workspaces.size)
        assertEquals(DEFAULT_WORKSPACE_ID, snap.workspaces[0].id)
        assertEquals(listOf("h1", "h2"), snap.workspaces[0].sessionHandles)
        assertEquals(WorkspaceSleepState.AWAKE, snap.sleepStates[DEFAULT_WORKSPACE_ID])
    }

    @Test
    fun invalidPersistedRecordsDroppedAndStaleHandlesPruned() {
        val prefs = context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE)
        prefs.edit().putString("workspaces_json", """
            [
              {"id":"","name":"Bad","rootPath":"/tmp","sortOrder":0,"sessionHandles":["x"]},
              {"id":"w1","name":"App","rootPath":"/proj/app","sortOrder":1,"sessionHandles":["live1","dead1"],"isSleeping":true},
              {"id":"w2","name":"NoRoot","rootPath":"","sortOrder":2,"sessionHandles":["y"]}
            ]
        """.trimIndent()).commit()

        WorkspaceManager.resetForTesting(context, "/home")
        WorkspaceManager.reconcileStartupSessions(listOf("live1", "live2"))
        val snap = WorkspaceManager.state.value
        assertEquals(2, snap.workspaces.size) // default + w1
        val w1 = snap.workspaces.first { it.id == "w1" }
        assertEquals(listOf("live1"), w1.sessionHandles)
        assertEquals(WorkspaceSleepState.AWAKE, snap.sleepStates["w1"])
        val def = snap.workspaces.first { it.id == DEFAULT_WORKSPACE_ID }
        assertEquals(listOf("live2"), def.sessionHandles)
    }

    @Test
    fun removingWorkspaceMigratesSessionsToDefaultAndProtectsDefault() {
        val w = WorkspaceManager.createWorkspace("API", "/tmp/api")
        WorkspaceManager.associateSession("s1", w.id)
        val migrated = WorkspaceManager.removeWorkspace(w.id)
        assertEquals(listOf("s1"), migrated)
        val snap = WorkspaceManager.state.value
        assertFalse(snap.workspaces.any { it.id == w.id })
        assertTrue(snap.workspaces.first { it.id == DEFAULT_WORKSPACE_ID }.sessionHandles.contains("s1"))
        assertEquals(emptyList<String>(), WorkspaceManager.removeWorkspace(DEFAULT_WORKSPACE_ID))
    }

    @Test
    fun rollbackFeatureFlagsToggleIndependently() {
        assertTrue(BeTermuxSettings.isWorkspacesEnabled(context))
        BeTermuxSettings.setWorkspaceHibernationEnabled(context, false)
        assertFalse(BeTermuxSettings.isWorkspaceHibernationEnabled(context))
        assertTrue(BeTermuxSettings.isWorkspacesEnabled(context))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.WorkspaceManagerTest"`
Expected: FAIL with unresolved references `WorkspaceManager`, `DEFAULT_WORKSPACE_ID`, `WorkspaceSleepState`.

- [ ] **Step 3: Implement `WorkspaceModels.kt`, `WorkspaceStore.kt`, `WorkspaceManager.kt`, `BeTermuxSettings.java`, and `TermuxApplication.java`**
- `WorkspaceModels.kt`: define `DEFAULT_WORKSPACE_ID = "default"`, `DEFAULT_WORKSPACE_NAME = "Default"`, `WorkspaceSleepState`, `ProjectWorkspace`, `WorkspaceStateSnapshot`.
- `WorkspaceStore.kt`: JSON read/write to `SharedPreferences("betermux_workspaces")` (`workspaces_json`, `active_workspace_id`). Validate every parsed item: drop records with blank `id` or blank `rootPath`; deduplicate `sessionHandles`; ignore any unknown/legacy `isSleeping` field (D4).
- `WorkspaceManager.kt`: thread-safe synchronized mutations + `MutableStateFlow<WorkspaceStateSnapshot>`. Always ensure `DEFAULT_WORKSPACE_ID` exists (created at `sortOrder = 0` with `defaultRootPath`). `associateSession` removes handle from any previous workspace before adding to target. `reconcileStartupSessions` prunes handles not in `liveHandles`, assigns any unmapped `liveHandles` to `DEFAULT_WORKSPACE_ID`, and resets all `sleepStates` to `WorkspaceSleepState.AWAKE`.
- `BeTermuxSettings.java`: add the 4 boolean feature flags (`KEY_WORKSPACES_ENABLED`, `KEY_DUAL_SIDEBARS_ENABLED`, `KEY_RIGHT_FILE_EXPLORER_ENABLED`, `KEY_WORKSPACE_HIBERNATION_ENABLED`), all defaulting to `true`.
- `TermuxApplication.java`: call `WorkspaceManager.init(this)` right after `TermuxShellManager.init(context)`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.WorkspaceManagerTest"`
Expected: PASS (4/4 tests).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/betermux/workspace/WorkspaceModels.kt \
  app/src/main/java/com/betermux/workspace/WorkspaceStore.kt \
  app/src/main/java/com/betermux/workspace/WorkspaceManager.kt \
  app/src/main/java/com/betermux/features/BeTermuxSettings.java \
  app/src/main/java/com/termux/app/TermuxApplication.java \
  app/src/test/java/com/betermux/workspace/WorkspaceManagerTest.kt
git commit -m "feat(workspace): add WorkspaceManager, models, JSON store, and phase flags"
```

---

### Task 2: Thread Workspace Association & Startup Reconciliation across Service & Activity (Phase 1B)

**Files:**
- Modify: `app/src/main/java/com/termux/app/TermuxService.java:130,421-457,677-771`
- Modify: `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java:371-412,456-475`
- Modify: `app/src/main/java/com/termux/app/TermuxActivity.java:854-906,1651-1705,2329-2345`
- Test: `app/src/test/java/com/betermux/workspace/WorkspaceSessionIntegrationTest.kt`

**Interfaces:**
- Consumes: `WorkspaceManager`, `BeTermuxSettings.isWorkspacesEnabled`
- Produces:
  - `TermuxActivity.getSessionsForActiveWorkspace(): List<TermuxSession>`
  - `TermuxActivity.switchToWorkspace(workspaceId: String)` (sets active workspace, selects its first live session or creates/switches terminal view cleanly)
  - `TermuxService.createTermuxSession(...)` automatically registers `mHandle` with `WorkspaceManager.associateSession(...)` (unless `pendingWorkspaceId` is specified via `ExecutionCommand` or active workspace), and `onTermuxSessionExited(...)` calls `WorkspaceManager.dissociateSession(...)`.

- [ ] **Step 1: Write failing test in `app/src/test/java/com/betermux/workspace/WorkspaceSessionIntegrationTest.kt`**
Verify:
1. Active workspace `rootPath` is chosen for new sessions when `currentSession` belongs to a different workspace or is null, and new session handle associates with the active workspace.
2. `filterSessionsForWorkspace(allHandles, activeWorkspace)` filters cleanly when `isWorkspacesEnabled == true`, and returns all sessions as a flat list when `isWorkspacesEnabled == false` (Phase 1 rollback).
3. `reconcileStartupSessions` after simulated process death clears dead handles and migrates restored sessions to Default Workspace.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.WorkspaceSessionIntegrationTest"`
Expected: FAIL.

- [ ] **Step 3: Implement workspace session threading in `TermuxService.java`, `TermuxTerminalSessionActivityClient.java`, `TermuxActivity.java`**
- In `TermuxService.java`:
  - `createTermuxSession(ExecutionCommand)`: after adding `newTermuxSession` to `mShellManager.mTermuxSessions`, call `WorkspaceManager.associateSession(newTermuxSession.getTerminalSession().mHandle)`.
  - `onTermuxSessionExited(TermuxSession)`: call `WorkspaceManager.dissociateSession(termuxSession.getTerminalSession().mHandle)`.
  - `restoreSessionsIfNeeded()`: after restoring snapshots, collect live handles from `mShellManager.mTermuxSessions` and call `WorkspaceManager.reconcileStartupSessions(liveHandles)`.
- In `TermuxTerminalSessionActivityClient.java`:
  - `addNewSession(boolean, String)`: when `BeTermuxSettings.isWorkspacesEnabled(mActivity)` and active workspace has a valid directory `rootPath`, if there is no `currentSession` in that workspace, default `workingDirectory` to active workspace's `rootPath`; after creating `newTermuxSession`, ensure `WorkspaceManager.associateSession(newTermuxSession.getTerminalSession().mHandle)` (it's already in `TermuxService.createTermuxSession`, and explicit association guarantees active workspace).
  - `removeFinishedSession`: `onTermuxSessionExited` handles dissociation; when picking the next session to focus after removal, prefer a remaining session in the active workspace if available.
- In `TermuxActivity.java`:
  - `onServiceConnected`: call `WorkspaceManager.reconcileStartupSessions(collectLiveHandles())` so live sessions always match `WorkspaceManager`.
  - Add helper `getVisibleTermuxSessions()` (returns workspace-filtered sessions if `BeTermuxSettings.isWorkspacesEnabled(this)`, else all `mTermuxService.getTermuxSessions()`).
  - Add `switchToWorkspace(String workspaceId)` and `selectOrCreateWorkspaceSession(String workspaceId)`.

- [ ] **Step 4: Run tests to verify pass**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.*"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/termux/app/TermuxService.java \
  app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java \
  app/src/main/java/com/termux/app/TermuxActivity.java \
  app/src/test/java/com/betermux/workspace/WorkspaceSessionIntegrationTest.kt
git commit -m "feat(workspace): thread workspace associations across service and activity lifecycle"
```

---

### Task 3: Dual Sidebar UX — Left Workspaces + Right Drawer Shell & Toolbar Toggle (Phase 2)

**Files:**
- Modify: `app/src/main/java/com/betermux/workspace/CollapsibleTabDrawer.kt`
- Create: `app/src/main/java/com/betermux/workspace/RightDrawerOverlay.kt`
- Modify: `app/src/main/java/com/betermux/workspace/WorkspaceHostBridge.kt`
- Modify: `app/src/main/java/com/termux/app/TermuxActivity.java:1071-1094,1868-2176,2953-2969`
- Modify: `app/src/main/res/layout/betermux_toolbar.xml:35-47` & `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/betermux/workspace/DualSidebarUiTest.kt`

**Interfaces:**
- Consumes: `WorkspaceManager`, `BeTermuxSettings.isDualSidebarsEnabled`, `BeTermuxSettings.isRightFileExplorerEnabled`, `BeTermuxSettings.isWorkspaceHibernationEnabled`
- Produces:
  - `data class WorkspaceDrawerItem(val id: String, val name: String, val rootPath: String, val sessionCount: Int, val sleepState: WorkspaceSleepState, val isActive: Boolean, val isDefault: Boolean)`
  - `CollapsibleTabDrawer(...)`: accepts `workspaces: List<WorkspaceDrawerItem>`, `hibernationEnabled: Boolean`, `onSelectWorkspace: (String) -> Unit`, `onAddWorkspace: (String, String) -> Unit`, `onToggleSleepWorkspace: (String) -> Unit`, `onRemoveWorkspace: (String, Boolean) -> Unit`, `allWorkspaceSessions: Map<String, List<DrawerSessionItem>>` (or scoped `sessions` + optional switcher), overflow `⋮` `DropdownMenu` (Sleep/Wake, Remove) + long-press shortcut, `AddProjectWorkspaceDialog`, `RemoveWorkspaceDialog` (Migrate to Default vs Terminate sessions).
  - `RightDrawerOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit)`
  - `WorkspaceHostBridge`: `openRightDrawer()`, `closeRightDrawer()`, `toggleRightDrawer()`, `isRightDrawerOpen(): Boolean`

- [ ] **Step 1: Write failing UI tests in `app/src/test/java/com/betermux/workspace/DualSidebarUiTest.kt`**
Test using `createComposeRule()`:
1. Left drawer renders `WORKSPACES` section with `+ Add Project` button (`testTag("drawer_add_workspace")`), workspace rows with name, session count, sleep badge (`zzz` only when `SLEEPING`, honest `Suspending…`/`Waking…`/`Error` badges), and `⋮` overflow button (`testTag("workspace_overflow_<id>")`) opening `Sleep`/`Wake` and `Remove`.
2. Tapping a terminal in another workspace invokes `onSelectSession` -> triggers cross-workspace switch + wake callback.
3. `RightDrawerOverlay` intercepts Back via `BackHandler` and closes right drawer (`onDismiss` invoked).
4. All interactive elements have `contentDescription` / semantics.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.DualSidebarUiTest"`
Expected: FAIL.

- [ ] **Step 3: Implement Dual Sidebar UX**
- Update `CollapsibleTabDrawer.kt`:
  - Fix `if (!visible) return` early return so `AnimatedVisibility` sits inside a `if (visible || ...)` or `AnimatedVisibility` wraps the scrim+drawer cleanly without suppressing exit transitions; keep `BackHandler(enabled = visible)`.
  - Add `WORKSPACES` section above `TERMINAL SESSIONS`: `+ Add Project` button, `WorkspaceRow`s with name, rootPath subtitle, session count pill, honest sleep badge (`SLEEPING` -> `zzz`, `SUSPENDING` -> `Sleeping…`, `RESUMING` -> `Waking…`, `ERROR` -> `Error`), and `48dp` `IconButton` overflow `⋮` menu (`Sleep`/`Wake` when `hibernationEnabled`, `Remove` when `!isDefault`) plus `combinedClickable(onLongClick = ...)` opening the same menu.
  - Scope `TERMINAL SESSIONS` list to the active workspace (plus optional collapsible "Other Workspaces" quick-jump rows or workspace-row tap so cross-workspace terminal tap is 1 tap away!).
  - Add inline/dialog `AddProjectWorkspaceDialog` (name + rootPath with validation) and `RemoveWorkspaceDialog` (explains sessions move to Default and project directory is never deleted; optional checkbox/action "Terminate running sessions instead").
- Create `RightDrawerOverlay.kt`: right-aligned `AnimatedVisibility(slideInHorizontally { it } / slideOutHorizontally { it })` respecting `rememberReduceMotion()`, scrim, `BackHandler(enabled = visible) { onDismiss() }`, `280.dp..340.dp` width, `1.dp` border (`LocalWarpPalette`).
- Update `WorkspaceHostBridge.kt`:
  - Observe `WorkspaceManager.state` and `activity.termuxService`.
  - Host both `CollapsibleTabDrawer` (left) and `RightDrawerOverlay` (right, showing placeholder/Project File Explorer content wired in Task 4).
  - When `isRightDrawerOpenFlow` changes, call `activity.setTabBarSuppressedForRightDrawer(open)` to hide `session_tabs_scroll` while the right drawer is open (preventing terminal resize storms).
- Update `TermuxActivity.java`:
  - `btn_files` click: if `BeTermuxSettings.isDualSidebarsEnabled(this) && BeTermuxSettings.isRightFileExplorerEnabled(this)`, toggle right drawer (`mWorkspaceHostBridge.toggleRightDrawer()`); else keep existing `PopupMenu` (rollback path!). Update `btn_files` active tint / `contentDescription`.
  - `populateTabContainer`: filter terminal tabs to active workspace when `BeTermuxSettings.isWorkspacesEnabled(this)`.
  - `onBackPressed`: if `mWorkspaceHostBridge.isRightDrawerOpen()` -> `closeRightDrawer()` first; else if left drawer open -> close left drawer; else existing back chain.
  - `switchToSession` / `switchToWorkspaceAndSession`: implement D5 (change active workspace -> select terminal -> request wake if sleeping -> switch to terminal tab).

- [ ] **Step 4: Run tests and `:app:assembleDebug --offline`**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.*"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/betermux/workspace/CollapsibleTabDrawer.kt \
  app/src/main/java/com/betermux/workspace/RightDrawerOverlay.kt \
  app/src/main/java/com/betermux/workspace/WorkspaceHostBridge.kt \
  app/src/main/java/com/termux/app/TermuxActivity.java \
  app/src/main/res/layout/betermux_toolbar.xml \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/betermux/workspace/DualSidebarUiTest.kt
git commit -m "feat(ui): implement dual sidebars, workspace drawer, and right drawer overlay"
```

---

### Task 4: Project File Explorer (`RightFileDrawer.kt` + `FileTreeRepository.kt`) (Phase 3)

**Files:**
- Create: `app/src/main/java/com/betermux/workspace/FileTreeModel.kt`
- Create: `app/src/main/java/com/betermux/workspace/RightFileDrawer.kt`
- Modify: `app/src/main/java/com/betermux/workspace/WorkspaceHostBridge.kt`
- Modify: `app/src/main/java/com/termux/app/activities/FileTabViewer.kt:1359-1425,1715-1745`
- Test: `app/src/test/java/com/betermux/workspace/FileTreeRepositoryAndDrawerTest.kt`

**Interfaces:**
- Consumes: `categorizeFile(File)` from `FileTabViewer.kt`, `FileProvider`, `WorkspaceManager`
- Produces:
  - `enum class FileTreeErrorType { ENOENT, EACCES, UNMOUNTED_STORAGE, STORAGE_PERMISSION_MISSING, IO_ERROR }`
  - `data class FileTreeNode(val path: String, val name: String, val isDirectory: Boolean, val isSymlink: Boolean, val depth: Int, val isExpanded: Boolean = false, val isLoading: Boolean = false, val error: FileTreeError? = null, val children: List<FileTreeNode> = emptyList(), val symlinkCycleDetected: Boolean = false)`
  - `object FileTreeRepository`: `suspend fun loadChildren(dir: File, depth: Int, showHidden: Boolean, ancestorCanonicalPaths: Set<String>, maxDepth: Int = 32): Result<List<FileTreeNode>>`, `suspend fun createFile(parent: File, name: String): Result<File>`, `suspend fun createDirectory(parent: File, name: String): Result<File>`, `suspend fun deleteEntry(target: File): Result<Unit>`
  - `RightFileDrawer(...)`: lazy directory tree, Reload, Close, Show Hidden toggle, `+ File` / `+ Folder`, per-node loading/error states, row menu (`Edit / View` or `View`, `Open Externally`, `Copy Path`, `Open Terminal Here`, `Delete`), friendly recoverable error cards.
  - `FileTabViewer.kt`: `openExternalEditor(context: Context, file: File): Boolean` (uses `Intent.ACTION_EDIT` with `FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION` via `FileProvider`, falling back to `ACTION_VIEW` and returning `false` cleanly if `FileProvider` rejects the path or no activity resolves).

- [ ] **Step 1: Write failing tests in `app/src/test/java/com/betermux/workspace/FileTreeRepositoryAndDrawerTest.kt`**
Test:
1. `loadChildren` loads lazily (single directory level only, does not recurse into subdirectories), sorts directories before files, filters hidden `.dot` files unless `showHidden = true`.
2. Symlink cycle protection: when `ancestorCanonicalPaths` already contains child's canonical path (or `depth >= maxDepth`), marks `symlinkCycleDetected = true` without infinite recursion.
3. Missing `rootPath` (`ENOENT`) and unreadable dir return structured `FileTreeError` (`ENOENT` / `EACCES` / `UNMOUNTED_STORAGE` for `/storage/emulated` when unmounted or lacking permission) without crashing.
4. `RightFileDrawer` UI test: expanding a directory shows loading/children, tapping Reload refreshes deleted files, `Open Terminal Here`, `Copy Path`, and `Edit / View` vs `View` labels reflect `categorizeFile`.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.FileTreeRepositoryAndDrawerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `FileTreeModel.kt`, `RightFileDrawer.kt`, `FileTabViewer.kt` external editor helper, and wire into `WorkspaceHostBridge.kt`**
- Ensure all filesystem calls run inside `withContext(Dispatchers.IO)`.
- Detect symlink via `Files.isSymbolicLink(file.toPath())` (or `canonicalPath != absolutePath` fallback on API < 26 since `minSdk=21`!). Check `canonicalPath in ancestorCanonicalPaths` and `depth >= 32` to guard symlink loops cleanly.
- Friendly error mapping for `ENOENT`, `EACCES`, unmounted storage (`/storage/...` or `/sdcard` when `Environment.getExternalStorageState()` != `MEDIA_MOUNTED`), and missing `termux-setup-storage` (`~/storage/...` when `PermissionUtils.checkStoragePermission` is false, offering a quick button/hint to run `termux-setup-storage` or grant permission).
- Fix `CodeTextTabViewer` `initialText` in `FileTabViewer.kt` (`remember { mutableStateOf(initial) }`) so `isModified` resets after Save, and add `openExternalEditor(context, file)`.

- [ ] **Step 4: Run tests**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.*"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/betermux/workspace/FileTreeModel.kt \
  app/src/main/java/com/betermux/workspace/RightFileDrawer.kt \
  app/src/main/java/com/betermux/workspace/WorkspaceHostBridge.kt \
  app/src/main/java/com/termux/app/activities/FileTabViewer.kt \
  app/src/test/java/com/betermux/workspace/FileTreeRepositoryAndDrawerTest.kt
git commit -m "feat(files): add lazy RightFileDrawer project explorer with symlink and error guards"
```

---

### Task 5: Session-Boundary Input Gating (D3) & SID-Based Process Control Engine (Phase 4A)

**Files:**
- Modify: `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java:55-65,175-225`
- Create: `app/src/main/java/com/betermux/workspace/ProcSessionController.kt`
- Create: `app/src/main/java/com/betermux/workspace/WorkspaceHibernationController.kt`
- Test: `terminal-emulator/src/test/java/com/termux/terminal/TerminalSessionInputGateTest.java`
- Test: `app/src/test/java/com/betermux/workspace/WorkspaceHibernationTest.kt`

**Interfaces:**
- Consumes: `TerminalSession.getPid()`, `TerminalSession.setInputGated(boolean)`, `WorkspaceManager`, `BeTermuxSettings.isWorkspaceHibernationEnabled`
- Produces:
  - `TerminalSession.setInputGated(boolean gated)`, `TerminalSession.isInputGated(): boolean` (blocks `write(byte[], int, int)` when `mInputGated` is `true`)
  - `interface ProcessOps` (injectable for deterministic unit tests + real `OsProcessOps` using `/proc` + `android.system.Os.kill`)
  - `data class ProcStatInfo(val pid: Int, val comm: String, val state: Char, val ppid: Int, val pgid: Int, val sid: Int, val uid: Int, val startTimeTicks: Long)`
  - `object ProcSessionController`: `parseProcStat(pid: Int, rawStat: String, uid: Int): ProcStatInfo?`, `findSessionProcesses(sid: Int, ownerUid: Int, ops: ProcessOps): ProcEnumerationResult`, `suspendSession(shellPid: Int, ownerUid: Int, ops: ProcessOps): SuspendResult`, `resumeSession(recordedPids: List<Int>, sid: Int, ownerUid: Int, ops: ProcessOps): ResumeResult`, `terminateSession(recordedPids: List<Int>, sid: Int, ownerUid: Int, timeoutMs: Long = 5000L, ops: ProcessOps): TerminateResult`
  - `class WorkspaceHibernationController`: `suspend fun sleepWorkspace(workspaceId: String, sessions: List<TerminalSession>): WorkspaceSleepState`, `suspend fun wakeWorkspace(workspaceId: String, sessions: List<TerminalSession>): WorkspaceSleepState`, `suspend fun terminateWorkspaceSessions(workspaceId: String, sessions: List<TerminalSession>): Boolean`, `suspend fun terminateSingleSession(workspaceId: String, session: TerminalSession): Boolean`, `suspend fun reconcileColdStartLeakedProcesses(ownerUid: Int)`

- [ ] **Step 1: Write failing tests**
- `TerminalSessionInputGateTest.java`: verify `write(String)`, `write(byte[], int, int)`, and `writeCodePoint(...)` drop input when `setInputGated(true)` and resume accepting when `setInputGated(false)` (no queued burst).
- `WorkspaceHibernationTest.kt` (using fake `ProcessOps` + real `/proc` parser tests):
  1. `parseProcStat` handles `comm` with spaces/parentheses (e.g. `123 (foo (bar)) T 1 200 100 ...`).
  2. Multi-PGID session (`bash` pgid=100, `vim` pgid=101, `sleep` pgid=102, all sid=100, uid=10912) + unrelated uid/sid process (sid=100 uid=9999 or sid=200 uid=10912): only matching `(sid, uid)` with `pid > 1 && pgid > 0` are signaled; never touches `pid <= 1` or other UIDs.
  3. Partial/failed `T` state verification transitions workspace to `WorkspaceSleepState.ERROR` (never `SLEEPING`) and un-gates or handles safely.
  4. `/proc` enumeration blocked/empty -> fails closed to `ERROR`.
  5. Termination sends `SIGCONT` -> `SIGTERM` -> polls -> `SIGKILL` survivors (covers bash ignoring `SIGTERM`).
  6. Hibernation feature flag off -> `sleepWorkspace` is a no-op (`AWAKE`), zero signals sent.

- [ ] **Step 2: Run tests to verify failure**
Run: `./gradlew :terminal-emulator:testDebugUnitTest --offline` and `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.WorkspaceHibernationTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `TerminalSession.java` input gate, `ProcSessionController.kt`, and `WorkspaceHibernationController.kt`**
- Track `workspaceId -> List<ManagedSidRecord(sid, pids, startTimeTicks)>` in `SharedPreferences("betermux_hibernation_sids")` so cold-start cleanup only targets previously managed BeTermux SIDs + matching UID + matching startTimeTicks (preventing PID/SID reuse hazards!).
- Hold a per-workspace `Mutex` inside `WorkspaceHibernationController` for all suspend/resume/terminate transitions.
- Gate `TerminalSession.setInputGated(true)` BEFORE sending `SIGSTOP`, and `setInputGated(false)` ONLY AFTER `SIGCONT` succeeds (if `SIGSTOP` fails and goes `ERROR`, `SIGCONT` any partially-stopped PIDs so the user isn't stuck with half-frozen processes, and restore `setInputGated(false)`).

- [ ] **Step 4: Run tests**
Run: `./gradlew :terminal-emulator:testDebugUnitTest --offline` and `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.*"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java \
  terminal-emulator/src/test/java/com/termux/terminal/TerminalSessionInputGateTest.java \
  app/src/main/java/com/betermux/workspace/ProcSessionController.kt \
  app/src/main/java/com/betermux/workspace/WorkspaceHibernationController.kt \
  app/src/test/java/com/betermux/workspace/WorkspaceHibernationTest.kt
git commit -m "feat(hibernation): add session-boundary input gating and SID-based process controller"
```

---

### Task 6: Wire Hibernation into Lifecycle, Tab Close, Cold-Start Cleanup & Honest UI (Phase 4B)

**Files:**
- Modify: `app/src/main/java/com/termux/app/TermuxService.java:130,736-771`
- Modify: `app/src/main/java/com/termux/app/TermuxActivity.java:2329-2475,3083-3095`
- Modify: `app/src/main/java/com/betermux/workspace/WorkspaceHostBridge.kt`
- Modify: `app/src/main/java/com/betermux/workspace/CollapsibleTabDrawer.kt`
- Modify: `app/src/main/java/com/termux/app/activities/SettingsActivity.kt` (optional/clean toggle for `KEY_WORKSPACE_HIBERNATION_ENABLED` rollback switch if appropriate)
- Test: `app/src/test/java/com/betermux/workspace/WorkspaceHibernationLifecycleTest.kt`

**Interfaces:**
- Consumes: `WorkspaceHibernationController`, `WorkspaceManager`, `BeTermuxSettings.isWorkspaceHibernationEnabled`
- Produces:
  - `TermuxActivity` sleeping-workspace tap-to-wake overlay banner/action + tab close routing through D2 (`terminateSingleSession`) when workspace is sleeping or hibernation enabled
  - `TermuxService.onCreate()` cold-start cleanup calling `WorkspaceHibernationController.reconcileColdStartLeakedProcesses` on background IO executor
  - `CollapsibleTabDrawer`: honest `SUSPENDING` / `RESUMING` / `ERROR` / `zzz` (`SLEEPING`) UI + SSH long-sleep honesty notice when hibernating

- [ ] **Step 1: Write failing lifecycle tests in `app/src/test/java/com/betermux/workspace/WorkspaceHibernationLifecycleTest.kt`**
Verify:
1. Cold-start reconciliation clears stale sleep state to `AWAKE` and triggers D2 cleanup for previously managed SIDs found in `T` state while sparing unrelated UIDs/SIDs.
2. Closing a tab in a sleeping workspace routes through `terminateSingleSession` (`CONT -> TERM -> wait -> KILL`).
3. Cross-workspace tap on a `SLEEPING` workspace wakes it (`SLEEPING -> RESUMING -> AWAKE`), un-gates `TerminalSession`, and switches active workspace + session.
4. UI displays honest `zzz` only when `SLEEPING`, shows `Error` badge on `ERROR`, and displays SSH notice on sleep.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.WorkspaceHibernationLifecycleTest"`
Expected: FAIL.

- [ ] **Step 3: Wire hibernation into `TermuxService.java`, `TermuxActivity.java`, `WorkspaceHostBridge.kt`, `CollapsibleTabDrawer.kt`**
- Add a clean, non-intrusive sleeping banner / tap-to-wake affordance on the terminal container when the active workspace is `SLEEPING` / `ERROR` so the user understands why typing is gated and can wake with one tap.
- In `TermuxActivity.closeSessionNow` and `showKillSessionDialog`: if `BeTermuxSettings.isWorkspaceHibernationEnabled(this)` and the session's workspace is `SLEEPING` or has child jobs, run `WorkspaceHibernationController.terminateSingleSession` on `Dispatchers.IO` (followed by `removeFinishedSession` on main thread).
- In `TermuxService.onCreate()`: dispatch `reconcileColdStartLeakedProcesses(android.os.Process.myUid())` on a background thread.

- [ ] **Step 4: Run full test suite & debug APK build**
Run: `./gradlew :terminal-emulator:testDebugUnitTest :app:testDebugUnitTest --offline --init-script ... --tests "com.betermux.workspace.*"` and `./gradlew :app:assembleDebug --offline`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/termux/app/TermuxService.java \
  app/src/main/java/com/termux/app/TermuxActivity.java \
  app/src/main/java/com/betermux/workspace/WorkspaceHostBridge.kt \
  app/src/main/java/com/betermux/workspace/CollapsibleTabDrawer.kt \
  app/src/test/java/com/betermux/workspace/WorkspaceHibernationLifecycleTest.kt
git commit -m "feat(hibernation): wire SID hibernation, cold-start cleanup, and honest UI states"
```
