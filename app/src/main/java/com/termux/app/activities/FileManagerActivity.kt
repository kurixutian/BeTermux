package com.termux.app.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.termux.app.TermuxActivity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.newtermux.compose.MenuItemDivider
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.compose.outlinedMenuCard
import com.termux.shared.android.PermissionUtils
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * In-app file browser, viewer, and full-screen text/code editor, scoped to the Termux files tree.
 * Allows direct opening, viewing, editing, and previewing of files without leaving the application.
 */
class FileManagerActivity : AppCompatActivity() {

    private val resumeRefreshKey = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeRefreshKey.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewTermuxComposeTheme(this) {
                FileManagerScreen(
                    onExit = { finish() },
                    resumeRefreshKey = resumeRefreshKey.intValue
                )
            }
        }
    }
}

private const val MAX_TEXT_SIZE = 2L * 1024 * 1024 // 2 MB text file editing limit

private val CODE_EXTENSIONS = setOf(
    "sh", "bash", "zsh", "py", "js", "ts", "java", "kt", "c", "cpp", "h", "hpp",
    "rs", "go", "rb", "php", "html", "css", "xml", "json", "yaml", "yml", "toml",
    "sql", "vim", "lua", "gradle", "properties", "conf", "cfg", "ini", "env", "rc"
)

private val TEXT_EXTENSIONS = CODE_EXTENSIONS + setOf(
    "txt", "md", "markdown", "log", "csv", "tsv", "diff", "patch"
)

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico"
)

