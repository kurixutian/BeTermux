package com.betermux.workspace

const val DEFAULT_WORKSPACE_ID = "default"
const val DEFAULT_WORKSPACE_NAME = "Default"

/**
 * Runtime-only hibernation lifecycle state for a workspace.
 * Never persisted to storage; cold starts always begin in [AWAKE].
 */
enum class WorkspaceSleepState {
    AWAKE,
    SUSPENDING,
    SLEEPING,
    RESUMING,
    ERROR
}

/**
 * Persistent identity and session association record for a project workspace.
 */
data class ProjectWorkspace(
    val id: String,
    val name: String,
    val rootPath: String,
    val sortOrder: Int,
    val sessionHandles: List<String> = emptyList()
)

/**
 * Immutable snapshot exposed via [WorkspaceManager.state].
 */
data class WorkspaceStateSnapshot(
    val workspaces: List<ProjectWorkspace>,
    val activeWorkspaceId: String,
    val sleepStates: Map<String, WorkspaceSleepState>
)
