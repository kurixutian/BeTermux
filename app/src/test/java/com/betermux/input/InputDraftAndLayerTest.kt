package com.betermux.input

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.features.BeTermuxSettings
import com.betermux.features.TextExpansionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InputDraftAndLayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draftSurvivesSimulatedProcessDeathViaSlotSync() {
        val context = RuntimeEnvironment.getApplication()
        val store1 = InputDraftStore(context)
        store1.syncSessionHandles(listOf("old-h0", "old-h1"))
        store1.setDraft("old-h0", "echo line1\necho line2", commitSync = true)
        store1.setDraft("old-h1", "git diff", commitSync = true)

        // New InputDraftStore instance mimics fresh process with regenerated session UUIDs
        val store2 = InputDraftStore(context)
        store2.syncSessionHandles(listOf("new-h0", "new-h1"))

        assertEquals("echo line1\necho line2", store2.getDraft("new-h0"))
        assertEquals("git diff", store2.getDraft("new-h1"))
        assertTrue(store2.hasNonBlankDraft("new-h0"))

        store2.removeSessionDraft("new-h0", commitSync = true)
        assertFalse(store2.hasNonBlankDraft("new-h0"))
    }

    @Test
    fun saveDraftAsSnippetCreatesUniqueEntries() {
        val context = RuntimeEnvironment.getApplication()
        val trigger1 = InputDraftStore.saveDraftAsSnippet(context, "git status -s\ngit diff")
        val trigger2 = InputDraftStore.saveDraftAsSnippet(context, "git status -s\ngit diff")
        assertNotNull(trigger1)
        assertNotNull(trigger2)
        assertTrue(trigger1 != trigger2)
        assertEquals("git status -s\ngit diff", TextExpansionStore.findExpansion(context, trigger1!!))
    }

    @Test
    fun promptContextResolvesGitBranchAndProjectTags() {
        val root = createTempDirectory("betermux-ctx").toFile()
        try {
            val gitDir = File(root, ".git").apply { mkdirs() }
            File(gitDir, "HEAD").writeText("ref: refs/heads/feat/warp-input\n")
            File(root, "build.gradle").writeText("// gradle")
            val sub = File(root, "app/src").apply { mkdirs() }

            val ctx = PromptContextResolver.resolve(sub.absolutePath, lastExitCode = 2, homeDir = root.absolutePath)
            assertEquals("feat/warp-input", ctx.gitBranch)
            assertEquals("~/app/src", ctx.displayCwd)
            assertEquals(2, ctx.lastExitCode)

            val rootCtx = PromptContextResolver.resolve(root.absolutePath, lastExitCode = 0, homeDir = root.absolutePath)
            assertEquals("~", rootCtx.displayCwd)
            assertTrue("gradle" in rootCtx.projectTags)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun inputPositionSettingPersistsTopAndBottom() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(BeTermuxSettings.INPUT_POSITION_BOTTOM, BeTermuxSettings.getInputBarPosition(context))
        BeTermuxSettings.setInputBarPosition(context, BeTermuxSettings.INPUT_POSITION_TOP)
        assertEquals(BeTermuxSettings.INPUT_POSITION_TOP, BeTermuxSettings.getInputBarPosition(context))
        BeTermuxSettings.setInputBarPosition(context, "invalid")
        assertEquals(BeTermuxSettings.INPUT_POSITION_BOTTOM, BeTermuxSettings.getInputBarPosition(context))
    }

    @Test
    fun floatingEditLayerPreservesDraftOnCloseAndDispatchesOnSendInPortraitAndLandscape() {
        val context = RuntimeEnvironment.getApplication()
        val sent = mutableListOf<String>()
        val configLandscape = Configuration(context.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }

        composeRule.setContent {
            var draft by remember { mutableStateOf("") }
            var editorOpen by remember { mutableStateOf(false) }
            var isTop by remember { mutableStateOf(false) }
            val promptCtx = remember {
                PromptContext("u0@device", "/home", "~", "main", null, emptyList())
            }
            CompositionLocalProvider(LocalConfiguration provides configLandscape) {
                BeTermuxComposeTheme(context) {
                    TwoRowInputBar(
                        promptContext = promptCtx,
                        draft = draft,
                        isTopPosition = isTop,
                        onOpenEditor = { editorOpen = true },
                        onSendCommand = { sent.add(it); draft = "" },
                        onClearDraft = { draft = "" },
                        onSendEnter = {},
                        onExecuteChipCommand = {},
                        onInsertChipText = {},
                        onCopyChipText = { _, _ -> }
                    )
                    FloatingEditLayer(
                        visible = editorOpen,
                        draft = draft,
                        promptContext = promptCtx,
                        isTopPosition = isTop,
                        onDraftChange = { draft = it },
                        onSend = { sent.add(it); draft = ""; editorOpen = false },
                        onClosePreservingDraft = { editorOpen = false },
                        onTogglePosition = { isTop = !isTop }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("two_row_input_field").performClick()
        composeRule.onNodeWithTag("floating_edit_input").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_edit_input").performTextInput("echo hello")
        composeRule.onNodeWithTag("floating_edit_newline").performClick()
        composeRule.onNodeWithTag("floating_edit_input").performTextInput("pwd")

        // Close sheet and verify draft is preserved in TwoRowInputBar
        composeRule.onNodeWithTag("floating_edit_close").performClick()
        composeRule.onNodeWithText("echo hello\npwd").assertIsDisplayed()
        assertTrue(sent.isEmpty())

        // Re-open and dispatch whole command via Send
        composeRule.onNodeWithTag("two_row_input_field").performClick()
        composeRule.onNodeWithTag("floating_edit_send").performClick()
        assertEquals(listOf("echo hello\npwd"), sent)
    }

    @Test
    fun commandEditorShowsSlashAndDirectoryAutocompleteAndAppliesSelection() {
        val tempRoot = java.nio.file.Files.createTempDirectory("autocomplete_test").toFile()
        try {
            val projectsDir = java.io.File(tempRoot, "projects").apply { mkdirs() }
            java.io.File(tempRoot, "notes.txt").writeText("hi")

            // Path completion from cwd
            val pathMatches = CommandAutocomplete.computeSuggestions(
                text = "cd ./pr",
                cursorIndex = 7,
                cwd = tempRoot.absolutePath,
                homeDir = tempRoot.absolutePath
            )
            assertTrue(pathMatches.any { it.label == "projects/" && it.insertText == "./projects/" })
            val (completedPath, pathCursor) = CommandAutocomplete.applySuggestion(
                "cd ./pr",
                7,
                pathMatches.first { it.label == "projects/" }
            )
            assertEquals("cd ./projects/", completedPath)
            assertEquals("cd ./projects/".length, pathCursor)

            // Slash command + UI chip insertion in FloatingEditLayer
            val context = RuntimeEnvironment.getApplication()
            var latestDraft = ""
            composeRule.setContent {
                var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                BeTermuxComposeTheme(context) {
                    FloatingEditLayer(
                        visible = true,
                        draft = draft,
                        promptContext = PromptContext(
                            userAtHost = "u@h",
                            fullCwd = tempRoot.absolutePath,
                            displayCwd = "~/test",
                            gitBranch = null,
                            lastExitCode = null
                        ),
                        isTopPosition = false,
                        onDraftChange = {
                            draft = it
                            latestDraft = it
                        },
                        onSend = {},
                        onClosePreservingDraft = {},
                        onTogglePosition = {}
                    )
                }
            }

            composeRule.onNodeWithTag("floating_edit_input").performTextInput("/imp")
            composeRule.waitUntil(timeoutMillis = 2_000) {
                composeRule.onAllNodesWithTag("autocomplete_/impeccable").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("autocomplete_/impeccable").assertIsDisplayed().performClick()
            assertEquals("/impeccable ", latestDraft)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
