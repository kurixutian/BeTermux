# Phase 1a — Foundation Diff Report

## Scope Confirmation
Phase 1a strictly covered mechanical visual-foundation work within `:app`:
- Removed the Liquid Glass shader/blur subsystem (`com.newtermux.glass`).
- Shipped the three curated Warp-inspired themes (**Warm Dark** default, **Solarized Dark**, **High Contrast**) + collapsible/accessible Custom ANSI palette + optional Background Image & Contrast Scrim system.
- Migrated all UI screens, overlays, pop-up menus, tabs, drawables, and color tokens from Liquid Glass / Obsidian-Violet to flat, border-structured Warp chrome.
- **Boundary intact**: zero modifications to `:terminal-emulator`, `:terminal-view`, `:termux-shared`, OSC parsing, or Block models (deferred to Phase 1b).

---

## Files Added
- `app/src/main/java/com/newtermux/theme/CuratedThemes.kt` — `WarpPalette` data class and `CuratedThemes` (`WarmDark`, `SolarizedDark`, `HighContrast`, `normalizeKey`, `resolve`, WCAG `relativeLuminance` / `contrastRatio`).
- `app/src/main/java/com/newtermux/theme/WarpComponents.kt` — Flat border-structured Compose components (`LocalWarpPalette`, `WarpBackground`, `WarpTopBar`, `WarpCard`, `WarpBottomSheet`, `WarpSegmentedBar`, `WarpSegmentedItem`, `WarpChip`, `Modifier.warpSurface`).
- `app/src/main/java/com/newtermux/theme/BackgroundImageStore.kt` — Off-main-thread image import/downsampling (`<= 2048px`), atomic write to `filesDir/backgrounds/wallpaper.img`, in-memory revision-cached bitmap loading, `clearImage`, and clamped scrim opacity persistence (`0.55f..0.96f`, default `0.84f`).
- `app/src/main/java/com/newtermux/theme/MotionPreferences.kt` — System reduce-motion observer (`MotionPreferences.shouldReduceMotion`, `rememberReduceMotion`) relocated out of the deleted `glass` package.
- `app/src/test/java/com/newtermux/theme/CuratedThemesTest.kt` — 8 Robolectric unit tests covering curated theme contrast ratios (WCAG AA/AAA), legacy key normalization, `colors.properties` generation, Java surface color helpers, background scrim clamping/persistence, and `MotionPreferences`.

## Files Deleted
- `app/src/main/java/com/newtermux/glass/GlassAvailability.kt`
- `app/src/main/java/com/newtermux/glass/GlassComponents.kt`
- `app/src/main/java/com/newtermux/glass/GlassModifier.kt`
- `app/src/main/java/com/newtermux/glass/GlassPageBackdrop.kt`
- `app/src/main/java/com/newtermux/glass/GlassShaders.kt`
- `app/src/main/java/com/newtermux/glass/GlassTokens.kt`
- `app/src/test/java/com/newtermux/glass/GlassTokensTest.kt`

## Files Modified (Phase 1a)
### Build & Test Configuration
- `app/build.gradle` — Bumped `org.robolectric:robolectric` from `4.10` to `4.14.1` and configured `testOptions.unitTests.all` (`robolectric.enabledSdks="28"`, plus conditional `conscryptMode=OFF` / `graphicsMode=LEGACY` / `sqliteMode=LEGACY` on `linux && aarch64` hosts where native JNI/conscrypt artifacts are unavailable).

### Theme Engine & Stores
- `app/src/main/java/com/newtermux/features/NewTermuxColorTheme.java` — Replaced the 13 legacy presets with `warm_dark` (default; legacy keys normalize to `warm_dark`), `solarized_dark`, `high_contrast`, and `custom`; added Java int accessors (`getBackgroundColorInt`, `getSurfaceColorInt`, `getSurfaceElevatedColorInt`, `getBorderSubtleColorInt`) and `getThemeContent(Context, String)` fallback handling; notified `WarpThemeState` on changes.
- `app/src/main/java/com/newtermux/features/NewTermuxTheme.java` — Updated `DEFAULT_COLOR` to `0xFFD49B54` (Warm Sand), refreshed preset swatches & `COLOR_NAMES`, and notified `WarpThemeState` on accent updates.
- `app/src/main/java/com/newtermux/compose/NewTermuxComposeTheme.kt` — Removed `LocalGlassEnabled` / `LocalGlassToggle`; added `WarpThemeState`, `WarpTypography` (`FontFamily.SansSerif`), `LocalWarpPalette`, and dynamic Material 3 `darkColorScheme` mapping.
- `app/src/main/java/com/newtermux/compose/NewTermuxMenuStyle.kt` — Switched `Modifier.outlinedMenuCard()` and `MenuItemDivider()` from translucent glass tinting to flat `warpSurface` + `borderSubtle`.

