package com.betermux.workspace

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.betermux.compose.BeTermuxComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h915dp")
class DualSidebarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun leftDrawerRendersWorkspacesBadgesAndOverflowMenuActions() {
        val context = RuntimeEnvironment.getApplication()
        val toggledSleepIds = mutableListOf<String>()
        val removedCalls = mutableListOf<Pair<String, Boolean>>()
        val addedWorkspaces = mutableListOf<Pair<String, String>>()

        val workspaces = listOf(
            WorkspaceDrawerItem(
                id = DEFAULT_WORKSPACE_ID,
                name = "Default",
                rootPath = "/data/data/com.termux/files/home",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.AWAKE,
                isActive = true,
                isDefault = true
            ),
            WorkspaceDrawerItem(
                id = "ws-sleep",
                name = "API Server",
                rootPath = "/home/projects/api",
                sessionCount = 2,
                sleepState = WorkspaceSleepState.SLEEPING,
                isActive = false,
                isDefault = false
            ),
            WorkspaceDrawerItem(
                id = "ws-susp",
                name = "ML Worker",
                rootPath = "/home/projects/ml",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.SUSPENDING,
                isActive = false,
                isDefault = false
            ),
            WorkspaceDrawerItem(
                id = "ws-wake",
                name = "Web App",
                rootPath = "/home/projects/web",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.RESUMING,
                isActive = false,
                isDefault = false
            ),
            WorkspaceDrawerItem(
                id = "ws-err",
                name = "Broken",
                rootPath = "/home/projects/broken",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.ERROR,
                isActive = false,
                isDefault = false
            )
        )

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                CollapsibleTabDrawer(
                    visible = true,
                    sessions = listOf(
                        DrawerSessionItem("h-def", "Default Shell", "ls -la", isActive = true, isRunning = true)
                    ),
                    workspaces = workspaces,
                    hibernationEnabled = true,
                    allWorkspaceSessions = mapOf(
                        DEFAULT_WORKSPACE_ID to listOf(
                            DrawerSessionItem("h-def", "Default Shell", "ls -la", isActive = true, isRunning = true)
                        ),
                        "ws-sleep" to listOf(
                            DrawerSessionItem("h-api-1", "uvicorn", "uvicorn main:app", isActive = false, isRunning = true)
                        )
                    ),
                    onDismiss = {},
                    onSelectSession = {},
                    onCloseSession = {},
                    onNewSession = {},
                    onOpenDevBrowser = {},
                    onOpenFileManager = {},
                    onOpenLocalhost = {},
                    onOpenQuickCommands = {},
                    onOpenSettings = {},
                    onSelectWorkspace = {},
                    onAddWorkspace = { name, path -> addedWorkspaces.add(name to path) },
                    onToggleSleepWorkspace = { toggledSleepIds.add(it) },
                    onRemoveWorkspace = { id, terminate -> removedCalls.add(id to terminate) }
                )
            }
        }

        // WORKSPACES section + Add Project button
        composeRule.onNodeWithText("WORKSPACES", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_add_workspace").assertIsDisplayed()

        // Honest sleep badges: zzz ONLY on SLEEPING, honest labels for SUSPENDING / RESUMING / ERROR
        composeRule.onNodeWithText("API Server").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_sleep_badge_ws-sleep", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("zzz").assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithText("zzz").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Suspending…").assertIsDisplayed()
        composeRule.onNodeWithText("Waking…").assertIsDisplayed()
        composeRule.onNodeWithText("Error").assertIsDisplayed()

        // Overflow menu on sleeping non-default workspace offers Wake and Remove
        composeRule.onNodeWithTag("workspace_overflow_ws-sleep").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Wake").assertIsDisplayed().performClick()
        assertEquals(listOf("ws-sleep"), toggledSleepIds)

        // Open overflow again -> Remove -> confirmation dialog
        composeRule.onNodeWithTag("workspace_overflow_ws-sleep").performClick()
        composeRule.onNodeWithText("Remove").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Remove Workspace", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("remove_workspace_confirm").performClick()
        assertEquals(listOf("ws-sleep" to false), removedCalls)

        // Add Project flow
        composeRule.onNodeWithTag("drawer_add_workspace").performClick()
        composeRule.onNodeWithTag("add_workspace_name_input").performTextInput("Mobile")
        composeRule.onNodeWithTag("add_workspace_path_input").performTextInput("/home/projects/mobile")
        composeRule.onNodeWithTag("add_workspace_confirm").performClick()
        assertEquals(listOf("Mobile" to "/home/projects/mobile"), addedWorkspaces)
    }

    @Test
    fun tappingTerminalInAnotherWorkspaceInvokesSelectSessionAndTriggersCrossWorkspaceSwitchAndWake() {
        val context = RuntimeEnvironment.getApplication()
        val selectedHandles = mutableListOf<String>()
        val wokenWorkspaces = mutableListOf<String>()
        val switchedWorkspaces = mutableListOf<String>()

        val workspaces = listOf(
            WorkspaceDrawerItem(
                id = DEFAULT_WORKSPACE_ID,
                name = "Default",
                rootPath = "/home",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.AWAKE,
                isActive = true,
                isDefault = true
            ),
            WorkspaceDrawerItem(
                id = "ws-backend",
                name = "Backend",
                rootPath = "/home/backend",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.SLEEPING,
                isActive = false,
                isDefault = false
            )
        )
        val sessionsByWorkspace = mapOf(
            DEFAULT_WORKSPACE_ID to listOf(
                DrawerSessionItem("h-def", "Shell 1", null, isActive = true, isRunning = true)
            ),
            "ws-backend" to listOf(
                DrawerSessionItem("h-api", "API Shell", "npm run dev", isActive = false, isRunning = true)
            )
        )

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                CollapsibleTabDrawer(
                    visible = true,
                    sessions = sessionsByWorkspace[DEFAULT_WORKSPACE_ID].orEmpty(),
                    workspaces = workspaces,
                    hibernationEnabled = true,
                    allWorkspaceSessions = sessionsByWorkspace,
                    onDismiss = {},
                    onSelectSession = { handle ->
                        selectedHandles.add(handle)
                        val owner = sessionsByWorkspace.entries.firstOrNull { (_, list) ->
                            list.any { it.handle == handle }
                        }?.key
                        if (owner != null) {
                            switchedWorkspaces.add(owner)
                            val ws = workspaces.firstOrNull { it.id == owner }
                            if (ws?.sleepState == WorkspaceSleepState.SLEEPING) {
                                wokenWorkspaces.add(owner)
                            }
                        }
                    },
                    onCloseSession = {},
                    onNewSession = {},
                    onOpenDevBrowser = {},
                    onOpenFileManager = {},
                    onOpenLocalhost = {},
                    onOpenQuickCommands = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag("drawer_session_h-api").assertIsDisplayed().performClick()
        assertEquals(listOf("h-api"), selectedHandles)
        assertEquals(listOf("ws-backend"), switchedWorkspaces)
        assertEquals(listOf("ws-backend"), wokenWorkspaces)
    }

    @Test
    fun rightDrawerOverlayInterceptsBackAndInvokesOnDismiss() {
        val context = RuntimeEnvironment.getApplication()
        val backDispatcher = OnBackPressedDispatcher {}
        val lifecycleRegistry = LifecycleRegistry(object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
        })
        val owner = object : OnBackPressedDispatcherOwner {
            override val lifecycle: Lifecycle = lifecycleRegistry
            override val onBackPressedDispatcher: OnBackPressedDispatcher = backDispatcher
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        var dismissCalls = 0
        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                BeTermuxComposeTheme(context) {
                    RightDrawerOverlay(
                        visible = visible,
                        onDismiss = {
                            dismissCalls++
                            visible = false
                        }
                    ) {
                        Text("Right Drawer Content")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("right_drawer_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Right Drawer Content").assertIsDisplayed()

        composeRule.runOnUiThread {
            backDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(1, dismissCalls)
        composeRule.onNodeWithTag("right_drawer_overlay").assertDoesNotExist()
    }

    @Test
    fun allInteractiveControlsExposeContentDescriptionSemantics() {
        val context = RuntimeEnvironment.getApplication()
        val hasContentDescription = SemanticsMatcher("has non-empty ContentDescription") { node ->
            val list = node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
            !list.isNullOrEmpty() && list.all { it.isNotBlank() }
        }

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                CollapsibleTabDrawer(
                    visible = true,
                    sessions = listOf(
                        DrawerSessionItem("h1", "Main", "echo hi", isActive = true, isRunning = true)
                    ),
                    workspaces = listOf(
                        WorkspaceDrawerItem(
                            id = DEFAULT_WORKSPACE_ID,
                            name = "Default",
                            rootPath = "/home",
                            sessionCount = 1,
                            sleepState = WorkspaceSleepState.AWAKE,
                            isActive = true,
                            isDefault = true
                        ),
                        WorkspaceDrawerItem(
                            id = "w2",
                            name = "Frontend",
                            rootPath = "/home/fe",
                            sessionCount = 0,
                            sleepState = WorkspaceSleepState.SLEEPING,
                            isActive = false,
                            isDefault = false
                        )
                    ),
                    hibernationEnabled = true,
                    onDismiss = {},
                    onSelectSession = {},
                    onCloseSession = {},
                    onNewSession = {},
                    onOpenDevBrowser = {},
                    onOpenFileManager = {},
                    onOpenLocalhost = {},
                    onOpenQuickCommands = {},
                    onOpenSettings = {}
                )
            }
        }

        val interactiveTags = listOf(
            "drawer_new_session",
            "drawer_close_button",
            "drawer_add_workspace",
            "workspace_row_default",
            "workspace_row_w2",
            "workspace_overflow_default",
            "workspace_overflow_w2",
            "drawer_session_h1",
            "drawer_close_session_h1"
        )
        interactiveTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assert(hasContentDescription)
        }
        composeRule.onNodeWithContentDescription("Dev Browser").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("File Manager").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Localhost Servers").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Quick Commands").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
