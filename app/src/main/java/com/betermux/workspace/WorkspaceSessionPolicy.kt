package com.betermux.workspace

import java.io.File

/**
 * Pure, testable workspace policy helpers for session filtering, working-directory
 * selection, and post-exit session focus selection.
 */
object WorkspaceSessionPolicy {

    fun interface HandleExtractor<T> {
        fun handleOf(item: T): String?
    }

    /**
     * Resolves the working directory for a newly created terminal session.
     *
     * When workspaces are enabled, always starts in [activeWorkspace.rootPath] if it is an
     * existing directory, falling back to [defaultWorkingDirectory]. A new session must not
     * inherit a current session's manually changed directory.
     */
    @JvmStatic
    @JvmOverloads
    fun resolveNewSessionWorkingDirectory(
        workspacesEnabled: Boolean,
        activeWorkspace: ProjectWorkspace,
        currentSessionHandle: String?,
        currentSessionCwd: String?,
        defaultWorkingDirectory: String,
        directoryExists: (String) -> Boolean = { path -> File(path).isDirectory }
    ): String? {
        if (!workspacesEnabled) {
            return defaultWorkingDirectory
        }

        val root = activeWorkspace.rootPath.trim()
        if (root.isNotEmpty() && directoryExists(root)) {
            return root
        }
        return defaultWorkingDirectory
    }

    /**
     * Filters [allHandles] to those belonging to [activeWorkspace] when [workspacesEnabled]
     * is true, or returns [allHandles] unchanged when disabled (Phase 1 rollback).
     */
    @JvmStatic
    fun filterSessionsForWorkspace(
        allHandles: List<String>,
        activeWorkspace: ProjectWorkspace,
        workspacesEnabled: Boolean
    ): List<String> {
        if (!workspacesEnabled) return allHandles
        val allowed = activeWorkspace.sessionHandles.toHashSet()
        return allHandles.filter { it in allowed }
    }

    /**
     * Generic item-filtering variant for Java/Kotlin callers holding session wrapper objects.
     */
    @JvmStatic
    fun <T> filterItemsForWorkspace(
        items: List<T>,
        activeWorkspace: ProjectWorkspace,
        workspacesEnabled: Boolean,
        extractor: HandleExtractor<T>
    ): List<T> {
        if (!workspacesEnabled) return items
        val allowed = activeWorkspace.sessionHandles.toHashSet()
        return items.filter { item ->
            val handle = extractor.handleOf(item)
            handle != null && handle in allowed
        }
    }

    /**
     * Computes the removal index to pass to [selectNextSessionHandleAfterRemoval] using
     * workspace-local indices when [workspacesEnabled] is true and at least one other session
     * remains in [activeWorkspace], or global indices otherwise.
     */
    @JvmStatic
    fun resolveRemovalIndex(
        handlesBeforeRemoval: List<String>,
        removedHandle: String,
        activeWorkspace: ProjectWorkspace,
        workspacesEnabled: Boolean
    ): Int {
        val globalIndex = handlesBeforeRemoval.indexOf(removedHandle).coerceAtLeast(0)
        if (!workspacesEnabled) return globalIndex

        val allowed = activeWorkspace.sessionHandles.toHashSet()
        val inWorkspace = handlesBeforeRemoval.filter { it in allowed }
        val localIndex = inWorkspace.indexOf(removedHandle)
        return if (localIndex >= 0 && inWorkspace.size > 1) {
            localIndex
        } else {
            globalIndex
        }
    }

    /**
     * Picks the next session handle to focus after removing a session at [removedIndex].
     * Prefers remaining sessions in [activeWorkspace] when [workspacesEnabled] is true.
     */
    @JvmStatic
    fun selectNextSessionHandleAfterRemoval(
        remainingHandles: List<String>,
        removedIndex: Int,
        activeWorkspace: ProjectWorkspace,
        workspacesEnabled: Boolean
    ): String? {
        if (remainingHandles.isEmpty()) return null
        val candidates = if (workspacesEnabled) {
            val allowed = activeWorkspace.sessionHandles.toHashSet()
            val inWorkspace = remainingHandles.filter { it in allowed }
            if (inWorkspace.isNotEmpty()) inWorkspace else remainingHandles
        } else {
            remainingHandles
        }
        val safeIndex = removedIndex.coerceIn(0, candidates.lastIndex)
        return candidates[safeIndex]
    }

    /**
     * Picks the next or previous session handle within [visibleHandles] for keyboard-shortcut
     * cycling so navigation stays scoped to the active workspace.
     */
    @JvmStatic
    fun selectAdjacentSessionHandle(
        visibleHandles: List<String>,
        currentHandle: String?,
        forward: Boolean
    ): String? {
        if (visibleHandles.isEmpty()) return null
        val currentIndex = if (currentHandle != null) visibleHandles.indexOf(currentHandle) else -1
        val targetIndex = when {
            currentIndex < 0 -> 0
            forward -> (currentIndex + 1) % visibleHandles.size
            else -> if (currentIndex - 1 < 0) visibleHandles.lastIndex else currentIndex - 1
        }
        return visibleHandles[targetIndex]
    }

    /**
     * Picks the session handle at [index] within [visibleHandles], or null if out of bounds.
     */
    @JvmStatic
    fun selectSessionHandleAtIndex(
        visibleHandles: List<String>,
        index: Int
    ): String? {
        if (index < 0 || index >= visibleHandles.size) return null
        return visibleHandles[index]
    }

    /**
     * Returns true when creating a new terminal session in an active workspace must wake the
     * workspace first so a SLEEPING/SUSPENDING/RESUMING workspace (or an ERROR workspace with live
     * sessions) never mixes stopped/gated sessions with a newly created un-gated shell.
     */
    @JvmStatic
    fun shouldWakeBeforeCreatingSession(
        sleepState: WorkspaceSleepState,
        hasSessions: Boolean
    ): Boolean {
        return when (sleepState) {
            WorkspaceSleepState.AWAKE -> false
            WorkspaceSleepState.SLEEPING,
            WorkspaceSleepState.SUSPENDING,
            WorkspaceSleepState.RESUMING -> true
            WorkspaceSleepState.ERROR -> hasSessions
        }
    }
}
