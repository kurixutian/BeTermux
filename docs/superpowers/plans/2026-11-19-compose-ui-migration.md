# BeTermux Compose UI Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development for the staged implementation and task reviews.

**Goal:** Move all active BeTermux user-facing UI to Compose screen by screen, while preserving the custom terminal and its input path.

**Architecture:** Keep existing Activity/service behavior and bridge Java owners to Kotlin Composables with `ComposeView`/small Kotlin host functions. Preserve `TerminalView`, its terminal input controls, and `TermuxActivityRootView` as Android adapters; migrate surrounding app UI without replacing the current resize, inset, or focus behavior.

**Tech Stack:** Existing Android app, Kotlin/Java, Jetpack Compose dependencies already in `app`, Robolectric/JUnit tests, Android emulator/device visual checks.

**Spec:** `docs/superpowers/specs/2026-11-19-compose-ui-migration-design.md`

## Global Constraints

- Keep `TerminalView`, terminal emulator, rendering, selection, and IME/input behavior intact.
- Keep `ExtraKeysView` and terminal text-input pager as terminal adapters unless equivalent dispatch and focus behavior are verified.
- Preserve `TermuxActivityRootView` inset, resize, focus, and multi-window behavior; do not add a second IME/system-inset owner.
- Use existing Compose dependencies and theme; do not add a UI library or a new navigation/state-management architecture.
- Preserve current navigation, accessibility, preferences, and behavior; do not change persistence formats.
- Keep Android resources/configuration XML that is not layout composition.
- Claim no performance improvement without device frame-timing evidence.

## Review Focus

- A session restored after process death must retain its saved workspace — cover persisted workspace identity and post-restore association/save ordering in the restore task, then force-stop/relaunch on an Android target.
- A new terminal must start at its workspace root, while a manually changed or restored session keeps its own CWD — cover root selection in `WorkspaceSessionIntegrationTest` and the live flow in the device check.
- Compose must not steal terminal focus or double-apply IME/window insets — preserve the existing root/view ownership and check keyboard show/hide, resize, and multi-window behavior on device.
- Toolbar order and actions must match the requested folder/extra-keys swap — cover semantics order and callback invocation in a Compose UI test.
- Extra-key dispatch, long-press/repeat, dialogs, and list actions must retain accessibility and interaction behavior — test each migrated owner and inspect the resulting screen/flow on device.

## File Map

- `app/src/main/java/com/betermux/workspace/WorkspaceSessionPolicy.kt` and `WorkspaceSessionIntegrationTest.kt`: default new-session root-directory policy.
- `app/src/main/java/com/termux/app/SessionStatePersister.java`, `TermuxService.java`, and `SessionStatePersisterTest.java`: snapshot workspace identity and restoration ordering.
- `app/src/main/res/layout/activity_termux.xml`, `betermux_toolbar.xml`, and `app/src/main/java/com/termux/app/TermuxActivity.java`: host the Compose header and wire its existing actions; preserve root host mechanics.
- New focused Kotlin Compose files under `app/src/main/java/com/termux/app/` or `com/betermux/`: toolbar, chrome, row, and dialog UI; expose narrow host functions only where Java callers need them.
- `TermuxActivity.java`, `TermuxActivityRootView.java`, `TermuxSessionsListViewController.java`, `NtPopupMenu.java`, and active referenced layout resources: migrate activity chrome/session UI and remove old view wiring only after replacement.
- `ColorPickerDialog.java`, `HsvColorWheelView.java`, `dialog_color_picker_hsv.xml`, and `dialog_color_picker_rgb.xml`: migrate active color-picker UI to Compose and retire replaced views/layouts.
- `HelpActivity.java`, active report/text-I/O activities/layouts in `termux-shared`, and their Gradle wiring if needed: migrate active app-owned support screens while preserving WebView/system integrations.
- A scope inventory document created in Task 1 will list all additional active UI surfaces discovered in `app`, `termux-shared`, and other packaged app modules. Already-Compose screens are excluded from rewrite; unused XML is deleted only after reference checks.

## Tasks

### Task 1: Establish the active UI scope

**Files:**
- Create: `docs/superpowers/specs/2026-11-19-compose-ui-inventory.md`
- Inspect: `app/src/main/res/layout`, `app/src/main/java`, `termux-shared/src/main/res/layout`, `termux-shared/src/main/java`, manifest registrations, and all app module layout resources.

