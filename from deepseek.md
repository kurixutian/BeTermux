BeTermux Compose Migration — Implementation Plan

Your spec is internally consistent and the chosen approach (screen-by-screen with a working mixed UI between slices) is the right call for an app whose risk lives in IME, focus, insets, and accessibility rather than in the rendering path. Below is a concrete plan that maps to your goals, order, and acceptance criteria, plus the specific technical traps that will decide whether each slice passes review.

Phase 0 — Pre-work (1 PR, no UI change)

· Inventory the UI surface. Enumerate every XML layout, View subclass, dialog fragment, and programmatic View construction that builds user-facing UI. Classify each as: migrate, keep as platform adapter (TerminalView, IME-related views, insets glue), or keep as non-UI XML (manifest, themes, vectors, file-paths, strings). This inventory is the scope contract for "all UI."
· Establish a Compose host baseline in the terminal activity using ComposeView (or ComponentActivity.setContent) even before any real screen migrates, so interop, previews, theme wiring, and CI screenshot infra are proven on a trivial surface first.
· Confirm the existing Compose dependency set and theme are adequate; do not add a UI library, per your builder call.
· Add a device-timing baseline for frame timings before any rendering-adjacent change, so any later performance claim has a before/after.

Phase 1 — Workspace/CWD correctness + toolbar Compose + control swap

This is the only phase that touches behavior, so it gets the tightest test envelope.

1a. Workspace restore fix (diagnose before designing)

Your "no data or preference format changes" constraint means the fix must live in restoration logic, not in schema. The likely bug shapes are:

· Restoration iterates sessions but re-binds each to the currently active workspace (or default) instead of the workspace recorded for that session.
· Active workspace is restored after sessions are recreated, so session creation falls back to Default.
· Sessions are keyed by session ID only, so a restore replay collides across workspaces.

Diagnosis step (blocking): dump the persisted session record and trace restore order. Fix should be one of: reorder restore (workspace before sessions), bind sessions to their persisted workspace key, or both. Add a unit test on the restore path that asserts each restored session's workspace equals its persisted value after simulated process death. This test must exist and pass before any toolbar Compose work lands.

1b. New-session CWD policy

· New terminal in workspace W starts at W's root (/home for Default).
· Explicit cd in that session overrides for that session only.
· Restored sessions keep their persisted CWD — do not re-apply the workspace-root policy on restore.

Add tests for all three cases, including "restore after cd" and "new session after active-workspace switch."

1c. Terminal activity toolbar → Compose, with folder/extra-keys swap

Host the toolbar in Compose above an AndroidView { TerminalView }:

```kotlin
setContent {
  BeTermuxTheme {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
      TerminalToolbar(              // Compose
        onFolder = { /* thin adapter → existing behavior */ },
        onExtraKeys = { /* thin adapter */ },
        // folder and extra-keys in the requested swapped order
      )
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> terminalView.apply { /* existing setup unchanged */ } },
        update = { it.applyInsetsFrom(composeInsets) }
      )
    }
  }
}
```

Interop contract to nail down in this slice:

· Insets: Compose is the single source of truth for the toolbar/IME inset region. Either let adjustResize shrink the whole window and do not also consume IME insets in Compose, or set adjustNothing and apply IME insets once in Compose, passing the terminal's available height down. Two owners of IME insets is the classic regression here — pick one and document it.
· Focus / IME: TerminalView must remain the input-connection owner. Keep requestFocus() and showSoftInput calls in the existing view, and verify Compose doesn't steal focus on recomposition. Tap-to-focus and back-to-show-keyboard must behave identically.
· Accessibility: Toolbar controls keep their content descriptions and traversal order; the swapped order must match the semantic order too, not just visual. The terminal remains one accessible region (as today).
· Extra-keys: Per your builder call, keep it Compose only if key dispatch and accessibility are preserved. If its existing implementation relies on InputConnection/sendKeyEvent timing that's hard to preserve through Compose callbacks, keep the smallest AndroidView/ViewGroup wrapper around the existing keys and migrate later. Document which path you took.

Acceptance for this slice: the two behavioral tests from 1a/1b pass; a Compose UI test asserts the swapped order and that each control's callback fires; manual device check confirms IME, selection, and focus unchanged.

Phase 2 — Shared/session controls

Migrate the shared controls and session-level UI (session list, workspace switcher) in 1–2 slices, each ending with a Compose replacement connected and the old inflation/wiring removed only in that slice. Use ComposeView inside existing containers to keep the rest of the screen live. Keep IDs only where external code (tests, analytics) references them.

Phase 3 — Dialogs, file/session rows, feature-specific views

· Dialogs: migrate to Dialog/AlertDialog composables; verify hardware back, cancellation, and system dialog semantics.
· Rows: migrate list row layouts; preserve click/long-press/accessibility actions and any RecyclerView recycling behavior that's actually load-bearing.
· Feature views: one slice per feature; each ends with its XML inflation removed.

Phase 4 — Cleanup

· Remove obsolete layouts and view-wiring only after each replacement is connected and checked on a device.
· Retain all non-UI XML.
· Final pass: every user-facing layout/control in agreed scope is Compose, except TerminalView, terminal input, and thin lifecycle/input/inset adapters.

Cross-cutting concerns

· Interop discipline: one owner per concern (insets, focus, IME, back handling). Write it down in the PR template.
· Thin adapters: Compose callbacks should be one-liners into existing behavior — no new state-management architecture, per your non-goal.
· Test strategy per slice: JVM unit tests for persistence/policy, Compose UI tests for the migrated surface, instrumentation for IME/focus on a real target, and screenshot check on the available Android target.
· Performance: no claims without frame-timing evidence; profile jank separately and treat Compose-vs-View rendering as neutral until measured.

Risk register (ranked)

1. Double IME inset handling — highest regression risk in Phase 1c. Mitigation: single-owner rule + manual IME test.
2. Focus theft by Compose breaking terminal input. Mitigation: keep focus/input calls in TerminalView; instrument tap-to-focus and keyboard-show in the slice's manual check.
3. Extra-keys key dispatch not surviving a pure-Compose rewrite. Mitigation: fall back to a minimal AndroidView adapter rather than forcing it.
4. Workspace restore diagnosis turning out to require a schema change your constraints forbid. Mitigation: diagnose in Phase 0; if restoration depends on data that isn't persisted today, escalate the constraint explicitly rather than silently adding a field.
5. Accessibility traversal drift from the folder/extra-keys swap. Mitigation: assert semantic order, not just visual order.

Acceptance mapping

Criterion Verified by
Restore to saved workspace after force-stop Phase 1a unit test + manual force-stop/relaunch
New terminal at workspace root; manual cd persists per-session Phase 1b unit tests + manual check
Folder/extra-keys swapped, actions intact Phase 1c Compose UI test + manual
All in-scope UI is Compose except terminal view + thin adapters Phase 0 inventory signed off at Phase 4
Each slice passes tests/build + visual check PR checklist per slice
Perf claims gated on device frame timing Baseline in Phase 0, re-measured as needed

The one thing I'd push back on before you start: make the workspace-restore diagnosis its own tiny PR that lands before any Compose change to the terminal activity. It keeps the behavioral fix reviewable in isolation and prevents a UI diff from masking a restore regression.