package com.betermux.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.features.BeTermuxSettings
import com.betermux.theme.rememberReduceMotion
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.app.activities.SettingsActivity
import com.termux.app.activities.openExternalEditor
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Coordinates the left-edge [CollapsibleTabDrawer] overlay, right-edge [RightDrawerOverlay],
 * workspace state observation, and terminal font-size sync.
 */
class WorkspaceHostBridge(
    private val activity: TermuxActivity
) {

    private val drawerVisibleFlow = MutableStateFlow(false)
    private val rightDrawerVisibleFlow = MutableStateFlow(false)
    private val rawSessionsFlow = MutableStateFlow<List<DrawerSessionItem>>(emptyList())
    private val settingsVersionFlow = MutableStateFlow(0)

    private var drawerView: ComposeView? = null
    private var bannerView: ComposeView? = null

    fun attachToContentRoot(contentRoot: FrameLayout?) {
        if (contentRoot == null) return

        attachBannerToTerminalContainer(activity.findViewById(R.id.terminal_container))

        val existing = contentRoot.findViewWithTag<ComposeView>("workspace_drawer_overlay")
        val view = existing ?: ComposeView(activity).apply {
            tag = "workspace_drawer_overlay"
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            contentRoot.addView(this)
        }
        drawerView = view

        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            BeTermuxComposeTheme(activity) {
                val visible by drawerVisibleFlow.collectAsState()
                val rightVisible by rightDrawerVisibleFlow.collectAsState()
                val rawSessions by rawSessionsFlow.collectAsState()
                val workspaceSnapshot by WorkspaceManager.state.collectAsState()
                settingsVersionFlow.collectAsState().value
                val reduceMotion = rememberReduceMotion()

                LaunchedEffect(visible, rightVisible) {
                    if (!visible && !rightVisible) {
                        if (!reduceMotion) delay(280L)
                        if (!drawerVisibleFlow.value && !rightDrawerVisibleFlow.value) {
                            drawerView?.visibility = View.GONE
                        }
                    }
                }

                val workspacesEnabled = BeTermuxSettings.isWorkspacesEnabled(activity)
                val hibernationEnabled = activity.isWorkspaceHibernationControllerReady()
                val rightExplorerEnabled = BeTermuxSettings.isDualSidebarsEnabled(activity) &&
                    BeTermuxSettings.isRightFileExplorerEnabled(activity)

                val liveByHandle = rawSessions.associateBy { it.handle }
                val allWorkspaceSessions = if (workspacesEnabled) {
                    workspaceSnapshot.workspaces.associate { ws ->
                        ws.id to ws.sessionHandles.mapNotNull { liveByHandle[it] }
                    }
                } else {
                    emptyMap()
                }
                val workspaces = if (workspacesEnabled) {
                    workspaceSnapshot.workspaces.map { ws ->
                        val wsSessions = allWorkspaceSessions[ws.id].orEmpty()
                        val effectiveSleepState = if (hibernationEnabled) {
                            workspaceSnapshot.sleepStates[ws.id] ?: WorkspaceSleepState.AWAKE
                        } else {
                            WorkspaceSleepState.AWAKE
                        }
                        WorkspaceDrawerItem(
                            id = ws.id,
                            name = ws.name,
                            rootPath = ws.rootPath,
                            sessionCount = wsSessions.size,
                            sleepState = effectiveSleepState,
                            isActive = ws.id == workspaceSnapshot.activeWorkspaceId,
                            isDefault = ws.id == DEFAULT_WORKSPACE_ID
                        )
                    }
                } else {
                    emptyList()
                }

                val activeWorkspace = workspaceSnapshot.workspaces
                    .firstOrNull { it.id == workspaceSnapshot.activeWorkspaceId }
                    ?: workspaceSnapshot.workspaces.firstOrNull()

                CollapsibleTabDrawer(
                    visible = visible,
                    sessions = rawSessions,
                    workspaces = workspaces,
                    hibernationEnabled = hibernationEnabled,
                    allWorkspaceSessions = allWorkspaceSessions,
                    onDismiss = { closeDrawer() },
                    onSelectSession = { handle ->
                        val sess = findTermuxSession(handle)
                        if (sess != null) {
                            activity.switchToSession(sess)
                        }
                    },
                    onCloseSession = { handle ->
                        val sess = findTermuxSession(handle)
                        if (sess != null) {
                            closeDrawer()
                            activity.closeSession(sess)
                        }
                    },
                    onNewSession = { activity.performToolbarAction(R.id.btn_new_session) },
                    onOpenDevBrowser = { activity.openBrowserTab("https://www.google.com") },
                    onOpenFileManager = { activity.openFileManagerActivity() },
                    onOpenLocalhost = { activity.openLocalhostTab() },
                    onOpenQuickCommands = { activity.showQuickCommandsDialog() },
                    onOpenSettings = {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java))
                    },
                    onSelectWorkspace = { workspaceId ->
                        activity.switchToWorkspace(workspaceId)
                    },
                    onAddWorkspace = { name, rootPath ->
                        activity.createAndSwitchWorkspace(name, rootPath)
                    },
                    onToggleSleepWorkspace = { workspaceId ->
                        activity.toggleWorkspaceSleep(workspaceId)
                    },
                    onRemoveWorkspace = { workspaceId, terminateSessions ->
                        activity.removeWorkspaceFromUi(workspaceId, terminateSessions)
                    }
                )

                if (rightExplorerEnabled) {
                    val activeWs = activeWorkspace
                    val activeRoot = activeWs?.rootPath?.takeIf { it.isNotBlank() }
                        ?: TermuxConstants.TERMUX_HOME_DIR_PATH
                    RightDrawerOverlay(
                        visible = rightVisible,
                        onDismiss = { closeRightDrawer() }
                    ) {
                        RightFileDrawer(
                            workspaceName = activeWs?.name ?: "Default",
                            rootPath = activeRoot,
                            onClose = { closeRightDrawer() },
                            onOpenFile = { file ->
                                closeRightDrawer()
                                activity.openFileTab(file)
                            },
                            onOpenExternally = { file ->
                                openExternalEditor(activity, file)
                            },
                            onCopyPath = { path ->
                                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Path", path))
                                Toast.makeText(activity, "Copied path", Toast.LENGTH_SHORT).show()
                            },
                            onOpenTerminalHere = { dirPath ->
                                closeRightDrawer()
                                if (activeWs != null) {
                                    activity.requestWorkspaceWakeIfNeeded(activeWs.id) {
                                        activity.cdCurrentSessionTo(dirPath)
                                    }
                                } else {
                                    activity.cdCurrentSessionTo(dirPath)
                                }
                            },
                            onOpenFullFileManager = {
                                closeRightDrawer()
                                activity.openFileManagerActivity()
                            },
                            onOpenHomeRoot = {
                                closeRightDrawer()
                                activity.cdCurrentSessionTo(TermuxConstants.TERMUX_HOME_DIR_PATH)
                            },
                            onSetupStorageHint = {
                                closeRightDrawer()
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", activity.packageName, null)
                                    )
                                    activity.startActivity(intent)
                                } catch (_: Exception) {
                                    activity.openFileManagerActivity()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun attachBannerToTerminalContainer(container: FrameLayout?) {
        if (container == null) return

        val existing = container.findViewWithTag<ComposeView>("workspace_hibernation_banner_host")
        val view = existing ?: ComposeView(activity).apply {
            tag = "workspace_hibernation_banner_host"
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
            visibility = View.GONE
            container.addView(this)
        }
        bannerView = view

        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            BeTermuxComposeTheme(activity) {
                val workspaceSnapshot by WorkspaceManager.state.collectAsState()
                settingsVersionFlow.collectAsState().value

                val workspacesEnabled = BeTermuxSettings.isWorkspacesEnabled(activity)
                val hibernationEnabled = activity.isWorkspaceHibernationControllerReady()
                val activeWorkspace = workspaceSnapshot.workspaces
                    .firstOrNull { it.id == workspaceSnapshot.activeWorkspaceId }
                    ?: workspaceSnapshot.workspaces.firstOrNull()
                val sleepState = activeWorkspace?.let { workspaceSnapshot.sleepStates[it.id] }
                    ?: WorkspaceSleepState.AWAKE
                val showBanner = workspacesEnabled && hibernationEnabled && sleepState != WorkspaceSleepState.AWAKE

                LaunchedEffect(showBanner) {
                    bannerView?.visibility = if (showBanner) View.VISIBLE else View.GONE
                }

                if (showBanner && activeWorkspace != null) {
                    WorkspaceHibernationBanner(
                        sleepState = sleepState,
                        onWakeClick = { activity.requestWorkspaceWakeIfNeeded(activeWorkspace.id) }
                    )
                }
            }
        }
    }

    fun destroy() {
        drawerView?.disposeComposition()
        drawerView = null
        bannerView?.disposeComposition()
        bannerView = null
    }

    fun openDrawer() {
        if (rightDrawerVisibleFlow.value) {
            closeRightDrawer()
        }
        refreshSessions()
        drawerVisibleFlow.value = true
        drawerView?.visibility = View.VISIBLE
    }

    fun closeDrawer() {
        drawerVisibleFlow.value = false
    }

    fun toggleDrawer() {
        if (drawerVisibleFlow.value) closeDrawer() else openDrawer()
    }

    fun isDrawerOpen(): Boolean = drawerVisibleFlow.value

    fun openRightDrawer() {
        val enabled = BeTermuxSettings.isDualSidebarsEnabled(activity) &&
            BeTermuxSettings.isRightFileExplorerEnabled(activity)
        if (!enabled) return
        if (drawerVisibleFlow.value) {
            closeDrawer()
        }
        rightDrawerVisibleFlow.value = true
        drawerView?.visibility = View.VISIBLE
        activity.setTabBarSuppressedForRightDrawer(true)
    }

    fun closeRightDrawer() {
        if (!rightDrawerVisibleFlow.value) return
        rightDrawerVisibleFlow.value = false
        activity.setTabBarSuppressedForRightDrawer(false)
    }

    fun toggleRightDrawer() {
        if (rightDrawerVisibleFlow.value) closeRightDrawer() else openRightDrawer()
    }

    fun isRightDrawerOpen(): Boolean = rightDrawerVisibleFlow.value

    fun refreshSettings() {
        settingsVersionFlow.value += 1
        val rightEnabled = BeTermuxSettings.isDualSidebarsEnabled(activity) &&
            BeTermuxSettings.isRightFileExplorerEnabled(activity)
        if (!rightEnabled && rightDrawerVisibleFlow.value) {
            closeRightDrawer()
        }
        syncTerminalZoom()
    }

    fun refreshSessions() {
        val service = activity.termuxService ?: return
        val current = activity.currentSession
        val items = service.termuxSessions.mapNotNull { ts ->
            val raw = ts?.terminalSession ?: return@mapNotNull null
            DrawerSessionItem(
                handle = raw.mHandle,
                title = raw.title ?: "Terminal",
                lastCommandSummary = null,
                isActive = raw == current,
                isRunning = raw.isRunning
            )
        }
        rawSessionsFlow.value = items
    }

    fun syncTerminalZoom() {
        val prefs = activity.preferences ?: return
        val tv = activity.terminalView ?: return
        val factor = BeTermuxSettings.getUiZoomFactor(activity)
        val scaledPx = Math.round(prefs.fontSize * factor).coerceIn(12, 96)
        tv.setTextSize(scaledPx)
    }

    private fun findTermuxSession(handle: String) =
        activity.termuxService?.termuxSessions
            ?.mapNotNull { it?.terminalSession }
            ?.firstOrNull { it.mHandle == handle }
}

data class WorkspaceRemovalPlan(
    val terminateBeforeSwitch: List<String>,
    val ensureActiveWorkspaceSession: Boolean,
    val terminateAfterSwitch: List<String>,
    val targetActiveWorkspaceId: String
)

/**
 * Pure session-transition planner for workspace removal so slot-freeing closures happen
 * before replacement session creation while preventing accidental zero-session activity exit.
 */
object WorkspaceRemovalPolicy {
    @JvmStatic
    fun planRemoval(
        removedWorkspaceId: String,
        previousActiveWorkspaceId: String,
        targetActiveWorkspaceId: String,
        removedHandles: List<String>,
        liveHandles: List<String>,
        targetActiveWorkspaceHandlesAfterRemoval: Set<String>,
        currentSessionHandle: String?,
        terminateSessions: Boolean
    ): WorkspaceRemovalPlan {
        val wasActiveWorkspace = removedWorkspaceId == previousActiveWorkspaceId
        val liveSet = liveHandles.toHashSet()
        val toClose = if (terminateSessions) {
            removedHandles.distinct().filter { it in liveSet }
        } else {
            emptyList()
        }
        val toCloseSet = toClose.toHashSet()
        val remainingOutsideClose = liveHandles.filter { it !in toCloseSet }
        val currentClosed = currentSessionHandle != null && currentSessionHandle in toCloseSet
        val currentInTargetAfter = currentSessionHandle != null &&
            currentSessionHandle in liveSet &&
            !currentClosed &&
            currentSessionHandle in targetActiveWorkspaceHandlesAfterRemoval
        val needsSessionInTarget = wasActiveWorkspace && !currentInTargetAfter || currentClosed

        return if (remainingOutsideClose.isNotEmpty() || toClose.isEmpty()) {
            WorkspaceRemovalPlan(
                terminateBeforeSwitch = toClose,
                ensureActiveWorkspaceSession = needsSessionInTarget,
                terminateAfterSwitch = emptyList(),
                targetActiveWorkspaceId = targetActiveWorkspaceId
            )
        } else {
            WorkspaceRemovalPlan(
                terminateBeforeSwitch = toClose.dropLast(1),
                ensureActiveWorkspaceSession = true,
                terminateAfterSwitch = listOf(toClose.last()),
                targetActiveWorkspaceId = targetActiveWorkspaceId
            )
        }
    }
}