- [ ] Enumerate every active app-owned screen, dialog, popup, row, and programmatic View construction; record its caller and resource/class owner.
- [ ] Classify each item as `Compose already`, `migrate`, `terminal/platform adapter`, `system/platform UI`, or `unreferenced candidate`.
- [ ] Verify likely orphan candidates such as `bottom_sheet_session_options.xml`, `preference_markdown_text.xml`, `betermux_stt_overlay.xml`, and `betermux_root_indicator.xml` with code and manifest references before excluding or removing them.
- [ ] List UI in `termux-shared` that is actually launched by this app; do not migrate a generic shared component without checking its external callers and publication role.
- [ ] Record completion criteria for each active `migrate` item so Task 7 can verify the scope is complete.

### Task 2: Fix workspace restore and new-session CWD rules

**Files:**
- Modify: `app/src/main/java/com/betermux/workspace/WorkspaceSessionPolicy.kt`
- Modify: `app/src/main/java/com/termux/app/TermuxService.java`
- Modify: `app/src/test/java/com/betermux/workspace/WorkspaceSessionIntegrationTest.kt`
- Modify: `app/src/test/java/com/termux/app/SessionStatePersisterTest.java`

- [ ] Add a regression case proving a new session uses the active workspace root even when the current session in that workspace has manually changed directory.
- [ ] Add a regression case proving Default uses `/home` (or the configured default root) for a new session even when another current session has changed CWD.
- [ ] Preserve the current snapshot schema and fix the confirmed ordering gap: `createTermuxSessionInternal(..., true)` saves each restored session before `restoreSessionsIfNeeded()` associates its saved workspace; the final restored entry can therefore be written without its workspace ID.
- [ ] Ensure the final persisted snapshot is rewritten after all restored sessions are associated and `reconcileStartupSessions` completes; preserve each restored CWD and existing snapshot format.
- [ ] Add `reconcileAfterRestoreKeepsDistinctWorkspaceMappings` coverage for multiple restored sessions, plus a snapshot load test for workspace/CWD round-trip and a legacy record with no workspace field. Use a narrow test seam if the service's PTY-backed restore cannot run under Robolectric.
- [ ] Run `./gradlew :app:testDebugUnitTest --offline --tests 'com.betermux.workspace.WorkspaceSessionIntegrationTest' --tests 'com.termux.app.SessionStatePersisterTest'`.

### Task 3: Compose the terminal activity header

**Files:**
- Create: a focused toolbar Composable and Kotlin ComposeView host under `app/src/main/java/com/termux/app/`.
- Modify: `app/src/main/res/layout/activity_termux.xml`, `app/src/main/res/layout/betermux_toolbar.xml`, `app/src/main/java/com/termux/app/TermuxActivity.java`.
- Test: focused Compose UI test under `app/src/test/java/com/termux/app/`.

- [ ] Add a failing `TermuxToolbarUiTest.folderAndExtraKeysActionsFollowSwappedSemanticOrder` test asserting the order is extra-keys, localhost, folder, more-options and that folder/extra-keys callbacks fire.
- [ ] Host the Compose header in the current activity hierarchy without replacing `TermuxActivityRootView`, the bottom-space IME probe, or existing inset/resize behavior.
- [ ] Wire callbacks to the existing folder drawer/file-manager and extra-keys visibility behavior; remove obsolete per-button `findViewById` wiring as each callback moves.
- [ ] Keep button descriptions, touch targets, toolbar visibility, theme colors, and traversal order; make the folder/extra-keys control order match visually and semantically.
- [ ] Run `./gradlew :app:testDebugUnitTest --offline --tests 'com.termux.app.TermuxToolbarUiTest'` and `./gradlew :app:assembleDebug --offline`.
- [ ] On an available emulator/device, verify header order/actions, terminal focus, keyboard resize, and drawer behavior.

### Task 4: Migrate remaining terminal activity chrome and session navigation

**Files:**
- Modify as listed by Task 1, primarily `app/src/main/java/com/termux/app/TermuxActivity.java`, `TermuxSessionsListViewController.java`, `NtPopupMenu.java`, and active layouts for status/autocorrect/session rows.
- Keep: `TerminalView`, `TermuxActivityRootView`, `TerminalToolbarViewPager`, `ExtraKeysView`, and terminal text-input adapter unless a behavior-preserving Compose replacement is verified.

