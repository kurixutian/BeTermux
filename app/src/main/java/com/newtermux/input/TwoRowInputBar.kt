package com.newtermux.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.theme.LocalWarpPalette

/**
 * Calm, compact two-row editor chrome matching Warp's flat charcoal design:
 * Row 1 — prompt context ({@code user@host}) + horizontally scrollable context chips.
 * Row 2 — tappable command field (opens {@link FloatingEditLayer}) + quick Send / Clear / Workflows.
 */
@Composable
fun TwoRowInputBar(
    promptContext: PromptContext,
    draft: String,
    isTopPosition: Boolean,
    onOpenEditor: () -> Unit,
    onSendCommand: (String) -> Unit,
    onClearDraft: () -> Unit,
    onSendEnter: () -> Unit,
    onExecuteChipCommand: (String) -> Unit,
    onInsertChipText: (String) -> Unit,
    onCopyChipText: (String, String) -> Unit,
    onOpenWorkflows: (() -> Unit)? = null,
    onJumpLastFailure: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val fieldShape = RoundedCornerShape(8.dp)
    val lineCount = if (draft.isEmpty()) 0 else draft.count { it == '\n' } + 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface)
    ) {
        if (!isTopPosition) {
            HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        }

        // Row 1: Prompt context + scrollable context chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = promptContext.userAtHost,
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .semantics { contentDescription = "Prompt host ${promptContext.userAtHost}" }
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .padding(vertical = 2.dp)
                    .background(palette.borderSubtle)
            )
            ContextChipsRow(
                context = promptContext,
                onExecuteCommand = onExecuteChipCommand,
                onInsertDraft = onInsertChipText,
                onCopyText = onCopyChipText,
                onOpenWorkflows = onOpenWorkflows,
                onJumpLastFailure = onJumpLastFailure,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
        }

        // Row 2: Tappable command field opening FloatingEditLayer + Send / Enter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 8.dp, top = 2.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(fieldShape)
                    .background(palette.surfaceElevated, fieldShape)
                    .border(
                        width = 1.dp,
                        color = if (draft.isNotEmpty()) accent.copy(alpha = 0.55f) else palette.borderSubtle,
                        shape = fieldShape
                    )
                    .clickable(onClick = onOpenEditor)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("two_row_input_field")
                    .semantics {
                        contentDescription = if (draft.isEmpty()) {
                            "Command input bar, tap to open editor"
                        } else {
                            "Command draft: $draft, tap to edit"
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (draft.isEmpty()) "Tap to edit command…" else draft,
                    color = if (draft.isEmpty()) palette.textMuted else palette.textPrimary,
                    fontFamily = if (draft.isEmpty()) FontFamily.SansSerif else FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (lineCount > 1) {
                    Text(
                        text = "${lineCount}L",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                } else if (draft.isEmpty()) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (draft.isNotEmpty()) {
                IconButton(
                    onClick = onClearDraft,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Clear command draft" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (onOpenWorkflows != null) {
                IconButton(
                    onClick = onOpenWorkflows,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Open workflow picker" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        color = if (draft.isNotBlank()) accent else palette.surfaceElevated,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (draft.isNotBlank()) accent else palette.borderSubtle,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        if (draft.isNotBlank()) {
                            onSendCommand(draft)
                        } else {
                            onSendEnter()
                        }
                    }
                    .testTag("two_row_send_button")
                    .semantics {
                        contentDescription = if (draft.isNotBlank()) "Send command" else "Send newline"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (draft.isNotBlank()) {
                        Icons.AutoMirrored.Filled.Send
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardReturn
                    },
                    contentDescription = null,
                    tint = if (draft.isNotBlank()) palette.background else palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isTopPosition) {
            HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        }
    }
}
