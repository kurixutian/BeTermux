package com.newtermux.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.newtermux.theme.LocalWarpPalette
import com.newtermux.theme.WarpChip

/**
 * Horizontally scrollable, tappable context chips (CWD, Git branch, detected toolchain,
 * last exit status, and workflows) providing one-tap quick actions.
 */
@Composable
fun ContextChipsRow(
    context: PromptContext,
    onExecuteCommand: (String) -> Unit,
    onInsertDraft: (String) -> Unit,
    onCopyText: (String, String) -> Unit,
    onOpenWorkflows: (() -> Unit)? = null,
    onJumpLastFailure: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    var cwdMenuOpen by remember { mutableStateOf(false) }
    var gitMenuOpen by remember { mutableStateOf(false) }

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. CWD chip + quick path actions
        item(key = "chip_cwd") {
            Box {
                WarpChip(
                    label = context.displayCwd,
                    accent = accent,
                    selected = false,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    onClick = { cwdMenuOpen = true },
                    modifier = Modifier.semantics {
                        contentDescription = "Current directory ${context.displayCwd}, tap for actions"
                    }
                )
                DropdownMenu(
                    expanded = cwdMenuOpen,
                    onDismissRequest = { cwdMenuOpen = false },
                    containerColor = palette.surfaceElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("cd ..", color = palette.textPrimary) },
                        onClick = { cwdMenuOpen = false; onExecuteCommand("cd ..") }
                    )
                    DropdownMenuItem(
                        text = { Text("cd ~", color = palette.textPrimary) },
                        onClick = { cwdMenuOpen = false; onExecuteCommand("cd ~") }
                    )
                    DropdownMenuItem(
                        text = { Text("ls -la", color = palette.textPrimary) },
                        onClick = { cwdMenuOpen = false; onExecuteCommand("ls -la") }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy full path", color = palette.textSecondary) },
                        onClick = { cwdMenuOpen = false; onCopyText(context.fullCwd, "Directory path copied") }
                    )
                    DropdownMenuItem(
                        text = { Text("Insert path into editor", color = palette.textSecondary) },
                        onClick = { cwdMenuOpen = false; onInsertDraft(context.fullCwd) }
                    )
                }
            }
        }

        // 2. Git branch chip (when detected)
        if (!context.gitBranch.isNullOrBlank()) {
            item(key = "chip_git") {
                Box {
                    WarpChip(
                        label = context.gitBranch,
                        accent = palette.info,
                        selected = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = palette.info,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        onClick = { gitMenuOpen = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Git branch ${context.gitBranch}, tap for git actions"
                        }
                    )
                    DropdownMenu(
                        expanded = gitMenuOpen,
                        onDismissRequest = { gitMenuOpen = false },
                        containerColor = palette.surfaceElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("git status", color = palette.textPrimary) },
                            onClick = { gitMenuOpen = false; onExecuteCommand("git status") }
                        )
                        DropdownMenuItem(
                            text = { Text("git diff --stat", color = palette.textPrimary) },
                            onClick = { gitMenuOpen = false; onExecuteCommand("git diff --stat") }
                        )
                        DropdownMenuItem(
                            text = { Text("git log -n 5 --oneline", color = palette.textPrimary) },
                            onClick = { gitMenuOpen = false; onExecuteCommand("git log -n 5 --oneline") }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy branch name", color = palette.textSecondary) },
                            onClick = { gitMenuOpen = false; onCopyText(context.gitBranch, "Branch copied") }
                        )
                    }
                }
            }
        }

        // 3. Non-zero exit indicator chip
        val lastExit = context.lastExitCode
        if (lastExit != null && lastExit != 0) {
            item(key = "chip_exit") {
                WarpChip(
                    label = "exit $lastExit",
                    accent = palette.error,
                    selected = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = palette.error,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    onClick = {
                        if (onJumpLastFailure != null) onJumpLastFailure() else onInsertDraft("!!")
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Last command exited with $lastExit"
                    }
                )
            }
        }

        // 4. Detected project toolchains
        for (tag in context.projectTags) {
            val quickCmd = when (tag) {
                "gradle" -> "./gradlew test"
                "node" -> "npm test"
                "rust" -> "cargo test"
                "python" -> "pytest"
                else -> tag
            }
            item(key = "chip_tag_$tag") {
                WarpChip(
                    label = tag,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    onClick = { onInsertDraft(quickCmd) },
                    modifier = Modifier.semantics {
                        contentDescription = "Detected $tag project, insert $quickCmd"
                    }
                )
            }
        }

        // 5. Optional Workflow picker quick access (activated in Phase 4)
        if (onOpenWorkflows != null) {
            item(key = "chip_workflows") {
                WarpChip(
                    label = "Workflows",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    onClick = onOpenWorkflows,
                    modifier = Modifier.semantics { contentDescription = "Open workflow picker" }
                )
            }
        }

        // 6. Quick directory listing shortcut
        item(key = "chip_ls") {
            WarpChip(
                label = "ls -la",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color.Unspecified.takeIf { false } ?: palette.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                },
                onClick = { onExecuteCommand("ls -la") },
                modifier = Modifier.semantics { contentDescription = "Run ls -la" }
            )
        }
    }
}
