package com.newtermux.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.termux.terminal.TerminalBuffer
import com.termux.view.TerminalRowPainter

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
    val accessibleText = remember(revision, startAbsRow, endAbsRow, explicitAbsRows) {
        val raw = if (explicitAbsRows != null) {
            explicitAbsRows.joinToString("\n") { buffer.getSelectedTextAbsolute(it, it) }.trim()
        } else {
            buffer.getSelectedTextAbsolute(startAbsRow, endAbsRow).trim()
        }
        if (raw.length > 500) raw.substring(0, 500) else raw
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .semantics {
                if (accessibleText.isNotEmpty()) {
                    contentDescription = accessibleText
                }
            }
            .pointerInput(startAbsRow, endAbsRow, explicitAbsRows, columns, screenRows, sessionCwd, painter) {
                detectTapGestures(
                    onTap = { offset ->
                        val relRow = (offset.y / painter.mFontLineSpacing)
                            .toInt()
                            .coerceIn(0, rowCount - 1)
                        val tappedAbsRow = explicitAbsRows?.getOrNull(relRow) ?: (startAbsRow + relRow)
                        val col = (offset.x / painter.mFontWidth).toInt().coerceAtLeast(0)
                        val target = TerminalTapResolver.resolveAtAbsRow(
                            buffer = buffer,
                            absRow = tappedAbsRow,
                            col = col,
                            columns = columns,
                            maxRows = screenRows,
                            sessionCwd = sessionCwd
                        )
                        if (target != null) {
                            onTargetTapped(target, offset)
                        } else {
                            onTapBody()
                        }
                    },
                    onLongPress = { onLongPressBody() }
                )
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
