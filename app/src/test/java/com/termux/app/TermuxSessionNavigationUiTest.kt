package com.termux.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.betermux.compose.BeTermuxComposeTheme
import com.termux.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TermuxSessionNavigationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun tabStripRendersTabsLivePortsAndAddMenu() {
        val callbacks = RecordingChromeCallbacks()
        val terminalTab = ChromeTabItem(
            key = "term:h1",
            kind = ChromeTabKind.TERMINAL,
            index = 0,
            handle = "h1",
            label = "Term",
            iconRes = R.drawable.ic_tab_terminal,
            status = TermuxActivity.TAB_STATUS_FINISHED,
            progress = 0,
            showClose = false,
        )
        val browserTab = ChromeTabItem(
            key = "browser:0",
            kind = ChromeTabKind.BROWSER,
            index = 0,
            handle = null,
            label = "Web",
            iconRes = R.drawable.ic_tab_browser,
            status = TermuxActivity.TAB_STATUS_PROCESSING,
            progress = 45,
            showClose = true,
        )
        val livePort = LivePortItem(port = 3000, processName = "node", pid = 1234)

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                TermuxTopChrome(
                    tabs = listOf(terminalTab, browserTab),
                    livePorts = listOf(livePort),
                    activeTabKey = terminalTab.key,
                    showSessionTabs = true,
                    filesOpen = false,
                    rightDrawerEnabled = false,
                    callbacks = callbacks,
                )
            }
        }

        composeRule.onNodeWithText("Term").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.close_tab))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(":3000").assertIsDisplayed().performClick()

        composeRule.onNodeWithContentDescription("New tab").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("New Terminal Tab").assertIsDisplayed()
        composeRule.onNodeWithText("New Dev Browser Tab").assertIsDisplayed()
        composeRule.onNodeWithText("New Terminal Tab").performClick()

        composeRule.onNodeWithContentDescription("New tab").performClick()
        composeRule.onNodeWithText("New Dev Browser Tab").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(terminalTab), callbacks.selectedTabs)
            assertEquals(listOf(browserTab), callbacks.closedTabs)
            assertEquals(listOf(3000), callbacks.openedPorts)
            assertEquals(1, callbacks.newTerminalTabCalls)
            assertEquals(1, callbacks.newBrowserTabCalls)
        }
    }

    @Test
    fun statusLineAvoidsDuplicateKeyboardButtonAndTogglesDeckButton() {
        val callbacks = RecordingChromeCallbacks()
        var showDeckButton by mutableStateOf(false)
        val terminalTab = ChromeTabItem(
            key = "term:h1",
            kind = ChromeTabKind.TERMINAL,
            index = 0,
            handle = "h1",
            label = "Term",
            iconRes = R.drawable.ic_tab_terminal,
            status = TermuxActivity.TAB_STATUS_STANDBY,
            progress = 0,
            showClose = false,
        )

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                TermuxStatusLine(
                    tabs = listOf(terminalTab),
                    livePorts = emptyList(),
                    activeTabKey = terminalTab.key,
                    filesOpen = false,
                    rightDrawerEnabled = false,
                    showDeckButton = showDeckButton,
                    callbacks = callbacks,
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Toggle Extra Keys").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Show controls on the second screen").assertDoesNotExist()

        showDeckButton = true
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Toggle Extra Keys").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Show controls on the second screen")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, callbacks.reopenDeckCalls) }
    }

    @Test
    fun chromeControllerAndAutoCorrectBarAreNonFocusableAndFireCallbacks() {
        val topView = ComposeView(context)
        val statusView = ComposeView(context)
        TermuxChromeController(context, topView, statusView, RecordingChromeCallbacks())
        listOf(topView, statusView).forEach { host ->
            assertFalse(host.isFocusable)
            assertFalse(host.isFocusableInTouchMode)
            assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, host.descendantFocusability)
        }

        val detachedBar = AutoCorrectComposeBar(context)
        assertFalse(detachedBar.isFocusable)
        assertFalse(detachedBar.isFocusableInTouchMode)
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, detachedBar.descendantFocusability)
        val innerCompose = detachedBar.getChildAt(0) as ViewGroup
        assertFalse(innerCompose.isFocusable)
        assertFalse(innerCompose.isFocusableInTouchMode)
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, innerCompose.descendantFocusability)
        assertEquals(View.GONE, detachedBar.visibility)
        detachedBar.showSuggestion("git status")
        assertEquals(View.VISIBLE, detachedBar.visibility)
        detachedBar.hideSuggestion()
        assertEquals(View.GONE, detachedBar.visibility)

        var applyCalls = 0
        var dismissCalls = 0
        composeRule.setContent {
            val hostContext = LocalContext.current
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    AutoCorrectComposeBar(hostContext).apply {
                        bindActions({ applyCalls++ }, { dismissCalls++ })
                        showSuggestion("git status")
                    }
                },
            )
        }

        composeRule.onNodeWithText("Did you mean: git status?").assertIsDisplayed()
        composeRule.onNodeWithText("Apply").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Dismiss suggestion").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, applyCalls)
            assertEquals(1, dismissCalls)
        }
    }

    @Test
    fun tabAndLivePortLongPressMenusInvokeActions() {
        val callbacks = RecordingChromeCallbacks()
        val terminalTab = ChromeTabItem(
            key = "term:h1",
            kind = ChromeTabKind.TERMINAL,
            index = 0,
            handle = "h1",
            label = "Term",
            iconRes = R.drawable.ic_tab_terminal,
            status = TermuxActivity.TAB_STATUS_FINISHED,
            progress = 0,
            showClose = false,
        )
        val livePort = LivePortItem(port = 3000, processName = "node", pid = 1234)

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                TermuxTopChrome(
                    tabs = listOf(terminalTab),
                    livePorts = listOf(livePort),
                    activeTabKey = terminalTab.key,
                    showSessionTabs = true,
                    filesOpen = false,
                    rightDrawerEnabled = false,
                    callbacks = callbacks,
                )
            }
        }

        composeRule.onNodeWithText("Term").assertIsDisplayed().performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").performClick()

        composeRule.onNodeWithText("Term").performTouchInput { longClick() }
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithText(":3000")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Localhost port"))
            .performTouchInput { longClick() }
        composeRule.onNodeWithText(":3000 • node (PID 1234)").assertIsDisplayed()
        composeRule.onNodeWithText("Stop Server").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("h1"), callbacks.renamedHandles)
            assertEquals(listOf("h1"), callbacks.closedHandles)
            assertEquals(listOf(3000), callbacks.stoppedPorts)
        }
    }

    @Test
    fun addMenuRemainingItemsAndControllerStatusUpdatesWorkCleanly() {
        val callbacks = RecordingChromeCallbacks()
        val terminalTab = ChromeTabItem(
            key = "term:h1",
            kind = ChromeTabKind.TERMINAL,
            index = 0,
            handle = "h1",
            label = "Term",
            iconRes = R.drawable.ic_tab_terminal,
            status = TermuxActivity.TAB_STATUS_STANDBY,
            progress = 0,
            showClose = false,
        )
        lateinit var controller: TermuxChromeController

        composeRule.setContent {
            val hostContext = LocalContext.current
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    LinearLayout(hostContext).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        val topView = ComposeView(hostContext)
                        val statusView = ComposeView(hostContext)
                        addView(
                            topView,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                        addView(
                            statusView,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                        controller = TermuxChromeController(hostContext, topView, statusView, callbacks)
                        controller.updateTabs(listOf(terminalTab), emptyList(), terminalTab.key)
                    }
                },
            )
        }

        composeRule.onNodeWithText("Term")
            .assertIsDisplayed()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Terminal tab, Standby"))

        composeRule.runOnIdle {
            controller.updateTabStatuses(
                listOf("term:h1"),
                intArrayOf(TermuxActivity.TAB_STATUS_PROCESSING),
                intArrayOf(65),
            )
        }

        composeRule.onNodeWithText("Term")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Terminal tab, Processing"))

        composeRule.onNodeWithContentDescription("New tab").performClick()
        composeRule.onNodeWithText("Localhost Servers").assertIsDisplayed().performClick()

        composeRule.onNodeWithContentDescription("New tab").performClick()
        composeRule.onNodeWithText("Files").assertIsDisplayed().performClick()

        composeRule.onNodeWithContentDescription("New tab").performClick()
        composeRule.onNodeWithText("Upload / Paste Image").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, callbacks.localhostCalls)
            assertEquals(1, callbacks.fileManagerCalls)
            assertEquals(1, callbacks.uploadImageCalls)
        }

        composeRule.onNodeWithContentDescription("New tab").performClick()
        composeRule.onNodeWithText("Localhost Servers").assertIsDisplayed()
        composeRule.runOnIdle { controller.setVisibleModes(topVisible = false, statusVisible = true) }
        composeRule.onNodeWithContentDescription("New tab").assertDoesNotExist()
        composeRule.onNodeWithText("Localhost Servers").assertDoesNotExist()
        composeRule.onNodeWithText("Term").assertIsDisplayed()
    }

    private class RecordingChromeCallbacks : TermuxChromeCallbacks {
        val selectedTabs = mutableListOf<ChromeTabItem>()
        val closedTabs = mutableListOf<ChromeTabItem>()
        val openedPorts = mutableListOf<Int>()
        val renamedHandles = mutableListOf<String>()
        val closedHandles = mutableListOf<String>()
        val stoppedPorts = mutableListOf<Int>()
        var newTerminalTabCalls = 0
        var newBrowserTabCalls = 0
        var reopenDeckCalls = 0
        var localhostCalls = 0
        var fileManagerCalls = 0
        var uploadImageCalls = 0

        override fun onOpenWorkspaceDrawer() = Unit
        override fun onToggleRightDrawer() = Unit
        override fun onOpenProjectDirectory() = Unit
        override fun onOpenFileManager() { fileManagerCalls++ }
        override fun onOpenLocalhost() { localhostCalls++ }
        override fun onToggleExtraKeys() = Unit
        override fun onReopenDeck() { reopenDeckCalls++ }
        override fun onQuickCommands() = Unit
        override fun onNewBrowserTab() { newBrowserTabCalls++ }
        override fun onOpenPackageManager() = Unit
        override fun onClearTerminal() = Unit
        override fun onUploadImage() { uploadImageCalls++ }
        override fun onSpeechToText() = Unit
        override fun onToggleAutocorrect() = Unit
        override fun onTipsAndShortcuts() = Unit
        override fun onOpenSettings() = Unit
        override fun onNewTerminalTab() { newTerminalTabCalls++ }
        override fun onSelectTab(item: ChromeTabItem) { selectedTabs += item }
        override fun onCloseTab(item: ChromeTabItem) { closedTabs += item }
        override fun onRenameTerminalTab(handle: String) { renamedHandles += handle }
        override fun onCloseTerminalTab(handle: String) { closedHandles += handle }
        override fun onReloadBrowserTab(index: Int) = Unit
        override fun onCloseOtherBrowserTabs(index: Int) = Unit
        override fun onCloseOtherFileTabs(index: Int) = Unit
        override fun onShareFileTab(index: Int) = Unit
        override fun onCopyFileTabPath(index: Int) = Unit
        override fun onCloseLocalhostTab() = Unit
        override fun onOpenLivePort(port: Int) { openedPorts += port }
        override fun onStopLivePort(port: Int) { stoppedPorts += port }
    }
}
