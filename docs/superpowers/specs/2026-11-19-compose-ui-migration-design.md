# BeTermux Compose UI Migration

## Intent

Make BeTermux easier for its maintainer to change by using Compose for all user-facing UI, while preserving the existing terminal renderer and input implementation. The migration is screen by screen so the app stays usable and each stage can be checked before the next.

## Goals

- Move active BeTermux user-facing layouts and custom view-based app UI to Compose, including active shared report/text-input screens; exclude unused legacy layouts and platform/system UI.
- Keep the custom `TerminalView`, terminal emulator, rendering, selection, and IME/input behavior intact. Compose may host the terminal view through Android interop.
- Keep terminal input surfaces such as `ExtraKeysView` and the terminal text-input pager as terminal adapters unless their dispatch and focus behavior can be preserved in Compose.
- Preserve existing navigation, accessibility, preferences, and behavior while changing presentation.
- Fix workspace session restoration so restored sessions remain in their saved workspace after process death.
- Start each newly created terminal at its active workspace root (`/home` for Default); an explicit `cd` changes that terminal's working directory. Restored terminals retain their saved working directory.
- Swap the toolbar folder and extra-keys controls as requested.

## Non-goals and constraints

- No claim that Compose alone improves rendering speed; profile actual jank separately.
- Do not rewrite terminal rendering, terminal input, process/session ownership, or emulator code.
- Android resource/configuration XML (manifest, themes, vectors, file-provider paths, strings) may remain XML; “all UI” means user-facing screens, controls, dialogs, and layout composition.
- Keep the existing Android app and its current Activity/service integration patterns; do not introduce a new navigation or state-management architecture for this migration.
- No data or preference format changes.
- Preserve `TermuxActivityRootView` inset, resize, focus, and multi-window behavior as a thin Android host adapter; do not replace its keyboard compensation with Compose insets without equivalent behavior and device verification.

## Approaches

1. **Screen-by-screen migration (chosen):** migrate a coherent screen or control group at a time, retaining a working mixed UI between stages. This limits regression scope and supports incremental review.
2. **Big-bang migration:** replace all remaining UI together. This reaches consistency sooner but makes keyboard, focus, window-inset, navigation, and accessibility regressions harder to isolate and review.

## Migration order

1. Fix workspace restore persistence and new-session root-directory policy with focused tests; convert the terminal activity's surrounding toolbar/header to Compose and swap the folder/extra-keys order while retaining the native terminal view.
2. Migrate remaining user-facing XML layouts and custom view screens in small, independently verifiable slices, prioritizing shared/session controls and then dialogs, file/session rows, and feature-specific views.
3. Remove obsolete layout inflation and view wiring only after each Compose replacement is connected and behavior has been checked. Keep XML resources that are not UI layouts.

## Acceptance

- Force-stop/relaunch restores a terminal to its saved workspace; creating a terminal in a workspace starts at that workspace root; a manually changed CWD remains valid for that session.
- The folder and extra-keys controls appear in the requested swapped order and retain their actions.
- Every active user-facing BeTermux/shared screen and control in the agreed app scope is Compose, except the terminal view, terminal input adapters, and thin Android host/platform adapters needed for lifecycle, input, and insets.
- Each migration slice passes its relevant tests/build checks and is visually checked on an available Android target. Performance claims require device frame-timing evidence.

## Builder calls

- Use the existing Compose dependencies and theme, with no new UI library.
- Preserve Java service/activity interfaces where needed and keep Compose callbacks thin adapters to existing behavior.
- Treat terminal-adjacent extra-keys controls as Compose UI only if their existing key dispatch and accessibility behavior can be preserved; otherwise keep the smallest platform adapter required.
- Keep the existing `TermuxActivityRootView` resize/inset behavior until the Compose host is proven equivalent on a device.
