The consensus is clear: the architecture direction holds, but workspace hibernation as originally specified is unsound because a terminal session is not a single process group. The roadmap below restructures implementation around that correction and locks down the lifecycle decisions before any UI work begins.

---

BeTermux Master Roadmap: Dual Sidebars, Project Workspaces & Sound Hibernation

0. Why the Original Specification Must Be Revised

The original plan assumed this would pause a workspace’s processes:

```kotlin
Os.kill(-pid, OsConstants.SIGSTOP)  // pid = shell PID
```

This is incorrect for job-control shells. The shell calls setsid() and becomes a session leader, so its PID equals its PGID and SID initially. But interactive shells create new process groups for foreground and background jobs. A vim in the foreground, or a sleep 1000 & in the background, may sit in a different PGID from the shell while remaining in the same session. Sending SIGSTOP to -shellPid will not stop those separate process groups. The workspace would show a convincing zzz badge while CPU consumption continues — a correctness failure, not a cosmetic bug.

Non-negotiable gate: Phase 4 cannot begin until Phase 0 proves, on real devices, that the suspension mechanism controls all processes belonging to the terminal session, not merely the shell’s process group.

---

1. Architecture Decisions (Locked)

D1 — Process suspension is session-wide, not PGID-only

The authoritative identity for “everything belonging to this terminal” is the Session ID (SID) returned by getsid(shellPid), not the shell’s PGID.

Mechanism (Phase 0 must validate):

1. Obtain shellPid from TerminalSession (already exposed by the native subprocess layer).
2. Compute sid = getsid(shellPid) — or read sid from /proc/<pid>/stat field 6.
3. Enumerate /proc/[0-9]*/stat (Android’s ps does exactly this without root). For each process, parse sid, pid, pgid, and state.
4. Retain processes where sid == targetSid and the process belongs to the BeTermux UID (prevent SID reuse against unrelated processes; verify uid from /proc/<pid>/status).
5. Send SIGSTOP individually to each retained PID (or to each distinct PGID observed within the SID — but PID-level targeting is more precise and easier to verify).
6. Record the exact PIDs stopped and verify their /proc/<pid>/stat state becomes T (stopped). If verification fails, transition workspace to ERROR and do not advertise SLEEPING.
7. To resume, send SIGCONT to the same recorded PIDs (plus any new processes discovered in the SID since suspension; see D4).

Critical safeguards:

· Never send to PID <= 1 or PGID <= 0.
· Hold a workspace-level lock during suspend/resume so the process set cannot change mid-operation.
· If a process in the SID creates a new session (setsid()), it leaves the managed SID by definition and cannot be controlled this way. Document this limitation honestly: a process that deliberately escapes the session is outside the workspace’s hibernation scope.
· If /proc enumeration is blocked or produces incomplete data, fail closed: mark ERROR, do not claim SLEEPING.

D2 — Session termination: CONT → TERM → bounded wait → KILL

A SIGTERM sent to a stopped process is held pending until SIGCONT; only SIGKILL is delivered immediately. Therefore termination of a sleeping workspace must be:

```
1. SIGCONT to all verified PIDs in the SID
2. SIGTERM to the same verified set
3. Wait up to N seconds (e.g. 5s), polling /proc state
4. SIGKILL any survivors
5. Verify all are gone; if not, report ERROR (do not silently claim cleanup)
```

This applies to: tab close, explicit session kill, Remove Workspace (if configured to terminate), and cold-start reconciliation of leaked stopped processes.

D3 — Input is gated at the session boundary

Blocking the Compose keyboard is insufficient. Gate TerminalSession.write() / the pty input path on workspace.sleepState != SLEEPING. This covers IME, hardware keyboards, paste, accessibility injection, and programmatic writes. Tap-to-wake a sleeping workspace first; never let keystrokes queue into a stopped pty.

D4 — Runtime state is authoritative; persistence is identity-only

Persist workspace identity, ordering, and associations; never trust a saved isSleeping = true. On startup, reconcile persisted sessionHandles against actual live TerminalSession objects. Reset sleep state to AWAKE unless a live session is independently verified as stopped. Treat activity recreation, task dismissal, force-stop, and actual process death as distinct cases — Android controls process lifetimes, so “app process killed” does not imply “all terminal children are still stopped and manageable.”

D5 — Cross-workspace terminal tap switches workspace

Tapping a terminal in another workspace: (1) changes the active workspace, (2) selects the requested terminal, (3) wakes that workspace if sleeping (via D1), then accepts input.

D6 — Removing a workspace migrates sessions; it does not delete project data

Default behavior: move the workspace’s sessions to Default Workspace. Removing a workspace must never delete the actual project directory. Termination is an explicit separate action, and when it is chosen, it follows D2.

