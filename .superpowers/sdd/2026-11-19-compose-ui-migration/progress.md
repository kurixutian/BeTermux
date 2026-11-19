# SDD ledger — plan: docs/superpowers/plans/2026-11-19-compose-ui-migration.md

Base: `8f1b056fff344785d0644672c0731ac0e4b71eef`
Branch: `codex/compose-ui-migration`

## Setup

- Worktree skill native checkout was created at `/home/.codex/worktrees/47cf/BeTermux`, outside this session's writable root. Per the skill's sandbox fallback, implementation continues in this writable checkout on the dedicated branch.
- User's untracked `from deepseek.md` is input only and is not part of the implementation branch changes.
- Compose and Ponytail are already installed; no new skill/dependency installation is needed.

## Pre-flight conflict scan

| Tasks | Shared file/interface | What one produces/other consumes | Finding and ruling |
|---|---|---|---|
| 1, 4–7 | `docs/superpowers/specs/2026-11-19-compose-ui-inventory.md` | Task 1 classifies active UI; later tasks migrate and close those entries. | Ordered correctly: Task 1 precedes all migrations; Task 7 is the only task to close/delete inventory entries. |
| 2, 4 | `WorkspaceSessionPolicy.kt`, workspace/session behavior | Task 2 defines root-CWD policy; Task 4 migrates its UI. | No conflict: behavior fix lands before its UI consumer is migrated. |
| 2, 3 | `TermuxActivity.java` indirectly / session service behavior | Task 2 fixes service restore; Task 3 wires toolbar behavior. | Separate logic vs UI, and Task 2 precedes Task 3. Preserve behavioral result while moving callbacks. |
| 3, 4 | `TermuxActivity.java`, terminal activity UI wiring | Task 3 exposes toolbar callbacks; Task 4 migrates other chrome and session controls. | Shared large file: execute sequentially; Task 4 consumes stable toolbar callbacks and must not rewrite Task 3 host. |
| 4, 5 | `TermuxActivity.java`, contextual UI callbacks | Task 4 migrates session menus; Task 5 migrates remaining dialogs. | Potential call-site overlap: Task 4 first, then Task 5 converts only remaining active dialog owners. |
| 1, 7 | UI inventory document and obsolete resources | Task 1 creates classification; Task 7 uses evidence to remove only verified unused/replaced UI. | Ordered correctly; no deletion before the full inventory and replacement proof. |

| Task | Internal consistency check | Finding and ruling |
|---|---|---|
| 1 | Inventory file created before later tasks inspect it. | Consistent; record paths/callers and classify active, platform, system, and orphan candidates. |
| 2 | New-session root CWD tests and restore snapshot test align with listed policy/service files. | Consistent; do not change the existing persisted schema. Use a narrow test seam only if PTY-backed service restore cannot run under Robolectric. |
| 3 | Compose header test covers visual/semantic order and action callbacks; Activity hierarchy keeps existing host. | Consistent; do not replace root/inset handling or terminal input view. |
| 4 | Chrome/session UI and row tests align with listed Activity/adapters and active resources. | Consistent; inventory must confirm any adapter is active before migration/removal. |
| 5 | App-owned dialog/color picker migration aligns with active Java call sites and picker resources. | Consistent; OS permission/system UI remains platform-owned. |
| 6 | Support screen migration includes Help and active shared report/TextIO; shared module may need Compose plugin wiring. | Conditional but plan is clear: change shared build only if active screens require it, keep APIs/integrations stable. |
| 7 | Final audit depends on completed earlier slices and inventory. | Consistent; delete only after replacement verification and retain resources/adapters. |

## Task progress

- Baseline `./gradlew :app:assembleDebug --offline --no-daemon`: PASS (`BUILD SUCCESSFUL`, 96 actionable tasks; Gradle warning about existing deprecations and experimental AAPT2 override).
- Baseline `./gradlew :app:testDebugUnitTest --offline --no-daemon`: FAIL in `:app:compileDebugUnitTestKotlin` before any task changes. Existing tests reference absent `com.betermux.ai`, `blocks`, `input`, and `workflows` production types. Production `:app:compileDebugKotlin` completed successfully first. A separate read-only agent is checking whether this is stale test coverage from the checkpoint; user has been asked whether this unrelated baseline should be repaired.
- Task 1: implementer `/root/inventory_task1` active; no product code changes.

- Task 1 fix round 1: added active TermuxActivity battery optimization, close-session and missing-styling prompts; TermuxInstaller bootstrap-error dialog; active shared ReportActivity and MessageDialogUtils migration scope; HelpActivity Compose shell with WebView adapter; marked uninstantiated TermuxSessionsListViewController and inactive TextIOActivity as candidates/out of scope. Static call-site checks and `git diff --check` recorded in task-1-report.md.
