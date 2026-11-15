package com.newtermux.workspace

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.termux.R
import com.newtermux.blocks.BlockHostBridge
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.compose.WarpThemeState
import com.newtermux.features.NewTermuxSettings
import com.termux.app.TermuxActivity
import com.termux.app.activities.SettingsActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Coordinates the left-edge {@link CollapsibleTabDrawer} overlay and workspace UI zoom updates.
 */
class WorkspaceHostBridge(
    private val activity: TermuxActivity,
    private val blockBridgeProvider: () -> BlockHostBridge?
) {

    private val drawerVisibleFlow = MutableStateFlow(false)
    private val sessionsFlow = MutableStateFlow<List<DrawerSessionItem>>(emptyList())
    private val zoomPercentFlow = MutableStateFlow(100)

    private var drawerView: ComposeView? = null

    fun attachToContentRoot(contentRoot: FrameLayout?) {
        zoomPercentFlow.value = NewTermuxSettings.getUiZoomPercent(activity)
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
            NewTermuxComposeTheme(activity) {
                val visible by drawerVisibleFlow.collectAsState()
                val sessions by sessionsFlow.collectAsState()
                val zoomPct by zoomPercentFlow.collectAsState()
                val blockBridge = blockBridgeProvider()
                val splitSecondary = blockBridge?.let {
                    it.splitSecondaryHandleFlow.collectAsState().value
                }

                CollapsibleTabDrawer(
                    visible = visible,
                    sessions = sessions,
                    splitActive = !splitSecondary.isNullOrEmpty(),
                    zoomPercent = zoomPct,
                    onDismiss = { closeDrawer() },
                    onSelectSession = { handle ->
                        val sess = blockBridge?.controller?.getSession(handle)
                            ?: findTermuxSession(handle)
                        if (sess != null) {
                            activity.switchToSession(sess)
                        }
                    },
                    onCloseSession = { handle ->
                        val sess = blockBridge?.controller?.getSession(handle)
                            ?: findTermuxSession(handle)
                        if (sess != null) {
                            closeDrawer()
                            activity.closeSession(sess)
                        }
                    },
                    onNewSession = { activity.performToolbarAction(R.id.btn_new_session) },
                    onToggleSplit = { chosenHandle -> toggleSplitPane(chosenHandle) },
                    onOpenDevBrowser = { activity.openBrowserTab("https://www.google.com") },
                    onOpenFileManager = { activity.performToolbarAction(R.id.btn_files) },
                    onOpenLocalhost = { activity.openLocalhostTab() },
                    onOpenQuickCommands = { activity.showQuickCommandsDialog() },
                    onOpenSettings = {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java))
                    },
                    onChangeZoomPercent = { nextPct ->
                        NewTermuxSettings.setUiZoomPercent(activity, nextPct)
                        zoomPercentFlow.value = NewTermuxSettings.getUiZoomPercent(activity)
                        WarpThemeState.notifyChanged()
                        syncTerminalZoom()
                    }
                )
            }
        }
    }

    fun openDrawer() {
        refreshSessions()
        drawerVisibleFlow.value = true
        drawerView?.visibility = View.VISIBLE
    }

    fun closeDrawer() {
        drawerVisibleFlow.value = false
        drawerView?.visibility = View.GONE
    }

    fun toggleDrawer() {
        if (drawerVisibleFlow.value) closeDrawer() else openDrawer()
    }

    fun refreshSettings() {
        zoomPercentFlow.value = NewTermuxSettings.getUiZoomPercent(activity)
        syncTerminalZoom()
    }

    fun refreshSessions() {
        val service = activity.termuxService ?: return
        val current = activity.currentSession
        val blockController = blockBridgeProvider()?.controller
        val splitHandle = blockBridgeProvider()?.splitSecondaryHandleFlow?.value
        val items = service.termuxSessions.mapNotNull { ts ->
            val raw = ts?.terminalSession ?: return@mapNotNull null
            DrawerSessionItem(
                handle = raw.mHandle,
                title = raw.title ?: "Terminal",
                lastCommandSummary = blockController?.getLastCommandSummary(raw.mHandle),
                isActive = raw == current,
                isRunning = raw.isRunning,
                isSplitCompanion = raw.mHandle == splitHandle
            )
        }
        sessionsFlow.value = items
    }

    fun toggleSplitPane(preferredHandle: String?) {
        val bridge = blockBridgeProvider() ?: return
        val current = activity.currentSession ?: return
        val service = activity.termuxService ?: return

        if (preferredHandle != null && preferredHandle != current.mHandle) {
            bridge.enableSplitWithSession(current.mHandle, preferredHandle)
            refreshSessions()
            return
        }

        if (!bridge.splitSecondaryHandleFlow.value.isNullOrEmpty()) {
            bridge.exitSplitMode()
            refreshSessions()
            return
        }

        val other = service.termuxSessions
            .mapNotNull { it?.terminalSession }
            .firstOrNull { it.mHandle != current.mHandle }
        if (other != null) {
            bridge.enableSplitWithSession(current.mHandle, other.mHandle)
        } else {
            val previousHandle = current.mHandle
            activity.performToolbarAction(R.id.btn_new_session)
            val created = activity.currentSession
            if (created != null && created.mHandle != previousHandle) {
                bridge.enableSplitWithSession(created.mHandle, previousHandle)
            }
        }
        refreshSessions()
    }

    fun syncTerminalZoom() {
        val prefs = activity.preferences ?: return
        val tv = activity.terminalView ?: return
        val factor = NewTermuxSettings.getUiZoomFactor(activity)
        val scaledPx = Math.round(prefs.fontSize * factor).coerceIn(12, 96)
        tv.setTextSize(scaledPx)
    }

    private fun findTermuxSession(handle: String) =
        activity.termuxService?.termuxSessions
            ?.mapNotNull { it?.terminalSession }
            ?.firstOrNull { it.mHandle == handle }
}