D7 — File explorer behaves like an IDE, not a recursive scanner

Lazy per-directory expansion, I/O on Dispatchers.IO, per-node loading/error state, symlink-loop cap, hidden-file toggle, file create/delete, “Open Terminal Here,” and an honest distinction between viewing and editing. Do not promise in-app editing until FileTabViewer’s actual capability has been verified (Phase 0). If it is viewer-only, label the action “View” and offer an explicit external-editor path.

D8 — Data model is future-proof, storage is deliberately simple

SharedPreferences + JSON is sufficient. Do not introduce Room for a handful of records. Use a runtime sleep state machine:

```kotlin
enum class WorkspaceSleepState {
    AWAKE,
    SUSPENDING,
    SLEEPING,
    RESUMING,
    ERROR
}

data class ProjectWorkspace(
    val id: String,
    val name: String,
    val rootPath: String,
    val sortOrder: Int,
    val sessionHandles: List<String>
)
```

WorkspaceSleepState is runtime-only, never deserialized as truth. Persist id, name, rootPath, sortOrder, sessionHandles. Validate on load and drop records with empty id/rootPath.

---

2. Revised Phased Roadmap

Phase 0 — Architecture & Lifecycle Audit (blocking gate)

Purpose: Prove or disprove the suspension mechanism before building anything on top of it.

Deliverables:

· Documented answer: how BeTermux spawns shells, where setsid() happens, how TerminalSession exposes the shell PID, and whether the SID is reachable from Java/Kotlin.
· Prototype (throwaway): spawn a session, start vim in the foreground and sleep 1000 & in the background, then attempt SID-based suspension. Verify via /proc/<pid>/stat that all relevant processes are in T state and CPU usage drops. Verify resume returns them to S/R.
· Audit of FileTabViewer: viewer-only or editor? Document the truth.
· Audit of session creation paths in TermuxActivity and service ownership: where a workspace ID must be threaded through.

Exit criteria: Suspension proof succeeds on at least two Android versions/devices, including one with a non-rooted Termux environment. If it fails, do not proceed to Phase 4; redesign the hibernation mechanism first (e.g., session-scoped cgroup freezing where available, with honest fallback to “no hibernation” rather than a false zzz).

Rollback: None needed; this phase is investigation only.

---

Phase 1 — Workspace Foundation

Deliverables:

· WorkspaceManager in the Application scope (authoritative session registry), with SharedPreferences persistence.
· Default Workspace migration for pre-existing sessions.
· Session association on every creation path (new tab, menu, intent, recreation).
· Ordering (sortOrder) and migration validation.
· Startup reconciliation: prune dead sessionHandles, reset stale sleep flags, and identify/clean up leaked stopped processes only after confirming they belong to a previously managed BeTermux session.

Acceptance criteria:

· A fresh install and an upgrade from a pre-workspace build both produce a consistent session↔workspace mapping.
· Killing the app and relaunching yields no stale handles and no trusted stale sleep state.
· Removing a workspace moves sessions to Default and leaves the directory untouched.

Rollback: Feature-flag workspaces off; sessions continue to work as a flat list.

---

Phase 2 — Dual Sidebar UX

Deliverables:

· Left drawer: Workspaces section, + Add Project, per-workspace row with name, session count, sleep badge, and an overflow ⋮ menu (Sleep/Wake, Remove) — long-press is an additional gesture, not the only path.
· Terminal list filtered to the active workspace.
· Right drawer toggle replacing btn_files; overlay AnimatedVisibility from the right; back gesture closes the right drawer first.
· Responsive behavior: hide the tab bar while the right drawer is open to avoid terminal resize storms.

Acceptance criteria:

· Adding two workspaces and creating terminals in each scopes the terminal list correctly.
· Tapping a terminal in another workspace switches the active workspace and wakes it if needed.
· Back closes the right drawer before leaving the activity.
· All interactive elements have content descriptions and appropriate semantics.

Rollback: Hide the right-drawer toggle and workspace section via flag; restore btn_files.

---

Phase 3 — Project File Explorer

Deliverables:

· RightFileDrawer.kt: lazy directory tree, per-node loading/error states, reload button, close button.
· Actions: View/Edit (per Phase 0 audit), Copy Path with confirmation, Open Terminal Here.
· Hidden-file toggle, file create/delete, symlink-loop protection.
· Friendly empty/error states for ENOENT, EACCES, unmounted storage, and missing termux-setup-storage grant.

Acceptance criteria:

· Expanding a node_modules-scale directory does not block the main thread or ANR.
· Deleting a file from the terminal and pressing Reload reflects the change.
· A rootPath that no longer exists shows a recoverable empty state, not a crash.
· Symlink loops do not hang or recurse unboundedly.

