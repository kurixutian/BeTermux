package com.newtermux.input

import android.os.Build
import java.io.File

/**
 * Lightweight, event-driven snapshot of the active session's shell context.
 * Uses only single-shot filesystem checks when CWD changes or a block finishes (no polling).
 */
data class PromptContext(
    val userAtHost: String,
    val fullCwd: String,
    val displayCwd: String,
    val gitBranch: String?,
    val lastExitCode: Int?,
    val projectTags: List<String> = emptyList()
)

object PromptContextResolver {

    @Volatile
    private var cachedUserAtHost: String? = null

    fun defaultUserAtHost(): String {
        cachedUserAtHost?.let { return it }
        val user = System.getenv("USER")?.takeIf { it.isNotBlank() } ?: "termux"
        val model = (Build.MODEL ?: "android")
            .lowercase()
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
            .take(16)
            .ifEmpty { "android" }
        return "$user@$model".also { cachedUserAtHost = it }
    }

    /**
     * Inspect {@code rawCwd} synchronously on an IO thread to extract display CWD,
     * git branch (from ancestor {@code .git/HEAD}), and top-level project hints.
     */
    fun resolve(rawCwd: String?, lastExitCode: Int?, homeDir: String? = "/data/data/com.termux/files/home"): PromptContext {
        val cwd = rawCwd?.takeIf { it.isNotBlank() } ?: homeDir ?: "/"
        val displayCwd = formatDisplayCwd(cwd, homeDir)
        val gitBranch = detectGitBranch(cwd)
        val tags = detectProjectTags(cwd)
        return PromptContext(
            userAtHost = defaultUserAtHost(),
            fullCwd = cwd,
            displayCwd = displayCwd,
            gitBranch = gitBranch,
            lastExitCode = lastExitCode,
            projectTags = tags
        )
    }

    fun formatDisplayCwd(cwd: String, homeDir: String?): String {
        if (!homeDir.isNullOrBlank() && cwd == homeDir) return "~"
        if (!homeDir.isNullOrBlank() && cwd.startsWith("$homeDir/")) {
            return "~/" + cwd.removePrefix("$homeDir/")
        }
        return cwd
    }

    fun detectGitBranch(cwd: String): String? {
        return runCatching {
            var dir: File? = File(cwd)
            var depth = 0
            while (dir != null && depth < 12) {
                val dotGit = File(dir, ".git")
                if (dotGit.exists()) {
                    val headFile = if (dotGit.isDirectory) {
                        File(dotGit, "HEAD")
                    } else {
                        val pointer = dotGit.readText().trim()
                        if (pointer.startsWith("gitdir:")) {
                            val targetPath = pointer.removePrefix("gitdir:").trim()
                            val gitDir = File(targetPath).let { if (it.isAbsolute) it else File(dir, targetPath) }
                            File(gitDir, "HEAD")
                        } else {
                            null
                        }
                    }
                    if (headFile != null && headFile.isFile) {
                        val content = headFile.bufferedReader().use { it.readLine() }?.trim() ?: return null
                        return when {
                            content.startsWith("ref: refs/heads/") -> content.removePrefix("ref: refs/heads/")
                            content.startsWith("ref: ") -> content.removePrefix("ref: ").substringAfterLast('/')
                            content.length >= 7 -> content.take(7)
                            else -> content.ifEmpty { null }
                        }
                    }
                    return null
                }
                dir = dir.parentFile
                depth++
            }
            null
        }.getOrNull()
    }

    private fun detectProjectTags(cwd: String): List<String> {
        val dir = File(cwd)
        if (!dir.isDirectory) return emptyList()
        val tags = ArrayList<String>(3)
        if (File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()) {
            tags.add("gradle")
        }
        if (File(dir, "package.json").exists()) {
            tags.add("node")
        }
        if (File(dir, "Cargo.toml").exists()) {
            tags.add("rust")
        }
        if (File(dir, "pyproject.toml").exists() || File(dir, "requirements.txt").exists()) {
            tags.add("python")
        }
        return tags
    }
}
