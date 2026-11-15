package com.newtermux.workflows

import java.util.Locale

/**
 * Parses and serializes BeTermux Markdown workflows (Amendment A1):
 * - Preferred: YAML front-matter ({@code --- ... ---}) + fenced shell code block.
 * - Fallback: H1 heading ({@code # Title}) + first paragraph + fenced shell block.
 * - Placeholders: {@code {{token}}} and {@code {{token=default}}}.
 */
object WorkflowMarkdownParser {

    private val TOKEN_REGEX = Regex("\\{\\{\\s*([A-Za-z0-9_.-]+)(?:=([^}]*))?\\s*\\}\\}")
    private val FENCE_START_REGEX = Regex("^\\s*(`{3,})([A-Za-z0-9_-]*)\\s*$")

    fun extractTokens(template: String): List<WorkflowToken> {
        if (template.isEmpty()) return emptyList()
        val seen = LinkedHashMap<String, String>()
        for (match in TOKEN_REGEX.findAll(template)) {
            val name = match.groupValues[1].trim()
            if (name.isEmpty()) continue
            val defVal = match.groupValues.getOrNull(2) ?: ""
            if (!seen.containsKey(name)) {
                seen[name] = defVal
            } else if (seen[name].isNullOrEmpty() && defVal.isNotEmpty()) {
                seen[name] = defVal
            }
        }
        return seen.entries.map { WorkflowToken(it.key, it.value) }
    }

    fun renderCommand(template: String, values: Map<String, String>): String {
        if (template.isEmpty()) return ""
        val defaults = extractTokens(template).associate { it.name to it.defaultValue }
        return TOKEN_REGEX.replace(template) { match ->
            val name = match.groupValues[1].trim()
            val explicit = values[name]
            when {
                explicit != null -> explicit
                match.groupValues.getOrNull(2)?.isNotEmpty() == true -> match.groupValues[2]
                else -> defaults[name] ?: ""
            }
        }
    }

    fun serialize(workflow: Workflow): String {
        val cleanName = workflow.name.trim().ifEmpty { "Untitled Workflow" }
        val cleanDesc = workflow.description.trim()
        val cleanAuthor = workflow.author.trim()
        val cleanTags = workflow.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val body = workflow.commandTemplate.trimEnd('\r', '\n')

        val fence = chooseFenceFor(body)

        return buildString {
            append("---\n")
            append("name: ").append(escapeYamlScalar(cleanName)).append('\n')
            if (cleanDesc.isNotEmpty()) {
                append("description: ").append(escapeYamlScalar(cleanDesc)).append('\n')
            }
            if (cleanTags.isNotEmpty()) {
                append("tags: [")
                cleanTags.forEachIndexed { idx, t ->
                    if (idx > 0) append(", ")
                    append(escapeYamlScalar(t))
                }
                append("]\n")
            }
            if (cleanAuthor.isNotEmpty()) {
                append("author: ").append(escapeYamlScalar(cleanAuthor)).append('\n')
            }
            append("version: ").append(workflow.version.coerceAtLeast(1)).append('\n')
            append("---\n\n")
            append("# ").append(cleanName).append("\n\n")
            if (cleanDesc.isNotEmpty()) {
                append(cleanDesc).append("\n\n")
            }
            append(fence).append("bash\n")
            append(body).append('\n')
            append(fence).append('\n')
        }
    }

