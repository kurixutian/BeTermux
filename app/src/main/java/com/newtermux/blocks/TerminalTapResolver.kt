package com.newtermux.blocks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.newtermux.features.NtPopupMenu
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.interact.ShareUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.data.TermuxUrlUtils
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TextStyle
import java.io.File
import java.util.regex.Pattern

/**
 * Shared tap-on-text resolver used by both {@code TermuxTerminalViewClient} (raw PTY /
 * alternate buffer) and {@link BlockRowsCanvas} (Block View).
 */
object TerminalTapResolver {

    enum class TargetType { URL, FILE, UNDERLINED }

    data class TapTarget(
        val type: TargetType,
        val text: String,
        val resolvedFile: File? = null
    )

    private val LINE_COL_PATTERN = Pattern.compile("^(.+?):(\\d+)(?::(\\d+))?$")

    private val CODE_OR_DOC_EXTENSIONS = setOf(
        "js", "ts", "jsx", "tsx",
        "py", "java", "kt", "rs", "go",
        "c", "cpp", "h", "hpp",
        "html", "css", "json", "xml", "yaml", "yml", "toml",
        "sh", "bash", "zsh",
        "md", "txt", "log", "sql",
        "pdf", "png", "jpg", "jpeg", "svg"
    )

    @JvmStatic
    fun resolveAtAbsRow(
        buffer: TerminalBuffer,
        absRow: Long,
        col: Int,
        columns: Int,
        maxRows: Int,
        sessionCwd: String?
    ): TapTarget? {
        val minAbs = buffer.oldestRetainedAbsoluteRow
        val maxAbsExcl = buffer.screenEndAbsoluteRowExclusive
        if (absRow < minAbs || absRow >= maxAbsExcl) return null
        val externalRow = (absRow - buffer.totalTranscriptRows).toInt()
        return resolveAtBufferCoord(buffer, externalRow, col, columns, maxRows, sessionCwd)
    }

