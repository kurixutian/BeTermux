package com.betermux.workspace

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.features.BeTermuxSettings
import com.betermux.theme.LocalWarpPalette
import com.betermux.theme.rememberReduceMotion
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.app.activities.SettingsActivity
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

    fun attachToContentRoot(contentRoot: FrameLayout?) {
        if (contentRoot == null) return

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
                val hibernationEnabled = BeTermuxSettings.isWorkspaceHibernationEnabled(activity)
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
                        WorkspaceDrawerItem(
                            id = ws.id,
                            name = ws.name,
                            rootPath = ws.rootPath,
                            sessionCount = wsSessions.size,
                            sleepState = workspaceSnapshot.sleepStates[ws.id] ?: WorkspaceSleepState.AWAKE,
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
                    RightDrawerOverlay(
                        visible = rightVisible,
                        onDismiss = { closeRightDrawer() }
                    ) {
                        RightDrawerShellContent(
                            workspaceName = activeWorkspace?.name ?: "Default",
                            rootPath = activeWorkspace?.rootPath ?: "",
                            onClose = { closeRightDrawer() },
                            onOpenFullFileManager = {
                                closeRightDrawer()
                                activity.openFileManagerActivity()
                            }
                        )
                    }
                }
            }
        }
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

@Composable
private fun RightDrawerShellContent(
    workspaceName: String,
    rootPath: String,
    onClose: () -> Unit,
    onOpenFullFileManager: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Project Files",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = workspaceName,
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("right_drawer_close")
                    .semantics { contentDescription = "Close project files drawer" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "WORKSPACE ROOT",
                color = palette.textMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = rootPath.ifBlank { "~" },
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated, shape)
                    .border(BorderStroke(1.dp, palette.borderSubtle), shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(palette.surfaceElevated, shape)
                    .border(1.dp, palette.borderStrong, shape)
                    .clickable(onClick = onOpenFullFileManager)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("right_drawer_open_file_manager")
                    .semantics { contentDescription = "Open full File Manager" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Open Full File Manager",
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
