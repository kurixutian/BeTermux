package com.newtermux.ai

import android.content.Intent
import com.newtermux.blocks.BlockHostBridge
import com.newtermux.input.InputHostBridge
import com.termux.app.TermuxActivity
import com.termux.app.activities.SettingsActivity

/**
 * Connects block-flow AI triggers and inline suggestion actions into [TermuxActivity],
 * keeping AI suggestions inline and inserting accepted commands into [InputHostBridge]
 * without ever auto-executing.
 */
class AiHostBridge(
    private val activity: TermuxActivity,
    private val blockBridgeProvider: () -> BlockHostBridge?,
    private val inputBridgeProvider: () -> InputHostBridge?
) {
    val controller = InlineAiController(activity)

    fun attachHandlers() {
        val blockBridge = blockBridgeProvider() ?: return
        blockBridge.setAiIntegration(
            suggestionsFlow = controller.suggestions,
            onAsk = { block, action, buffer, cwd ->
                controller.requestForBlock(block, action, buffer, cwd)
            },
            onAccept = { blockId, command ->
                inputBridgeProvider()?.insertIntoActiveDraft(command, openEditorAfter = false)
                controller.dismissBlock(blockId)
            },
            onEdit = { blockId, command ->
                inputBridgeProvider()?.insertIntoActiveDraft(command, openEditorAfter = true)
                controller.dismissBlock(blockId)
            },
            onRetry = { blockId -> controller.retryBlock(blockId) },
            onCancel = { controller.cancelActive() },
            onDismiss = { blockId -> controller.dismissBlock(blockId) },
            onOpenSettings = {
                val intent = Intent(activity, SettingsActivity::class.java).apply {
                    putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_AI_ASSISTANT)
                }
                activity.startActivity(intent)
            }
        )
    }

    fun destroy() {
        controller.destroy()
    }
}
