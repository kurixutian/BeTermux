package com.betermux.blocks

import android.graphics.Typeface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.betermux.compose.BeTermuxComposeTheme
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.Osc133Event
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalRowPainter
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
class BlockSelectionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blockHeaderLongPressEntersSelectionModeAndOpensTextSheet() {
        val context = RuntimeEnvironment.getApplication()
        val controller = BlockSessionController(scheduleDelayMs = { _, r -> r.run() }, cancelCallback = {})
        val client = object : TermuxTerminalSessionClientBase() {
            override fun onTextChanged(changedSession: TerminalSession) = controller.onSessionTextChanged(changedSession)
            override fun onOsc133Event(session: TerminalSession, event: Osc133Event) = controller.onOsc133Event(session, event)
            override fun onOsc7CwdChanged(session: TerminalSession, cwd: String) = controller.onOsc7CwdChanged(session, cwd)
            override fun onMainBufferReflowed(session: TerminalSession, mapping: TerminalBuffer.ReflowMapping) =
                controller.onMainBufferReflowed(session, mapping)
        }
        val session = TerminalSession("/bin/sh", "/", emptyArray(), emptyArray(), 500, client)
        val emulator = TerminalEmulator(session, 50, 10, 12, 24, 500, client)
        session.attachEmulatorForTesting(emulator)
        controller.setActiveSession(session)

        val stream = ("\u001B]133;A\u0007$ \u001B]133;B\u0007echo hello\u001B]133;C\u0007\r\nhello world\r\n\u001B]133;D;0\u0007")
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

        composeRule.onNodeWithText("echo hello").assertIsDisplayed()

        // Long-press the block header to enter outline multi-select mode.
        composeRule.onNodeWithContentDescription("Command block: echo hello, exit code 0")
            .performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        assertTrue(controller.getCurrentState().selectionMode)

        // Open the plain-text selection sheet and verify extracted output renders inside it.
        composeRule.onNodeWithContentDescription("Select text within block").performClick()
        composeRule.onNodeWithText("Drag handles to select exact characters or words").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close text selection sheet").performClick()
    }
}
