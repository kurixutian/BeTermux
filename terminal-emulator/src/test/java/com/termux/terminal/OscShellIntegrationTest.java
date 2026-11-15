package com.termux.terminal;

/**
 * Tests for OSC 133 shell integration, OSC 7 CWD tracking, monotonic {@code absRow}
 * coordinates across scrollback, and column-reflow {@link TerminalBuffer.ReflowMapping}.
 */
public class OscShellIntegrationTest extends TerminalTestCase {

    public void testOsc133SequenceWithScrollbackAndExitCodes() {
        withTerminalSized(40, 4);

        enterString("\033]133;A\007$ \033]133;B\007ls -la\033]133;C;cmdline_url=ls%20-la\007\r\n");
        enterString("file1\r\nfile2\r\nfile3\r\nfile4\r\nfile5\r\n");
        enterString("\033]133;D;0\007\033]133;A\007$ \033]133;B\007false\033]133;C\007\r\n");
        enterString("\033]133;D;1\033\\");

        assertEquals(8, mOutput.osc133Events.size());
        Osc133Event a1 = mOutput.osc133Events.get(0);
        Osc133Event c1 = mOutput.osc133Events.get(2);
        Osc133Event d1 = mOutput.osc133Events.get(3);
        Osc133Event d2 = mOutput.osc133Events.get(7);

        assertEquals(Osc133Event.TYPE_PROMPT_START, a1.type);
        assertEquals(0L, a1.absRow);
        assertEquals(Osc133Event.TYPE_COMMAND_EXECUTED, c1.type);
        assertEquals("ls -la", c1.commandLine);
        assertEquals(Osc133Event.TYPE_COMMAND_FINISHED, d1.type);
        assertEquals(Integer.valueOf(0), d1.exitCode);
        assertTrue("d1.absRow should advance past initial 4 screen rows, was " + d1.absRow, d1.absRow >= 6L);

        assertEquals(Osc133Event.TYPE_COMMAND_FINISHED, d2.type);
        assertEquals(Integer.valueOf(1), d2.exitCode);

        // Verify monotonic absolute row text lookup retains scrolled-off first command output.
        TerminalBuffer buf = mTerminal.getMainBuffer();
        String cmd1Text = buf.getSelectedTextAbsolute(c1.absRow, d1.absRow);
        assertTrue(cmd1Text.contains("file1"));
        assertTrue(cmd1Text.contains("file5"));
    }

    public void testOsc133IgnoredInAlternateScreenBuffer() {
        withTerminalSized(40, 6);
        enterString("\033]133;A\007");
        int countBeforeAlt = mOutput.osc133Events.size();

        enterString("\033[?1049h\033]133;A\007\033]133;C\007\033]133;D;0\007\033[?1049l");
        assertEquals(countBeforeAlt, mOutput.osc133Events.size());
    }

    public void testOsc7WorkingDirectoryDecodesPercentAndPreservesPlus() {
        withTerminalSized(40, 4);
        enterString("\033]7;file://localhost/tmp/my%20dir%2Bc%2B%2B\007");
        enterString("\033]7;file:///data/home\033\\");

        assertEquals(2, mOutput.osc7Cwds.size());
        assertEquals("/tmp/my dir+c++", mOutput.osc7Cwds.get(0));
        assertEquals("/data/home", mOutput.osc7Cwds.get(1));
        assertEquals("/data/home", mTerminal.getCurrentWorkingDirectory());
    }

    public void testHeightResizeKeepsAbsoluteRowCoordinatesInvariant() {
        withTerminalSized(30, 4);
        for (int i = 0; i < 8; i++) {
            enterString("line-" + i + "\r\n");
        }
        TerminalBuffer buf = mTerminal.getMainBuffer();
        long rowBefore = buf.getTotalTranscriptRows() - 3;
        String expected = buf.getSelectedTextAbsolute(rowBefore, rowBefore).trim();
        assertTrue("Expected non-empty retained line, got: " + expected, expected.startsWith("line-"));

        mTerminal.resize(30, 6, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
        assertNull(mOutput.lastReflowMapping);
        assertEquals(expected, buf.getSelectedTextAbsolute(rowBefore, rowBefore).trim());
    }

    public void testColumnResizeReflowProducesValidReflowMapping() {
        withTerminalSized(40, 6);
        enterString("\033]133;C\00701234567890123456789012345678901\r\n");
        long cmdAbsRow = mOutput.osc133Events.get(0).absRow;

        mTerminal.resize(15, 6, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
        assertNotNull(mOutput.lastReflowMapping);

        long mappedStart = mOutput.lastReflowMapping.mapStart(cmdAbsRow);
        long mappedEnd = mOutput.lastReflowMapping.mapEnd(cmdAbsRow);
        assertTrue(mappedEnd >= mappedStart);
        String remappedText = mTerminal.getMainBuffer().getSelectedTextAbsolute(mappedStart, mappedEnd);
        assertTrue("Remapped text should contain wrapped string, got: " + remappedText,
            remappedText.replace("\n", "").contains("01234567890123456789012345678901"));
    }
}
