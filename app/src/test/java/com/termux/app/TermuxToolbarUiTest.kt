package com.termux.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.betermux.compose.BeTermuxComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TermuxToolbarUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun folderAndExtraKeysActionsFollowSwappedSemanticOrder() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                TerminalToolbar(
                    compact = false,
                    filesOpen = false,
                    rightDrawerEnabled = false,
                    onDrawer = { actions += "workspace" },
                    onToggleRightDrawer = { actions += "right_drawer" },
                    onOpenProjectDir = { actions += "project_dir" },
                    onOpenFileManager = { actions += "file_manager" },
                    onLocalhost = { actions += "localhost" },
                    onKeys = { actions += "keys" },
                    onQuickCommands = { actions += "quick_commands" },
                    onNewBrowserTab = { actions += "browser" },
                    onPackageManager = { actions += "packages" },
                    onClearTerminal = { actions += "clear" },
                    onUploadImage = { actions += "upload" },
                    onSpeechToText = { actions += "speech" },
                    onToggleAutocorrect = { actions += "autocorrect" },
                    onTipsAndShortcuts = { actions += "tips" },
                    onSettings = { actions += "settings" },
                )
            }
        }

        val drawerX = composeRule.onNodeWithContentDescription("Open workspace session drawer").fetchSemanticsNode().boundsInRoot.left
        val keysX = composeRule.onNodeWithContentDescription("Toggle Extra Keys").fetchSemanticsNode().boundsInRoot.left
        val localhostX = composeRule.onNodeWithContentDescription("Localhost Servers").fetchSemanticsNode().boundsInRoot.left
        val filesX = composeRule.onNodeWithContentDescription("Files").fetchSemanticsNode().boundsInRoot.left
        val moreX = composeRule.onNodeWithContentDescription("More Options").fetchSemanticsNode().boundsInRoot.left

        assertTrue(drawerX < keysX)
        assertTrue(keysX < localhostX)
        assertTrue(localhostX < filesX)
        assertTrue(filesX < moreX)

        composeRule.onNodeWithContentDescription("Open workspace session drawer").performClick()
        composeRule.onNodeWithContentDescription("Toggle Extra Keys").performClick()
        composeRule.onNodeWithContentDescription("Localhost Servers").performClick()
        composeRule.runOnIdle { assertEquals(listOf("workspace", "keys", "localhost"), actions) }
    }

    @Test
    fun filesAndMoreInlineMenusInvokeCallbacks() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                TerminalToolbar(
                    compact = false,
                    filesOpen = false,
                    rightDrawerEnabled = false,
                    onDrawer = { actions += "workspace" },
                    onToggleRightDrawer = { actions += "right_drawer" },
                    onOpenProjectDir = { actions += "project_dir" },
                    onOpenFileManager = { actions += "file_manager" },
                    onLocalhost = { actions += "localhost" },
                    onKeys = { actions += "keys" },
                    onQuickCommands = { actions += "quick_commands" },
                    onNewBrowserTab = { actions += "browser" },
                    onPackageManager = { actions += "packages" },
                    onClearTerminal = { actions += "clear" },
                    onUploadImage = { actions += "upload" },
                    onSpeechToText = { actions += "speech" },
                    onToggleAutocorrect = { actions += "autocorrect" },
                    onTipsAndShortcuts = { actions += "tips" },
                    onSettings = { actions += "settings" },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Files").performClick()
        composeRule.onNodeWithText("Project directory").assertIsDisplayed()
        composeRule.onNodeWithText("File manager").assertIsDisplayed()
        composeRule.onNodeWithText("Project directory").performClick()

        composeRule.onNodeWithContentDescription("Files").performClick()
        composeRule.onNodeWithText("File manager").performClick()

        val expectedMoreItems = listOf(
            "Quick Commands" to "quick_commands",
            "Localhost Servers" to "localhost",
            "New Dev Browser Tab" to "browser",
            "Package Manager" to "packages",
            "Clear Terminal" to "clear",
            "Upload / Paste Image" to "upload",
            "Speech to Text" to "speech",
            "Toggle Autocorrect" to "autocorrect",
            "Tips & Shortcuts" to "tips",
            "Settings" to "settings",
        )
        expectedMoreItems.forEach { (label, _) ->
            composeRule.onNodeWithContentDescription("More Options").performClick()
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf("project_dir", "file_manager") + expectedMoreItems.map { it.second },
                actions,
            )
        }
    }

    @Test
    fun filesActionTogglesRightDrawerDirectlyWhenEnabled() {
        var filesOpen by mutableStateOf(false)
        var toggleCalls = 0
        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                TerminalToolbar(
                    compact = false,
                    filesOpen = filesOpen,
                    rightDrawerEnabled = true,
                    onDrawer = {},
                    onToggleRightDrawer = {
                        toggleCalls++
                        filesOpen = !filesOpen
                    },
                    onOpenProjectDir = {},
                    onOpenFileManager = {},
                    onLocalhost = {},
                    onKeys = {},
                    onQuickCommands = {},
                    onNewBrowserTab = {},
                    onPackageManager = {},
                    onClearTerminal = {},
                    onUploadImage = {},
                    onSpeechToText = {},
                    onToggleAutocorrect = {},
                    onTipsAndShortcuts = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open project files drawer").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, toggleCalls)
        composeRule.onNodeWithText("Project directory").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close project files drawer").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(2, toggleCalls) }
    }
}
