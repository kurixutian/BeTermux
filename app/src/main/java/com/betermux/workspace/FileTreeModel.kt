package com.betermux.workspace

import android.content.Context
import android.os.Build
import android.os.Environment
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RequiresApi
import com.termux.shared.android.PermissionUtils
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Structured error categories for lazy project file-tree loading so the UI can
 * render actionable recovery cards instead of crashing or showing blank states.
 */
enum class FileTreeErrorType {
    ENOENT,
    EACCES,
    UNMOUNTED_STORAGE,
    STORAGE_PERMISSION_MISSING,
    IO_ERROR
}

data class FileTreeError(
    val type: FileTreeErrorType,
    val message: String,
    val path: String = ""
)

class FileTreeException(val error: FileTreeError) : IOException(error.message)

data class FileTreeNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val depth: Int,
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val error: FileTreeError? = null,
    val children: List<FileTreeNode> = emptyList(),
    val symlinkCycleDetected: Boolean = false
)

/**
 * Lazy single-directory filesystem repository with symlink-cycle/depth protection,
 * API 21+ symlink detection, and structured error mapping.
 */
object FileTreeRepository {

    const val DEFAULT_MAX_DEPTH = 32

    suspend fun loadChildren(
        dir: File,
        depth: Int,
        showHidden: Boolean,
        ancestorCanonicalPaths: Set<String>,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        context: Context? = null
    ): Result<List<FileTreeNode>> = withContext(Dispatchers.IO) {
        val extState = readExternalStorageStateSafe()
        val hasStoragePerm = context?.let { readStoragePermissionSafe(it) }
        val path = dir.absolutePath

        if (isExternalStoragePath(path) && extState != null && extState != Environment.MEDIA_MOUNTED) {
            return@withContext Result.failure(
                FileTreeException(classifyDirectoryError(dir, hasStoragePerm, extState))
            )
        }
        if (isTermuxStoragePath(path) && hasStoragePerm == false) {
            return@withContext Result.failure(
                FileTreeException(classifyDirectoryError(dir, hasStoragePerm, extState))
            )
        }
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
            return@withContext Result.failure(
                FileTreeException(classifyDirectoryError(dir, hasStoragePerm, extState))
            )
        }