    @JvmStatic
    fun resolveAtBufferCoord(
        buffer: TerminalBuffer,
        externalRow: Int,
        col: Int,
        columns: Int,
        maxRows: Int,
        sessionCwd: String?
    ): TapTarget? {
        val clampedCol = col.coerceIn(0, (columns - 1).coerceAtLeast(0))
        val minExtRow = -buffer.activeTranscriptRows
        val maxExtRow = (maxRows - 1).coerceAtLeast(0)
        if (externalRow < minExtRow || externalRow > maxExtRow) return null

        return try {
            val style = buffer.getStyleAt(externalRow, clampedCol)
            val isUnderlined = (TextStyle.decodeEffect(style) and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0
            var candidate: String? = null

            if (isUnderlined) {
                var startRow = externalRow
                var startCol = clampedCol
                while (startCol > 0) {
                    val prevStyle = buffer.getStyleAt(startRow, startCol - 1)
                    if ((TextStyle.decodeEffect(prevStyle) and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                        startCol--
                    } else {
                        break
                    }
                }
                if (startCol == 0 && startRow > minExtRow) {
                    val prevRowEndStyle = buffer.getStyleAt(startRow - 1, (columns - 1).coerceAtLeast(0))
                    if ((TextStyle.decodeEffect(prevRowEndStyle) and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                        startRow--
                        startCol = (columns - 1).coerceAtLeast(0)
                        while (startCol > 0) {
                            val prevStyle = buffer.getStyleAt(startRow, startCol - 1)
                            if ((TextStyle.decodeEffect(prevStyle) and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                                startCol--
                            } else {
                                break
                            }
                        }
                    }
                }

                var endRow = externalRow
                var endCol = clampedCol
                while (endCol + 1 < columns) {
                    val nextStyle = buffer.getStyleAt(endRow, endCol + 1)
                    if ((TextStyle.decodeEffect(nextStyle) and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                        endCol++
                    } else {
                        break
                    }
                }
                if (endCol + 1 >= columns && endRow + 1 < maxRows) {
                    val nextRowStartStyle = buffer.getStyleAt(endRow + 1, 0)
                    if ((TextStyle.decodeEffect(nextRowStartStyle) and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                        endRow++
                        endCol = 0
                        while (endCol + 1 < columns) {
                            val nextStyle = buffer.getStyleAt(endRow, endCol + 1)
                            if ((TextStyle.decodeEffect(nextStyle) and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                                endCol++
                            } else {
                                break
                            }
                        }
                    }
                }

                candidate = buffer.getSelectedText(startCol, startRow, endCol, endRow)?.trim()
            }

            if (candidate.isNullOrEmpty()) {
                candidate = buffer.getWordAtLocation(clampedCol, externalRow)?.trim()
            }
            if (candidate.isNullOrEmpty()) return null

            val clean = stripEnclosingPunctuation(candidate)
            if (clean.isEmpty()) return null

            extractUrl(clean)?.let { return TapTarget(TargetType.URL, it) }
            resolveFile(clean, sessionCwd)?.let { return TapTarget(TargetType.FILE, clean, it) }
            if (isUnderlined) return TapTarget(TargetType.UNDERLINED, clean)
            null
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun stripEnclosingPunctuation(s: String?): String {
        if (s == null) return ""
        var start = 0
        var end = s.length
        while (start < end) {
            val c = s[start]
            if (c == '"' || c == '\'' || c == '`' || c == '(' || c == '[' || c == '{' || c == '<' || c.isWhitespace()) {
                start++
            } else {
                break
            }
        }
        while (end > start) {
            val c = s[end - 1]
            if (c == '"' || c == '\'' || c == '`' || c == ')' || c == ']' || c == '}' || c == '>' || c == ',' || c == ';' || c.isWhitespace()) {
                end--
            } else {
                break
            }
        }
        return s.substring(start, end).trim()
    }

    @JvmStatic
    fun extractUrl(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        if (text.startsWith("file://")) return null
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("ftp://")) {
            return text
        }
        if (text.matches(Regex("^(?:localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0):\\d+.*"))) {
            return "http://$text"
        }
        val urls = TermuxUrlUtils.extractUrls(text)
        return if (!urls.isEmpty()) urls.iterator().next().toString() else null
    }

    @JvmStatic
    fun resolveFile(text: String?, cwd: String?): File? {
        if (text.isNullOrEmpty()) return null
        var raw = if (text.startsWith("file://")) text.substring(7) else text
        val m = LINE_COL_PATTERN.matcher(raw)
        if (m.matches()) {
            raw = m.group(1) ?: raw
        } else if (raw.contains("#L")) {
            raw = raw.substring(0, raw.indexOf("#L"))
        }
        raw = stripEnclosingPunctuation(raw)
        if (raw.isEmpty()) return null

        val homePath = TermuxConstants.TERMUX_HOME_DIR_PATH
        if (raw == "~") return File(homePath)
        if (raw.startsWith("~/")) return File(homePath, raw.substring(2))

        if (raw.startsWith("/")) {
            val f = File(raw)
            if (f.exists()) return f
            if (raw.startsWith("/sdcard/") || raw.startsWith("/storage/") || raw.startsWith("/data/data/")) {
                return f
            }
        }

        if (raw.startsWith("./") || raw.startsWith("../")) {
            if (cwd != null) {
                val f = File(cwd, raw)
                if (f.exists()) return f
            }
            val fHome = File(homePath, raw)
            if (fHome.exists()) return fHome
            if (cwd != null) return File(cwd, raw)
        }

        if (cwd != null) {
            val f = File(cwd, raw)
            if (f.exists()) return f
        }
        val fHome = File(homePath, raw)
        if (fHome.exists()) return fHome

        val dotIdx = raw.lastIndexOf('.')
        if (dotIdx > 0 && dotIdx < raw.length - 1 && !raw.contains(" ")) {
            val ext = raw.substring(dotIdx + 1).lowercase()
            if (ext in CODE_OR_DOC_EXTENSIONS) {
                return File(cwd ?: homePath, raw)
            }
        }
        return null
    }

    @JvmStatic
    fun showChooser(
        activity: TermuxActivity,
        anchor: View,
        target: TapTarget,
        sessionCwd: String?,
        rawX: Int,
        rawY: Int
    ) {
        when (target.type) {
            TargetType.URL -> {
                val url = target.text
                val title = if (url.length > 38) url.substring(0, 35) + "..." else url
                NtPopupMenu.showAtLocation(
                    activity, anchor, rawX, rawY, title,
                    arrayOf("Open in Dev Browser", "Open in External Browser", "Copy Link")
                ) { idx ->
                    when (idx) {
                        0 -> activity.openBrowserTab(url)
                        1 -> ShareUtils.openUrl(activity, url)
                        2 -> ShareUtils.copyTextToClipboard(
                            activity, url,
                            activity.getString(R.string.msg_select_url_copied_to_clipboard)
                        )
                    }
                }
            }
            TargetType.FILE -> {
                val file = target.resolvedFile ?: return
                var title = file.name.ifEmpty { file.absolutePath }
                if (title.length > 38) title = "..." + title.substring(title.length - 35)
                NtPopupMenu.showAtLocation(
                    activity, anchor, rawX, rawY, title,
                    arrayOf("Open in Editor Tab", "Open with External App", "Copy File Path", "Share File")
                ) { idx ->
                    when (idx) {
                        0 -> activity.openFileTab(file)
                        1 -> openFileWithSystem(activity, file)
                        2 -> ShareUtils.copyTextToClipboard(activity, file.absolutePath, "Path copied to clipboard")
                        3 -> shareFileWithSystem(activity, file)
                    }
                }
            }
            TargetType.UNDERLINED -> {
                val text = target.text
                val title = if (text.length > 38) text.substring(0, 35) + "..." else text
                NtPopupMenu.showAtLocation(
                    activity, anchor, rawX, rawY, title,
                    arrayOf("Open in Editor Tab", "Search Web", "Copy Text")
                ) { idx ->
                    when (idx) {
                        0 -> activity.openFileTab(File(sessionCwd ?: TermuxConstants.TERMUX_HOME_DIR_PATH, text))
                        1 -> activity.openBrowserTab("https://www.google.com/search?q=" + Uri.encode(text))
                        2 -> ShareUtils.copyTextToClipboard(activity, text, "Copied to clipboard")
                    }
                }
            }
        }
    }

    private fun openFileWithSystem(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open " + file.name))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFileWithSystem(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share " + file.name))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }
}
