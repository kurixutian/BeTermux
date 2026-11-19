package com.betermux.workspace

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.betermux.compose.BeTermuxComposeTheme
import com.termux.app.activities.openExternalEditor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h915dp")
class FileTreeRepositoryAndDrawerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var tempRoot: File
    private val chmodRestore = mutableListOf<File>()

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("betermux_file_tree_test").toFile()
    }

    @After
    fun tearDown() {
        chmodRestore.forEach { it.setReadable(true) }
        tempRoot.deleteRecursively()
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
    }

    @Test
    fun loadChildrenIsLazySortsDirsFirstAndRespectsShowHidden() = runBlocking {
        val subDir = File(tempRoot, "src").apply { mkdirs() }
        File(subDir, "nested.kt").writeText("fun nested() {}")
        File(tempRoot, "alpha_dir").apply { mkdirs() }
        File(tempRoot, "README.md").writeText("# Hello")
        File(tempRoot, "a_file.txt").writeText("hi")
        File(tempRoot, ".env").writeText("SECRET=1")
        File(tempRoot, ".git").apply { mkdirs() }

        val hiddenOff = FileTreeRepository.loadChildren(
            dir = tempRoot,
            depth = 0,
            showHidden = false,
            ancestorCanonicalPaths = setOf(tempRoot.canonicalPath)
        ).getOrThrow()

        assertEquals(listOf("alpha_dir", "src", "a_file.txt", "README.md"), hiddenOff.map { it.name })
        assertTrue(hiddenOff[0].isDirectory)
        assertTrue(hiddenOff[1].isDirectory)
        assertFalse(hiddenOff[2].isDirectory)
        // Lazy: subdirectories do not eagerly populate children
        assertTrue(hiddenOff[1].children.isEmpty())
        assertFalse(hiddenOff[1].isExpanded)

        val hiddenOn = FileTreeRepository.loadChildren(
            dir = tempRoot,
            depth = 0,
            showHidden = true,
            ancestorCanonicalPaths = setOf(tempRoot.canonicalPath)
        ).getOrThrow()

        assertEquals(
            listOf(".git", "alpha_dir", "src", ".env", "a_file.txt", "README.md"),
            hiddenOn.map { it.name }
        )
    }

    @Test
    fun symlinkCycleAndMaxDepthAreFlaggedWithoutInfiniteRecursion() = runBlocking {
        val sub = File(tempRoot, "sub").apply { mkdirs() }
        val loopLink = File(sub, "loop_to_root")
        Files.createSymbolicLink(loopLink.toPath(), tempRoot.toPath())

        val ancestors = setOf(tempRoot.canonicalPath, sub.canonicalPath)
        val nodes = FileTreeRepository.loadChildren(
            dir = sub,
            depth = 1,
            showHidden = false,
            ancestorCanonicalPaths = ancestors
        ).getOrThrow()

        val loopNode = nodes.first { it.name == "loop_to_root" }
        assertTrue(loopNode.isDirectory)
        assertTrue(loopNode.isSymlink)
        assertTrue(loopNode.symlinkCycleDetected)
        assertTrue(loopNode.children.isEmpty())

        // Depth >= maxDepth also marks directories as cycle/depth-capped
        val deepDir = File(sub, "deep").apply { mkdirs() }
        val deepNodes = FileTreeRepository.loadChildren(
            dir = sub,
            depth = 32,
            showHidden = false,
            ancestorCanonicalPaths = emptySet(),
            maxDepth = 32
        ).getOrThrow()
        val deepNode = deepNodes.first { it.name == "deep" }
        assertTrue(deepNode.symlinkCycleDetected)
        assertTrue(deepDir.exists())
    }

    @Test
    fun missingUnreadableUnmountedAndStoragePermissionReturnStructuredErrors() = runBlocking {
        // 1. ENOENT
        val missing = File(tempRoot, "does_not_exist")
        val enoentRes = FileTreeRepository.loadChildren(missing, 0, false, emptySet())
        assertTrue(enoentRes.isFailure)
        val enoentErr = (enoentRes.exceptionOrNull() as? FileTreeException)?.error
        assertNotNull(enoentErr)
        assertEquals(FileTreeErrorType.ENOENT, enoentErr?.type)

        // 2. EACCES (when running non-root, chmod 000 blocks reading)
        val locked = File(tempRoot, "locked").apply { mkdirs() }
        chmodRestore.add(locked)
        if (locked.setReadable(false) && !locked.canRead()) {
            val eaccesRes = FileTreeRepository.loadChildren(locked, 0, false, emptySet())
            assertTrue(eaccesRes.isFailure)
            val eaccesErr = (eaccesRes.exceptionOrNull() as? FileTreeException)?.error
            assertEquals(FileTreeErrorType.EACCES, eaccesErr?.type)
        }

        // 3. UNMOUNTED_STORAGE for /storage/emulated/... when external storage unmounted
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)
        val unmountedRes = FileTreeRepository.loadChildren(
            dir = File("/storage/emulated/0/Projects"),
            depth = 0,
            showHidden = false,
            ancestorCanonicalPaths = emptySet(),
            context = RuntimeEnvironment.getApplication()
        )
        assertTrue(unmountedRes.isFailure)
        val unmountedErr = (unmountedRes.exceptionOrNull() as? FileTreeException)?.error
        assertEquals(FileTreeErrorType.UNMOUNTED_STORAGE, unmountedErr?.type)

        // 4. STORAGE_PERMISSION_MISSING for ~/storage/... when permission is absent
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        val termuxStorageMissing = File("/data/data/com.termux/files/home/storage/downloads")
        val permErr = FileTreeRepository.classifyDirectoryError(
            dir = termuxStorageMissing,
            storagePermissionGranted = false,
            externalStorageState = Environment.MEDIA_MOUNTED
        )
        assertEquals(FileTreeErrorType.STORAGE_PERMISSION_MISSING, permErr.type)
    }

    @Test
    fun deleteEntryRemovesSymlinkWithoutDeletingTargetContents() = runBlocking {
        val outsideTarget = File(tempRoot, "outside_keep").apply { mkdirs() }
        val keptFile = File(outsideTarget, "important.txt").apply { writeText("keep me") }
        val workDir = File(tempRoot, "work").apply { mkdirs() }
        val symlinkDir = File(workDir, "linked_out")
        Files.createSymbolicLink(symlinkDir.toPath(), outsideTarget.toPath())

        val result = FileTreeRepository.deleteEntry(symlinkDir)
        assertTrue(result.isSuccess)
        assertFalse(Files.exists(symlinkDir.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(keptFile.exists())
        assertEquals("keep me", keptFile.readText())
    }

    @Test
    fun rightFileDrawerExpandsReloadsMenusReflectCategoryAndInvokesCallbacks() {
        val context = RuntimeEnvironment.getApplication()
        val srcDir = File(tempRoot, "src").apply { mkdirs() }
        val ktFile = File(srcDir, "Main.kt").apply { writeText("fun main() {}") }
        val pngFile = File(tempRoot, "logo.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val tempFile = File(tempRoot, "stale.txt").apply { writeText("delete me") }

        val openedFiles = mutableListOf<String>()
        val copiedPaths = mutableListOf<String>()
        val terminalDirs = mutableListOf<String>()
        val externalFiles = mutableListOf<String>()

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                RightFileDrawer(
                    workspaceName = "Demo",
                    rootPath = tempRoot.absolutePath,
                    onClose = {},
                    onOpenFile = { openedFiles.add(it.absolutePath) },
                    onOpenExternally = { externalFiles.add(it.absolutePath) },
                    onCopyPath = { copiedPaths.add(it) },
                    onOpenTerminalHere = { terminalDirs.add(it) },
                    onOpenFullFileManager = {}
                )
            }
        }

        // Wait for initial load
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("stale.txt").fetchSemanticsNodes().isNotEmpty()
        }

        // Expand src directory -> shows Main.kt lazily
        composeRule.onNodeWithText("src").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("Main.kt").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Main.kt").assertIsDisplayed()

        // Menu on editable .kt file shows "Edit / View", "Open Externally", "Copy Path", "Open Terminal Here", "Delete"
        composeRule.onNodeWithTag("file_tree_menu_${ktFile.absolutePath}").performClick()
        composeRule.onNodeWithText("Edit / View").assertIsDisplayed()
        composeRule.onNodeWithText("Open Externally").assertIsDisplayed()
        composeRule.onNodeWithText("Copy Path").performClick()
        assertEquals(listOf(ktFile.absolutePath), copiedPaths)

        // Open Terminal Here on ktFile passes its parent directory (srcDir)
        composeRule.onNodeWithTag("file_tree_menu_${ktFile.absolutePath}").performClick()
        composeRule.onNodeWithText("Open Terminal Here").performClick()
        assertEquals(listOf(srcDir.absolutePath), terminalDirs)

        // Menu on view-only .png file shows "View" (not "Edit / View")
        composeRule.onNodeWithTag("file_tree_menu_${pngFile.absolutePath}").performClick()
        composeRule.onNodeWithText("View").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Edit / View").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("View").performClick()
        assertEquals(listOf(pngFile.absolutePath), openedFiles)

        // Delete file externally then tap Reload -> stale.txt disappears
        assertTrue(tempFile.delete())
        composeRule.onNodeWithTag("right_drawer_reload").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("stale.txt").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText("stale.txt").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun rightFileDrawerMissingRootShowsRecoverableErrorCard() {
        val context = RuntimeEnvironment.getApplication()
        val missingRoot = File(tempRoot, "missing_workspace_root")
        var fileManagerOpened = false

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                RightFileDrawer(
                    workspaceName = "Broken",
                    rootPath = missingRoot.absolutePath,
                    onClose = {},
                    onOpenFile = {},
                    onOpenExternally = {},
                    onCopyPath = {},
                    onOpenTerminalHere = {},
                    onOpenFullFileManager = { fileManagerOpened = true }
                )
            }
        }

        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("right_drawer_error_card").assertIsDisplayed()
        composeRule.onNodeWithText("Open File Manager").performClick()
        assertTrue(fileManagerOpened)
    }

    @Test
    fun openExternalEditorReturnsFalseCleanlyWhenPathOutsideFileProviderOrNoHandler() {
        val context = RuntimeEnvironment.getApplication()
        val unsharedFile = File(tempRoot, "unshared.txt").apply { writeText("hello") }
        val result = openExternalEditor(context, unsharedFile)
        assertFalse(result)
    }

    @Test
    fun rightFileDrawerReloadsWhenWorkspaceRootChangesAndSupportsCreateFileAndCycleNotice() {
        val context = RuntimeEnvironment.getApplication()
        val ws1 = File(tempRoot, "ws1").apply { mkdirs() }
        val ws2 = File(tempRoot, "ws2").apply { mkdirs() }
        File(ws1, "first.txt").writeText("one")
        val loopLink = File(ws1, "self_loop")
        Files.createSymbolicLink(loopLink.toPath(), ws1.toPath())
        File(ws2, "second.kt").writeText("fun two() {}")

        var activeName by mutableStateOf("WS1")
        var activeRoot by mutableStateOf(ws1.absolutePath)

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                RightFileDrawer(
                    workspaceName = activeName,
                    rootPath = activeRoot,
                    onClose = {},
                    onOpenFile = {},
                    onOpenExternally = {},
                    onCopyPath = {},
                    onOpenTerminalHere = {},
                    onOpenFullFileManager = {}
                )
            }
        }

        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("first.txt").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("file_tree_cycle_${loopLink.absolutePath}", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("self_loop").performClick()
        composeRule.onNodeWithTag("file_tree_cycle_notice_${loopLink.absolutePath}").assertIsDisplayed()

        // Create file via + File dialog
        composeRule.onNodeWithTag("right_drawer_create_file").performClick()
        composeRule.onNodeWithTag("create_entry_name_input").performTextInput("created.md")
        composeRule.onNodeWithTag("create_entry_confirm").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("created.md").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(File(ws1, "created.md").exists())

        // Switch workspace rootPath -> drawer reloads new workspace files
        composeRule.runOnUiThread {
            activeName = "WS2"
            activeRoot = ws2.absolutePath
        }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("second.kt").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText("first.txt").fetchSemanticsNodes().isEmpty())
    }
}
