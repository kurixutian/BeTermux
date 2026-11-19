---
name: betermux-ui-ux
description: Audit and polish BeTermux Android UI safely.
---

# BeTermux UI/UX

Improve BeTermux as an Android productivity tool without replacing its terminal-focused identity or Termux compatibility.

## When to Use

Use for Compose or XML UI changes, visual audits, accessibility fixes, theme work, input flows, browser/file screens, and device verification in BeTermux.

## Product Direction

- **Mode:** Operate. Fast, calm, dense, and trustworthy for terminal work.
- **Identity:** Preserve the flat charcoal, Warp-inspired visual language and compact information hierarchy.
- **Compatibility:** Keep `com.termux` application identity, launcher behavior, and the Termux package ecosystem intact.
- **Primary path:** The terminal, command input, tabs, files, and browser must remain usable with one hand, hardware keys, and the Android IME.

## Procedure

1. Load the `compose`, `compose-ui`, `mobile-design-android`, and `impeccable` skills.
2. Inspect the current implementation and a device rendering before changing it.
3. Identify the blocked or confusing task first, then hierarchy, accessibility, adaptivity, and finally polish.
4. Reuse `BeTermuxComposeTheme`, `CuratedThemes`, `LocalWarpPalette`, and `WarpComponents` before adding UI code.
5. Make the smallest change that completes the affected path, including lifecycle wiring and tests.
6. Build, test, install, and inspect the result on Android before declaring success.

## UI Rules

- Draw feature colors from theme tokens; do not add raw hex colors outside the theme package.
- Give every interactive control a minimum 48dp target and an accessible name.
- Use built-in Material or Compose controls when they already provide semantics, focus, and touch feedback.
- Resolve user-facing strings from resources when touching a localized flow.
- Apply window and IME insets once per screen; never double-pad content.
- Honor reduced motion and preserve terminal text metrics and selection behavior.
- Keep loading, empty, error, disabled, and destructive states explicit without clearing useful content.
- Keep the raw PTY available; block-oriented UI must not remove an escape hatch.

## Verification

1. Run `./gradlew :app:testDebugUnitTest`.
2. Run `./gradlew :app:assembleDebug`.
3. Install `app/build/outputs/apk/debug/termux-app_apt-android-7-debug_universal.apk` with `adb install -r`.
4. Launch `com.termux/.app.TermuxActivity` and inspect portrait and landscape on the connected device.
5. Check dark appearance, large font scale, IME visibility, Back handling, and TalkBack labels.
6. Capture device screenshots when a visual change is made; restore changed device settings afterward.

## Pitfalls

- `com.termux` is the application ID even though Java sources use `com.betermux` for the redesigned feature package.
- The legacy `TerminalView` and Compose overlays coexist; changing one may leave the other visible.
- The wireless ADB device may be in a freeform window, so a full-screen screenshot can contain another app until BeTermux is brought forward.
- Do not replace the terminal engine or rewrite the whole activity to improve one screen.
