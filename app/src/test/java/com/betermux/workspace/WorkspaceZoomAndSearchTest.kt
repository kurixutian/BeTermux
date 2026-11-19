package com.betermux.workspace

import android.graphics.Typeface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.betermux.blocks.BlockDensity
import com.betermux.blocks.BlockSearchMatcher
import com.betermux.blocks.BlockSessionController
import com.betermux.blocks.BlockWorkspaceHost
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.compose.UiZoomMath
import com.betermux.features.BeTermuxSettings
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.Osc133Event
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalRowPainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceZoomAndSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uiZoomClampsRangeAndPreservesSystemFontScale() {
        val context = RuntimeEnvironment.getApplication()
        BeTermuxSettings.setUiZoomPercent(context, 40)
        assertEquals(85, BeTermuxSettings.getUiZoomPercent(context))
        BeTermuxSettings.setUiZoomPercent(context, 200)
        assertEquals(130, BeTermuxSettings.getUiZoomPercent(context))
        BeTermuxSettings.setUiZoomPercent(context, 115)
        assertEquals(1.15f, BeTermuxSettings.getUiZoomFactor(context), 0.001f)

        val base = Density(density = 2.0f, fontScale = 1.4f)
        val zoomed = UiZoomMath.computeZoomedDensity(base, 120)
        assertEquals(2.4f, zoomed.density, 0.001f)
        assertEquals(1.4f, zoomed.fontScale, 0.001f)
    }

    @Test
    fun blockSearchMatcherFindsMatchingRowsAndCaseInsensitiveColumns() {
        val client = TermuxTerminalSessionClientBase()
        val session = TerminalSession("/bin/sh", "/", emptyArray(), emptyArray(), 200, client)
        val emulator = TerminalEmulator(session, 40, 10, 12, 24, 200, client)
        session.attachEmulatorForTesting(emulator)

        val bytes = "first ok\r\nWARN failed check\r\nsecond FAILED step\r\ndone\r\n"
            .toByteArray(StandardCharsets.UTF_8)
        emulator.append(bytes, bytes.size)

        val result = BlockSearchMatcher.searchBlock(
            buffer = emulator.mainBuffer,
            outputStartAbsRow = 0L,
            endAbsRow = 3L,
            query = "failed"
        )
        assertEquals(listOf(1L, 2L), result.matchingAbsRows)
        assertEquals(4, result.totalOutputRows)
        assertEquals(2, result.totalMatches)
        assertEquals(listOf(5 until 11), result.columnSpansByAbsRow[1L])
        assertEquals(listOf(7 until 13), result.columnSpansByAbsRow[2L])
    }

    @Test
    fun inBlockSearchUiFiltersLinesAndTogglesAllMode() {
        val context = RuntimeEnvironment.getApplication()
        val controller = BlockSessionController(scheduleDelayMs = { _, r -> r.run() }, cancelCallback = {})
        val client = object : TermuxTerminalSessionClientBase() {
            override fun onTextChanged(changedSession: TerminalSession) = controller.onSessionTextChanged(changedSession)
            override fun onOsc133Event(session: TerminalSession, event: Osc133Event) = controller.onOsc133Event(session, event)
        }
        val session = TerminalSession("/bin/sh", "/", emptyArray(), emptyArray(), 200, client)
        val emulator = TerminalEmulator(session, 50, 10, 12, 24, 200, client)
        session.attachEmulatorForTesting(emulator)
        controller.setActiveSession(session)

        val stream = ("\u001B]133;A\u0007$ \u001B]133;B\u0007run-tests\r\n\u001B]133;C\u0007alpha ok\r\nbeta fail\r\ngamma OK\r\n\u001B]133;D;0\u0007")
            .toByteArray(StandardCharsets.UTF_8)
        emulator.append(stream, stream.size)

        val painter = TerminalRowPainter(24, Typeface.MONOSPACE)
        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                BlockWorkspaceHost(
                    controller = controller,
                    sessionProvider = { session },
                    painterProvider = { painter },
                    density = BlockDensity.COMFORT,
                    onTapTarget = { _, _, _ -> },
                    onRequestTerminalInputFocus = {},
                    onToggleDensity = {},
                    onToggleRawPty = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Block actions for run-tests").performClick()
        composeRule.onNodeWithText("Search in block").performClick()
        composeRule.onNodeWithTag("block_search_input").performTextInput("fail")
        composeRule.onNodeWithText("1/3").assertIsDisplayed()

        // Toggle Filter -> All mode
        composeRule.onNodeWithTag("block_search_filter_toggle").performClick()
        composeRule.onNodeWithText("All").assertIsDisplayed()

        // Close search bar
        composeRule.onNodeWithTag("block_search_close").performClick()
        composeRule.onNodeWithTag("block_search_input").assertDoesNotExist()
    }

    @Test
    fun collapsibleDrawerAndSplitPaneWorkspaceHandleSessionActionsAndDimming() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(0.82f, INACTIVE_PANE_ALPHA, 0.001f)
        val closedHandles = mutableListOf<String>()
        val selectedHandles = mutableListOf<String>()
        var focusedSecondary = false
        var splitClosed = false

        composeRule.setContent {
            var drawerOpen by remember { mutableStateOf(true) }
            BeTermuxComposeTheme(context) {
                SplitPaneWorkspace(
                    primaryTitle = "Main",
                    secondaryTitle = "Companion",
                    isPrimaryFocused = true,
                    onFocusPrimary = {},
                    onFocusSecondary = { focusedSecondary = true },
                    onCloseSplit = { splitClosed = true },
                    primaryContent = { Text("Primary Body") },
                    secondaryContent = { Text("Secondary Body") }
                )
                CollapsibleTabDrawer(
                    visible = drawerOpen,
                    sessions = listOf(
                        DrawerSessionItem("h1", "Main", "git status", isActive = true, isRunning = true),
                        DrawerSessionItem("h2", "Companion", "tail -f log", isActive = false, isRunning = false)
                    ),
                    onDismiss = { drawerOpen = false },
                    onSelectSession = { selectedHandles.add(it) },
                    onCloseSession = { closedHandles.add(it) },
                    onNewSession = {},
                    onOpenDevBrowser = {},
                    onOpenFileManager = {},
                    onOpenLocalhost = {},
                    onOpenQuickCommands = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("git status").assertIsDisplayed()
        composeRule.onNodeWithText("tail -f log").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_close_session_h2").performClick()
        assertEquals(listOf("h2"), closedHandles)

        composeRule.onNodeWithTag("drawer_session_h1").performClick()
        assertEquals(listOf("h1"), selectedHandles)

        // Drawer dismissed cleanly; now interact with split panes
        composeRule.onNodeWithTag("split_pane_secondary").performClick()
        assertTrue(focusedSecondary)
        composeRule.onNodeWithTag("split_pane_close").performClick()
        assertTrue(splitClosed)
    }
}