### Activities, Overlays & Terminal Chrome
- `app/src/main/java/com/termux/app/activities/SettingsActivity.kt` — Migrated `SettingsScaffold`, `NavRow`, and `SwitchRow` to `WarpBackground` / `WarpTopBar` / `WarpCard`; removed the `"Liquid Glass"` switch and its Appearance header inside `FeaturesScreen` (preserving top-level `"Appearance"` navigation to `ThemePickerActivity`).
- `app/src/main/java/com/termux/app/activities/ThemePickerActivity.kt` — Migrated to `WarpBackground` / `WarpTopBar`; displays the 3 curated theme cards, an inline `"Custom ANSI Colors"` editor card, and a `"Background Image"` section with photo picker (`ActivityResultContracts.GetContent`), Clear button, and Contrast Scrim slider (`55%–96%`).
- `app/src/main/java/com/termux/app/activities/PackageManagerActivity.kt` — Migrated `GlassBackground` / `GlassTopBar` / `GlassTabBar` / `GlassCard` to `WarpBackground` / `WarpTopBar` / `WarpSegmentedBar` / `WarpCard`.
- `app/src/main/java/com/termux/app/activities/SshManagerActivity.kt` — Migrated to `WarpBackground` / `WarpTopBar` / `WarpCard`.
- `app/src/main/java/com/termux/app/activities/FileManagerActivity.kt` — Migrated all 5 screens (`FileBrowserScreen`, `MarkdownViewer`, `ImageViewer`, `TextEditorView`, `BinaryFileInfoView`) to `WarpBackground` + `.warpSurface(...)` header containers.
- `app/src/main/java/com/termux/app/activities/DevBrowserActivity.kt` — Migrated `GlassBackground` / `glassSurface` / `GlassTokens` calls to `WarpBackground` / `warpSurface` / `LocalWarpPalette` while preserving pre-existing `DisposableEffect` and `internal fun isLocalDevUrl` changes.
- `app/src/main/java/com/newtermux/features/LocalhostServersTab.kt` — Migrated to `WarpBackground` / `WarpTopBar` / `WarpCard` using `LocalWarpPalette.current.success`.
- `app/src/main/java/com/newtermux/update/UpdateUi.kt` — Migrated `UpdatePill` surface to `warpSurface` and honored `rememberReduceMotion()`.
- `app/src/main/java/com/newtermux/deck/ControlDeck.kt` — Replaced `GlassTokens` / `glassSurface` with `CuratedThemes.WarmDark` surfaces and `warpSurface` + `clickable`.
- `app/src/main/java/com/termux/app/TermuxActivity.java` — Renamed `buildGlassTabView` to `buildTabPillView` using `NewTermuxColorTheme` surface/border ints; updated `ExtraKeysView` button defaults to warm off-white + active curated surface; added `refreshTerminalBackground()` invoked in `onCreate`, `onResume`, and `TermuxTerminalSessionActivityClient.updateBackgroundColor()`.
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java` — Updated fallback theme lookup to `getThemeContent(mActivity, savedTheme)` and called `mActivity.refreshTerminalBackground()` inside `updateBackgroundColor()`.

### XML Resources, Layouts & Drawables
- `app/src/main/res/values/newtermux_colors.xml` — Replaced Obsidian-Violet hex palette with Warm Dark charcoal surfaces, `#FAF9F6` text, and `#D49B54` default accent tokens.
- `app/src/main/res/drawable/bg_popup_menu.xml` — Replaced translucent `#EC121720` / `#33FFFFFF` with `@color/nt_surface_variant` and `@color/nt_menu_outline`.
- `app/src/main/res/drawable/bg_package_button.xml` — Replaced translucent `#D9161B22` / `#2EFFFFFF` with `@color/nt_surface` and `@color/nt_border`.
- `app/src/main/res/drawable/terminal_scroll_shape.xml` — Replaced `#4D8B5CF6` purple thumb with `#66D49B54` warm sand.
- `app/src/main/res/layout/activity_termux.xml` — Added `terminal_bg_image` and `terminal_bg_scrim` inside `terminal_container` behind `TerminalView`.
- `app/src/main/res/layout/newtermux_toolbar.xml` — Updated XML comment from Liquid Glass to Warp header.

## Pre-Existing Uncommitted Tree Changes Preserved
The working tree already contained unrelated modifications prior to Phase 1a; they remain intact and untested beyond suite compilation:
- `app/src/main/java/com/newtermux/features/SshProfileStore.java`
- `app/src/main/java/com/newtermux/features/TextExpansionStore.java`
- `app/src/main/java/com/termux/app/terminal/TermuxActivityRootView.java`
- `app/src/test/java/com/newtermux/features/SshProfileStoreTest.kt`
- `app/src/test/java/com/newtermux/update/UpdateManagerVersionTest.kt`
- `app/src/test/java/com/termux/app/activities/DevBrowserUrlTest.kt`
- `docs/antigravity/implementation-plan.md`

Overall `git diff --stat app/build.gradle app/src/`: `32 files changed, 809 insertions(+), 1679 deletions(-)` (excluding untracked new files under `com.newtermux.theme` and tests).
