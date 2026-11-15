package com.newtermux.workflows

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Atomic file-backed repository storing BeTermux Markdown workflows as `.md` files inside
 * `filesDir/workflows`. Survives process death without SQLite overhead and allows zero-conversion
 * external sharing.
 */
class WorkflowRepository(context: Context) {

    private val rootDir: File = File(context.applicationContext.filesDir, DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private val _workflows = MutableStateFlow<List<Workflow>>(emptyList())
    val workflows: StateFlow<List<Workflow>> = _workflows.asStateFlow()

    init {
        reload()
    }

    @Synchronized
    fun reload(): List<Workflow> {
        if (!rootDir.exists()) rootDir.mkdirs()
        val files = rootDir.listFiles { f -> f.isFile && f.name.endsWith(".md") && !f.name.endsWith(".tmp") }
            ?: emptyArray()
        val loaded = files.mapNotNull { file ->
            runCatching {
                val content = file.readText(StandardCharsets.UTF_8)
                val stem = file.name.removeSuffix(".md")
                WorkflowMarkdownParser.parse(content, fallbackId = stem)
                    ?.copy(id = stem, updatedAtMs = file.lastModified())
            }.getOrNull()
        }.sortedByDescending { it.updatedAtMs }
        _workflows.value = loaded
        return loaded
    }

    @Synchronized
    fun listWorkflows(): List<Workflow> = _workflows.value

    @Synchronized
    fun getWorkflow(id: String): Workflow? = _workflows.value.firstOrNull { it.id == id }

    @Synchronized
    fun saveWorkflow(workflow: Workflow): Workflow {
        if (!rootDir.exists()) rootDir.mkdirs()
        val safeId = resolveStorageId(workflow)
        val stored = workflow.copy(id = safeId, updatedAtMs = System.currentTimeMillis())
        val markdown = WorkflowMarkdownParser.serialize(stored)
        val target = File(rootDir, "$safeId.md")
        val temp = File(rootDir, "$safeId.md.tmp")
        temp.writeText(markdown, StandardCharsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(markdown, StandardCharsets.UTF_8)
            temp.delete()
        }
        reload()
        return stored
    }

    @Synchronized
    fun importMarkdown(markdown: String, fallbackName: String? = null): Workflow? {
        val parsed = WorkflowMarkdownParser.parse(markdown, fallbackId = fallbackName) ?: return null
        val unique = parsed.copy(id = allocateUniqueId(parsed.name))
        return saveWorkflow(unique)
    }

    @Synchronized
    fun deleteWorkflow(id: String): Boolean {
        val safeId = WorkflowMarkdownParser.slugify(id)
        val target = File(rootDir, "$safeId.md")
        if (target.canonicalFile.parentFile != rootDir.canonicalFile) return false
        val removed = !target.exists() || target.delete()
        reload()
        return removed
    }

    fun getFileForWorkflow(workflow: Workflow): File {
        val safeId = WorkflowMarkdownParser.slugify(workflow.id.ifBlank { workflow.name })
        val file = File(rootDir, "$safeId.md")
        if (!file.exists()) {
            saveWorkflow(workflow.copy(id = safeId))
        }
        return file
    }

    private fun resolveStorageId(workflow: Workflow): String {
        val existingId = workflow.id.trim()
        if (existingId.isNotEmpty() && !existingId.startsWith("new_")) {
            return WorkflowMarkdownParser.slugify(existingId)
        }
        return allocateUniqueId(workflow.name)
    }

    private fun allocateUniqueId(name: String): String {
        val base = WorkflowMarkdownParser.slugify(name)
        var candidate = base
        var idx = 2
        while (File(rootDir, "$candidate.md").exists()) {
            candidate = "${base}_$idx"
            idx++
        }
        return candidate
    }

    companion object {
        const val DIR_NAME = "workflows"
    }
}
