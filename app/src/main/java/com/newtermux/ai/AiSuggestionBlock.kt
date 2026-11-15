package com.newtermux.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.theme.LocalWarpPalette

/**
 * Inline differentiated AI suggestion card rendered directly under a [com.newtermux.blocks.TerminalBlock].
 * Never auto-executes: "Accept" only inserts into the input bar draft, and "Edit" opens the
 * Floating Edit Layer with the proposed command.
 */
@Composable
fun AiSuggestionBlock(
    state: AiSuggestionState,
    horizontalPaddingDp: Int,
    onAcceptCommand: (String) -> Unit,
    onEditCommand: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onAccessibilityAnnouncement: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val view = LocalView.current

    val stripColor = when (state) {
        is AiSuggestionState.Error -> palette.error
        is AiSuggestionState.DisabledOrUnconfigured -> palette.textMuted
        is AiSuggestionState.Loading, is AiSuggestionState.Ready -> accent
    }

    val announcementText = when (state) {
        is AiSuggestionState.Ready -> {
            if (!state.suggestedCommand.isNullOrBlank()) {
                "AI suggestion ready: ${state.explanation}. Suggested command: ${state.suggestedCommand}"
            } else {
                "AI response ready: ${state.explanation}"
            }
        }
        is AiSuggestionState.Error -> "AI request failed: ${state.message}"
        is AiSuggestionState.DisabledOrUnconfigured -> "AI Assistant unavailable: ${state.reason}"
        is AiSuggestionState.Loading -> null
    }

    LaunchedEffect(state) {
        if (announcementText != null) {
            onAccessibilityAnnouncement?.invoke(announcementText)
            runCatching { view.announceForAccessibility(announcementText) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = horizontalPaddingDp.dp, vertical = 4.dp)
            .background(palette.surfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, palette.borderStrong, RoundedCornerShape(8.dp))
            .testTag("ai_suggestion_${state.blockId}")
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcementText ?: "AI request in progress for ${state.action.label}"
            }
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(stripColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = stripColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI · ${state.action.label}",
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = if (state is AiSuggestionState.Loading) onCancel else onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("ai_dismiss_${state.blockId}")
                        .semantics {
                            contentDescription = if (state is AiSuggestionState.Loading) {
                                "Cancel AI request"
                            } else {
                                "Dismiss AI suggestion"
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            when (state) {
                is AiSuggestionState.Loading -> {
                    Text(
                        text = "Analyzing block context…",
                        color = palette.textSecondary,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary),
                            modifier = Modifier
                                .testTag("ai_cancel_${state.blockId}")
                                .semantics { contentDescription = "Cancel AI request" }
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                    }
                }

                is AiSuggestionState.DisabledOrUnconfigured -> {
                    Text(
                        text = state.reason,
                        color = palette.textSecondary,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = palette.textMuted),
                            modifier = Modifier.semantics { contentDescription = "Dismiss AI notice" }
                        ) {
                            Text("Dismiss", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        AiActionPill(
                            label = "Open AI Settings",
                            containerColor = accent,
                            contentColor = palette.background,
                            borderColor = accent,
                            onClick = onOpenSettings,
                            tag = "ai_open_settings_${state.blockId}",
                            description = "Open AI Assistant settings"
                        )
                    }
                }

                is AiSuggestionState.Error -> {
                    Text(
                        text = state.message,
                        color = palette.error,
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary),
                            modifier = Modifier.semantics { contentDescription = "Dismiss AI error" }
                        ) {
                            Text("Dismiss", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        AiActionPill(
                            label = "Retry",
                            containerColor = palette.surfaceInteractive,
                            contentColor = palette.textPrimary,
                            borderColor = palette.borderStrong,
                            onClick = onRetry,
                            tag = "ai_retry_${state.blockId}",
                            description = "Retry AI request"
                        )
                    }
                }

                is AiSuggestionState.Ready -> {
                    Text(
                        text = state.explanation,
                        color = palette.textPrimary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )

                    val cmd = state.suggestedCommand
                    if (!cmd.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cmd,
                            color = accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.background, RoundedCornerShape(6.dp))
                                .border(1.dp, palette.borderSubtle, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("ai_command_${state.blockId}")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = palette.textMuted),
                            modifier = Modifier.semantics { contentDescription = "Dismiss AI suggestion" }
                        ) {
                            Text("Dismiss", fontSize = 12.sp)
                        }
                        if (!cmd.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            AiActionPill(
                                label = "Edit",
                                containerColor = palette.surfaceInteractive,
                                contentColor = palette.textPrimary,
                                borderColor = palette.borderStrong,
                                onClick = { onEditCommand(cmd) },
                                tag = "ai_edit_${state.blockId}",
                                description = "Edit suggested command in input editor"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AiActionPill(
                                label = "Accept",
                                containerColor = accent,
                                contentColor = palette.background,
                                borderColor = accent,
                                onClick = { onAcceptCommand(cmd) },
                                tag = "ai_accept_${state.blockId}",
                                description = "Accept suggested command into input bar"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiActionPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    tag: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = description }
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