enum class FileSortOption(val title: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Date modified (Newest)"),
    DATE_ASC("Date modified (Oldest)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    TYPE_ASC("Type (Extension A to Z)")
}

private const val PREFS_FILE_MANAGER = "newtermux_file_manager"
private const val PREF_KEY_SORT = "sort_option"
private const val PREF_KEY_SHOW_HIDDEN = "show_hidden"
private const val PREF_KEY_FOLDERS_FIRST = "folders_first"

@Composable
private fun FileManagerScreen(
    onExit: () -> Unit,
    resumeRefreshKey: Int = 0,
) {
    val context = LocalContext.current
    var activeFile by remember { mutableStateOf<File?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    if (activeFile != null) {
        FileViewerScreen(
            file = activeFile!!,
            onClose = { activeFile = null },
            onFileChanged = { refreshKey++ }
        )
    } else {
        FileBrowserScreen(
            onExit = onExit,
            onOpenFile = { file ->
                val intent = Intent(context, TermuxActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("OPEN_FILE_PATH", file.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            },
            refreshKey = refreshKey + resumeRefreshKey,
            onRefresh = { refreshKey++ }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileBrowserScreen(
    onExit: () -> Unit,
    onOpenFile: (File) -> Unit,
    refreshKey: Int,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val homeDir = remember {
        val termuxHome = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        if (termuxHome.exists() && termuxHome.canRead()) {
            termuxHome
        } else {
            File(context.filesDir, "home").apply { if (!exists()) mkdirs() }
        }
    }
    val sdcardDir = remember { Environment.getExternalStorageDirectory() }
    val downloadsDir = remember { File(sdcardDir, "Download") }
    val documentsDir = remember { File(sdcardDir, "Documents") }

    val hasStoragePermission = remember(refreshKey) {
        PermissionUtils.checkStoragePermission(context, false)
    }

    val prefs = remember { context.getSharedPreferences(PREFS_FILE_MANAGER, Context.MODE_PRIVATE) }
    var sortOption by remember {
        val saved = prefs.getString(PREF_KEY_SORT, FileSortOption.NAME_ASC.name)
        mutableStateOf(try { FileSortOption.valueOf(saved ?: "") } catch (e: Exception) { FileSortOption.NAME_ASC })
    }
    var showHidden by remember {
        mutableStateOf(prefs.getBoolean(PREF_KEY_SHOW_HIDDEN, true))
    }
    var foldersFirst by remember {
        mutableStateOf(prefs.getBoolean(PREF_KEY_FOLDERS_FIRST, true))
    }
    var sortDialogOpen by remember { mutableStateOf(false) }

    var currentDir by remember { mutableStateOf(homeDir) }
    val files = remember(currentDir, refreshKey, sortOption, showHidden, foldersFirst) {
        listDir(currentDir, sortOption, showHidden, foldersFirst)
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val displayedFiles = remember(files, searchQuery, isSearchActive) {
        if (!isSearchActive || searchQuery.isBlank()) {
            files
        } else {
            val q = searchQuery.trim().lowercase()
            files.filter { it.name.lowercase().contains(q) }
        }
    }

    var overflowOpen by remember { mutableStateOf(false) }
    var optionsFile by remember { mutableStateOf<File?>(null) }
    var deleteFile by remember { mutableStateOf<File?>(null) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }

    val isUnderSdcard = currentDir.absolutePath.startsWith(sdcardDir.absolutePath)

    fun navigateUpOrExit() {
        val parent = currentDir.parentFile
        val isHomeRoot = currentDir.absolutePath == homeDir.absolutePath
        val isSdcardRoot = currentDir.absolutePath == sdcardDir.absolutePath
        val canGoUp = parent != null && parent.exists() && parent.canRead() && !isHomeRoot && !isSdcardRoot

        if (canGoUp) {
            currentDir = parent!!
        } else {
            onExit()
        }
    }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            navigateUpOrExit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Search in folder...",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Text("File Manager")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            navigateUpOrExit()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search files")
                        }
                        IconButton(onClick = {
                            val newVal = !showHidden
                            showHidden = newVal
                            prefs.edit().putBoolean(PREF_KEY_SHOW_HIDDEN, newVal).apply()
                            Toast.makeText(
                                context,
                                if (newVal) "Showing hidden files" else "Hidden files hidden",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (showHidden) "Hide hidden files" else "Show hidden files",
                                tint = if (showHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { sortDialogOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort and Organize")
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                            modifier = Modifier.outlinedMenuCard()
                        ) {
                            DropdownMenuItem(
                                text = { Text("New File") },
                                onClick = { overflowOpen = false; newFileDialog = true }
                            )
                            MenuItemDivider()
                            DropdownMenuItem(
                                text = { Text("New Folder") },
                                onClick = { overflowOpen = false; newFolderDialog = true }
                            )
                            MenuItemDivider()
                            DropdownMenuItem(
                                text = { Text(if (showHidden) "Hide Hidden Files" else "Show Hidden Files") },
                                onClick = {
                                    overflowOpen = false
                                    val newVal = !showHidden
                                    showHidden = newVal
                                    prefs.edit().putBoolean(PREF_KEY_SHOW_HIDDEN, newVal).apply()
                                }
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Storage quick shortcuts row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StorageChip(
                    label = "Home (~)",
                    icon = Icons.Filled.Home,
                    isSelected = currentDir.absolutePath.startsWith(homeDir.absolutePath),
                    onClick = { currentDir = homeDir }
                )
                StorageChip(
                    label = "Shared Storage",
                    icon = Icons.Filled.SdStorage,
                    isSelected = currentDir.absolutePath == sdcardDir.absolutePath,
                    onClick = { currentDir = sdcardDir }
                )
                StorageChip(
                    label = "Downloads",
                    icon = Icons.Filled.Download,
                    isSelected = currentDir.absolutePath.startsWith(downloadsDir.absolutePath),
                    onClick = { currentDir = downloadsDir }
                )
                StorageChip(
                    label = "Documents",
                    icon = Icons.Filled.Description,
                    isSelected = currentDir.absolutePath.startsWith(documentsDir.absolutePath),
                    onClick = { currentDir = documentsDir }
                )
            }

            // Storage permission warning banner if browsing /sdcard without permission
            if (isUnderSdcard && !hasStoragePermission) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Storage Permission Needed",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Grant permission to browse files in /sdcard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    PermissionUtils.requestManageStorageExternalPermission(context)
                                } else if (context is Activity) {
                                    PermissionUtils.requestLegacyStorageExternalPermission(context, 1001)
                                }
                            }
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }

            // Path navigation row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        friendlyPath(currentDir.absolutePath, homeDir, sdcardDir),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (!currentDir.canRead()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Permission Denied",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "This directory cannot be read. You may need storage permissions or elevated privileges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (displayedFiles.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (isSearchActive) Icons.Filled.Search else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (isSearchActive) "No matches found" else "This folder is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (isSearchActive)
                            "No files matching \"$searchQuery\" in this folder"
                        else if (currentDir.absolutePath == homeDir.absolutePath)
                            "Create files here via menu (⋮), or tap 'Shared Storage' above to browse /sdcard"
                        else
                            "Use the menu (⋮) above to create a new file or directory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayedFiles, key = { it.absolutePath }) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    currentDir = file
                                    searchQuery = ""
                                } else {
                                    onOpenFile(file)
                                }
                            },
                            onLongClick = { optionsFile = file },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }

    // --- File options dialog on long click ---
    optionsFile?.let { file ->
        val actions = listOf("Open", "Share", "Copy path", "Delete")
        AlertDialog(
            onDismissRequest = { optionsFile = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    actions.forEachIndexed { i, action ->
                        if (i > 0) MenuItemDivider()
                        DropdownMenuItem(
                            text = { Text(action) },
                            onClick = {
                                optionsFile = null
                                when (action) {
                                    "Open" -> onOpenFile(file)
                                    "Share" -> shareFile(context, file)
                                    "Copy path" -> copyPath(context, file)
                                    "Delete" -> deleteFile = file
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { optionsFile = null }) { Text("Cancel") } },
        )
    }

    // --- Delete confirmation ---
    deleteFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteFile = null },
            title = { Text("Delete") },
            text = { Text("Delete \"${file.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = deleteRecursive(file)
                    deleteFile = null
                    if (ok) {
                        onRefresh()
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteFile = null }) { Text("Cancel") } },
        )
    }

    // --- New file ---
    if (newFileDialog) {
        NameEntryDialog(
            title = "New File",
            hint = "filename.txt",
            onDismiss = { newFileDialog = false },
            onConfirm = { name ->
                newFileDialog = false
                val f = File(currentDir, name)
                try {
                    if (f.createNewFile()) onRefresh()
                    else Toast.makeText(context, "File already exists", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // --- New folder ---
    if (newFolderDialog) {
        NameEntryDialog(
            title = "New Folder",
            hint = "folder-name",
            onDismiss = { newFolderDialog = false },
            onConfirm = { name ->
                newFolderDialog = false
                val f = File(currentDir, name)
                if (f.mkdir()) onRefresh()
                else Toast.makeText(context, "Could not create folder", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // --- Sort & Organize dialog ---
    if (sortDialogOpen) {
        AlertDialog(
            onDismissRequest = { sortDialogOpen = false },
            title = {
                Text("Sort & Organize", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "SORT BY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FileSortOption.values().forEach { option ->
                        val selected = (sortOption == option)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    sortOption = option
                                    prefs.edit().putString(PREF_KEY_SORT, option.name).apply()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    sortOption = option
                                    prefs.edit().putString(PREF_KEY_SORT, option.name).apply()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                option.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Text(
                        "ORGANIZATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val newVal = !showHidden
                                showHidden = newVal
                                prefs.edit().putBoolean(PREF_KEY_SHOW_HIDDEN, newVal).apply()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show hidden files", style = MaterialTheme.typography.bodyMedium)
                            Text("Include files and folders starting with .", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showHidden,
                            onCheckedChange = { newVal ->
                                showHidden = newVal
                                prefs.edit().putBoolean(PREF_KEY_SHOW_HIDDEN, newVal).apply()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val newVal = !foldersFirst
                                foldersFirst = newVal
                                prefs.edit().putBoolean(PREF_KEY_FOLDERS_FIRST, newVal).apply()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep folders on top", style = MaterialTheme.typography.bodyMedium)
                            Text("Group directories before regular files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = foldersFirst,
                            onCheckedChange = { newVal ->
                                foldersFirst = newVal
                                prefs.edit().putBoolean(PREF_KEY_FOLDERS_FIRST, newVal).apply()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { sortDialogOpen = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(file: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isDir = file.isDirectory
    val ext = file.name.substringAfterLast('.', "").lowercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isDir -> Icon(
                Icons.Filled.Folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            ext in CODE_EXTENSIONS -> Icon(
                Icons.Filled.Code,
                contentDescription = "Code file",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            ext in IMAGE_EXTENSIONS -> Icon(
                Icons.Filled.Image,
                contentDescription = "Image file",
                tint = Color(0xFF81C784),
                modifier = Modifier.size(24.dp)
            )
            else -> Icon(
                Icons.Filled.Description,
                contentDescription = "File",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sizeStr = if (isDir) "Folder" else formatSize(file.length())
            val dateStr = formatDate(file.lastModified())
            Text(
                if (dateStr.isNotEmpty()) "$sizeStr • $dateStr" else sizeStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileViewerScreen(
    file: File,
    onClose: () -> Unit,
    onFileChanged: () -> Unit,
) {
    val isImg = remember(file) { isImageFile(file) }
    val isTxt = remember(file) { isTextFile(file) }
    val isMd = remember(file) {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        ext == "md" || ext == "markdown"
    }

    when {
        isImg -> ImageViewer(file = file, onClose = onClose)
        isMd -> MarkdownViewer(file = file, onClose = onClose, onSaved = onFileChanged)
        isTxt || file.length() <= MAX_TEXT_SIZE -> TextEditorView(file = file, onClose = onClose, onSaved = onFileChanged)
        else -> BinaryFileInfoView(file = file, onClose = onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownViewer(
    file: File,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var initialText by remember(file) { mutableStateOf(readFile(file)) }
    var text by remember(file) { mutableStateOf(initialText) }
    val isModified = text != initialText
    var showPreview by remember { mutableStateOf(true) }
    var confirmExitDialog by remember { mutableStateOf(false) }

    fun tryExit() {
        if (isModified) {
            confirmExitDialog = true
        } else {
            onClose()
        }
    }

    BackHandler { tryExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isModified) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Text(
                            if (showPreview) "Preview (Markdown + Mermaid)" else "Edit Markdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { tryExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPreview = !showPreview }) {
                        Icon(
                            if (showPreview) Icons.Filled.Edit else Icons.Filled.Visibility,
                            contentDescription = if (showPreview) "Edit" else "Preview",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!showPreview) {
                        IconButton(
                            onClick = {
                                try {
                                    file.writeText(text)
                                    initialText = text
                                    onSaved()
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = isModified
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = "Save",
                                tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    } else {
                        IconButton(onClick = { shareFile(context, file) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showPreview) {
                val html = remember(text) { buildMarkdownHtml(text) }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            setBackgroundColor(0xFF0D1117.toInt())
                        }
                    },
                    update = { wv ->
                        wv.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
                    },
                    onRelease = { wv ->
                        try {
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            wv.destroy()
                        } catch (ignored: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }

    if (confirmExitDialog) {
        AlertDialog(
            onDismissRequest = { confirmExitDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes in ${file.name}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExitDialog = false
                    onClose()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExitDialog = false }) { Text("Keep Editing") }
            }
        )
    }
}

private fun buildMarkdownHtml(markdown: String): String {
    val escapedJson = org.json.JSONObject.quote(markdown)
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
        <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
        <style>
        body {
            background-color: #0d1117;
            color: #c9d1d9;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            font-size: 15px;
            line-height: 1.6;
            margin: 0;
            padding: 16px;
            word-wrap: break-word;
        }
        h1, h2, h3, h4, h5, h6 {
            color: #58a6ff;
            font-weight: 600;
            margin-top: 24px;
            margin-bottom: 12px;
            border-bottom: 1px solid #21262d;
            padding-bottom: 6px;
        }
        code {
            background-color: #161b22;
            color: #ff7b72;
            padding: 2px 6px;
            border-radius: 4px;
            font-family: monospace;
            font-size: 85%;
        }
        pre {
            background-color: #161b22;
            border: 1px solid #30363d;
            border-radius: 6px;
            padding: 12px;
            overflow-x: auto;
        }
        pre code {
            background-color: transparent;
            color: #c9d1d9;
            padding: 0;
        }
        table {
            border-collapse: collapse;
            width: 100%;
            margin: 16px 0;
        }
        th, td {
            border: 1px solid #30363d;
            padding: 8px 12px;
        }
        th {
            background-color: #161b22;
            color: #f0f6fc;
        }
        blockquote {
            border-left: 4px solid #388bfd;
            color: #8b949e;
            margin: 0;
            padding-left: 12px;
        }
        a {
            color: #58a6ff;
            text-decoration: none;
        }
        .mermaid {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 8px;
            padding: 16px;
            margin: 16px 0;
            display: flex;
            justify-content: center;
            overflow-x: auto;
        }
        </style>
        </head>
        <body>
        <div id="content"></div>
        <script>
        const rawMd = $escapedJson;
        try {
            if (typeof marked !== 'undefined') {
                const renderer = new marked.Renderer();
                const origCode = renderer.code.bind(renderer);
                renderer.code = function(code, lang, escaped) {
                    if (lang === 'mermaid') {
                        return '<div class="mermaid">' + code + '</div>';
                    }
                    return origCode(code, lang, escaped);
                };
                document.getElementById('content').innerHTML = marked.parse(rawMd, { renderer: renderer });
                if (typeof mermaid !== 'undefined') {
                    mermaid.initialize({ startOnLoad: false, theme: 'dark' });
                    mermaid.run({ querySelector: '.mermaid' });
                }
            } else {
                document.getElementById('content').innerHTML = '<pre>' + rawMd.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</pre>';
            }
        } catch (e) {
            document.getElementById('content').innerHTML = '<pre>' + rawMd.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</pre>';
        }
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun decodeSampledBitmap(file: File, reqWidth: Int = 2048, reqHeight: Int = 2048): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(file) {
        decodeSampledBitmap(file)?.asImageBitmap()
    }

    BackHandler { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareFile(context, file) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0C0E11)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                Text("Unable to render image preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditorView(
    file: File,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var initialText by remember(file) { mutableStateOf(readFile(file)) }
    var text by remember(file) { mutableStateOf(initialText) }
    val isModified = text != initialText
    var lineWrap by remember { mutableStateOf(true) }
    var overflowOpen by remember { mutableStateOf(false) }
    var confirmExitDialog by remember { mutableStateOf(false) }

    fun tryExit() {
        if (isModified) {
            confirmExitDialog = true
        } else {
            onClose()
        }
    }

    BackHandler { tryExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isModified) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Text(
                            friendlyPath(file.parent ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { tryExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                file.writeText(text)
                                initialText = text
                                onSaved()
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = isModified
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "Save",
                            tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                        modifier = Modifier.outlinedMenuCard()
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (lineWrap) "Disable word wrap" else "Enable word wrap") },
                            onClick = { lineWrap = !lineWrap; overflowOpen = false }
                        )
                        MenuItemDivider()
                        DropdownMenuItem(
                            text = { Text("Share file") },
                            onClick = { overflowOpen = false; shareFile(context, file) }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy path") },
                            onClick = { overflowOpen = false; copyPath(context, file) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
                    Text(
                        "$lineCount lines · ${text.length} chars",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isModified) "● Modified" else "UTF-8",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F1115))
        ) {
            val lines = remember(text) { text.lines() }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                // Line numbers gutter
                Column(
                    modifier = Modifier
                        .background(Color(0xFF14171D))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.indices.forEach { index ->
                        Text(
                            text = "${index + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF5A6270),
                            lineHeight = 20.sp
                        )
                    }
                }

                // Text / Code editor input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (!lineWrap) Modifier.horizontalScroll(hScroll) else Modifier)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFECECEC),
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }

    if (confirmExitDialog) {
        AlertDialog(
            onDismissRequest = { confirmExitDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes in \"${file.name}\". Discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExitDialog = false
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExitDialog = false }) { Text("Keep Editing") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinaryFileInfoView(
    file: File,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var forceText by remember { mutableStateOf(false) }

    if (forceText) {
        TextEditorView(file = file, onClose = onClose, onSaved = {})
        return
    }

    BackHandler { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(file.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                formatSize(file.length()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { forceText = true }) {
                    Text("Open as Text")
                }
                OutlinedButton(onClick = { openWithSystem(context, file) }) {
                    Text("Open in App")
                }
            }
        }
    }
}

@Composable
private fun NameEntryDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.trim().isNotEmpty()) onConfirm(name.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- helpers ---

private val fileDateFormat by lazy { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
private fun formatDate(timestamp: Long): String = try {
    fileDateFormat.format(Date(timestamp))
} catch (e: Exception) {
    ""
}

private fun listDir(
    dir: File,
    sortOption: FileSortOption = FileSortOption.NAME_ASC,
    showHidden: Boolean = true,
    foldersFirst: Boolean = true
): List<File> {
    val entries = dir.listFiles() ?: return emptyList()
    var filtered = entries.filter { it.name != "." && it.name != ".." }
    if (!showHidden) {
        filtered = filtered.filter { !it.name.startsWith(".") }
    }

    val comparator = when (sortOption) {
        FileSortOption.NAME_ASC -> compareBy<File> { it.name.lowercase() }
        FileSortOption.NAME_DESC -> compareByDescending<File> { it.name.lowercase() }
        FileSortOption.DATE_DESC -> compareByDescending<File> { it.lastModified() }
        FileSortOption.DATE_ASC -> compareBy<File> { it.lastModified() }
        FileSortOption.SIZE_DESC -> compareByDescending<File> { if (it.isDirectory) 0L else it.length() }
        FileSortOption.SIZE_ASC -> compareBy<File> { if (it.isDirectory) 0L else it.length() }
        FileSortOption.TYPE_ASC -> compareBy<File> { it.extension.lowercase() }.thenBy { it.name.lowercase() }
    }

    return if (foldersFirst) {
        filtered.sortedWith(compareBy<File> { !it.isDirectory }.then(comparator))
    } else {
        filtered.sortedWith(comparator)
    }
}

private fun isImageFile(file: File): Boolean {
    if (file.isDirectory) return false
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

private fun isTextFile(file: File): Boolean {
    if (file.isDirectory) return false
    val name = file.name.lowercase()
    if (!name.contains(".")) {
        return name.startsWith(".") || name == "makefile" || name == "dockerfile" || name == "license" || name == "readme"
    }
    val ext = name.substringAfterLast('.')
    return ext in TEXT_EXTENSIONS
}

private fun readFile(file: File): String = try {
    file.readText()
} catch (e: Exception) {
    ""
}

private fun deleteRecursive(f: File): Boolean {
    if (f.isDirectory) {
        f.listFiles()?.forEach { if (!deleteRecursive(it)) return false }
    }
    return f.delete()
}

private fun friendlyPath(path: String, homeDir: File? = null, sdcardDir: File? = null): String {
    val hPath = homeDir?.absolutePath ?: TermuxConstants.TERMUX_HOME_DIR_PATH
    val sPath = sdcardDir?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath
    return when {
        path == hPath -> "~"
        path.startsWith("$hPath/") -> "~/" + path.removePrefix("$hPath/")
        path == sPath -> "/sdcard"
        path.startsWith("$sPath/") -> "/sdcard/" + path.removePrefix("$sPath/")
        path == "/data/data/com.termux/files/home" -> "Original Termux Home"
        path.startsWith("/data/data/com.termux/files/home/") -> "Original Home/" + path.removePrefix("/data/data/com.termux/files/home/")
        else -> path
    }
}

@Composable
private fun StorageChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
}

private fun openWithSystem(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${file.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    } catch (e: Exception) {
        copyPath(context, file)
        Toast.makeText(context, "Share unavailable, path copied", Toast.LENGTH_SHORT).show()
    }
}

private fun copyPath(context: Context, file: File) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("path", file.absolutePath))
    Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
}
