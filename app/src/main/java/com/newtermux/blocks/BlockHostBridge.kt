package com.newtermux.blocks

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.newtermux.ai.AiAction
import com.newtermux.ai.AiSuggestionState
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.features.NewTermuxSettings
import com.newtermux.workspace.SplitPaneWorkspace
import com.termux.app.TermuxActivity
import com.termux.shared.view.KeyboardUtils
import com.termux.shared.view.ViewUtils
import com.termux.terminal.Osc133Event
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Activity bridge wiring {@link BlockSessionController} and {@link BlockWorkspaceHost}
 * into {@link TermuxActivity} without bloating Java activity code.
 */
class BlockHostBridge(private val activity: TermuxActivity) {

    val controller = BlockSessionController()
    private val blockViewEnabledFlow = MutableStateFlow(true)
    private val densityFlow = MutableStateFlow(BlockDensity.COMFORT)
    private val searchTriggerFlow = MutableStateFlow(0)
    val splitSecondaryHandleFlow = MutableStateFlow<String?>(null)

    private var composeContainer: ComposeView? = null
    private var attachedTerminalView: TerminalView? = null
    private var saveWorkflowHandler: ((List<TerminalBlock>) -> Unit)? = null
    private var aiSuggestionsFlow: StateFlow<Map<Long, AiSuggestionState>>? = null
    private var askAiHandler: ((TerminalBlock, AiAction, TerminalBuffer?, String?) -> Unit)? = null
    private var aiAcceptHandler: ((Long, String) -> Unit)? = null
    private var aiEditHandler: ((Long, String) -> Unit)? = null
    private var aiRetryHandler: ((Long) -> Unit)? = null
    private var aiCancelHandler: (() -> Unit)? = null
    private var aiDismissHandler: ((Long) -> Unit)? = null
    private var openAiSettingsHandler: (() -> Unit)? = null

    fun setSaveWorkflowHandler(handler: ((List<TerminalBlock>) -> Unit)?) {
        saveWorkflowHandler = handler
    }

    fun setAiIntegration(
        suggestionsFlow: StateFlow<Map<Long, AiSuggestionState>>,
        onAsk: (TerminalBlock, AiAction, TerminalBuffer?, String?) -> Unit,
        onAccept: (Long, String) -> Unit,
        onEdit: (Long, String) -> Unit,
        onRetry: (Long) -> Unit,
        onCancel: () -> Unit,
        onDismiss: (Long) -> Unit,
        onOpenSettings: () -> Unit
    ) {
        aiSuggestionsFlow = suggestionsFlow
        askAiHandler = onAsk
        aiAcceptHandler = onAccept
        aiEditHandler = onEdit
        aiRetryHandler = onRetry
        aiCancelHandler = onCancel
        aiDismissHandler = onDismiss
        openAiSettingsHandler = onOpenSettings
    }

