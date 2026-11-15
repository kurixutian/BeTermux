package com.newtermux.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.features.NewTermuxSettings
import com.newtermux.theme.LocalWarpPalette
import com.newtermux.theme.WarpChip
import com.newtermux.theme.rememberReduceMotion

data class DrawerSessionItem(
    val handle: String,
    val title: String,
    val lastCommandSummary: String?,
    val isActive: Boolean,
    val isRunning: Boolean,
    val isSplitCompanion: Boolean
)

/**
 * Collapsible left-edge vertical navigation drawer presenting active terminal sessions
 * (title, last command summary, split toggle, close routed via process/draft guard),
 * overlay workspaces (Dev Browser, File Manager, Localhost), and UI Zoom (85%–130%).
 */
@Composable
fun CollapsibleTabDrawer(
    visible: Boolean,
    sessions: List<DrawerSessionItem>,
    splitActive: Boolean,
    zoomPercent: Int,
    onDismiss: () -> Unit,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onToggleSplit: (String?) -> Unit,
    onOpenDevBrowser: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenLocalhost: () -> Unit,
    onOpenQuickCommands: () -> Unit,
    onOpenSettings: () -> Unit,
    onChangeZoomPercent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    BackHandler(enabled = true) {
        onDismiss()
    }

    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val reduceMotion = rememberReduceMotion()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reduceMotion) EnterTransition.None else slideInHorizontally { -it },
            exit = if (reduceMotion) ExitTransition.None else slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 250.dp, max = 304.dp)
                    .fillMaxHeight()
                    .background(palette.surface)
                    .border(BorderStroke(1.dp, palette.borderSubtle))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("collapsible_tab_drawer")
            ) {
                // Header: Title + New Session + Split + Collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Workspace",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onToggleSplit(null) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("drawer_toggle_split")
                            .semantics {
                                contentDescription = if (splitActive) "Exit split pane mode" else "Split active pane"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = null,
                            tint = if (splitActive) accent else palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onNewSession(); onDismiss() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("drawer_new_session")
                            .semantics { contentDescription = "Create new terminal session" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("drawer_close_button")
                            .semantics { contentDescription = "Collapse workspace drawer" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)

                // Sessions list
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "sec_sessions") {
                        Text(
                            text = "TERMINAL SESSIONS (${sessions.size})",
                            color = palette.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    itemsIndexed(sessions, key = { _, item -> "sess_${item.handle}" }) { index, item ->
                        DrawerSessionCard(
                            index = index,
                            item = item,
                            onSelect = { onSelectSession(item.handle); onDismiss() },
                            onSplit = { onToggleSplit(item.handle); onDismiss() },
                            onClose = { onCloseSession(item.handle) }
                        )
                    }

                    item(key = "sec_tools") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TABS & TOOLS",
                            color = palette.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    item(key = "tool_browser") {
                        DrawerToolRow(
                            icon = Icons.Default.Language,
                            label = "Dev Browser",
                            onClick = { onOpenDevBrowser(); onDismiss() }
                        )
                    }
                    item(key = "tool_files") {
                        DrawerToolRow(
                            icon = Icons.Default.FolderOpen,
                            label = "File Manager",
                            onClick = { onOpenFileManager(); onDismiss() }
                        )
                    }
                    item(key = "tool_localhost") {
                        DrawerToolRow(
                            icon = Icons.Default.Dns,
                            label = "Localhost Servers",
                            onClick = { onOpenLocalhost(); onDismiss() }
                        )
                    }
                    item(key = "tool_quick") {
                        DrawerToolRow(
                            icon = Icons.Default.Bolt,
                            label = "Quick Commands",
                            onClick = { onDismiss(); onOpenQuickCommands() }
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)

                // Footer: UI Zoom (85%-130%) + Settings
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "UI Zoom",
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    onChangeZoomPercent((zoomPercent - 5).coerceAtLeast(NewTermuxSettings.UI_ZOOM_MIN_PERCENT))
                                },
                                enabled = zoomPercent > NewTermuxSettings.UI_ZOOM_MIN_PERCENT,
                                modifier = Modifier
                                    .size(28.dp)
                                    .semantics { contentDescription = "Decrease UI zoom" }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                            }
                            WarpChip(
                                label = "$zoomPercent%",
                                onClick = { onChangeZoomPercent(NewTermuxSettings.UI_ZOOM_DEFAULT_PERCENT) },
                                modifier = Modifier.semantics { contentDescription = "Reset UI zoom to 100%" }
                            )
                            IconButton(
                                onClick = {
                                    onChangeZoomPercent((zoomPercent + 5).coerceAtMost(NewTermuxSettings.UI_ZOOM_MAX_PERCENT))
                                },
                                enabled = zoomPercent < NewTermuxSettings.UI_ZOOM_MAX_PERCENT,
                                modifier = Modifier
                                    .size(28.dp)
                                    .semantics { contentDescription = "Increase UI zoom" }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    DrawerToolRow(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = { onDismiss(); onOpenSettings() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSessionCard(
    index: Int,
    item: DrawerSessionItem,
    onSelect: () -> Unit,
    onSplit: () -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    val border = when {
        item.isActive -> accent
        item.isSplitCompanion -> palette.info.copy(alpha = 0.7f)
        else -> palette.borderSubtle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (item.isActive) palette.surfaceElevated else palette.surface, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("drawer_session_${item.handle}")
            .semantics {
                contentDescription = "Session ${index + 1}: ${item.title}, last command ${item.lastCommandSummary ?: "none"}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = if (item.isActive) accent else palette.textMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title.ifBlank { "Session ${index + 1}" },
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (item.isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isRunning) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.success)
                    )
                }
            }
            Text(
                text = item.lastCommandSummary?.takeIf { it.isNotBlank() } ?: "Idle shell",
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!item.isActive) {
            IconButton(
                onClick = onSplit,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Open ${item.title} in split pane" }
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalSplit,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(28.dp)
                .testTag("drawer_close_session_${item.handle}")
                .semantics { contentDescription = "Close session ${item.title}" }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun DrawerToolRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surfaceElevated, shape)
            .border(1.dp, palette.borderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        Text(text = label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