Rollback: Disable the right drawer; file actions fall back to the existing file manager.

---

Phase 4 — Workspace Sleep / Hibernate (only after Phase 0 gate)

Deliverables:

· SID-based suspend/resume per D1, with verified PID tracking and ERROR state on partial success.
· Input gating at the session boundary per D3.
· Termination path per D2, wired to tab close, session kill, and explicit workspace termination.
· Warm UI: SUSPENDING, RESUMING, ERROR states surfaced honestly; zzz only when verified.
· Long-sleep honesty notice: remote ssh sessions may drop; a long sleep is not a promise of network session survival.

Acceptance criteria:

· vim in the foreground and a background sleep 1000 & are both stopped when the workspace sleeps (verified via /proc state and CPU).
· Typing while sleeping does nothing; waking first is required.
· Closing a tab in a sleeping workspace actually terminates the process (CONT→TERM→KILL).
· App kill while sleeping: on relaunch, stale sleep flags are cleared, leaked stopped processes are identified and cleaned up safely, and no unrelated process is signaled.
· If SID verification fails, the workspace shows ERROR, not SLEEPING.

Rollback: Feature-flag hibernation off entirely. Workspaces remain functional; the zzz path and all signal sending are disabled. This must be a single flag flip because hibernation is the highest-risk subsystem.

---

Phase 5 — Quality & Developer Experience (post-MVP)

Git integration, editor persistence, workspace customization, split terminals, command palette. These are valuable but must not dilute the correctness work in Phases 0–4.

---

3. Consolidated Test Matrix

# Scenario Expected Phase
1 Foreground vim + background job; sleep workspace Both verified T; CPU drops; no false zzz 0/4
2 Wake slept workspace with vim/htop UI redraws correctly 4
3 Background sleep 1000 &; sleep → wake Job survives and remains tracked 4
4 Type while sleeping No input accepted; no queued burst 4
5 Sleep → close tab Process actually dies (CONT→TERM→KILL) 4
6 Sleep → app killed → relaunch Stale flags cleared; no orphaned stopped processes; no unrelated kills 4
7 Remove workspace with running sessions Sessions moved to Default; directory untouched 1
8 Tap terminal in other workspace Active workspace changes; wakes if needed 2
9 rootPath deleted from terminal Right drawer shows recoverable empty state 3
10 Large directory expansion No jank/ANR; lazy loading works 3
11 ~/storage/shared without setup-storage Friendly error, no crash 3
12 Symlink loop in project tree No hang; depth/visited guard works 3
13 Cold start with no workspaces Default Workspace created; legacy sessions migrated 1
14 SID enumeration blocked / partial Workspace goes ERROR; no false SLEEPING 4

---

4. Risk Register

Risk Severity Mitigation
SIGSTOP misses separate process groups Critical SID-based enumeration + per-PID stop + /proc state verification; fail closed to ERROR.
SIGTERM held pending on stopped processes Critical Always SIGCONT before termination; bounded wait then SIGKILL.
PID/SID reuse High Verify uid ownership and, where possible, session age/start time before signaling.
Android kills app or children independently High Runtime registry authoritative; reconcile on startup; never trust persisted sleep flags.
Input queued while stopped High Gate at session write boundary, not only the UI keyboard.
/proc access restricted Medium Feature-flag hibernation off; do not fake state.
File tree jank/ANR Medium Lazy per-directory I/O; no recursive root scan.
FileTabViewer is viewer-only Medium Audit in Phase 0; label actions honestly; offer external editor.
Long sleep drops ssh sessions Medium Document; do not promise network-session survival.
Two-drawer UI cramped on phones Low Overlays; hide tab bar when right drawer open.

---

5. Rollback Strategy (Per Phase)

Every phase must be independently disableable:

· Phase 1: Flag off workspaces → flat session list; no data loss because persistence is additive.
· Phase 2: Flag off drawers → restore btn_files; workspace data remains.
· Phase 3: Flag off right drawer → file actions fall back to FileManagerActivity.
· Phase 4: Single hibernation flag disables all signal sending and the zzz path. This is mandatory: hibernation is the only subsystem that can affect processes outside the app’s immediate UI, and it must be killable without touching workspace identity or drawer behavior.

Hard rule: No phase ships with a claimed capability that is not verified in Phase 0’s audit. A SLEEPING badge is a correctness claim, not decoration.

---

6. Final Verdict

The dual-sidebar direction is right and worth building. The original hibernation design was not. This roadmap makes the SID-based suspension proof the first blocking gate, locks the four lifecycle decisions before UI work, treats persistence as identity-only, and keeps hibernation behind a single rollback flag. Proceed in order; do not let the polish of Phases 2–3 create pressure to ship Phase 4 before its mechanism is proven.