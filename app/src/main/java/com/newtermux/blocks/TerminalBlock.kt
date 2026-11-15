package com.newtermux.blocks

import androidx.compose.runtime.Immutable

/**
 * Spacing density preset for command blocks in Block View.
 */
enum class BlockDensity(
    val key: String,
    val label: String,
    val verticalPaddingDp: Int,
    val horizontalPaddingDp: Int,
    val dividerThicknessDp: Int
) {
    COMFORT("comfort", "Comfort", verticalPaddingDp = 10, horizontalPaddingDp = 10, dividerThicknessDp = 1),
    COMPACT("compact", "Compact", verticalPaddingDp = 4, horizontalPaddingDp = 8, dividerThicknessDp = 1);

    companion object {
        @JvmStatic
        fun fromKey(key: String?): BlockDensity =
            if (key == COMPACT.key) COMPACT else COMFORT
    }
}

/**
 * Addressable command block metadata projecting an immutable range over {@code TerminalBuffer}
 * absolute row coordinates without duplicating transcript memory per block.
 */
@Immutable
data class TerminalBlock(
    val id: Long,
    val command: String,
    val promptStartAbsRow: Long,
    val commandStartAbsRow: Long,
    val outputStartAbsRow: Long,
    val endAbsRow: Long,
    val exitCode: Int? = null,
    val timestampMs: Long = 0L,
    val completedAtMs: Long? = null,
    val cwd: String? = null,
    val isSyntheticFallback: Boolean = false
) {
    val isRunning: Boolean
        get() = completedAtMs == null

    val durationMs: Long?
        get() = completedAtMs?.let { (it - timestampMs).coerceAtLeast(0L) }

    val outputRowCount: Int
        get() = ((endAbsRow - outputStartAbsRow) + 1L).coerceAtLeast(0L).toInt()

    val isFailure: Boolean
        get() = exitCode != null && exitCode != 0
}

/**
 * Unblockified or evicted transcript range retained in {@code TerminalBuffer.mMainBuffer}
 * and rendered continuously at the top of Block View (Amendment Q3(a) & Q5).
 */
@Immutable
data class UnblockifiedSlice(
    val startAbsRow: Long,
    val endAbsRow: Long
) {
    val rowCount: Int
        get() = ((endAbsRow - startAbsRow) + 1L).coerceAtLeast(0L).toInt()
}

/**
 * Observable per-session Block View snapshot rendered by {@link BlockWorkspaceHost}.
 */
@Immutable
data class BlockSessionState(
    val sessionHandle: String = "",
    val blocks: List<TerminalBlock> = emptyList(),
    val unblockifiedHeader: UnblockifiedSlice? = null,
    val rawPtyOverride: Boolean = false,
    val inAltScreen: Boolean = false,
    val cwd: String? = null,
    val focusedBlockId: Long? = null,
    val selectedBlockIds: Set<Long> = emptySet(),
    val cursorAbsRow: Long = -1L,
    val cursorCol: Int = 0,
    val cursorVisible: Boolean = true,
    val cursorStyle: Int = 0,
    val revision: Long = 0L
) {
    val selectionMode: Boolean
        get() = selectedBlockIds.isNotEmpty()
}
