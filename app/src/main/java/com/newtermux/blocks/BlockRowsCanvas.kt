package com.newtermux.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.termux.terminal.TerminalBuffer
import com.termux.view.TerminalRowPainter
import kotlinx.coroutines.withTimeoutOrNull

const val BLOCK_CANVAS_CHUNK_ROWS = 50

/**
 * Renders either a contiguous slice {@code [startAbsRow..endAbsRow]} or an explicit list of
 * filtered {@code explicitAbsRows} directly from {@link TerminalBuffer} via {@link TerminalRowPainter},
 * ensuring pixel-identical glyphs, colors, underlines, wide characters, active cursor, and
 * search highlights with zero per-frame Paint/String allocations.
 */
@Composable
fun BlockRowsCanvas(
    buffer: TerminalBuffer,
    painter: TerminalRowPainter,
    palette: IntArray,
    columns: Int,
    screenRows: Int,
    reverseVideo: Boolean,
    startAbsRow: Long,
    endAbsRow: Long,
    cursorAbsRow: Long,
    cursorCol: Int,
    cursorVisible: Boolean,
    cursorStyle: Int,
    revision: Long,
    sessionCwd: String?,
    explicitAbsRows: List<Long>? = null,
    matchSpansByAbsRow: Map<Long, List<IntRange>> = emptyMap(),
    onTargetTapped: (TerminalTapResolver.TapTarget, Offset) -> Unit,
    onTapBody: () -> Unit,
    onLongPressBody: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowCount = explicitAbsRows?.size
        ?: ((endAbsRow - startAbsRow) + 1L).coerceIn(0L, BLOCK_CANVAS_CHUNK_ROWS.toLong()).toInt()
    if (rowCount <= 0) return

    val density = LocalDensity.current
    val heightDp = with(density) { (rowCount * painter.mFontLineSpacing).toDp() }
    val accentArgb = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f).toArgb()
    val highlightPaint = remember(accentArgb) {
        android.graphics.Paint().apply {
            color = accentArgb
            style = android.graphics.Paint.Style.FILL
        }
    }
    val isLiveChunk = cursorAbsRow in startAbsRow..endAbsRow
    val accessibleRevision = if (isLiveChunk) revision else 0L
    val accessibleText = remember(accessibleRevision, startAbsRow, endAbsRow, explicitAbsRows) {
        val raw = if (explicitAbsRows != null) {
            explicitAbsRows.joinToString("\n") { buffer.getSelectedTextAbsolute(it, it) }.trim()
        } else {
            buffer.getSelectedTextAbsolute(startAbsRow, endAbsRow).trim()
        }
        if (raw.length > 500) raw.substring(0, 500) else raw
    }

    val contentWidthDp = with(density) { (columns * painter.mFontWidth).toDp() }
    val canvasSizeModifier = if (columns > 0 && painter.mFontWidth > 0f) {
        Modifier.width(contentWidthDp).height(heightDp)
    } else {
        Modifier.fillMaxWidth().height(heightDp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Canvas(
            modifier = canvasSizeModifier
                .semantics {
                if (accessibleText.isNotEmpty()) {
                    contentDescription = accessibleText
                }
            }
            .pointerInput(startAbsRow, endAbsRow, explicitAbsRows, columns, screenRows, sessionCwd, painter) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val downPos = down.position
                    val slop = viewConfiguration.touchSlop
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val deadline = System.currentTimeMillis() + longPressTimeout
                    var cancelled = false
                    var upOffset: Offset? = null

                    while (true) {
                        val remaining = deadline - System.currentTimeMillis()
                        val event = if (remaining > 0L) {
                            withTimeoutOrNull(remaining) {
                                awaitPointerEvent(PointerEventPass.Final)
                            }
                        } else {
                            null
                        }

                        if (event == null) {
                            if (!cancelled) {
                                onLongPressBody()
                            }
                            break
                        }

                        if (event.changes.size > 1 || event.changes.count { it.pressed } > 1) {
                            cancelled = true
                            break
                        }

                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || (change.position - downPos).getDistance() > slop) {
                            cancelled = true
                            break
                        }

                        if (change.changedToUpIgnoreConsumed()) {
                            upOffset = change.position
                            break
                        }
                    }

                    if (!cancelled && upOffset != null) {
                        val relRow = (upOffset.y / painter.mFontLineSpacing)
                            .toInt()
                            .coerceIn(0, rowCount - 1)
                        val tappedAbsRow = explicitAbsRows?.getOrNull(relRow) ?: (startAbsRow + relRow)
                        val col = (upOffset.x / painter.mFontWidth).toInt().coerceAtLeast(0)
                        val target = TerminalTapResolver.resolveAtAbsRow(
                            buffer = buffer,
                            absRow = tappedAbsRow,
                            col = col,
                            columns = columns,
                            maxRows = screenRows,
                            sessionCwd = sessionCwd
                        )
                        if (target != null) {
                            onTargetTapped(target, upOffset)
                        } else {
                            onTapBody()
                        }
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        revision
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val lineSpacing = painter.mFontLineSpacing
            val fontWidth = painter.mFontWidth
            val hasHighlights = matchSpansByAbsRow.isNotEmpty()

            for (i in 0 until rowCount) {
                val absRow = explicitAbsRows?.get(i) ?: (startAbsRow + i)
                val row = buffer.getRowAtAbsolute(absRow) ?: continue
                val rowTopY = (i * lineSpacing).toFloat()
                val baselineBottomY = ((i + 1) * lineSpacing).toFloat()

                if (hasHighlights) {
                    val spans = matchSpansByAbsRow[absRow]
                    if (spans != null) {
                        for (sIdx in spans.indices) {
                            val span = spans[sIdx]
                            val leftX = span.first * fontWidth
                            val rightX = (span.last + 1) * fontWidth
                            nativeCanvas.drawRect(leftX, rowTopY, rightX, baselineBottomY, highlightPaint)
                        }
                    }
                }

                val cursorX = if (cursorVisible && absRow == cursorAbsRow) cursorCol else -1
                painter.drawRow(
                    nativeCanvas,
                    row,
                    palette,
                    columns,
                    baselineBottomY,
                    cursorX,
                    cursorStyle,
                    -1,
                    -1,
                    reverseVideo
                )
            }
        }
    }
    }
}
