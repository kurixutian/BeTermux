package com.newtermux.blocks

import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.ai.AiAction
import com.newtermux.ai.AiSuggestionBlock
import com.newtermux.ai.AiSuggestionState
import com.newtermux.theme.LocalWarpPalette
import com.newtermux.theme.rememberReduceMotion
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalRowPainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Primary Warp-style Block View container for a session.
 *
 * Features:
 * - Renders pre-block / metadata-evicted rows at the top via {@link BlockRowsCanvas}.
 * - Renders each {@link TerminalBlock} with sticky command headers, tap-to-jump,
 *   3dp status left strip, Comfort/Compact density spacing, long-press multi-select outline,
 *   and hardware Ctrl+Up / Ctrl+Down navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlockWorkspaceHost(
    controller: BlockSessionController,
    sessionProvider: (String) -> TerminalSession?,
    painterProvider: () -> TerminalRowPainter?,
    density: BlockDensity,
    onTapTarget: (TerminalTapResolver.TapTarget, Offset, String?) -> Unit,
    onRequestTerminalInputFocus: () -> Unit,
    onToggleDensity: () -> Unit,
    onToggleRawPty: () -> Unit,
    onSaveWorkflow: ((List<TerminalBlock>) -> Unit)? = null,
    aiSuggestionsFlow: StateFlow<Map<Long, AiSuggestionState>>? = null,
    onAskAiAction: ((TerminalBlock, AiAction, TerminalBuffer?, String?) -> Unit)? = null,
    onAiAcceptCommand: ((Long, String) -> Unit)? = null,
    onAiEditCommand: ((Long, String) -> Unit)? = null,
    onAiRetry: ((Long) -> Unit)? = null,
    onAiCancel: (() -> Unit)? = null,
    onAiDismiss: ((Long) -> Unit)? = null,
    onOpenAiSettings: (() -> Unit)? = null,
    onAiAnnouncement: ((String) -> Unit)? = null,
    stateFlowOverride: StateFlow<BlockSessionState>? = null,
    externalSearchTrigger: Int = 0,
    onPaneFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWarpPalette.current
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val state by (stateFlowOverride ?: controller.activeState).collectAsState()
    val emptyAiFlow = remember { MutableStateFlow<Map<Long, AiSuggestionState>>(emptyMap()) }
    val aiSuggestions by (aiSuggestionsFlow ?: emptyAiFlow).collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val backgroundInteraction = remember { MutableInteractionSource() }

    var selectionSheetState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchStates by remember(state.sessionHandle) {
        mutableStateOf<Map<Long, BlockSearchState>>(emptyMap())
    }

    fun openSearchForBlock(blockId: Long) {
        if (!searchStates.containsKey(blockId)) {
            searchStates = searchStates + (blockId to BlockSearchState())
        }
    }

    LaunchedEffect(externalSearchTrigger) {
        if (externalSearchTrigger > 0) {
            val targetId = state.focusedBlockId ?: state.blocks.lastOrNull()?.id
            if (targetId != null) {
                openSearchForBlock(targetId)
            }
        }
    }

    val session = remember(state.sessionHandle, state.revision) {
        sessionProvider(state.sessionHandle)
    }
    val emulator = session?.emulator
    val mainBuffer = emulator?.mainBuffer
    val painter = remember(state.sessionHandle, state.revision) { painterProvider() }

    val searchResults = remember(state.blocks, state.revision, searchStates, mainBuffer) {
        if (mainBuffer == null || searchStates.isEmpty()) {
            emptyMap()
        } else {
            val map = LinkedHashMap<Long, BlockSearchResult>(searchStates.size)
            for (block in state.blocks) {
                val st = searchStates[block.id] ?: continue
                map[block.id] = BlockSearchMatcher.searchBlock(
                    buffer = mainBuffer,
                    outputStartAbsRow = block.outputStartAbsRow,
                    endAbsRow = block.endAbsRow,
                    query = st.query
                )
            }
            map
        }
    }

    // Map block IDs to LazyColumn item indices for sticky tap-to-jump and Ctrl+Up/Down navigation.
    val headerChunkCount = remember(state.unblockifiedHeader) {
        val hdr = state.unblockifiedHeader ?: return@remember 0
        if (hdr.rowCount <= 0) 0 else ((hdr.rowCount + BLOCK_CANVAS_CHUNK_ROWS - 1) / BLOCK_CANVAS_CHUNK_ROWS)
    }
    val blockHeaderItemIndexMap = remember(state.blocks, headerChunkCount, searchStates, searchResults, aiSuggestions) {
        val map = LinkedHashMap<Long, Int>(state.blocks.size)
        var cursorIdx = headerChunkCount
        for (block in state.blocks) {
            map[block.id] = cursorIdx
            cursorIdx += 1 // stickyHeader
            val sState = searchStates[block.id]
            val sRes = searchResults[block.id]
            if (sState != null) {
                cursorIdx += 1 // BlockSearchBar
            }
            val filteringActive = sState != null && sState.filterOnly && sState.query.isNotBlank()
            val outChunks = if (filteringActive && sRes != null) {
                val n = sRes.matchingAbsRows.size
                if (n <= 0) 1 else ((n + BLOCK_CANVAS_CHUNK_ROWS - 1) / BLOCK_CANVAS_CHUNK_ROWS)
            } else {
                val outRows = block.outputRowCount
                if (outRows <= 0) 0 else ((outRows + BLOCK_CANVAS_CHUNK_ROWS - 1) / BLOCK_CANVAS_CHUNK_ROWS)
            }
            cursorIdx += outChunks
            if (aiSuggestions.containsKey(block.id)) {
                cursorIdx += 1 // inline AiSuggestionBlock
            }
            cursorIdx += 1 // footer/divider slot
        }
        map
    }

    // Keep viewport scrolled to the latest active block while typing/streaming unless user scrolled away.
    LaunchedEffect(state.blocks.size, state.revision) {
        val lastItem = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        val visible = listState.layoutInfo.visibleItemsInfo
        val nearBottom = visible.isEmpty() || (visible.lastOrNull()?.index ?: 0) >= (lastItem - 3)
        if (nearBottom && lastItem > 0) {
            listState.scrollToItem(lastItem)
        }
    }

    fun jumpToBlock(blockId: Long) {
        val idx = blockHeaderItemIndexMap[blockId] ?: return
        scope.launch {
            if (reduceMotion) {
                listState.scrollToItem(idx)
            } else {
                listState.animateScrollToItem(idx)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.isCtrlPressed &&
                    !native.isAltPressed &&
                    !native.isShiftPressed
                ) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            val idx = controller.jumpFocus(-1, state.sessionHandle)
                            if (idx >= 0) {
                                state.blocks.getOrNull(idx)?.let { jumpToBlock(it.id) }
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val idx = controller.jumpFocus(+1, state.sessionHandle)
                            if (idx >= 0) {
                                state.blocks.getOrNull(idx)?.let { jumpToBlock(it.id) }
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_F -> {
                            val targetId = state.focusedBlockId ?: state.blocks.lastOrNull()?.id
                            if (targetId != null) {
                                openSearchForBlock(targetId)
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                false
            }
            .clickable(
                interactionSource = backgroundInteraction,
                indication = null,
                onClickLabel = "Focus terminal input"
            ) {
                onPaneFocused?.invoke()
                if (state.selectionMode) {
                    controller.clearSelection(state.sessionHandle)
                } else {
                    onRequestTerminalInputFocus()
                }
            }
    ) {
        if (mainBuffer != null && painter != null) {
            val paletteInts = emulator.mColors.mCurrentColors
            val columns = emulator.mColumns
            val screenRows = emulator.mRows
            val reverseVideo = emulator.isReverseVideo

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 6.dp,
                    bottom = if (state.selectionMode) 76.dp else 16.dp
                )
            ) {
                val hdr = state.unblockifiedHeader
                if (hdr != null && hdr.rowCount > 0) {
                    items(
                        count = headerChunkCount,
                        key = { chunkIdx -> "unblock_${hdr.startAbsRow}_$chunkIdx" }
                    ) { chunkIdx ->
                        val cStart = hdr.startAbsRow + chunkIdx.toLong() * BLOCK_CANVAS_CHUNK_ROWS
                        val cEnd = minOf(hdr.endAbsRow, cStart + BLOCK_CANVAS_CHUNK_ROWS - 1L)
                        BlockRowsCanvas(
                            buffer = mainBuffer,
                            painter = painter,
                            palette = paletteInts,
                            columns = columns,
                            screenRows = screenRows,
                            reverseVideo = reverseVideo,
                            startAbsRow = cStart,
                            endAbsRow = cEnd,
                            cursorAbsRow = state.cursorAbsRow,
                            cursorCol = state.cursorCol,
                            cursorVisible = state.cursorVisible,
                            cursorStyle = state.cursorStyle,
                            revision = state.revision,
                            sessionCwd = state.cwd,
                            onTargetTapped = { target, offset ->
                                onTapTarget(target, offset, state.cwd)
                            },
                            onTapBody = onRequestTerminalInputFocus,
                            onLongPressBody = {},
                            modifier = Modifier.padding(horizontal = density.horizontalPaddingDp.dp)
                        )
                    }
                }

                for (block in state.blocks) {
                    val isSelected = block.id in state.selectedBlockIds
                    val isFocused = block.id == state.focusedBlockId
                    val stripColor = when {
                        block.isRunning -> palette.defaultAccent
                        block.isFailure -> palette.error
                        else -> palette.borderSubtle
                    }

                    stickyHeader(key = "blk_hdr_${block.id}") {
                        BlockCommandHeader(
                            block = block,
                            density = density,
                            stripColor = stripColor,
                            isSelected = isSelected,
                            isFocused = isFocused,
                            selectionMode = state.selectionMode,
                            onHeaderTap = {
                                onPaneFocused?.invoke()
                                if (state.selectionMode) {
                                    controller.toggleBlockSelection(block.id, state.sessionHandle)
                                } else {
                                    controller.focusBlock(block.id, state.sessionHandle)
                                    jumpToBlock(block.id)
                                    if (block.isRunning) {
                                        onRequestTerminalInputFocus()
                                    }
                                }
                            },
                            onHeaderLongPress = {
                                onPaneFocused?.invoke()
                                controller.toggleBlockSelection(block.id, state.sessionHandle)
                            },
                            onCopy = {
                                val sess = sessionProvider(state.sessionHandle) ?: return@BlockCommandHeader
                                val txt = controller.buildPlainTextForBlocks(sess, listOf(block.id), includeCommands = true)
                                BlockActions.copyText(context, txt, "Block copied")
                            },
                            onShare = {
                                val sess = sessionProvider(state.sessionHandle) ?: return@BlockCommandHeader
                                val txt = controller.buildPlainTextForBlocks(sess, listOf(block.id), includeCommands = true)
                                BlockActions.shareText(context, txt, block.command)
                            },
                            onSelectText = {
                                val sess = sessionProvider(state.sessionHandle) ?: return@BlockCommandHeader
                                val txt = controller.buildPlainTextForBlocks(sess, listOf(block.id), includeCommands = true)
                                selectionSheetState = block.command to txt
                            },
                            onSearchBlock = { openSearchForBlock(block.id) },
                            onToggleDensity = onToggleDensity,
                            onToggleRawPty = onToggleRawPty,
                            onSaveWorkflow = onSaveWorkflow?.let { cb -> { cb(listOf(block)) } },
                            onExplainError = onAskAiAction?.let { cb ->
                                { cb(block, AiAction.EXPLAIN_ERROR, mainBuffer, block.cwd ?: state.cwd) }
                            },
                            onProposeFix = onAskAiAction?.let { cb ->
                                { cb(block, AiAction.PROPOSE_FIX, mainBuffer, block.cwd ?: state.cwd) }
                            }
                        )
                    }

                    val blockSearch = searchStates[block.id]
                    val blockSearchResult = searchResults[block.id]
                    if (blockSearch != null) {
                        item(key = "blk_src_${block.id}") {
                            BlockSearchBar(
                                stripColor = stripColor,
                                horizontalPaddingDp = density.horizontalPaddingDp,
                                searchState = blockSearch,
                                result = blockSearchResult,
                                onQueryChange = { nextQ ->
                                    searchStates = searchStates + (block.id to blockSearch.copy(query = nextQ))
                                },
                                onToggleFilter = {
                                    searchStates = searchStates + (block.id to blockSearch.copy(filterOnly = !blockSearch.filterOnly))
                                },
                                onClose = {
                                    searchStates = searchStates - block.id
                                }
                            )
                        }
                    }

                    val filteringActive = blockSearch != null && blockSearch.filterOnly && blockSearch.query.isNotBlank()
                    val spanMap = blockSearchResult?.columnSpansByAbsRow ?: emptyMap()
                    if (filteringActive && blockSearchResult != null) {
                        val matchedRows = blockSearchResult.matchingAbsRows
                        if (matchedRows.isEmpty()) {
                            item(key = "blk_out_empty_${block.id}") {
                                BlockOutputFrame(
                                    stripColor = stripColor,
                                    isSelected = isSelected,
                                    isFocused = isFocused,
                                    horizontalPaddingDp = density.horizontalPaddingDp
                                ) {
                                    Text(
                                        text = "No lines matching \"${blockSearch.query}\"",
                                        color = palette.textMuted,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            val matchChunkCount = ((matchedRows.size + BLOCK_CANVAS_CHUNK_ROWS - 1) / BLOCK_CANVAS_CHUNK_ROWS)
                            items(
                                count = matchChunkCount,
                                key = { chunkIdx -> "blk_out_${block.id}_f_$chunkIdx" }
                            ) { chunkIdx ->
                                val from = chunkIdx * BLOCK_CANVAS_CHUNK_ROWS
                                val to = minOf(matchedRows.size, from + BLOCK_CANVAS_CHUNK_ROWS)
                                val slice = matchedRows.subList(from, to)
                                BlockOutputFrame(
                                    stripColor = stripColor,
                                    isSelected = isSelected,
                                    isFocused = isFocused,
                                    horizontalPaddingDp = density.horizontalPaddingDp
                                ) {
                                    BlockRowsCanvas(
                                        buffer = mainBuffer,
                                        painter = painter,
                                        palette = paletteInts,
                                        columns = columns,
                                        screenRows = screenRows,
                                        reverseVideo = reverseVideo,
                                        startAbsRow = slice.first(),
                                        endAbsRow = slice.last(),
                                        cursorAbsRow = state.cursorAbsRow,
                                        cursorCol = state.cursorCol,
                                        cursorVisible = state.cursorVisible,
                                        cursorStyle = state.cursorStyle,
                                        revision = state.revision,
                                        sessionCwd = block.cwd ?: state.cwd,
                                        explicitAbsRows = slice,
                                        matchSpansByAbsRow = spanMap,
                                        onTargetTapped = { target, offset ->
                                            onTapTarget(target, offset, block.cwd ?: state.cwd)
                                        },
                                        onTapBody = {
                                            onPaneFocused?.invoke()
                                            if (state.selectionMode) {
                                                controller.toggleBlockSelection(block.id, state.sessionHandle)
                                            } else {
                                                controller.focusBlock(block.id, state.sessionHandle)
                                                onRequestTerminalInputFocus()
                                            }
                                        },
                                        onLongPressBody = {
                                            onPaneFocused?.invoke()
                                            controller.toggleBlockSelection(block.id, state.sessionHandle)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        val outRows = block.outputRowCount
                        val chunkCount = if (outRows <= 0) 0 else ((outRows + BLOCK_CANVAS_CHUNK_ROWS - 1) / BLOCK_CANVAS_CHUNK_ROWS)
                        items(
                            count = chunkCount,
                            key = { chunkIdx -> "blk_out_${block.id}_$chunkIdx" }
                        ) { chunkIdx ->
                            val cStart = block.outputStartAbsRow + chunkIdx.toLong() * BLOCK_CANVAS_CHUNK_ROWS
                            val cEnd = minOf(block.endAbsRow, cStart + BLOCK_CANVAS_CHUNK_ROWS - 1L)
                            BlockOutputFrame(
                                stripColor = stripColor,
                                isSelected = isSelected,
                                isFocused = isFocused,
                                horizontalPaddingDp = density.horizontalPaddingDp
                            ) {
                                BlockRowsCanvas(
                                    buffer = mainBuffer,
                                    painter = painter,
                                    palette = paletteInts,
                                    columns = columns,
                                    screenRows = screenRows,
                                    reverseVideo = reverseVideo,
                                    startAbsRow = cStart,
                                    endAbsRow = cEnd,
                                    cursorAbsRow = state.cursorAbsRow,
                                    cursorCol = state.cursorCol,
                                    cursorVisible = state.cursorVisible,
                                    cursorStyle = state.cursorStyle,
                                    revision = state.revision,
                                    sessionCwd = block.cwd ?: state.cwd,
                                    matchSpansByAbsRow = spanMap,
                                    onTargetTapped = { target, offset ->
                                        onTapTarget(target, offset, block.cwd ?: state.cwd)
                                    },
                                    onTapBody = {
                                        onPaneFocused?.invoke()
                                        if (state.selectionMode) {
                                            controller.toggleBlockSelection(block.id, state.sessionHandle)
                                        } else {
                                            controller.focusBlock(block.id, state.sessionHandle)
                                            onRequestTerminalInputFocus()
                                        }
                                    },
                                    onLongPressBody = {
                                        onPaneFocused?.invoke()
                                        controller.toggleBlockSelection(block.id, state.sessionHandle)
                                    }
                                )
                            }
                        }
                    }

                    val aiState = aiSuggestions[block.id]
                    if (aiState != null) {
                        item(key = "blk_ai_${block.id}") {
                            AiSuggestionBlock(
                                state = aiState,
                                horizontalPaddingDp = density.horizontalPaddingDp,
                                onAcceptCommand = { cmd -> onAiAcceptCommand?.invoke(block.id, cmd) },
                                onEditCommand = { cmd -> onAiEditCommand?.invoke(block.id, cmd) },
                                onRetry = { onAiRetry?.invoke(block.id) },
                                onCancel = { onAiCancel?.invoke() },
                                onDismiss = { onAiDismiss?.invoke(block.id) },
                                onOpenSettings = { onOpenAiSettings?.invoke() },
                                onAccessibilityAnnouncement = onAiAnnouncement
                            )
                        }
                    }

                    item(key = "blk_ftr_${block.id}") {
                        BlockFooterDivider(
                            stripColor = stripColor,
                            isSelected = isSelected,
                            verticalPaddingDp = density.verticalPaddingDp,
                            dividerThicknessDp = density.dividerThicknessDp
                        )
                    }
                }
            }
        }

        // Floating multi-select action bar (Outline-only selection mode)
        AnimatedVisibility(
            visible = state.selectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BlockSelectionBar(
                selectedCount = state.selectedBlockIds.size,
                hasSingleSelection = state.selectedBlockIds.size == 1,
                onCopy = {
                    val sess = sessionProvider(state.sessionHandle) ?: return@BlockSelectionBar
                    val txt = controller.buildPlainTextForBlocks(sess, state.selectedBlockIds)
                    BlockActions.copyText(context, txt, "${state.selectedBlockIds.size} block(s) copied")
                    controller.clearSelection(state.sessionHandle)
                },
                onShare = {
                    val sess = sessionProvider(state.sessionHandle) ?: return@BlockSelectionBar
                    val txt = controller.buildPlainTextForBlocks(sess, state.selectedBlockIds)
                    BlockActions.shareText(context, txt)
                },
                onSelectText = {
                    val sess = sessionProvider(state.sessionHandle) ?: return@BlockSelectionBar
                    val txt = controller.buildPlainTextForBlocks(sess, state.selectedBlockIds)
                    selectionSheetState = "Selected Blocks" to txt
                },
                onSaveWorkflow = onSaveWorkflow?.let { cb ->
                    {
                        val chosen = state.blocks.filter { it.id in state.selectedBlockIds }
                        if (chosen.isNotEmpty()) cb(chosen)
                        controller.clearSelection(state.sessionHandle)
                    }
                },
                onExplainError = onAskAiAction?.let { cb ->
                    {
                        val chosen = state.blocks.firstOrNull { it.id in state.selectedBlockIds }
                        if (chosen != null) {
                            cb(chosen, AiAction.EXPLAIN_ERROR, mainBuffer, chosen.cwd ?: state.cwd)
                            controller.clearSelection(state.sessionHandle)
                        }
                    }
                },
                onProposeFix = onAskAiAction?.let { cb ->
                    {
                        val chosen = state.blocks.firstOrNull { it.id in state.selectedBlockIds }
                        if (chosen != null) {
                            cb(chosen, AiAction.PROPOSE_FIX, mainBuffer, chosen.cwd ?: state.cwd)
                            controller.clearSelection(state.sessionHandle)
                        }
                    }
                },
                onClear = { controller.clearSelection(state.sessionHandle) }
            )
        }
    }

    selectionSheetState?.let { (sheetTitle, sheetText) ->
        BlockTextSelectionSheet(
            title = sheetTitle,
            text = sheetText,
            onCopyAll = {
                BlockActions.copyText(context, sheetText, "Copied to clipboard")
                selectionSheetState = null
            },
            onShareAll = {
                BlockActions.shareText(context, sheetText, sheetTitle)
            },
            onDismiss = { selectionSheetState = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockCommandHeader(
    block: TerminalBlock,
    density: BlockDensity,
    stripColor: Color,
    isSelected: Boolean,
    isFocused: Boolean,
    selectionMode: Boolean,
    onHeaderTap: () -> Unit,
    onHeaderLongPress: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSelectText: () -> Unit,
    onSearchBlock: () -> Unit,
    onToggleDensity: () -> Unit,
    onToggleRawPty: () -> Unit,
    onSaveWorkflow: (() -> Unit)?,
    onExplainError: (() -> Unit)?,
    onProposeFix: (() -> Unit)?
) {
    val palette = LocalWarpPalette.current
    var menuExpanded by remember { mutableStateOf(false) }

    val cwdLabel = remember(block.cwd) {
        val raw = block.cwd ?: return@remember null
        if (raw == "/") "/" else File(raw).name.ifEmpty { raw }
    }
    val durationLabel = remember(block.durationMs) {
        block.durationMs?.let { formatDurationCompact(it) }
    }
    val outlineModifier = when {
        isSelected -> Modifier.border(1.5.dp, palette.defaultAccent, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
        isFocused -> Modifier.border(1.dp, palette.borderSubtle, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
        else -> Modifier
    }

    Surface(
        color = palette.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .then(outlineModifier)
            .combinedClickable(
                onClick = onHeaderTap,
                onLongClick = onHeaderLongPress,
                onClickLabel = "Jump to block: ${block.command}"
            )
            .semantics {
                contentDescription = buildString {
                    append("Command block: ${block.command}")
                    block.exitCode?.let { append(", exit code $it") }
                    if (block.isRunning) append(", running")
                    if (isSelected) append(", selected")
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(stripColor)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = density.horizontalPaddingDp.dp,
                        vertical = (density.verticalPaddingDp * 0.7f).dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    color = if (block.isFailure) palette.error else palette.defaultAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = block.command,
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!cwdLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = cwdLabel,
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (durationLabel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = durationLabel,
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                }
                if (block.exitCode != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    val code = block.exitCode
                    Text(
                        text = if (code == 0) "0" else "err $code",
                        color = if (code == 0) palette.textMuted else palette.error,
                        fontSize = 11.sp,
                        fontWeight = if (code != 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                } else if (block.isRunning) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "run",
                        color = palette.defaultAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!selectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "Block actions for ${block.command}" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = palette.surfaceElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("Search in block", color = palette.textPrimary) },
                            onClick = { menuExpanded = false; onSearchBlock() }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy block", color = palette.textPrimary) },
                            onClick = { menuExpanded = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text("Select text", color = palette.textPrimary) },
                            onClick = { menuExpanded = false; onSelectText() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share block", color = palette.textPrimary) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        if (onSaveWorkflow != null) {
                            DropdownMenuItem(
                                text = { Text("Save as workflow", color = palette.textPrimary) },
                                onClick = { menuExpanded = false; onSaveWorkflow() }
                            )
                        }
                        if (onExplainError != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (block.isFailure) "Explain error" else "Explain block",
                                        color = palette.defaultAccent
                                    )
                                },
                                onClick = { menuExpanded = false; onExplainError() }
                            )
                        }
                        if (onProposeFix != null) {
                            DropdownMenuItem(
                                text = { Text("Propose fix", color = palette.defaultAccent) },
                                onClick = { menuExpanded = false; onProposeFix() }
                            )
                        }
                        HorizontalDivider(color = palette.borderSubtle)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Density: ${if (density == BlockDensity.COMFORT) "Switch to Compact" else "Switch to Comfort"}",
                                    color = palette.textSecondary
                                )
                            },
                            onClick = { menuExpanded = false; onToggleDensity() }
                        )
                        DropdownMenuItem(
                            text = { Text("Switch to Raw PTY", color = palette.textSecondary) },
                            onClick = { menuExpanded = false; onToggleRawPty() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockSearchBar(
    stripColor: Color,
    horizontalPaddingDp: Int,
    searchState: BlockSearchState,
    result: BlockSearchResult?,
    onQueryChange: (String) -> Unit,
    onToggleFilter: () -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = palette.defaultAccent
    val countLabel = if (searchState.query.isBlank() || result == null) {
        ""
    } else {
        "${result.matchingAbsRows.size}/${result.totalOutputRows}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .background(stripColor)
            .padding(start = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surfaceElevated)
                .border(1.dp, accent.copy(alpha = 0.5f))
                .padding(horizontal = horizontalPaddingDp.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.size(15.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (searchState.query.isEmpty()) {
                    Text(
                        text = "Filter output…",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
                BasicTextField(
                    value = searchState.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp
                    ),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("block_search_input")
                        .semantics { contentDescription = "Search within block output" }
                )
            }
            if (countLabel.isNotEmpty()) {
                Text(
                    text = countLabel,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.testTag("block_search_count")
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (searchState.filterOnly) accent.copy(alpha = 0.2f) else palette.surfaceInteractive,
                border = BorderStroke(1.dp, if (searchState.filterOnly) accent else palette.borderSubtle),
                modifier = Modifier
                    .clickable(onClick = onToggleFilter)
                    .testTag("block_search_filter_toggle")
                    .semantics {
                        contentDescription = if (searchState.filterOnly) "Filtering matching lines" else "Highlighting matches in full output"
                    }
            ) {
                Text(
                    text = if (searchState.filterOnly) "Filter" else "All",
                    color = if (searchState.filterOnly) accent else palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("block_search_close")
                    .semantics { contentDescription = "Close block search" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockOutputFrame(
    stripColor: Color,
    isSelected: Boolean,
    isFocused: Boolean,
    horizontalPaddingDp: Int,
    content: @Composable () -> Unit
) {
    val palette = LocalWarpPalette.current
    val outlineModifier = when {
        isSelected -> Modifier.border(1.5.dp, palette.defaultAccent)
        isFocused -> Modifier.border(1.dp, palette.borderSubtle)
        else -> Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(IntrinsicSize.Min)
            .then(outlineModifier)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(stripColor)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = horizontalPaddingDp.dp, vertical = 2.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun BlockFooterDivider(
    stripColor: Color,
    isSelected: Boolean,
    verticalPaddingDp: Int,
    dividerThicknessDp: Int
) {
    val palette = LocalWarpPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(verticalPaddingDp.dp)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(stripColor)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(BorderStroke(0.dp, Color.Transparent))
                )
            }
        }
        HorizontalDivider(
            thickness = dividerThicknessDp.dp,
            color = palette.borderSubtle
        )
    }
}

@Composable
private fun BlockSelectionBar(
    selectedCount: Int,
    hasSingleSelection: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSelectText: () -> Unit,
    onSaveWorkflow: (() -> Unit)?,
    onExplainError: (() -> Unit)?,
    onProposeFix: (() -> Unit)?,
    onClear: () -> Unit
) {
    val palette = LocalWarpPalette.current
    Surface(
        color = palette.surfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.defaultAccent.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$selectedCount selected",
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.semantics { contentDescription = "Copy selected blocks" }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.semantics { contentDescription = "Share selected blocks" }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                    }
                    if (hasSingleSelection) {
                        IconButton(
                            onClick = onSelectText,
                            modifier = Modifier.semantics { contentDescription = "Select text within block" }
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (onSaveWorkflow != null) {
                        IconButton(
                            onClick = onSaveWorkflow,
                            modifier = Modifier.semantics { contentDescription = "Save selected blocks as workflow" }
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = palette.defaultAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.semantics { contentDescription = "Clear block selection" }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (hasSingleSelection && (onExplainError != null || onProposeFix != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onExplainError != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.surfaceInteractive,
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier
                                .clickable(onClick = onExplainError)
                                .testTag("selection_ai_explain")
                                .semantics { contentDescription = "Explain error with AI" }
                        ) {
                            Text(
                                text = "Explain error",
                                color = palette.defaultAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (onProposeFix != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.surfaceInteractive,
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier
                                .clickable(onClick = onProposeFix)
                                .testTag("selection_ai_propose_fix")
                                .semantics { contentDescription = "Propose fix with AI" }
                        ) {
                            Text(
                                text = "Propose fix",
                                color = palette.defaultAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationCompact(durationMs: Long): String = when {
    durationMs < 1000L -> "${durationMs}ms"
    durationMs < 60_000L -> String.format(Locale.US, "%.1fs", durationMs / 1000.0)
    else -> {
        val secs = durationMs / 1000L
        "${secs / 60L}m${secs % 60L}s"
    }
}
