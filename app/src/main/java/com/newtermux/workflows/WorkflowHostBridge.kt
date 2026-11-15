package com.newtermux.workflows

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.newtermux.blocks.BlockHostBridge
import com.newtermux.blocks.TerminalBlock
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.input.InputHostBridge
import com.termux.app.TermuxActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Activity bridge wiring {@link WorkflowRepository}, {@link WorkflowSheetOverlay},
 * Phase 1b block-to-workflow saving, Phase 2 input picker insertion, and Android Share export.
 */
class WorkflowHostBridge(
    private val activity: TermuxActivity,
    private val blockBridgeProvider: () -> BlockHostBridge?,
    private val inputBridgeProvider: () -> InputHostBridge?
) {

    val repository = WorkflowRepository(activity)
    private val sheetModeFlow = MutableStateFlow<WorkflowSheetMode>(WorkflowSheetMode.Hidden)
    private var overlayView: ComposeView? = null

    fun attachToContentRoot(contentRoot: FrameLayout?) {
        blockBridgeProvider()?.setSaveWorkflowHandler { blocks ->
            openCreateFromBlocks(blocks)
        }
        inputBridgeProvider()?.setOpenWorkflowsHandler {
            openPicker()
        }

        if (contentRoot == null) return
        val existing = contentRoot.findViewWithTag<ComposeView>("workflow_sheet_overlay")
        val view = existing ?: ComposeView(activity).apply {
            tag = "workflow_sheet_overlay"
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            contentRoot.addView(this)
        }
        overlayView = view

        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            NewTermuxComposeTheme(activity) {
                val mode by sheetModeFlow.collectAsState()
                val workflows by repository.workflows.collectAsState()

                WorkflowSheetOverlay(
                    mode = mode,
                    workflows = workflows,
                    onDismiss = { close() },
                    onOpenNew = {
                        sheetModeFlow.value = WorkflowSheetMode.Editor(
                            initialWorkflow = Workflow(id = "new_${System.currentTimeMillis()}", name = "", commandTemplate = ""),
                            isNew = true
                        )
                    },
                    onSelectForInsert = { wf ->
                        sheetModeFlow.value = WorkflowSheetMode.ParamFill(wf)
                    },
                    onSaveWorkflow = { wf ->
                        val saved = repository.saveWorkflow(wf)
                        Toast.makeText(activity, "Saved workflow: ${saved.name}", Toast.LENGTH_SHORT).show()
                        sheetModeFlow.value = WorkflowSheetMode.Picker
                    },
                    onDeleteWorkflow = { wf ->
                        repository.deleteWorkflow(wf.id)
                    },
                    onShareWorkflow = { wf ->
                        WorkflowShareHelper.shareWorkflow(activity, wf, repository)
                    },
                    onEditWorkflow = { wf ->
                        sheetModeFlow.value = WorkflowSheetMode.Editor(wf, isNew = false)
                    },
                    onInsertRenderedCommand = { cmd ->
                        inputBridgeProvider()?.insertIntoActiveDraft(cmd, openEditorAfter = false)
                        close()
                    }
                )
            }
        }
    }

    fun openPicker() {
        repository.reload()
        sheetModeFlow.value = WorkflowSheetMode.Picker
        overlayView?.visibility = View.VISIBLE
    }

    fun openCreateFromBlocks(blocks: List<TerminalBlock>) {
        if (blocks.isEmpty()) return
        val joined = blocks.joinToString("\n") { it.command.trim() }.trim()
        val defaultName = blocks.first().command.trim().take(36).ifEmpty { "Saved Block Command" }
        val draft = Workflow(
            id = "new_${System.currentTimeMillis()}",
            name = defaultName,
            description = if (blocks.size > 1) "Combined from ${blocks.size} terminal blocks" else "",
            commandTemplate = joined
        )
        sheetModeFlow.value = WorkflowSheetMode.Editor(draft, isNew = true)
        overlayView?.visibility = View.VISIBLE
    }

    fun close() {
        sheetModeFlow.value = WorkflowSheetMode.Hidden
        overlayView?.visibility = View.GONE
    }
}