        val entries = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            return@withContext Result.failure(
                FileTreeException(
                    FileTreeError(
                        type = FileTreeErrorType.EACCES,
                        message = e.message ?: "Permission denied reading ${dir.name.ifBlank { path }}",
                        path = path
                    )
                )
            )
        } catch (e: Exception) {
            return@withContext Result.failure(
                FileTreeException(
                    FileTreeError(
                        type = FileTreeErrorType.IO_ERROR,
                        message = e.message ?: "Unable to read ${dir.name.ifBlank { path }}",
                        path = path
                    )
                )
            )
        }

        if (entries == null) {
            return@withContext Result.failure(
                FileTreeException(classifyDirectoryError(dir, hasStoragePerm, extState))
            )
        }

        val nodes = entries
            .asSequence()
            .filter { entry -> showHidden || !entry.name.startsWith(".") }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { entry ->
                val isDir = entry.isDirectory
                val isSymlink = isSymbolicLinkCompat(entry)
                val canonical = canonicalPathOrNull(entry)
                val cycleOrDepth = isDir && (
                    depth >= maxDepth ||
                        (canonical != null && canonical in ancestorCanonicalPaths)
                    )
                FileTreeNode(
                    path = entry.absolutePath,
                    name = entry.name.ifBlank { entry.absolutePath },
                    isDirectory = isDir,
                    isSymlink = isSymlink,
                    depth = depth,
                    symlinkCycleDetected = cycleOrDepth
                )
            }
            .toList()

        Result.success(nodes)
    }

    suspend fun createFile(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        val cleanName = validateEntryName(name).getOrElse { return@withContext Result.failure(it) }
        if (!parent.exists() || !parent.isDirectory) {
            return@withContext Result.failure(IOException("Parent directory does not exist: ${parent.absolutePath}"))
        }
        val target = File(parent, cleanName)
        if (target.exists()) {
            return@withContext Result.failure(IOException("Already exists: $cleanName"))
        }
        try {
            if (target.createNewFile()) {
                Result.success(target)
            } else {
                Result.failure(IOException("Could not create file: $cleanName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        val cleanName = validateEntryName(name).getOrElse { return@withContext Result.failure(it) }
        if (!parent.exists() || !parent.isDirectory) {
            return@withContext Result.failure(IOException("Parent directory does not exist: ${parent.absolutePath}"))
        }
        val target = File(parent, cleanName)
        if (target.exists()) {
            return@withContext Result.failure(IOException("Already exists: $cleanName"))
        }
        try {
            if (target.mkdir()) {
                Result.success(target)
            } else {
                Result.failure(IOException("Could not create folder: $cleanName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes [target] safely: symlinks are unlinked directly without following into
     * their target directory, preventing accidental deletion outside the project tree.
     */
    suspend fun deleteEntry(target: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (deleteRecursivelyNoFollow(target)) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Failed to delete ${target.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun classifyDirectoryError(
        dir: File,
        storagePermissionGranted: Boolean?,
        externalStorageState: String?
    ): FileTreeError {
        val path = dir.absolutePath
        if (isExternalStoragePath(path)) {
            if (externalStorageState != null && externalStorageState != Environment.MEDIA_MOUNTED) {
                return FileTreeError(
                    type = FileTreeErrorType.UNMOUNTED_STORAGE,
                    message = "Shared storage is currently unmounted ($externalStorageState).",
                    path = path
                )
            }
            if (storagePermissionGranted == false) {
                return FileTreeError(
                    type = FileTreeErrorType.STORAGE_PERMISSION_MISSING,
                    message = "Storage permission is required to browse shared storage.",
                    path = path
                )
            }
        }
        if (isTermuxStoragePath(path)) {
            val storageRoot = termuxStorageRootPath()
            if (storagePermissionGranted == false || (path == storageRoot && !dir.exists())) {
                return FileTreeError(
                    type = FileTreeErrorType.STORAGE_PERMISSION_MISSING,
                    message = "Shared storage isn't set up or permission is missing. Run termux-setup-storage or grant storage permission.",
                    path = path
                )
            }
        }
        if (!dir.exists()) {
            return FileTreeError(
                type = FileTreeErrorType.ENOENT,
                message = "Directory does not exist: $path",
                path = path
            )
        }
        if (!dir.isDirectory) {
            return FileTreeError(
                type = FileTreeErrorType.IO_ERROR,
                message = "Path is not a directory: $path",
                path = path
            )
        }
        if (!dir.canRead()) {
            return FileTreeError(
                type = FileTreeErrorType.EACCES,
                message = "Permission denied reading ${dir.name.ifBlank { path }}",
                path = path
            )
        }
        return FileTreeError(
            type = FileTreeErrorType.IO_ERROR,
            message = "Unable to list files in ${dir.name.ifBlank { path }}",
            path = path
        )
    }

    private fun deleteRecursivelyNoFollow(file: File): Boolean {
        if (isSymbolicLinkCompat(file)) {
            return file.delete()
        }
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteRecursivelyNoFollow(child)) return false
                }
            }
        }
        return if (file.exists()) file.delete() else true
    }

    private fun validateEntryName(name: String): Result<String> {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> Result.failure(IllegalArgumentException("Name cannot be empty"))
            trimmed == "." || trimmed == ".." -> Result.failure(IllegalArgumentException("Invalid name: $trimmed"))
            trimmed.contains('/') || trimmed.contains('\u0000') ->
                Result.failure(IllegalArgumentException("Name cannot contain '/'"))
            else -> Result.success(trimmed)
        }
    }

    internal fun canonicalPathOrNull(file: File): String? = try {
        file.canonicalPath
    } catch (_: Exception) {
        null
    }

    internal fun isSymbolicLinkCompat(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return isSymbolicLinkNio(file)
        }
        return try {
            val stat = Os.lstat(file.absolutePath)
            OsConstants.S_ISLNK(stat.st_mode)
        } catch (_: Throwable) {
            try {
                val parentCanonical = file.parentFile?.canonicalPath
                val resolvedThroughParent = if (parentCanonical != null) {
                    File(parentCanonical, file.name).absolutePath
                } else {
                    file.absolutePath
                }
                file.canonicalPath != resolvedThroughParent
            } catch (_: Exception) {
                false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun isSymbolicLinkNio(file: File): Boolean = try {
        Files.isSymbolicLink(file.toPath())
    } catch (_: Exception) {
        false
    }

    private fun isExternalStoragePath(path: String): Boolean {
        return path == "/sdcard" ||
            path.startsWith("/sdcard/") ||
            path == "/storage" ||
            path.startsWith("/storage/")
    }

    private fun termuxStorageRootPath(): String =
        "${TermuxConstants.TERMUX_HOME_DIR_PATH}/storage"

    private fun isTermuxStoragePath(path: String): Boolean {
        val root = termuxStorageRootPath()
        return path == root || path.startsWith("$root/")
    }

    private fun readExternalStorageStateSafe(): String? = try {
        Environment.getExternalStorageState()
    } catch (_: Throwable) {
        null
    }

    private fun readStoragePermissionSafe(context: Context): Boolean? = try {
        PermissionUtils.checkStoragePermission(
            context,
            PermissionUtils.isLegacyExternalStoragePossible(context)
        )
    } catch (_: Throwable) {
        null
    }
}

internal fun flattenVisibleTree(nodes: List<FileTreeNode>): List<FileTreeNode> {
    if (nodes.isEmpty()) return emptyList()
    val result = ArrayList<FileTreeNode>(nodes.size)
    fun append(list: List<FileTreeNode>) {
        for (node in list) {
            result.add(node)
            if (node.isDirectory && node.isExpanded) {
                append(node.children)
            }
        }
    }
    append(nodes)
    return result
}

internal fun updateTreeNode(
    nodes: List<FileTreeNode>,
    targetPath: String,
    transform: (FileTreeNode) -> FileTreeNode
): List<FileTreeNode> = nodes.map { node ->
    when {
        node.path == targetPath -> transform(node)
        node.isDirectory && node.children.isNotEmpty() ->
            node.copy(children = updateTreeNode(node.children, targetPath, transform))
        else -> node
    }
}

internal fun collectAncestorCanonicalPaths(
    rootPath: String,
    nodes: List<FileTreeNode>,
    targetPath: String
): Set<String> {
    val ancestors = LinkedHashSet<String>()
    FileTreeRepository.canonicalPathOrNull(File(rootPath))?.let { ancestors.add(it) }

    fun findTrail(list: List<FileTreeNode>): Boolean {
        for (node in list) {
            if (node.path == targetPath) {
                FileTreeRepository.canonicalPathOrNull(File(node.path))?.let { ancestors.add(it) }
                return true
            }
            if (node.isDirectory && node.children.isNotEmpty() && findTrail(node.children)) {
                FileTreeRepository.canonicalPathOrNull(File(node.path))?.let { ancestors.add(it) }
                return true
            }
        }
        return false
    }

    findTrail(nodes)
    return ancestors
}