    fun attach(composeView: ComposeView?, terminalView: TerminalView?) {
        composeContainer = composeView
        attachedTerminalView = terminalView
        refreshSettings()

        if (composeView == null) return
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.setContent {
            NewTermuxComposeTheme(activity) {
                val blockViewEnabled by blockViewEnabledFlow.collectAsState()
                val density by densityFlow.collectAsState()
                val activeState by controller.activeState.collectAsState()
                val splitSecondaryHandle by splitSecondaryHandleFlow.collectAsState()
                val searchSeq by searchTriggerFlow.collectAsState()
                val showBlocks = shouldShowBlocks(blockViewEnabled, activeState)
                if (showBlocks) {
                    val companionHandle = splitSecondaryHandle?.takeIf {
                        it.isNotEmpty() && it != activeState.sessionHandle && controller.getSession(it) != null
                    }
                    if (companionHandle != null) {
                        val primarySess = controller.getSession(activeState.sessionHandle)
                        val companionSess = controller.getSession(companionHandle)
                        SplitPaneWorkspace(
                            primaryTitle = primarySess?.title ?: "Pane 1",
                            secondaryTitle = companionSess?.title ?: "Pane 2",
                            isPrimaryFocused = true,
                            onFocusPrimary = { requestSoftKeyboardFocus() },
                            onFocusSecondary = {
                                if (companionSess != null) {
                                    splitSecondaryHandleFlow.value = activeState.sessionHandle
                                    activity.switchToSession(companionSess)
                                }
                            },
                            onCloseSplit = { exitSplitMode() },
                            primaryContent = {
                                BlockWorkspaceHost(
                                    controller = controller,
                                    sessionProvider = { handle -> controller.getSession(handle) },
                                    painterProvider = { attachedTerminalView?.rowPainter },
                                    density = density,
                                    onTapTarget = { target, offset, cwd ->
                                        handleBlockTargetTap(composeView, target, offset, cwd)
                                    },
                                    onRequestTerminalInputFocus = { requestSoftKeyboardFocus() },
                                    onToggleDensity = { toggleDensityFromUi() },
                                    onToggleRawPty = { toggleRawPtyForActiveSession() },
                                    onSaveWorkflow = { blocks -> saveWorkflowHandler?.invoke(blocks) },
                                    aiSuggestionsFlow = aiSuggestionsFlow,
                                    onAskAiAction = { blk, act, buf, cwd -> askAiHandler?.invoke(blk, act, buf, cwd) },
                                    onAiAcceptCommand = { id, cmd -> aiAcceptHandler?.invoke(id, cmd) },
                                    onAiEditCommand = { id, cmd -> aiEditHandler?.invoke(id, cmd) },
                                    onAiRetry = { id -> aiRetryHandler?.invoke(id) },
                                    onAiCancel = { aiCancelHandler?.invoke() },
                                    onAiDismiss = { id -> aiDismissHandler?.invoke(id) },
                                    onOpenAiSettings = { openAiSettingsHandler?.invoke() },
                                    externalSearchTrigger = searchSeq
                                )
                            },
                            secondaryContent = {
                                BlockWorkspaceHost(
                                    controller = controller,
                                    sessionProvider = { handle -> controller.getSession(handle) },
                                    painterProvider = { attachedTerminalView?.rowPainter },
                                    density = density,
                                    onTapTarget = { target, offset, cwd ->
                                        handleBlockTargetTap(composeView, target, offset, cwd)
                                    },
                                    onRequestTerminalInputFocus = {
                                        if (companionSess != null) {
                                            splitSecondaryHandleFlow.value = activeState.sessionHandle
                                            activity.switchToSession(companionSess)
                                        }
                                    },
                                    onToggleDensity = { toggleDensityFromUi() },
                                    onToggleRawPty = { toggleRawPtyForActiveSession() },
                                    onSaveWorkflow = { blocks -> saveWorkflowHandler?.invoke(blocks) },
                                    aiSuggestionsFlow = aiSuggestionsFlow,
                                    onAskAiAction = { blk, act, buf, cwd -> askAiHandler?.invoke(blk, act, buf, cwd) },
                                    onAiAcceptCommand = { id, cmd -> aiAcceptHandler?.invoke(id, cmd) },
                                    onAiEditCommand = { id, cmd -> aiEditHandler?.invoke(id, cmd) },
                                    onAiRetry = { id -> aiRetryHandler?.invoke(id) },
                                    onAiCancel = { aiCancelHandler?.invoke() },
                                    onAiDismiss = { id -> aiDismissHandler?.invoke(id) },
                                    onOpenAiSettings = { openAiSettingsHandler?.invoke() },
                                    stateFlowOverride = controller.stateFlowForSession(companionHandle),
                                    onPaneFocused = {
                                        if (companionSess != null) {
                                            splitSecondaryHandleFlow.value = activeState.sessionHandle
                                            activity.switchToSession(companionSess)
                                        }
                                    }
                                )
                            }
                        )
                    } else {
                        BlockWorkspaceHost(
                            controller = controller,
                            sessionProvider = { handle -> controller.getSession(handle) },
                            painterProvider = { attachedTerminalView?.rowPainter },
                            density = density,
                            onTapTarget = { target, offset, cwd ->
                                handleBlockTargetTap(composeView, target, offset, cwd)
                            },
                            onRequestTerminalInputFocus = { requestSoftKeyboardFocus() },
                            onToggleDensity = { toggleDensityFromUi() },
                            onToggleRawPty = { toggleRawPtyForActiveSession() },
                            onSaveWorkflow = { blocks -> saveWorkflowHandler?.invoke(blocks) },
                            aiSuggestionsFlow = aiSuggestionsFlow,
                            onAskAiAction = { blk, act, buf, cwd -> askAiHandler?.invoke(blk, act, buf, cwd) },
                            onAiAcceptCommand = { id, cmd -> aiAcceptHandler?.invoke(id, cmd) },
                            onAiEditCommand = { id, cmd -> aiEditHandler?.invoke(id, cmd) },
                            onAiRetry = { id -> aiRetryHandler?.invoke(id) },
                            onAiCancel = { aiCancelHandler?.invoke() },
                            onAiDismiss = { id -> aiDismissHandler?.invoke(id) },
                            onOpenAiSettings = { openAiSettingsHandler?.invoke() },
                            externalSearchTrigger = searchSeq
                        )
                    }
                }
            }
        }
        syncVisibility()
    }

    fun refreshSettings() {
        blockViewEnabledFlow.value = NewTermuxSettings.isBlockViewEnabled(activity)
        densityFlow.value = BlockDensity.fromKey(NewTermuxSettings.getBlockDensity(activity))
        syncVisibility()
    }

