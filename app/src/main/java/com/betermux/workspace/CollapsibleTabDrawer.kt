package com.betermux.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betermux.compose.MenuItemDivider
import com.betermux.compose.outlinedMenuCard
import com.betermux.theme.LocalWarpPalette
import com.betermux.theme.rememberReduceMotion

data class DrawerSessionItem(
    val handle: String,
    val title: String,
    val lastCommandSummary: String?,
    val isActive: Boolean,
    val isRunning: Boolean
)

data class WorkspaceDrawerItem(
    val id: String,
    val name: String,
    val rootPath: String,
    val sessionCount: Int,
    val sleepState: WorkspaceSleepState,
    val isActive: Boolean,
    val isDefault: Boolean
)

/**
 * Collapsible left-edge vertical navigation drawer presenting project workspaces
 * (with sleep/wake status and overflow actions), active terminal sessions,
 * quick-jump sessions from other workspaces, and overlay tools.
 */
@Composable
fun CollapsibleTabDrawer(
    visible: Boolean,
    sessions: List<DrawerSessionItem>,
    onDismiss: () -> Unit,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onOpenDevBrowser: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenLocalhost: () -> Unit,
    onOpenQuickCommands: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    workspaces: List<WorkspaceDrawerItem> = emptyList(),
    hibernationEnabled: Boolean = false,
    allWorkspaceSessions: Map<String, List<DrawerSessionItem>> = emptyMap(),
    onSelectWorkspace: (String) -> Unit = {},
    onAddWorkspace: (String, String) -> Unit = { _, _ -> },
    onToggleSleepWorkspace: (String) -> Unit = {},
    onRemoveWorkspace: (String, Boolean) -> Unit = { _, _ -> }
) {
    BackHandler(enabled = visible) {
        onDismiss()
    }

    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val reduceMotion = rememberReduceMotion()
    // Dock as a side panel at medium+ window width (>=600dp); modal scrim overlay in compact.
    val docked = LocalConfiguration.current.screenWidthDp >= 600

    var showAddWorkspaceDialog by remember { mutableStateOf(false) }
    var workspacePendingRemove by remember { mutableStateOf<WorkspaceDrawerItem?>(null) }

    val activeWorkspace = workspaces.firstOrNull { it.isActive }
    val scopedSessions = remember(workspaces, sessions, allWorkspaceSessions) {
        if (activeWorkspace != null && allWorkspaceSessions.containsKey(activeWorkspace.id)) {
            allWorkspaceSessions[activeWorkspace.id].orEmpty()
        } else {
            sessions
        }
    }
    val otherWorkspaceSessions = remember(workspaces, allWorkspaceSessions) {
        workspaces.filter { !it.isActive }.mapNotNull { ws ->
            val list = allWorkspaceSessions[ws.id].orEmpty()
            if (list.isEmpty()) null else ws to list
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (docked) {
                        // Docked: taps outside the panel fall through to the terminal.
                        Modifier
                    } else {
                        Modifier
                            .background(palette.background.copy(alpha = 0.62f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            )
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .animateEnterExit(
                        enter = if (reduceMotion) EnterTransition.None else slideInHorizontally { -it },
                        exit = if (reduceMotion) ExitTransition.None else slideOutHorizontally { -it }
                    )
                    .widthIn(min = 250.dp, max = 316.dp)
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
                // Header: Title + New Session + Collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeWorkspace?.name ?: "Workspace",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onNewSession(); onDismiss() },
                        modifier = Modifier
                            .size(48.dp)
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
                            .size(48.dp)
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

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("drawer_lazy_list"),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (workspaces.isNotEmpty()) {
                        item(key = "sec_workspaces") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "WORKSPACES (${workspaces.size})",
                                    color = palette.textMuted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                val addShape = RoundedCornerShape(6.dp)
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .clip(addShape)
                                        .background(palette.surfaceElevated, addShape)
                                        .border(1.dp, palette.borderSubtle, addShape)
                                        .clickable { showAddWorkspaceDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("drawer_add_workspace")
                                        .semantics { contentDescription = "Add project workspace" },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Add Project",
                                        color = palette.textPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        items(workspaces, key = { ws -> "ws_${ws.id}" }) { ws ->
                            WorkspaceRow(
                                item = ws,
                                hibernationEnabled = hibernationEnabled,
                                onSelect = {
                                    onSelectWorkspace(ws.id)
                                    onDismiss()
                                },
                                onToggleSleep = { onToggleSleepWorkspace(ws.id) },
                                onRequestRemove = { workspacePendingRemove = ws }
                            )
                        }

                        item(key = "sec_workspaces_spacer") {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    item(key = "sec_sessions") {
                        Text(
                            text = "TERMINAL SESSIONS (${scopedSessions.size})",
                            color = palette.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    itemsIndexed(scopedSessions, key = { _, item -> "sess_${item.handle}" }) { index, item ->
                        DrawerSessionCard(
                            index = index,
                            item = item,
                            onSelect = { onSelectSession(item.handle); onDismiss() },
                            onClose = { onCloseSession(item.handle) }
                        )
                    }

                    if (otherWorkspaceSessions.isNotEmpty()) {
                        item(key = "sec_other_workspaces") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "OTHER WORKSPACES",
                                color = palette.textMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }

                        otherWorkspaceSessions.forEach { (ws, wsSessions) ->
                            item(key = "other_hdr_${ws.id}") {
                                Text(
                                    text = "${ws.name} · ${wsSessions.size}",
                                    color = palette.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            itemsIndexed(
                                wsSessions,
                                key = { _, item -> "other_${ws.id}_${item.handle}" }
                            ) { index, item ->
                                DrawerSessionCard(
                                    index = index,
                                    item = item,
                                    onSelect = { onSelectSession(item.handle); onDismiss() },
                                    onClose = { onCloseSession(item.handle) }
                                )
                            }
                        }
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

                // Footer: Settings
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerToolRow(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        onClick = { onDismiss(); onOpenSettings() }
                    )
                }
            }
        }
    }

    if (showAddWorkspaceDialog) {
        AddProjectWorkspaceDialog(
            onDismiss = { showAddWorkspaceDialog = false },
            onConfirm = { name, rootPath ->
                showAddWorkspaceDialog = false
                onAddWorkspace(name, rootPath)
            }
        )
    }

    val removeTarget = workspacePendingRemove
    if (removeTarget != null) {
        RemoveWorkspaceDialog(
            workspace = removeTarget,
            onDismiss = { workspacePendingRemove = null },
            onConfirm = { terminateSessions ->
                workspacePendingRemove = null
                onRemoveWorkspace(removeTarget.id, terminateSessions)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceRow(
    item: WorkspaceDrawerItem,
    hibernationEnabled: Boolean,
    onSelect: () -> Unit,
    onToggleSleep: () -> Unit,
    onRequestRemove: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    val border = if (item.isActive) accent else palette.borderSubtle
    var menuExpanded by remember { mutableStateOf(false) }

    val effectiveSleepState = if (hibernationEnabled) item.sleepState else WorkspaceSleepState.AWAKE
    val sleepBadgeText: String?
    val sleepBadgeColor: Color
    when (effectiveSleepState) {
        WorkspaceSleepState.SLEEPING -> {
            sleepBadgeText = "zzz"
            sleepBadgeColor = palette.textSecondary
        }
        WorkspaceSleepState.SUSPENDING -> {
            sleepBadgeText = "Suspending…"
            sleepBadgeColor = palette.warning
        }
        WorkspaceSleepState.RESUMING -> {
            sleepBadgeText = "Waking…"
            sleepBadgeColor = palette.info
        }
        WorkspaceSleepState.ERROR -> {
            sleepBadgeText = "Error"
            sleepBadgeColor = palette.error
        }
        WorkspaceSleepState.AWAKE -> {
            sleepBadgeText = null
            sleepBadgeColor = palette.textMuted
        }
    }

    val showOverflowMenu = hibernationEnabled || !item.isDefault
    val stateLabel = sleepBadgeText ?: "Awake"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (item.isActive) palette.surfaceElevated else palette.surface, shape)
            .border(1.dp, border, shape)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { if (showOverflowMenu) menuExpanded = true }
            )
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
            .testTag("workspace_row_${item.id}")
            .semantics {
                contentDescription = "Workspace ${item.name}, ${item.sessionCount} sessions, $stateLabel"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
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
                    text = item.name,
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (item.isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.surfaceInteractive)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.sessionCount.toString(),
                        color = palette.textSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (sleepBadgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(sleepBadgeColor.copy(alpha = 0.14f))
                            .border(1.dp, sleepBadgeColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                            .testTag("workspace_sleep_badge_${item.id}")
                    ) {
                        Text(
                            text = sleepBadgeText,
                            color = sleepBadgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = item.rootPath,
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hibernationEnabled && item.sleepState == WorkspaceSleepState.SLEEPING) {
                Text(
                    text = "Remote SSH sessions may drop during long sleep",
                    color = palette.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showOverflowMenu) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("workspace_overflow_${item.id}")
                        .semantics { contentDescription = "Workspace actions for ${item.name}" }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.outlinedMenuCard()
                ) {
                    if (hibernationEnabled) {
                        val shouldShowWake =
                            item.sleepState == WorkspaceSleepState.SLEEPING ||
                                item.sleepState == WorkspaceSleepState.SUSPENDING ||
                                item.sleepState == WorkspaceSleepState.ERROR
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(if (shouldShowWake) "Wake" else "Sleep")
                                    if (!shouldShowWake) {
                                        Text(
                                            text = "Remote SSH sessions may drop during long sleep",
                                            color = palette.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleSleep()
                            }
                        )
                    }
                    if (!item.isDefault) {
                        if (hibernationEnabled) {
                            MenuItemDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Remove", color = palette.error) },
                            onClick = {
                                menuExpanded = false
                                onRequestRemove()
                            }
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun WarpModalCard(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(enabled = true) {
        onDismiss()
    }
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 380.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surfaceElevated, shape)
                .border(1.dp, palette.borderStrong, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dismissButton()
                Spacer(modifier = Modifier.width(8.dp))
                confirmButton()
            }
        }
    }
}

@Composable
private fun WarpLabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    isError: Boolean = false,
    monospace: Boolean = false
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surface, shape)
                .border(1.dp, if (isError) palette.error else palette.borderStrong, shape)
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = palette.textMuted,
                    fontSize = 13.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(accent),
                textStyle = TextStyle(
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
                    .semantics { contentDescription = label }
            )
        }
    }
}

@Composable
private fun AddProjectWorkspaceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, rootPath: String) -> Unit
) {
    val palette = LocalWarpPalette.current
    var name by remember { mutableStateOf("") }
    var rootPath by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    WarpModalCard(
        title = "Add Project Workspace",
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedPath = rootPath.trim()
                    if (trimmedPath.isEmpty()) {
                        validationError = "Project directory path is required"
                    } else {
                        onConfirm(name.trim(), trimmedPath)
                    }
                },
                modifier = Modifier
                    .testTag("add_workspace_confirm")
                    .semantics { contentDescription = "Create workspace" }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .testTag("add_workspace_cancel")
                    .semantics { contentDescription = "Cancel add workspace" }
            ) {
                Text("Cancel")
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WarpLabeledTextField(
                label = "Workspace name",
                value = name,
                onValueChange = { name = it },
                placeholder = "e.g. API Server",
                testTag = "add_workspace_name_input"
            )
            WarpLabeledTextField(
                label = "Project directory path",
                value = rootPath,
                onValueChange = {
                    rootPath = it
                    if (validationError != null) validationError = null
                },
                placeholder = "/data/data/com.termux/files/home/project",
                testTag = "add_workspace_path_input",
                isError = validationError != null,
                monospace = true
            )
            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    color = palette.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RemoveWorkspaceDialog(
    workspace: WorkspaceDrawerItem,
    onDismiss: () -> Unit,
    onConfirm: (terminateSessions: Boolean) -> Unit
) {
    val palette = LocalWarpPalette.current
    var terminateSessions by remember { mutableStateOf(false) }

    WarpModalCard(
        title = "Remove Workspace",
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(terminateSessions) },
                modifier = Modifier
                    .testTag("remove_workspace_confirm")
                    .semantics { contentDescription = "Confirm remove workspace" }
            ) {
                Text("Remove", color = palette.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .testTag("remove_workspace_cancel")
                    .semantics { contentDescription = "Cancel remove workspace" }
            ) {
                Text("Cancel")
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Remove \"${workspace.name}\"? Existing terminal sessions will move to Default. Your project directory on disk (${workspace.rootPath}) will never be deleted.",
                fontSize = 13.sp,
                color = palette.textSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { terminateSessions = !terminateSessions }
            ) {
                Checkbox(
                    checked = terminateSessions,
                    onCheckedChange = { terminateSessions = it },
                    modifier = Modifier
                        .testTag("remove_workspace_terminate_checkbox")
                        .semantics { contentDescription = "Terminate running sessions instead" }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Terminate running sessions instead",
                    fontSize = 13.sp,
                    color = palette.textPrimary
                )
            }
        }
    }
}

@Composable
private fun DrawerSessionCard(
    index: Int,
    item: DrawerSessionItem,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    val border = if (item.isActive) accent else palette.borderSubtle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (item.isActive) palette.surfaceElevated else palette.surface, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
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
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(48.dp)
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
