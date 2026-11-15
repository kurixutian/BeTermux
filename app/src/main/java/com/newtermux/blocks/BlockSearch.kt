package com.newtermux.blocks

import com.termux.terminal.TerminalBuffer

/** Per-block search state supporting line filtering and substring highlighting. */
data class BlockSearchState(
    val query: String = "",
    val filterOnly: Boolean = true
)

data class BlockSearchResult(
    val matchingAbsRows: List<Long>,
    val totalOutputRows: Int,
    val totalMatches: Int,
    val columnSpansByAbsRow: Map<Long, List<IntRange>>
)

object BlockSearchMatcher {

    /**
     * Scan a block's output row range {@code [outputStartAbsRow..endAbsRow]} in {@link TerminalBuffer}
     * once when query or revision changes. Returns matching row indices and column ranges for
     * zero-allocation highlight rendering in {@link BlockRowsCanvas}.
     */
    fun searchBlock(
        buffer: TerminalBuffer,
        outputStartAbsRow: Long,
        endAbsRow: Long,
        query: String
    ): BlockSearchResult {
        if (endAbsRow < outputStartAbsRow) {
            return BlockSearchResult(emptyList(), 0, 0, emptyMap())
        }
        val clippedStart = maxOf(outputStartAbsRow, buffer.oldestRetainedAbsoluteRow)
        val clippedEnd = minOf(endAbsRow, buffer.screenEndAbsoluteRowExclusive - 1L)
        if (clippedEnd < clippedStart) {
            return BlockSearchResult(emptyList(), 0, 0, emptyMap())
        }
        val totalRows = ((clippedEnd - clippedStart) + 1L).toInt().coerceAtLeast(0)
        val needle = query.trim()
        if (needle.isEmpty()) {
            return BlockSearchResult(emptyList(), totalRows, 0, emptyMap())
        }

        val matchingRows = ArrayList<Long>()
        val spansByRow = LinkedHashMap<Long, List<IntRange>>()
        var matchCount = 0

        for (absRow in clippedStart..clippedEnd) {
            val rowText = buffer.getSelectedTextAbsolute(absRow, absRow)
            if (rowText.isEmpty()) continue
            val spans = findCaseInsensitiveSpans(rowText, needle)
            if (spans.isNotEmpty()) {
                matchingRows.add(absRow)
                spansByRow[absRow] = spans
                matchCount += spans.size
            }
        }

        return BlockSearchResult(
            matchingAbsRows = matchingRows,
            totalOutputRows = totalRows,
            totalMatches = matchCount,
            columnSpansByAbsRow = spansByRow
        )
    }

    internal fun findCaseInsensitiveSpans(line: String, needle: String): List<IntRange> {
        if (needle.isEmpty() || line.length < needle.length) return emptyList()
        val spans = ArrayList<IntRange>(2)
        var startIdx = 0
        while (startIdx <= line.length - needle.length) {
            val hit = line.indexOf(needle, startIndex = startIdx, ignoreCase = true)
            if (hit < 0) break
            val endExclusive = hit + needle.length
            // Convert Java char indices cleanly to visual column offsets accounting for surrogate pairs
            val startCol = line.codePointCount(0, hit)
            val endCol = startCol + line.codePointCount(hit, endExclusive)
            spans.add(startCol until endCol)
            startIdx = maxOf(hit + 1, endExclusive)
        }
        return spans
    }
}
