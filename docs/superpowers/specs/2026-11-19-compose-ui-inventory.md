# Active UI inventory: Compose migration

Scope checked: `app/src/main/res/layout`, `app/src/main/java`, app manifest, all `app/src/main/res` XML resources, and `termux-shared/src/main/{res/layout,java}`. Classification tracks whether each visible surface is Compose, migrated to Compose, retained as a terminal/platform adapter, OS-owned system UI, or an unreferenced candidate.

## Active BeTermux surfaces

| Surface / owner | Caller or registration | Class / status | Verification & implementation evidence |
|---|---|---|---|
| Main terminal host, toolbar, autocorrect strip, status strip: `activity_termux.xml`, `TermuxActivity` | Launcher and Leanback registration in `app/src/main/AndroidManifest.xml`; `TermuxActivity.onCreate()` sets `activity_termux`; `setupBeTermuxFeatures()` binds Compose hosts | migrated (BeTermux chrome); terminal widget & insets host retained as adapter | `TermuxToolbarCompose.kt` (`TerminalToolbar`), `TermuxChromeCompose.kt` (`TermuxChromeController`, `TermuxTopChrome`, `TermuxStatusLine`), `AutoCorrectComposeBar.kt`; legacy `betermux_toolbar.xml`, `betermux_autocorrect_bar.xml`, `betermux_status_line.xml`, and `floating_edit_overlay` deleted. Single insets owner: `TermuxActivityRootView` (`fitsSystemWindows="true"` + `adjustResize` + `activity_termux_bottom_space_view` + `FullScreenWorkAround`); Compose chrome sets `FOCUS_BLOCK_DESCENDANTS` and adds no system-bar/IME padding. |
| Session/tab strip, live localhost chips, add-tab affordance | `TermuxActivity.updateSessionTabs()` / `refreshTabStatusDots()` push `ChromeTabItem` / `LivePortItem` state into `TermuxChromeController` | migrated | `TermuxChromeCompose.kt` (`TermuxTabStripRow`, `ChromeTabPill`, `LivePortChip`, `AddTabChip`) owns horizontal `LazyRow` strip, auto-scroll, status dots/progress bars, close buttons, localhost live chips, and `"+"` add menu; legacy `TabPillComposeView.kt` and programmatic View builders deleted. |
| Main toolbar overflow, session/file/browser/localhost tab popups, add-tab menu, image insert menu | Previously `NtPopupMenu.showAsDropDown()`, Android `PopupMenu`, and programmatic dialogs in `TermuxActivity` | migrated | Inline Compose `DropdownMenu`s (`Modifier.outlinedMenuCard()` + `MenuItemDivider()`) inside `TermuxToolbarCompose.kt` and `TermuxChromeCompose.kt`, plus `TermuxDialogsCompose.showActionListDialog` for clipboard/gallery image insert; `NtPopupMenu.java` deleted (0 remaining `NtPopupMenu` / `android.widget.PopupMenu` usages in `app/src/main/java`). |
| Quick commands, edit command, make script, tips/shortcuts, kill session, battery optimization prompt, close-session confirmation, missing-styling-app prompt, URL/file-tap pickers, max-sessions alert, bootstrap installing progress | `TermuxActivity`, `TermuxTerminalViewClient`, `TermuxTerminalSessionActivityClient`, `TermuxInstaller` | migrated (app-authored dialogs); OS permission/exemption/picker intents remain system UI | `TermuxDialogsCompose.kt` (`showConfirmDialog`, `showActionListDialog`, `showQuickCommandsDialog`, `showEditDrawerButtonDialog`, `showMakeScriptDialog`, `showTipsAndShortcutsDialog`, `showProgressDialog`) via `ComposeDialogHost.kt`; 0 `AlertDialog` or `ProgressDialog` calls remain in `app/src/main/java`. OS battery exemption intent (`PermissionUtils.requestDisableBatteryOptimizations`), SAF pickers, permissions, and external app/browser intents remain system UI. |
| Session list row/controller: `item_terminal_sessions_list.xml`, `TermuxSessionsListViewController` | No constructor call exists in app sources. `TermuxActivity` holds a nullable field and `TermuxService` notifies it only if non-null. | unreferenced candidate | Static grep confirms no instantiation site; untouched and kept out of scope. |
| Terminal toolbar pager: `view_terminal_toolbar_extra_keys.xml`, `view_terminal_toolbar_text_input.xml`, `TerminalToolbarViewPager` | Attached/configured by `TermuxActivity.setTerminalToolbarView`; pager creates pages and terminal client locates text input | terminal/platform adapter | Retained intact for `ExtraKeysView`, terminal keyboard repeat/long-press/haptics, upload-image trigger, and text-input IME behavior. |
| Terminal content, cursor, selection/context menu, key handling, root view, full-screen workaround: `TermuxActivityRootView`, `TermuxTerminalViewClient`, `TermuxTerminalSessionActivityClient`, `FullScreenWorkAround`, terminal-view dependency | Main activity and terminal session/client lifecycle | terminal/platform adapter | Retained intact as sole insets/input/IME adapter inside `activity_termux.xml`. |
| Extra keys reusable component: `termux-shared/.../ExtraKeysView.java` | Inflated in terminal toolbar page; also hosted by `ControlDeck` via `AndroidView` | terminal/platform adapter | Retained intact. |
| `ControlDeck` activity and Compose screen | Created by `DeckBridge` for secondary display (`ControlDeckPresentation`) | Compose already | Existing Compose deck retained with mini-terminal and extra-keys `AndroidView` adapters (`performToolbarAction(R.id.btn_new_session)` preserved). |
| Workspace drawer, file drawer, split-pane workspace and hibernation banner | `WorkspaceHostBridge` attached by `TermuxActivity`; `ComposeView` overlays | Compose already | Retained mounted and lifecycle-safe. |
| File manager and archive/property/create/delete dialogs | Manifest-registered `FileManagerActivity`; Compose `FileManagerActivity.kt` / `FileTabViewer.kt` | Compose already | Retained. |
| Dev browser, browser tabs/popups/dialogs/sheets | Manifest-registered `DevBrowserActivity`; per-tab `ComposeView`s in `TermuxActivity` | Compose already | Retained with `WebView` `AndroidView` adapter. |
| Localhost servers tab and start dialog | Compose tab hosted by `TermuxActivity`; `LocalhostServersTab.kt` | Compose already | Retained. |
| Settings and settings dialogs | Manifest-registered/exported `SettingsActivity` | Compose already | Retained. |
| Package manager | Manifest-registered `PackageManagerActivity` | Compose already | Retained. |
| SSH manager | Manifest-registered `SshManagerActivity` | Compose already | Retained. |
| Theme picker/custom theme editor | Manifest-registered `ThemePickerActivity`; invokes `ColorPickerDialog` | Compose already | Color picker subdialog now Compose too (below). |
| Bootstrap setup error dialog | `TermuxInstaller.showBootstrapErrorDialog()` | migrated | `BootstrapErrorComposeDialog.kt` via `ComposeDialogHost.kt`; Abort invokes `onAbort` (`activity.finish()`), Retry invokes `onRetry`. |
| HSV/RGB color picker (`ColorPickerDialog`) | Called from `ThemePickerActivity` via `ColorPickerDialog.show()` | migrated | `ColorPickerComposeDialog.kt` via `ComposeDialogHost.kt` (separate HSV float state across zero brightness, direct wheel pointer gesture handling, free hex editing, `roundToInt` RGB, `Role.RadioButton` style rows). Dead `HsvColorWheelView.java`, `dialog_color_picker_hsv.xml`, and `dialog_color_picker_rgb.xml` deleted. |
| Help activity shell, loading and navigation/error states; `HelpActivity` | Manifest-registered `HelpActivity`, launched by `TermuxActivity` help action | migrated (shell/loading overlay); `WebView` retained as platform adapter | `HelpActivityCompose.kt` keeps `AndroidView { webView }` continuously mounted under an opaque pointer-blocking loading overlay; `HelpActivity.java` clears loading on commit/finish/error/back and safely destroys `WebView` in `onDestroy()`. |
| Update overlay, highlights dialog, progress/download card | `UpdateUi` mounts `ComposeView` from `UpdateManager` | Compose already | Retained; `TermuxActivity` updates top offset on toolbar container layout changes. |
| Speech input overlay resource: `betermux_stt_overlay.xml` | 0 source/manifest refs; handled by `SpeechInputManager` | unreferenced candidate | Left untouched. |
| Root indicator resource: `betermux_root_indicator.xml` | 0 source/manifest refs | unreferenced candidate | Left untouched. |
| Session options sheet resource: `bottom_sheet_session_options.xml` | 0 source/manifest refs | unreferenced candidate | Left untouched. |
| Markdown preference row: `preference_markdown_text.xml` | 0 app source/manifest refs | unreferenced candidate | Left untouched. |