    fun onActiveSessionChanged(session: TerminalSession?) {
        controller.setActiveSession(session)
        syncVisibility()
    }

    fun onTextChanged(session: TerminalSession) {
        controller.onSessionTextChanged(session)
        if (activity.currentSession == session) {
            syncVisibility()
        }
    }

    fun onOsc133Event(session: TerminalSession, event: Osc133Event) {
        controller.onOsc133Event(session, event)
        if (activity.currentSession == session) {
            syncVisibility()
        }
    }

    fun onOsc7CwdChanged(session: TerminalSession, cwd: String) {
        controller.onOsc7CwdChanged(session, cwd)
    }

    fun onMainBufferReflowed(session: TerminalSession, mapping: TerminalBuffer.ReflowMapping) {
        controller.onMainBufferReflowed(session, mapping)
    }

    fun onSessionFinished(session: TerminalSession, exitCode: Int) {
        controller.onSessionFinished(session, exitCode)
    }

    fun onSessionRemoved(session: TerminalSession?) {
        if (session != null && session.mHandle == splitSecondaryHandleFlow.value) {
            splitSecondaryHandleFlow.value = null
        }
        controller.removeSession(session)
        syncVisibility()
    }

    fun enableSplitWithSession(primaryHandle: String, secondaryHandle: String) {
        if (primaryHandle == secondaryHandle) return
        val primary = controller.getSession(primaryHandle)
        val secondary = controller.getSession(secondaryHandle)
        val priEmu = primary?.emulator
        if (secondary != null && secondary.emulator == null && priEmu != null) {
            val painter = attachedTerminalView?.rowPainter
            val cellW = painter?.mFontWidth?.toInt()?.coerceAtLeast(8) ?: 16
            val cellH = painter?.mFontLineSpacing?.coerceAtLeast(12) ?: 32
            secondary.updateSize(priEmu.mColumns, priEmu.mRows, cellW, cellH)
        }
        splitSecondaryHandleFlow.value = secondaryHandle
        syncVisibility()
    }

    fun exitSplitMode() {
        splitSecondaryHandleFlow.value = null
        syncVisibility()
    }

    fun triggerInBlockSearch() {
        searchTriggerFlow.value++
    }

    fun toggleRawPtyForActiveSession() {
        val session = activity.currentSession ?: return
        controller.toggleRawPtyOverride(session.mHandle)
        syncVisibility()
    }

    fun toggleDensityFromUi() {
        val next = if (densityFlow.value == BlockDensity.COMFORT) {
            BlockDensity.COMPACT
        } else {
            BlockDensity.COMFORT
        }
        NewTermuxSettings.setBlockDensity(activity, next.key)
        densityFlow.value = next
    }

    fun handleHardwareKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isBlockSurfaceActive()) return false
        if (event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return controller.jumpFocus(-1) >= 0
                KeyEvent.KEYCODE_DPAD_DOWN -> return controller.jumpFocus(+1) >= 0
                KeyEvent.KEYCODE_F -> {
                    triggerInBlockSearch()
                    return true
                }
            }
        }
        return false
    }

    fun isBlockSurfaceActive(): Boolean =
        shouldShowBlocks(blockViewEnabledFlow.value, controller.getCurrentState())

    fun syncVisibility() {
        val showBlocks = isBlockSurfaceActive()
        composeContainer?.visibility = if (showBlocks) View.VISIBLE else View.GONE
        attachedTerminalView?.let { tv ->
            tv.alpha = if (showBlocks) 0f else 1f
            tv.importantForAccessibility = if (showBlocks) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
        }
    }

    private fun shouldShowBlocks(blockViewEnabled: Boolean, state: BlockSessionState): Boolean =
        blockViewEnabled && state.sessionHandle.isNotEmpty() && !state.rawPtyOverride && !state.inAltScreen

    private fun handleBlockTargetTap(
        anchorView: View,
        target: TerminalTapResolver.TapTarget,
        offset: Offset,
        cwd: String?
    ) {
        val loc = IntArray(2)
        anchorView.getLocationOnScreen(loc)
        val rawX = loc[0] + offset.x.roundToInt()
        val rawY = loc[1] + offset.y.roundToInt()
        TerminalTapResolver.showChooser(activity, anchorView, target, cwd, rawX, rawY)
    }

    private fun requestSoftKeyboardFocus() {
        val tv = attachedTerminalView ?: return
        if (ViewUtils.isFloatingMultiWindow(activity) && activity.preferences.isSoftKeyboardEnabled) {
            KeyboardUtils.clearDisableSoftKeyboardFlags(activity)
        }
        if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(activity)) {
            if (!tv.hasFocus()) {
                tv.requestFocusFromTouch()
                tv.requestFocus()
            }
            KeyboardUtils.showSoftKeyboard(activity, tv)
        }
    }
}
