# BeTermux Compose migration: remaining scope & status

This checklist tracks the migration described in [`from deepseek.md`](from%20deepseek.md) and [`2026-11-19-compose-ui-inventory.md`](docs/superpowers/specs/2026-11-19-compose-ui-inventory.md). **All code migration items for Milestones 1–4 are now implemented, built, and verified in JVM/Robolectric unit tests.** Only physical device/emulator manual verification and the pre-existing stale test baseline cleanup (if desired separately) remain.

## Completed across sessions

### Earlier session (Phase 0 & workspace behavior)
- Created and refined [`2026-11-19-compose-ui-inventory.md`](docs/superpowers/specs/2026-11-19-compose-ui-inventory.md).
- Implemented workspace new-session CWD policy (`WorkspaceSessionPolicy.kt`) and post-reconcile session persistence (`TermuxService.java`), with regression tests in `WorkspaceSessionIntegrationTest.kt` and `SessionStatePersisterTest.java`.

### Subsequent & current milestones (Milestones 1–4)
1. **Milestone 1 — Main terminal host, toolbar, tab strip, status line & inline popups:**
   - Migrated top toolbar + session strip (`TermuxToolbarCompose.kt`, `TermuxChromeCompose.kt`), status line (`TermuxStatusLine` without duplicate keyboard button and with width budgeting for 130% UI zoom), and autocorrect strip (`AutoCorrectComposeBar.kt`).
   - Replaced all 7 `NtPopupMenu` sites and 1 `PopupMenu` site with inline Compose `DropdownMenu`s (`Modifier.outlinedMenuCard()` + `MenuItemDivider()`) and Compose image-insert chooser (`TermuxDialogsCompose.showActionListDialog`).
   - Preserved `TermuxActivityRootView` (`fitsSystemWindows="true"` + `adjustResize` + `activity_termux_bottom_space_view`) as the sole insets owner; marked non-modal Compose chrome `FOCUS_BLOCK_DESCENDANTS` / non-focusable; restored `mImeVisible` tracking in `onImeInsetsChanged` and added post-popup IME restoration + toolbar layout listener for `UpdateUi.setTopOffset()`.
   - Deleted `betermux_toolbar.xml`, `betermux_autocorrect_bar.xml`, `betermux_status_line.xml`, `floating_edit_overlay`, `NtPopupMenu.java`, and `TabPillComposeView.kt`.
2. **Milestone 2 — App-owned dialogs & migrated support surfaces:**
   - Added `ComposeDialogHost.kt` (lifecycle `ON_DESTROY` cleanup, 480dp window width cap) and `TermuxDialogsCompose.kt`, migrating all remaining `AlertDialog`/`ProgressDialog` call sites across `TermuxActivity.java`, `TermuxTerminalViewClient.java`, `TermuxTerminalSessionActivityClient.java`, and `TermuxInstaller.java`.
   - Fixed `BootstrapErrorComposeDialog` Abort button (`onAbort` vs `onDismiss`), `ColorPickerComposeDialog` zero-brightness hue/saturation preservation + direct wheel pointer handling + free hex editing + pinned button bar (deleting `HsvColorWheelView.java` and `dialog_color_picker_{hsv,rgb}.xml`), and `HelpActivityCompose` continuous `WebView` mounting + pointer-blocking loading overlay + `HelpActivity.onDestroy()` cleanup.
3. **Milestone 3 — Shared module (`termux-shared`):**
   - Enabled Kotlin/Compose/Robolectric in `termux-shared/build.gradle` while preserving the `com.termux:termux-shared:0.118.0` Maven release publication (removed `withJavadocJar()` to avoid offline Dokka resolution).
   - Migrated `ReportActivity` (`ReportComposeScreen.kt`, keeping `MarkwonAdapter`/`RecyclerView` `FencedCodeBlock` rendering behind `AndroidView`), `MessageDialogUtils` (`SharedMessageComposeDialog.kt` with `Linkify` URL/email spans, `FlowRow`, callback-before-dismiss, finishing-activity guard), and `TextInputDialogUtils` (`SharedTextInputComposeDialog.kt` with single-invocation callback-before-dismiss, cursor-at-end, auto-focus, IME `Done`), deleting `activity_report.xml`, `menu_report.xml`, and `dialog_show_message.xml`.
4. **Milestone 4 — Static audit & tests:**
   - Updated `docs/superpowers/specs/2026-11-19-compose-ui-inventory.md` and `.superpowers/sdd/2026-11-19-compose-ui-migration/progress.md`.
   - Added/updated Robolectric Compose UI suites: `SupportScreensUiTest.kt` (`:termux-shared`), `TermuxToolbarUiTest.kt`, `TermuxSessionNavigationUiTest.kt`, and `TermuxDialogsUiTest.kt` (`:app`).

## Remaining manual verification (device / emulator required)

- **No Android device or emulator is attached** (`adb devices -l` empty), so runtime device checks remain outstanding:
  - Install and launch universal/ABI debug APK (`app/build/outputs/apk/debug/`).
  - Verify IME / Gboard show/hide, `DropdownMenu` dismissal IME restore, tap-to-focus, multi-window/floating resize, and `FullScreenWorkAround` behavior.
  - Verify TalkBack traversal and touch interaction across top toolbar, status line, session/live-port chips, autocorrect strip, dialogs, `ColorPickerDialog` wheel/rotation, `HelpActivity` WebView, `ReportActivity` share/copy/save/new-intent, and `FileReceiverActivity` error/name flows.
  - No performance claim is made without before/after device frame timings.

## Known test-baseline limitation

- Unfiltered `./gradlew :app:testDebugUnitTest` fails in `:app:compileDebugUnitTestKotlin` because 7 pre-existing unrelated test files (`app/src/test/java/com/betermux/{ai,blocks,input,workflows}/**` and `WorkspaceZoomAndSearchTest.kt`) reference absent production packages (`com.betermux.ai`, `blocks`, `input`, `workflows`). Focused `:app` tests pass when compiled with those 7 files excluded via an init script; `:termux-shared:testDebugUnitTest` compiles and passes directly.