## Other app layout resources

| Resource | Caller / status | Class / disposition |
|---|---|---|
| `betermux_toolbar.xml`, `betermux_autocorrect_bar.xml`, `betermux_status_line.xml` | Deleted after migration to `TermuxToolbarCompose.kt`, `TermuxChromeCompose.kt`, `AutoCorrectComposeBar.kt` | migrated & removed. |
| `dialog_color_picker_hsv.xml`, `dialog_color_picker_rgb.xml` | Deleted after migration to `ColorPickerComposeDialog.kt` | migrated & removed. |
| `activity_termux.xml` | `TermuxActivity` (hosts `ComposeView` `betermux_toolbar_container`, `AutoCorrectComposeBar` `autocorrect_bar`, `ComposeView` `betermux_status_line`, and retained `TerminalView`/pagers/insets root) | migrated host chrome; terminal adapters retained. |
| `view_terminal_toolbar_extra_keys.xml`, `view_terminal_toolbar_text_input.xml` | `TerminalToolbarViewPager` | terminal/platform adapter (retained). |
| `item_terminal_sessions_list.xml` | `TermuxSessionsListViewController` (no constructor call) | unreferenced candidate (retained untouched). |
| `activity_report.xml`, `dialog_show_message.xml` (and `menu_report.xml`) in `termux-shared` | Deleted after migration to `ReportComposeScreen.kt` and `SharedMessageComposeDialog.kt` | migrated & removed. |
| `markdown_adapter_node_default.xml`, `markdown_adapter_node_code_block.xml`, `partial_primary_toolbar.xml` in `termux-shared` | Retained for `MarkwonAdapter` (`ReportComposeScreen.kt`), `preference_markdown_text.xml`, and `TextIOActivity` | shared adapter/other-surface layouts (retained). |
| `betermux_stt_overlay.xml`, `betermux_root_indicator.xml`, `bottom_sheet_session_options.xml`, `preference_markdown_text.xml` | No active source/layout caller | unreferenced candidates (retained untouched). |