    fun parse(markdown: String, fallbackId: String? = null): Workflow? {
        if (markdown.isBlank()) return null
        val lines = markdown.replace("\r\n", "\n").lines()
        var idx = 0
        while (idx < lines.size && lines[idx].isBlank()) idx++

        var name: String? = null
        var description: String? = null
        val tags = ArrayList<String>()
        var author = ""
        var version = 1

        // 1. Parse optional YAML front-matter
        if (idx < lines.size && lines[idx].trim() == "---") {
            var endIdx = -1
            for (j in (idx + 1) until lines.size) {
                val trimmed = lines[j].trim()
                if (trimmed == "---" || trimmed == "...") {
                    endIdx = j
                    break
                }
            }
            if (endIdx > idx) {
                var currentListKey: String? = null
                for (j in (idx + 1) until endIdx) {
                    val line = lines[j]
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    if (currentListKey == "tags" && trimmed.startsWith("-")) {
                        val item = unquoteYamlScalar(trimmed.removePrefix("-").trim())
                        if (item.isNotEmpty()) tags.add(item)
                        continue
                    }
                    currentListKey = null
                    val colon = trimmed.indexOf(':')
                    if (colon <= 0) continue
                    val key = trimmed.substring(0, colon).trim().lowercase(Locale.US)
                    val rawVal = trimmed.substring(colon + 1).trim()
                    when (key) {
                        "name", "title" -> name = unquoteYamlScalar(rawVal)
                        "description", "desc" -> description = unquoteYamlScalar(rawVal)
                        "author" -> author = unquoteYamlScalar(rawVal)
                        "version" -> version = rawVal.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        "tags" -> {
                            if (rawVal.isEmpty()) {
                                currentListKey = "tags"
                            } else if (rawVal.startsWith("[") && rawVal.endsWith("]")) {
                                rawVal.substring(1, rawVal.length - 1)
                                    .split(',')
                                    .map { unquoteYamlScalar(it.trim()) }
                                    .filter { it.isNotEmpty() }
                                    .forEach { tags.add(it) }
                            } else {
                                rawVal.split(',')
                                    .map { unquoteYamlScalar(it.trim()) }
                                    .filter { it.isNotEmpty() }
                                    .forEach { tags.add(it) }
                            }
                        }
                    }
                }
                idx = endIdx + 1
            }
        }

        // 2. Scan body for H1 fallback, optional prose description fallback, and first fenced command block
        var headingName: String? = null
        var proseDesc: String? = null
        var commandBody: String? = null

        var i = idx
        while (i < lines.size) {
            val line = lines[i]
            val fenceMatch = FENCE_START_REGEX.matchEntire(line)
            if (fenceMatch != null) {
                val fenceChars = fenceMatch.groupValues[1]
                val lang = fenceMatch.groupValues[2].lowercase(Locale.US)
                val bodyLines = ArrayList<String>()
                var k = i + 1
                var closed = false
                while (k < lines.size) {
                    val candidate = lines[k].trim()
                    if (candidate.startsWith(fenceChars) && candidate.all { it == '`' }) {
                        closed = true
                        break
                    }
                    bodyLines.add(lines[k])
                    k++
                }
                val isShellFence = lang.isEmpty() || lang in SHELL_LANGUAGES
                if (closed && isShellFence && commandBody == null) {
                    commandBody = bodyLines.joinToString("\n").trimEnd('\n')
                    break
                }
                i = if (closed) k + 1 else lines.size
                continue
            }

            val trimmed = line.trim()
            if (headingName == null && trimmed.startsWith("# ")) {
                headingName = trimmed.removePrefix("# ").trim()
            } else if (headingName != null && proseDesc == null && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                proseDesc = trimmed
            }
            i++
        }

        val finalCmd = commandBody?.takeIf { it.isNotBlank() } ?: return null
        val finalName = (name?.takeIf { it.isNotBlank() }
            ?: headingName?.takeIf { it.isNotBlank() }
            ?: fallbackId?.takeIf { it.isNotBlank() }
            ?: "Imported Workflow").trim()
        val finalDesc = (description ?: proseDesc ?: "").trim()
        val finalId = slugify(fallbackId?.takeIf { it.isNotBlank() } ?: finalName)

        return Workflow(
            id = finalId,
            name = finalName,
            description = finalDesc,
            tags = tags.distinct(),
            author = author,
            version = version,
            commandTemplate = finalCmd
        )
    }

    fun slugify(name: String): String = name
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_-]+"), "-")
        .trim('-', '_')
        .take(48)
        .ifEmpty { "workflow" }

    private fun chooseFenceFor(body: String): String {
        var maxRun = 0
        var run = 0
        for (ch in body) {
            if (ch == '`') {
                run++
                if (run > maxRun) maxRun = run
            } else {
                run = 0
            }
        }
        return "`".repeat(maxOf(3, maxRun + 1))
    }

    private fun escapeYamlScalar(value: String): String {
        val singleLine = value.replace("\n", " ").trim()
        val needsQuote = singleLine.isEmpty() ||
            singleLine.any { it in charArrayOf(':', '#', '"', '\'', '{', '}', '[', ']', ',', '&', '*', '!') }
        return if (!needsQuote) singleLine else "\"" + singleLine.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private fun unquoteYamlScalar(raw: String): String {
        if (raw.length >= 2 && ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")))) {
            val inner = raw.substring(1, raw.length - 1)
            return if (raw.startsWith("\"")) {
                inner.replace("\\\"", "\"").replace("\\\\", "\\")
            } else {
                inner
            }
        }
        return raw
    }

    private val SHELL_LANGUAGES = setOf("bash", "sh", "zsh", "shell", "console", "fish", "cmd")
}
