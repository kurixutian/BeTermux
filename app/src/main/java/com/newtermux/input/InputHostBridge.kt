package com.newtermux.input

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.newtermux.blocks.BlockHostBridge
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.features.NewTermuxSettings
import com.termux.app.TermuxActivity
import com.termux.shared.interact.ShareUtils
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/**
 * Activity bridge coordinating the persistent {@link InputDraftStore}, top/bottom
 * {@link TwoRowInputBar} slots, context chip updates, and {@link FloatingEditLayer}.
 */
class InputHostBridge(
    private val activity: TermuxActivity,
    private val blockBridgeProvider: () -> BlockHostBridge?
) {

    val draftStore = InputDraftStore(activity)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var contextResolveJob: Job? = null

    private val activeHandleFlow = MutableStateFlow("")
    private val isTopPositionFlow = MutableStateFlow(false)
    private val editorVisibleFlow = MutableStateFlow(false)
    private val overlayHiddenFlow = MutableStateFlow(false)
    private val promptContextFlow = MutableStateFlow(
        PromptContextResolver.resolve(null, null)
    )

    private var topBarView: ComposeView? = null
    private var bottomBarView: ComposeView? = null
    private var floatingOverlayView: ComposeView? = null
    private var onOpenWorkflowsHook: (() -> Unit)? = null

    fun setOpenWorkflowsHandler(handler: (() -> Unit)?) {
        onOpenWorkflowsHook = handler
    }

    fun attach(
        topSlot: ComposeView?,
        bottomSlot: ComposeView?,
        overlaySlot: ComposeView?
    ) {
        topBarView = topSlot
        bottomBarView = bottomSlot
        floatingOverlayView = overlaySlot

        refreshSettings()

        topSlot?.let { view ->
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.setContent {
                NewTermuxComposeTheme(activity) {
                    val isTop by isTopPositionFlow.collectAsState()
                    if (isTop) {
                        RenderDockedBar(isTopPosition = true)
                    }
                }
            }
        }

        bottomSlot?.let { view ->
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.setContent {
                NewTermuxComposeTheme(activity) {
                    val isTop by isTopPositionFlow.collectAsState()
                    if (!isTop) {
                        RenderDockedBar(isTopPosition = false)
                    }
                }
            }
        }

        overlaySlot?.let { view ->
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.setContent {
                NewTermuxComposeTheme(activity) {
                    val visible by editorVisibleFlow.collectAsState()
                    val handle by activeHandleFlow.collectAsState()
                    val drafts by draftStore.drafts.collectAsState()
                    val ctx by promptContextFlow.collectAsState()
                    val isTop by isTopPositionFlow.collectAsState()
                    val draft = drafts[handle].orEmpty()

                    FloatingEditLayer(
                        visible = visible,
                        draft = draft,
                        promptContext = ctx,
                        isTopPosition = isTop,
                        onDraftChange = { next ->
                            if (handle.isNotEmpty()) draftStore.setDraft(handle, next)
                        },
                        onSend = { cmd ->
                            dispatchCommand(cmd)
                            if (handle.isNotEmpty()) draftStore.clearDraft(handle)
                            closeEditorPreservingDraft()
                        },
                        onClosePreservingDraft = { closeEditorPreservingDraft() },
                        onTogglePosition = { togglePosition() },
                        onOpenWorkflows = onOpenWorkflowsHook
                    )
                }
            }
        }

        syncVisibility()
    }

    @Composable
    private fun RenderDockedBar(isTopPosition: Boolean) {
        val handle by activeHandleFlow.collectAsState()
        val drafts by draftStore.drafts.collectAsState()
        val ctx by promptContextFlow.collectAsState()
        val draft = drafts[handle].orEmpty()

        TwoRowInputBar(
            promptContext = ctx,
            draft = draft,
            isTopPosition = isTopPosition,
            onOpenEditor = { openEditor() },
            onSendCommand = { cmd ->
                dispatchCommand(cmd)
                if (handle.isNotEmpty()) draftStore.clearDraft(handle)
            },
            onClearDraft = {
                if (handle.isNotEmpty()) draftStore.clearDraft(handle)
            },
            onSendEnter = { sendRaw("\r") },
            onExecuteChipCommand = { cmd -> dispatchCommand(cmd) },
            onInsertChipText = { snippet ->
                if (handle.isNotEmpty()) {
                    draftStore.appendText(handle, snippet)
                    openEditor()
                }
            },
            onCopyChipText = { value, label ->
                ShareUtils.copyTextToClipboard(activity, value, label)
            },
            onOpenWorkflows = onOpenWorkflowsHook,
            onJumpLastFailure = {
                blockBridgeProvider()?.controller?.jumpToLastFailure(handle)
            }
        )
    }

    fun refreshSettings() {
        val top = NewTermuxSettings.INPUT_POSITION_TOP == NewTermuxSettings.getInputBarPosition(activity)
        isTopPositionFlow.value = top
        syncVisibility()
    }

    fun togglePosition() {
        val next = if (isTopPositionFlow.value) {
            NewTermuxSettings.INPUT_POSITION_BOTTOM
        } else {
            NewTermuxSettings.INPUT_POSITION_TOP
        }
        NewTermuxSettings.setInputBarPosition(activity, next)
        refreshSettings()
    }

    fun syncSessions(sessions: List<TerminalSession>, currentSession: TerminalSession?) {
        draftStore.syncSessionHandles(sessions.map { it.mHandle })
        onActiveSessionChanged(currentSession)
    }

    fun onActiveSessionChanged(session: TerminalSession?) {
        val handle = session?.mHandle.orEmpty()
        if (activeHandleFlow.value != handle) {
            activeHandleFlow.value = handle
        }
        refreshPromptContext(session)
        syncVisibility()
    }

    fun onSessionContextSignal(session: TerminalSession?) {
        if (session == activity.currentSession) {
            refreshPromptContext(session)
        }
    }

    fun onSessionRemoved(session: TerminalSession?) {
        val handle = session?.mHandle ?: return
        draftStore.removeSessionDraft(handle, commitSync = true)
    }

    fun hasUnsavedDraft(session: TerminalSession?): Boolean {
        val handle = session?.mHandle ?: return false
        return draftStore.hasNonBlankDraft(handle)
    }

    fun getDraftForSession(session: TerminalSession?): String {
        val handle = session?.mHandle ?: return ""
        return draftStore.getDraft(handle)
    }

    fun insertIntoActiveDraft(text: String, openEditorAfter: Boolean = false) {
        val handle = activeHandleFlow.value.ifEmpty { activity.currentSession?.mHandle.orEmpty() }
        if (handle.isEmpty() || text.isEmpty()) return
        draftStore.setDraft(handle, text)
        if (openEditorAfter) openEditor()
    }

    fun openEditor() {
        if (overlayHiddenFlow.value) return
        editorVisibleFlow.value = true
        syncVisibility()
    }

    fun closeEditorPreservingDraft() {
        editorVisibleFlow.value = false
        syncVisibility()
    }

    fun setOverlayHidden(hidden: Boolean) {
        overlayHiddenFlow.value = hidden
        if (hidden && editorVisibleFlow.value) {
            editorVisibleFlow.value = false
        }
        syncVisibility()
    }

    fun onPause() {
        draftStore.flushAllSync()
    }

    fun destroy() {
        draftStore.flushAllSync()
        contextResolveJob?.cancel()
        scope.cancel()
    }

    fun syncVisibility() {
        val showBar = !overlayHiddenFlow.value && activeHandleFlow.value.isNotEmpty()
        val isTop = isTopPositionFlow.value
        val showEditor = showBar && editorVisibleFlow.value
        topBarView?.visibility = if (showBar && isTop) View.VISIBLE else View.GONE
        bottomBarView?.visibility = if (showBar && !isTop) View.VISIBLE else View.GONE
        floatingOverlayView?.visibility = if (showEditor) View.VISIBLE else View.GONE
    }

    private fun refreshPromptContext(session: TerminalSession?) {
        val handle = session?.mHandle.orEmpty()
        val rawCwd = blockBridgeProvider()?.controller?.stateFlowForSession(handle)?.value?.cwd
            ?: runCatching { session?.cwd }.getOrNull()
        val lastExit = blockBridgeProvider()?.controller?.stateFlowForSession(handle)?.value
            ?.blocks?.lastOrNull { it.exitCode != null }?.exitCode

        contextResolveJob?.cancel()
        contextResolveJob = scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                PromptContextResolver.resolve(rawCwd, lastExit)
            }
            if (activeHandleFlow.value == handle) {
                promptContextFlow.value = resolved
            }
        }
    }

    private fun dispatchCommand(command: String) {
        val normalized = command.replace("\r\n", "\n").trimEnd('\n')
        if (normalized.isEmpty()) return
        val session = activity.currentSession ?: return
        val payload = "$normalized\n".toByteArray(StandardCharsets.UTF_8)
        session.write(payload, 0, payload.size)
    }

    private fun sendRaw(text: String) {
        val session = activity.currentSession ?: return
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        session.write(payload, 0, payload.size)
    }
}
