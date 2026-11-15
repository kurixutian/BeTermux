package com.newtermux.blocks

import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Installs idempotent bash/zsh shell integration hooks that emit OSC 133 command zone markers
 * (A prompt start, B command start, C command executed, D command finished + exit code) and
 * OSC 7 working directory updates. Never overwrites existing user rc files.
 */
object ShellIntegrationInstaller {

    private const val LOG_TAG = "ShellIntegration"
    private const val SOURCE_MARKER = "# BeTermux block integration"

    private val BASH_SCRIPT = """
        # BeTermux OSC 133 & OSC 7 shell integration for bash
        if [ -z "${'$'}__betermux_integrated" ]; then
          __betermux_integrated=1
          __betermux_in_cmd=0
          __betermux_osc7() {
            local p="${'$'}{PWD//%/%25}"
            p="${'$'}{p// /%20}"
            printf '\033]7;file://localhost%s\007' "${'$'}p"
          }
          __betermux_preexec() {
            [ "${'$'}__betermux_in_cmd" = "1" ] && return
            __betermux_in_cmd=1
            printf '\033]133;C\007'
          }
          __betermux_precmd() {
            local ec=$?
            if [ "${'$'}__betermux_in_cmd" = "1" ]; then
              printf '\033]133;D;%s\007' "${'$'}ec"
              __betermux_in_cmd=0
            fi
            __betermux_osc7
            printf '\033]133;A\007'
          }
          if [[ "${'$'}{PROMPT_COMMAND}" != *"__betermux_precmd"* ]]; then
            PROMPT_COMMAND="__betermux_precmd${'$'}{PROMPT_COMMAND:+; ${'$'}PROMPT_COMMAND}"
          fi
          PS1="${'$'}PS1\[\033]133;B\007\]"
          PS0="\$(__betermux_preexec)${'$'}PS0"
        fi
    """.trimIndent() + "\n"

    private val ZSH_SCRIPT = """
        # BeTermux OSC 133 & OSC 7 shell integration for zsh
        if [ -z "${'$'}__betermux_integrated" ]; then
          __betermux_integrated=1
          __betermux_in_cmd=0
          __betermux_osc7() {
            local p="${'$'}{PWD//%/%25}"
            p="${'$'}{p// /%20}"
            printf '\033]7;file://localhost%s\007' "${'$'}p"
          }
          __betermux_precmd() {
            local ec=$?
            if [ "${'$'}__betermux_in_cmd" = "1" ]; then
              printf '\033]133;D;%s\007' "${'$'}ec"
              __betermux_in_cmd=0
            fi
            __betermux_osc7
            printf '\033]133;A\007'
          }
          __betermux_preexec() {
            __betermux_in_cmd=1
            printf '\033]133;C;cmdline=%s\007' "${'$'}{1//$'\n'/ }"
          }
          autoload -Uz add-zsh-hook 2>/dev/null || true
          if typeset -f add-zsh-hook >/dev/null 2>&1; then
            add-zsh-hook precmd __betermux_precmd
            add-zsh-hook preexec __betermux_preexec
          fi
          PROMPT="${'$'}{PROMPT}%{\033]133;B\007%}"
        fi
    """.trimIndent() + "\n"

    @JvmStatic
    fun installIfPossible() {
        installIntoHomeDir(File(TermuxConstants.TERMUX_HOME_DIR_PATH))
    }

    @JvmStatic
    fun installIntoHomeDir(homeDir: File) {
        try {
            if (!homeDir.exists() && !homeDir.mkdirs()) return
            val configDir = File(homeDir, ".config/betermux")
            if (!configDir.exists() && !configDir.mkdirs()) return

            File(configDir, "integration.bash").writeText(BASH_SCRIPT, StandardCharsets.UTF_8)
            File(configDir, "integration.zsh").writeText(ZSH_SCRIPT, StandardCharsets.UTF_8)

            appendSourceGuardIfMissing(
                rcFile = File(homeDir, ".bashrc"),
                sourceLine = "[ -r \"\$HOME/.config/betermux/integration.bash\" ] && . \"\$HOME/.config/betermux/integration.bash\" $SOURCE_MARKER"
            )
            appendSourceGuardIfMissing(
                rcFile = File(homeDir, ".zshrc"),
                sourceLine = "[ -r \"\$HOME/.config/betermux/integration.zsh\" ] && . \"\$HOME/.config/betermux/integration.zsh\" $SOURCE_MARKER"
            )
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Shell integration install skipped: " + e.message)
        }
    }

    private fun appendSourceGuardIfMissing(rcFile: File, sourceLine: String) {
        val existing = if (rcFile.exists()) rcFile.readText(StandardCharsets.UTF_8) else ""
        if (existing.contains(SOURCE_MARKER)) return
        val prefix = if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n"
        rcFile.writeText(existing + prefix + sourceLine + "\n", StandardCharsets.UTF_8)
    }
}
