package com.betermux.workspace

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.termux.app.activities.FileCategory
import com.termux.app.activities.categorizeFile
import kotlinx.coroutines.launch
import java.io.File

private enum class CreateEntryKind {
    FILE,
    FOLDER
}

private data class PendingCreateTarget(
    val parentDir: File,
    val kind: CreateEntryKind
)

/**
 * Right-edge workspace project file explorer with lazy directory expansion,
 * symlink cycle/depth guards, inline node loading/error states, and friendly
 * recoverable root error cards.
 */
@Composable
fun RightFileDrawer(
    workspaceName: String,
    rootPath: String,
    onClose: () -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenExternally: (File) -> Unit,
    onCopyPath: (String) -> Unit,
    onOpenTerminalHere: (String) -> Unit,
    onOpenFullFileManager: () -> Unit,
    onOpenHomeRoot: (() -> Unit)? = null,
    onSetupStorageHint: (() -> Unit)? = null,
    initialShowHidden: Boolean = false
) {
    val context = LocalContext.current
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    rememberReduceMotion()

    var showHidden by remember { mutableStateOf(initialShowHidden) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var rootNodes by remember(rootPath) { mutableStateOf<List<FileTreeNode>>(emptyList()) }
    var rootLoading by remember(rootPath) { mutableStateOf(false) }
    var rootError by remember(rootPath) { mutableStateOf<FileTreeError?>(null) }
    var pendingCreate by remember { mutableStateOf<PendingCreateTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<FileTreeNode?>(null) }

    suspend fun loadRootPreservingExpanded(expandedSnapshot: Set<String>) {
        if (rootPath.isBlank()) {
            rootNodes = emptyList()
            rootLoading = false
            rootError = FileTreeError(
                type = FileTreeErrorType.ENOENT,
                message = "Workspace root path is empty.",
                path = rootPath
            )
            return
        }
        rootLoading = true
        rootError = null
        val rootFile = File(rootPath)
        val rootAncestors = buildSet {
            FileTreeRepository.canonicalPathOrNull(rootFile)?.let { add(it) }
        }
        val rootResult = FileTreeRepository.loadChildren(
            dir = rootFile,
            depth = 0,
            showHidden = showHidden,
            ancestorCanonicalPaths = rootAncestors,
            context = context
        )
        rootResult.fold(
            onSuccess = { loaded ->
                rootNodes = reloadExpandedSubtrees(
                    nodes = loaded,
                    expandedPaths = expandedSnapshot,
                    showHidden = showHidden,
                    ancestorCanonicalPaths = rootAncestors,
                    context = context
                )
                rootError = null
            },
            onFailure = { err ->
                rootNodes = emptyList()
                rootError = (err as? FileTreeException)?.error ?: FileTreeError(
                    type = FileTreeErrorType.IO_ERROR,
                    message = err.message ?: "Unable to read directory",
                    path = rootPath
                )
            }
        )
        rootLoading = false
    }

    LaunchedEffect(rootPath, showHidden, reloadNonce) {
        val expandedSnapshot = collectExpandedPaths(rootNodes)
        loadRootPreservingExpanded(expandedSnapshot)
    }

    fun expandOrRetryNode(node: FileTreeNode) {
        if (!node.isDirectory) return
        if (node.isExpanded && node.error == null) {
            rootNodes = updateTreeNode(rootNodes, node.path) {
                it.copy(isExpanded = false, isLoading = false, error = null)
            }
            return
        }
        if (node.symlinkCycleDetected) {
            rootNodes = updateTreeNode(rootNodes, node.path) {
                it.copy(isExpanded = !it.isExpanded, isLoading = false)
            }
            return
        }
        val ancestors = collectAncestorCanonicalPaths(rootPath, rootNodes, node.path)
        rootNodes = updateTreeNode(rootNodes, node.path) {
            it.copy(isExpanded = true, isLoading = true, error = null)
        }
        scope.launch {
            val result = FileTreeRepository.loadChildren(
                dir = File(node.path),
                depth = node.depth + 1,
                showHidden = showHidden,
                ancestorCanonicalPaths = ancestors,
                context = context
            )
            result.fold(
                onSuccess = { loadedChildren ->
                    rootNodes = updateTreeNode(rootNodes, node.path) {
                        it.copy(
                            isExpanded = true,
                            isLoading = false,
                            error = null,
                            children = loadedChildren
                        )
                    }
                },
                onFailure = { err ->
                    val treeErr = (err as? FileTreeException)?.error ?: FileTreeError(
                        type = FileTreeErrorType.IO_ERROR,
                        message = err.message ?: "Unable to read directory",
                        path = node.path
                    )
                    rootNodes = updateTreeNode(rootNodes, node.path) {
                        it.copy(
                            isExpanded = true,
                            isLoading = false,
                            error = treeErr
                        )
                    }
                }
            )
        }
    }

    val visibleNodes = remember(rootNodes) { flattenVisibleTree(rootNodes) }
    val shape = RoundedCornerShape(8.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Header: Title + Workspace + Reload + Close (48dp touch targets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Project Files",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = workspaceName,
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { reloadNonce += 1 },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("right_drawer_reload")
                    .semantics { contentDescription = "Reload project files" }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("right_drawer_close")
                    .semantics { contentDescription = "Close project files drawer" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Compact root path display
        Text(
            text = rootPath.ifBlank { "~" },
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(palette.surfaceElevated, shape)
                .border(BorderStroke(1.dp, palette.borderSubtle), shape)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )

        // Toolbar: Show Hidden, + File, + Folder, Full Manager (48dp min touch targets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { showHidden = !showHidden },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("right_drawer_toggle_hidden")
                    .semantics {
                        contentDescription = if (showHidden) "Hide hidden files" else "Show hidden files"
                    }
            ) {
                Icon(
                    imageVector = if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = if (showHidden) accent else palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerToolbarChip(
                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                    label = "+ File",
                    contentDescription = "New file in project root",
                    testTag = "right_drawer_create_file",
                    enabled = rootError == null && rootPath.isNotBlank(),
                    onClick = {
                        pendingCreate = PendingCreateTarget(File(rootPath), CreateEntryKind.FILE)
                    }
                )
                DrawerToolbarChip(
                    icon = Icons.Default.CreateNewFolder,
                    label = "+ Folder",
                    contentDescription = "New folder in project root",
                    testTag = "right_drawer_create_folder",
                    enabled = rootError == null && rootPath.isNotBlank(),
                    onClick = {
                        pendingCreate = PendingCreateTarget(File(rootPath), CreateEntryKind.FOLDER)
                    }
                )
            }

            IconButton(
                onClick = onOpenFullFileManager,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("right_drawer_open_file_manager")
                    .semantics { contentDescription = "Open full File Manager" }
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)

        // Body: loading / root error card / empty / lazy file tree
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                rootLoading && visibleNodes.isEmpty() -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("right_drawer_root_loading"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accent
                        )
                        Text(
                            text = "Loading project files…",
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                rootError != null -> {
                    RootErrorCard(
                        error = rootError!!,
                        onRetry = { reloadNonce += 1 },
                        onOpenFullFileManager = onOpenFullFileManager,
                        onOpenHomeRoot = onOpenHomeRoot,
                        onSetupStorageHint = onSetupStorageHint
                    )
                }
                visibleNodes.isEmpty() -> {
                    EmptyDirectoryCard(
                        showHidden = showHidden,
                        onShowHidden = { showHidden = true },
                        onCreateFile = {
                            if (rootPath.isNotBlank()) {
                                pendingCreate = PendingCreateTarget(File(rootPath), CreateEntryKind.FILE)
                            }
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("right_drawer_file_list"),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = visibleNodes,
                            key = { it.path }
                        ) { node ->
                            FileTreeRow(
                                node = node,
                                rootPath = rootPath,
                                onToggleDir = { expandOrRetryNode(node) },
                                onOpenFile = { onOpenFile(File(node.path)) },
                                onOpenExternally = { onOpenExternally(File(node.path)) },
                                onCopyPath = { onCopyPath(node.path) },
                                onOpenTerminalHere = { dirPath -> onOpenTerminalHere(dirPath) },
                                onCreateInDir = { kind ->
                                    pendingCreate = PendingCreateTarget(File(node.path), kind)
                                },
                                onDelete = { pendingDelete = node }
                            )

                            if (node.isDirectory && node.isExpanded) {
                                val childIndent = ((node.depth + 1).coerceAtMost(8) * 14 + 26).dp
                                when {
                                    node.isLoading -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = childIndent, top = 2.dp, bottom = 4.dp)
                                                .testTag("file_tree_loading_${node.path}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = accent
                                            )
                                            Text(
                                                text = "Loading…",
                                                color = palette.textMuted,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    }
                                    node.error != null -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = childIndent, end = 6.dp, top = 2.dp, bottom = 4.dp)
                                                .background(palette.surfaceElevated, RoundedCornerShape(6.dp))
                                                .border(1.dp, palette.error.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .testTag("file_tree_error_${node.path}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = palette.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = node.error.message,
                                                color = palette.textSecondary,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            TextButton(
                                                onClick = { expandOrRetryNode(node) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Text("Retry", fontSize = 11.5.sp)
                                            }
                                        }
                                    }
                                    node.symlinkCycleDetected -> {
                                        Text(
                                            text = "Symlink cycle or max depth (32) reached",
                                            color = palette.warning,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .padding(start = childIndent, top = 2.dp, bottom = 4.dp)
                                                .testTag("file_tree_cycle_notice_${node.path}")
                                        )
                                    }
                                    node.children.isEmpty() -> {
                                        Text(
                                            text = "(empty)",
                                            color = palette.textMuted,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(start = childIndent, top = 2.dp, bottom = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val createTarget = pendingCreate
    if (createTarget != null) {
        CreateEntryDialog(
            target = createTarget,
            onDismiss = { pendingCreate = null },
            onCreate = { name ->
                val result = when (createTarget.kind) {
                    CreateEntryKind.FILE -> FileTreeRepository.createFile(createTarget.parentDir, name)
                    CreateEntryKind.FOLDER -> FileTreeRepository.createDirectory(createTarget.parentDir, name)
                }
                if (result.isSuccess) {
                    val parentPath = createTarget.parentDir.absolutePath
                    if (parentPath != rootPath) {
                        rootNodes = updateTreeNode(rootNodes, parentPath) { it.copy(isExpanded = true) }
                    }
                    pendingCreate = null
                    reloadNonce += 1
                }
                result
            }
        )
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        DeleteEntryDialog(
            node = deleteTarget,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch {
                    FileTreeRepository.deleteEntry(File(deleteTarget.path))
                    pendingDelete = null
                    reloadNonce += 1
                }
            }
        )
    }
    }
}

@Composable
private fun DrawerToolbarChip(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .background(palette.surfaceElevated, shape)
                .border(1.dp, palette.borderSubtle, shape)
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) palette.textPrimary else palette.textMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = if (enabled) palette.textPrimary else palette.textMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(
    node: FileTreeNode,
    rootPath: String,
    onToggleDir: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenExternally: () -> Unit,
    onCopyPath: () -> Unit,
    onOpenTerminalHere: (String) -> Unit,
    onCreateInDir: (CreateEntryKind) -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(6.dp)
    var menuExpanded by remember { mutableStateOf(false) }

    val fileObj = remember(node.path) { File(node.path) }
    val category = remember(node.path, node.isDirectory) {
        if (node.isDirectory) FileCategory.FOLDER else categorizeFile(fileObj)
    }
    val openLabel = remember(category) {
        when (category) {
            FileCategory.MARKDOWN, FileCategory.HTML, FileCategory.CODE_TEXT -> "Edit / View"
            FileCategory.FOLDER -> "Open Folder Tab"
            else -> "View"
        }
    }
    val indent = (node.depth.coerceAtMost(8) * 14).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(shape)
            .background(
                if (node.isDirectory && node.isExpanded) palette.surfaceElevated else palette.surface,
                shape
            )
            .border(
                width = 1.dp,
                color = if (node.isDirectory && node.isExpanded) palette.borderSubtle else palette.surface,
                shape = shape
            )
            .combinedClickable(
                onClick = {
                    if (node.isDirectory) onToggleDir() else onOpenFile()
                },
                onLongClick = { menuExpanded = true }
            )
            .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .testTag("file_tree_row_${node.path}")
            .semantics {
                contentDescription = buildString {
                    append(if (node.isDirectory) "Folder " else "File ")
                    append(node.name)
                    if (node.isSymlink) append(", symlink")
                    if (node.symlinkCycleDetected) append(", cycle detected")
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDirectory) {
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = iconForNode(node, category),
            contentDescription = null,
            tint = if (node.isDirectory) accent else palette.textSecondary,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = node.name,
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (node.isSymlink) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Symlink",
                tint = palette.info,
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(13.dp)
            )
        }

        if (node.symlinkCycleDetected) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .background(palette.warning.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                    .border(1.dp, palette.warning.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag("file_tree_cycle_${node.path}")
            ) {
                Text(
                    text = "Cycle",
                    color = palette.warning,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (node.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(13.dp),
                strokeWidth = 1.5.dp,
                color = accent
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("file_tree_menu_${node.path}")
                    .semantics { contentDescription = "Actions for ${node.name}" }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.outlinedMenuCard()
            ) {
                DropdownMenuItem(
                    text = { Text(openLabel) },
                    onClick = {
                        menuExpanded = false
                        onOpenFile()
                    }
                )
                if (!node.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Open Externally") },
                        onClick = {
                            menuExpanded = false
                            onOpenExternally()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("New File Here") },
                        onClick = {
                            menuExpanded = false
                            onCreateInDir(CreateEntryKind.FILE)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Folder Here") },
                        onClick = {
                            menuExpanded = false
                            onCreateInDir(CreateEntryKind.FOLDER)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy Path") },
                    onClick = {
                        menuExpanded = false
                        onCopyPath()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Open Terminal Here") },
                    onClick = {
                        menuExpanded = false
                        val terminalDir = if (node.isDirectory) {
                            node.path
                        } else {
                            fileObj.parent ?: rootPath
                        }
                        onOpenTerminalHere(terminalDir)
                    }
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = palette.error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun iconForNode(node: FileTreeNode, category: FileCategory): ImageVector {
    if (node.isDirectory) {
        return if (node.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
    }
    return when (category) {
        FileCategory.CODE_TEXT -> Icons.Default.Code
        FileCategory.MARKDOWN, FileCategory.HTML, FileCategory.DOCX,
        FileCategory.PPTX, FileCategory.SPREADSHEET -> Icons.Default.Description
        FileCategory.IMAGE -> Icons.Default.Image
        FileCategory.VIDEO -> Icons.Default.VideoFile
        FileCategory.AUDIO -> Icons.Default.AudioFile
        FileCategory.PDF -> Icons.Default.PictureAsPdf
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

@Composable
private fun RootErrorCard(
    error: FileTreeError,
    onRetry: () -> Unit,
    onOpenFullFileManager: () -> Unit,
    onOpenHomeRoot: (() -> Unit)?,
    onSetupStorageHint: (() -> Unit)?
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(10.dp)
    val title = when (error.type) {
        FileTreeErrorType.ENOENT -> "Directory not found"
        FileTreeErrorType.EACCES -> "Permission denied"
        FileTreeErrorType.UNMOUNTED_STORAGE -> "Storage unmounted"
        FileTreeErrorType.STORAGE_PERMISSION_MISSING -> "Storage permission required"
        FileTreeErrorType.IO_ERROR -> "Unable to read directory"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(palette.surfaceElevated, shape)
            .border(1.dp, palette.error.copy(alpha = 0.55f), shape)
            .padding(14.dp)
            .testTag("right_drawer_error_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = palette.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = error.message,
            color = palette.textSecondary,
            fontSize = 12.5.sp
        )
        if (error.type == FileTreeErrorType.STORAGE_PERMISSION_MISSING) {
            Text(
                text = "Tip: Run `termux-setup-storage` in a terminal or grant storage access.",
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ErrorActionButton(label = "Retry", onClick = onRetry)
            ErrorActionButton(label = "Open File Manager", onClick = onOpenFullFileManager)
        }
        if (error.type == FileTreeErrorType.STORAGE_PERMISSION_MISSING && onSetupStorageHint != null) {
            ErrorActionButton(label = "Grant Storage / Setup", onClick = onSetupStorageHint)
        } else if (error.type == FileTreeErrorType.ENOENT && onOpenHomeRoot != null) {
            ErrorActionButton(label = "Open Home", onClick = onOpenHomeRoot)
        }
    }
}

@Composable
private fun ErrorActionButton(
    label: String,
    onClick: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(palette.surfaceInteractive, shape)
            .border(1.dp, palette.borderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyDirectoryCard(
    showHidden: Boolean,
    onShowHidden: () -> Unit,
    onCreateFile: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(palette.surfaceElevated, shape)
            .border(1.dp, palette.borderSubtle, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (showHidden) "This folder is empty." else "No visible files in this folder.",
            color = palette.textSecondary,
            fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!showHidden) {
                ErrorActionButton(label = "Show Hidden", onClick = onShowHidden)
            }
            ErrorActionButton(label = "New File", onClick = onCreateFile)
        }
    }
}

@Composable
private fun CreateEntryDialog(
    target: PendingCreateTarget,
    onDismiss: () -> Unit,
    onCreate: suspend (String) -> Result<File>
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(12.dp)
    val fieldShape = RoundedCornerShape(8.dp)
    val title = if (target.kind == CreateEntryKind.FILE) "New File" else "New Folder"

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 320.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surfaceElevated, shape)
                .border(1.dp, palette.borderStrong, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = target.parentDir.absolutePath,
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(palette.surface, fieldShape)
                    .border(1.dp, if (errorMsg != null) palette.error else palette.borderStrong, fieldShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = if (target.kind == CreateEntryKind.FILE) "e.g. notes.md" else "e.g. components",
                        color = palette.textMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (errorMsg != null) errorMsg = null
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(accent),
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_entry_name_input")
                )
            }
            if (errorMsg != null) {
                Text(
                    text = errorMsg.orEmpty(),
                    color = palette.error,
                    fontSize = 12.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            val res = onCreate(name)
                            if (res.isFailure) {
                                errorMsg = res.exceptionOrNull()?.message ?: "Failed to create"
                            }
                        }
                    },
                    modifier = Modifier.testTag("create_entry_confirm")
                ) {
                    Text("Create")
                }
            }
        }
    }
}

@Composable
private fun DeleteEntryDialog(
    node: FileTreeNode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(12.dp)
    val detail = when {
        node.isSymlink -> "Remove symbolic link \"${node.name}\"? The link target on disk will not be deleted."
        node.isDirectory -> "Delete folder \"${node.name}\" and all of its contents? This cannot be undone."
        else -> "Delete file \"${node.name}\"? This cannot be undone."
    }

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 320.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surfaceElevated, shape)
                .border(1.dp, palette.borderStrong, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Delete ${if (node.isDirectory) "Folder" else "File"}",
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                color = palette.textSecondary,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("delete_entry_confirm")
                ) {
                    Text("Delete", color = palette.error)
                }
            }
        }
    }
}

private fun collectExpandedPaths(nodes: List<FileTreeNode>): Set<String> {
    if (nodes.isEmpty()) return emptySet()
    val expanded = LinkedHashSet<String>()
    fun walk(list: List<FileTreeNode>) {
        for (node in list) {
            if (node.isDirectory && node.isExpanded) {
                expanded.add(node.path)
                walk(node.children)
            }
        }
    }
    walk(nodes)
    return expanded
}

private suspend fun reloadExpandedSubtrees(
    nodes: List<FileTreeNode>,
    expandedPaths: Set<String>,
    showHidden: Boolean,
    ancestorCanonicalPaths: Set<String>,
    context: android.content.Context
): List<FileTreeNode> {
    if (expandedPaths.isEmpty()) return nodes
    return nodes.map { node ->
        if (node.isDirectory && node.path in expandedPaths && !node.symlinkCycleDetected) {
            val childCanonical = FileTreeRepository.canonicalPathOrNull(File(node.path))
            val nextAncestors = if (childCanonical != null) {
                ancestorCanonicalPaths + childCanonical
            } else {
                ancestorCanonicalPaths
            }
            val result = FileTreeRepository.loadChildren(
                dir = File(node.path),
                depth = node.depth + 1,
                showHidden = showHidden,
                ancestorCanonicalPaths = nextAncestors,
                context = context
            )
            result.fold(
                onSuccess = { loadedChildren ->
                    node.copy(
                        isExpanded = true,
                        isLoading = false,
                        error = null,
                        children = reloadExpandedSubtrees(
                            nodes = loadedChildren,
                            expandedPaths = expandedPaths,
                            showHidden = showHidden,
                            ancestorCanonicalPaths = nextAncestors,
                            context = context
                        )
                    )
                },
                onFailure = { err ->
                    val treeErr = (err as? FileTreeException)?.error ?: FileTreeError(
                        type = FileTreeErrorType.IO_ERROR,
                        message = err.message ?: "Unable to read directory",
                        path = node.path
                    )
                    node.copy(isExpanded = true, isLoading = false, error = treeErr)
                }
            )
        } else {
            node
        }
    }
}
