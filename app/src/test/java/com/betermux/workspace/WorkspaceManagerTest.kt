package com.betermux.workspace

import android.content.Context
import com.betermux.features.BeTermuxSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE).edit().clear().commit()
        WorkspaceManager.resetForTesting(context, "/data/data/com.termux/files/home")
    }

    @Test
    fun coldStartCreatesDefaultWorkspaceAndMigratesOrphanedLiveSessions() {
        WorkspaceManager.reconcileStartupSessions(listOf("h1", "h2"))
        val snap = WorkspaceManager.state.value
        assertEquals(1, snap.workspaces.size)
        assertEquals(DEFAULT_WORKSPACE_ID, snap.workspaces[0].id)
        assertEquals(listOf("h1", "h2"), snap.workspaces[0].sessionHandles)
        assertEquals(WorkspaceSleepState.AWAKE, snap.sleepStates[DEFAULT_WORKSPACE_ID])
    }

    @Test
    fun invalidPersistedRecordsDroppedAndStaleHandlesPruned() {
        val prefs = context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE)
        prefs.edit().putString("workspaces_json", """
            [
              {"id":"","name":"Bad","rootPath":"/tmp","sortOrder":0,"sessionHandles":["x"]},
              {"id":"w1","name":"App","rootPath":"/proj/app","sortOrder":1,"sessionHandles":["live1","dead1"],"isSleeping":true},
              {"id":"w2","name":"NoRoot","rootPath":"","sortOrder":2,"sessionHandles":["y"]}
            ]
        """.trimIndent()).commit()

        WorkspaceManager.resetForTesting(context, "/home")
        WorkspaceManager.reconcileStartupSessions(listOf("live1", "live2"))
        val snap = WorkspaceManager.state.value
        assertEquals(2, snap.workspaces.size) // default + w1
        val w1 = snap.workspaces.first { it.id == "w1" }
        assertEquals(listOf("live1"), w1.sessionHandles)
        assertEquals(WorkspaceSleepState.AWAKE, snap.sleepStates["w1"])
        val def = snap.workspaces.first { it.id == DEFAULT_WORKSPACE_ID }
        assertEquals(listOf("live2"), def.sessionHandles)
    }

    @Test
    fun removingWorkspaceMigratesSessionsToDefaultAndProtectsDefault() {
        val w = WorkspaceManager.createWorkspace("API", "/tmp/api")
        WorkspaceManager.associateSession("s1", w.id)
        val migrated = WorkspaceManager.removeWorkspace(w.id)
        assertEquals(listOf("s1"), migrated)
        val snap = WorkspaceManager.state.value
        assertFalse(snap.workspaces.any { it.id == w.id })
        assertTrue(snap.workspaces.first { it.id == DEFAULT_WORKSPACE_ID }.sessionHandles.contains("s1"))
        assertEquals(emptyList<String>(), WorkspaceManager.removeWorkspace(DEFAULT_WORKSPACE_ID))
    }

    @Test
    fun rollbackFeatureFlagsToggleIndependently() {
        assertTrue(BeTermuxSettings.isWorkspacesEnabled(context))
        BeTermuxSettings.setWorkspaceHibernationEnabled(context, false)
        assertFalse(BeTermuxSettings.isWorkspaceHibernationEnabled(context))
        assertTrue(BeTermuxSettings.isWorkspacesEnabled(context))
    }

    @Test
    fun jsonNullFieldsAreIgnoredInWorkspaceStoreLoad() {
        val prefs = context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE)
        prefs.edit().putString("workspaces_json", """
            [
              {"id":null,"name":"NullId","rootPath":"/tmp/a","sortOrder":1,"sessionHandles":["x"]},
              {"id":"w_null_root","name":"NullRoot","rootPath":null,"sortOrder":2,"sessionHandles":["y"]},
              {"id":"w_valid","name":null,"rootPath":"/proj/app","sortOrder":3,"sessionHandles":[null,"h1"]}
            ]
        """.trimIndent()).commit()

        WorkspaceManager.resetForTesting(context, "/home")
        val snap = WorkspaceManager.state.value
        assertEquals(2, snap.workspaces.size)
        assertFalse(snap.workspaces.any { it.id == "null" || it.id == "w_null_root" })
        val valid = snap.workspaces.first { it.id == "w_valid" }
        assertEquals("app", valid.name)
        assertEquals(listOf("h1"), valid.sessionHandles)
    }

    @Test
    fun workspaceScopedSessionCyclingStaysWithinVisibleHandles() {
        val visible = listOf("ws-a-1", "ws-a-2", "ws-a-3")
        assertEquals("ws-a-2", WorkspaceSessionPolicy.selectAdjacentSessionHandle(visible, "ws-a-1", forward = true))
        assertEquals("ws-a-1", WorkspaceSessionPolicy.selectAdjacentSessionHandle(visible, "ws-a-3", forward = true))
        assertEquals("ws-a-3", WorkspaceSessionPolicy.selectAdjacentSessionHandle(visible, "ws-a-1", forward = false))
        assertEquals("ws-a-1", WorkspaceSessionPolicy.selectAdjacentSessionHandle(visible, "other-ws", forward = true))
        assertNull(WorkspaceSessionPolicy.selectAdjacentSessionHandle(emptyList(), "ws-a-1", forward = true))

        assertEquals("ws-a-2", WorkspaceSessionPolicy.selectSessionHandleAtIndex(visible, 1))
        assertNull(WorkspaceSessionPolicy.selectSessionHandleAtIndex(visible, -1))
        assertNull(WorkspaceSessionPolicy.selectSessionHandleAtIndex(visible, 3))
    }

    @Test
    fun resetAllSleepStatesToAwakeClearsSleepingAndErrorStates() {
        val w1 = WorkspaceManager.createWorkspace("One", "/tmp/one")
        val w2 = WorkspaceManager.createWorkspace("Two", "/tmp/two")
        WorkspaceManager.setSleepState(w1.id, WorkspaceSleepState.SLEEPING)
        WorkspaceManager.setSleepState(w2.id, WorkspaceSleepState.ERROR)

        WorkspaceManager.resetAllSleepStatesToAwake()

        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(w1.id))
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(w2.id))
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(DEFAULT_WORKSPACE_ID))
    }
}
