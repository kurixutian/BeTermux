package com.newtermux.input

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.theme.LocalWarpPalette
import com.newtermux.theme.WarpChip

/**
 * Full-width floating command editor layer:
 * - Free cursor placement & soft wrapping across multiple lines
 * - Shift+Enter (or Newline chip) inserts a newline at the cursor
 * - Enter / Send dispatches the whole command buffer to the shell
 * - Dismissing preserves the draft in {@link InputDraftStore}
 * - Adapts height between portrait and landscape orientations
 */
@Composable
fun FloatingEditLayer(
    visible: Boolean,
    draft: String,
    promptContext: PromptContext,
    isTopPosition: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onClosePreservingDraft: () -> Unit,
    onTogglePosition: () -> Unit,
    onOpenWorkflows: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    BackHandler(enabled = true) {
        onClosePreservingDraft()
    }

    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxFieldHeight = if (isLandscape) 116.dp else 220.dp
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(draft, TextRange(draft.length)))
    }
    if (textFieldValue.text != draft) {
        val clampedStart = textFieldValue.selection.start.coerceIn(0, draft.length)
        val clampedEnd = textFieldValue.selection.end.coerceIn(0, draft.length)
        textFieldValue = textFieldValue.copy(text = draft, selection = TextRange(clampedStart, clampedEnd))
    }

    LaunchedEffect(visible) {
        if (visible) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun insertNewlineAtCursor() {
        val current = textFieldValue
        val minIdx = minOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
        val maxIdx = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
        val updated = buildString {
            append(current.text, 0, minIdx)
            append('\n')
            append(current.text, maxIdx, current.text.length)
        }
        val cursor = minIdx + 1
        textFieldValue = TextFieldValue(updated, TextRange(cursor))
        onDraftChange(updated)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClosePreservingDraft
            )
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = if (isTopPosition) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.borderStrong),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header: context summary + Dock Top/Bottom toggle + Workflows + Close (preserves draft)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Command Editor",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = buildString {
                                append(promptContext.displayCwd)
                                if (!promptContext.gitBranch.isNullOrBlank()) {
                                    append("  •  ")
                                    append(promptContext.gitBranch)
                                }
                            },
                            color = palette.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    WarpChip(
                        label = if (isTopPosition) "Top" else "Bottom",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        onClick = onTogglePosition,
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle input bar position, currently ${if (isTopPosition) "top" else "bottom"}"
                        }
                    )

                    if (onOpenWorkflows != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onOpenWorkflows,
                            modifier = Modifier
                                .size(34.dp)
                                .semantics { contentDescription = "Insert workflow" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClosePreservingDraft,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("floating_edit_close")
                            .semantics { contentDescription = "Close editor and keep draft" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
                Spacer(modifier = Modifier.height(8.dp))

                // Soft-wrapped multi-line BasicTextField with Shift+Enter & Enter handling
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isLandscape) 64.dp else 96.dp, max = maxFieldHeight)
                        .background(palette.surfaceElevated, RoundedCornerShape(8.dp))
                        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = "Type multi-line shell command… (Shift+Enter for newline)",
                            color = palette.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { updated ->
                            textFieldValue = updated
                            onDraftChange(updated.text)
                        },
                        textStyle = TextStyle(
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(accent),
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("floating_edit_input")
                            .semantics { contentDescription = "Multi-line command input" }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    if (event.isShiftPressed) {
                                        insertNewlineAtCursor()
                                        true
                                    } else if (!event.isAltPressed) {
                                        if (textFieldValue.text.isNotBlank()) {
                                            onSend(textFieldValue.text)
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer action row: Newline helper chip, Clear, Send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WarpChip(
                            label = "↵ Newline",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            onClick = { insertNewlineAtCursor() },
                            modifier = Modifier
                                .testTag("floating_edit_newline")
                                .semantics { contentDescription = "Insert newline at cursor" }
                        )
                        if (textFieldValue.text.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    textFieldValue = TextFieldValue("")
                                    onDraftChange("")
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary),
                                modifier = Modifier.semantics { contentDescription = "Clear draft" }
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${textFieldValue.text.count { it == '\n' } + 1}L · ${textFieldValue.text.length}c",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (textFieldValue.text.isNotBlank()) accent else palette.surfaceInteractive,
                            modifier = Modifier
                                .clickable(enabled = textFieldValue.text.isNotBlank()) {
                                    onSend(textFieldValue.text)
                                }
                                .testTag("floating_edit_send")
                                .semantics { contentDescription = "Send full command" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = if (textFieldValue.text.isNotBlank()) palette.background else palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Send",
                                    color = if (textFieldValue.text.isNotBlank()) palette.background else palette.textMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
