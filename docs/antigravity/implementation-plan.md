# BeTermux Warp-Inspired Redesign & Block Architecture — Implementation Plan (v2)

> **Status:** Amended Plan v2 — Awaiting User Approval Before Phase 1a  
> **Repository:** `/data/data/com.termux/files/home/projects/BeTermux`  
> **Target Baseline:** Commit `9b128cc` (`main`)

---

## 1. Amendment 1 — Architecture & Spike Resolutions (v2)

### Q1 — Block Text Rendering Fidelity vs. `TerminalView`
- **(a) Custom Canvas Composable Reusing `TerminalRenderer`:** Completed **and** active running blocks render through a custom Canvas-backed Composable (`BlockRowsCanvas` in `:app`) powered by an extracted, stateless row-painting helper (`TerminalRowPainter` in `:terminal-view`, sharing `TerminalRenderer`'s `mTextPaint`, `Typeface`, `mFontWidth`, `mFontLineSpacing`, `mFontLineSpacingAndAscent`, `TerminalColorScheme`, and `drawTextRun` logic) — **not** Compose `Text` / `SelectionContainer` / `AnnotatedString`.
  - *Justification:* Compose `Text` uses `StaticLayout` proportional shaping and fallback line metrics, which misalign box-drawing glyphs (`┌─┐`), wide CJK/Nerd-Font characters, and inverse-video columns compared to `TerminalView`, force text-shaping allocations during fast scrolling, and visually snap when a running command finishes. Drawing fixed-height `Canvas` slices (`height = chunkRows * lineSpacing`) via `TerminalRowPainter` yields **pixel-identical font metrics, ANSI 256/truecolor fidelity, O(1) Compose measure, and zero per-frame allocations**. TalkBack accessibility is preserved without `Text` by attaching `Modifier.semantics { text = ...; contentDescription = ... }` from pre-computed cached strings.
- **(b) Active Running Block Cursor Rendering:** While a block is actively running (`isRunning == true`), `BlockSessionController` exposes the live row window `[block.startRow .. activeBottomOrCursorRow]` from `TerminalBuffer.mMainBuffer` together with `cursorCol`, `relCursorRow`, `cursorStyle` (block / underline / bar), and `cursorBlinkVisible` (static/solid when `rememberReduceMotion()` is active). `BlockRowsCanvas` passes those into `TerminalRowPainter.renderRows(...)`, executing the **exact same cursor draw path** used by `TerminalView.onDraw()`.
- **(c) Link Detection & Text Selection Across Boundaries:**
  - *Unified Viewport Model (No Adjacent View Split):* When Block View is active, **all** primary-screen scrollback, completed blocks, and the active running block render inside the single unified `LazyColumn` (`BlockRowsCanvas`). Full-screen `TerminalView` is swapped in **only** when `isAlternateBufferActive()` (`vim`, `htop`, `less`, `tmux`) or when the user manually activates the **Raw PTY** toggle — so there is no live `TerminalView` sitting adjacent to Compose blocks to create a dual-renderer boundary mismatch. (During Phase 1b before Phase 2 adds `TwoRowInputBar`, soft/hardware keystrokes still enter the active PTY and render seamlessly in the live bottom block.)
  - *Shared Link & File Detection:* Extract URL, relative/absolute file-path (`./src/Foo.kt:42`), and OSC-8 hit-testing from `TermuxTerminalViewClient.onSingleTapUp` into a shared helper (`TerminalTapResolver.resolveAtCell(row, col, cwd)` in `:app`). Both `TerminalView` and `BlockRowsCanvas` (`detectTapGestures` -> `(col = x / fontWidth, row = y / lineSpacing)`) call `TerminalTapResolver` and show the identical `NtPopupMenu` (`Open in Dev Browser`, `Open in Editor Tab`, `Copy Link`).
  - *Text Selection Semantics:* Long-press on a block enters **Block Selection Mode** (1.5dp outline, tap to multi-select N blocks, Copy/Share whole command+output across blocks cleanly). For **arbitrary character/substring selection** inside a block (e.g., partial stack traces or tokens), a block header action and single-block selection bar **"Select Text"** opens an inline/sheet monospace `SelectionContainer` over that block's plain text — avoiding touch-drag vs. `LazyColumn` vertical scroll conflicts on small screens while keeping `TextSelectionCursorController` intact inside full-screen Raw/Alt-Screen `TerminalView`.

### Q2 — Phase 1 Split
Phase 1 is split into two independently gated sub-phases with separate diff and test artifacts:
1. **Phase 1a — Foundation (Mechanical, low-risk):** Remove `com.newtermux.glass.*` and `GlassTokensTest.kt`; add `com.newtermux.theme.*` (**Warm Dark**, **Solarized Dark**, **High Contrast**, optional **Custom ANSI Colors** slot, `WarpComponents`, `MotionPreferences`, `BackgroundImageStore`); migrate every `Glass*` Compose screen, `TermuxActivity` tab pill/toast, `NtPopupMenu`, and XML colors/drawables; fold in the verified Q4 Robolectric Gradle config. **Zero block model, zero OSC changes.** Stop for approval before starting Phase 1b.
2. **Phase 1b — Block System (Architectural):** `:terminal-emulator` OSC 133/7 parsing + `mTotalTranscriptRows` + resize marker tracking; `:terminal-view` `TerminalRowPainter` extraction; `:app` `TerminalBlock` index, `BlockSessionController` (OSC 133 + fallback boundary heuristic), `TerminalTapResolver`, `BlockWorkspaceHost` (left status strip, sticky command headers + tap-to-jump, dividers + Comfort/Compact density toggle, long-press multi-select outline + Copy/Share, `Ctrl+↑/↓`, Alt-screen pass-through, Raw PTY toggle). Stop for approval before Phase 2.

### Q3 — Retroactive Blockification on Mode Switch (`Raw PTY -> Block View`)
- **Chosen Option: `(a)` Leave pre-block output as unblockified history text at the top of the block list.**
- **UX Consequences:**
  1. Toggling `Raw PTY -> Block View` is **always immediately available** (never disabled waiting for a future prompt, unlike `(c)`), and never synthesizes fake command headers or timestamps around arbitrary prior PTY output (unlike `(b)`).
  2. Existing `mMainBuffer` scrollback accumulated before OSC 133 / Block capture (or inside unhooked subshells / SSH) renders seamlessly in an **`UnblockifiedTranscriptHeader`** section above Block #1 using the exact same `BlockRowsCanvas` renderer (pixel-identical typography and URL/file tap support, minus block strips, sticky headers, and block selection outlines).
  3. The next executed command appends Block #1 cleanly beneath that history header; later block evictions (Q5) merge back into that same header prefix naturally.

### Q4 — Robolectric Upgrade Verification (Observed Spike Results)
Ran a clean throwaway spike (`spike/robolectric-q4` off `9b128cc`, reverted completely afterward) bumping `org.robolectric:robolectric` `4.10 -> 4.14.1` (`includeAndroidResources = true` was already present in `app/build.gradle`) and running `./gradlew :app:testDebugUnitTest` on this host (**OpenJDK 21.0.13**, **`linux-aarch64` Android/Termux**):
1. **Bump `4.10 -> 4.14.1` alone did NOT pass:** `FileReceiverActivityTest > testIsSharedTextAnUrl` changed from `ShadowActivityThread.reset: ActivityThread not set` (`4.10`) to `java.lang.UnsatisfiedLinkError: no conscrypt_openjdk_jni-linux-aarch_64 in java.library.path` (`4.14.1`).
   - *Actual cause 1 (Conscrypt & NativeRuntime on `linux-aarch64`):* `conscrypt-openjdk-uber:2.5.2` only bundles `windows-x86`, `windows-x86_64`, `linux-x86_64`, and `osx-x86_64` binaries (`nativeruntime-dist-compat:1.0.16` likewise omits `native/linux/aarch64/`). In Robolectric `4.14.1`, `ConscryptModeConfigurer` auto-disables Conscrypt only on `Mac && aarch64`, omitting `Linux && aarch64`.
2. **Adding `conscryptMode=OFF`, `graphicsMode=LEGACY`, `sqliteMode=LEGACY` exposed cause 2:** `MavenArtifactFetcher` failed with `SocketException` trying to download `org.robolectric:android-all-instrumented:15-robolectric-12650502-i7` at test runtime because `compileSdkVersion=36` causes Robolectric `4.14.1` to default to SDK 35, whereas **SDK 28 (`9-robolectric-4913185-2-i7`, matching `targetSdkVersion=28`)** is already cached locally in `~/.m2`.
3. **Proved Fix (`BUILD SUCCESSFUL`):** Setting `robolectric.enabledSdks = "28"` plus `robolectric.conscryptMode = "OFF"`, `robolectric.graphicsMode = "LEGACY"`, `robolectric.sqliteMode = "LEGACY"` in `testOptions.unitTests.all` caused **`./gradlew :app:testDebugUnitTest` to pass all 14/14 tests** (`0` failures, `0` skipped, including `FileReceiverActivityTest > testIsSharedTextAnUrl`).
- **Plan Adjustment:** Phase 1a folds in `robolectric:4.14.1`, `enabledSdks = "28"`, and automatic `os.arch == "aarch64" && Linux` fallback to `OFF`/`LEGACY` in `app/build.gradle` (preserving native graphics on `x86_64` CI runners). *Transparent caveat:* Compose `ui-test-junit4` was **not** tested under `graphicsMode=LEGACY` in this spike; Phase 1b will verify whether Compose `createComposeRule()` works under `LEGACY` or requires `x86_64`/on-device `androidTest` fallback for Compose UI assertions.

### Q5 — Block Eviction Semantics (Cap = 200 Blocks)
- **Chosen Semantics:** The block list is strictly a **metadata view/index over `TerminalBuffer.mMainBuffer` (`{ id, command, exitCode, startTimestampMs, durationMs, startAbsoluteRow, endAbsoluteRow }`)** — blocks do **not** exclusively own or delete transcript row memory.
- **Behavior at Cap > 200 Blocks:** Evicting the oldest block (Block #1) drops only its block metadata and UI chrome (sticky command header, status strip, duration/timestamp badge, selection ID, per-block search/AI attachments). The physical rows inside `TerminalBuffer.mMainBuffer` remain untouched and seamlessly fold into the `UnblockifiedTranscriptHeader` prefix at the top of the Block View (unified with Q3(a)).
- **Explicit Limitation:** `TerminalBuffer` itself is a circular buffer bounded by `mTotalRows` (`TerminalEmulator.DEFAULT_TRANSCRIPT_ROWS = 2000`, configurable in emulator up to `50000`). Once total output *lines* exceed `mTotalRows` (or `clearTranscript()` `\x1b[3J` runs), `TerminalBuffer.scrollDownOneLine()` overwrites its oldest ring-buffer rows independently of the 200-block cap. Storing block bounds as row indices (rather than duplicating megabytes of lines in heap objects) prevents mobile OOM and handles terminal width rotation reflow cleanly via marker adjustment inside `TerminalBuffer.resize()`.

---

## 2. Observed Baseline & Cross-Cutting Theme Direction

- **Modules (`settings.gradle`):** `:app` (Kotlin `2.2.20`, Compose BOM `2025.06.01`, Java 11), `:termux-shared` (Java 8, Markwon `4.6.2`), `:terminal-emulator` (Java 8 + JNI `termux.c`), `:terminal-view` (Java 8).
- **SDK Constraint (`gradle.properties`):** Preserve `minSdkVersion=21`, `targetSdkVersion=28` (required for `$PREFIX/bin` `execve`), `compileSdkVersion=36`.
- **Uncommitted Working Tree State:** Existing local edits in `SshProfileStore.java`, `TextExpansionStore.java`, `GlassTokens.kt`, `DevBrowserActivity.kt`, `TermuxActivityRootView.java`, plus untracked test files under `app/src/test/java/com/newtermux/` are preserved cleanly while migrating Glass styling to Warp.
- **Confirmed Choices:**
  - Keep a collapsible **Custom ANSI Colors** editor alongside the 3 curated themes (**Warm Dark** default, **Solarized Dark**, **High Contrast**) + Background Image & Scrim setting.
  - Use `com.squareup.okhttp3:okhttp:4.12.0` (cached) and an `AndroidKeyStore` AES/GCM helper with API 21–22 fallback (no alpha `security-crypto` needed).
  - Preserve all non-block tabs (`Terminal`, multi-tab `DevBrowser`, `FileTabViewer`, `LocalhostServersTab`), `TermuxService` process protection (`specialUse` FGS + `SessionStatePersister`), and dual-screen `ControlDeckPresentation`.

---

## 3. Dependencies (`app/build.gradle`)

No dependencies added to `:terminal-emulator`, `:terminal-view`, or `:termux-shared`. Changes in `app/build.gradle`:

| Dependency / Config | Value | Purpose | Phase |
|---|---|---|---|
| `org.robolectric:robolectric` | `4.10 -> 4.14.1` | SDK 28–35 support, fixes `FileReceiverActivityTest` on OpenJDK 21 | 1a |
| `android.testOptions.unitTests` | `enabledSdks="28"` + `aarch64` `OFF`/`LEGACY` guards | Fixes Termux `linux-aarch64` Conscrypt/NativeRuntime crash cleanly | 1a |
| `androidx.compose.ui:ui-test-junit4` | `testImplementation` & `androidTestImplementation` | UI tests for block selection & floating edit layer | 1b / 2 |
| `androidx.compose.ui:ui-test-manifest` | `debugImplementation` (cached) | Empty Compose test host manifest | 1b / 2 |
| `com.squareup.okhttp3:okhttp:4.12.0` | `implementation` (cached) | AI HTTP/SSE client, single-flight cancellation, timeouts | 5 |

---

## 4. Target File Structure

```text
docs/antigravity/
  [NEW] implementation-plan.md
  [NEW] phase-1a-diff.md / phase-1a-tests.md
  [NEW] phase-1b-diff.md / phase-1b-tests.md
  [NEW] phase-{2..5}-diff.md / phase-{2..5}-tests.md

terminal-emulator/src/main/java/com/termux/terminal/
  [MODIFY] TerminalBuffer.java                        (mTotalTranscriptRows counter, row-marker reflow in resize(), read-only row slice accessors)
  [MODIFY] TerminalEmulator.java                      (parse OSC 133 A/B/C/D and OSC 7 file://cwd, cursor style/pos getters)
  [MODIFY] TerminalSession.java                       (emit OSC callbacks, monotonic buffer offsets)
  [MODIFY] TerminalSessionClient.java                 (onOsc133Event, onOsc7CwdChanged default methods)
terminal-emulator/src/test/java/com/termux/terminal/
  [NEW] OscShellIntegrationTest.java                  (OSC 133 A/B/C/D, OSC 7, resize reflow markers, ring eviction)

terminal-view/src/main/java/com/termux/view/
  [MODIFY] TerminalRenderer.java                      (expose TerminalRowPainter / renderRowSlice + drawCursorCell sharing exact paints/metrics)
terminal-view/src/main/java/com/termux/view/
  [NEW] TerminalRowPainter.java                       (stateless row-range + cursor Canvas painter shared by TerminalView and BlockRowsCanvas)

app/src/main/java/com/newtermux/glass/                [DELETE ENTIRE PACKAGE in Phase 1a]
  [DELETE] GlassAvailability.kt, GlassComponents.kt, GlassModifier.kt, GlassPageBackdrop.kt, GlassShaders.kt, GlassTokens.kt
app/src/test/java/com/newtermux/glass/
  [DELETE] GlassTokensTest.kt                         [in Phase 1a]

app/src/main/java/com/newtermux/theme/                [NEW in Phase 1a]
  [NEW] CuratedThemes.kt                              (Warm Dark, Solarized Dark, High Contrast + optional Custom ANSI override)
  [NEW] WarpComponents.kt                             (WarpBackground, WarpTopBar, WarpCard, WarpBottomSheet, WarpChip, WarpDivider)
  [NEW] MotionPreferences.kt                          (rememberReduceMotion, animation duration helper)
  [NEW] BackgroundImageStore.kt                       (photo copy into filesDir/backgrounds/, downsampled cache, scrim opacity)

app/src/main/java/com/newtermux/blocks/               [NEW in Phase 1b]
  [NEW] TerminalBlock.kt                              (Block {id, command, exitCode, timestamp, duration, startAbsRow, endAbsRow}, BlockDensity)
  [NEW] TerminalTapResolver.kt                        (shared URL / file-path / OSC-8 cell resolver extracted from TermuxTerminalViewClient)
  [NEW] BlockRowsCanvas.kt                            (zero-alloc Compose Canvas wrapper calling TerminalRowPainter + TalkBack semantics)
  [NEW] BlockSessionController.kt                     (per-session OSC 133 + fallback heuristic, 200-block view eviction -> history prefix, CWD/git context)
  [NEW] BlockWorkspaceHost.kt                         (LazyColumn stream, sticky headers, selection outline, Ctrl+Up/Down, Alt-screen/Raw toggle)
  [NEW] BlockActions.kt                               (Copy, Share, Select Text sheet, Save-as-Workflow hook [P4], AI actions hook [P5])

app/src/main/java/com/newtermux/input/                [NEW in Phase 2]
  [NEW] TwoRowInputBar.kt                             (Row 1: user@host / cwd / git branch; Row 2: draft preview trigger, ExtraKeys, Workflow, Send)
  [NEW] FloatingEditLayer.kt                          (portrait/landscape sheet: free cursor, Shift+Enter multi-line, soft wrap, Send, expansions)
  [NEW] ContextChipsRow.kt                            (scrollable cwd/git/project/localhost chips + quick action menus)
  [NEW] InputDraftStore.kt                            (per-session draft + cursor persistence across process death + saved snippet store)

app/src/main/java/com/newtermux/workspace/            [NEW in Phase 3]
  [NEW] CollapsibleTabDrawer.kt                       (left-edge vertical drawer: sessions + last command + close, browser/file/localhost tabs)
  [NEW] SplitPaneWorkspace.kt                         (multi-pane split layout + 0.82f inactive pane dimming + focus routing)

app/src/main/java/com/newtermux/workflows/            [NEW in Phase 4]
  [NEW] Workflow.kt                                   (Workflow, PlaceholderToken)
  [NEW] WorkflowMarkdownParser.kt                     (YAML front-matter + ```lang {{token=default}}``` parser/serializer + H1 fallback import)
  [NEW] WorkflowRepository.kt                         (atomic ~/.termux/workflows/ store + starter templates)
  [NEW] WorkflowSheets.kt                             (picker quick-access sheet, inline token inputs, block-to-workflow & CRUD editor)
  [NEW] WorkflowShareHelper.kt                        (FileProvider ACTION_SEND export + import confirmation sheet)

app/src/main/java/com/newtermux/ai/                   [NEW in Phase 5]
  [NEW] AiProvider.kt                                 (provider interface, AiRequest, AiResponse, AiError/OfflineState, AiConfig)
  [NEW] AiProviders.kt                                (OpenAI-compatible/Ollama, Anthropic, Gemini OkHttp clients)
  [NEW] AiKeyStore.kt                                 (AndroidKeyStore AES/GCM + API 21-22 fallback key storage)
  [NEW] AiContextBuilder.kt                           (structured block payload, 6k-char smart truncation, secret redaction)
  [NEW] InlineAiController.kt                         (0 auto-retries, max-1 concurrent cancel-on-new, Accept->editor / Edit / Dismiss, TalkBack announce)

app/src/main/java/com/newtermux/compose/
  [MODIFY] NewTermuxComposeTheme.kt, NewTermuxMenuStyle.kt
app/src/main/java/com/newtermux/features/
  [MODIFY] NewTermuxSettings.java, NewTermuxColorTheme.java, NtPopupMenu.java, LocalhostServersTab.kt
app/src/main/java/com/newtermux/deck/
  [MODIFY] ControlDeck.kt
app/src/main/java/com/newtermux/update/
  [MODIFY] UpdateUi.kt
app/src/main/java/com/termux/app/
  [MODIFY] TermuxActivity.java, TermuxInstaller.java, terminal/TermuxTerminalViewClient.java,
           terminal/TermuxTerminalSessionActivityClient.java, terminal/io/TerminalToolbarViewPager.java,
           api/file/FileReceiverActivity.java,
           activities/{SettingsActivity,ThemePickerActivity,DevBrowserActivity,FileManagerActivity,PackageManagerActivity,SshManagerActivity}.kt
app/src/main/res/
  [MODIFY] layout/{activity_termux,newtermux_toolbar,newtermux_status_line}.xml,
           values/newtermux_colors.xml, values*/newtermux_styles.xml,
           drawable/{bg_popup_menu,bg_package_button,bg_drawer,nt_bottom_sheet_bg,nt_terminal_bg}.xml
```

---

## 5. Per-Phase Breakdown

### Phase 1a — Visual Foundation & Liquid Glass Removal
*Reasoning tier: `high`.*

1. **Gradle & Robolectric Spike Fold-In:**
   - Update `app/build.gradle`: `robolectric:4.14.1`, `robolectric.enabledSdks = "28"`, conditional `aarch64` Linux `OFF`/`LEGACY` properties, `ui-test-junit4` + `ui-test-manifest`.
2. **Design System Replacement (`com.newtermux.glass` -> `com.newtermux.theme`):**
   - Delete `com.newtermux.glass.*` and `GlassTokensTest.kt`; relocate `rememberReduceMotion()` to `MotionPreferences.kt`.
   - Implement `CuratedThemes.kt` with **Warm Dark** (default: `#121210` bg, `#1B1A18` surface, `#22201D` card, `#2F2C28` 1dp border, `#FAF9F6` off-white text, `#D49B54` warm sand accent), **Solarized Dark** (`#002B36` / `#073642` / `#FAF9F6` / `#268BD2`), **High Contrast** (`#000000` / `#0D0D0D` / `#FAF9F6` / `#58A6FF`, ≥ 7:1 contrast, `1.5–2dp` borders), plus collapsible **Custom ANSI Colors** override synced to `~/.termux/colors.properties` via `NewTermuxColorTheme.java`.
   - Build flat `WarpComponents.kt` (`0dp` shadows, `1dp` hairline borders, `FontFamily.SansSerif`, zero `RenderEffect` / AGSL / backdrop recording).
   - Migrate all 9 Compose/Activity surfaces (`SettingsActivity` — replacing the "Liquid Glass" toggle with Theme + Reduced Motion + Density placeholders; `ThemePickerActivity`, `PackageManagerActivity`, `SshManagerActivity`, `FileManagerActivity`, `DevBrowserActivity`, `LocalhostServersTab`, `UpdateUi`, `ControlDeck`) and XML/Views (`TermuxActivity.buildGlassTabView` -> flat tab pill, `NtPopupMenu`, `newtermux_colors.xml`, `bg_popup_menu.xml`, `bg_package_button.xml`, `newtermux_toolbar.xml`, `newtermux_status_line.xml`).
3. **Optional Background Image + Scrim:**
   - Photo Picker / `OpenDocument` in `ThemePickerActivity`, copied off-main-thread to `filesDir/backgrounds/current_bg`, downsampled once to viewport dimensions, rendered behind `terminal_container` under a flat active-theme color scrim (`60%–95%`, default `82%`) + one-tap Clear.
4. **Acceptance & Verification (Phase 1a):**
   - `CuratedThemesTest`: WCAG contrast checks for High Contrast (`>= 7:1`), palette persistence, background scrim bounds.
   - Zero occurrences of `com.newtermux.glass`, `RuntimeShader`, or blur modifiers across repo; full `:app:testDebugUnitTest` passes (`14/14`+ green) and APK builds cleanly.
   - Screenshots of Warm Dark, Solarized Dark, High Contrast, Background Image + Scrim, Settings, ThemePicker, Dev Browser.
   - Deliver `docs/antigravity/phase-1a-diff.md` and `phase-1a-tests.md`; **wait for approval before Phase 1b**.

---

### Phase 1b — Block Visual System
*Reasoning tier: `xhigh`.*

1. **`:terminal-emulator` Shell Integration & Transcript Indexing:**
   - Track monotonic 64-bit `mTotalTranscriptRows` in `TerminalBuffer.java`; adjust block row bounds cleanly across `resize()` rewrap and `clearTranscript()`.
   - Parse `OSC 133;A/B/C[;cmdline=...]/D[;exitCode]` and `OSC 7;file://host/cwd` in `TerminalEmulator.doOscSetTextParameters`, emitting callbacks via `TerminalSessionClient` on `MainThreadHandler`.
   - Install idempotent `~/.config/betermux/integration.{zsh,bash}` via `TermuxInstaller` + fallback command-boundary tracker (`exitCode = null`) when OSC 133 is absent.
2. **`:terminal-view` Fidelity Sharing (`TerminalRowPainter`):**
   - Extract stateless `TerminalRowPainter` from `TerminalRenderer.java` supporting slicing arbitrary `TerminalRow[]` ranges + active `(cursorRow, cursorCol, cursorStyle, blink)` drawing with exact `TerminalRenderer` paints and metrics.
   - Extract `TerminalTapResolver` from `TermuxTerminalViewClient.onSingleTapUp` so both `TerminalView` and `BlockRowsCanvas` resolve URLs, files, and OSC-8 targets identically.
3. **Block Stream UI & Interactions (`com.newtermux.blocks`):**
   - `BlockSessionController`: manages up to **200** `TerminalBlock` descriptors per session; oldest eviction drops only block chrome while preserving `TerminalBuffer` transcript rows in `UnblockifiedTranscriptHeader` (Q3(a) + Q5); coalesces live streaming updates at ~30 Hz.
   - `BlockWorkspaceHost`: Compose `LazyColumn` rendering `UnblockifiedTranscriptHeader` (when non-empty) + block `stickyHeader`s + 50-line `BlockRowsCanvas` slices (zero per-frame allocations, TalkBack `semantics`).
   - Subtle `3dp` left-edge strip (neutral `#4A4742`/muted on exit 0 or `null`; warm red/amber `#D9534F`/`#D9822B` on non-zero; no full-card background tint).
   - Sticky command header pinned at viewport top during long output; tap jumps cleanly to block start (`scrollToItem` or instant jump under `rememberReduceMotion()`).
   - `1dp` horizontal dividers + **Comfort / Compact** spacing toggle persisted in `NewTermuxSettings`.
   - **Block selection mode:** Long-press enters multi-select (`1.5dp` crisp outline, NO fill); tap toggles additional blocks; action bar with **Copy** (combined commands+outputs), **Share** (`ACTION_SEND`), and single-block **Select Text** (substring `SelectionContainer` sheet).
   - **Hardware keyboard nav:** `Ctrl+↑` / `Ctrl+↓` jumps focus/scroll between blocks (`KeyEvent` handler in workspace; passes through to PTY in Raw/Alt-screen mode).
   - **TUI & Raw PTY Pass-Through:** Auto-switch to full `TerminalView` whenever `isAlternateBufferActive()` + one-tap **Raw PTY** pill in toolbar; switching Raw -> Block leaves prior scrollback cleanly in `UnblockifiedTranscriptHeader` (Q3(a)).
4. **Acceptance & Verification (Phase 1b):**
   - Unit tests: `OscShellIntegrationTest`, `TerminalBlockModelTest` (133 lifecycle, fallback boundaries, resize reflow, 200-block chrome eviction keeping transcript intact, Comfort/Compact density).
   - UI tests (`BlockSelectionUiTest`): Verify `createComposeRule()` under Q4 Robolectric config (or device `androidTest` if `LEGACY` graphics limitation hits Compose Canvas): long-press outline, multi-select Copy, sticky header jump, `Ctrl+↑/↓`.
   - Scroll jank check vs baseline; screenshots of Block Stream (exit 0 + non-zero), Sticky Header, Comfort vs Compact, Selection Outline, Raw/Alt-Screen TUI.
   - Deliver `phase-1b-diff.md` and `phase-1b-tests.md`; **wait for approval before Phase 2**.

---

### Phase 2 — Editor-Style Input
*Reasoning tier: `high`.*

1. **Two-Row Input Bar (`TwoRowInputBar.kt`):**
   - Top row: prompt context (`user@host`, smart shortened `cwd`, git branch read event-driven from `.git/HEAD` on prompt/CWD updates — zero background polling).
   - Bottom row: tappable command draft box opening edit layer, quick **Send**, **Extra Keys** tray toggle (keeping `ExtraKeysView` & `ControlDeckPresentation` functional), and Phase 4 **Workflows** trigger slot.
2. **Floating Edit Layer (`FloatingEditLayer.kt` & `InputDraftStore.kt`):**
   - Opens responsive Compose sheet adapting cleanly to portrait & landscape (`imePadding()`, capped max height in landscape).
   - Monospace `BasicTextField` with free cursor placement, `Shift+Enter` newline insertion, soft wrapping, `TextExpansionStore` trigger suggestions, and **Send** dispatching whole multi-line command (bracketed-paste aware when `mEmulator.isBracketedPasteEnabled()`).
   - **Draft persistence (A3):** Edits persist per-session (`SharedPreferences` + JSON backup) surviving sheet dismissal and process death. **Closing a session with a non-empty draft** prompts **"Save draft as snippet" / "Discard & close" / "Cancel"** (composed cleanly with process-protection exit confirmation — never silently discards).
3. **Context Chips (`ContextChipsRow.kt`):**
   - Horizontally scrollable chips for `cwd` (copy, insert path, open in File Manager tab), `git branch` (insert branch, `git status/diff/log`), and detected project/localhost context (`gradlew`, `package.json`, `Cargo.toml`, live `:port` -> open in Dev Browser), refreshed event-driven on `OSC 7`/`133;D`/tab focus.
4. **Input Position Toggle (Bottom default vs Top):**
   - Persisted in `NewTermuxSettings`; switches input bar above vs below the block stream cleanly alongside `TermuxActivityRootView` insets.
5. **Acceptance & Verification (Phase 2):**
   - Unit tests: `InputDraftStore` survival & draft-on-close snippet save, context chip detection.
   - UI tests (`InputDraftAndEditLayerUiTest`): portrait & landscape sheet rendering, multi-line input, close preserves draft, top vs bottom placement toggle, draft-on-close confirmation prompt.
   - Deliver `phase-2-diff.md` and `phase-2-tests.md`; **wait for approval before Phase 3**.

---

### Phase 3 — Navigation & Workspace
*Reasoning tier: `high`.*

1. **Collapsible Vertical Tab Drawer (`CollapsibleTabDrawer.kt`):**
   - Left-edge slide-out drawer listing terminal sessions (title, last command summary, exit status indicator, close button routed through draft + process-protection confirmations), Dev Browser tabs, File Manager tabs, Localhost tab, and utility triggers (`+ New Session`, `SSH`, `Packages`, `Settings`, `Quick Commands`).
   - Streamlines [newtermux_toolbar.xml](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/res/layout/newtermux_toolbar.xml) with drawer button + concise active session label while keeping horizontal overflow uncluttered on small screens.
2. **Split Panes & Inactive Pane Dimming (`SplitPaneWorkspace.kt`):**
   - Split terminal workspace container hosting 2+ panes bound to active sessions (horizontal/vertical split, tap-to-focus, close pane).
   - Inactive panes apply `Modifier.alpha(0.82f)` (18% opacity reduction, within 15–20%) while the focused pane stays at `1.0f` alpha with subtle `1dp` active border and receives keyboard/input-bar routing.
3. **Full UI Zoom (`85%–130%`) Respecting System Font Scaling:**
   - Persisted `ui_zoom_factor` (`0.85f .. 1.30f`, default `1.0f`) applied via `LocalDensity` as `Density(density = base.density * zoom, fontScale = base.fontScale)` so both chrome and text zoom proportionally **without overriding Android system `fontScale`** (and synced to `TerminalRowPainter` / `TerminalView` cell sizes).
4. **In-Block Search:**
   - Per-block search icon / `Ctrl+F` opens an inline compact filter bar inside that block showing query, match count, **Filter lines / Highlight all** mode toggle, and Close (restores untouched block view).
5. **Acceptance & Verification (Phase 3):**
   - Unit tests (`WorkspaceZoomAndSearchTest`): zoom clamping & `fontScale` preservation, split pane focus & `0.82f` alpha calculation, case-insensitive in-block line filter.
   - Full non-regression check on existing tabs, Dev Browser, `TermuxService` process protection, and `ControlDeck`.
   - Deliver `phase-3-diff.md` and `phase-3-tests.md`; **wait for approval before Phase 4**.

---

### Phase 4 — Workflows & Sharing
*Reasoning tier: `high`.*

1. **Workflow Markdown Format (A1 — YAML Front-Matter + Fenced Code):**
   - Canonical `.md` format with YAML front-matter (`title`, `description`, `tags`, `shell`) + fenced code block supporting `{{placeholder}}` and `{{placeholder=default}}` tokens — parsed cleanly in pure Kotlin with zero YAML library dependency, plus graceful fallback parsing for headerless/`# Title` fenced Markdown snippets on import:
     ```markdown
     ---
     title: Restart Dev Server
     description: Kill port listener and start dev server
     tags: [dev, node]
     ---

     ```bash
     kill $(lsof -t -i:{{port=3000}}) || true
     npm run dev -- --port {{port}}
     ```
     ```
2. **Workflow CRUD & Save from Blocks (`WorkflowRepository.kt`, `WorkflowSheets.kt`):**
   - Atomic storage under `~/.termux/workflows/workflows.json` surviving process death; ships starter Termux/git templates.
   - Multi-block selection **"Save as Workflow"** extracts commands chronologically, supports tokenizing arguments into `{{placeholders}}`, and full Create/Edit/Delete management.
3. **Android Share Intent Export / Import (`WorkflowShareHelper.kt`, `FileReceiverActivity.java`):**
   - Export via `FileProvider` (`.md` attachment) or plain `ACTION_SEND` text.
   - Import intercepts `ACTION_SEND` / `ACTION_VIEW` for `.md` / Markdown text in `FileReceiverActivity.java`, opens a **Workflow Import Preview & Confirm** sheet if valid, and falls back untouched to standard URL/file receiver behavior otherwise.
4. **Workflow Picker Quick-Access Sheet:**
   - Launched from the Phase 2 Input Bar icon; filters by title/tags/command, renders inline `{{placeholder}}` fields when present, and inserts substituted command straight into the Phase 2 input draft (**never auto-executes**).
5. **Acceptance & Verification (Phase 4):**
   - Unit tests (`WorkflowParserAndStoreTest`): YAML front-matter + H1 fallback parsing, token defaults/multi-line bash, atomic CRUD reload survival, round-trip `export -> import -> parameterize -> insert` across simulated restart.
   - Deliver `phase-4-diff.md` and `phase-4-tests.md`; **wait for approval before Phase 5**.

---

### Phase 5 — AI Agent (Minimal Loop)
*Reasoning tier: `xhigh`.*

1. **Block-Integrated Workflow (No Separate Chat Tab):**
   - No separate AI tab in drawer or toolbar. Failed blocks (`exitCode != 0`) display quick **"Explain error"** and **"Propose fix"** actions; Block Selection Mode also exposes AI actions on selected block(s).
2. **Structured Block Context & Guards (`AiContextBuilder.kt`, `InlineAiController.kt` — A2):**
   - Assembles `{ command, output, exitCode, timestamp, duration }` + `cwd` / shell / detected project context within a **~6,000-character deterministic budget** (preserving command + stderr/tail lines), redacting common secret patterns (`API_KEY`, `TOKEN`, `Bearer ...`, private keys).
   - **Hard Safety & Cost Guards (A2):**
     - **Max automatic retries:** **`0`** (manual **Retry** button only).
     - **Max output tokens per request:** Configurable `ai_max_tokens` (`256 .. 4096`), **default `1024`**.
     - **Max concurrent in-flight requests:** **`1` global request (`cancel-on-new`)** — starting a new request or tapping **Dismiss** immediately cancels the active OkHttp `Call`.
3. **Provider-Agnostic Client, Key Storage & Offline State (`AiProvider.kt`, `AiProviders.kt`, `AiKeyStore.kt`):**
   - Adapters for **OpenAI-compatible / Local Ollama**, **Anthropic Messages**, and **Google Gemini** behind one interface, configured in Settings.
   - API key encrypted cleanly via `AiKeyStore` (`AndroidKeyStore` AES/GCM on API 23+, Keystore RSA fallback on API 21–22).
   - **Offline / Disabled State:** Disabled or empty key shows **"AI disabled — Configure key in Settings"** linking straight to AI Settings; network failure/4xx/5xx renders concise inline error + manual **Retry** / **Settings** / **Dismiss**.
4. **Inline Suggestion Block & Accessibility (`BlockWorkspaceHost.kt` — A4):**
   - Renders `AiSuggestionBlock` inline directly beneath the inspected block with a distinct left accent strip, explanation text, and boxed proposed command + **Accept** / **Edit** / **Dismiss** buttons:
     - **Accept** inserts command into `InputDraftStore` / Phase 2 input editor — **never writes directly to `TerminalSession` (`session.write`)**.
     - **Edit** opens the Floating Edit Layer pre-populated with the proposed command.
     - **Dismiss** cancels any streaming call and removes the inline block.
   - **Screen-Reader Announcement (A4):** `AiSuggestionBlock` attaches `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` and triggers a single `View.announceForAccessibility(...)` once a suggestion completes (`"AI fix ready"` / `"AI explanation ready"`) or errors, alongside explicit `contentDescription` on every action button.
5. **Acceptance & Verification (Phase 5):**
   - Unit tests (`AiControllerAndContextTest`): context redaction & truncation, provider adapter request/response formatting, `0` auto-retries, `1` concurrent cancel-on-new, `Accept` populates editor and never auto-executes, disabled/offline state handling.
   - UI test: verify `AiSuggestionBlock` renders inline beneath target block with Accept/Edit/Dismiss and accessibility semantics, not a modal or chat tab.
   - Deliver `phase-5-diff.md` and `phase-5-tests.md`.
