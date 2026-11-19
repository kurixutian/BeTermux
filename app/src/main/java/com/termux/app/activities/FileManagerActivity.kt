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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.betermux.compose.MenuItemDivider
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.compose.outlinedMenuCard
import com.betermux.theme.BackgroundImageStore
import com.betermux.theme.LocalWarpPalette
import com.betermux.theme.WarpBackground
import com.betermux.theme.warpSurface
import com.termux.shared.android.PermissionUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.file.TermuxFileUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.max
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

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
        val initialPath = intent.getStringExtra("initial_path")
        setContent {
            BeTermuxComposeTheme(this) {
                FileManagerScreen(
                    onExit = { finish() },
                    resumeRefreshKey = resumeRefreshKey.intValue,
                    initialPath = initialPath
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

internal enum class ClipboardOperation(val readyVerb: String, val confirmationLabel: String) {
    COPY("copy", "Copy Here"),
    MOVE("move", "Move Here")
}

internal data class FileClipboardState(
    val pendingOperation: ClipboardOperation? = null,
    val selectedItems: List<File> = emptyList()
) {
    fun readyTitle(): String {
        val operation = pendingOperation ?: return ""
        val count = selectedItems.size
        return "$count ${if (count == 1) "item" else "items"} ready to ${operation.readyVerb}"
    }
}

private const val PREFS_FILE_MANAGER = "betermux_file_manager"
private const val PREF_KEY_SORT = "sort_option"
private const val PREF_KEY_SHOW_HIDDEN = "show_hidden"
private const val PREF_KEY_FOLDERS_FIRST = "folders_first"

@Composable
private fun FileManagerScreen(
    onExit: () -> Unit,
    resumeRefreshKey: Int = 0,
    initialPath: String? = null,
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
            onRefresh = { refreshKey++ },
            initialPath = initialPath
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
    initialPath: String? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
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

    // Ask for storage permission once on entry; resumeRefreshKey re-checks after grant/deny
    LaunchedEffect(hasStoragePermission) {
        if (!hasStoragePermission) {
            PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(context, 1001, false)
        }
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

    var currentDir by remember {
        val initial = initialPath?.let { File(it) }?.takeIf { it.isDirectory && it.canRead() }
        mutableStateOf(initial ?: homeDir)
    }
    val files = remember(currentDir, refreshKey, sortOption, showHidden, foldersFirst) {
        try {
            listDir(currentDir, sortOption, showHidden, foldersFirst)
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // --- Phase 1: Path Navigation & Direct Search Jump ---
    var searchScopeRecursive by remember { mutableStateOf(false) } // false = current folder, true = recursive
    var showBreadcrumbs by remember { mutableStateOf(true) }

    val displayedFiles = remember(files, searchQuery, isSearchActive, searchScopeRecursive, refreshKey) {
        if (!isSearchActive || searchQuery.isBlank()) {
            files
        } else {
            val q = searchQuery.trim().lowercase()
            if (searchScopeRecursive) {
                // Recursive search - skip heavy directories
                val allFiles = listDirRecursive(currentDir, sortOption, showHidden, foldersFirst)
                allFiles.filter { it.name.lowercase().contains(q) }
            } else {
                files.filter { it.name.lowercase().contains(q) }
            }
        }
    }

    var overflowOpen by remember { mutableStateOf(false) }
    var optionsFile by remember { mutableStateOf<File?>(null) }
    var deleteFile by remember { mutableStateOf<File?>(null) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }

    // --- Phase 2: Core File Operations ---
    var clipboardState by remember { mutableStateOf(FileClipboardState()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<MutableSet<File>>(mutableSetOf()) }

    // --- Phase 3: Metadata ---
    var propertiesFile by remember { mutableStateOf<File?>(null) }
    var renameFile by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }

    // --- Phase 2c: Archive Support ---
    var showExtractDialog by remember { mutableStateOf(false) }
    var extractDialogFile by remember { mutableStateOf<File?>(null) }
    var showCompressDialog by remember { mutableStateOf(false) }

    // Quick-access paths
    val prefixDir = remember { File(TermuxConstants.TERMUX_PREFIX_DIR_PATH) }
    val prefixEtcDir = remember { File(TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH) }

    val isUnderSdcard = try {
        currentDir.canonicalPath.startsWith(sdcardDir.canonicalPath)
    } catch (e: Exception) {
        currentDir.absolutePath.startsWith(sdcardDir.absolutePath)
    }

    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()

    fun closeSearch() {
        softwareKeyboardController?.hide()
        focusManager.clearFocus()
        isSearchActive = false
        searchQuery = ""
        searchScopeRecursive = false
    }

    fun navigateToRoot(dir: File) {
        currentDir = dir
        if (searchQuery.isBlank()) closeSearch()
    }

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

    fun handleSearchSubmit() {
        val rawQuery = searchQuery.trim()
        if (rawQuery.isEmpty()) {
            closeSearch()
            return
        }

        val looksLikePath = rawQuery.startsWith("/") ||
            rawQuery.startsWith("~") ||
            rawQuery.startsWith("\$") ||
            rawQuery.contains('/')
        if (!looksLikePath) return

        val expanded = expandPath(rawQuery, currentDir, homeDir) ?: return
        val targetFile = File(expanded)
        if (!targetFile.exists()) {
            Toast.makeText(context, "Path not found: $expanded", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetFile.isDirectory) {
            currentDir = targetFile
            closeSearch()
            Toast.makeText(context, "Navigated to ${friendlyPath(targetFile.absolutePath, homeDir, sdcardDir)}", Toast.LENGTH_SHORT).show()
        } else {
            currentDir = targetFile.parentFile ?: homeDir
            closeSearch()
            onOpenFile(targetFile)
            Toast.makeText(context, "Opened ${targetFile.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmRename(file: File) {
        val newName = renameText.trim()
        if (newName.isNotEmpty() && newName != file.name) {
            val newFile = File(file.parentFile, newName)
            try {
                if (file.renameTo(newFile)) {
                    onRefresh()
                    Toast.makeText(context, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        renameFile = null
        renameText = ""
    }

    fun pasteFiles(destDir: File) {
        val operation = clipboardState.pendingOperation ?: return
        val itemsToPaste = clipboardState.selectedItems
        if (itemsToPaste.isEmpty()) return

        coroutineScope.launch {
            var successCount = 0
            var failCount = 0
            var lastError: String? = null

            fun failureReason(srcFile: File, destFile: File): String = when {
                !srcFile.exists() -> "${srcFile.name} no longer exists"
                !PermissionUtils.checkStoragePermission(context, PermissionUtils.isLegacyExternalStoragePossible(context)) ->
                    "Grant storage permission in Settings, then retry"
                !destDir.canWrite() -> "Cannot write to ${friendlyPath(destDir.absolutePath, homeDir, sdcardDir)}"
                else -> "Failed to ${operation.readyVerb} ${srcFile.name}"
            }

            withContext(Dispatchers.IO) {
                for (srcFile in itemsToPaste) {
                    val destFile = File(destDir, srcFile.name)
                    val isMove = operation == ClipboardOperation.MOVE

                    try {
                        if (srcFile.parentFile?.canonicalFile == destDir.canonicalFile) {
                            successCount++
                            continue
                        }
                        if (srcFile.isDirectory) {
                            if (isMove) {
                                if (moveDirectoryRecursive(srcFile, destFile)) successCount++ else failCount++
                            } else {
                                if (copyDirectoryRecursive(srcFile, destFile)) successCount++ else failCount++
                            }
                        } else {
                            if (isMove) {
                                // renameTo fails across filesystems (e.g. home → /sdcard); fall back to copy+delete
                                if (srcFile.renameTo(destFile) || (copyFile(srcFile, destFile) && srcFile.delete())) successCount++ else failCount++
                            } else {
                                if (copyFile(srcFile, destFile)) successCount++ else failCount++
                            }
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                    if (lastError == null && failCount > 0) {
                        lastError = failureReason(srcFile, destFile)
                    }
                }
            }

            onRefresh()

            val action = if (operation == ClipboardOperation.MOVE) "Moved" else "Copied"
            Toast.makeText(context, "$action $successCount item(s)" + if (failCount > 0) " ($failCount failed${lastError?.let { ": $it" } ?: ""})" else "", if (failCount > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

            if (failCount == 0 &&
                clipboardState.pendingOperation == operation &&
                clipboardState.selectedItems == itemsToPaste
            ) {
                clipboardState = FileClipboardState()
            }
        }
    }

    // Multi-select batch operations
    fun copySelectedToClipboard() {
        clipboardState = FileClipboardState(ClipboardOperation.COPY, selectedFiles.toList())
        selectedFiles.clear()
        isMultiSelectMode = false
    }

    fun moveSelectedToClipboard() {
        clipboardState = FileClipboardState(ClipboardOperation.MOVE, selectedFiles.toList())
        selectedFiles.clear()
        isMultiSelectMode = false
    }

    fun deleteSelectedFiles() {
        val scope = coroutineScope
        scope.launch {
            var successCount = 0
            var failCount = 0
            withContext(Dispatchers.IO) {
                for (file in selectedFiles) {
                    try {
                        if (deleteRecursive(file)) successCount++ else failCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                }
            }
            selectedFiles.clear()
            isMultiSelectMode = false
            onRefresh()
            Toast.makeText(context, "Deleted $successCount item(s)" + if (failCount > 0) " ($failCount failed)" else "", Toast.LENGTH_SHORT).show()
        }
    }

    fun extractArchiveHere(file: File, destDir: File) {
        val scope = coroutineScope
        scope.launch {
            val success = extractArchive(file, destDir) { name, current, total ->
            }
            onRefresh()
            Toast.makeText(context, if (success) "Extracted to ${destDir.name}" else "Extraction failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun compressSelectedFiles() {
        showCompressDialog = true
    }

    fun selectAllFiles() {
        selectedFiles.clear()
        selectedFiles.addAll(displayedFiles)
    }

    BackHandler {
        if (isSearchActive) {
            closeSearch()
        } else {
            navigateUpOrExit()
        }
    }

    WarpBackground {
        val warpPalette = LocalWarpPalette.current
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .warpSurface(
                            shape = RoundedCornerShape(10.dp),
                            color = warpPalette.surface,
                            borderColor = warpPalette.borderSubtle,
                        )
                ) {
                    if (isSearchActive) {
                        // Custom header: TopAppBar's fixed height clips the search field + scope toggle
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { closeSearch() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                                }
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .padding(end = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
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
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { handleSearchSubmit() }),
                                        decorationBox = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        "Search or enter path...",
                                                        style = TextStyle(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { handleSearchSubmit() }) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search or go", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { closeSearch() }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                                    }
                                }
                            }
                                }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (searchScopeRecursive) "Recursive" else "Current folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = searchScopeRecursive,
                                    onCheckedChange = { searchScopeRecursive = it },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    } else {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            title = { Text("File Manager") },
                            navigationIcon = {
                                IconButton(onClick = { navigateUpOrExit() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
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
                        )
                    }
                }
        },
        bottomBar = {
            val operation = clipboardState.pendingOperation
            if (operation != null && clipboardState.selectedItems.isNotEmpty()) {
                ClipboardActionBar(
                    state = clipboardState,
                    onConfirm = { pasteFiles(currentDir) },
                    onCancel = { clipboardState = FileClipboardState() }
                )
            } else if (isMultiSelectMode) {
                MultiSelectActionBar(
                    selectedCount = selectedFiles.size,
                    onCopy = { copySelectedToClipboard() },
                    onMove = { moveSelectedToClipboard() },
                    onDelete = { deleteSelectedFiles() },
                    onCompress = { compressSelectedFiles() },
                    onSelectAll = { selectAllFiles() },
                    onDeselectAll = { selectedFiles.clear() },
                    onExit = { isMultiSelectMode = false; selectedFiles.clear() }
                )
            }
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
                    onClick = { navigateToRoot(homeDir) }
                )
                StorageChip(
                    label = "Shared Storage",
                    icon = Icons.Filled.SdStorage,
                    isSelected = currentDir.absolutePath == sdcardDir.absolutePath,
                    onClick = { navigateToRoot(sdcardDir) }
                )
                StorageChip(
                    label = "Downloads",
                    icon = Icons.Filled.Download,
                    isSelected = currentDir.absolutePath.startsWith(downloadsDir.absolutePath),
                    onClick = { navigateToRoot(downloadsDir) }
                )
                StorageChip(
                    label = "Documents",
                    icon = Icons.Filled.Description,
                    isSelected = currentDir.absolutePath.startsWith(documentsDir.absolutePath),
                    onClick = { navigateToRoot(documentsDir) }
                )
                StorageChip(
                    label = "\$PREFIX",
                    icon = Icons.Filled.Folder,
                    isSelected = currentDir.absolutePath.startsWith(prefixDir.absolutePath),
                    onClick = { navigateToRoot(prefixDir) }
                )
                StorageChip(
                    label = "\$PREFIX/etc",
                    icon = Icons.Filled.Settings,
                    isSelected = currentDir.absolutePath.startsWith(prefixEtcDir.absolutePath),
                    onClick = { navigateToRoot(prefixEtcDir) }
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

            // Path navigation row with interactive breadcrumbs
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Breadcrumbs(
                    currentDir = currentDir,
                    homeDir = homeDir,
                    sdcardDir = sdcardDir,
                    prefixDir = prefixDir,
                    prefixEtcDir = prefixEtcDir,
                    onNavigate = { dir ->
                        currentDir = dir
                        closeSearch()
                    }
                )
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
                        val isSelected = selectedFiles.contains(file)
                        FileRow(
                            file = file,
                            onClick = {
                                if (isMultiSelectMode) {
                                    if (isSelected) selectedFiles.remove(file) else selectedFiles.add(file)
                                } else if (file.isDirectory) {
                                    currentDir = file
                                    closeSearch()
                                } else {
                                    onOpenFile(file)
                                }
                            },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    optionsFile = file
                                } else {
                                    if (isSelected) selectedFiles.remove(file) else selectedFiles.add(file)
                                }
                            },
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = isSelected,
                            onSelectionChange = { selected ->
                                if (selected) selectedFiles.add(file) else selectedFiles.remove(file)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
    }

    // --- File options dialog on long click ---
    optionsFile?.let { file ->
        val isDir = file.isDirectory
        val isArchive = isArchiveFile(file)
        val isApk = file.extension.lowercase() == "apk"
        val isDeb = file.extension.lowercase() == "deb"
        val isImage = file.extension.lowercase() in IMAGE_EXTENSIONS
        data class ContextAction(
            val label: String,
            val icon: ImageVector,
            val dividerBefore: Boolean = false
        )
        val actions = buildList {
            add(ContextAction("Open", Icons.AutoMirrored.Filled.OpenInNew))
            add(ContextAction("Share", Icons.Filled.Share))
            add(ContextAction("Copy path", Icons.Filled.Link))
            add(ContextAction("Rename", Icons.Filled.Edit))
            add(ContextAction("Properties", Icons.Filled.Info))
            if (isDir) add(ContextAction("Open Terminal Here", Icons.Filled.Terminal, dividerBefore = true))
            if (isDir) {
                add(ContextAction("cd to this folder", Icons.Filled.Terminal))
            } else {
                add(ContextAction("cd to this file path", Icons.Filled.Terminal))
            }
            if (isArchive) {
                add(ContextAction("Extract Here", Icons.Filled.Unarchive, dividerBefore = !isDir))
                add(ContextAction("Extract to Folder…", Icons.Filled.FolderOpen))
            }
            if (isImage) add(ContextAction("Set as Wallpaper", Icons.Filled.Wallpaper, dividerBefore = !isDir && !isArchive))
            if (isApk) add(ContextAction("Install APK", Icons.Filled.Android, dividerBefore = !isDir && !isArchive && !isImage))
            if (isDeb) add(ContextAction("Install DEB (pkg)", Icons.Filled.Android))
            if (clipboardState.pendingOperation != null && clipboardState.selectedItems.isNotEmpty()) {
                add(ContextAction("Paste Here", Icons.Filled.ContentPaste, dividerBefore = true))
            }
            add(ContextAction("Copy", Icons.Filled.ContentCopy))
            add(ContextAction("Move", Icons.AutoMirrored.Filled.DriveFileMove))
            add(ContextAction("Delete", Icons.Filled.Delete, dividerBefore = true))
        }
        AlertDialog(
            onDismissRequest = { optionsFile = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    actions.forEach { action ->
                        if (action.dividerBefore) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        FileContextActionRow(
                            label = action.label,
                            icon = action.icon,
                            isDanger = action.label == "Delete",
                            onClick = {
                                optionsFile = null
                                when (action.label) {
                                    "Open" -> onOpenFile(file)
                                    "Share" -> shareFile(context, file)
                                    "Copy path" -> copyPath(context, file)
                                    "Copy" -> clipboardState = FileClipboardState(ClipboardOperation.COPY, listOf(file))
                                    "Move" -> clipboardState = FileClipboardState(ClipboardOperation.MOVE, listOf(file))
                                    "Paste Here" -> pasteFiles(currentDir)
                                    "Rename" -> {
                                        renameFile = file
                                        renameText = file.name
                                    }
                                    "Properties" -> propertiesFile = file
                                    "Open Terminal Here" -> openTerminalHere(context, file)
                                    "cd to this folder" -> cdTerminalTo(context, file.absolutePath)
                                    "cd to this file path" -> file.parentFile?.let { cdTerminalTo(context, it.absolutePath) }
                                    "Extract Here" -> extractArchiveHere(file, currentDir)
                                    "Extract to Folder…" -> {
                                        showExtractDialog = true
                                        extractDialogFile = file
                                    }
                                    "Install APK" -> installApk(context, file)
                                    "Install DEB (pkg)" -> installDeb(context, file)
                                    "Set as Wallpaper" -> coroutineScope.launch { setWallpaperFromFile(context, file) }
                                    "Delete" -> deleteFile = file
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { optionsFile = null }) { Text("Cancel") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
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

    // --- Rename dialog ---
    renameFile?.let { file ->
        AlertDialog(
            onDismissRequest = { renameFile = null; renameText = "" },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { confirmRename(file) }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameFile = null; renameText = "" }) { Text("Cancel") } },
        )
    }

    // --- Properties dialog ---
    propertiesFile?.let { file ->
        PropertiesDialog(
            file = file,
            onDismiss = { propertiesFile = null },
            onRefresh = onRefresh
        )
    }

    // --- Extract Archive dialog ---
    showExtractDialog.let {
        extractDialogFile?.let { file ->
            ArchiveExtractDialog(
                file = file,
                onDismiss = { showExtractDialog = false; extractDialogFile = null },
                onExtract = { destDir ->
                    showExtractDialog = false
                    extractDialogFile = null
                    val scope = coroutineScope
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            extractArchive(file, destDir) { name, current, total ->
                            }
                        }
                        onRefresh()
                        Toast.makeText(context, if (success) "Extracted to ${destDir.name}" else "Extraction failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // --- Compress dialog ---
    showCompressDialog.let {
        if (selectedFiles.isNotEmpty()) {
            val filesToCompress = selectedFiles.toList() // Capture at dialog open
            ArchiveCompressDialog(
                files = filesToCompress,
                onDismiss = { showCompressDialog = false },
                onCompress = { destFile, type ->
                    showCompressDialog = false
                    val scope = coroutineScope
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            createArchive(filesToCompress, destFile, type) { name, current, total ->
                            }
                        }
                        if (success) {
                            selectedFiles.clear()
                            isMultiSelectMode = false
                            onRefresh()
                        }
                        Toast.makeText(context, if (success) "Created ${destFile.name}" else "Compression failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
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
private fun FileRow(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: ((Boolean) -> Unit)? = null
) {
    val isDir = file.isDirectory
    val ext = file.name.substringAfterLast('.', "").lowercase()
    val isSymlink = try { Files.isSymbolicLink(file.toPath()) } catch (e: Exception) { false }
    val rowBackground = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isMultiSelectMode) onSelectionChange?.invoke(!isSelected) else onClick() },
                onLongClick = { if (!isMultiSelectMode) onLongClick() else onSelectionChange?.invoke(!isSelected) }
            )
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .background(rowBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection checkbox in multi-select mode
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChange?.invoke(it) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        
        // Main file icon
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
        
        // Symlink badge overlay (use offset instead of negative padding)
        if (isSymlink) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = -8.dp, y = -8.dp)
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = "Symbolic link",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isSymlink) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "→",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
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

    WarpBackground {
        val warpPalette = LocalWarpPalette.current
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .warpSurface(
                            shape = RoundedCornerShape(10.dp),
                            color = warpPalette.surface,
                            borderColor = warpPalette.borderSubtle,
                        )
                ) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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

    WarpBackground {
        val warpPalette = LocalWarpPalette.current
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .warpSurface(
                            shape = RoundedCornerShape(10.dp),
                            color = warpPalette.surface,
                            borderColor = warpPalette.borderSubtle,
                        )
                ) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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

    WarpBackground {
        val warpPalette = LocalWarpPalette.current
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .warpSurface(
                            shape = RoundedCornerShape(10.dp),
                            color = warpPalette.surface,
                            borderColor = warpPalette.borderSubtle,
                        )
                ) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            }
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
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
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

    WarpBackground {
        val warpPalette = LocalWarpPalette.current
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .warpSurface(
                            shape = RoundedCornerShape(10.dp),
                            color = warpPalette.surface,
                            borderColor = warpPalette.borderSubtle,
                        )
                ) {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            }
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

// Recursive directory listing for search (skips heavy directories, bounded depth/results)
private fun listDirRecursive(
    dir: File,
    sortOption: FileSortOption = FileSortOption.NAME_ASC,
    showHidden: Boolean = true,
    foldersFirst: Boolean = true,
    maxDepth: Int = 20,
    maxResults: Int = 5000
): List<File> {
    val result = mutableListOf<File>()
    val heavyDirs = setOf("node_modules", ".git", ".gradle", "build", "target", "dist", "out", ".idea", ".vscode")
    val visited = mutableSetOf<String>()

    // Iterative DFS with explicit stack to avoid stack overflow
    val stack = mutableListOf<Pair<File, Int>>()
    stack.add(dir to 0)

    while (stack.isNotEmpty() && result.size < maxResults) {
        val (currentDir, depth) = stack.removeAt(stack.lastIndex)
        if (depth > maxDepth) continue

        val canonicalPath = try { currentDir.canonicalPath } catch (e: Exception) { currentDir.absolutePath }
        if (!visited.add(canonicalPath)) continue // Skip cycles (symlinks)

        val entries = currentDir.listFiles() ?: continue
        for (entry in entries) {
            if (result.size >= maxResults) break
            if (entry.name == "." || entry.name == "..") continue
            if (!showHidden && entry.name.startsWith(".")) continue
            if (entry.isDirectory && heavyDirs.contains(entry.name)) continue

            result.add(entry)
            if (entry.isDirectory) {
                stack.add(entry to (depth + 1))
            }
        }
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
        result.sortedWith(compareBy<File> { !it.isDirectory }.then(comparator))
    } else {
        result.sortedWith(comparator)
    }
}

// Path expansion for direct path jump
private fun expandPath(input: String, currentDir: File, homeDir: File): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // Expand ~, $VAR, ${VAR}
    var expanded = trimmed
        .replace("~", homeDir.absolutePath)
        .replace("\$PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        .replace("\$HOME", homeDir.absolutePath)

    // Expand ${VAR} and $VAR for all env vars
    expanded = expanded.replace(Regex("\\$\\{([^}]+)\\}")) { matchResult ->
        System.getenv(matchResult.groupValues[1]) ?: matchResult.value
    }
    expanded = expanded.replace(Regex("\\$([A-Za-z_][A-Za-z0-9_]*)")) { matchResult ->
        System.getenv(matchResult.groupValues[1]) ?: matchResult.value
    }

    val path = when {
        expanded.startsWith("/") -> expanded
        else -> File(currentDir, expanded).absolutePath
    }

    // Normalize path
    return try {
        File(path).canonicalPath
    } catch (e: Exception) {
        path
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

// Breadcrumbs for interactive path navigation
@Composable
private fun Breadcrumbs(
    currentDir: File,
    homeDir: File,
    sdcardDir: File,
    prefixDir: File,
    prefixEtcDir: File,
    onNavigate: (File) -> Unit
) {
    val pathSegments = remember(currentDir) {
        getPathSegments(currentDir, homeDir, sdcardDir, prefixDir, prefixEtcDir)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        pathSegments.forEachIndexed { index, segment ->
            val isLast = index == pathSegments.lastIndex
            val isClickable = !isLast

            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Separator",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(16.dp)
                        .padding(horizontal = 2.dp)
                )
            }

            if (isClickable) {
                Text(
                    text = AnnotatedString(
                        segment.name,
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth(if (isLast) 1f else 0f)
                        .clickable { onNavigate(segment.path) }
                        .padding(horizontal = 2.dp)
                )
            } else {
                Text(
                    segment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

data class PathSegment(val name: String, val path: File)

private fun getPathSegments(
    currentDir: File,
    homeDir: File,
    sdcardDir: File,
    prefixDir: File,
    prefixEtcDir: File
): List<PathSegment> {
    val segments = mutableListOf<PathSegment>()
    var current: File? = currentDir

    // Build path from root to current
    val pathParts = mutableListOf<File>()
    while (current != null) {
        pathParts.add(0, current)
        current = current.parentFile
    }

    // Determine root and create friendly names
    for (part in pathParts) {
        val name = when {
            part.absolutePath == homeDir.absolutePath -> "~"
            part.absolutePath == sdcardDir.absolutePath -> "/sdcard"
            part.absolutePath == prefixDir.absolutePath -> "\$PREFIX"
            part.absolutePath == prefixEtcDir.absolutePath -> "etc"
            part.absolutePath == "/data/data/com.termux/files/home" -> "Original Home"
            else -> part.name
        }
        segments.add(PathSegment(name, part))
    }

    return segments
}

// Compact docked clipboard action bar
@Composable
private fun ClipboardActionBar(
    state: FileClipboardState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val operation = requireNotNull(state.pendingOperation)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.readyTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Navigate to destination folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (operation == ClipboardOperation.COPY) Icons.Filled.ContentCopy else Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(operation.confirmationLabel)
                }
            }
        }
    }
}

@Composable
private fun FileContextActionRow(
    label: String,
    icon: ImageVector,
    isDanger: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Multi-select batch action bar
@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.Close, contentDescription = "Exit selection mode")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onSelectAll) { Text("Select All") }
                OutlinedButton(onClick = onDeselectAll) { Text("Deselect All") }
                Button(onClick = onCopy) { Text("Copy") }
                Button(onClick = onMove) { Text("Move") }
                Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text("Delete") }
                Button(onClick = onCompress) { Text("Compress") }
            }
        }
    }
}

// Properties dialog with metadata and permissions editor
@Composable
private fun PropertiesDialog(
    file: File,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isSymlink = try { Files.isSymbolicLink(file.toPath()) } catch (e: Exception) { false }
    val symlinkTarget = if (isSymlink) {
        try { Files.readSymbolicLink(file.toPath()).toString() } catch (e: Exception) { "Unknown" }
    } else null

    // SHA-256 state
    var sha256 by remember { mutableStateOf<String?>(null) }
    var isCalculatingHash by remember { mutableStateOf(false) }
    
    // Directory size state
    var dirSize by remember { mutableStateOf<Long?>(null) }
    var isCalculatingSize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PropertyRow("Type", if (file.isDirectory) "Directory" else "File")
                PropertyRow("Path", file.absolutePath)
                if (symlinkTarget != null) PropertyRow("Symlink →", symlinkTarget)
                
                // Size with calculate button for directories
                val currentDirSize = dirSize
                PropertyRow("Size", 
                    if (file.isDirectory) {
                        if (isCalculatingSize) "Calculating..."
                        else if (currentDirSize != null) formatSize(currentDirSize)
                        else formatSize(file.length())
                    } else {
                        formatSize(file.length())
                    }
                )
                if (file.isDirectory && !isCalculatingSize && dirSize == null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            isCalculatingSize = true
                            coroutineScope.launch {
                                val size = calculateDirectorySize(file)
                                dirSize = size
                                isCalculatingSize = false
                            }
                        }) {
                            Text("Calculate Size")
                        }
                    }
                }
                
                PropertyRow("Modified", formatDate(file.lastModified()))
                
                // Permissions display
                val perms = getPosixPermissions(file)
                PropertyRow("Permissions", perms)
                
                // SHA-256 for files
                if (!file.isDirectory) {
                    PropertyRow("SHA-256", sha256 ?: "Tap to calculate")
                    if (sha256 == null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                isCalculatingHash = true
                                coroutineScope.launch {
                                    val hash = calculateSHA256(file)
                                    sha256 = hash
                                    isCalculatingHash = false
                                }
                            }, enabled = !isCalculatingHash) {
                                Text(if (isCalculatingHash) "Calculating..." else "Calculate SHA-256")
                            }
                        }
                    }
                }

                // Permissions editor
                HorizontalDivider()
                Text("Permissions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                SimplePermissionsEditor(
                    file = file,
                    currentPermissions = perms,
                    onApply = { newPerms ->
                        applyPermissions(context, file, newPerms)
                        onRefresh()
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: Any) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SimplePermissionsEditor(
    file: File,
    currentPermissions: String,
    onApply: (String) -> Unit
) {
    var perms by remember { mutableStateOf(currentPermissions) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Owner, Group, Others rows
        PermissionTriplet("Owner", perms, 0, { perms = it; onApply(it) })
        PermissionTriplet("Group", perms, 3, { perms = it; onApply(it) })
        PermissionTriplet("Others", perms, 6, { perms = it; onApply(it) })
        
        // Quick actions - use structured helpers
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { 
                onApply(addExecutePermission(perms, ownerOnly = true))
            }) {
                Text("+x Owner")
            }
            Button(onClick = { 
                onApply(addExecutePermission(perms, ownerOnly = false))
            }) {
                Text("+x All")
            }
            OutlinedButton(onClick = { onApply("rwxr-xr-x") }) {
                Text("Reset 755")
            }
        }
    }
}

// Parse "rwxr-xr-x" -> Map<PosixFilePermission, Boolean>
private fun parsePermissions(perms: String): Map<PosixFilePermission, Boolean> {
    val result = mutableMapOf<PosixFilePermission, Boolean>()
    val triples = listOf(
        listOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
        listOf(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE),
        listOf(PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE)
    )
    perms.chunked(3).forEachIndexed { i, chunk ->
        if (chunk.length == 3) {
            triples[i].forEachIndexed { j, perm ->
                result[perm] = chunk[j] != '-'
            }
        }
    }
    return result.withDefault { false }
}

// Build permission string from map
private fun buildPermissions(map: Map<PosixFilePermission, Boolean>): String {
    val sb = StringBuilder()
    val order = listOf(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE
    )
    for (perm in order) {
        sb.append(if (map[perm] == true) perm.name.last() else '-')
    }
    return sb.toString()
}

// Add execute permission to owner or all
private fun addExecutePermission(perms: String, ownerOnly: Boolean): String {
    val map = parsePermissions(perms).toMutableMap()
    if (ownerOnly) {
        map[PosixFilePermission.OWNER_EXECUTE] = true
    } else {
        map[PosixFilePermission.OWNER_EXECUTE] = true
        map[PosixFilePermission.GROUP_EXECUTE] = true
        map[PosixFilePermission.OTHERS_EXECUTE] = true
    }
    return buildPermissions(map)
}

@Composable
private fun PermissionTriplet(
    label: String,
    currentPerms: String,
    offset: Int,
    onChange: (String) -> Unit
) {
    val r = currentPerms.getOrElse(offset) { '-' } == 'r'
    val w = currentPerms.getOrElse(offset + 1) { '-' } == 'w'
    val x = currentPerms.getOrElse(offset + 2) { '-' } == 'x'
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Checkbox(checked = r, onCheckedChange = { 
                val newPerms = currentPerms.substring(0, offset) + (if (it) "r" else "-") + currentPerms.substring(offset + 1)
                onChange(newPerms)
            })
            Text("R", style = MaterialTheme.typography.labelMedium)
            Checkbox(checked = w, onCheckedChange = { 
                val newPerms = currentPerms.substring(0, offset + 1) + (if (it) "w" else "-") + currentPerms.substring(offset + 2)
                onChange(newPerms)
            })
            Text("W", style = MaterialTheme.typography.labelMedium)
            Checkbox(checked = x, onCheckedChange = { 
                val newPerms = currentPerms.substring(0, offset + 2) + (if (it) "x" else "-") + currentPerms.substring(offset + 3)
                onChange(newPerms)
            })
            Text("X", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun getPosixPermissions(file: File): String {
    return try {
        val attrs = java.nio.file.Files.getAttribute(file.toPath(), "posix:permissions") as java.util.Set<java.nio.file.attribute.PosixFilePermission>
        val sb = StringBuilder()
        val order = listOf(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
            java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
            java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
            java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
        )
        for (perm in order) {
            sb.append(if (perm in attrs) perm.name.last() else '-')
        }
        sb.toString()
    } catch (e: Exception) {
        "rwxr-xr-x"
    }
}

// Calculate SHA-256 hash of a file (runs on IO dispatcher)
private suspend fun calculateSHA256(file: File): String = withContext(Dispatchers.IO) {
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

// Calculate directory size recursively (bounded depth, cycle detection)
private fun calculateDirectorySize(dir: File, maxDepth: Int = 20): Long {
    var totalSize = 0L
    val visited = mutableSetOf<String>()
    val stack = mutableListOf<Pair<File, Int>>()
    stack.add(dir to 0)

    while (stack.isNotEmpty()) {
        val (currentDir, depth) = stack.removeAt(stack.lastIndex)
        if (depth > maxDepth) continue

        val canonicalPath = try { currentDir.canonicalPath } catch (e: Exception) { currentDir.absolutePath }
        if (!visited.add(canonicalPath)) continue

        val files = currentDir.listFiles() ?: continue
        for (file in files) {
            if (file.isDirectory) {
                stack.add(file to (depth + 1))
            } else {
                totalSize += file.length()
            }
        }
    }
    return totalSize
}

private fun applyPermissions(context: Context, file: File, perms: String) {
    try {
        val permSet = mutableSetOf<PosixFilePermission>()
        val triples = listOf(
            listOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
            listOf(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE),
            listOf(PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE)
        )
        perms.chunked(3).forEachIndexed { i, chunk ->
            if (chunk.length == 3) {
                triples[i].forEachIndexed { j, perm ->
                    if (chunk[j] != '-') permSet.add(perm)
                }
            }
        }
        Files.setPosixFilePermissions(file.toPath(), permSet)
        val mode = Integer.parseInt(
            perms.replace("r", "4").replace("w", "2").replace("x", "1").replace("-", "0"), 8
        )
        Toast.makeText(context, "Permissions applied: ${String.format("%03o", mode)}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to apply permissions: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ============================================================
// ARCHIVE UTILITIES (Phase 2c)
// ============================================================

enum class ArchiveType(val extensions: List<String>, val description: String, val creatable: Boolean = true) {
    ZIP(listOf("zip"), "ZIP Archive"),
    TAR_GZ(listOf("tar.gz", "tgz"), "Compressed TAR (gzip)"),
    TAR_XZ(listOf("tar.xz", "txz"), "Compressed TAR (xz)"),
    TAR(listOf("tar"), "TAR Archive"),
    DEB(listOf("deb"), "Debian Package", false),
    UNKNOWN(emptyList(), "Unknown", false)
}

data class ArchiveEntry(
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val permissions: String? = null
)

fun detectArchiveType(file: File): ArchiveType {
    val name = file.name.lowercase()
    return when {
        name.endsWith(".zip") -> ArchiveType.ZIP
        name.endsWith(".tar.gz") || name.endsWith(".tgz") -> ArchiveType.TAR_GZ
        name.endsWith(".tar.xz") || name.endsWith(".txz") -> ArchiveType.TAR_XZ
        name.endsWith(".tar") -> ArchiveType.TAR
        name.endsWith(".deb") -> ArchiveType.DEB
        else -> ArchiveType.UNKNOWN
    }
}

fun isArchiveFile(file: File): Boolean {
    return detectArchiveType(file) != ArchiveType.UNKNOWN
}

// List archive contents without extracting
fun listArchiveContents(file: File): List<ArchiveEntry> {
    val type = detectArchiveType(file)
    return when (type) {
        ArchiveType.ZIP -> listZipContents(file)
        ArchiveType.TAR_GZ -> listTarContents(file, true)
        ArchiveType.TAR_XZ -> listTarContents(file, true, true)
        ArchiveType.TAR -> listTarContents(file, false)
        ArchiveType.DEB -> listDebContents(file)
        else -> emptyList()
    }
}

// Extract archive to destination directory
fun extractArchive(file: File, destDir: File, onProgress: ((String, Int, Int) -> Unit)? = null): Boolean {
    val type = detectArchiveType(file)
    return when (type) {
        ArchiveType.ZIP -> extractZip(file, destDir, onProgress)
        ArchiveType.TAR_GZ -> extractTar(file, destDir, true, onProgress)
        ArchiveType.TAR_XZ -> extractTar(file, destDir, true, onProgress, true)
        ArchiveType.TAR -> extractTar(file, destDir, false, onProgress)
        ArchiveType.DEB -> extractDeb(file, destDir, onProgress)
        else -> false
    }
}

// Create archive from files
fun createArchive(files: List<File>, destFile: File, type: ArchiveType, onProgress: ((String, Int, Int) -> Unit)? = null): Boolean {
    if (!type.creatable) return false
    return when (type) {
        ArchiveType.ZIP -> createZip(files, destFile, onProgress)
        ArchiveType.TAR_GZ -> createTarGz(files, destFile, onProgress)
        ArchiveType.TAR -> createTar(files, destFile, onProgress)
        else -> false
    }
}

// ===== ZIP Implementation =====
private fun listZipContents(file: File): List<ArchiveEntry> {
    val entries = mutableListOf<ArchiveEntry>()
    try {
        java.util.zip.ZipFile(file).use { zipFile ->
            val enumeration = zipFile.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                entries.add(ArchiveEntry(
                    name = entry.name,
                    size = entry.size,
                    isDirectory = entry.isDirectory,
                    lastModified = entry.time,
                    permissions = null // ZIP doesn't store POSIX perms reliably
                ))
            }
        }
    } catch (e: Exception) {
        // Return empty list on error
    }
    return entries
}

// True when destFile resolves to a path inside destDirCanon (canonicalized once by the caller).
// Guards against zip/tar slip path traversal via ".." or absolute entry names.
private fun isPathInside(destDirCanon: String, destFile: File): Boolean {
    return try {
        val canon = destFile.canonicalPath
        canon == destDirCanon || canon.startsWith(destDirCanon + File.separator)
    } catch (e: Exception) {
        false
    }
}

private fun extractZip(file: File, destDir: File, onProgress: ((String, Int, Int) -> Unit)?): Boolean {
    try {
        destDir.mkdirs()
        val destDirCanon = destDir.canonicalPath
        java.util.zip.ZipFile(file).use { zipFile ->
            val entries = zipFile.entries().asSequence().toList()
            var processed = 0
            for (entry in entries) {
                val destFile = File(destDir, entry.name)
                if (!isPathInside(destDirCanon, destFile)) {
                    // Entry escapes destination directory — skip (zip/tar slip)
                    continue
                }
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                processed++
                onProgress?.invoke(entry.name, processed, entries.size)
            }
        }
        return true
    } catch (e: Exception) {
        return false
    }
}

private fun createZip(files: List<File>, destFile: File, onProgress: ((String, Int, Int) -> Unit)?): Boolean {
    try {
        destFile.parentFile?.mkdirs()
        java.util.zip.ZipOutputStream(destFile.outputStream()).use { zipOut ->
            var processed = 0
            for (file in files) {
                addToZip(zipOut, file, file.name)
                processed++
                onProgress?.invoke(file.name, processed, files.size)
            }
        }
        return true
    } catch (e: Exception) {
        return false
    }
}

private fun addToZip(zipOut: java.util.zip.ZipOutputStream, file: File, baseName: String) {
    if (file.isDirectory) {
        val entry = java.util.zip.ZipEntry(baseName + "/")
        zipOut.putNextEntry(entry)
        zipOut.closeEntry()
        file.listFiles()?.forEach { child ->
            addToZip(zipOut, child, baseName + "/" + child.name)
        }
    } else {
        val entry = java.util.zip.ZipEntry(baseName)
        zipOut.putNextEntry(entry)
        file.inputStream().use { input ->
            input.copyTo(zipOut)
        }
        zipOut.closeEntry()
    }
}

// ===== TAR Implementation (Apache Commons Compress) =====
private fun listTarContents(file: File, compressed: Boolean, useXz: Boolean = false): List<ArchiveEntry> {
    val entries = mutableListOf<ArchiveEntry>()
    try {
        var inputStream: java.io.InputStream = file.inputStream()
        if (useXz) {
            inputStream = XZCompressorInputStream(inputStream)
        } else if (compressed) {
            inputStream = GzipCompressorInputStream(inputStream)
        }
        val tarIn = TarArchiveInputStream(inputStream)
        var entry: TarArchiveEntry? = tarIn.getNextTarEntry()
        while (entry != null) {
            val isDir = entry.isDirectory
            entries.add(ArchiveEntry(
                name = entry.name,
                size = if (isDir) 0 else entry.size,
                isDirectory = isDir,
                lastModified = entry.lastModifiedTime.toMillis(),
                permissions = entry.mode.toString(8)
            ))
            if (!isDir) {
                IOUtils.skip(tarIn, entry.size)
            }
            entry = tarIn.getNextTarEntry()
        }
        tarIn.close()
    } catch (e: Exception) {
        // Return empty list on error
    }
    return entries
}

private fun extractTar(file: File, destDir: File, compressed: Boolean, onProgress: ((String, Int, Int) -> Unit)?, useXz: Boolean = false): Boolean {
    try {
        destDir.mkdirs()
        val destDirCanon = destDir.canonicalPath
        var inputStream: java.io.InputStream = file.inputStream()
        if (useXz) {
            inputStream = XZCompressorInputStream(inputStream)
        } else if (compressed) {
            inputStream = GzipCompressorInputStream(inputStream)
        }
        val tarIn = TarArchiveInputStream(inputStream)
        var entry: TarArchiveEntry? = tarIn.getNextTarEntry()
        var processed = 0
        val entries = mutableListOf<TarArchiveEntry>()
        while (entry != null) {
            entries.add(entry)
            entry = tarIn.getNextTarEntry()
        }
        for (entry in entries) {
            val destFile = File(destDir, entry.name)
            if (!isPathInside(destDirCanon, destFile)) {
                // Entry escapes destination directory — skip (zip/tar slip)
                continue
            }
            if (entry.isDirectory) {
                destFile.mkdirs()
            } else {
                destFile.parentFile?.mkdirs()
                destFile.outputStream().use { out ->
                    IOUtils.copy(tarIn, out)
                }
            }
            processed++
            onProgress?.invoke(entry.name, processed, entries.size)
        }
        tarIn.close()
        return true
    } catch (e: Exception) {
        return false
    }
}

private fun createTarGz(files: List<File>, destFile: File, onProgress: ((String, Int, Int) -> Unit)?): Boolean {
    try {
        destFile.parentFile?.mkdirs()
        val tarOut = TarArchiveOutputStream(GzipCompressorOutputStream(destFile.outputStream()))
        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        var processed = 0
        for (file in files) {
            addToTar(tarOut, file, file.name)
            processed++
            onProgress?.invoke(file.name, processed, files.size)
        }
        tarOut.finish()
        tarOut.close()
        return true
    } catch (e: Exception) {
        return false
    }
}

private fun createTar(files: List<File>, destFile: File, onProgress: ((String, Int, Int) -> Unit)?): Boolean {
    try {
        destFile.parentFile?.mkdirs()
        val tarOut = TarArchiveOutputStream(destFile.outputStream())
        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        var processed = 0
        for (file in files) {
            addToTar(tarOut, file, file.name)
            processed++
            onProgress?.invoke(file.name, processed, files.size)
        }
        tarOut.finish()
        tarOut.close()
        return true
    } catch (e: Exception) {
        return false
    }
}

private fun addToTar(tarOut: TarArchiveOutputStream, file: File, baseName: String) {
    val entry = TarArchiveEntry(file, baseName)
    if (file.isDirectory) {
        entry.mode = 493 // 0o755
    }
    tarOut.putArchiveEntry(entry)
    if (!file.isDirectory) {
        file.inputStream().use { input ->
            IOUtils.copy(input, tarOut)
        }
    }
    tarOut.closeArchiveEntry()
    if (file.isDirectory) {
        file.listFiles()?.forEach { child ->
            addToTar(tarOut, child, baseName + "/" + child.name)
        }
    }
}

// ===== DEB Implementation (Apache Commons Compress) =====
private fun listDebContents(file: File): List<ArchiveEntry> {
    val entries = mutableListOf<ArchiveEntry>()
    try {
        val arIn = ArArchiveInputStream(file.inputStream())
        var entry: ArArchiveEntry? = arIn.getNextArEntry()
        while (entry != null) {
            if (entry.name == "data.tar.gz" || entry.name == "data.tar.xz" || entry.name == "data.tar") {
                // Read the data.tar.* contents
                val dataEntries = when {
                    entry.name.endsWith(".gz") -> listTarContentsFromStream(arIn, true, false)
                    entry.name.endsWith(".xz") -> listTarContentsFromStream(arIn, true, true)
                    else -> listTarContentsFromStream(arIn, false, false)
                }
                entries.addAll(dataEntries)
                break
            }
            // Skip other entries (debian-binary, control.tar.gz)
            IOUtils.skip(arIn, entry.size)
            entry = arIn.getNextArEntry()
        }
        arIn.close()
    } catch (e: Exception) {
        // Ignore
    }
    return entries
}

private fun listTarContentsFromStream(inputStream: java.io.InputStream, compressed: Boolean, useXz: Boolean): List<ArchiveEntry> {
    val entries = mutableListOf<ArchiveEntry>()
    try {
        var tarIn: TarArchiveInputStream
        if (useXz) {
            tarIn = TarArchiveInputStream(XZCompressorInputStream(inputStream))
        } else if (compressed) {
            tarIn = TarArchiveInputStream(GzipCompressorInputStream(inputStream))
        } else {
            tarIn = TarArchiveInputStream(inputStream)
        }
        var entry: TarArchiveEntry? = tarIn.getNextTarEntry()
        while (entry != null) {
            val isDir = entry.isDirectory
            entries.add(ArchiveEntry(
                name = entry.name,
                size = if (isDir) 0 else entry.size,
                isDirectory = isDir,
                lastModified = entry.lastModifiedTime.toMillis(),
                permissions = entry.mode.toString(8)
            ))
            if (!isDir) {
                IOUtils.skip(tarIn, entry.size)
            }
            entry = tarIn.getNextTarEntry()
        }
        tarIn.close()
    } catch (e: Exception) {
        // Ignore
    }
    return entries
}

private fun extractDeb(file: File, destDir: File, onProgress: ((String, Int, Int) -> Unit)?): Boolean {
    try {
        destDir.mkdirs()
        val arIn = ArArchiveInputStream(file.inputStream())
        var entry: ArArchiveEntry? = arIn.getNextArEntry()
        while (entry != null) {
            if (entry.name == "data.tar.gz" || entry.name == "data.tar.xz" || entry.name == "data.tar") {
                val compressed = entry.name.endsWith(".gz")
                val useXz = entry.name.endsWith(".xz")
                return extractTarFromStream(arIn, destDir, compressed, useXz, onProgress)
            }
            IOUtils.skip(arIn, entry.size)
            entry = arIn.getNextArEntry()
        }
        arIn.close()
        return false
    } catch (e: Exception) {
        return false
    }
}

private fun extractTarFromStream(inputStream: java.io.InputStream, destDir: File, compressed: Boolean, useXz: Boolean, onProgress: ((String, Int, Int) -> Unit)?): Boolean {
    try {
        destDir.mkdirs()
        val destDirCanon = destDir.canonicalPath
        var tarIn: TarArchiveInputStream
        if (useXz) {
            tarIn = TarArchiveInputStream(XZCompressorInputStream(inputStream))
        } else if (compressed) {
            tarIn = TarArchiveInputStream(GzipCompressorInputStream(inputStream))
        } else {
            tarIn = TarArchiveInputStream(inputStream)
        }
        var entry: TarArchiveEntry? = tarIn.getNextTarEntry()
        val entries = mutableListOf<TarArchiveEntry>()
        while (entry != null) {
            entries.add(entry)
            entry = tarIn.getNextTarEntry()
        }
        var processed = 0
        for (entry in entries) {
            val destFile = File(destDir, entry.name)
            if (!isPathInside(destDirCanon, destFile)) {
                // Entry escapes destination directory — skip (zip/tar slip)
                continue
            }
            if (entry.isDirectory) {
                destFile.mkdirs()
            } else {
                destFile.parentFile?.mkdirs()
                destFile.outputStream().use { out ->
                    IOUtils.copy(tarIn, out)
                }
            }
            processed++
            onProgress?.invoke(entry.name, processed, entries.size)
        }
        tarIn.close()
        return true
    } catch (e: Exception) {
        return false
    }
}

// ===== Archive Dialog Helpers =====
@Composable
fun ArchiveExtractDialog(
    file: File,
    onDismiss: () -> Unit,
    onExtract: (File) -> Unit
) {
    val context = LocalContext.current
    var destDir by remember { mutableStateOf(file.parentFile?.absolutePath ?: "") }
    var extractToSubfolder by remember { mutableStateOf(true) }
    
    // Compute folder name from archive type
    val archiveType = detectArchiveType(file)
    val folderName = if (archiveType != ArchiveType.UNKNOWN) {
        val longestExt = archiveType.extensions.maxByOrNull { it.length } ?: ""
        file.name.removeSuffix(longestExt)
    } else {
        file.name
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extract ${file.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = destDir,
                    onValueChange = { destDir = it },
                    label = { Text("Destination") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    Checkbox(checked = extractToSubfolder, onCheckedChange = { extractToSubfolder = it })
                    Text("Extract to '$folderName/'")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val baseDest = File(destDir)
                val finalDest = if (extractToSubfolder) {
                    File(baseDest, folderName)
                } else {
                    baseDest
                }
                onExtract(finalDest)
            }) { Text("Extract") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ArchiveCompressDialog(
    files: List<File>,
    onDismiss: () -> Unit,
    onCompress: (File, ArchiveType) -> Unit
) {
    val context = LocalContext.current
    var destName by remember { mutableStateOf("archive.zip") }
    var archiveType by remember { mutableStateOf(ArchiveType.ZIP) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress ${files.size} item(s)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = destName,
                    onValueChange = { destName = it },
                    label = { Text("Archive name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Format", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    ArchiveType.values().filter { it.creatable }.forEach { type ->
                        val selected = (archiveType == type)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { archiveType = type }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { archiveType = type })
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(type.description, style = MaterialTheme.typography.bodyMedium)
                                Text(type.extensions.joinToString(", ") { ".$it" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val destFile = File(files.first().parentFile, destName)
                onCompress(destFile, archiveType)
            }) { Text("Compress") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
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

// Open terminal in directory
private fun openTerminalHere(context: Context, dir: File) {
    val intent = Intent(context, TermuxActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("OPEN_FILE_PATH", dir.absolutePath)
        putExtra("CD_TO_PATH", dir.absolutePath)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    context.startActivity(intent)
    (context as? Activity)?.finish()
}

// Cd the active terminal session to a path (TermuxActivity handles the CD_TO_PATH extra)
private fun cdTerminalTo(context: Context, path: String) {
    val intent = Intent(context, TermuxActivity::class.java).apply {
        putExtra("CD_TO_PATH", path)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    context.startActivity(intent)
    (context as? Activity)?.finish()
}

// Install APK via system package installer
private fun installApk(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(context, "Opening APK installer...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to install APK: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Install DEB via pkg/dpkg in Termux terminal
private fun installDeb(context: Context, file: File) {
    val intent = Intent(context, TermuxActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("OPEN_FILE_PATH", file.absolutePath)
        putExtra("CD_TO_PATH", file.parentFile?.absolutePath ?: "/")
        putExtra("INITIAL_COMMAND", "pkg install ./${file.name}")
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    context.startActivity(intent)
    (context as? Activity)?.finish()
    Toast.makeText(context, "Opening Termux to install DEB...", Toast.LENGTH_SHORT).show()
}

// Set image as wallpaper/background
private suspend fun setWallpaperFromFile(context: Context, file: File) {
    // Resolve symlinks to get the actual file
    val resolvedFile = try {
        file.canonicalFile
    } catch (e: Exception) {
        file
    }

    // Decode the file directly — same path as the image viewer, no URI/permission layer
    val bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(resolvedFile) }
    if (bitmap == null) {
        Toast.makeText(context, "Not a readable image", Toast.LENGTH_SHORT).show()
        return
    }

    val success = BackgroundImageStore.saveFromBitmap(context, bitmap)
    if (success) {
        Toast.makeText(context, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
        // Refresh UI to show new wallpaper
        (context as? Activity)?.recreate()
    } else {
        bitmap.recycle()
        Toast.makeText(context, "Failed to save wallpaper", Toast.LENGTH_SHORT).show()
    }
}

// Copy file helper (preserves symlinks)
private fun copyFile(src: File, dest: File): Boolean {
    return try {
        dest.parentFile?.mkdirs()
        if (Files.isSymbolicLink(src.toPath())) {
            val target = Files.readSymbolicLink(src.toPath())
            Files.createSymbolicLink(dest.toPath(), target)
        } else {
            src.copyTo(dest, true)
        }
        true
    } catch (e: Exception) {
        false
    }
}

// Copy directory recursively (preserves symlinks, doesn't follow them)
private fun copyDirectoryRecursive(src: File, dest: File): Boolean {
    return try {
        // No FOLLOW_LINKS: symlinks are visited as links, not followed, so symlink loops
        // (e.g. ln -s ..) cannot recurse forever. Files.copy below uses NOFOLLOW_LINKS.
        Files.walkFileTree(src.toPath(), EnumSet.noneOf(java.nio.file.FileVisitOption::class.java), Integer.MAX_VALUE, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun preVisitDirectory(dir: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                val relPath = src.toPath().relativize(dir)
                val targetDir = dest.toPath().resolve(relPath)
                if (!Files.exists(targetDir)) {
                    Files.createDirectory(targetDir)
                }
                return java.nio.file.FileVisitResult.CONTINUE
            }

            override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                val relPath = src.toPath().relativize(file)
                val targetFile = dest.toPath().resolve(relPath)
                Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                return java.nio.file.FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: java.nio.file.Path, exc: IOException): java.nio.file.FileVisitResult {
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
        true
    } catch (e: Exception) {
        false
    }
}

// Move directory recursively (preserves symlinks)
private fun moveDirectoryRecursive(src: File, dest: File): Boolean {
    return try {
        if (src.renameTo(dest)) {
            true
        } else {
            // Fallback: copy then delete (preserves symlinks)
            if (copyDirectoryRecursive(src, dest)) {
                deleteRecursive(src)
                true
            } else {
                false
            }
        }
    } catch (e: Exception) {
        false
    }
}
