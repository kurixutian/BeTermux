package com.newtermux.blocks

import android.os.Handler
import android.os.Looper
import com.termux.terminal.Osc133Event
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Manages per-session command blocks projected over {@link TerminalBuffer} absolute row ranges.
 *
 * Key contracts (Amendment Q3(a) & Q5):
 * - Capped at {@link #MAX_BLOCKS_PER_SESSION} blocks per session; evicting oldest blocks folds their
 *   row range into {@link BlockSessionState#unblockifiedHeader} while the transcript remains intact.
 * - Pre-block output before shell integration emits OSC 133 stays in {@code unblockifiedHeader}.
 * - Output updates are coalesced at ~30 Hz for smooth 60fps Canvas scrolling without frame churn.
 */
class BlockSessionController(
    private val scheduleDelayMs: (Long, Runnable) -> Unit = { delayMs, r ->
        val h = Handler(Looper.getMainLooper())
        if (delayMs <= 0L) r.run() else h.postDelayed(r, delayMs)
    },
    private val cancelCallback: (Runnable) -> Unit = { r ->
        Handler(Looper.getMainLooper()).removeCallbacks(r)
    }
) {

    companion object {
        const val MAX_BLOCKS_PER_SESSION = 200
        private const val UPDATE_COALESCE_MS = 33L
    }

    private class SessionTracker(val handle: String) {
        val blocks = mutableListOf<TerminalBlock>()
        var unblockifiedHeader: UnblockifiedSlice? = null
        var hasSeenOsc133: Boolean = false
        var rawPtyOverride: Boolean = false
        var inAltScreen: Boolean = false
        var cwd: String? = null
        var focusedBlockId: Long? = null
        val selectedBlockIds = linkedSetOf<Long>()
        var pendingPromptStartAbsRow: Long = -1L
        var pendingCommandStartAbsRow: Long = -1L
        var pendingCommandStartCol: Int = 0
        var activeBlockId: Long = -1L
        var cursorAbsRow: Long = 0L
        var cursorCol: Int = 0
        var cursorVisible: Boolean = true
        var cursorStyle: Int = 0
        var revision: Long = 0L

        fun toState(): BlockSessionState = BlockSessionState(
            sessionHandle = handle,
            blocks = blocks.toList(),
            unblockifiedHeader = unblockifiedHeader,
            rawPtyOverride = rawPtyOverride,
            inAltScreen = inAltScreen,
            cwd = cwd,
            focusedBlockId = focusedBlockId,
            selectedBlockIds = selectedBlockIds.toSet(),
            cursorAbsRow = cursorAbsRow,
            cursorCol = cursorCol,
            cursorVisible = cursorVisible,
            cursorStyle = cursorStyle,
            revision = revision
        )
    }

    private val trackers = LinkedHashMap<String, SessionTracker>()
    private val sessionsByHandle = LinkedHashMap<String, TerminalSession>()
    private var activeHandle: String = ""
    private var nextBlockId: Long = 1L

    private val _activeState = MutableStateFlow(BlockSessionState())
    val activeState: StateFlow<BlockSessionState> = _activeState.asStateFlow()

    private val perSessionFlows = LinkedHashMap<String, MutableStateFlow<BlockSessionState>>()
    private var coalescedFlushRunnable: Runnable? = null

    fun setActiveSession(session: TerminalSession?) {
        val handle = session?.mHandle ?: ""
        activeHandle = handle
        if (session != null) {
            sessionsByHandle[handle] = session
            syncCursorAndFlags(getOrCreateTracker(handle), session)
            _activeState.value = emitTracker(getOrCreateTracker(handle))
        } else {
            _activeState.value = BlockSessionState()
        }
    }

    fun stateFlowForSession(handle: String): StateFlow<BlockSessionState> {
        return perSessionFlows.getOrPut(handle) {
            MutableStateFlow(trackers[handle]?.toState() ?: BlockSessionState(sessionHandle = handle))
        }.asStateFlow()
    }

    fun getCurrentState(): BlockSessionState = _activeState.value

    fun getSession(handle: String): TerminalSession? = sessionsByHandle[handle]

    fun removeSession(session: TerminalSession?) {
        val handle = session?.mHandle ?: return
        trackers.remove(handle)
        sessionsByHandle.remove(handle)
        perSessionFlows.remove(handle)
        if (activeHandle == handle) {
            activeHandle = ""
            _activeState.value = BlockSessionState()
        }
    }

    fun onOsc7CwdChanged(session: TerminalSession, cwd: String) {
        val tracker = getOrCreateTracker(session.mHandle)
        sessionsByHandle[session.mHandle] = session
        tracker.cwd = cwd
        publishImmediate(tracker)
    }

    fun onOsc133Event(session: TerminalSession, event: Osc133Event) {
        val emulator = session.emulator ?: return
        val buffer = emulator.mainBuffer ?: return
        val tracker = getOrCreateTracker(session.mHandle)
        sessionsByHandle[session.mHandle] = session
        tracker.hasSeenOsc133 = true

        when (event.type) {
            Osc133Event.TYPE_PROMPT_START -> {
                closeActiveBlockIfOpen(tracker, buffer, event.absRow - 1L, null, event.timestampMs)
                tracker.pendingPromptStartAbsRow = max(buffer.oldestRetainedAbsoluteRow, event.absRow)
                tracker.pendingCommandStartAbsRow = tracker.pendingPromptStartAbsRow
                tracker.pendingCommandStartCol = 0
                ensurePreBlockHeader(tracker, buffer, tracker.pendingPromptStartAbsRow)
            }
            Osc133Event.TYPE_COMMAND_START -> {
                if (tracker.pendingPromptStartAbsRow < 0L) {
                    tracker.pendingPromptStartAbsRow = max(buffer.oldestRetainedAbsoluteRow, event.absRow)
                    ensurePreBlockHeader(tracker, buffer, tracker.pendingPromptStartAbsRow)
                }
                tracker.pendingCommandStartAbsRow = max(buffer.oldestRetainedAbsoluteRow, event.absRow)
                tracker.pendingCommandStartCol = event.cursorCol.coerceAtLeast(0)
            }
            Osc133Event.TYPE_COMMAND_EXECUTED -> {
                closeActiveBlockIfOpen(tracker, buffer, event.absRow - 1L, null, event.timestampMs)
                val promptRow = if (tracker.pendingPromptStartAbsRow >= 0L) {
                    tracker.pendingPromptStartAbsRow
                } else {
                    max(buffer.oldestRetainedAbsoluteRow, event.absRow)
                }
                val cmdStartRow = if (tracker.pendingCommandStartAbsRow >= 0L) {
                    tracker.pendingCommandStartAbsRow
                } else {
                    event.absRow
                }
                ensurePreBlockHeader(tracker, buffer, promptRow)

                val commandText = extractCommandText(
                    buffer = buffer,
                    explicitCmd = event.commandLine,
                    cmdStartAbsRow = cmdStartRow,
                    cmdStartCol = tracker.pendingCommandStartCol,
                    executedAbsRow = event.absRow
                )
                val outputStartRow = event.absRow
                val newId = nextBlockId++
                val block = TerminalBlock(
                    id = newId,
                    command = commandText.ifBlank { "(command)" },
                    promptStartAbsRow = promptRow,
                    commandStartAbsRow = cmdStartRow,
                    outputStartAbsRow = outputStartRow,
                    endAbsRow = outputStartRow - 1L,
                    exitCode = null,
                    timestampMs = event.timestampMs,
                    completedAtMs = null,
                    cwd = tracker.cwd ?: emulator.currentWorkingDirectory
                )
                tracker.blocks.add(block)
                tracker.activeBlockId = newId
                tracker.focusedBlockId = newId
                tracker.pendingPromptStartAbsRow = -1L
                tracker.pendingCommandStartAbsRow = -1L
                enforceBlockCap(tracker)
            }
            Osc133Event.TYPE_COMMAND_FINISHED -> {
                val endRow = resolveFinishedEndRow(buffer, tracker, event.absRow)
                closeActiveBlockIfOpen(tracker, buffer, endRow, event.exitCode, event.timestampMs)
            }
        }

        trimPrunedScrollback(tracker, buffer)
        syncCursorAndFlags(tracker, session)
        publishImmediate(tracker)
    }

    fun onMainBufferReflowed(session: TerminalSession, mapping: TerminalBuffer.ReflowMapping) {
        val tracker = getOrCreateTracker(session.mHandle)
        tracker.unblockifiedHeader?.let { hdr ->
            tracker.unblockifiedHeader = UnblockifiedSlice(
                startAbsRow = mapping.mapStart(hdr.startAbsRow),
                endAbsRow = mapping.mapEnd(hdr.endAbsRow)
            )
        }
        if (tracker.pendingPromptStartAbsRow >= 0L) {
            tracker.pendingPromptStartAbsRow = mapping.mapStart(tracker.pendingPromptStartAbsRow)
        }
        if (tracker.pendingCommandStartAbsRow >= 0L) {
            tracker.pendingCommandStartAbsRow = mapping.mapStart(tracker.pendingCommandStartAbsRow)
        }
        for (i in tracker.blocks.indices) {
            val b = tracker.blocks[i]
            val mappedPrompt = mapping.mapStart(b.promptStartAbsRow)
            val mappedCmd = mapping.mapStart(b.commandStartAbsRow)
            val mappedOutStart = mapping.mapStart(b.outputStartAbsRow)
            val mappedOutEnd = if (b.endAbsRow < b.outputStartAbsRow) {
                mappedOutStart - 1L
            } else {
                max(mappedOutStart, mapping.mapEnd(b.endAbsRow))
            }
            tracker.blocks[i] = b.copy(
                promptStartAbsRow = mappedPrompt,
                commandStartAbsRow = mappedCmd,
                outputStartAbsRow = mappedOutStart,
                endAbsRow = mappedOutEnd
            )
        }
        syncCursorAndFlags(tracker, session)
        publishImmediate(tracker)
    }

    fun onSessionTextChanged(session: TerminalSession) {
        val tracker = getOrCreateTracker(session.mHandle)
        sessionsByHandle[session.mHandle] = session
        syncCursorAndFlags(tracker, session)

        val emulator = session.emulator
        val buffer = emulator?.mainBuffer
        if (emulator != null && buffer != null && !emulator.isAlternateBufferActive) {
            val cursorAbs = emulator.cursorAbsoluteRow
            if (!tracker.hasSeenOsc133 && tracker.blocks.isEmpty()) {
                val oldest = buffer.oldestRetainedAbsoluteRow
                if (cursorAbs >= oldest) {
                    tracker.unblockifiedHeader = UnblockifiedSlice(oldest, cursorAbs)
                }
            } else {
                val activeIdx = tracker.blocks.indexOfFirst { it.id == tracker.activeBlockId }
                if (activeIdx >= 0) {
                    val active = tracker.blocks[activeIdx]
                    if (active.isRunning && cursorAbs >= active.outputStartAbsRow - 1L) {
                        tracker.blocks[activeIdx] = active.copy(endAbsRow = max(active.outputStartAbsRow - 1L, cursorAbs))
                    }
                }
            }
            trimPrunedScrollback(tracker, buffer)
        }

        scheduleCoalescedPublish(tracker)
    }

    fun onSessionFinished(session: TerminalSession, exitCode: Int) {
        val tracker = getOrCreateTracker(session.mHandle)
        val buffer = session.emulator?.mainBuffer
        if (buffer != null) {
            val cursorAbs = session.emulator?.cursorAbsoluteRow ?: (buffer.screenEndAbsoluteRowExclusive - 1L)
            closeActiveBlockIfOpen(tracker, buffer, cursorAbs, exitCode, System.currentTimeMillis())
        }
        publishImmediate(tracker)
    }

    fun toggleRawPtyOverride(handle: String = activeHandle) {
        val tracker = trackers[handle] ?: return
        tracker.rawPtyOverride = !tracker.rawPtyOverride
        if (tracker.rawPtyOverride) {
            tracker.selectedBlockIds.clear()
        }
        publishImmediate(tracker)
    }

    fun toggleBlockSelection(blockId: Long, handle: String = activeHandle) {
        val tracker = trackers[handle] ?: return
        if (!tracker.selectedBlockIds.remove(blockId)) {
            tracker.selectedBlockIds.add(blockId)
        }
        tracker.focusedBlockId = blockId
        publishImmediate(tracker)
    }

    fun clearSelection(handle: String = activeHandle) {
        val tracker = trackers[handle] ?: return
        if (tracker.selectedBlockIds.isNotEmpty()) {
            tracker.selectedBlockIds.clear()
            publishImmediate(tracker)
        }
    }

    fun focusBlock(blockId: Long, handle: String = activeHandle) {
        val tracker = trackers[handle] ?: return
        if (tracker.blocks.any { it.id == blockId }) {
            tracker.focusedBlockId = blockId
            publishImmediate(tracker)
        }
    }

    /**
     * Move keyboard focus across blocks (`delta = -1` for Ctrl+Up, `+1` for Ctrl+Down).
     * Returns newly focused block index in the list, or -1 if no blocks exist.
     */
    fun jumpFocus(delta: Int, handle: String = activeHandle): Int {
        val tracker = trackers[handle] ?: return -1
        if (tracker.blocks.isEmpty()) return -1
        val currentIdx = tracker.focusedBlockId?.let { fid ->
            tracker.blocks.indexOfFirst { it.id == fid }
        } ?: if (delta < 0) tracker.blocks.lastIndex else 0
        val nextIdx = if (currentIdx < 0) {
            if (delta < 0) tracker.blocks.lastIndex else 0
        } else {
            (currentIdx + delta).coerceIn(0, tracker.blocks.lastIndex)
        }
        tracker.focusedBlockId = tracker.blocks[nextIdx].id
        publishImmediate(tracker)
        return nextIdx
    }

    fun jumpToLastFailure(handle: String = activeHandle): Boolean {
        val tracker = trackers[handle] ?: return false
        val failed = tracker.blocks.lastOrNull { it.isFailure } ?: return false
        tracker.focusedBlockId = failed.id
        publishImmediate(tracker)
        return true
    }

    fun buildPlainTextForBlocks(
        session: TerminalSession,
        blockIds: Collection<Long>,
        includeCommands: Boolean = true
    ): String {
        val tracker = trackers[session.mHandle] ?: return ""
        val buffer = session.emulator?.mainBuffer ?: return ""
        val selectedSet = blockIds.toSet()
        val chosen = tracker.blocks.filter { it.id in selectedSet }
        if (chosen.isEmpty()) return ""
        return chosen.joinToString("\n\n") { block ->
            val output = if (block.endAbsRow >= block.outputStartAbsRow) {
                buffer.getSelectedTextAbsolute(block.outputStartAbsRow, block.endAbsRow).trimEnd()
            } else {
                ""
            }
            if (includeCommands) {
                if (output.isEmpty()) "$ ${block.command}" else "$ ${block.command}\n$output"
            } else {
                output
            }
        }
    }

    fun extractBlockOutput(session: TerminalSession, block: TerminalBlock): String {
        val buffer = session.emulator?.mainBuffer ?: return ""
        if (block.endAbsRow < block.outputStartAbsRow) return ""
        return buffer.getSelectedTextAbsolute(block.outputStartAbsRow, block.endAbsRow).trimEnd()
    }

    fun getLastCommandSummary(handle: String): String? {
        val tracker = trackers[handle] ?: return null
        return tracker.blocks.lastOrNull()?.command
    }

    private fun getOrCreateTracker(handle: String): SessionTracker =
        trackers.getOrPut(handle) { SessionTracker(handle) }

    private fun ensurePreBlockHeader(
        tracker: SessionTracker,
        buffer: TerminalBuffer,
        firstBlockRow: Long
    ) {
        if (tracker.blocks.isNotEmpty() || tracker.unblockifiedHeader != null) return
        val oldest = buffer.oldestRetainedAbsoluteRow
        if (firstBlockRow > oldest) {
            tracker.unblockifiedHeader = UnblockifiedSlice(oldest, firstBlockRow - 1L)
        }
    }

    private fun enforceBlockCap(tracker: SessionTracker) {
        while (tracker.blocks.size > MAX_BLOCKS_PER_SESSION) {
            val evicted = tracker.blocks.removeAt(0)
            tracker.selectedBlockIds.remove(evicted.id)
            if (tracker.focusedBlockId == evicted.id) {
                tracker.focusedBlockId = tracker.blocks.firstOrNull()?.id
            }
            val evStart = evicted.promptStartAbsRow
            val evEnd = max(evStart, max(evicted.endAbsRow, evicted.outputStartAbsRow - 1L))
            val prev = tracker.unblockifiedHeader
            tracker.unblockifiedHeader = if (prev == null) {
                UnblockifiedSlice(evStart, evEnd)
            } else {
                UnblockifiedSlice(min(prev.startAbsRow, evStart), max(prev.endAbsRow, evEnd))
            }
        }
    }

    private fun trimPrunedScrollback(tracker: SessionTracker, buffer: TerminalBuffer) {
        val minRetained = buffer.oldestRetainedAbsoluteRow
        tracker.unblockifiedHeader?.let { hdr ->
            if (hdr.endAbsRow < minRetained) {
                tracker.unblockifiedHeader = null
            } else if (hdr.startAbsRow < minRetained) {
                tracker.unblockifiedHeader = hdr.copy(startAbsRow = minRetained)
            }
        }
        val iter = tracker.blocks.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            val effectiveEnd = max(b.endAbsRow, b.promptStartAbsRow)
            if (effectiveEnd < minRetained) {
                tracker.selectedBlockIds.remove(b.id)
                if (tracker.focusedBlockId == b.id) tracker.focusedBlockId = null
                iter.remove()
            }
        }
    }

    private fun closeActiveBlockIfOpen(
        tracker: SessionTracker,
        buffer: TerminalBuffer,
        endAbsRow: Long,
        exitCode: Int?,
        timestampMs: Long
    ) {
        val activeId = tracker.activeBlockId
        if (activeId < 0L) return
        val idx = tracker.blocks.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            val cur = tracker.blocks[idx]
            if (cur.isRunning) {
                val clampedEnd = min(endAbsRow, buffer.screenEndAbsoluteRowExclusive - 1L)
                tracker.blocks[idx] = cur.copy(
                    endAbsRow = max(cur.outputStartAbsRow - 1L, clampedEnd),
                    exitCode = exitCode ?: cur.exitCode,
                    completedAtMs = timestampMs
                )
            }
        }
        tracker.activeBlockId = -1L
    }

    private fun resolveFinishedEndRow(
        buffer: TerminalBuffer,
        tracker: SessionTracker,
        eventAbsRow: Long
    ): Long {
        val active = tracker.blocks.firstOrNull { it.id == tracker.activeBlockId }
        val row = buffer.getRowAtAbsolute(eventAbsRow)
        val candidate = if (row != null && !row.isBlank) eventAbsRow else eventAbsRow - 1L
        return if (active != null) max(active.outputStartAbsRow - 1L, candidate) else candidate
    }

    private fun extractCommandText(
        buffer: TerminalBuffer,
        explicitCmd: String?,
        cmdStartAbsRow: Long,
        cmdStartCol: Int,
        executedAbsRow: Long
    ): String {
        if (!explicitCmd.isNullOrBlank()) return explicitCmd.trim()
        val endRow = if (executedAbsRow > cmdStartAbsRow) executedAbsRow - 1L else cmdStartAbsRow
        val firstRowText = buffer.getSelectedTextAbsolute(cmdStartAbsRow, cmdStartAbsRow)
        val trimmedFirst = if (cmdStartCol > 0 && cmdStartCol < firstRowText.length) {
            firstRowText.substring(cmdStartCol)
        } else {
            firstRowText
        }
        return if (endRow <= cmdStartAbsRow) {
            trimmedFirst.trim()
        } else {
            val tail = buffer.getSelectedTextAbsolute(cmdStartAbsRow + 1L, endRow)
            (trimmedFirst + "\n" + tail).trim()
        }
    }

    private fun syncCursorAndFlags(tracker: SessionTracker, session: TerminalSession) {
        val em = session.emulator ?: return
        tracker.inAltScreen = em.isAlternateBufferActive
        tracker.cursorAbsRow = em.cursorAbsoluteRow
        tracker.cursorCol = em.cursorCol
        tracker.cursorVisible = em.shouldCursorBeVisible()
        tracker.cursorStyle = em.cursorStyle
        em.currentWorkingDirectory?.let { tracker.cwd = it }
    }

    private fun emitTracker(tracker: SessionTracker): BlockSessionState {
        tracker.revision++
        val state = tracker.toState()
        perSessionFlows[tracker.handle]?.value = state
        return state
    }

    private fun publishImmediate(tracker: SessionTracker) {
        coalescedFlushRunnable?.let { cancelCallback(it) }
        coalescedFlushRunnable = null
        val state = emitTracker(tracker)
        if (tracker.handle == activeHandle) {
            _activeState.value = state
        }
    }

    private fun scheduleCoalescedPublish(tracker: SessionTracker) {
        if (coalescedFlushRunnable != null) return
        val runnable = Runnable {
            coalescedFlushRunnable = null
            val current = trackers[tracker.handle] ?: return@Runnable
            val state = emitTracker(current)
            if (current.handle == activeHandle) {
                _activeState.value = state
            }
        }
        coalescedFlushRunnable = runnable
        scheduleDelayMs(UPDATE_COALESCE_MS, runnable)
    }
}
