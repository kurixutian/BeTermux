package com.betermux.blocks

import android.graphics.Typeface
import com.betermux.features.BeTermuxSettings
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.Osc133Event
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TerminalBlockModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestHarness(columns: Int = 60, rows: Int = 10): Pair<TerminalSession, BlockSessionController> {
        val controller = BlockSessionController(
            scheduleDelayMs = { _, r -> r.run() },
            cancelCallback = {}
        )
        lateinit var session: TerminalSession
        val client = object : TermuxTerminalSessionClientBase() {
            override fun onTextChanged(changedSession: TerminalSession) {
                controller.onSessionTextChanged(changedSession)
            }

            override fun onOsc133Event(s: TerminalSession, event: Osc133Event) {
                controller.onOsc133Event(s, event)
            }

            override fun onOsc7CwdChanged(s: TerminalSession, cwd: String) {
                controller.onOsc7CwdChanged(s, cwd)
            }

            override fun onMainBufferReflowed(s: TerminalSession, mapping: TerminalBuffer.ReflowMapping) {
                controller.onMainBufferReflowed(s, mapping)
            }
        }
        session = TerminalSession("/bin/sh", "/", emptyArray(), emptyArray(), 2000, client)
        val emulator = TerminalEmulator(session, columns, rows, 12, 24, 2000, client)
        session.attachEmulatorForTesting(emulator)
        controller.setActiveSession(session)
        return session to controller
    }

    private fun feed(session: TerminalSession, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        session.emulator.append(bytes, bytes.size)
        session.emulator.mainBuffer
        controllerOnTextChangedSafe(session)
    }

    private fun controllerOnTextChangedSafe(session: TerminalSession) {
        session.onColorsChanged() // keeps session client untouched; onTextChanged invoked manually if needed
    }

    @Test
    fun osc133LifecycleProducesStructuredBlockWithExitCodeAndDuration() {
        val (session, controller) = createTestHarness()
        val em = session.emulator
        val payload = (
            "Welcome banner line 1\r\n" +
                "\u001B]7;file://localhost/tmp/project\u0007" +
                "\u001B]133;A\u0007$ \u001B]133;B\u0007ls -la\u001B]133;C;cmdline=ls%20-la\r\n"
            ).replace("\r\n", "\u0007\r\n").let {
                "Welcome banner line 1\r\n" +
                    "\u001B]7;file://localhost/tmp/project\u0007" +
                    "\u001B]133;A\u0007$ \u001B]133;B\u0007ls -la\u001B]133;C;cmdline_url=ls%20-la\u0007\r\n" +
                    "README.md\r\nsrc/\r\n" +
                    "\u001B]133;D;0\u0007"
            }
        val b = payload.toByteArray(StandardCharsets.UTF_8)
        em.append(b, b.size)

        val state = controller.getCurrentState()
        assertNotNull("Pre-block banner should populate unblockifiedHeader (Q3(a))", state.unblockifiedHeader)
        assertEquals("/tmp/project", state.cwd)
        assertEquals(1, state.blocks.size)
        val blk = state.blocks.single()
        assertEquals("ls -la", blk.command)
        assertEquals(0, blk.exitCode)
        assertFalse(blk.isRunning)
        assertNotNull(blk.durationMs)
        val output = controller.extractBlockOutput(session, blk)
        assertTrue(output.contains("README.md"))
        assertTrue(output.contains("src/"))
    }

    @Test
    fun evictingOver200BlocksFoldsIntoUnblockifiedHeaderWithoutTranscriptLoss() {
        val (session, controller) = createTestHarness(columns = 60, rows = 8)
        val em = session.emulator
        val totalCommands = 205
        for (i in 1..totalCommands) {
            val seq = "\u001B]133;A\u0007$ \u001B]133;B\u0007echo $i\u001B]133;C\u0007\r\nout-$i\r\n\u001B]133;D;0\u0007"
            val bytes = seq.toByteArray(StandardCharsets.UTF_8)
            em.append(bytes, bytes.size)
        }

        val state = controller.getCurrentState()
        assertEquals(BlockSessionController.MAX_BLOCKS_PER_SESSION, state.blocks.size)
        assertEquals("echo 6", state.blocks.first().command)
        assertEquals("echo 205", state.blocks.last().command)
        val hdr = state.unblockifiedHeader
        assertNotNull("Evicted blocks must fold into unblockifiedHeader (Q5)", hdr)
        val evictedText = em.mainBuffer.getSelectedTextAbsolute(hdr!!.startAbsRow, hdr.endAbsRow)
        assertTrue("Evicted transcript output must remain readable", evictedText.contains("out-1"))
        assertTrue("Evicted transcript output must remain readable", evictedText.contains("out-5"))
    }

    @Test
    fun keyboardNavigationSelectionAndRawPtyToggleBehaveCleanly() {
        val (session, controller) = createTestHarness()
        val em = session.emulator
        val seq =
            "\u001B]133;A\u0007$ \u001B]133;B\u0007cmd1\u001B]133;C\u0007\r\na\r\n\u001B]133;D;0\u0007" +
                "\u001B]133;A\u0007$ \u001B]133;B\u0007cmd2\u001B]133;C\u0007\r\nbad\r\n\u001B]133;D;2\u0007"
        val bytes = seq.toByteArray(StandardCharsets.UTF_8)
        em.append(bytes, bytes.size)

        assertEquals(2, controller.getCurrentState().blocks.size)
        val firstId = controller.getCurrentState().blocks[0].id
        val secondId = controller.getCurrentState().blocks[1].id
        assertEquals(secondId, controller.getCurrentState().focusedBlockId)

        controller.jumpFocus(-1)
        assertEquals(firstId, controller.getCurrentState().focusedBlockId)
        controller.jumpFocus(+1)
        assertEquals(secondId, controller.getCurrentState().focusedBlockId)

        controller.toggleBlockSelection(firstId)
        controller.toggleBlockSelection(secondId)
        assertEquals(setOf(firstId, secondId), controller.getCurrentState().selectedBlockIds)

        val copied = controller.buildPlainTextForBlocks(session, listOf(firstId, secondId), includeCommands = true)
        assertTrue(copied.contains("$ cmd1"))
        assertTrue(copied.contains("bad"))

        controller.toggleRawPtyOverride()
        assertTrue(controller.getCurrentState().rawPtyOverride)
        assertTrue(controller.getCurrentState().selectedBlockIds.isEmpty())
        controller.toggleRawPtyOverride()
        assertFalse(controller.getCurrentState().rawPtyOverride)
    }

    @Test
    fun blockSettingsAndDensityPersistAcrossReads() {
        val ctx = RuntimeEnvironment.getApplication()
        assertTrue(BeTermuxSettings.isBlockViewEnabled(ctx))
        assertEquals(BlockDensity.COMFORT, BlockDensity.fromKey(BeTermuxSettings.getBlockDensity(ctx)))

        BeTermuxSettings.setBlockViewEnabled(ctx, false)
        BeTermuxSettings.setBlockDensity(ctx, BeTermuxSettings.DENSITY_COMPACT)

        assertFalse(BeTermuxSettings.isBlockViewEnabled(ctx))
        assertEquals(BlockDensity.COMPACT, BlockDensity.fromKey(BeTermuxSettings.getBlockDensity(ctx)))
    }

    @Test
    fun shellIntegrationInstallerIsIdempotentAndPreservesExistingUserRc() {
        val fakeHome = tempFolder.newFolder("home")
        val zshrc = File(fakeHome, ".zshrc")
        zshrc.writeText("export MY_CUSTOM=1\n")

        ShellIntegrationInstaller.installIntoHomeDir(fakeHome)
        ShellIntegrationInstaller.installIntoHomeDir(fakeHome)

        val content = zshrc.readText()
        assertTrue(content.contains("export MY_CUSTOM=1"))
        val count = Regex("# BeTermux block integration").findAll(content).count()
        assertEquals(1, count)
        assertTrue(File(fakeHome, ".config/betermux/integration.bash").exists())
        assertTrue(File(fakeHome, ".config/betermux/integration.zsh").exists())
    }

    @Test
    fun tapResolverDetectsUrlsAndPathsCleanly() {
        assertEquals("https://example.com/a/b", TerminalTapResolver.extractUrl("(https://example.com/a/b)".let {
            TerminalTapResolver.stripEnclosingPunctuation(it)
        }))
        assertEquals("http://localhost:3000/api", TerminalTapResolver.extractUrl("localhost:3000/api"))
        assertNull(TerminalTapResolver.extractUrl("plainword"))
    }
}
