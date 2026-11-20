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
- Baseline `./gradlew :app:testDebugUnitTest --offline --no-daemon`: FAIL in `:app:compileDebugUnitTestKotlin` before any task changes. Existing tests reference absent `com.betermux.ai`, `blocks`, `input`, and `workflows` production types. Production `:app:compileDebugKotlin` completed successfully first. User chose to keep the migration scoped and report this baseline limitation.
- Ruling: retain active shared `ReportActivity` and `MessageDialogUtils` UI, plus the HelpActivity Compose shell, in migration scope because the approved design covers active user-facing surfaces; preserve their shared API/integrations and WebView adapter. Keep `TextIOActivity` and the uninstantiated sessions-list adapter out of scope because no app registration/construction exists. Cost if wrong: inventory scope may need reclassification before those surfaces are implemented.
- Task 1: complete (commits `8f1b056..573b85f`, review clean; docs-only, `git diff --check` passed).

- Task 1 fix round 1: added active TermuxActivity battery optimization, close-session and missing-styling prompts; TermuxInstaller bootstrap-error dialog; active shared ReportActivity and MessageDialogUtils migration scope; HelpActivity Compose shell with WebView adapter; marked uninstantiated TermuxSessionsListViewController and inactive TextIOActivity as candidates/out of scope. Static call-site checks and `git diff --check` recorded in task-1-report.md.
- Task 1: fix round 1/5 (all findings addressed, none open — dialog inventory, shared/support UI scope, inactive adapter classification; commits `1d8e6f4..573b85f`).

- Continuation work completed across Milestones 1–4 (all active BeTermux & shared app-launched UI migrated to Compose while preserving terminal/platform adapters and OS UI contracts):
  - **Main terminal chrome, tabs & inline popups**: Added `TermuxChromeCompose.kt` (`TermuxChromeController`, `TermuxTopChrome`, `TermuxStatusLine`, `TermuxTabStripRow`) and updated `TermuxToolbarCompose.kt` (`TerminalToolbar` with inline anchored `DropdownMenu`s) + `AutoCorrectComposeBar.kt` (themed + `FOCUS_BLOCK_DESCENDANTS`). Removed `betermux_toolbar.xml`, `betermux_autocorrect_bar.xml`, `betermux_status_line.xml`, `floating_edit_overlay`, `NtPopupMenu.java`, and `TabPillComposeView.kt`. Fixed `TermuxActivityRootView` `toWindowInsetsCompat(insets, v)`, `TermuxActivity` `mImeVisible` / `KEY_AUTOCORRECT` / `UpdateUi` layout listener / post-popup IME restore, and `TermuxStatusLine` narrow/zoom width budgeting. Retained `TermuxActivityRootView` (`fitsSystemWindows="true"` + `adjustResize` + `activity_termux_bottom_space_view`) as sole insets owner and preserved `performToolbarAction(R.id.btn_new_session)`, `TerminalToolbarViewPager`, `ExtraKeysView`, and `TerminalView`.
  - **App-owned dialogs & migrated support surfaces**: Added `ComposeDialogHost.kt` (lifecycle-aware `ON_DESTROY` cleanup, 480dp window width cap matching card bounds) and `TermuxDialogsCompose.kt`, migrating all remaining `AlertDialog`/`ProgressDialog` call sites in `TermuxActivity.java`, `TermuxTerminalViewClient.java`, `TermuxTerminalSessionActivityClient.java`, and `TermuxInstaller.java`. Fixed `BootstrapErrorComposeDialog` Abort callback (`onAbort`), `ColorPickerComposeDialog` zero-brightness H/S preservation + direct wheel pointer handling + free hex editing + pinned button bar (`HsvColorWheelView.java` and `dialog_color_picker_{hsv,rgb}.xml` deleted), and `HelpActivityCompose` continuous `WebView` mounting + pointer-blocking loading overlay.
  - **Shared module (`termux-shared`)**: Enabled Kotlin/Compose in `termux-shared/build.gradle` (preserving `com.termux:termux-shared:0.118.0` Maven release publication; dropped `withJavadocJar()` which attempts offline Dokka fetch), migrated `ReportActivity` (`ReportComposeScreen.kt` retaining `MarkwonAdapter` `RecyclerView` + `FencedCodeBlock`), `MessageDialogUtils` (`SharedMessageComposeDialog.kt` with `Linkify` + `FlowRow` + finishing-activity guard), and `TextInputDialogUtils` (`SharedTextInputComposeDialog.kt` with single-invocation callback-before-dismiss + auto-focus); deleted `activity_report.xml`, `menu_report.xml`, and `dialog_show_message.xml`.
  - **Static audit & inventory closure**: Updated `docs/superpowers/specs/2026-11-19-compose-ui-inventory.md`; grep confirmed 0 remaining `NtPopupMenu`, `PopupMenu`, `ProgressDialog`, or `AlertDialog` usages in `app/src/main/java/`, 0 dangling deleted-layout references, and all unreferenced candidates/adapters left intact.
  - **Ruling (`termux-shared` publication)**: Removed `withJavadocJar()` from `termux-shared/build.gradle`'s `multipleVariants` block because AGP attempts to resolve uncached `org.jetbrains.dokka:dokka-core:1.4.32` offline for mixed Java/Kotlin libraries (`withSourcesJar()` and `release` AAR publication succeed offline). Cost if wrong: external consumers expecting a separate `-javadoc.jar` will need Dokka cached/configured later.
  - **Remaining verification limits**: No Android device/emulator is attached (`adb devices -l` empty), so runtime IME/Gboard, focus traversal, TalkBack, layout/rotation, Help WebView, and Report share/save checks remain unverified on target hardware. Broad `:app:testDebugUnitTest` without exclusions remains blocked on 7 pre-existing stale test files referencing absent `com.betermux.{ai,blocks,input,workflows}` types (`WorkspaceZoomAndSearchTest.kt` + 6 files in those packages).