- [ ] Migrate active status/autocorrect bars and session tabs/pips in isolated sub-slices; each sub-slice removes only its own legacy view wiring.
- [ ] Replace active workspace/session controls, contextual menus, and session row inflation with Compose while preserving selection, rename/long-press actions, running/exit status, and stable session identity.
- [ ] Keep one owner for session state; pass explicit state and callbacks to composables rather than adding a new state architecture.
- [ ] Add `TermuxSessionNavigationUiTest` coverage for session selection, workspace-scoped visibility, tab actions, and accessibility semantics using the repository's existing Compose test setup.
- [ ] Run focused tests and `./gradlew :app:assembleDebug --offline`; visually inspect each migrated surface on an available Android target.

### Task 5: Migrate app-owned dialogs and color picking

**Files:**
- Modify: active dialog builders found by Task 1 in `TermuxActivity.java`, `TermuxTerminalViewClient.java`, `TermuxTerminalSessionActivityClient.java`, `TermuxInstaller.java`, and `com/betermux/features/ColorPickerDialog.java`.
- Retire only after verified replacement: `HsvColorWheelView.java`, `dialog_color_picker_hsv.xml`, `dialog_color_picker_rgb.xml`, and other active dialog layouts.

- [ ] Migrate active app-owned dialogs/popups to Compose while retaining cancellation, back dismissal, result callbacks, share/copy actions, and error behavior; add `TermuxDialogsUiTest` coverage for dismissal and primary actions.
- [ ] Move the HSV wheel interaction to Compose Canvas/pointer input only after tests cover value changes and accessibility descriptions; preserve existing theme and color application behavior.
- [ ] Leave Android permission/system-owned dialogs as platform UI and list any shared generic dialog utility that cannot be migrated without changing external API contracts.
- [ ] Run focused Compose/UI behavior tests and `./gradlew :app:assembleDebug --offline`; inspect dialog/back/accessibility behavior on an available Android target.

### Task 6: Migrate remaining active support screens

**Files:**
- Modify: `HelpActivity.java` and active report/text-I/O UI identified in Task 1.
- If a shared library Composable is required, update `termux-shared/build.gradle` with only the Compose compiler/dependencies already used by the app and verify downstream APIs remain source/binary compatible.
- Preserve platform integrations such as `WebView`, share intents, notifications, report cleanup receivers, and file I/O.

- [ ] Migrate HelpActivity's loading/error shell and active app-owned report/text-I/O controls to Compose, hosting `WebView` through Android interop where needed.
- [ ] Preserve Activity intents, saved report/text payloads, back behavior, clipboard/share actions, and manifest registrations.
- [ ] Add `SupportScreensUiTest` coverage for report/text input payload display and primary actions; run the affected module test/build tasks and the Android app build.
- [ ] Visually inspect each migrated support screen and its platform content on an available Android target.

### Task 7: Close the inventory and remove replaced UI

**Files:**
- Modify: each item still classified `migrate` in `docs/superpowers/specs/2026-11-19-compose-ui-inventory.md`.
- Delete only verified obsolete layout/view files; retain resource/configuration XML and terminal/platform adapters.

- [ ] Re-run the inventory searches and update each item to `Compose already`, `migrated`, `terminal/platform adapter`, `system/platform UI`, or `verified unused` with evidence.
- [ ] Remove obsolete view inflation, listeners, adapter classes, and layout files after their Compose replacement has passed its task checks.
- [ ] Run relevant app/shared module tests and `./gradlew :app:assembleDebug --offline`.
- [ ] Complete a device walkthrough of terminal startup, terminal input, workspace switch/restore, settings, file/browser tabs, dialogs, support screens, keyboard/focus, and window resizing.
- [ ] Make no performance claim unless the same device/build/workflow has frame-timing evidence before and after.

## Plan Review Notes

- The `from deepseek.md` sample using a simple `Column(WindowInsets.systemBars)` is not a drop-in for this app: `activity_termux.xml` already coordinates `adjustResize`, `fitsSystemWindows`, `TermuxActivityRootView` global-layout margin correction, and a bottom-space probe. Keep that behavior until a tested replacement exists.
- Compose is already hosted in this app; an extra baseline Compose host phase is unnecessary.
- The extra-keys view implements special key dispatch, hold/repeat, swipe/popup actions, and haptics. Keep it in the terminal input adapter boundary unless a replacement proves behavior parity.
- App Robolectric/Compose UI tests are available, but there is no app instrumentation/screenshot/benchmark harness. Device IME/visual checks are manual; offline app builds may still need bootstrap archives already present locally.

