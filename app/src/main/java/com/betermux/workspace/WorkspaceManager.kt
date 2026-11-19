package com.betermux.workspace

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Application-scoped workspace registry and session-to-workspace mapper.
 *
 * Persists identity and session handle associations via [WorkspaceStore], while keeping
 * [WorkspaceSleepState] strictly in memory (D4).
 */
object WorkspaceManager {
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var defaultRootPath: String = TermuxConstants.TERMUX_HOME_DIR_PATH

    private var initialized: Boolean = false

    private val _state = MutableStateFlow(
        WorkspaceStateSnapshot(
            workspaces = listOf(createDefaultWorkspace(TermuxConstants.TERMUX_HOME_DIR_PATH)),
            activeWorkspaceId = DEFAULT_WORKSPACE_ID,
            sleepStates = mapOf(DEFAULT_WORKSPACE_ID to WorkspaceSleepState.AWAKE)
        )
    )
    val state: StateFlow<WorkspaceStateSnapshot> = _state.asStateFlow()

    @JvmStatic
    @JvmOverloads
    fun init(context: Context, defaultRootPath: String = TermuxConstants.TERMUX_HOME_DIR_PATH) {
        synchronized(lock) {
            if (initialized) return
            initializeLocked(context, defaultRootPath)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun resetForTesting(
        context: Context,
        defaultRootPath: String = TermuxConstants.TERMUX_HOME_DIR_PATH
    ) {
        synchronized(lock) {
            initializeLocked(context, defaultRootPath)
        }
    }

    private fun initializeLocked(context: Context, rootPath: String) {
        val resolvedContext = context.applicationContext ?: context
        val resolvedRoot = rootPath.trim().ifEmpty { TermuxConstants.TERMUX_HOME_DIR_PATH }
        appContext = resolvedContext
        defaultRootPath = resolvedRoot
        initialized = true

        val persisted = WorkspaceStore.load(resolvedContext)
        val normalizedWorkspaces = ensureDefaultWorkspace(persisted.workspaces, resolvedRoot)
            .sortedWith(compareBy<ProjectWorkspace> { it.sortOrder }.thenBy { it.id != DEFAULT_WORKSPACE_ID })

        val activeId = persisted.activeWorkspaceId
            ?.takeIf { id -> normalizedWorkspaces.any { it.id == id } }
            ?: DEFAULT_WORKSPACE_ID

        val sleepStates = normalizedWorkspaces.associate { it.id to WorkspaceSleepState.AWAKE }
        _state.value = WorkspaceStateSnapshot(
            workspaces = normalizedWorkspaces,
            activeWorkspaceId = activeId,
            sleepStates = sleepStates
        )
    }

    @JvmStatic
    fun createWorkspace(name: String, rootPath: String): ProjectWorkspace {
        synchronized(lock) {
            val trimmedRoot = rootPath.trim()
            require(trimmedRoot.isNotEmpty()) { "Workspace rootPath cannot be blank" }
            val resolvedName = name.trim().ifEmpty {
                trimmedRoot.substringAfterLast('/').ifEmpty { "Workspace" }
            }

            val current = _state.value
            val nextSortOrder = (current.workspaces.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val created = ProjectWorkspace(
                id = UUID.randomUUID().toString(),
                name = resolvedName,
                rootPath = trimmedRoot,
                sortOrder = nextSortOrder,
                sessionHandles = emptyList()
            )
            val updatedWorkspaces = current.workspaces + created
            val updatedSleepStates = current.sleepStates + (created.id to WorkspaceSleepState.AWAKE)
            commitStateLocked(updatedWorkspaces, current.activeWorkspaceId, updatedSleepStates)
            return created
        }
    }

    @JvmStatic
    fun renameWorkspace(workspaceId: String, newName: String) {
        synchronized(lock) {
            val trimmedName = newName.trim()
            if (trimmedName.isEmpty()) return
            val current = _state.value
            var changed = false
            val updated = current.workspaces.map { ws ->
                if (ws.id == workspaceId && ws.name != trimmedName) {
                    changed = true
                    ws.copy(name = trimmedName)
                } else {
                    ws
                }
            }
            if (changed) {
                commitStateLocked(updated, current.activeWorkspaceId, current.sleepStates)
            }
        }
    }

    /**
     * Removes [workspaceId] (unless it is [DEFAULT_WORKSPACE_ID]) and migrates its sessions
     * to [DEFAULT_WORKSPACE_ID] without deleting files on disk (D6).
     *
     * @return the list of migrated session handles.
     */
    @JvmStatic
    fun removeWorkspace(workspaceId: String): List<String> {
        return removeWorkspaceInternal(workspaceId, migrateToDefault = true)
    }

    /**
     * Removes [workspaceId] (unless it is [DEFAULT_WORKSPACE_ID]) and drops its session
     * associations so the caller can terminate those sessions (D2/D6).
     *
     * @return the list of session handles that belonged to the removed workspace.
     */
    @JvmStatic
    fun removeWorkspaceAndClearSessions(workspaceId: String): List<String> {
        return removeWorkspaceInternal(workspaceId, migrateToDefault = false)
    }

    private fun removeWorkspaceInternal(workspaceId: String, migrateToDefault: Boolean): List<String> {
        synchronized(lock) {
            if (workspaceId == DEFAULT_WORKSPACE_ID) return emptyList()
            val current = _state.value
            val target = current.workspaces.firstOrNull { it.id == workspaceId } ?: return emptyList()
            val targetHandles = target.sessionHandles

            val remaining = ensureDefaultWorkspace(
                current.workspaces.filterNot { it.id == workspaceId },
                defaultRootPath
            )
            val updatedWorkspaces = if (migrateToDefault && targetHandles.isNotEmpty()) {
                remaining.map { ws ->
                    if (ws.id == DEFAULT_WORKSPACE_ID) {
                        val merged = (ws.sessionHandles + targetHandles).distinct()
                        ws.copy(sessionHandles = merged)
                    } else {
                        ws
                    }
                }
            } else {
                remaining
            }

            val updatedActiveId = if (current.activeWorkspaceId == workspaceId) {
                DEFAULT_WORKSPACE_ID
            } else {
                current.activeWorkspaceId
            }
            val updatedSleepStates = current.sleepStates - workspaceId
            commitStateLocked(updatedWorkspaces, updatedActiveId, updatedSleepStates)
            return targetHandles
        }
    }

    @JvmStatic
    fun setActiveWorkspace(workspaceId: String): Boolean {
        synchronized(lock) {
            val current = _state.value
            if (current.workspaces.none { it.id == workspaceId }) return false
            if (current.activeWorkspaceId != workspaceId) {
                commitStateLocked(current.workspaces, workspaceId, current.sleepStates)
            }
            return true
        }
    }

    @JvmStatic
    @JvmOverloads
    fun associateSession(sessionHandle: String, workspaceId: String? = null) {
        synchronized(lock) {
            val handle = sessionHandle.trim()
            if (handle.isEmpty()) return

            val current = _state.value
            val targetId = when {
                workspaceId != null && current.workspaces.any { it.id == workspaceId } -> workspaceId
                workspaceId == null && current.workspaces.any { it.id == current.activeWorkspaceId } ->
                    current.activeWorkspaceId
                else -> DEFAULT_WORKSPACE_ID
            }

            val currentOwner = current.workspaces.firstOrNull { handle in it.sessionHandles }
            if (currentOwner?.id == targetId) return

            val updated = current.workspaces.map { ws ->
                val withoutHandle = ws.sessionHandles.filterNot { it == handle }
                if (ws.id == targetId) {
                    ws.copy(sessionHandles = withoutHandle + handle)
                } else if (withoutHandle.size != ws.sessionHandles.size) {
                    ws.copy(sessionHandles = withoutHandle)
                } else {
                    ws
                }
            }
            commitStateLocked(updated, current.activeWorkspaceId, current.sleepStates)
        }
    }

    @JvmStatic
    fun dissociateSession(sessionHandle: String) {
        synchronized(lock) {
            val handle = sessionHandle.trim()
            if (handle.isEmpty()) return
            val current = _state.value
            var changed = false
            val updated = current.workspaces.map { ws ->
                if (handle in ws.sessionHandles) {
                    changed = true
                    ws.copy(sessionHandles = ws.sessionHandles.filterNot { it == handle })
                } else {
                    ws
                }
            }
            if (changed) {
                commitStateLocked(updated, current.activeWorkspaceId, current.sleepStates)
            }
        }
    }

    @JvmStatic
    fun workspaceForSession(sessionHandle: String): ProjectWorkspace? {
        return _state.value.workspaces.firstOrNull { sessionHandle in it.sessionHandles }
    }

    @JvmStatic
    fun activeWorkspace(): ProjectWorkspace {
        val snap = _state.value
        return snap.workspaces.firstOrNull { it.id == snap.activeWorkspaceId }
            ?: snap.workspaces.firstOrNull { it.id == DEFAULT_WORKSPACE_ID }
            ?: createDefaultWorkspace(defaultRootPath)
    }

    @JvmStatic
    fun reconcileStartupSessions(liveHandles: Collection<String>) {
        reconcileLiveSessionsInternal(liveHandles, resetSleepStates = true)
    }

    /**
     * Reconciles live session handles without resetting runtime [WorkspaceSleepState] values,
     * preserving active hibernation/error states when an activity reconnects to an already-running service.
     */
    @JvmStatic
    fun syncLiveSessionsPreservingSleepState(liveHandles: Collection<String>) {
        reconcileLiveSessionsInternal(liveHandles, resetSleepStates = false)
    }

    private fun reconcileLiveSessionsInternal(
        liveHandles: Collection<String>,
        resetSleepStates: Boolean
    ) {
        synchronized(lock) {
            val orderedLive = liveHandles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val liveSet = orderedLive.toSet()
            val current = _state.value

            val seenHandles = HashSet<String>()
            val prunedWorkspaces = ensureDefaultWorkspace(current.workspaces, defaultRootPath).map { ws ->
                val kept = ws.sessionHandles.filter { it in liveSet && seenHandles.add(it) }
                ws.copy(sessionHandles = kept)
            }

            val unmapped = orderedLive.filter { it !in seenHandles }
            val reconciledWorkspaces = if (unmapped.isEmpty()) {
                prunedWorkspaces
            } else {
                prunedWorkspaces.map { ws ->
                    if (ws.id == DEFAULT_WORKSPACE_ID) {
                        ws.copy(sessionHandles = ws.sessionHandles + unmapped)
                    } else {
                        ws
                    }
                }
            }

            val activeId = current.activeWorkspaceId
                .takeIf { id -> reconciledWorkspaces.any { it.id == id } }
                ?: DEFAULT_WORKSPACE_ID
            val nextSleepStates = if (resetSleepStates) {
                reconciledWorkspaces.associate { it.id to WorkspaceSleepState.AWAKE }
            } else {
                current.sleepStates
            }

            commitStateLocked(reconciledWorkspaces, activeId, nextSleepStates)
        }
    }

    @JvmStatic
    fun getSleepState(workspaceId: String): WorkspaceSleepState {
        return _state.value.sleepStates[workspaceId] ?: WorkspaceSleepState.AWAKE
    }

    @JvmStatic
    fun setSleepState(workspaceId: String, sleepState: WorkspaceSleepState) {
        synchronized(lock) {
            val current = _state.value
            if (current.workspaces.none { it.id == workspaceId }) return
            if (current.sleepStates[workspaceId] == sleepState) return
            val updatedSleepStates = current.sleepStates + (workspaceId to sleepState)
            // Runtime-only: intentionally do not persist sleep states (D4).
            _state.value = current.copy(sleepStates = updatedSleepStates)
        }
    }

    /**
     * Resets all in-memory workspace sleep states to [WorkspaceSleepState.AWAKE] when
     * workspaces or hibernation rollback flags are turned off at runtime.
     */
    @JvmStatic
    fun resetAllSleepStatesToAwake() {
        synchronized(lock) {
            val current = _state.value
            if (current.sleepStates.values.all { it == WorkspaceSleepState.AWAKE }) return
            val reset = current.workspaces.associate { it.id to WorkspaceSleepState.AWAKE }
            _state.value = current.copy(sleepStates = reset)
        }
    }

    private fun commitStateLocked(
        workspaces: List<ProjectWorkspace>,
        activeWorkspaceId: String,
        sleepStates: Map<String, WorkspaceSleepState>
    ) {
        val sorted = workspaces.sortedWith(
            compareBy<ProjectWorkspace> { it.sortOrder }.thenBy { it.id != DEFAULT_WORKSPACE_ID }
        )
        val resolvedActiveId = activeWorkspaceId
            .takeIf { id -> sorted.any { it.id == id } }
            ?: DEFAULT_WORKSPACE_ID
        val normalizedSleepStates = sorted.associate { ws ->
            ws.id to (sleepStates[ws.id] ?: WorkspaceSleepState.AWAKE)
        }
        _state.value = WorkspaceStateSnapshot(
            workspaces = sorted,
            activeWorkspaceId = resolvedActiveId,
            sleepStates = normalizedSleepStates
        )
        appContext?.let { ctx ->
            WorkspaceStore.save(ctx, sorted, resolvedActiveId)
        }
    }

    private fun ensureDefaultWorkspace(
        workspaces: List<ProjectWorkspace>,
        rootPath: String
    ): List<ProjectWorkspace> {
        return if (workspaces.any { it.id == DEFAULT_WORKSPACE_ID }) {
            workspaces
        } else {
            listOf(createDefaultWorkspace(rootPath)) + workspaces
        }
    }

    private fun createDefaultWorkspace(rootPath: String): ProjectWorkspace {
        return ProjectWorkspace(
            id = DEFAULT_WORKSPACE_ID,
            name = DEFAULT_WORKSPACE_NAME,
            rootPath = rootPath,
            sortOrder = 0,
            sessionHandles = emptyList()
        )
    }
}
