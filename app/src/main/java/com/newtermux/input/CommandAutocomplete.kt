package com.newtermux.input

import java.io.File

data class AutocompleteSuggestion(
    val label: String,
    val insertText: String,
    val kind: String
)

/**
 * Lightweight inline autocomplete for [FloatingEditLayer]:
 * - Slash commands & installed skills when typing `/...` at the start of a line
 * - Filesystem directory/file completion for `/`, `./`, `../`, `~/`, or `cwd` tokens
 * - Common shell commands on the first token
 */
object CommandAutocomplete {

    private val BUILTIN_SLASH_COMMANDS = listOf(
        "/help",
        "/clear",
        "/plan",
        "/compact",
        "/goal",
        "/schedule",
        "/teamwork",
        "/learn",
        "/boost",
        "/grill-me",
        "/impeccable",
        "/caveman",
        "/lean"
    )

    private val COMMON_COMMANDS = listOf(
        "pkg", "apt", "git", "ls", "cd", "cat", "grep", "rg", "find",
        "mkdir", "rm", "cp", "mv", "chmod", "python", "node", "npm",
        "bun", "cargo", "./gradlew", "ssh", "tmux", "curl", "vim", "nano"
    )

    fun computeSuggestions(
        text: String,
        cursorIndex: Int,
        cwd: String?,
        homeDir: String? = resolveDefaultHome(cwd),
        maxResults: Int = 8
    ): List<AutocompleteSuggestion> {
        val safeCursor = cursorIndex.coerceIn(0, text.length)
        val (tokenStart, token) = extractTokenBeforeCursor(text, safeCursor)
        if (token.isEmpty()) return emptyList()

        val lineStart = text.lastIndexOf('\n', (safeCursor - 1).coerceAtLeast(0)) + 1
        val isFirstTokenOnLine = text.substring(lineStart, tokenStart).isBlank()

        val results = LinkedHashMap<String, AutocompleteSuggestion>()

        // 1) Slash command + installed skill suggestions at start of line
        if (isFirstTokenOnLine && token.startsWith("/") && !token.drop(1).contains('/')) {
            val prefix = token.lowercase()
            val slashCandidates = LinkedHashSet<String>(BUILTIN_SLASH_COMMANDS)
            slashCandidates.addAll(discoverInstalledSkillSlashNames(homeDir))
            for (cmd in slashCandidates) {
                if (cmd.lowercase().startsWith(prefix) && cmd != token) {
                    results.putIfAbsent(
                        cmd,
                        AutocompleteSuggestion(label = cmd, insertText = "$cmd ", kind = "slash")
                    )
                    if (results.size >= maxResults) break
                }
            }
        }

        // 2) Path / directory completion (for /, ./, ../, ~/, or any path with '/', or cwd prefix >= 2 chars)
        val looksLikePath = token.startsWith("/") ||
            token.startsWith("./") ||
            token.startsWith("../") ||
            token.startsWith("~/") ||
            token == "~" ||
            token.contains('/')
        if (looksLikePath || (!isFirstTokenOnLine && token.length >= 2)) {
            val pathMatches = completePath(token, cwd, homeDir, maxResults - results.size)
            for (match in pathMatches) {
                results.putIfAbsent(match.insertText, match)
                if (results.size >= maxResults) break
            }
        }

        // 3) Common shell commands for first token (non-slash)
        if (isFirstTokenOnLine && !looksLikePath && results.size < maxResults) {
            val prefix = token.lowercase()
            for (cmd in COMMON_COMMANDS) {
                if (cmd.startsWith(prefix) && cmd != token) {
                    results.putIfAbsent(
                        cmd,
                        AutocompleteSuggestion(label = cmd, insertText = "$cmd ", kind = "cmd")
                    )
                    if (results.size >= maxResults) break
                }
            }
        }

        return results.values.take(maxResults)
    }

