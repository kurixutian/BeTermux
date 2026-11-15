package com.newtermux.ai

import com.newtermux.blocks.TerminalBlock
import com.termux.terminal.TerminalBuffer

/**
 * Extracts bounded, secret-redacted structured context from a [TerminalBlock]
 * for AI troubleshooting without dumping huge scrollback or local credentials.
 */
object AiContextBuilder {

    const val DEFAULT_MAX_OUTPUT_CHARS = 6000

    private val REDACTION_RULES: List<Pair<Regex, String>> = listOf(
        // Private keys
        Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----") to
            "[REDACTED_PRIVATE_KEY]",
        // Bearer / Basic authorization headers
        Regex("(?i)\\b(Authorization:\\s*(?:Bearer|Basic)\\s+)([^\\s'\"]+)") to "$1[REDACTED]",
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._\\-]{12,}") to "Bearer [REDACTED]",
        // Well-known provider / VCS / cloud token prefixes
        Regex("\\b(?:sk-ant-[A-Za-z0-9\\-_]{8,}|sk-[A-Za-z0-9\\-_]{12,}|AIza[0-9A-Za-z\\-_]{20,}|gh[pou]_[A-Za-z0-9]{16,}|AKIA[0-9A-Z]{16})\\b") to
            "[REDACTED_TOKEN]",
        // Env vars / CLI flags containing key, token, secret, password, auth
        Regex("(?i)\\b([A-Z0-9_]*(?:API_KEY|SECRET|TOKEN|PASSWORD|PASSWD|AUTH)[A-Z0-9_]*=)([^\\s'\"]+)") to "$1[REDACTED]",
        Regex("(?i)(--?(?:api-key|token|secret|password|passwd)[=\\s]+)([^\\s'\"]+)") to "$1[REDACTED]"
    )

    fun buildFromBuffer(
        block: TerminalBlock,
        buffer: TerminalBuffer?,
        sessionCwd: String?,
        maxOutputChars: Int = DEFAULT_MAX_OUTPUT_CHARS
    ): AiBlockContext {
        val rawOutput = extractOutputRows(block, buffer)
        val redactedCmd = redactSecrets(block.command)
        val redactedOut = trimToTail(redactSecrets(rawOutput), maxOutputChars)
        return AiBlockContext(
            blockId = block.id,
            command = redactedCmd,
            exitCode = block.exitCode,
            cwd = block.cwd ?: sessionCwd,
            durationMs = block.durationMs,
            outputTail = redactedOut
        )
    }

    fun redactSecrets(input: String): String {
        var current = input
        for ((regex, replacement) in REDACTION_RULES) {
            current = regex.replace(current, replacement)
        }
        return current
    }

    private fun extractOutputRows(block: TerminalBlock, buffer: TerminalBuffer?): String {
        if (buffer == null) return ""
        if (block.endAbsRow < block.outputStartAbsRow) return ""
        val firstRow = maxOf(block.outputStartAbsRow, buffer.oldestRetainedAbsoluteRow)
        val lastRow = minOf(block.endAbsRow, buffer.screenEndAbsoluteRowExclusive - 1L)
        if (lastRow < firstRow) return ""
        return (buffer.getSelectedTextAbsolute(firstRow, lastRow) ?: "").trimEnd('\n')
    }

    private fun trimToTail(text: String, maxChars: Int): String {
        if (text.length <= maxChars || maxChars <= 0) return text
        val suffix = text.substring(text.length - maxChars)
        return "...(truncated earlier output)...\n$suffix"
    }
}
