package com.betermux.workspace

import android.content.Context
import com.betermux.features.BeTermuxSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceSessionIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE).edit().clear().commit()
        BeTermuxSettings.setWorkspacesEnabled(context, true)
        WorkspaceManager.resetForTesting(context, "/data/data/com.termux/files/home")
    }

    @Test
    fun newSessionUsesActiveWorkspaceRootWhenCurrentSessionIsNullOrInDifferentWorkspace() {
        val projectRoot = tempFolder.newFolder("proj-api").absolutePath
        val defaultSubdir = tempFolder.newFolder("default-sub").absolutePath
        val apiWorkspace = WorkspaceManager.createWorkspace("API", projectRoot)

        WorkspaceManager.associateSession("s-default", DEFAULT_WORKSPACE_ID)
        WorkspaceManager.setActiveWorkspace(apiWorkspace.id)
        val active = WorkspaceManager.activeWorkspace()

        // 1) currentSession is null -> picks active workspace rootPath
        val cwdWhenNull = WorkspaceSessionPolicy.resolveNewSessionWorkingDirectory(
            workspacesEnabled = BeTermuxSettings.isWorkspacesEnabled(context),
            activeWorkspace = active,
            currentSessionHandle = null,
            currentSessionCwd = null,
            defaultWorkingDirectory = "/fallback/home"
        )
        assertEquals(projectRoot, cwdWhenNull)

        // 2) currentSession belongs to a different workspace -> picks active workspace rootPath
        val cwdWhenOtherWorkspace = WorkspaceSessionPolicy.resolveNewSessionWorkingDirectory(
            workspacesEnabled = BeTermuxSettings.isWorkspacesEnabled(context),
            activeWorkspace = active,
            currentSessionHandle = "s-default",
            currentSessionCwd = defaultSubdir,
            defaultWorkingDirectory = "/fallback/home"
        )
        assertEquals(projectRoot, cwdWhenOtherWorkspace)

        // New session handle associates with the active workspace
        WorkspaceManager.associateSession("s-api-1")
        assertEquals(apiWorkspace.id, WorkspaceManager.workspaceForSession("s-api-1")?.id)

        // 3) currentSession belongs to the active workspace -> preserves its CWD
        val apiSubdir = tempFolder.newFolder("proj-api-sub").absolutePath
        val cwdWhenSameWorkspace = WorkspaceSessionPolicy.resolveNewSessionWorkingDirectory(
            workspacesEnabled = BeTermuxSettings.isWorkspacesEnabled(context),
            activeWorkspace = WorkspaceManager.activeWorkspace(),
            currentSessionHandle = "s-api-1",
            currentSessionCwd = apiSubdir,
            defaultWorkingDirectory = "/fallback/home"
        )
        assertEquals(apiSubdir, cwdWhenSameWorkspace)

        // 4) Invalid rootPath falls back to defaultWorkingDirectory
        val invalidWs = WorkspaceManager.createWorkspace("Missing", "/nonexistent/path/xyz_12345")
        WorkspaceManager.setActiveWorkspace(invalidWs.id)
        val cwdWhenInvalidRoot = WorkspaceSessionPolicy.resolveNewSessionWorkingDirectory(
            workspacesEnabled = true,
            activeWorkspace = WorkspaceManager.activeWorkspace(),
            currentSessionHandle = "s-api-1",
            currentSessionCwd = apiSubdir,
            defaultWorkingDirectory = "/fallback/home"
        )
        assertEquals("/fallback/home", cwdWhenInvalidRoot)
    }

    @Test
    fun filterSessionsForWorkspaceFiltersWhenEnabledAndReturnsFlatListWhenDisabled() {
        val w1 = WorkspaceManager.createWorkspace("Web", tempFolder.newFolder("web").absolutePath)
        WorkspaceManager.associateSession("h1", DEFAULT_WORKSPACE_ID)
        WorkspaceManager.associateSession("h2", w1.id)
        WorkspaceManager.associateSession("h3", DEFAULT_WORKSPACE_ID)
        WorkspaceManager.associateSession("h4", w1.id)
        WorkspaceManager.setActiveWorkspace(w1.id)

        val allHandles = listOf("h1", "h2", "h3", "h4")
        val active = WorkspaceManager.activeWorkspace()

        // Enabled -> only sessions in active workspace, preserving order
        BeTermuxSettings.setWorkspacesEnabled(context, true)
        val filtered = WorkspaceSessionPolicy.filterSessionsForWorkspace(
            allHandles = allHandles,
            activeWorkspace = active,
            workspacesEnabled = BeTermuxSettings.isWorkspacesEnabled(context)
        )
        assertEquals(listOf("h2", "h4"), filtered)

        // Disabled (Phase 1 rollback) -> full flat list
        BeTermuxSettings.setWorkspacesEnabled(context, false)
        val flat = WorkspaceSessionPolicy.filterSessionsForWorkspace(
            allHandles = allHandles,
            activeWorkspace = active,
            workspacesEnabled = BeTermuxSettings.isWorkspacesEnabled(context)
        )
        assertEquals(allHandles, flat)
    }

    @Test
    fun nextSessionSelectionPrefersActiveWorkspaceAfterRemoval() {
        val w1 = WorkspaceManager.createWorkspace("Mobile", tempFolder.newFolder("mobile").absolutePath)
        WorkspaceManager.associateSession("d1", DEFAULT_WORKSPACE_ID)
        WorkspaceManager.associateSession("m1", w1.id)
        WorkspaceManager.associateSession("m2", w1.id)
        WorkspaceManager.setActiveWorkspace(w1.id)
        val active = WorkspaceManager.activeWorkspace()

        // Remaining handles after removing m1 at index 1
        val next = WorkspaceSessionPolicy.selectNextSessionHandleAfterRemoval(
            remainingHandles = listOf("d1", "m2"),
            removedIndex = 1,
            activeWorkspace = active,
            workspacesEnabled = true
        )
        assertEquals("m2", next)

        // If active workspace has no remaining sessions, fall back to remaining handles
        val fallback = WorkspaceSessionPolicy.selectNextSessionHandleAfterRemoval(
            remainingHandles = listOf("d1"),
            removedIndex = 0,
            activeWorkspace = active,
            workspacesEnabled = true
        )
        assertEquals("d1", fallback)

        // No sessions left -> null
        assertNull(
            WorkspaceSessionPolicy.selectNextSessionHandleAfterRemoval(
                remainingHandles = emptyList(),
                removedIndex = 0,
                activeWorkspace = active,
                workspacesEnabled = true
            )
        )
    }

    @Test
    fun reconcileStartupSessionsAfterSimulatedProcessDeathClearsDeadAndMigratesRestoredToDefault() {
        val w1 = WorkspaceManager.createWorkspace("Backend", tempFolder.newFolder("backend").absolutePath)
        WorkspaceManager.associateSession("dead-default-1", DEFAULT_WORKSPACE_ID)
        WorkspaceManager.associateSession("dead-backend-1", w1.id)
        WorkspaceManager.associateSession("surviving-backend", w1.id)
        WorkspaceManager.setActiveWorkspace(w1.id)

        // Simulate process death & cold restart: WorkspaceManager re-loads persisted state
        WorkspaceManager.resetForTesting(context, "/data/data/com.termux/files/home")
        assertTrue(WorkspaceManager.state.value.workspaces.any { it.id == w1.id })

        // Reconcile against live restored handles (dead handles gone, restored unmapped handles arrive)
        WorkspaceManager.reconcileStartupSessions(listOf("surviving-backend", "restored-1", "restored-2"))

        val snap = WorkspaceManager.state.value
        val backend = snap.workspaces.first { it.id == w1.id }
        val defaultWs = snap.workspaces.first { it.id == DEFAULT_WORKSPACE_ID }

        assertFalse(backend.sessionHandles.contains("dead-backend-1"))
        assertEquals(listOf("surviving-backend"), backend.sessionHandles)

        assertFalse(defaultWs.sessionHandles.contains("dead-default-1"))
        assertEquals(listOf("restored-1", "restored-2"), defaultWs.sessionHandles)
        assertEquals(WorkspaceSleepState.AWAKE, snap.sleepStates[w1.id])
        assertEquals(WorkspaceSleepState.AWAKE, snap.sleepStates[DEFAULT_WORKSPACE_ID])
    }
}