## App non-layout resource-backed UI

- `res/xml/termux_preferences.xml` and related `termux_*_preferences.xml` files remain legacy Termux/plugin PreferenceFragment contracts (system/platform UI; untouched).
- `termux-shared/src/main/res/menu/menu_text_io.xml` remains for inactive `TextIOActivity`; `menu_report.xml` was removed with `ReportActivity` migration.
- Drawables (including `bg_popup_menu.xml` used by `betermux_styles.xml` and `TermuxActivity.showToast()`), colors, styles, themes, `ids.xml` (`btn_new_session`), and provider path XML are retained where still referenced.

## termux-shared UI actually used by this app

| Shared surface/component | Evidence of app use | Class / status & implementation |
|---|---|---|
| `ReportActivity` | Manifest-registered `.shared.activities.ReportActivity` + receiver; called by `TermuxTerminalViewClient`, `SettingsActivity`, crash/plugin utils | migrated (`ReportComposeScreen.kt` + `SharedComposeTheme`; `MarkwonAdapter` `RecyclerView` retained behind `AndroidView` for `FencedCodeBlock` fidelity; public API, 1MB truncation, intent/cache/receiver/save permission behavior preserved; `activity_report.xml` and `menu_report.xml` deleted). |
| `MessageDialogUtils` | Called by `TermuxInstaller`, `FileReceiverActivity`, `TermuxTerminalViewClient`, `PackageUtils` | migrated (`SharedMessageComposeDialog.kt` + `SharedComposeDialogHost.kt`; `Linkify` URL/email links, callback-before-dismiss, finishing-activity guard, non-`ComponentActivity` `AlertDialog` fallback; `dialog_show_message.xml` deleted). |
| `TextInputDialogUtils` | Called by `FileReceiverActivity` and `TermuxTerminalSessionActivityClient` | migrated (`SharedTextInputComposeDialog.kt` + `SharedComposeDialogHost.kt`; single-invocation callback-before-dismiss ordering, IME `Done`, cursor-at-end, auto-focus, finishing-activity guard, non-`ComponentActivity` fallback). |
| `ExtraKeysView` | Inflated in `view_terminal_toolbar_extra_keys.xml`; embedded by `ControlDeck` | terminal/platform adapter (retained). |
| `TextIOActivity` + `activity_text_io.xml` | No app manifest registration or source call | unreferenced candidate (retained untouched). |
| `MarkdownUtils`, `MarkdownView` and generic shared helpers | Report/notification/setup formatting | platform/library component (retained). |

## Verification notes

- Automated JVM/Robolectric Compose & unit suites pass for the migrated surfaces (`SupportScreensUiTest` in `:termux-shared` and `WorkspaceSessionIntegrationTest`, `SessionStatePersisterTest`, `TermuxToolbarUiTest`, `TermuxSessionNavigationUiTest`, `TermuxDialogsUiTest` under isolated `:app` test compilation excluding 7 pre-existing broken test files referencing absent `ai`/`blocks`/`input`/`workflows` types).
- Runtime checks on an attached Android device/emulator (IME/Gboard interaction, keyboard resize, focus traversal, TalkBack walkthrough, WebView help loading, report actions, rotation, and visual inspection) were not run because no device/emulator is attached (`adb devices -l` returned empty).
