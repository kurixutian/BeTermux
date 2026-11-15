package com.newtermux.workflows

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Android Share Intent export and incoming `.md` workflow detection helpers.
 */
object WorkflowShareHelper {

    private const val MAX_IMPORT_BYTES = 256 * 1024

    fun buildShareIntent(context: Context, workflow: Workflow, repo: WorkflowRepository): Intent {
        val mdFile = repo.getFileForWorkflow(workflow)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", mdFile)
        val markdown = WorkflowMarkdownParser.serialize(workflow)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_SUBJECT, "${workflow.name}.md")
            putExtra(Intent.EXTRA_TITLE, "${workflow.name}.md")
            putExtra(Intent.EXTRA_TEXT, markdown)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share Workflow")
    }

    fun shareWorkflow(context: Context, workflow: Workflow, repo: WorkflowRepository) {
        val chooser = buildShareIntent(context, workflow, repo)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Reads up to {@link #MAX_IMPORT_BYTES} from a shared stream if filename/mime suggests Markdown
     * or returns parsed workflow + buffered bytes so non-workflow files can fall back cleanly.
     */
    @JvmStatic
    fun inspectIncomingBytes(
        bytes: ByteArray,
        fileName: String?,
        mimeType: String?
    ): Workflow? {
        if (bytes.isEmpty() || bytes.size > MAX_IMPORT_BYTES) return null
        val isMdName = fileName?.lowercase()?.let { it.endsWith(".md") || it.endsWith(".markdown") } == true
        val isMdMime = mimeType?.lowercase()?.contains("markdown") == true
        val text = String(bytes, StandardCharsets.UTF_8)
        val hasFrontMatterOrFence = text.trimStart().startsWith("---") ||
            text.contains("```bash") || text.contains("```sh") || text.contains("```zsh")
        if (!isMdName && !isMdMime && !hasFrontMatterOrFence) return null
        val stem = fileName?.substringBeforeLast('.')
        return WorkflowMarkdownParser.parse(text, fallbackId = stem)
    }

    @JvmStatic
    fun readBoundedBytes(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (total <= MAX_IMPORT_BYTES) {
            val read = stream.read(buf)
            if (read <= 0) break
            out.write(buf, 0, read)
            total += read
        }
        return out.toByteArray()
    }
}