    fun applySuggestion(
        text: String,
        cursorIndex: Int,
        suggestion: AutocompleteSuggestion
    ): Pair<String, Int> {
        val safeCursor = cursorIndex.coerceIn(0, text.length)
        val (tokenStart, _) = extractTokenBeforeCursor(text, safeCursor)
        val updated = buildString {
            append(text, 0, tokenStart)
            append(suggestion.insertText)
            append(text, safeCursor, text.length)
        }
        return updated to (tokenStart + suggestion.insertText.length)
    }

    internal fun extractTokenBeforeCursor(text: String, cursorIndex: Int): Pair<Int, String> {
        var i = cursorIndex.coerceIn(0, text.length)
        while (i > 0 && !text[i - 1].isWhitespace()) {
            i--
        }
        return i to text.substring(i, cursorIndex)
    }

    private fun completePath(
        token: String,
        cwd: String?,
        homeDir: String?,
        limit: Int
    ): List<AutocompleteSuggestion> {
        if (limit <= 0) return emptyList()
        if (token == "~") {
            return listOf(AutocompleteSuggestion("~/", "~/", "dir"))
        }

        val lastSlash = token.lastIndexOf('/')
        val typedDirPart = if (lastSlash >= 0) token.substring(0, lastSlash + 1) else ""
        val namePrefix = if (lastSlash >= 0) token.substring(lastSlash + 1) else token

        val resolvedDir: File = when {
            typedDirPart.startsWith("~/") -> {
                val home = homeDir?.let(::File) ?: return emptyList()
                val sub = typedDirPart.removePrefix("~/")
                if (sub.isEmpty()) home else File(home, sub)
            }
            typedDirPart.startsWith("/") -> File(typedDirPart)
            !cwd.isNullOrBlank() -> {
                if (typedDirPart.isEmpty()) File(cwd) else File(cwd, typedDirPart)
            }
            else -> return emptyList()
        }

        val children = runCatching { resolvedDir.listFiles() }.getOrNull() ?: return emptyList()
        val showHidden = namePrefix.startsWith(".")
        val prefixLower = namePrefix.lowercase()

        return children
            .asSequence()
            .filter { file ->
                val name = file.name
                (showHidden || !name.startsWith(".")) &&
                    name.lowercase().startsWith(prefixLower)
            }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .take(limit)
            .map { file ->
                val suffix = if (file.isDirectory) "/" else ""
                val display = file.name + suffix
                val insert = typedDirPart + display
                AutocompleteSuggestion(
                    label = display,
                    insertText = insert,
                    kind = if (file.isDirectory) "dir" else "file"
                )
            }
            .toList()
    }

    private fun discoverInstalledSkillSlashNames(homeDir: String?): List<String> {
        if (homeDir.isNullOrBlank()) return emptyList()
        val roots = listOf(
            File(homeDir, ".gemini/config/skills"),
            File(homeDir, ".gemini/skills"),
            File(homeDir, ".claude/skills")
        )
        val out = ArrayList<String>()
        for (root in roots) {
            val entries = runCatching { root.listFiles() }.getOrNull() ?: continue
            for (entry in entries) {
                if (entry.isDirectory && !entry.name.startsWith(".")) {
                    out.add("/${entry.name}")
                }
            }
        }
        val pluginsRoot = File(homeDir, ".gemini/config/plugins")
        val plugins = runCatching { pluginsRoot.listFiles() }.getOrNull()
        if (plugins != null) {
            for (plugin in plugins) {
                val skillsDir = File(plugin, "skills")
                val entries = runCatching { skillsDir.listFiles() }.getOrNull() ?: continue
                for (entry in entries) {
                    if (entry.isDirectory && !entry.name.startsWith(".")) {
                        out.add("/${entry.name}")
                    }
                }
            }
        }
        return out
    }

    private fun resolveDefaultHome(cwd: String?): String? {
        val prop = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        if (prop != null) return prop
        if (!cwd.isNullOrBlank() && cwd.contains("/files/home")) {
            val idx = cwd.indexOf("/files/home")
            return cwd.substring(0, idx + "/files/home".length)
        }
        return null
    }
}
